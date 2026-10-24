<a id="back-to-top"></a>

# Field

## Menu
- [Tên, declaring class, raw type và generic type](#fields-001)
- [getFields khác getDeclaredFields khi có kế thừa](#fields-002)
- [Field hiding và nhiều field trùng tên trong phân cấp](#fields-003)
- [Đọc field instance bằng đối tượng đúng kiểu](#fields-004)
- [Đọc field static và đối số đích bị bỏ qua](#fields-005)
- [Field.get trả primitive dưới dạng wrapper](#fields-006)
- [Các phương thức getInt, getLong, getDouble và chuyển đổi được phép](#fields-007)
- [Field.set với kiểu tham chiếu hoặc primitive](#fields-008)
- [Các phương thức setInt, setLong và giới hạn widening](#fields-009)
- [Giá trị null với field tham chiếu và primitive](#fields-010)
- [Truy cập static field có thể kích hoạt khởi tạo lớp](#fields-011)
- [Field volatile và phân biệt thao tác đọc ghi với cập nhật nguyên tử](#fields-012)

## <a id="fields-001">Tên, declaring class, raw type và generic type</a>

<details>
<summary>Xem chi tiết</summary>

# Field trong Java Reflection: tên, declaring class, raw type và generic type

## 1. Mục tiêu

Khi dùng Java Reflection để khảo sát một trường dữ liệu, bốn câu hỏi nền tảng là:

1. **Tên trường là gì?** → `Field.getName()`.
2. **Lớp hoặc interface nào thực sự khai báo trường?** → `Field.getDeclaringClass()`.
3. **Kiểu runtime của trường sau khi xóa generic là gì?** → `Field.getType()`.
4. **Kiểu được khai báo trong chữ ký generic là gì?** → `Field.getGenericType()`.

Bốn thông tin này phục vụ các mục đích khác nhau. Chẳng hạn một serializer cần tên để ánh xạ thuộc tính, cần declaring class để xác định vị trí khai báo, cần raw type để biết trường có phải `List` hoặc `Map`, và cần generic type để biết `List<String>` hay `List<Integer>`. Đừng lấy kết quả của một phương thức để trả lời thay câu hỏi của phương thức khác.

Đối tượng `java.lang.reflect.Field` là **metadata mô tả khai báo trường** trong một lớp/interface. Nó không tự chứa giá trị trường của từng object. Muốn đọc giá trị cần `field.get(receiver)`; thao tác này liên quan quyền truy cập và không phải trọng tâm của bốn thuộc tính đang học.

Ví dụ:

```java
class Book {
    private String title;
    private java.util.List<String> tags;
}
```

Với `Field f = Book.class.getDeclaredField("tags")`:

| Câu hỏi | Phương thức | Kết quả |
|---|---|---|
| Tên trường? | `f.getName()` | `"tags"` |
| Ai khai báo? | `f.getDeclaringClass()` | `Book.class` |
| Raw type? | `f.getType()` | `java.util.List.class` |
| Generic type? | `f.getGenericType()` | Đối tượng `ParameterizedType` biểu diễn `java.util.List<java.lang.String>` |

Đây là ví dụ minh họa về metadata, chưa phải chương trình có hàm `main` độc lập; chương trình chạy được đầy đủ xuất hiện ở mục 8.

## 2. Từ `Class<?>` đến đối tượng `Field`

Ta lấy `Field` qua `Class<?>`, không tạo `Field` bằng `new`:

```java
Class<?> type = Book.class;
Field tags = type.getDeclaredField("tags");
```

Cần `import java.lang.reflect.Field;` và xử lý `NoSuchFieldException`.

Các API tra cứu phổ biến:

| API của `Class` | Tìm gì? | Có trường kế thừa? |
|---|---|---|
| `getDeclaredField(String name)` | Một trường do **chính** lớp/interface hiện tại khai báo, bất kể `private`, `protected`, package-private hay `public` | Không |
| `getDeclaredFields()` | Mọi trường do **chính** lớp/interface hiện tại khai báo | Không |
| `getField(String name)` | Một trường `public` theo quy tắc tìm kiếm của Reflection | Có thể có |
| `getFields()` | Các trường `public` mà kiểu nhìn thấy theo quy tắc API | Có thể có |

**Declared không có nghĩa private.** Nó chỉ cho biết trường được khai báo ngay tại kiểu đang xét. Một trường `public` trong lớp cha cũng sẽ không có mặt trong `Child.class.getDeclaredFields()` nếu `Child` không khai báo trường đó.

Với `getDeclaredField("x")`, nếu lớp hiện tại không khai báo `x`, Java ném `NoSuchFieldException`. Nếu muốn tra cứu trường kế thừa `public`, xem `getField`. Nếu cần rà soát mọi lớp cha, kể cả trường không public, phải tự duyệt qua `getSuperclass()` và gọi `getDeclaredFields()` từng lớp.

Lưu ý: Java Reflection **không cam kết thứ tự** của mảng từ `getDeclaredFields()` hoặc `getFields()`. Tool xuất schema cần thứ tự ổn định thì tự sắp xếp theo tên và quy tắc riêng. Ngoài ra compiler có thể tạo **synthetic fields**, nên một công cụ chỉ quan tâm trường nghiệp vụ đôi khi cần kiểm tra `field.isSynthetic()` trước khi xử lý.

## 3. `Field.getName()`: tên của trường

Chữ ký API:

```java
String getName()
```

`getName()` trả **tên của trường** dưới dạng `String`. Đây là tên thành viên khai báo, không phải:

- Tên class hay tên package.
- Giá trị của trường trong một object.
- Tên property của JavaBeans theo quy tắc getter/setter.
- Tên cột trong database hay tên khóa JSON do framework đặt.
- Tên gồm kiểu generic, modifier hay declaring class.

Ví dụ:

```java
class Inventory {
    private long quantity;
    public static final int MAX_ITEMS = 100;
}
```

```java
Field one = Inventory.class.getDeclaredField("quantity");
Field two = Inventory.class.getDeclaredField("MAX_ITEMS");

System.out.println(one.getName()); // quantity
System.out.println(two.getName()); // MAX_ITEMS
```

Hai đoạn trên cần được đặt cùng ngữ cảnh và import `Field`; chúng minh họa rằng tên trường không phụ thuộc field là instance hay `static final`.

Khi ánh xạ `Field` sang external schema, **không nên lấy `getName()` làm tên khóa bất biến** nếu tên source có thể đổi. Một framework có thể chủ động xử lý annotation hoặc naming strategy, ví dụ camelCase sang snake_case, nhưng `Field.getName()` tự nó không thực hiện phép biến đổi đó.

Một lớp có thể chứa những trường bị compiler sinh thêm với tên không xuất hiện như khai báo bình thường trong source. Khi cần duyệt schema nghiệp vụ, xem xét `isSynthetic()` và các quy tắc loại trừ phù hợp.

## 4. `Field.getDeclaringClass()`: kiểu khai báo thực sự

Chữ ký:

```java
Class<?> getDeclaringClass()
```

Kết quả là đối tượng `Class<?>` đại diện **kiểu đã khai báo field**, bất kể người gọi tìm field thông qua kiểu nào.

Giả sử:

```java
class Parent {
    public String id;
}

class Child extends Parent {
    public int age;
}
```

```java
Field inherited = Child.class.getField("id");
Field own = Child.class.getDeclaredField("age");

System.out.println(inherited.getDeclaringClass() == Parent.class); // true
System.out.println(own.getDeclaringClass() == Child.class);        // true
```

Vì `id` được khai báo trong `Parent`, việc tra cứu qua `Child.class` **không thay đổi** declaring class của nó.

### 4.1. Phân biệt các khái niệm gần nhau

| Biểu thức | Hỏi điều gì? |
|---|---|
| `Child.class` | `Class` được chọn làm **điểm bắt đầu tra cứu** |
| `field.getDeclaringClass()` | Lớp/interface **khai báo chính field đó** |
| `field.getDeclaringClass().getName()` | Tên đầy đủ (binary name) của lớp khai báo |
| `field.getDeclaringClass().getSimpleName()` | Tên đơn giản, thuận tiện để hiển thị nhưng không bảo đảm duy nhất |
| `field.getClass()` | Kiểu runtime của **đối tượng Reflection metadata**, không phải lớp khai báo field |
| `field.getType()` | Kiểu dữ liệu của **trường**, không phải declaring class |

Không được dùng `field.getType()` để suy ra lớp chứa field. Ví dụ trong `class User { String name; }`: declaring class của `name` là `User`, còn raw type là `String`.

### 4.2. Kế thừa, field hiding và declaring class

Java cho phép lớp con khai báo field trùng tên với lớp cha (field hiding). Hai field vẫn là **hai thành viên khác nhau**, có declaring class khác nhau, ngay cả khi tên giống hệt:

```java
class Parent {
    public String code;
}

class Child extends Parent {
    public int code;
}
```

```java
Field parentCode = Parent.class.getDeclaredField("code");
Field childCode = Child.class.getDeclaredField("code");

System.out.println(parentCode.getName().equals(childCode.getName())); // true
System.out.println(parentCode.getDeclaringClass() == Parent.class);    // true
System.out.println(childCode.getDeclaringClass() == Child.class);      // true
System.out.println(parentCode.getType() == childCode.getType());      // false
```

Đừng định danh một field chỉ bằng tên nếu quét nhiều tầng kế thừa. Một khóa nhận diện hợp lý trong hệ thống phân tích có thể gồm **declaring class và field name** (cùng ngữ cảnh class loader khi cần), vì `Parent.code` và `Child.code` là hai trường khác nhau.

Khi quét hệ thống có nhiều class loader, tên lớp dạng chuỗi cũng không đủ để xác định chính xác danh tính `Class`. Hai class loader có thể nạp những kiểu có cùng tên nhị phân nhưng khác identity.

## 5. `Field.getType()`: raw type ở runtime

Chữ ký:

```java
Class<?> getType()
```

Kết quả luôn là một `Class<?>`, mô tả **kiểu của field tại runtime sau các quy tắc erasure của generic**. Với các trường không generic, đây chính là kiểu khai báo thông thường; với tham số generic, thông tin type arguments thường không hiện diện trong kết quả `getType()`.

Ví dụ:

```java
class Data {
    String title;
    int count;
    int[] scores;
    java.util.List<String> names;
    java.util.Map<String, Integer> ages;
}
```

| Field | `getType()` | `getType().getTypeName()` |
|---|---|---|
| `title` | `String.class` | `java.lang.String` |
| `count` | `int.class` | `int` |
| `scores` | `int[].class` | `int[]` |
| `names` | `List.class` | `java.util.List` |
| `ages` | `Map.class` | `java.util.Map` |

Do `List<String>` và `List<Integer>` cùng có raw type `List.class`, chỉ so sánh `getType()` **không phân biệt** chúng. Tương tự `Map<String, Integer>` và `Map<String, Long>` đều cho `Map.class`.

Với field khai báo bằng biến kiểu:

```java
class Holder<T extends Number> {
    T value;
    T[] values;
}
```

`getType()` của `value` là `Number.class` vì erasure của `T extends Number` dùng bound đầu tiên `Number`. Với `values`, erasure là `Number[]`. Nếu `T` không có bound cụ thể thì erasure thông thường là `Object`; khi có nhiều bounds, bound đầu tiên xác định erasure.

Một điểm thực tế: `getType()` trả `Class` có thể dùng với `isPrimitive()`, `isArray()`, `getComponentType()`, `isAssignableFrom()`. Tuy nhiên kiểm tra khả năng gán kiểu bằng `Class.isAssignableFrom` là một chủ đề khác với việc phục hồi generic type arguments.

## 6. `Field.getGenericType()`: kiểu đã khai báo, bao gồm signature generic

Chữ ký:

```java
Type getGenericType()
```

`java.lang.reflect.Type` là interface chung cho nhiều dạng mô tả kiểu trong Reflection. `getGenericType()` đọc thông tin kiểu theo khai báo generic (khi có metadata cần thiết), **không tự xác định loại đối tượng hiện đang được gán vào field**.

Các dạng thường gặp:

| Dạng `Type` | Ý nghĩa | Ví dụ field |
|---|---|---|
| `Class<?>` | Kiểu không tham số hóa / primitive / array kiểu thường | `String name`, `int age`, `String[] names` |
| `ParameterizedType` | Kiểu generic đã dùng với type arguments | `List<String> tags`, `Map<String, Integer> counts` |
| `TypeVariable<?>` | Biến kiểu được khai báo ở class/method/constructor | `T value` trong `class Box<T>` |
| `GenericArrayType` | Mảng có component type là kiểu tham số hóa hoặc biến kiểu | `T[] items`, `List<String>[] groups` |
| `WildcardType` | Wildcard thường xuất hiện như type argument trong `ParameterizedType` | `List<? extends Number> numbers`: wildcard là đối số bên trong `List` |

**Chú ý:** Với `List<? extends Number> numbers`, `field.getGenericType()` cho **`ParameterizedType`** của `List<...>`; bạn chỉ gặp `WildcardType` khi lấy phần tử của `getActualTypeArguments()`. Không ép trực tiếp kết quả `getGenericType()` sang `WildcardType` trong trường hợp này.

### 6.1. `ParameterizedType`

Ví dụ:

```java
Field field = Data.class.getDeclaredField("names");
Type type = field.getGenericType();

if (type instanceof ParameterizedType parameterized) {
    Type raw = parameterized.getRawType();
    Type[] arguments = parameterized.getActualTypeArguments();
    System.out.println(raw.getTypeName());
    System.out.println(arguments[0].getTypeName());
}
```

Trong ngữ cảnh `Data.names` là `List<String>` ở mục 5, đầu ra:

```text
java.util.List
java.lang.String
```

Đây là đoạn minh họa không độc lập: cần import `Field`, `Type`, `ParameterizedType` và định nghĩa `Data`. `ParameterizedType.getRawType()` trả một `Type`; trường hợp `List<String>` nó biểu diễn `java.util.List`. Tham số `String` nằm trong `getActualTypeArguments()`.

Với kiểu lồng nhau `Map<String, List<Integer>>`, type argument thứ hai của `Map` bản thân nó là một `ParameterizedType`. Muốn lấy tới `Integer` phải đi sâu từng cấp, **không chỉ đọc một level**.

### 6.2. `TypeVariable<?>`

Với `class Box<T extends Number> { T value; }`:

- `getType()` của `value` → `Number.class`.
- `getGenericType()` của `value` → `TypeVariable<?>` có tên `T`.
- `((TypeVariable<?>) type).getBounds()` cho danh sách upper bounds đã khai báo; ví dụ trên có `Number`.

Không nhầm thông tin bound với type argument cụ thể. Biết `T extends Number` chỉ bảo đảm `T` có ràng buộc upper bound; nó **không nói** một instance nào đó đang chứa `Integer` hay `Double`.

### 6.3. `GenericArrayType`

Ví dụ:

```java
class Bag<T extends Number> {
    T[] values;
    java.util.List<String>[] groups;
}
```

- `values`: `getType()` là `Number[].class` nhưng `getGenericType()` là `GenericArrayType` với generic component type là biến `T`.
- `groups`: `getType()` là `List[].class`, còn `getGenericType()` biểu diễn một mảng có component type `List<String>`, thường là `GenericArrayType`.
- `int[]` hay `String[]` thông thường dùng `Class<?>` ở `getGenericType()` vì component không phải biến kiểu/tham số hóa.

`GenericArrayType.getGenericComponentType()` giúp truy cập kiểu phần tử tổng quát. Không nên ép `getGenericType()` của mọi trường mảng sang `Class<?>`.

### 6.4. `WildcardType`

Với trường:

```java
java.util.List<? extends Number> numbers;
```

Ta lấy wildcard từ type argument của `List`:

```java
ParameterizedType listType =
        (ParameterizedType) field.getGenericType();
Type argument = listType.getActualTypeArguments()[0];

if (argument instanceof WildcardType wildcard) {
    Type[] uppers = wildcard.getUpperBounds();
    Type[] lowers = wildcard.getLowerBounds();
    // Ở đây upper bound là Number và không có lower bound tường minh.
}
```

Đoạn này cần `field` trỏ tới trường `numbers`, cùng các import Reflection; nó không phải chương trình độc lập. Với `List<? super Integer>`, `getLowerBounds()` chứa `Integer`; upper bound mặc định là `Object`. Với `List<?>`, upper bound mặc định là `Object` và lower bounds rỗng.

### 6.5. Không tự động “giải” biến kiểu qua lớp con

Xét:

```java
class Box<T> {
    public T item;
}

class StringBox extends Box<String> {
}
```

Khi gọi:

```java
Field inherited = StringBox.class.getField("item");
System.out.println(inherited.getDeclaringClass() == Box.class); // true
System.out.println(inherited.getGenericType().getTypeName());   // T
```

Metadata của field `item` **được khai báo tại `Box<T>`**, nên `getGenericType()` cho `T`, ngay cả khi tìm nó qua `StringBox.class`. Nó không tự biến thành `String.class`.

Muốn suy ra `T = String` ở ngữ cảnh subclass, cần phân tích quan hệ generic superclass/interface (`StringBox.class.getGenericSuperclass()` trả dạng có `Box<String>`), sau đó lập ánh xạ type variables sang type arguments và áp dụng xuyên qua các tầng kế thừa. Java Reflection cung cấp dữ liệu để làm điều đó nhưng **không có một thao tác `Field.getGenericType()` tự thực hiện toàn bộ phép ánh xạ theo runtime object**.

## 7. So sánh `getType()` và `getGenericType()` trong các tình huống

| Khai báo field | Kết quả `getType().getTypeName()` | Kết quả `getGenericType().getTypeName()` |
|---|---|---|
| `String name` | `java.lang.String` | `java.lang.String` |
| `int count` | `int` | `int` |
| `String[] names` | `java.lang.String[]` | `java.lang.String[]` |
| `List<String> tags` | `java.util.List` | `java.util.List<java.lang.String>` |
| `Map<String, Integer> scores` | `java.util.Map` | `java.util.Map<java.lang.String, java.lang.Integer>` |
| `T value`, trong `T extends Number` | `java.lang.Number` | `T` |
| `T[] values`, trong `T extends Number` | `java.lang.Number[]` | `T[]` |
| `List<? extends Number> ranks` | `java.util.List` | `java.util.List<? extends java.lang.Number>` |

Các chuỗi trên là cách hiển thị điển hình của `Type.getTypeName()` trên Java 17. Chúng rất hữu ích để quan sát/log, nhưng khi **phân tích cấu trúc kiểu** hãy dùng `instanceof` với `Class`, `ParameterizedType`, `TypeVariable`, `GenericArrayType` và `WildcardType`. Không phân tích generic bằng cách tự cắt chuỗi ở dấu `<`, dấu phẩy hoặc dấu `>`: cách đó sai với type lồng nhau, nested class và nhiều loại type khác.

Một `Type` generic có thể trỏ đến class không tải được hoặc metadata chữ ký không hợp lệ; khi đọc chữ ký generic, Reflection có thể ném những lỗi như `TypeNotPresentException`, `MalformedParameterizedTypeException` hoặc `GenericSignatureFormatError` trong bối cảnh metadata hỏng/thiếu. Với tool phân tích bytecode từ nguồn không hoàn toàn kiểm soát, nên xử lý những trường hợp này có chủ đích.

## 8. Chương trình Java 17+ chạy được: quan sát đủ bốn thuộc tính

Tạo file **`FieldMetadataDemo.java`** với nội dung:

```java
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.List;
import java.util.Map;

public class FieldMetadataDemo {
    static class Parent {
        public String inheritedCode;
        public String code;
    }

    static class Box<T extends Number> extends Parent {
        private String label;
        public T value;
        public T[] values;
        public List<String> tags;
        public Map<String, List<? extends Number>> scores;
        public int[] counts;
        public int code;
    }

    static class IntegerBox extends Box<Integer> {
    }

    public static void main(String[] args) throws NoSuchFieldException {
        Field tags = Box.class.getDeclaredField("tags");

        System.out.println("Name: " + tags.getName());
        System.out.println("Declaring: "
                + tags.getDeclaringClass().getSimpleName());
        System.out.println("Raw type: " + tags.getType().getTypeName());
        System.out.println("Generic type: "
                + tags.getGenericType().getTypeName());

        ParameterizedType tagsType =
                (ParameterizedType) tags.getGenericType();
        System.out.println("Tags argument: "
                + tagsType.getActualTypeArguments()[0].getTypeName());

        Field value = Box.class.getDeclaredField("value");
        Type valueGeneric = value.getGenericType();
        TypeVariable<?> variable = (TypeVariable<?>) valueGeneric;

        System.out.println("Value raw: " + value.getType().getTypeName());
        System.out.println("Value generic: " + valueGeneric.getTypeName());
        System.out.println("Value bound: "
                + variable.getBounds()[0].getTypeName());

        Field values = Box.class.getDeclaredField("values");
        Type valuesGeneric = values.getGenericType();
        GenericArrayType genericArray = (GenericArrayType) valuesGeneric;

        System.out.println("Values raw: "
                + values.getType().getTypeName());
        System.out.println("Values generic: " + valuesGeneric.getTypeName());
        System.out.println("Array component: "
                + genericArray.getGenericComponentType().getTypeName());

        Field scores = Box.class.getDeclaredField("scores");
        ParameterizedType scoresType =
                (ParameterizedType) scores.getGenericType();
        ParameterizedType nestedList = (ParameterizedType)
                scoresType.getActualTypeArguments()[1];
        WildcardType wildcard = (WildcardType)
                nestedList.getActualTypeArguments()[0];

        System.out.println("Scores raw: "
                + scores.getType().getTypeName());
        System.out.println("Scores generic: "
                + scores.getGenericType().getTypeName());
        System.out.println("Wildcard upper: "
                + wildcard.getUpperBounds()[0].getTypeName());

        Field inherited = IntegerBox.class.getField("inheritedCode");
        Field childCode = Box.class.getDeclaredField("code");
        Field parentCode = Parent.class.getDeclaredField("code");

        System.out.println("Inherited declaring: "
                + inherited.getDeclaringClass().getSimpleName());
        System.out.println("Hidden parent declaring: "
                + parentCode.getDeclaringClass().getSimpleName());
        System.out.println("Hidden child declaring: "
                + childCode.getDeclaringClass().getSimpleName());

        Field subclassValue = IntegerBox.class.getField("value");
        System.out.println("Subclass field generic: "
                + subclassValue.getGenericType().getTypeName());
        System.out.println("Subclass generic parent: "
                + IntegerBox.class.getGenericSuperclass().getTypeName());
    }
}
```

Chạy tại thư mục chứa file, sử dụng JDK 17 hoặc mới hơn:

```bash
javac FieldMetadataDemo.java
java FieldMetadataDemo
```

Kết quả chuẩn:

```text
Name: tags
Declaring: Box
Raw type: java.util.List
Generic type: java.util.List<java.lang.String>
Tags argument: java.lang.String
Value raw: java.lang.Number
Value generic: T
Value bound: java.lang.Number
Values raw: java.lang.Number[]
Values generic: T[]
Array component: T
Scores raw: java.util.Map
Scores generic: java.util.Map<java.lang.String, java.util.List<? extends java.lang.Number>>
Wildcard upper: java.lang.Number
Inherited declaring: Parent
Hidden parent declaring: Parent
Hidden child declaring: Box
Subclass field generic: T
Subclass generic parent: FieldMetadataDemo$Box<java.lang.Integer>
```

**Phân tích kết quả:**

1. `tags.getName()` trả tên đúng theo khai báo; `getDeclaringClass()` là `Box`, mặc dù field `tags` có kiểu `List`.
2. `tags.getType()` chỉ giữ `List`. `tags.getGenericType()` giữ type argument `String`, nên metadata được biểu diễn thành `ParameterizedType`.
3. Trường `value` khai báo `T extends Number` có raw type `Number`, nhưng generic type là biến `T`; `TypeVariable.getBounds()` cho `Number`.
4. Trường `values` khai báo `T[]` có raw type `Number[]` và generic type `T[]`. `GenericArrayType.getGenericComponentType()` cho `T`.
5. Trường `scores` có generic lồng nhau; để lấy wildcard bound phải xuống qua `Map` → `List` → wildcard. Type argument `? extends Number` có upper bound `Number`.
6. `IntegerBox.class.getField("inheritedCode")` trả field của `Parent`. Cùng tên `code` ở `Parent` và `Box` tạo thành **hai Field** với declaring class khác nhau.
7. `IntegerBox.class.getField("value")` trả Field khai báo tại `Box`, nên generic type vẫn là `T`. Phần generic superclass của `IntegerBox` có `Box<Integer>`, là metadata **khác** cần xét khi giải biến kiểu.

Trong dòng cuối, `IntegerBox.class.getGenericSuperclass().getTypeName()` hiển thị tên nhị phân của lớp lồng nhau `Box` dưới dạng `FieldMetadataDemo$Box<...>`. Ký hiệu `$` là phần của tên nhị phân của nested class, không phải lỗi định dạng.

Trong ví dụ, các nested classes được dùng để đặt tất cả type trong **một file duy nhất**. Các tên hiện ra qua `getSimpleName()` là `Box`/`Parent`; phân biệt với `getName()` của `Class`, vốn bao gồm tên bao quanh và ký hiệu `$` đối với nested classes.

## 9. Trình duyệt metadata đơn giản và ổn định thứ tự

Khi xây công cụ đọc schema, ta thường muốn liệt kê field theo tên, declaring class, raw type và generic type. Một helper minh họa:

```java
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Comparator;

public final class FieldPrinter {
    public static void printDeclaredFields(Class<?> type) {
        Field[] fields = type.getDeclaredFields();
        Arrays.sort(fields, Comparator.comparing(Field::getName));

        for (Field field : fields) {
            if (field.isSynthetic()) {
                continue;
            }

            System.out.println(
                    field.getDeclaringClass().getName() + "#"
                    + field.getName() + " : "
                    + field.getType().getTypeName() + " / "
                    + field.getGenericType().getTypeName());
        }
    }
}
```

Đoạn này là **lớp tiện ích** không có `main`, cần được gọi từ một chương trình khác, ví dụ `FieldPrinter.printDeclaredFields(SomeClass.class)`. Nó chỉ **đọc metadata** và không cần `setAccessible(true)` để lấy tên/kiểu.

Việc sắp xếp bằng `Comparator.comparing(Field::getName)` đảm bảo thứ tự ổn định **trong một danh sách field khai báo của cùng class**; nếu bạn gộp field từ nhiều class trong cùng hierarchy (có thể trùng tên), hãy bổ sung declaring class vào tiêu chí sắp xếp. Code in generic type bằng `getTypeName()` phục vụ hiển thị; một bộ phân tích thật nên kiểm tra cấu trúc `Type` như ở mục 6.

`getDeclaredFields()` có thể trả field private, protected hay package-private nhưng **chỉ liệt kê metadata**. Nếu tiếp tục đọc giá trị bằng `Field.get(receiver)`, quyền truy cập cần được kiểm tra riêng. Tìm được field không đồng nghĩa có quyền sử dụng nó.

## 10. Ứng dụng thực tế của bốn thuộc tính

### 10.1. JSON mapping / object serialization

Một mapper có thể lập schema như sau:

- `getName()`: tên mặc định của property, trước khi xét annotation/naming strategy.
- `getDeclaringClass()`: tìm ra field gốc trong hierarchy, phân biệt field bị che khuất.
- `getType()`: xử lý primitive, array, enum, collection/map ở mức class.
- `getGenericType()`: đọc `List<Order>`, `Map<String, Price>` hoặc biến kiểu để quyết định cách xử lý các phần tử.

Nếu bỏ `getGenericType()`, mapper chỉ thấy `List` và có thể mất thông tin loại phần tử **được khai báo**. Nhưng nếu field khai báo `T` trong một generic superclass, chỉ `getGenericType()` **chưa đủ** để suy ra `T` là gì trong một subclass cụ thể.

### 10.2. Dependency injection / framework scanning

Một framework có thể đọc field đã đánh dấu annotation, xác định raw type để tìm bean ứng viên và dùng generic type để giải quyết các injection points như `Repository<User>`. Việc field nằm trong superclass ảnh hưởng declaring class, annotation và thứ tự xử lý.

Không suy ra rằng hai field có chung raw type sẽ có cùng ý nghĩa injection: `Repository<User>` và `Repository<Order>` có cùng raw type `Repository` nhưng khác type argument.

### 10.3. API schema / validation

Một công cụ sinh mô tả dữ liệu có thể hiển thị tên và khai báo của field; nó cần phân biệt:

- `String` với `List<String>`;
- `List<? extends Number>` với `List<Number>`;
- `T extends Number` với một kiểu cụ thể `Integer`;
- field ở lớp cha với field bị lớp con che khuất.

Những metadata đó mô tả **ràng buộc và chữ ký khai báo**, không thể tự bảo đảm trạng thái/value runtime đang thỏa mọi điều kiện nếu framework còn cho phép dữ liệu raw/unchecked.

## 11. Những lỗi phổ biến

| Sai lầm | Vì sao sai? | Cách đúng |
|---|---|---|
| Dùng `getName()` để lấy giá trị String của field | `getName()` chỉ trả tên khai báo | Muốn đọc giá trị phải dùng `get(receiver)` và xử lý quyền |
| Dùng `getType()` để phân biệt `List<String>` và `List<Integer>` | Cả hai bị erasure về `List.class` | Phân tích `getGenericType()` |
| Ép mọi `getGenericType()` thành `ParameterizedType` | Primitive, class thường, type variable hoặc generic array có loại `Type` khác | Kiểm tra `instanceof` rồi xử lý theo nhánh |
| Cho rằng `getDeclaringClass()` luôn bằng class dùng để gọi `getField` | Field public có thể kế thừa | Đọc declaring class trên chính `Field` |
| Cho rằng field bị che khuất là cùng một field | Hai lớp khai báo hai thành viên khác nhau | Phân biệt bởi declaring class và tên |
| Cho rằng `getGenericType()` của field `T` trả `String` khi subclass là `Box<String>` | Metadata lưu chữ ký khai báo tại `Box<T>` | Phân tích generic superclass/interface và ánh xạ type variables |
| Đọc wildcard bằng cách cast trực tiếp `List<? extends Number>` sang `WildcardType` | Kiểu bên ngoài là `ParameterizedType` | Lấy wildcard từ `getActualTypeArguments()` |
| Dựa vào thứ tự `getDeclaredFields()` | API không hứa thứ tự cụ thể | Tự sắp xếp |
| Cố mở private access chỉ để đọc metadata | Metadata như tên, declaring class, type không đòi đọc giá trị | Chỉ xét quyền khi thật sự `get`/`set` |
| Coi tên lớp dạng chuỗi là identity toàn cục | Class loader khác nhau có thể tạo các `Class` khác nhau cùng tên | Sử dụng `Class<?>`/loader đúng ngữ cảnh khi cần |
| Cắt chuỗi `Type.getTypeName()` để “parse” generic | Generic nested/wildcard/array làm parsing thủ công dễ sai | Phân nhánh cấu trúc `Type` |

## 12. Checklist và câu hỏi tự kiểm tra

Checklist khi khảo sát một field:

1. Xác định **lớp tra cứu** bằng `SomeClass.class`, `instance.getClass()` hoặc một nguồn `Class<?>` hợp lệ.
2. Chọn `getDeclaredField(s)` nếu cần khai báo trực tiếp; chọn `getField(s)` nếu muốn truy vấn trường public có thể kế thừa.
3. Đọc `field.getName()` và `field.getDeclaringClass()` để xác định danh tính trường.
4. Đọc `field.getType()` cho kiểu erased ở dạng `Class<?>`.
5. Đọc `field.getGenericType()` cho khai báo generic, kiểm tra `instanceof` để phân tích cấu trúc `Type`.
6. Với field `T`, nếu cần type argument cụ thể của subclass, xây ánh xạ biến kiểu qua generic hierarchy.
7. Khi muốn đọc/ghi giá trị, xử lý quyền và receiver tách khỏi metadata; không mặc định `getDeclaredField` đã cấp quyền.
8. Với scanner, quyết định cách xử lý field synthetic, field bị hiding và thứ tự liệt kê.

Tự kiểm tra:

- Field khai báo `List<String> names`: `getType()` và `getGenericType()` khác nhau thế nào?
- `Child.class.getField("id")` tìm field do `Parent` khai báo: `getDeclaringClass()` trả về đâu?
- Trong `class Box<T extends Number> { T value; T[] values; }`, raw type của hai field là gì?
- `getGenericType()` trả loại `Type` nào cho `List<? extends Number>`? Lấy `WildcardType` ở đâu?
- Khi field `T` được khai báo trong `Box<T>`, vì sao tra qua `Box<String>` theo một subclass vẫn có thể thấy `T`?
- Vì sao field `Parent.code` và `Child.code` phải được coi là hai field riêng biệt?
- Khi một công cụ chỉ cần liệt kê tên/kiểu field private, có nhất thiết gọi `setAccessible(true)` không?

**Ghi nhớ:** `Field.getName()` là **tên**, `getDeclaringClass()` là **nơi khai báo**, `getType()` là **kiểu erased dưới dạng `Class`**, còn `getGenericType()` là **kiểu theo chữ ký khai báo dưới dạng `Type`**. Bốn câu trả lời phối hợp để hiểu cấu trúc field; không câu trả lời nào tự động cho biết giá trị của field hay cấp quyền truy cập vào giá trị đó.

Tài liệu tham khảo chuẩn Java 17: [`Field`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Field.html), [`Type`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Type.html), [`ParameterizedType`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/ParameterizedType.html), [`TypeVariable`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/TypeVariable.html), [`GenericArrayType`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/GenericArrayType.html), [`WildcardType`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/WildcardType.html).

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="fields-002">getFields khác getDeclaredFields khi có kế thừa</a>

<details>
<summary>Xem chi tiết</summary>

# getFields() khác getDeclaredFields() khi có kế thừa — Java Reflection

Khi đọc cấu trúc một lớp bằng Reflection, hai lời gọi `Class.getFields()` và `Class.getDeclaredFields()` đều trả về mảng `Field[]`, nhưng chúng trả lời **hai câu hỏi khác nhau**:

- **`getFields()`:** “Những trường `public` nào nằm trong phạm vi tra cứu thành viên công khai của lớp/interface này, bao gồm trường ở superclass và superinterface?”
- **`getDeclaredFields()`:** “Chính lớp/interface đang xét **khai báo trực tiếp** những trường nào, bất kể `public`, `protected`, package-private hay `private`?”

Vì vậy, `getDeclaredFields()` **không** phải phiên bản “đầy đủ hơn” của `getFields()` về kế thừa. Nó lấy được các trường không công khai *trong chính lớp*, nhưng **không tự duyệt trường của lớp cha**. Muốn phân tích toàn bộ chuỗi kế thừa, chương trình thường phải kết hợp `getDeclaredFields()` với `getSuperclass()`.

## 1. Bảng so sánh phạm vi tra cứu

| Tình huống | `SomeClass.class.getFields()` | `SomeClass.class.getDeclaredFields()` |
| --- | --- | --- |
| Trường `public` khai báo ngay ở `SomeClass` | Có | Có |
| Trường `private`, `protected`, package-private khai báo ngay tại `SomeClass` | Không | Có |
| Trường `public` ở superclass | Có | Không |
| Trường `private`, `protected`, package-private ở superclass | Không | Không |
| Hằng `public static final` khai báo ở interface mà lớp triển khai/kế thừa | Có trong phạm vi interface liên quan | Không nếu interface là nơi khai báo |
| Trường `static` | Có nếu `public` thuộc phạm vi tìm | Có nếu khai báo trực tiếp, bất kể mức truy cập |
| Trường tổng hợp (synthetic) do compiler sinh ra | Có thể xuất hiện nếu thỏa điều kiện `public`/phạm vi | Có thể xuất hiện nếu được khai báo trong bytecode của chính lớp |
| Thứ tự các phần tử trong mảng | Không được bảo đảm | Không được bảo đảm |

**Điểm quan trọng:** “Declared” là **nơi khai báo**, không có nghĩa là “chỉ các trường được phép truy cập”, và “Fields” không có nghĩa là “mọi trường”. Nếu lớp con không có trường `public` nào, `getFields()` vẫn có thể trả trường `public` từ lớp cha hoặc interface.

Tài liệu chuẩn Java 17:
- `Class.getFields()`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html#getFields()
- `Class.getDeclaredFields()`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html#getDeclaredFields()

## 2. Cơ chế khi lớp con kế thừa lớp cha

Xét quan hệ:

```text
                 interface Labeled
                 + CATEGORY: String (public static final)
                         ^
                         | implements
class Base              |
+ name: String (public) |
+ version: int (public static)
+ level: int (protected)
+ token: long (private)
       ^                 |
       | extends         |
       +------ class Child
               + name: String (public, che Base.name)
               + active: boolean (public)
               + note: String (private)
```

Với `Child.class`:

- `getFields()` tìm các trường `public` của `Child`, những trường `public` từ `Base` và trường công khai của `Labeled`. Nó không lấy `Base.level`, `Base.token` hay `Child.note`.
- `getDeclaredFields()` chỉ lấy ba trường do `Child` khai báo: `name`, `active`, `note`. Nó không lấy `Base.name`, `Base.version` hoặc `Labeled.CATEGORY`.
- `Base.class.getDeclaredFields()` trả bốn trường riêng của `Base` ở mọi mức truy cập; nó không bị ảnh hưởng bởi việc `Child` có che tên trường hay không.

Ở đây có **field hiding**: `Child` cũng khai báo `name` nên tên trường đó che `Base.name` khi tham chiếu bằng cú pháp thông thường. Trong Reflection, mỗi đối tượng `Field` vẫn gắn với **lớp khai báo thật** (qua `getDeclaringClass()`). Do đó, một danh sách `getFields()` có thể chứa **nhiều `Field` trùng tên** nếu chúng thuộc các lớp khác nhau.

## 3. Ví dụ Java 17+ chạy độc lập và kết quả chính xác

Lưu đoạn sau vào tệp `FieldInheritanceDemo.java`. Các class phụ trợ là kiểu top-level cùng package, giúp ví dụ minh họa quan hệ kế thừa, không phụ thuộc vào các field riêng của enclosing class.

```java
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.stream.Collectors;

public class FieldInheritanceDemo {
    private static String names(Field[] fields) {
        return Arrays.stream(fields)
                .map(f -> f.getDeclaringClass().getSimpleName()
                        + "." + f.getName())
                .sorted()
                .collect(Collectors.joining(", "));
    }

    public static void main(String[] args) throws Exception {
        Class<Child> type = Child.class;

        System.out.println("getFields=" + names(type.getFields()));
        System.out.println(
                "getDeclaredFields=" + names(type.getDeclaredFields()));
        System.out.println(
                "baseDeclared=" + names(Base.class.getDeclaredFields()));

        Field selectedName = type.getField("name");
        System.out.println("selectedName.owner="
                + selectedName.getDeclaringClass().getSimpleName());

        Field parentName = Base.class.getDeclaredField("name");
        Child child = new Child();
        System.out.println("parentName.value=" + parentName.get(child));
        System.out.println("childName.value=" + selectedName.get(child));

        long publicNameCount = Arrays.stream(type.getFields())
                .filter(f -> f.getName().equals("name"))
                .count();
        System.out.println("publicNameCount=" + publicNameCount);

        Field category = type.getField("CATEGORY");
        System.out.println("interfaceField.owner="
                + category.getDeclaringClass().getSimpleName());
        System.out.println("interfaceField.value=" + category.get(null));

        try {
            type.getDeclaredField("level");
        } catch (NoSuchFieldException e) {
            System.out.println("childDeclaredLevel=NoSuchFieldException");
        }

        System.out.println("parentProtected.exists="
                + Arrays.stream(type.getFields())
                        .anyMatch(f -> f.getName().equals("level")));
    }
}

interface Labeled {
    String CATEGORY = "tagged";
}

class Base {
    public String name = "base";
    public static int version = 1;
    protected int level = 2;
    private long token = 3;
}

class Child extends Base implements Labeled {
    public String name = "child";
    public boolean active = true;
    private String note = "private";
}
```

Lệnh biên dịch/chạy:

```bash
javac FieldInheritanceDemo.java
java FieldInheritanceDemo
```

Đầu ra (đã chủ động sắp xếp kết quả `Field[]` bằng `sorted()` để không dựa vào thứ tự Reflection trả về):

```text
getFields=Base.name, Base.version, Child.active, Child.name, Labeled.CATEGORY
getDeclaredFields=Child.active, Child.name, Child.note
baseDeclared=Base.level, Base.name, Base.token, Base.version
selectedName.owner=Child
parentName.value=base
childName.value=child
publicNameCount=2
interfaceField.owner=Labeled
interfaceField.value=tagged
childDeclaredLevel=NoSuchFieldException
parentProtected.exists=false
```

Giải thích những điểm dễ sai:

1. `getFields()` bao gồm hai trường `public` cùng tên `name`: `Base.name` và `Child.name`. Vì vậy, **không nên xây map chỉ với key là tên** nếu cần bảo toàn mọi trường trong cây kế thừa.
2. `getField("name")` trả trường `Child.name` do lớp đang xét tự khai báo `public`. Trong ví dụ này, `getDeclaringClass()` là `Child.class`.
3. Vẫn có thể lấy riêng `Base.name` bằng `Base.class.getDeclaredField("name")`. Khi đọc nó từ một đối tượng `Child`, giá trị là `"base"`; trường của `Child` có giá trị riêng `"child"`. Field hiding **không ghi đè cùng một ô nhớ** theo cơ chế đa hình của phương thức.
4. `Labeled.CATEGORY` là trường interface. Theo Java, trường khai báo trong interface mặc định là `public static final`, và `getField("CATEGORY")` có thể tìm thấy nó từ `Child.class`. Vì là `static`, lời gọi `Field.get(null)` đọc giá trị trường.
5. `Base.level` là `protected` và `Base.token` là `private`, nên không xuất hiện trong `Child.class.getFields()`; cả hai cũng không thuộc `Child.class.getDeclaredFields()`, vì được khai báo tại `Base`.

## 4. getField(name) và getDeclaredField(name) liên hệ thế nào?

Hai API dạng số ít có phạm vi tương tự:

| API | Điều kiện để tìm thấy | Không tìm thấy |
| --- | --- | --- |
| `getField(String name)` | Trường `public` có tên khớp, tìm theo quy tắc class/superinterface/superclass | `NoSuchFieldException` |
| `getDeclaredField(String name)` | Trường **khai báo trực tiếp** ở `Class` hiện tại, bất kỳ access modifier | `NoSuchFieldException` |

Ví dụ:

```java
Child.class.getField("active");           // Child.active, public
Child.class.getField("version");          // Base.version, public
Child.class.getField("CATEGORY");         // Labeled.CATEGORY, public
Child.class.getDeclaredField("note");     // Child.note, private
Base.class.getDeclaredField("level");     // Base.level, protected
Base.class.getDeclaredField("token");     // Base.token, private

// Hai lời gọi dưới đây ném NoSuchFieldException:
Child.class.getDeclaredField("version");  // Khai báo trong Base
Child.class.getField("level");            // Không phải public
```

Đây là đoạn **minh họa đặt trong ngữ cảnh có các class đã khai báo phía trên**, không phải chương trình chạy độc lập; các phương thức số ít cũng có thể yêu cầu xử lý `NoSuchFieldException`.

### Khi nhiều trường public trùng tên

`getField(name)` **không** trả toàn bộ các field trùng tên. Thuật toán tra cứu ưu tiên trường `public` khai báo ngay trong class/interface đang xét; nếu không có, nó tìm trong superinterface theo thứ tự khai báo rồi mới xét superclass. Kết quả là **một `Field`** theo thuật toán của JDK, không phải danh sách đầy đủ. Vì thế:

- Cần tra cứu một member public theo cách Java định nghĩa → dùng `getField("name")`.
- Cần xem **mọi** public field trong cây kế thừa, kể cả trường bị che tên → duyệt `getFields()` và xét `getDeclaringClass()`.
- Cần field cụ thể được khai báo ở superclass → gọi `SuperClass.class.getDeclaredField("name")` (hoặc duyệt hierarchy), không phụ thuộc vào việc lớp con cũng có trường cùng tên.

Đừng lấy phần tử đầu tiên từ mảng `getFields()` rồi cho rằng đó là trường lớp con. **Thứ tự các phần tử không được bảo đảm**; dùng `getField` với thuật toán tra cứu rõ ràng hoặc tự chọn bằng `getDeclaringClass()`.

## 5. Cách thu thập tất cả trường khai báo trong chuỗi superclass

Không có một lời gọi `getDeclaredFields()` duy nhất tự tìm tất cả trường non-public ở mọi lớp cha. Với trường hợp cần inspect toàn bộ state (ví dụ serializer hoặc công cụ debug), có thể duyệt `getSuperclass()`:

```java
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public class HierarchyFieldsExample {
    static List<Field> declaredFieldsInClassHierarchy(Class<?> type) {
        List<Field> result = new ArrayList<>();
        for (Class<?> current = type;
             current != null;
             current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                result.add(field);
            }
        }
        return result;
    }
}
```

Đây là **mã helper minh họa**, không có `main` để tự chạy. Với `Child.class` ở ví dụ trước, logic trên thu thập trường do `Child` khai báo rồi tới `Base` và `Object`; `Object` không có instance field khai báo cho ví dụ. Cách này giữ các trường trùng tên vì mỗi `Field` mang declaring class riêng.

**Giới hạn cần nhớ:**

- `getSuperclass()` chỉ duyệt **superclass chain**, không duyệt các superinterface. Nếu cần thêm trường interface, phải duyệt `getInterfaces()` và các superinterface một cách riêng biệt, đồng thời chống lặp theo identity khi đi qua đồ thị interface.
- Kết quả có thể gồm cả `static`, `final`, compiler-generated/synthetic field. Lọc theo nhu cầu thay vì mặc định mọi field đều là state instance.
- Thứ tự từng mảng `getDeclaredFields()` không được bảo đảm. Nếu cần kết quả tái lập được, hãy sắp xếp bằng khóa rõ ràng, thường gồm declaring class và tên; khi cần phân biệt chính xác hơn, thêm descriptor/type.
- `getDeclaredFields()` chỉ trả metadata. Việc đọc/ghi giá trị ở field non-public còn chịu quy tắc truy cập, giới hạn module và `AccessibleObject`.

### Ví dụ lọc trường instance hoặc synthetic

```java
for (Field field : declaredFieldsInClassHierarchy(Child.class)) {
    boolean isInstanceField = !java.lang.reflect.Modifier.isStatic(
            field.getModifiers());
    if (isInstanceField && !field.isSynthetic()) {
        // Xử lý metadata: tên, kiểu, lớp khai báo...
    }
}
```

Đây là đoạn minh họa được đặt cạnh helper `declaredFieldsInClassHierarchy`, không phải chương trình chạy độc lập. Bộ lọc trên không tự quyết định một trường có phù hợp serialize hay không; trong thực tế còn phải xem `transient`, annotation, visibility, quy tắc nghiệp vụ và trạng thái truy cập.

## 6. Trường interface, field hiding và trường được kế thừa

### Interface fields có thể xuất hiện dù lớp con không khai báo

Mọi field khai báo trong interface là `public static final`. Nếu `Child implements Labeled`, `Child.class.getFields()` bao gồm `Labeled.CATEGORY` theo phạm vi thành viên công khai. Tuy nhiên, `Child.class.getDeclaredFields()` **không** bao gồm field đó. `Labeled.class.getDeclaredFields()` mới trả field này với `getDeclaringClass() == Labeled.class`.

Nếu nhiều interface khai báo trường cùng tên, tra cứu `getField(name)` tuân theo thứ tự superinterface của thuật toán API; đừng lấy đó làm phép phát hiện “không có xung đột” của toàn bộ cây interface. Khi cần kiểm toán, duyệt `getFields()` cùng declaring class.

### Field hiding khác method overriding

Trường trong Java **không override** theo dispatch động như instance method. Nếu `Base` và `Child` cùng khai báo `public String name`, đó là **hai trường riêng**. `Field.get(child)` đọc trường mà chính đối tượng `Field` biểu diễn, dù receiver thực tế là instance của `Child`. Do đó một `Field` trỏ tới `Base.name` đọc giá trị `Base.name`.

Để phân biệt các field trùng tên, dùng `field.getDeclaringClass()`, `field.getType()`, `field.getModifiers()`. Tên đơn thuần không đủ định danh field khi có hiding.

### “Inherited” và phạm vi Reflection

Trong Java Language Specification, một field có bị che hay không được kế thừa như thế nào là khái niệm ngữ nghĩa ngôn ngữ. `Class.getFields()` mô tả phạm vi **public field của class, superclass và superinterface** và có thể cho thấy cả field bị che tên. Vì thế đừng suy diễn rằng “chỉ có thể gọi được `obj.name` một cách đơn giản” đồng nghĩa với “Reflection chỉ tìm thấy một Field tên `name`”.

## 7. Truy cập giá trị và giới hạn Java module

Tìm thấy `Field` không có nghĩa là đọc hoặc sửa giá trị được. Chẳng hạn `Base.class.getDeclaredField("token")` trả metadata của trường `private`, nhưng `field.get(object)` vẫn kiểm tra quyền truy cập và có thể ném `IllegalAccessException`. Khi cần deep reflection, `trySetAccessible()` hoặc `setAccessible(true)` chỉ có thể bật cơ chế bỏ qua kiểm tra nếu quy tắc truy cập và module cho phép. Package của named module không mở có thể chặn thao tác với `InaccessibleObjectException` hoặc làm `trySetAccessible()` trả `false`.

Đối với field `final`, đừng mặc định bật accessible flag là ghi được hoặc việc ghi luôn an toàn. Một số final field như `static final`, field final của record hoặc hidden class không thể ghi qua Reflection; việc sửa các final field khác cũng có giới hạn về tính nhất quán và tối ưu hóa.

Các công cụ cần duyệt field nên tách rõ hai giai đoạn: **thu thập metadata** bằng `getFields`/`getDeclaredFields` trước, sau đó **xác định quyền truy cập thực tế** nếu cần đọc/ghi.

## 8. Lỗi phổ biến và cách lựa chọn API

| Nhầm lẫn | Cách hiểu đúng |
| --- | --- |
| `getDeclaredFields()` lấy mọi field kể cả trong superclass | Chỉ lấy field do chính class/interface khai báo |
| `getFields()` lấy mọi field trong cây kế thừa | Chỉ lấy field `public` trong phạm vi class/superclass/superinterface |
| `getFields()` không trả field bị che tên | Có thể trả nhiều `Field` cùng tên nhưng khác declaring class |
| `getFields()[0]` luôn là field của lớp con | Thứ tự các phần tử không được bảo đảm |
| `getField("name")` và `getDeclaredField("name")` tương đương | Một API tra cứu public member, một API tra cứu khai báo trực tiếp bất kể visibility |
| `getDeclaredField` của lớp con tìm được private field lớp cha | Phải lấy `Class` lớp cha hoặc duyệt superclass |
| Có `Field` tức là có quyền `get`/`set` | Metadata và quyền truy cập là hai bước khác nhau |
| Mọi trường interface được sao chép thành field khai báo của class triển khai | Interface field giữ declaring class là interface |

**Quy tắc chọn nhanh:** cần tập field **public của lớp cùng kế thừa/interface** thì dùng `getFields()`; cần tập field **khai báo ngay trong một class bất kể mức truy cập** thì dùng `getDeclaredFields()`; cần cả field `private` hoặc `protected` ở các superclass thì **tự duyệt hierarchy và gọi `getDeclaredFields()` trên từng lớp**. Khi có tên trùng, luôn gắn field với `getDeclaringClass()` để tránh nhầm.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="fields-003">Field hiding và nhiều field trùng tên trong phân cấp</a>

<details>
<summary>Xem chi tiết</summary>

# Field hiding và nhiều field trùng tên trong phân cấp lớp Java

## 1. Field hiding là gì?

Trong Java, một lớp con có thể **khai báo field cùng tên** với field của lớp cha. Field ở lớp con *hide* (che khuất) field cùng tên của lớp cha khi tra cứu bằng cú pháp Java thông thường. Đây là **field hiding**, không phải ghi đè (*overriding*) như với phương thức instance.

```java
class Parent {
    public int count = 10;
}
class Child extends Parent {
    public String count = "ten";
}
```

Hai field `Parent.count` và `Child.count` cùng tồn tại, dù **cùng tên nhưng khác kiểu**. Cú pháp khai báo này hợp lệ vì chúng nằm trong **hai lớp khác nhau**. Ngược lại, khai báo hai field cùng tên ngay trong *một lớp* là lỗi biên dịch. Field ở lớp con có thể cùng hoặc khác kiểu; việc hiding cũng không phụ thuộc vào field là `static` hay instance.

Với một đối tượng `Child child = new Child()`:

- `child.count` được phân giải theo kiểu tĩnh `Child`, nên tham chiếu tới `Child.count` (giá trị `"ten"`).
- `((Parent) child).count` tham chiếu tới `Parent.count` (giá trị `10`).
- Việc ép kiểu **không tạo đối tượng mới**, mà khiến trình biên dịch chọn field theo kiểu của biểu thức truy cập.
- Trong phương thức của `Child`, có thể dùng `super.count` để truy cập field của `Parent`.

Do đó, một đối tượng con có thể chứa **nhiều vùng lưu trữ field khác nhau có cùng tên** trên chuỗi kế thừa. Field hiding không xóa field cha, không hợp nhất hai field và không tạo cơ chế dispatch động theo runtime class.

## 2. Reflection tra cứu field theo phạm vi nào?

| API | Có tìm field kế thừa? | Có gồm field không `public`? | Khi trùng tên |
| --- | --- | --- | --- |
| `type.getDeclaredField(name)` | Không | Có, nếu field khai báo trực tiếp trong `type` | Trả về đúng field của `type`; nếu lớp đó không khai báo, ném `NoSuchFieldException` |
| `type.getDeclaredFields()` | Không | Có, từ `public` đến `private` | Trả về tất cả field được khai báo trực tiếp, không có thứ tự đảm bảo |
| `type.getField(name)` | Có, chỉ member field `public` | Không | Chọn **một** field theo thuật toán tìm kiếm công khai của `Class` |
| `type.getFields()` | Có, gồm field `public` từ lớp cha và interface | Không | Có thể chứa **nhiều field cùng tên**, kể cả field bị hide; thứ tự không đảm bảo |

**Điểm quan trọng:** `getFields()` trả về một mảng các `Field`, không phải ánh xạ `name -> Field` duy nhất. Không được giả định tên là khóa không trùng hoặc phần tử đầu tiên là field cần dùng.

### Thuật toán `Class.getField("name")`

Với kiểu `C`, theo tài liệu `Class` của Java SE 17:

1. Nếu `C` **trực tiếp khai báo field `public`** với tên đó, trả về field ấy.
2. Nếu chưa tìm được, tra đệ quy từng **superinterface trực tiếp** của `C`, theo thứ tự interface đã khai báo.
3. Nếu vẫn chưa tìm được, tra đệ quy **superclass** của `C`; nếu không còn ứng viên, ném `NoSuchFieldException`.

Vì vậy, khi một lớp con có field `private` tên `marker` còn lớp cha có field `public` cũng tên `marker`, `Child.class.getField("marker")` có thể **trả field `public` của cha**: bước đầu bỏ qua field con không `public` và tiếp tục tìm kiếm. Đừng diễn giải `getField` đơn giản là luôn chọn field gần lớp con nhất bất kể modifier.

Đặc biệt, trong tình huống **interface và superclass đều cung cấp field công khai cùng tên**, việc tra superinterface diễn ra **trước** superclass khi lớp hiện tại không trực tiếp khai báo field `public` phù hợp.

## 3. Ví dụ Java 17+ chạy được: ba field `value` trên một đối tượng

Lưu nguyên chương trình dưới tên **`FieldHidingDemo.java`**:

```java
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;

public class FieldHidingDemo {
    public static class Root {
        public int value = 10;
        public String marker = "root";
    }

    public static class Mid extends Root {
        public String value = "mid";
        private String marker = "mid-private";
    }

    public static class Leaf extends Mid {
        public long value = 30L;
    }

    public interface First {
        int LIMIT = 100;
    }

    public interface Second {
        int LIMIT = 200;
    }

    public static class Settings implements First, Second {
    }

    public static void main(String[] args) throws Exception {
        Leaf leaf = new Leaf();

        System.out.println("Leaf expression: " + leaf.value);
        System.out.println("Mid expression: " + ((Mid) leaf).value);
        System.out.println("Root expression: " + ((Root) leaf).value);

        Field selected = Leaf.class.getField("value");
        System.out.println("getField(value): "
                + selected.getDeclaringClass().getSimpleName());

        for (Class<?> c = Leaf.class; c != Object.class; c = c.getSuperclass()) {
            Field f = c.getDeclaredField("value");
            System.out.println(c.getSimpleName() + ".value = " + f.get(leaf)
                    + " (" + f.getType().getSimpleName() + ")");
        }

        String[] valueOwners = Arrays.stream(Leaf.class.getFields())
                .filter(f -> f.getName().equals("value"))
                .map(f -> f.getDeclaringClass().getSimpleName())
                .sorted()
                .toArray(String[]::new);
        System.out.println("getFields(value) owners: "
                + String.join(", ", valueOwners));

        Field marker = Leaf.class.getField("marker");
        System.out.println("getField(marker): "
                + marker.getDeclaringClass().getSimpleName()
                + "=" + marker.get(leaf));

        System.out.println("Mid marker private: " + Modifier.isPrivate(
                Mid.class.getDeclaredField("marker").getModifiers()));

        String[] limitOwners = Arrays.stream(Settings.class.getFields())
                .filter(f -> f.getName().equals("LIMIT"))
                .map(f -> f.getDeclaringClass().getSimpleName())
                .sorted()
                .toArray(String[]::new);
        System.out.println("getFields(LIMIT) owners: "
                + String.join(", ", limitOwners));
        System.out.println("getField(LIMIT): "
                + Settings.class.getField("LIMIT")
                        .getDeclaringClass().getSimpleName());
        System.out.println("First.LIMIT via reflection: "
                + First.class.getField("LIMIT").getInt(null));
    }
}
```

Biên dịch và chạy trong thư mục chứa tệp:

```bash
javac FieldHidingDemo.java
java FieldHidingDemo
```

Stdout (đã kiểm chứng thứ tự dòng và giá trị trên Java):

```text
Leaf expression: 30
Mid expression: mid
Root expression: 10
getField(value): Leaf
Leaf.value = 30 (long)
Mid.value = mid (String)
Root.value = 10 (int)
getFields(value) owners: Leaf, Mid, Root
getField(marker): Root=root
Mid marker private: true
getFields(LIMIT) owners: First, Second
getField(LIMIT): First
First.LIMIT via reflection: 100
```

Các kết quả cần phân biệt:

1. **Cú pháp Java phân giải theo kiểu tĩnh:** `leaf.value`, `((Mid) leaf).value` và `((Root) leaf).value` đều truy cập cùng một đối tượng nhưng tới **ba field độc lập**.
2. **`getField("value")` trả đúng một `Field`:** `Leaf.value` là field `public` khai báo trực tiếp trên lớp đang tra cứu nên được chọn trước.
3. **`getDeclaredField("value")` theo từng lớp:** Khi gọi riêng trên `Leaf.class`, `Mid.class`, `Root.class`, ta lấy được chính xác cả ba field. `Field.get(leaf)` sử dụng field của **declaring class** đã chọn, không tự chuyển sang field của `Leaf`.
4. **`getFields()` có nhiều tên `value`:** mảng chứa cả ba field công khai dù bị hide. Code chủ động `sorted()` theo tên lớp để stdout ổn định; API không cam kết thứ tự trả về.
5. **`getField("marker")` vẫn tìm tới `Root`:** `Mid.marker` là `private` và được tìm thấy bằng `Mid.class.getDeclaredField("marker")`; còn `getField` tìm trường `public` kế thừa của `Root`.
6. **Hai interface khai báo `LIMIT`:** `Settings.class.getFields()` chứa `First.LIMIT` lẫn `Second.LIMIT`. `getField("LIMIT")` chọn `First` vì `First` là superinterface đầu tiên trong khai báo `implements First, Second`. Để lấy đúng một hằng, hãy truy cập `First.class.getField("LIMIT")` hoặc `Second.class.getField("LIMIT")`.

Cả `Root`, `Mid` và `Leaf` trong ví dụ đều là lớp `public static` lồng trong `FieldHidingDemo` để có thể chạy độc lập từ một tệp và tránh các chi tiết quyền truy cập lớp không liên quan.

## 4. Tại sao `Field.get()` không tự đọc field của lớp con?

Đối tượng `Field` đại diện cho **một khai báo cụ thể**. Ba thông tin đặc biệt hữu ích là:

- `field.getName()`: tên đơn của field, ví dụ `"value"`.
- `field.getDeclaringClass()`: lớp/interface **thực sự khai báo**, ví dụ `Root.class`.
- `field.getType()`: kiểu khai báo, ví dụ `int.class`, `String.class`, `long.class`.

Ví dụ, `Root.class.getDeclaredField("value").get(leaf)` đọc slot `Root.value` của đối tượng `leaf`, cho kết quả `Integer` mang giá trị `10` (phương thức `Field.get` boxing giá trị primitive trong kết quả `Object`). `Leaf.class.getDeclaredField("value").get(leaf)` đọc slot `Leaf.value`, cho `Long` giá trị `30`.

Đối tượng truyền vào `Field.get(receiver)` cho field instance phải thuộc lớp khai báo field hoặc lớp con phù hợp. Với field `static`, truyền `null` là hợp lệ. `Field` không thực hiện **virtual dispatch** như lời gọi phương thức instance được override.

Do đó, để định danh một field trong dữ liệu Reflection, **không chỉ lưu `name`**. Hãy giữ ít nhất `declaringClass` và `name`; kiểm tra thêm `type` và `modifiers` khi xử lý schema hoặc ánh xạ dữ liệu.

## 5. Cách thu thập *tất cả* field trùng tên trong chuỗi kế thừa

Khi muốn lấy field ở **mọi cấp lớp**, kể cả `private` và `protected`, duyệt lần lượt `getSuperclass()` và gọi `getDeclaredField(name)` trên từng cấp:

```java
// Đoạn hàm dùng trong một lớp Java; không phải chương trình độc lập.
static java.util.List<java.lang.reflect.Field> findAllInClassHierarchy(
        Class<?> start, String name) {
    java.util.List<java.lang.reflect.Field> result =
            new java.util.ArrayList<>();

    for (Class<?> c = start; c != null; c = c.getSuperclass()) {
        try {
            result.add(c.getDeclaredField(name));
        } catch (NoSuchFieldException ignored) {
            // Cấp lớp này không khai báo field; vẫn duyệt tiếp superclass.
        }
    }
    return result; // Từ lớp đang xét tới các superclass.
}
```

Ví dụ dùng `findAllInClassHierarchy(Leaf.class, "value")` sẽ cho ba `Field` khai báo ở `Leaf`, `Mid`, `Root`, theo thứ tự đó. Hàm này **chỉ duyệt class và superclass**, không đệ quy các interface; nếu cần tất cả hằng/field từ interface, phải khảo sát riêng `getInterfaces()` (bao gồm cả interface cha nếu cần) hoặc chọn `getFields()` khi chỉ quan tâm `public`.

Một số lưu ý triển khai:

- Không dùng `getDeclaredFields()` của một lớp con rồi kỳ vọng có field của lớp cha.
- Nếu cần hiển thị danh sách, tự sắp xếp theo **cấp lớp** hoặc `declaringClass + name` vì thứ tự trong mảng `getFields()`/`getDeclaredFields()` không được đảm bảo.
- Không dùng `Map<String, Field>` nếu cần giữ mọi field trùng tên: field sau có thể ghi đè field trước. Dùng `List<Field>` hoặc một khóa có `declaringClass`.
- Khi mapping serialization/ORM/custom injection, phải đặt quy tắc rõ ràng: chọn field khai báo gần nhất, chọn field ở lớp cụ thể, hay báo lỗi nếu nhiều field cùng tên. Không coi việc mảng tình cờ trả về field nào trước là hợp đồng API.
- Với field không công khai, **tra cứu metadata** khác với **quyền đọc/ghi**: `getDeclaredField` có thể thành công nhưng `Field.get`/`set` vẫn phải tuân thủ access check; `trySetAccessible()` cũng chịu giới hạn module `opens`.

## 6. Field hiding khác method overriding

| Tiêu chí | Field cùng tên trong cha/con | Method instance được override |
| --- | --- | --- |
| Quan hệ | Hiding giữa các khai báo field | Overriding nếu đáp ứng quy tắc chữ ký |
| Cách chọn qua cú pháp Java | Theo kiểu tĩnh của biểu thức chứa field | Dispatch động theo lớp runtime của đối tượng |
| Dùng cast về cha | Chọn field khai báo/tồn tại theo kiểu cha | Vẫn gọi override của con nếu method thực sự được override |
| Reflection | `Field` trỏ đến một khai báo; `Field.get` đọc khai báo đó | `Method.invoke` của method instance có thể thực thi override tương ứng |
| Khác kiểu / `static` | Field con có thể khác kiểu, và hiding không phân biệt static/instance | Method có điều kiện override và quy tắc static hiding riêng |

```java
// Mảnh mã minh họa; đặt trong một chương trình Java thích hợp.
class A {
    int number = 1;
    String describe() { return "A"; }
}
class B extends A {
    int number = 2;
    @Override String describe() { return "B"; }
}
// A ref = new B();
// ref.number     -> 1 (field A)
// ref.describe() -> "B" (method override ở B)
```

Không suy luận từ method dispatch rằng một `Field` được lấy ở cha sẽ tự tham chiếu tới field cùng tên trong con.

## 7. Các trường hợp dễ nhầm

### 7.1. Field `private` ở lớp con và field `public` ở lớp cha

Lớp con có thể khai báo field `private` cùng tên với field `public` ở lớp cha. `getDeclaredField` trên lớp con tìm field `private`; `getField` trên lớp con có thể đi tiếp đến field `public` ở cha như ví dụ `marker`. Việc `getField` tìm được một field công khai **không có nghĩa** lớp con không có field khác cùng tên.

### 7.2. Hai interface có hằng cùng tên

Các field khai báo trong interface mặc định là `public static final`. Nếu lớp implement `First` và `Second` cùng có `LIMIT`, biểu thức không đủ điều kiện như `Settings.LIMIT` có thể **mơ hồ ở bước biên dịch**. Reflection `Settings.class.getField("LIMIT")` lại được định nghĩa theo thuật toán tra cứu của `Class` và trong ví dụ chọn `First`. Đây là hai quy tắc khác nhau; tốt nhất định danh rõ `First.LIMIT` hoặc `Second.LIMIT`.

### 7.3. Field cùng tên nhưng khác kiểu

`int value`, `String value`, `long value` ở ba cấp lớp là ba khai báo độc lập. Trong phạm vi **một lớp**, Java không cho overload field theo kiểu như overload method. Khi đọc qua Reflection, dùng `getType()` trước khi ép kiểu giá trị trả về từ `Field.get()`.

### 7.4. Field static và instance

Hiding của field không đòi hỏi cả hai field đều là `static` hoặc đều là instance. Một lớp con có thể khai báo field trùng tên với field `static` của cha. Truy cập field `static` qua Reflection bằng `get(null)`; với field instance, truyền đối tượng phù hợp. Tên giống nhau không biến field thành polymorphic.

### 7.5. Có metadata nhưng không đọc được giá trị

`getDeclaredField` có thể tìm được field `private` nhưng `get(receiver)` không truy cập được từ caller ngoài phạm vi hợp lệ và ném `IllegalAccessException`. Trong named modules, deep reflection còn bị giới hạn bởi `opens`; `setAccessible(true)` có thể ném `InaccessibleObjectException`, còn `trySetAccessible()` có thể trả `false`. Không dùng thao tác mở quyền như điều kiện mặc định khi chỉ cần liệt kê metadata.

## 8. Các lỗi thường gặp và hướng xử lý

| Hiện tượng | Nguyên nhân | Cách xử lý |
| --- | --- | --- |
| `Leaf.class.getDeclaredField("marker")` ném `NoSuchFieldException` | `marker` chỉ được khai báo tại `Mid` và `Root`; `getDeclaredField` không tìm lớp cha | Duyệt `getSuperclass()` và gọi `getDeclaredField` trên từng cấp |
| `getFields()` xuất hiện nhiều `value` | Các field `public` bị hide vẫn tồn tại | Dùng `getDeclaringClass()` để phân biệt |
| `getField("marker")` trả `Root.marker` thay vì `Mid.marker` | `Mid.marker` là `private`; `getField` chỉ lấy field `public` | Dùng `Mid.class.getDeclaredField("marker")` khi cần đúng field đã khai báo |
| Đọc `Root.value` từ đối tượng `Leaf` nhưng nhận `10` thay vì `30` | `Field` gắn với khai báo của `Root`, không dispatch động | Chọn `Leaf.class.getDeclaredField("value")` nếu cần field con |
| Mất một field khi đưa danh sách vào `Map<String, Field>` | Khóa trùng nhau và bị ghi đè | Dùng danh sách hoặc khóa gồm declaring class |
| `getField("LIMIT")` chọn interface không mong muốn | Thuật toán tra interface trực tiếp theo thứ tự khai báo | Dùng `First.class.getField("LIMIT")` / `Second.class.getField("LIMIT")` |
| Bị `IllegalAccessException` khi `Field.get` | Có metadata nhưng caller chưa được phép sử dụng | Kiểm tra modifier, receiver, module và quyền truy cập |
| Dựa vào thứ tự `getFields()` | API không bảo đảm sắp xếp | Sắp xếp hoặc xác định lớp khai báo tường minh |

## 9. Tự kiểm tra

1. `class Base { public int x; }` và `class Derived extends Base { public String x; }`: `Derived.class.getFields()` có thể chứa mấy field tên `x`? **Hai**: `Derived.x` và `Base.x`.
2. `Derived.class.getDeclaredField("x")` trả field nào? **`Derived.x`**, vì API chỉ xét field khai báo trực tiếp.
3. `Base.class.getDeclaredField("x").get(derived)` đọc field nào? **`Base.x`** trên cùng đối tượng `derived`.
4. Nếu `Derived` không khai báo `public x`, còn `Base` khai báo `public x`, `Derived.class.getField("x")` có thể trả field nào? **`Base.x`**, sau khi duyệt các thành phần theo thuật toán `getField`.
5. Vì sao `Map<String, Field>` không đủ biểu diễn field trong phân cấp? Vì nhiều khai báo khác nhau chia sẻ cùng tên và có thể bị ghi đè trong map.

**Ghi nhớ:** Field hiding thay đổi cách **tên field được phân giải**, không loại bỏ field của cha. Khi Reflection gặp nhiều field cùng tên, hãy xem `getDeclaringClass()` và dùng đúng API cho phạm vi cần tìm. `Field` định danh một khai báo cụ thể, vì thế `Field.get()` đọc chính khai báo đó.

Nguồn tra cứu: [Java Language Specification §8.3 (Field Declarations)](https://docs.oracle.com/javase/specs/jls/se17/html/jls-8.html#jls-8.3), [Java SE 17 Class API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html), [Dev.java — Reading and Writing Fields](https://dev.java/learn/api/reflection-method-handles/reflection/fields/).

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="fields-004">Đọc field instance bằng đối tượng đúng kiểu</a>

<details>
<summary>Xem chi tiết</summary>

# Đọc field instance bằng đối tượng đúng kiểu trong Java Reflection

## 1. Bài toán: một `Field` cần một object để đọc giá trị

Reflection cho phép lấy metadata của một field ngay cả khi chưa có object cụ thể:

```java
Field field = Person.class.getDeclaredField("name");
```

Nhưng **field instance thuộc trạng thái của từng object**. Để đọc giá trị, cần truyền một **đối tượng nhận (receiver)** phù hợp cho phương thức `Field.get(Object obj)`:

```java
Person person = new Person("Lan");
Object value = field.get(person);
```

Trong ví dụ minh họa này, `Person` phải là class có field `name`, constructor nhận `String`, và quyền truy cập phải hợp lệ. Đây là mảnh code giải thích cú pháp, không phải chương trình độc lập.

Có hai điều kiện riêng biệt: **(1) receiver phải thuộc đúng kiểu tương thích với class khai báo field; (2) code đang gọi phải có quyền truy cập field đó**. Nếu receiver sai kiểu, Java Reflection không thể lấy giá trị trường theo metadata đã chọn, dù field mang modifier `public`.

Câu hỏi cốt lõi của bài: `field.get(obj)` đòi `obj` phải có kiểu gì, và những khác biệt giữa class khai báo field, class runtime của object, field kế thừa và field cùng tên là gì?

## 2. Quy tắc chính xác của `Field.get(Object)`

Chữ ký API:

```java
public Object get(Object obj)
        throws IllegalArgumentException, IllegalAccessException
```

Khi `field` đại diện cho **instance field**:

- `obj` phải **khác `null`**.
- `obj` phải là instance của **class hoặc interface khai báo field**, hoặc một lớp con/lớp triển khai tương thích. Với field khai báo trong `Parent`, receiver `new Parent()` và `new Child()` khi `Child extends Parent` đều hợp lệ về kiểu.
- `Field.getDeclaringClass()` chỉ ra **kiểu khai báo field**. Đây là mốc cần đối chiếu với receiver, không phải class đã dùng để tìm kiếm một field một cách tình cờ.
- Khi receiver sai kiểu: `IllegalArgumentException`. Khi receiver `null` cho instance field: `NullPointerException`.
- Khi receiver đúng kiểu nhưng caller không có quyền đọc field trong chế độ kiểm tra truy cập hiện hành: `IllegalAccessException`.
- Khi đọc field kiểu nguyên thủy như `int`, `get` trả đối tượng wrapper, ví dụ `Integer`. Khi field kiểu tham chiếu chứa `null`, kết quả `get` cũng có thể là `null`: đừng nhầm **giá trị field null** với **receiver null**.

**Khác biệt với field static:** nếu field có modifier `static`, Java bỏ qua receiver truyền vào `Field.get` (có thể truyền `null`), vì giá trị thuộc class, không thuộc một object cụ thể. Bài này tập trung vào **instance field**.

Có thể dự đoán receiver đúng kiểu bằng biểu thức:

```java
Class<?> owner = field.getDeclaringClass();
boolean compatible = owner.isInstance(obj);
```

`Class.isInstance(null)` trả `false`, nên biểu thức kiểm tra an toàn cả khi receiver có thể null. Nó chỉ xác minh **khả năng tương thích kiểu**, không tự cấp quyền truy cập cho một field `private` hay một class/package bị giới hạn.

## 3. Ví dụ Java 17+ chạy độc lập: đúng kiểu, sai kiểu và `null`

Tạo file **`InstanceFieldReadDemo.java`**:

```java
import java.lang.reflect.Field;

public class InstanceFieldReadDemo {
    public static class Parent {
        public String label = "parent";
    }

    public static final class Child extends Parent {
        public String label = "child";
        public int count = 7;
    }

    public static void main(String[] args) throws ReflectiveOperationException {
        Parent parent = new Parent();
        Child child = new Child();

        Field parentLabel = Parent.class.getDeclaredField("label");
        Field childLabel = Child.class.getDeclaredField("label");
        Field count = Child.class.getDeclaredField("count");

        System.out.println("Parent.label from Parent: " + parentLabel.get(parent));
        System.out.println("Parent.label from Child: " + parentLabel.get(child));
        System.out.println("Child.label from Child: " + childLabel.get(child));

        System.out.println("Parent field accepts Child: "
                + parentLabel.getDeclaringClass().isInstance(child));
        System.out.println("Child field accepts Parent: "
                + childLabel.getDeclaringClass().isInstance(parent));

        Object countValue = count.get(child);
        System.out.println("Child.count: " + countValue);
        System.out.println("Count declared type: " + count.getType().getName());
        System.out.println("Count returned type: "
                + countValue.getClass().getSimpleName());

        try {
            childLabel.get(parent);
        } catch (IllegalArgumentException e) {
            System.out.println("Wrong receiver: IllegalArgumentException");
        }

        try {
            childLabel.get(null);
        } catch (NullPointerException e) {
            System.out.println("Null receiver: NullPointerException");
        }
    }
}
```

Biên dịch và chạy từ thư mục chứa file:

```bash
javac --release 17 InstanceFieldReadDemo.java
java InstanceFieldReadDemo
```

Kết quả stdout:

```text
Parent.label from Parent: parent
Parent.label from Child: parent
Child.label from Child: child
Parent field accepts Child: true
Child field accepts Parent: false
Child.count: 7
Count declared type: int
Count returned type: Integer
Wrong receiver: IllegalArgumentException
Null receiver: NullPointerException
```

**Vì sao chương trình cho kết quả trên?**

1. `parentLabel` khai báo tại `Parent` nên đọc được từ một `Parent` hoặc `Child`. Một object `Child` **vẫn chứa trạng thái instance của phần `Parent` được kế thừa**.
2. `Child` tự khai báo `label` cùng tên: đó là **field hiding**. Hai đối tượng `Field` đại diện cho **hai khai báo khác nhau**. `parentLabel.get(child)` vẫn đọc `Parent.label` và trả `parent`; `childLabel.get(child)` đọc `Child.label` và trả `child`.
3. `childLabel` khai báo tại `Child`, nên receiver `parent` không hợp lệ: một instance `Parent` chưa chắc là `Child`. Java phát sinh `IllegalArgumentException`.
4. `childLabel.get(null)` không có object chứa trạng thái instance nên phát sinh `NullPointerException`.
5. `count` khai báo là `int`; `Field.get` trả `Integer` sau boxing. `count.getType()` vẫn là `int.class`, còn kiểu runtime của kết quả là `Integer.class`.

Ví dụ cố ý dùng các field `public` trong những nested class `public` để làm nổi bật **kiểm tra kiểu receiver** mà không lẫn thêm lỗi quyền truy cập.

## 4. Hiểu đúng quan hệ lớp khai báo và kiểu runtime

Giả sử:

```java
class Animal {
    public int age = 3;
}

class Dog extends Animal {
    public String breed = "Shiba";
}
```

Đây là **mảnh code độc lập về khai báo kiểu**; để chạy thử, đặt hai class vào file phù hợp với quy tắc tên file Java, hoặc dùng chúng trong một chương trình có `main`.

Với:

```java
Field ageField = Animal.class.getDeclaredField("age");
Field breedField = Dog.class.getDeclaredField("breed");

Dog dog = new Dog();
Animal animal = new Animal();

Object a = ageField.get(dog);     // Hợp lệ: Dog là Animal
Object b = ageField.get(animal);  // Hợp lệ: Animal là Animal
Object c = breedField.get(dog);   // Hợp lệ: Dog có field breed
// breedField.get(animal);        // IllegalArgumentException
```

Các câu lệnh nằm trong một phương thức khai báo `throws ReflectiveOperationException` hoặc có xử lý các exception phù hợp.

Đừng đảo chiều quan hệ:

- **Field khai báo ở superclass + object subclass:** hợp lệ về kiểu.
- **Field khai báo ở subclass + object superclass:** không hợp lệ, trừ khi object được truyền thực sự là instance của subclass.
- **Field khai báo ở lớp A + object không liên quan B:** không hợp lệ.
- **Field khai báo ở A + biến tham chiếu có static type `Object` nhưng chứa instance của A:** vẫn hợp lệ, vì `Field.get` kiểm tra **object runtime**, không bị giới hạn bởi kiểu khai báo của biến Java.

Ví dụ:

```java
Object receiver = new Dog();
Object value = Animal.class.getDeclaredField("age").get(receiver);
```

Receiver hợp lệ vì object runtime là `Dog`. Việc biến `receiver` có static type `Object` không khiến `Field.get` ném `IllegalArgumentException`.

Có thể nhớ bằng biểu thức:

```java
field.getDeclaringClass().isInstance(receiver)
```

Nhưng biểu thức này không kiểm tra field có phải `static` và không thay thế việc xác thực quyền truy cập. Muốn tránh nhập nhằng, xử lý hai điều kiện đó riêng.

## 5. Đọc đúng field khi có kế thừa và trùng tên

### 5.1. `getDeclaredField` cố định class khai báo

`Parent.class.getDeclaredField("label")` trả field được khai báo **ngay trong `Parent`**. Tương tự, `Child.class.getDeclaredField("label")` trả field khai báo **ngay trong `Child`**; hai field có cùng tên nhưng khác declaring class.

Đây là lý do **đối tượng `Field` quyết định ô dữ liệu nào được đọc**, còn receiver quyết định **đọc ô dữ liệu đó trên instance nào**. Field không tự đổi thành khai báo gần subclass nhất chỉ vì truyền instance subclass vào `get`.

### 5.2. `getField` có thể tìm field public được kế thừa

`Child.class.getField("name")` có thể tìm field `public` kế thừa từ superclass nếu `Child` không khai báo field public cùng tên che khuất theo quy tắc Reflection. Tuy nhiên, khi xử lý field hiding, nên lấy chính xác theo class cần xét bằng `getDeclaredField` và kiểm tra `getDeclaringClass()`.

Để tìm field có tên cho trước trên **chuỗi superclass**, có thể duyệt lớp từ con lên cha và lấy khai báo đầu tiên tìm thấy:

```java
static Field findNearestDeclaredField(Class<?> start, String fieldName)
        throws NoSuchFieldException {
    for (Class<?> current = start;
         current != null;
         current = current.getSuperclass()) {
        try {
            return current.getDeclaredField(fieldName);
        } catch (NoSuchFieldException ignored) {
            // Lớp này không khai báo field đó; thử lớp cha.
        }
    }
    throw new NoSuchFieldException(fieldName);
}
```

Đây là **phương thức minh họa** (đặt trong class tiện ích và import `java.lang.reflect.Field`), không phải file hoàn chỉnh. Nó tìm **khai báo gần subclass nhất**, không tự kiểm tra đó là instance hay static field, không tự bảo đảm field đọc được về access. Nếu cần field cụ thể ở superclass, chọn `Parent.class.getDeclaredField("...")` ngay từ đầu.

### 5.3. Field hiding khác method overriding

Method có thể override và thực hiện dynamic dispatch. Với field, Java **không có override field** theo nghĩa dispatch động như method. Khi hai class có field cùng tên, việc truy cập qua `Field` được xác định bởi **khai báo mà `Field` đại diện**, bất kể kiểu runtime của receiver là subclass.

Vì vậy, đừng áp dụng trực giác “object là Child thì mọi field tên `label` phải lấy từ Child” cho Reflection. Hãy kiểm tra `Field.getDeclaringClass()`.

## 6. Hàm hỗ trợ đọc instance field an toàn hơn

Nếu viết mapper hoặc công cụ chẩn đoán, có thể tạo helper bắt buộc receiver tương thích ngay từ đầu, tránh gọi nhầm field static hoặc đọc từ object khác loại.

Đặt phương thức sau trong một class đã import `java.lang.reflect.Field`, `java.lang.reflect.Modifier` và `java.util.Objects`:

```java
static Object readInstanceField(Field field, Object receiver)
        throws IllegalAccessException {
    Objects.requireNonNull(field, "field");

    if (Modifier.isStatic(field.getModifiers())) {
        throw new IllegalArgumentException("Expected an instance field");
    }

    Class<?> owner = field.getDeclaringClass();
    if (!owner.isInstance(receiver)) {
        throw new IllegalArgumentException(
                "Receiver must be an instance of " + owner.getName()
        );
    }

    return field.get(receiver);
}
```

Ý nghĩa:

- `Objects.requireNonNull(field)` phát hiện sớm lỗi không có metadata field.
- `Modifier.isStatic(...)` kiểm tra đối tượng `Field` đại diện cho **instance field** đúng phạm vi helper.
- `owner.isInstance(receiver)` nhận cả instance của owner và subclass tương thích; với `null` trả `false`.
- Khi receiver hợp lệ, `field.get(receiver)` vẫn có thể ném `IllegalAccessException` nếu field không accessible. Helper **không tự bypass access control**, và đây là chủ ý thiết kế.

Helper cố tình chuyển cả null receiver và sai kiểu thành thông báo `IllegalArgumentException` của chính helper. Đó là **hợp đồng helper tự đặt ra**; nếu gọi API gốc `Field.get(null)` đối với instance field thì Java ném `NullPointerException` theo hợp đồng chính thức.

Nếu dùng helper trong hệ thống nhận dữ liệu từ bên ngoài, hãy xác định rõ chính sách exception và quyền truy cập thay vì tự động mở toàn bộ field.

## 7. Receiver đúng kiểu nhưng vẫn có thể bị `IllegalAccessException`

Ví dụ:

```java
public class Account {
    private String token = "secret";
}
```

Phần minh họa cần đặt trong ngữ cảnh phù hợp để biên dịch (top-level public class nằm trong `Account.java`). Code bên ngoài class có thể tìm metadata:

```java
Field tokenField = Account.class.getDeclaredField("token");
Account account = new Account();
Object value = tokenField.get(account); // Có thể ném IllegalAccessException
```

Ở đây `account` đúng kiểu, nhưng `token` là `private`. Nếu code gọi Reflection không có quyền truy cập private member, `get` bị chặn bằng `IllegalAccessException`. Nguyên nhân **khác hẳn** `IllegalArgumentException` khi receiver sai kiểu.

Cách xử lý nên ưu tiên **public getter hoặc API do lớp cung cấp** nếu bạn kiểm soát code nguồn. Chỉ dùng deep reflection khi bài toán thực sự yêu cầu và quyền truy cập cho phép. `trySetAccessible()` có thể trả `false`; `setAccessible(true)` có thể bị chặn, đặc biệt khi class nằm trong package của named module không được `opens` phù hợp. Không giả định mọi private field đều đọc được chỉ vì đã tìm thấy `Field`.

Phân biệt:

| Trường hợp | Receiver đúng kiểu? | Quyền truy cập? | Kết quả |
| --- | --- | --- | --- |
| Field `public` của public class, truy cập hợp lệ | Có | Có | Trả giá trị |
| Field `public` của `Parent`, receiver `Child extends Parent` | Có | Có | Trả phần trạng thái `Parent` trên object `Child` |
| Field của `Child`, receiver chỉ là `Parent` | Không | Không quyết định được kết quả thành công | `IllegalArgumentException` |
| Instance field, receiver là `null` | Không | Không quyết định được kết quả thành công | `NullPointerException` |
| Private field, receiver đúng kiểu nhưng caller không có quyền | Có | Không | `IllegalAccessException` |
| Static field | Receiver bị bỏ qua | Vẫn phải hợp lệ | Có thể truyền `null` |

Ở các trường hợp kết hợp nhiều lỗi, cần phân tích điều kiện của API và access control; bảng tập trung vào nguyên nhân nổi bật được tách riêng.

## 8. Đọc field primitive và kiểu tham chiếu

`Field.get(Object)` luôn trả về `Object` nên giá trị primitive được **boxing** tự động:

```java
Field countField = Child.class.getDeclaredField("count");
Object boxed = countField.get(child);   // Integer(7)
int count = (Integer) boxed;            // Unboxing
```

Đây là mảnh code tham chiếu `Child` và biến `child` trong chương trình ở mục 3.

Khi đã biết chính xác kiểu field, API còn có các phương thức đọc theo kiểu nguyên thủy, ví dụ `getInt(receiver)`, `getLong(receiver)`, `getBoolean(receiver)`. Chúng tránh trả về wrapper trong chữ ký gọi và áp dụng quy tắc chuyển đổi primitive thích hợp. **Không gọi `getInt` trên field tham chiếu `Integer` rồi mặc nhiên kỳ vọng được unboxing**, vì các phương thức chuyên biệt này không phải công cụ chuyển đổi kiểu tùy ý.

Với field kiểu tham chiếu:

```java
String text = (String) field.get(receiver);
```

ép kiểu `String` chỉ hợp lệ khi giá trị thực sự là `String` hoặc `null`; nếu giá trị thuộc class khác, code ép kiểu có thể ném `ClassCastException`. Có thể kiểm tra `field.getType()` để biết **kiểu khai báo của field**, nhưng type parameter generic yêu cầu thêm `getGenericType()` nếu cần phân tích chữ ký generic. Không nên dùng `getClass()` ngay trên kết quả mà chưa kiểm tra `null`.

## 9. Lỗi phổ biến khi đọc field instance

| Sai lầm | Triệu chứng | Cách sửa |
| --- | --- | --- |
| `field.get(null)` với instance field | `NullPointerException` | Truyền instance thật, khác `null` |
| Truyền object không thuộc declaring class hoặc subclass | `IllegalArgumentException` | Kiểm tra `field.getDeclaringClass().isInstance(receiver)` |
| Dùng `Field` của `Child` để đọc trên một `Parent` thuần túy | `IllegalArgumentException` | Lấy `Field` từ class phù hợp hoặc dùng receiver `Child` |
| Ngỡ field của superclass sẽ tự chuyển thành field cùng tên của subclass | Đọc nhầm giá trị khi field hiding | Kiểm tra `getDeclaringClass()`; chọn đúng khai báo |
| Receiver đúng nhưng field private không accessible | `IllegalAccessException` | Ưu tiên public API; kiểm tra module và quyền truy cập |
| Gọi `getDeclaredField` trên subclass cho field chỉ khai báo ở superclass | `NoSuchFieldException` | Tìm ở declaring class hoặc duyệt superclass |
| Ép trực tiếp `Object` từ field int sang `String` | `ClassCastException` | Dùng wrapper tương ứng, `getInt` hoặc kiểm tra kiểu |
| Dùng `field.getType().isInstance(receiver)` để kiểm tra receiver | Kết quả sai mục tiêu vì `getType()` là **kiểu giá trị** của field | Dùng `getDeclaringClass().isInstance(receiver)` |
| Quên phân biệt `static` và instance | Truyền receiver không cần thiết hoặc dùng helper sai mục đích | Dùng `Modifier.isStatic(field.getModifiers())` |

### Phân biệt ba kiểu trong cùng thao tác

Đọc biểu thức:

```java
Field field = Parent.class.getDeclaredField("label");
Object receiver = new Child();
Object value = field.get(receiver);
```

- `field.getDeclaringClass() == Parent.class`: **kiểu khai báo field**.
- `receiver.getClass() == Child.class`: **kiểu runtime của object đang chứa giá trị**.
- `field.getType() == String.class`: **kiểu khai báo của giá trị field**.

Để xác thực object truyền vào `get`, so **declaring class với runtime receiver**, không so `field.getType()` với receiver. `getType()` hữu ích khi kiểm tra hoặc chuyển đổi **giá trị trả về**, không phải khi xác định owner của field.

## 10. Câu hỏi tự kiểm tra

1. Với `Base` và `Derived extends Base`, nếu `age` được khai báo trong `Base` thì `Base.class.getDeclaredField("age").get(new Derived())` có hợp lệ về kiểu không?
2. Nếu `Derived` có thêm field `score`, điều gì xảy ra khi gọi `Derived.class.getDeclaredField("score").get(new Base())`?
3. `field.getDeclaringClass()` khác `field.getType()` như thế nào?
4. `field.get(null)` với instance field gây exception gì? Nếu field là `static` thì receiver được xử lý ra sao?
5. Tại sao đọc một private field có thể thất bại ngay cả khi `owner.isInstance(receiver)` trả `true`?
6. Hai class cha/con cùng khai báo field tên `label`: đối tượng `Field` thu từ class cha sẽ đọc field nào khi receiver là object con?

**Đáp án ngắn:** (1) hợp lệ; (2) `IllegalArgumentException`; (3) một bên là class khai báo field, bên kia là kiểu giá trị của field; (4) `NullPointerException` đối với instance, receiver bị bỏ qua đối với static; (5) vì còn kiểm tra quyền truy cập; (6) khai báo `label` của class cha.

## 11. Ghi nhớ khi áp dụng

Đối với **instance field**, thao tác đọc đúng là:

```java
if (field.getDeclaringClass().isInstance(receiver)) {
    Object value = field.get(receiver);
    // Sử dụng value theo kiểu khai báo phù hợp.
}
```

Đây là mảnh code đặt trong phương thức có xử lý `IllegalAccessException`, áp dụng riêng cho instance field. Nó thể hiện đúng quy tắc tương thích receiver, nhưng chương trình thực tế còn phải xử lý receiver không hợp lệ, quyền truy cập và giá trị `null`.

Ba câu hỏi nên trả lời trước mọi lệnh đọc bằng Reflection: **field được khai báo ở đâu? receiver thực sự là object thuộc kiểu nào? caller có quyền truy cập không?** Trả lời đúng ba câu hỏi này giúp phân biệt rõ lỗi kiểu, lỗi `null` và lỗi access, đồng thời tránh đọc nhầm field khi có kế thừa hoặc field hiding.

Tài liệu API tham khảo: [Java SE `Field.get(Object)`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/reflect/Field.html#get(java.lang.Object)).

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="fields-005">Đọc field static và đối số đích bị bỏ qua</a>

<details>
<summary>Xem chi tiết</summary>

# Đọc field static bằng Reflection: đối số đích bị bỏ qua

## 1. Vấn đề: vì sao Field.get(null) vẫn đọc được giá trị?

Trong Java, **field static thuộc về lớp** (chính xác hơn, thuộc về lớp định nghĩa field trong từng môi trường nạp lớp), thay vì thuộc riêng từng đối tượng. Do đó, khi gọi Reflection để đọc một field static, đối số `obj` của `Field.get(Object obj)` **không được dùng để chọn nơi chứa giá trị**. Theo Javadoc của `java.lang.reflect.Field`:

- Nếu field là **static**: `obj` **bị bỏ qua**; có thể truyền `null`, một instance của lớp khai báo hoặc thậm chí một đối tượng không liên quan.
- Nếu field là **instance**: `obj` bắt buộc là instance của lớp/interface khai báo field (hoặc subtype tương thích); `null` gây `NullPointerException`, đối tượng không tương thích gây `IllegalArgumentException`.

Ví dụ cơ bản:

```java
class Settings {
    public static int port = 8080;
    public int id = 7;
}
```

Khi đã có `Field portField = Settings.class.getDeclaredField("port")`, các lời gọi `portField.get(null)`, `portField.get(new Settings())` và `portField.get(new Object())` đều đọc cùng **giá trị static hiện tại** là `8080`, nếu chưa có code khác cập nhật field. Việc đọc **không cần tạo bất kỳ `Settings` instance nào**.

Ngược lại, `id` là instance field: `Settings.class.getDeclaredField("id").get(null)` không hợp lệ, bởi không có instance nào để tìm giá trị `id`.

**Quy tắc cần nhớ:** Field object quyết định *field nào được đọc*, còn `obj` chỉ định *instance nào cần đọc* **nếu field đó không static**. Với field static, giá trị gắn với `Field.getDeclaringClass()`, không gắn với đối tượng truyền vào.

## 2. Nhận biết field static trước khi đọc

Reflection trả về một `Field` đại diện cho khai báo, gồm tên, kiểu, modifier và lớp khai báo. Cách tương thích Java 17+ để kiểm tra static:

```java
Field f = Settings.class.getDeclaredField("port");
boolean isStatic = Modifier.isStatic(f.getModifiers());
Object value = f.get(isStatic ? null : someInstance);
```

Đoạn trên chỉ là **mảnh mã minh họa**, cần đặt trong một phương thức có xử lý `NoSuchFieldException`/`IllegalAccessException` và cung cấp biến `someInstance`, các import cần thiết.

- `f.getModifiers()` trả về bit mask modifier; `Modifier.isStatic(...)` là cách kiểm tra chuẩn.
- `f.getDeclaringClass()` trả về lớp/interface **khai báo chính field**, không nhất thiết là `getClass()` của đối tượng đích mà bạn đang có.
- `f.getType()` trả về kiểu khai báo của **giá trị field** (ví dụ `int.class` với `port`), không cho biết field static hay instance.
- `f.getName()` cho tên field; `getDeclaredField("port")` tìm khai báo trực tiếp trên `Settings`, còn `getField("port")` tìm field `public` theo quy tắc tìm kiếm public field, gồm thành viên kế thừa.

Trên JDK 20+, `f.accessFlags().contains(AccessFlag.STATIC)` cũng biểu diễn kiểm tra này bằng tập cờ. Tuy nhiên, `accessFlags()` **không có trên JDK 17**; dùng `Modifier` nếu mã cần chạy trên JDK 17.

Trong mã xử lý nhiều field bất kỳ, nên rẽ nhánh rõ ràng:

```java
Object read(Field field, Object instance) throws IllegalAccessException {
    if (Modifier.isStatic(field.getModifiers())) {
        return field.get(null);
    }
    return field.get(instance);
}
```

Đây là phương thức minh họa, không phải chương trình hoàn chỉnh. Cách viết `get(null)` cho field static làm rõ ý định và tránh tạo/đánh giá những biểu thức đối tượng thừa; ở nhánh instance, người gọi phải cung cấp đối tượng phù hợp và quyền truy cập vẫn được kiểm tra.

## 3. Chương trình Java 17+ độc lập, có thể biên dịch/chạy

Lưu toàn bộ mã sau vào **`StaticFieldReadDemo.java`**:

```java
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

public class StaticFieldReadDemo {
    public static class Settings {
        static { System.out.println("Settings initialized"); }
        public static int port = 8080;
        public int id = 7;
    }

    static Object makeTarget() {
        System.out.println("argument evaluated");
        return new Object();
    }

    public static void main(String[] args) throws ReflectiveOperationException {
        Class<?> type = Class.forName(Settings.class.getName(), false,
                StaticFieldReadDemo.class.getClassLoader());

        Field staticField = type.getDeclaredField("port");
        Field instanceField = type.getDeclaredField("id");

        System.out.println("static modifier = "
                + Modifier.isStatic(staticField.getModifiers()));
        System.out.println("before first get");
        System.out.println("get(null) = " + staticField.get(null));
        System.out.println("get(unrelated) = " + staticField.get(new Object()));
        System.out.println("get(expression) = " + staticField.get(makeTarget()));
        System.out.println("getInt(null) = " + staticField.getInt(null));
        System.out.println("instance get = " + instanceField.get(new Settings()));

        try {
            instanceField.get(null);
        } catch (NullPointerException ex) {
            System.out.println("instance with null = NullPointerException");
        }
        try {
            instanceField.get(new Object());
        } catch (IllegalArgumentException ex) {
            System.out.println("instance with unrelated = IllegalArgumentException");
        }

        staticField.setInt(null, 9090);
        System.out.println("after static set = " + staticField.getInt(new Object()));
    }
}
```

Biên dịch/chạy trong thư mục chứa tệp bằng JDK 17 trở lên:

```bash
javac StaticFieldReadDemo.java
java StaticFieldReadDemo
```

Stdout **đã được kiểm chứng khi chạy trên JDK 24**:

```text
static modifier = true
before first get
Settings initialized
get(null) = 8080
get(unrelated) = 8080
argument evaluated
get(expression) = 8080
getInt(null) = 8080
instance get = 7
instance with null = NullPointerException
instance with unrelated = IllegalArgumentException
after static set = 9090
```

### Phân tích tuần tự

1. `Class.forName(name, false, loader)` nạp/nhận diện lớp mà **không chủ động khởi tạo lớp** khi tham số `initialize` là `false`. `Settings.class.getName()` và `getDeclaredField(...)` cho phép xem metadata mà chưa cần tạo instance; ví dụ cố ý chưa gọi `new Settings()`.
2. `Modifier.isStatic(staticField.getModifiers())` là `true`, nhưng mới chỉ kiểm tra metadata.
3. Lần đầu `staticField.get(null)` **khởi tạo `Settings`** nếu chưa khởi tạo; vì thế dòng `Settings initialized` xuất hiện **sau** `before first get` và **trước** `get(null) = 8080`. Khởi tạo xong mới có thể đọc giá trị field.
4. `staticField.get(new Object())` không ném `IllegalArgumentException` dù đối tượng đích không thuộc `Settings`, bởi field static **bỏ qua đối số đích**.
5. Với `staticField.get(makeTarget())`, **Java vẫn đánh giá biểu thức đối số** `makeTarget()` trước khi bước vào `Field.get`. Vì vậy `argument evaluated` vẫn được in. Chỉ **giá trị tham chiếu trả về** của biểu thức mới bị phương thức bỏ qua khi xác định nơi đọc static field.
6. `getInt(null)` đọc field `int` trực tiếp dưới dạng primitive; `get(null)` trả `Object` chứa `Integer` nhờ boxing.
7. `instanceField.get(new Settings())` đọc đúng instance field `id`. Hai lời gọi tiếp theo minh họa lỗi với `null` và đối tượng sai kiểu đối với **instance field**.
8. `staticField.setInt(null, 9090)` minh họa quy tắc tương tự ở thao tác **ghi** static field không `final`. Giá trị mới được đọc lại qua `getInt(new Object())`; đối tượng `new Object()` vẫn bị bỏ qua.

## 4. Bảng phân biệt các tình huống

Giả sử field có quyền truy cập phù hợp, lớp khởi tạo thành công và chưa bị thay đổi bởi luồng khác:

| Thao tác | Static field | Instance field |
| --- | --- | --- |
| `field.get(null)` | Hợp lệ, đọc giá trị thuộc lớp khai báo | `NullPointerException` |
| `field.get(new DeclaringClass())` | Hợp lệ; instance bị bỏ qua | Hợp lệ, đọc giá trị của instance được truyền |
| `field.get(new Unrelated())` | Hợp lệ; không kiểm tra target có thuộc lớp khai báo không | `IllegalArgumentException` |
| `field.getInt(null)` nếu field có kiểu `int` | Hợp lệ, trả `int` | `NullPointerException` |
| `field.set(null, value)` | Target bị bỏ qua; thao tác ghi còn phụ thuộc quyền truy cập, kiểu và `final` | `NullPointerException` |
| `field.getDeclaringClass()` | Lớp/interface sở hữu field | Lớp/interface sở hữu field |

**Giới hạn của bảng:** `field.get(new Unrelated())` vẫn thực hiện việc **tạo `new Unrelated()` trước khi gọi `get`**; nếu constructor có tác dụng phụ hoặc lỗi, điều đó vẫn xảy ra. “Bị bỏ qua” chỉ có nghĩa là Reflection không dùng **đối tượng đã được truyền vào** để chọn storage của static field.

Một lựa chọn rất nên dùng là `field.get(null)` cho static field: rõ nghĩa, không tạo object, không đánh giá biểu thức thừa.

## 5. Static field là dữ liệu của lớp nào?

Xét **field hiding**, khi lớp con và lớp cha cùng khai báo static field trùng tên:

```java
class Parent {
    public static int count = 10;
}

class Child extends Parent {
    public static int count = 20;
}
```

Với metadata lấy từ **khai báo lớp cha**:

```java
Field parentCount = Parent.class.getDeclaredField("count");
Object result = parentCount.get(new Child()); // 10
```

Đây là mảnh mã minh họa và cần các import/ngoại lệ Reflection tương ứng. `Field` ở đây trỏ thẳng tới **`Parent.count`**. Mặc dù một instance `Child` được truyền vào, static target bị bỏ qua; không có “dynamic dispatch” chọn `Child.count`. Để đọc `Child.count`, phải có `Field childCount = Child.class.getDeclaredField("count")`.

Điều này rất khác với **instance method override**: đọc field (kể cả field instance) không áp dụng quy tắc dynamic dispatch theo class thực tế như gọi method override. `Field.getDeclaringClass()` là căn cứ đúng để biết field thuộc lớp nào.

**Class loader cũng là phần của danh tính lớp.** Hai `Class<?>` có cùng binary name nhưng được nạp bởi hai defining class loader khác nhau là hai runtime class khác nhau và có trạng thái static riêng. Nếu công cụ Reflection dùng sai `Class`/`ClassLoader`, việc thấy giá trị static khác nhau không có nghĩa `get` đã sử dụng target; hãy kiểm tra `field.getDeclaringClass()` và class loader thực tế.

## 6. Class initialization: đọc static có thể kích hoạt `<clinit>`

Javadoc của `Field.get(Object)` quy định rằng **nếu underlying field là static, lớp khai báo field sẽ được khởi tạo nếu trước đó chưa được khởi tạo**. Vì vậy, đọc bằng Reflection không chỉ là thao tác “xem metadata” thuần túy.

Các thao tác thường dễ bị nhầm:

- `Class.forName(name, false, loader)`: yêu cầu không initialize lớp ở bước tìm/nhận `Class`.
- `SomeClass.class`, `Field.getName()`, `Field.getModifiers()` và `Class.getDeclaredField(...)`: dùng để tham chiếu/tra metadata; **không đồng nghĩa với việc đọc giá trị static**.
- `field.get(null)` / `field.getInt(null)` với static field: kích hoạt khởi tạo lớp khai báo, nếu chưa được khởi tạo, rồi mới đọc.
- Nếu static initializer thất bại, lời gọi có thể ném `ExceptionInInitializerError` (hoặc một lỗi khởi tạo liên quan). Những lần sử dụng tiếp theo có thể gặp `NoClassDefFoundError` do lớp đã ở trạng thái khởi tạo lỗi.

Trong ví dụ chính, chính dòng `get(null)` đã làm khối `static { ... }` chạy. Đây là điểm quan trọng khi viết scanner cấu hình hoặc plugin: việc “đọc static để kiểm tra” có thể làm chạy mã khởi tạo ngoài ý muốn, mở kết nối hoặc thực hiện tác vụ khác.

**Trường hằng compile-time không được đánh đồng với mọi static field.** Với `static final int LIMIT = 123`, một biểu thức Java thông thường tham chiếu hằng có thể được compiler inline nên hành vi khởi tạo khác với `Field.get(null)`. Theo quy tắc của Reflection, đọc underlying static field vẫn yêu cầu initialize declaring class nếu chưa khởi tạo. Không dựa vào việc inlining của lời gọi trực tiếp để dự đoán hành vi Reflection.

## 7. Đọc primitive static field: get hay getInt/getLong?

`Field.get(Object)` luôn trả về `Object`. Với field primitive `int`, giá trị được boxing thành `Integer`; với field reference, trả đối tượng được lưu trong field (có thể là `null`).

`Field` còn có các biến thể `getBoolean`, `getByte`, `getChar`, `getShort`, `getInt`, `getLong`, `getFloat`, `getDouble`. **Quy tắc static bỏ qua target cũng áp dụng** cho các cách đọc typed này.

Ví dụ với field `public static int port = 8080`:

```java
Object boxed = staticField.get(null); // Integer(8080)
int primitive = staticField.getInt(null); // 8080
long widened = staticField.getLong(null); // 8080L
```

Đây là đoạn minh họa trong phạm vi ví dụ chính, với `staticField` đã được lấy từ lớp `Settings`.

Typed getter sử dụng **primitive widening conversion** khi hợp lệ. Vì `int` có thể widen thành `long`, `getLong(null)` dùng được với underlying `int`; nhưng `getInt(null)` không thể đọc underlying `long` bằng narrowing về `int` và có thể ném `IllegalArgumentException`. Đừng nhầm việc field static bỏ qua *target* với việc nó bỏ qua *kiểm tra kiểu dữ liệu*.

Với field static có kiểu tham chiếu `String`, dùng `get(null)` rồi cast đúng kiểu, sau khi đã xác nhận metadata `getType()` hoặc điều kiện thực tế. Không gọi `getInt(null)` để đọc một field `String`.

## 8. Các loại lỗi cần tách biệt

| Hiện tượng | Nguyên nhân đúng cần kiểm tra |
| --- | --- |
| `NoSuchFieldException` từ `getDeclaredField(...)` | Không có field tên đó **được khai báo trực tiếp** trên `Class` đang hỏi; có thể field kế thừa, tên sai hoặc sai class loader. |
| `IllegalAccessException` khi `get(null)` | Field không cho phép caller truy cập theo quy tắc Java/module hiện hành. Target `null` không bỏ qua access check. |
| `InaccessibleObjectException` từ `setAccessible(true)` | Không thể cho phép deep reflection do quy tắc module và điều kiện truy cập. |
| `NullPointerException` khi `get(null)` | Có thể đang đọc **instance field**; xác nhận `Modifier.isStatic(f.getModifiers())` trước. |
| `IllegalArgumentException` khi `get(wrongInstance)` | Với instance field, target sai kiểu; với typed getter, còn có thể do kiểu primitive không chuyển đổi hợp lệ. Với **static Field.get(Object)**, riêng target không liên quan **không** là lý do lỗi này. |
| `ExceptionInInitializerError` khi đọc static | Static initialization thất bại; kiểm tra nguyên nhân ở khối static/khởi tạo field của lớp khai báo. |
| Giá trị khác giữa các lần đọc | Có code đã ghi lại field, các luồng thay đổi trạng thái hoặc bạn đang xem hai runtime class từ class loader khác nhau. |

Với field `private static`, truyền `null` vẫn được nhưng **không tự vượt quyền private**. Có thể dùng `trySetAccessible()` khi module/điều kiện cho phép; không bảo đảm thành công. Với JDK 17+, strong encapsulation của module JDK và các named module có thể hạn chế reflection. Prefer public API hoặc thiết kế rõ quyền mở package, thay vì coi `setAccessible(true)` là luôn thành công.

`Field.set(Object, Object)` và `setInt` cũng bỏ qua đối số target nếu field static; tuy nhiên quyền ghi, tính `final`, kiểu giá trị, class initialization và module vẫn được áp dụng. Nhiều **static final field là non-modifiable** qua Reflection ngay cả sau khi bật `setAccessible(true)`; chỉ nên minh họa ghi lên field static **không final** như ví dụ.

## 9. Các hiểu nhầm thường gặp

**Nhầm 1 — `null` luôn gây NullPointerException.** Không đúng với `Field.get(null)` trên **static field**. Với instance field, lỗi là đúng vì thiếu đối tượng chứa giá trị.

**Nhầm 2 — truyền sai class sẽ khiến field static bị đọc từ class khác.** Không. `Field` đã gắn với declaring class ngay khi được tra cứu; `obj` không ảnh hưởng địa chỉ lưu trữ static.

**Nhầm 3 — “ignored” nghĩa là biểu thức đối số không chạy.** Sai. Java đánh giá các đối số trước khi gọi phương thức. `field.get(expensive())` vẫn có thể chạy `expensive()`, ném lỗi hoặc tạo tác dụng phụ. Chỉ giá trị target cuối cùng không được dùng bởi `get` nếu field static.

**Nhầm 4 — Reflection tự khởi tạo class ngay khi hỏi tên field.** Không nhất thiết. Việc tra cứu `getDeclaredField` khác với việc đọc **giá trị static**; `get` có thể kích hoạt class initialization.

**Nhầm 5 — dùng một `new Object()` thì tránh được kiểm tra quyền truy cập.** Sai. Bỏ qua target không bỏ qua `IllegalAccessException`, encapsulation hay quyền module.

**Nhầm 6 — getInt() hoạt động cho mọi kiểu numeric.** Sai. Typed getter thực hiện chuyển đổi primitive hợp lệ theo hướng widening; không tự narrowing, không parse `String` và không biến field kiểu tham chiếu thành primitive tùy ý.

**Nhầm 7 — field static dùng dynamic dispatch giống method override.** Không. Field hiding khác method overriding; `Field.getDeclaringClass()` cho biết đúng field được đọc.

**Nhầm 8 — muốn đọc static bắt buộc tạo instance.** Không. `field.get(null)` là cách đọc có chủ ý và thường là cách dễ hiểu nhất, khi đã đủ quyền.

## 10. Áp dụng trong framework / utility phản chiếu

Khi viết reflection utility xử lý cả static và instance field:

1. **Lấy đúng `Class`** cần khảo sát; nếu có plugin/class loader, xác định loader của lớp.
2. **Tra cứu đúng `Field`** bằng `getDeclaredField` hoặc `getField` theo phạm vi mong muốn; xử lý `NoSuchFieldException`.
3. **Đọc metadata `Modifier.isStatic(field.getModifiers())`** và chuẩn bị target: `null` cho static, instance thực cho non-static.
4. **Kiểm tra khả năng truy cập**; tôn trọng Java access control/module boundary. Nếu field không public, xem có thật sự cần reflection sâu hay có accessor API hợp lệ.
5. **Đọc bằng getter phù hợp** với field type: `get(null)` cho reference/muốn boxing, `getInt(null)` cho primitive int, v.v. Xem xét khả năng static initialization phát sinh tác dụng phụ.
6. **Diễn giải dữ liệu** theo declaring class và cấu trúc field; không suy từ target runtime class rằng field static có “bản sao riêng” trên object.

Một helper gọn có thể dùng cho field đã có quyền truy cập:

```java
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

public final class FieldReader {
    private FieldReader() {}

    public static Object read(Field field, Object instance)
            throws IllegalAccessException {
        Object target = Modifier.isStatic(field.getModifiers())
                ? null
                : instance;
        return field.get(target);
    }
}
```

Đây là **một lớp utility hoàn chỉnh** nhưng không có `main`; nó được gọi từ ứng dụng khác. Nó không tự bypass private/module access, không đảm bảo target instance đúng kiểu, và không ngăn tác dụng phụ khi class initialization xảy ra. Với field instance, `instance` phải phù hợp; với field static, caller có thể truyền `null`. Java reflection vẫn thực hiện access check bình thường.

## 11. Tự kiểm tra

Giả sử `Field score` trỏ tới `public static int score = 50` trong `Game` và quyền truy cập bình thường:

1. `score.get(null)` cho gì? **`Integer` chứa `50`** qua boxing.
2. `score.get(new Object())` có gây `IllegalArgumentException` vì sai target không? **Không**; target bị bỏ qua.
3. `score.getInt(null)` cho gì? **Primitive `int` bằng `50`**.
4. Nếu `static { System.out.println("init"); }` chưa chạy, lần đầu `score.get(null)` có thể làm gì? **Khởi tạo `Game` và in `init` trước khi trả giá trị**, nếu lớp chưa được initialize.
5. Với `public int level = 2`, `levelField.get(null)` cho gì? **`NullPointerException`** vì thiếu instance.
6. Nếu `score` được khai báo `private static` trong named module không cho phép deep reflection, truyền `null` có chữa lỗi quyền truy cập không? **Không**.
7. Nếu `score.get(makeTarget())`, hàm `makeTarget()` có được chạy không? **Có**, vì biểu thức đối số được đánh giá trước; target object tạo ra vẫn bị bỏ qua sau đó.
8. Hai lớp khác nhau đều có `static int score`: Reflection đọc field nào? **Field của declaring class gắn với đối tượng `Field`**, không dựa trên class của target được truyền.

## 12. Tài liệu chính thức

- [Oracle Java SE 17 — Field.get(Object), set(Object,Object) và typed getters](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Field.html)
- [Oracle Java SE 25 — Field.get(Object), Javadoc](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/reflect/Field.html)
- [Oracle Java SE 17 — Modifier.isStatic(int)](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Modifier.html)
- [Java Language Specification 8.3.1.1 — static Fields](https://docs.oracle.com/javase/specs/jls/se17/html/jls-8.html#jls-8.3.1.1)
- [Java Language Specification 12.4 — Initialization of Classes and Interfaces](https://docs.oracle.com/javase/specs/jls/se17/html/jls-12.html#jls-12.4)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="fields-006">Field.get trả primitive dưới dạng wrapper</a>

<details>
<summary>Xem chi tiết</summary>

# `Field.get()` trả về wrapper khi field có kiểu primitive

## 1. Vấn đề: field là `int`, vì sao kết quả của `get()` là `Integer`?

Khi dùng Java Reflection để đọc một field, chữ ký API quan trọng nhất là:

~~~java
public Object get(Object obj) throws IllegalArgumentException, IllegalAccessException
~~~

`Field.get(Object)` có kiểu trả về **`Object`**, trong khi Java có tám kiểu primitive (`boolean`, `byte`, `short`, `char`, `int`, `long`, `float`, `double`). Primitive không phải đối tượng, nên không thể trực tiếp là giá trị trả về kiểu `Object`. Vì vậy, nếu field được khai báo là primitive, Java đọc giá trị primitive rồi **boxing** thành một đối tượng của wrapper tương ứng.

Ví dụ:

~~~java
class Counter {
    public int count = 42;
}

Counter counter = new Counter();
java.lang.reflect.Field field = Counter.class.getField("count");

Object result = field.get(counter);           // Integer chứa 42
System.out.println(field.getType());          // int
System.out.println(result.getClass());        // class java.lang.Integer
int count = (Integer) result;                 // cast + unboxing
~~~

Đoạn code trên là minh họa. `Field.getType()` trả về kiểu **khai báo trong class** (`int.class`), còn `result.getClass()` trả về class **của đối tượng được boxing** (`Integer.class`). Không có chuyện field ban đầu bị đổi từ `int` sang `Integer` trong đối tượng `Counter`.

Tư duy đúng là:

~~~text
Field khai báo int -> đọc giá trị int -> boxing thành Integer -> trả về Object
                                               |
                              getType() vẫn là int.class
~~~

Với field kiểu tham chiếu, `get()` trả về chính giá trị tham chiếu hiện tại (hoặc `null`), không tạo thêm wrapper chỉ vì kiểu trả về của phương thức là `Object`.

## 2. Bảng ánh xạ primitive và wrapper

| Kiểu khai báo của field | Đối tượng do `Field.get()` trả về khi field primitive |
| --- | --- |
| `boolean` | `Boolean` |
| `byte` | `Byte` |
| `short` | `Short` |
| `char` | `Character` |
| `int` | `Integer` |
| `long` | `Long` |
| `float` | `Float` |
| `double` | `Double` |

Đây là ánh xạ kiểu, không phải việc mọi lần gọi đều bắt buộc tạo đối tượng wrapper mới. Đừng dựa vào `==` giữa hai kết quả `Field.get()` để kiểm tra “có cùng giá trị primitive hay không”. Nếu cần so sánh dữ liệu, hãy unbox để dùng toán tử của primitive hoặc dùng phép so sánh giá trị phù hợp.

Một số điểm cần phân biệt:

- Field `int number` luôn lưu giá trị primitive `int`; `Field.get` biểu diễn kết quả bằng `Integer`. Primitive field không thể mang `null`.
- Field `Integer number` vốn là **kiểu tham chiếu**. `Field.get` trả chính tham chiếu `Integer` hoặc `null` đang có; bản thân field không phải primitive.
- Field `String name` cho ra `String`, không phải một “wrapper của String”.
- Field `int[] numbers` là **kiểu mảng tham chiếu**. `Field.get` trả về đối tượng `int[]`; nó không tự boxing từng phần tử thành `Integer[]`. Muốn lấy riêng một phần tử qua Reflection, xem `java.lang.reflect.Array.get`; với phần tử primitive, thao tác đó cũng biểu diễn giá trị bằng wrapper.

## 3. Ví dụ Java 17+ có thể biên dịch/chạy

Tạo file `FieldGetBoxingDemo.java`:

~~~java
import java.lang.reflect.Field;

public class FieldGetBoxingDemo {
    public static class Data {
        public int count = 42;
        public boolean enabled = true;
        public char letter = 'J';
        public long large = 3_000_000_000L;
        public Integer nullable = null;
        public String label = "Java";
        public static double factor = 1.5;
    }

    public static void main(String[] args) throws Exception {
        Data data = new Data();

        Field count = Data.class.getField("count");
        Object value = count.get(data);

        System.out.println("field type: " + count.getType().getName());
        System.out.println("result type: " + value.getClass().getSimpleName());
        System.out.println("result: " + value);
        System.out.println("unboxed + 1: " + ((Integer) value + 1));

        System.out.println("getInt: " + count.getInt(data));
        System.out.println("getLong widening: " + count.getLong(data));

        Object flag = Data.class.getField("enabled").get(data);
        System.out.println("boolean wrapper: "
                + flag.getClass().getSimpleName() + "=" + flag);

        Object letter = Data.class.getField("letter").get(data);
        System.out.println("char wrapper: "
                + letter.getClass().getSimpleName() + "=" + letter);

        Object large = Data.class.getField("large").get(data);
        System.out.println("long wrapper: "
                + large.getClass().getSimpleName() + "=" + large);

        Object staticValue = Data.class.getField("factor").get(null);
        System.out.println("static wrapper: "
                + staticValue.getClass().getSimpleName() + "=" + staticValue);

        System.out.println("nullable value: "
                + Data.class.getField("nullable").get(data));
        System.out.println("reference type: "
                + Data.class.getField("label").get(data)
                        .getClass().getSimpleName());

        try {
            Data.class.getField("large").getInt(data);
        } catch (IllegalArgumentException e) {
            System.out.println("getInt from long: "
                    + e.getClass().getSimpleName());
        }

        try {
            Data.class.getField("nullable").getInt(data);
        } catch (IllegalArgumentException e) {
            System.out.println("getInt from Integer: "
                    + e.getClass().getSimpleName());
        }
    }
}
~~~

Biên dịch và chạy từ thư mục chứa file:

~~~bash
javac --release 17 FieldGetBoxingDemo.java
java FieldGetBoxingDemo
~~~

Kết quả:

~~~text
field type: int
result type: Integer
result: 42
unboxed + 1: 43
getInt: 42
getLong widening: 42
boolean wrapper: Boolean=true
char wrapper: Character=J
long wrapper: Long=3000000000
static wrapper: Double=1.5
nullable value: null
reference type: String
getInt from long: IllegalArgumentException
getInt from Integer: IllegalArgumentException
~~~

### Giải thích kết quả

1. `count` được **khai báo** kiểu `int`, nên `count.getType().getName()` là `"int"`. Sau `count.get(data)`, biến `value` giữ tham chiếu đến `Integer` chứa 42, vì `get` bắt buộc trả `Object`.
2. `(Integer) value + 1` gồm phép cast sang `Integer`, sau đó **auto-unboxing** khi thực hiện phép cộng, kết quả là 43. Không nên cast trực tiếp `(int) value` trong code cần tương thích/tường minh vì kiểu tĩnh của `value` là `Object` và cần đảm bảo giá trị là wrapper phù hợp; cách dễ hiểu là `((Integer) value).intValue()` hoặc `int n = (Integer) value;`.
3. `getInt(data)` là API chuyên biệt trả về trực tiếp `int`. `getLong(data)` cũng hợp lệ đối với field `int` vì Java cho phép **widening primitive conversion** từ `int` sang `long`.
4. `enabled`, `letter`, `large`, `factor` được khai báo lần lượt `boolean`, `char`, `long`, `double`. `Field.get` cho về `Boolean`, `Character`, `Long`, `Double`; field static `factor` không cần một instance để đọc nên gọi `get(null)`.
5. `nullable` là field tham chiếu kiểu `Integer` có giá trị `null`; `get` trả `null`, không tự cung cấp số 0 hay sinh một wrapper. `label` là field tham chiếu kiểu `String`, kết quả thực sự là `String`.
6. `getInt` không chấp nhận đọc field `long` bằng cách thu hẹp kiểu (`long` → `int`), nên báo `IllegalArgumentException`. Nó cũng không đọc được field **khai báo `Integer`**: đây là kiểu tham chiếu, không phải field primitive `int`. Nếu cần wrapper field, dùng `get` rồi kiểm tra/null-check và ép kiểu thích hợp.

Ví dụ dùng các public field để tập trung vào quy tắc boxing. Với field private hoặc field thuộc module đóng, có thể cần xử lý quyền truy cập trước khi đọc; boxing không làm thay đổi access check.

## 4. Khác biệt giữa `get()` và các typed getter

`Field` cung cấp các getter trả về primitive cụ thể:

~~~java
boolean getBoolean(Object obj)
byte    getByte(Object obj)
short   getShort(Object obj)
char    getChar(Object obj)
int     getInt(Object obj)
long    getLong(Object obj)
float   getFloat(Object obj)
double  getDouble(Object obj)
~~~

Các method trên là API của `java.lang.reflect.Field`, không phải code mới cần tự cài đặt. Mỗi method nhận receiver tương tự `get(obj)`, nhưng **kiểu trả về là primitive**, nên phù hợp khi cần tính toán và muốn tránh API trả `Object`.

### Quy tắc chuyển đổi kiểu

- `get()` trả `Object`: field primitive được boxing thành **đúng wrapper tương ứng với kiểu của field**. Không tự chuyển field `int` thành `Long` chỉ vì caller muốn số `long`.
- `getInt()` đọc được một field nguyên thủy kiểu `byte`, `short`, `char` hoặc `int` thông qua phép mở rộng kiểu primitive (widening) đến `int`. Không đọc được field `long`, `float`, `double`, `boolean` hoặc field tham chiếu `Integer`.
- `getLong()` đọc được các field primitive `byte`, `short`, `char`, `int` hoặc `long`; ví dụ từ field `int` có thể mở rộng thành `long`.
- `getDouble()` có thể đọc các kiểu số primitive được phép widening đến `double` (`byte`, `short`, `char`, `int`, `long`, `float`, `double`).
- `getBoolean()` chỉ phù hợp với field có kiểu primitive `boolean`. `boolean` không widening sang số.
- `getByte()` chỉ nhận `byte`; `getChar()` chỉ nhận `char`. Các getter còn lại tuân theo widening primitive hợp lệ của Java.

Typed getter không thực hiện unboxing field kiểu tham chiếu. Một field khai báo `Integer` vẫn cần `Field.get()`; đối tượng trả về có thể là `null`. Đây là khác biệt thiết yếu giữa **chuyển đổi widening primitive** và **unboxing wrapper**.

Ví dụ dưới đây là mảnh code dùng lại `count` và `data` trong chương trình trên:

~~~java
int exact = count.getInt(data);     // 42, primitive int
long wider = count.getLong(data);   // 42L, int -> long
Object boxed = count.get(data);    // Integer, vì get trả Object
~~~

Không nên mặc định typed getter là “cách đọc nào cũng ép được” như cơ chế chuyển đổi của trình ánh xạ dữ liệu. Khi kiểu khai báo không phù hợp, nó ném `IllegalArgumentException` thay vì tự parse số hay thực hiện narrowing cast.

## 5. Kiểu khai báo và kiểu giá trị: cách kiểm tra đúng

Trong mã làm việc với nhiều field khác nhau, thường cần xét `Field.getType()` **trước** khi ép kiểu:

~~~java
Class<?> declared = count.getType();
if (declared == int.class) {
    int n = count.getInt(data);
    System.out.println(n);
}
~~~

Đây là mảnh code dùng lại biến `count` và `data` từ ví dụ. Với field primitive, `getType()` trả về `int.class`, `boolean.class`,… (các `Class` đại diện kiểu primitive). Với field kiểu wrapper, `getType()` là `Integer.class`, `Boolean.class`,… và `isPrimitive()` trả `false`. Có thể phân nhánh:

~~~java
Class<?> declared = field.getType();
if (declared.isPrimitive()) {
    // Dữ liệu trong field là primitive; get() sẽ boxing khi trả Object.
} else {
    // Dữ liệu field là tham chiếu, có thể null.
}
~~~

Đây cũng là mảnh minh họa; `field` đại diện một `Field` đã được tra cứu. Không gọi `result.getClass()` trước khi kiểm tra `result != null`: với field tham chiếu chứa `null`, bạn sẽ gặp `NullPointerException`. Ngược lại, field primitive luôn có một giá trị primitive (kể cả giá trị mặc định 0, `false`, `'\0'`), nên kết quả `Field.get` của một field primitive đã đọc thành công không phải `null`.

Một lỗi khác là dựa vào `value.getClass() == Integer.class` để suy rằng **field khai báo `int`**. Điều đó không chính xác: kết quả `Field.get` từ một field `int` và một field `Integer` không-null đều có thể là `Integer`. Muốn phân biệt, phải dùng **`Field.getType()`**.

## 6. Những yếu tố dễ làm việc đọc field thất bại

Cơ chế boxing chỉ diễn ra sau khi đọc field thành công. Các lỗi khi tìm/đọc field là vấn đề riêng:

| Tình huống | Hành vi cần lưu ý |
| --- | --- |
| `getField("x")` nhưng field không public/không nằm trong public lookup | `NoSuchFieldException`; thử `getDeclaredField` trên đúng declaring class nếu thực sự cần member không-public |
| `get()` field private khi caller không có quyền | `IllegalAccessException`; `getDeclaredField` không tự bật quyền |
| Đọc instance field với `null` receiver | `NullPointerException`; phải có object đích |
| Truyền object không thuộc kiểu khai báo field | `IllegalArgumentException` |
| Đọc static field bằng `get(null)` | Hợp lệ; receiver bị bỏ qua cho static field, vẫn phải đáp ứng quyền truy cập |
| Gọi typed getter `getInt` trên field `Integer`/`long` | `IllegalArgumentException` do kiểu khai báo không phù hợp |
| Truy cập private field xuyên named module bằng deep reflection | `trySetAccessible()` có thể trả `false`; `setAccessible(true)` có thể ném `InaccessibleObjectException` |
| `get()` trả `null`, sau đó gọi `getClass()`/unbox | `NullPointerException` của code xử lý `null`, không phải “boxing primitive thành null” |

Với named module, cần phân biệt `exports` cho API public và `opens` cho deep reflection vào package khi module khác yêu cầu. Không mặc định `setAccessible(true)` thành công. Nếu một ứng dụng phụ thuộc vào field private, hãy ưu tiên API truy cập chính thức khi có thể.

## 7. Hiệu năng, boxing và lựa chọn API

Nếu mục đích là xử lý một số lượng lớn field primitive trên đường thực thi nóng, `getInt`/`getLong` có kiểu trả về primitive và tránh việc **API bắt buộc** trả `Object`, nên là lựa chọn tự nhiên khi biết trước kiểu. Tuy nhiên Reflection nói chung có chi phí runtime, và JVM có thể tối ưu boxing/cấp phát tùy trường hợp; không nên kết luận `Field.get` **luôn** cấp phát một wrapper mới hay một typed getter **luôn** nhanh hơn đáng kể khi chưa đo đạc.

Các cách chọn thường hợp lý:

1. Biết rõ field `int` và cần tính toán: dùng `getInt(receiver)`, có xử lý `IllegalAccessException`.
2. Cần một API chung đọc nhiều kiểu field: dùng `get(receiver)`; khi field primitive, kết quả sẽ được boxing, và bạn xử lý qua `Object`/wrapper.
3. Field thực sự khai báo wrapper `Integer`: dùng `get(receiver)`, xét trường hợp `null`; không dùng `getInt`.
4. Cần phân loại kiểu một cách đáng tin: dùng `field.getType()`, xét `isPrimitive()` và các class nguyên thủy cụ thể; đừng chỉ dựa vào class của object kết quả.

**Tự kiểm tra:**

- Một field `double price = 1.25;` được đọc qua `Field.get` trả đối tượng kiểu gì? → `Double`.
- Một field `Integer price = null;` được đọc qua `Field.get` trả gì? → `null`; không boxing giá trị mới.
- `Field.getType()` của field `int count` là `Integer.class` hay `int.class`? → `int.class`.
- `getLong` đọc field `int` được không? → Được, nhờ widening primitive `int` sang `long`.
- `getInt` đọc field khai báo `Integer` được không? → Không, vì typed getter không unbox field tham chiếu.
- `Field.get` đọc field `int[]` có trả `Integer[]` không? → Không, nó trả tham chiếu đến `int[]`; mảng primitive không tự đổi thành mảng wrapper.

Điểm cần nhớ là **Reflection giữ nguyên kiểu khai báo của field**. Chỉ giá trị trả về qua `Field.get(Object)` cần được biểu diễn dưới dạng `Object`, nên primitive mới được boxing thành wrapper; những thao tác typed getter và kiểm tra `getType()` giúp giữ ranh giới này rõ ràng.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="fields-007">Các phương thức getInt, getLong, getDouble và chuyển đổi được phép</a>

<details>
<summary>Xem chi tiết</summary>

# Field.getInt(), getLong(), getDouble() và các chuyển đổi kiểu được phép

Khi đọc giá trị trường bằng Java Reflection, `java.lang.reflect.Field` cung cấp hai nhóm API: `get(Object)` trả về `Object`, còn các phương thức như `getInt(Object)`, `getLong(Object)`, `getDouble(Object)` trả về một **kiểu nguyên thủy cụ thể**. Những phương thức `getXxx` không đơn giản “ép” bất kỳ dữ liệu nào về kiểu `Xxx`: chúng chỉ chấp nhận trường có **kiểu nguyên thủy tương thích theo quy tắc chuyển đổi mở rộng** (*widening primitive conversion*).

Ví dụ, với một trường khai báo là `byte`, cả `getInt`, `getLong` và `getDouble` đều có thể đọc giá trị. Nhưng với trường `double`, gọi `getInt` sẽ ném `IllegalArgumentException`, kể cả khi giá trị `double` đang bằng `10.0`. Phép chuyển đổi được xét từ **kiểu khai báo của trường**, không phải dựa vào giá trị hiện tại có thể “vừa” trong kiểu đích hay không.

Bài học tập trung vào ba phương thức `getInt`, `getLong`, `getDouble`, đồng thời trình bày quy tắc chung để áp dụng đúng cho cả các primitive getter khác.

## 1. Field.getInt(), getLong(), getDouble() làm gì?

Một `Field` biểu diễn **một trường xác định trong lớp khai báo**, không phải một bản sao giá trị trường. Khi gọi getter, đối số `obj` chỉ ra đối tượng đang chứa giá trị cần đọc (hoặc `null` nếu là trường `static`).

| Phương thức | Kiểu Java trả về | Chấp nhận trường có kiểu khai báo |
| --- | --- | --- |
| `getInt(obj)` | `int` | `byte`, `short`, `char`, `int` |
| `getLong(obj)` | `long` | `byte`, `short`, `char`, `int`, `long` |
| `getDouble(obj)` | `double` | `byte`, `short`, `char`, `int`, `long`, `float`, `double` |

Tại mỗi dòng, trường có **cùng kiểu** với kiểu trả về cũng hợp lệ (chuyển đổi đồng nhất). Những kiểu còn lại hợp lệ vì tồn tại phép chuyển đổi **mở rộng primitive** từ kiểu của trường sang kiểu getter trả về.

Ví dụ các chuyển đổi hợp lệ:

- Trường `byte` chứa `12`: `field.getInt(obj)` trả `12` kiểu `int`; `getLong(obj)` trả `12L`; `getDouble(obj)` trả `12.0`.
- Trường `char` chứa ký tự `'A'`: `getInt(obj)` trả **65** (`char` là mã UTF-16 unsigned 16-bit, mở rộng lên `int` theo giá trị số).
- Trường `float` chứa `1.25f`: `getDouble(obj)` trả `1.25`.
- Trường `long`: `getDouble(obj)` được phép nhưng **có thể làm tròn, mất độ chính xác**.

Những ví dụ không hợp lệ:

- `getInt` trên `long`, `float` hoặc `double`: đây sẽ là **narrowing**, không được getter thực hiện.
- `getLong` trên `float` hoặc `double`: không có widening primitive conversion tương ứng.
- `getInt` trên `boolean`: `boolean` không chuyển đổi sang kiểu số theo widening.
- `getInt` trên `Integer`: `Integer` là **kiểu tham chiếu**, không phải trường primitive `int`. `getInt` không tự unbox đối tượng `Integer` đang được lưu trong trường.

## 2. Bảng chuyển đổi đầy đủ cho mọi primitive getter

Để không học thuộc từng hàm rời rạc, hãy ghi nhớ **kiểu nguồn là kiểu khai báo của trường**, **kiểu đích là kiểu trả về của getter**. Getter chỉ hỗ trợ identity hoặc một bước chuyển đổi widening primitive hợp lệ theo Java Language Specification.

| Getter | Kiểu trường được phép đọc |
| --- | --- |
| `getBoolean` | `boolean` |
| `getByte` | `byte` |
| `getShort` | `byte`, `short` |
| `getChar` | `char` |
| `getInt` | `byte`, `short`, `char`, `int` |
| `getLong` | `byte`, `short`, `char`, `int`, `long` |
| `getFloat` | `byte`, `short`, `char`, `int`, `long`, `float` |
| `getDouble` | `byte`, `short`, `char`, `int`, `long`, `float`, `double` |

Sơ đồ các đường chuyển đổi widening số thường gặp:

```text
byte  -> short -> int -> long -> float -> double
char  ---------> int -> long -> float -> double
                 (char không widening sang short hoặc byte)
```

Sơ đồ này biểu diễn các bước đi theo hướng mở rộng, bao gồm cả những bước có thể nhảy qua (chẳng hạn `byte -> double`). `short` **không** tự đổi thành `char`; `char` không đổi thành `short` vì miền giá trị khác nhau. `boolean` đứng riêng và chỉ có thể đọc qua `getBoolean`.

**Widening khác với casting/narrowing trong mã Java:** biểu thức Java có thể viết `(int) someDouble` với ép kiểu tường minh, nhưng `Field.getInt` không nhận một trường có kiểu `double`. Muốn đọc rồi ép kiểu có chủ đích, phải dùng getter đúng kiểu nguồn trước, sau đó thực hiện conversion ở tầng ứng dụng.

## 3. Ví dụ Java 17+ có thể biên dịch và chạy

Ví dụ dưới đây minh họa chuyển đổi widening thành công, hai trường hợp bị từ chối, cách đọc `static` field và trường hợp **widening vẫn mất độ chính xác số học**. Mọi field cần đọc được khai báo `public` trong lớp lồng để ví dụ tập trung vào quy tắc kiểu của getter, không vướng quyền truy cập.

Tạo tệp `FieldWideningDemo.java`:

```java
import java.lang.reflect.Field;

public class FieldWideningDemo {
    public static class Values {
        public byte small = 12;
        public char letter = 'A';
        public short portion = 32_000;
        public int count = 16_777_217; // 2^24 + 1
        public long huge = 9_007_199_254_740_993L; // 2^53 + 1
        public float fraction = 1.25f;
        public double price = 2.5;
        public Integer wrapper = 99;
        public boolean active = true;
        public static int staticCount = 7;
    }

    public static void main(String[] args) throws ReflectiveOperationException {
        Values obj = new Values();

        Field small = Values.class.getDeclaredField("small");
        Field letter = Values.class.getDeclaredField("letter");
        Field portion = Values.class.getDeclaredField("portion");
        Field count = Values.class.getDeclaredField("count");
        Field huge = Values.class.getDeclaredField("huge");
        Field fraction = Values.class.getDeclaredField("fraction");
        Field price = Values.class.getDeclaredField("price");
        Field wrapper = Values.class.getDeclaredField("wrapper");
        Field active = Values.class.getDeclaredField("active");
        Field staticCount = Values.class.getDeclaredField("staticCount");

        System.out.println("byte -> int: " + small.getInt(obj));
        System.out.println("char -> long: " + letter.getLong(obj));
        System.out.println("short -> double: " + portion.getDouble(obj));
        System.out.println("float -> double: " + fraction.getDouble(obj));
        System.out.println("int -> float (rounded): " + (int) count.getFloat(obj));
        System.out.println("long -> double (rounded): " + (long) huge.getDouble(obj));
        System.out.println("static int -> long: " + staticCount.getLong(null));
        System.out.println("boolean -> boolean: " + active.getBoolean(obj));

        try {
            price.getInt(obj); // double -> int: không phải widening
        } catch (IllegalArgumentException ex) {
            System.out.println("double -> int: rejected");
        }

        try {
            wrapper.getInt(obj); // Integer là reference field
        } catch (IllegalArgumentException ex) {
            System.out.println("Integer -> int: rejected");
        }
    }
}
```

Lệnh biên dịch và chạy:

```bash
javac FieldWideningDemo.java
java FieldWideningDemo
```

Kết quả:

```text
byte -> int: 12
char -> long: 65
short -> double: 32000.0
float -> double: 1.25
int -> float (rounded): 16777216
long -> double (rounded): 9007199254740992
static int -> long: 7
boolean -> boolean: true
double -> int: rejected
Integer -> int: rejected
```

Hai phép ép kiểu `(int) count.getFloat(obj)` và `(long) huge.getDouble(obj)` **chỉ dùng để in số nguyên sau khi giá trị đã bị làm tròn ở bước widening**, giúp dễ thấy chênh lệch. Chúng không phải conversion được `Field.getFloat` hay `Field.getDouble` tự thực hiện. Kết quả widening từ `16_777_217` thành `float` là `16_777_216.0f`; widening từ `9_007_199_254_740_993L` thành `double` là `9_007_199_254_740_992.0`.

Ở hai `try/catch`, chương trình minh họa **thất bại do kiểu nguồn không phù hợp**, không phải lỗi truy cập private field. `IllegalArgumentException` được bắt tại đúng vị trí để chương trình tiếp tục in những dòng còn lại.

### Đọc giá trị từ static field

Đối với `static` field, tham số receiver `obj` **được bỏ qua**; cách viết rõ ý định nhất là `staticCount.getLong(null)`. Với instance field, phải truyền instance hợp lệ, ví dụ `small.getInt(obj)`. Truyền `null` cho instance field sẽ gây `NullPointerException`, không phải “đọc giá trị mặc định”.

## 4. Widening được phép nhưng vẫn có thể mất precision

Từ “mở rộng” trong JLS không đồng nghĩa rằng mọi giá trị giữ nguyên toàn bộ chữ số có nghĩa. Cần phân biệt hai nhóm:

**Nhóm giữ chính xác giá trị khi widening:**

- `byte -> int/long/double`, `short -> int/long/double`, `char -> int/long/double`;
- `int -> long` và `int -> double`;
- `float -> double` giữ chính xác giá trị số *đã được biểu diễn bởi float* (không khôi phục dữ liệu có thể mất từ lúc tạo float).

**Nhóm có thể bị làm tròn:**

- `int -> float`;
- `long -> float`;
- `long -> double`.

Ví dụ, kiểu `int` có 32 bit, trong khi `float` thường có độ chính xác 24 bit nhị phân của significand. Vì vậy số nguyên `16_777_217 = 2^24 + 1` không thể được biểu diễn chính xác bằng `float`. Tương tự, `double` có 53 bit độ chính xác nhị phân, nên `2^53 + 1` không biểu diễn được chính xác dưới dạng `double`.

Các phép chuyển đổi này vẫn **hợp lệ theo API** và không ném lỗi: chương trình nhận kết quả đã làm tròn. Đây là điểm đặc biệt quan trọng khi xử lý mã định danh số lớn, timestamp có độ phân giải cao, giá trị tiền hoặc các phép so sánh đòi hỏi tuyệt đối chính xác. Với tài chính, nên lựa chọn kiểu dữ liệu và mô hình tính toán thích hợp từ đầu, thay vì dựa vào `float`/`double` để giữ nguyên mọi chữ số thập phân.

Một hiểu nhầm khác là “đọc `float` bằng `getDouble` sẽ làm phép tính ban đầu chính xác hơn”. Thực tế, nó chỉ biểu diễn **đúng giá trị float hiện có** bằng `double`; nếu float đã làm tròn khi được gán, việc đọc sẽ không phục hồi dữ liệu trước đó.

## 5. Primitive getter khác Field.get(Object) như thế nào?

`Field.get(Object)` luôn có kiểu trả về khai báo là `Object`:

- Nếu trường khai báo kiểu primitive, giá trị được **boxing** thành đối tượng wrapper phù hợp (`int -> Integer`, `long -> Long`, `double -> Double`...).
- Nếu trường khai báo kiểu tham chiếu, `get(obj)` trả về chính reference hiện có (có thể là `null`).
- `get(obj)` không tự thay đổi một `long` thành `double` theo kiểu getter đích mà chương trình mong muốn.

Ví dụ (đoạn mã minh họa, giả định `count`, `obj` và `wrapper` là các biến `Field`/`Values` đã khai báo trong ví dụ trên):

```java
Object boxedPrimitive = count.get(obj);  // Integer(16777217)
Object referenced = wrapper.get(obj);    // Integer(99)
int fromReference = ((Integer) referenced).intValue();
```

Ở đây `count` mô tả một trường primitive `int`, còn `wrapper` mô tả một trường reference `Integer`. Cả hai đều cho một `Integer` khi gọi `get`, nhưng lý do là khác nhau: primitive được boxing, còn reference đã lưu sẵn đối tượng `Integer`.

Chính vì vậy, không thể nhìn thấy `get` trả `Integer` rồi suy luận `wrapper.getInt(obj)` cũng sẽ hợp lệ. **Primitive getter kiểm tra kiểu khai báo của field**, không unbox tự động từ một field wrapper. Muốn xử lý nhiều trường có kiểu reference `Number`, trước tiên hãy đọc bằng `get(obj)`, kiểm tra kiểu/null, rồi chọn cách chuyển đổi ở ứng dụng — ý thức rằng `Number.intValue()` có thể thu hẹp, mất phần lẻ hoặc overflow theo kiểu nguồn.

Ví dụ chuyển đổi sau là **chủ động narrowing ngoài Reflection**, không phải hành vi của `getInt`:

```java
double value = price.getDouble(obj); // primitive double field
int narrowed = (int) value;           // Java cast tường minh
```

Không được dùng việc cast tường minh này để biện minh rằng `price.getInt(obj)` đáng lẽ phải thành công.

## 6. Lựa chọn getter theo metadata và yêu cầu dữ liệu

Với code phân tích/serialize nhiều field, nên kiểm tra kiểu khai báo bằng `field.getType()` và quyết định cách đọc dựa trên contract rõ ràng. Ví dụ:

```java
Class<?> type = field.getType();
if (type == int.class) {
    int value = field.getInt(receiver);
    // Xử lý giá trị int.
} else if (type == long.class) {
    long value = field.getLong(receiver);
    // Xử lý giá trị long.
} else if (type == double.class) {
    double value = field.getDouble(receiver);
    // Xử lý giá trị double.
} else {
    Object value = field.get(receiver);
    // Xử lý các kiểu khác, có thể gồm wrapper hoặc null.
}
```

Đoạn trên **không phải chương trình Java độc lập**: cần biến `field`, `receiver`, các khai báo cục bộ đúng phạm vi và xử lý/khai báo `IllegalAccessException`. Nó minh họa một cách dispatch rõ ràng theo kiểu. Nếu nghiệp vụ thực sự muốn mọi trường primitive số đều thành `double`, có thể chọn `getDouble` vì tất cả primitive kiểu số có đường widening tới `double`, nhưng vẫn cần chấp nhận nguy cơ precision loss với `long`.

Một API chỉ nhận kiểu đầu vào chính xác (ví dụ schema phân biệt `int` và `long`) nên kiểm tra `field.getType()` trước, thay vì gọi `getLong` lên mọi field rồi tưởng rằng mọi giá trị được đọc đều vốn là `long`.

**Không nhầm với `Field.setXxx`:** khi *ghi*, nguồn/đích của chuyển đổi đảo vai trò. `getDouble` đọc một `int` là hợp lệ, nhưng điều đó không chứng minh `setInt` sẽ nhận một trường khai báo `double` trong mọi ngữ cảnh khác mà không xét riêng hợp đồng API; hãy đọc tài liệu tương ứng của thao tác ghi. Các setter có quy tắc và hạn chế riêng, nhất là với field `final`.

## 7. Các ngoại lệ và điều kiện truy cập

Ngoài tương thích kiểu, một lần đọc field còn phải hợp lệ về đối tượng nhận, quyền truy cập và khởi tạo lớp:

| Tình huống | Hành vi cần dự kiến |
| --- | --- |
| Getter đích không nhận kiểu primitive của field (`getInt` cho `double`) | `IllegalArgumentException` |
| Primitive getter gọi trên field có kiểu reference (`Integer`, `String`, `Number`) | `IllegalArgumentException` |
| Instance field nhưng receiver `null` | `NullPointerException` |
| Instance field nhưng receiver sai kiểu đối tượng | `IllegalArgumentException` |
| Không có quyền đọc field qua Reflection | `IllegalAccessException` |
| Đọc static field kích hoạt khởi tạo lớp và việc khởi tạo thất bại | `ExceptionInInitializerError` |

Ví dụ `getDeclaredField("hidden")` có thể **tìm thấy** private field, nhưng không có nghĩa `getInt(obj)` được phép đọc. Cần tuân thủ Java language access rules và ranh giới module. Nếu đã có quyền bình thường thì không cần bật cờ suppress; nếu cần deep reflection, `canAccess(receiver)` và `trySetAccessible()` có thể giúp kiểm tra/yêu cầu quyền theo hợp đồng, nhưng không nên coi chúng là giải pháp mặc định để bỏ qua encapsulation.

`IllegalAccessException` là checked exception do các getter khai báo. `IllegalArgumentException` và `NullPointerException` là unchecked; việc `main` khai báo `throws ReflectiveOperationException` trong ví dụ không làm các lỗi dùng getter sai kiểu trở thành checked exception. Đây là lý do ví dụ bắt riêng hai `IllegalArgumentException` có chủ ý.

Khi getter đọc một `static` field, Java có thể khởi tạo declaring class nếu chưa khởi tạo. Nếu khởi tạo thất bại, lỗi liên quan tới quá trình khởi tạo, **không phải chuyển đổi từ `int` sang `long`**.

## 8. Sai lầm phổ biến khi làm việc với Field

**1. “Tên phương thức quyết định kiểu field phải khai báo.”** `getInt` không chỉ dành riêng cho field `int`, mà còn hợp lệ với `byte`, `short`, `char`. Đổi lại, nó không thể đọc trực tiếp field `long`, `float`, `double`.

**2. “Nếu giá trị nằm trong miền int thì getInt(doubleField) được phép.”** Sai; API xét **kiểu field**, không xét giá trị. Field `double` chứa `1.0` vẫn không hợp lệ với `getInt`.

**3. “getDouble không bao giờ mất dữ liệu.”** Sai; `long -> double` có thể mất độ chính xác dù conversion hợp lệ. Widening không nhất thiết là exact.

**4. “Wrapper Integer tương đương primitive int trong Reflection.”** Sai; `Field.getInt` không unbox trường reference. `Field.get` là lựa chọn để lấy wrapper, sau đó ứng dụng tự xử lý.

**5. “getLong có thể đọc float vì long 64 bit.”** Sai; quan hệ widening primitive dựa trên loại số và các quy tắc JLS, không chỉ độ rộng bit. `float -> long` là narrowing và không được `getLong` thực hiện.

**6. “char sang int sẽ trả ký tự như String.”** Sai; `getInt` trả giá trị số của UTF-16 code unit. Với `'A'`, kết quả là `65`, không phải chuỗi `"A"`.

**7. “Receiver luôn phải là instance.”** Sai với `static` field; truyền `null` là cách thể hiện rõ ràng. Nhưng instance field bắt buộc cần receiver hợp lệ.

**8. “Lỗi IllegalAccessException và IllegalArgumentException là giống nhau.”** Sai; một lỗi liên quan **quyền truy cập**, lỗi còn lại thường liên quan **kiểu getter hoặc receiver không phù hợp**. Xử lý đúng nguyên nhân thay vì thử `setAccessible(true)` để chữa mọi lỗi.

## 9. Tự kiểm tra

1. Field khai báo `byte` có thể dùng `getInt`, `getLong`, `getDouble` không? **Có**, vì có các đường widening tương ứng.
2. Field khai báo `char` có thể dùng `getShort` không? **Không**, `char -> short` không phải widening primitive conversion.
3. Field khai báo `float` có thể đọc bằng `getLong` không? **Không**; `float -> long` là narrowing.
4. Field khai báo `long` có thể đọc bằng `getDouble` không? **Có**, nhưng có thể mất precision.
5. Field `Integer` chứa giá trị `42` có đọc được qua `getInt` không? **Không**; đó là reference field. Dùng `get` rồi chuyển đổi có kiểm tra.
6. Field `double` chứa đúng `100.0` có đọc bằng `getInt` được không? **Không**; kiểu khai báo không tương thích.
7. Vì sao `getInt` có thể trả `65` khi field khai báo `char letter = 'A'`? **Vì `char -> int` là widening**, biểu diễn giá trị UTF-16 của `'A'` dưới dạng số.
8. Field `static int` có thể gọi `getLong(null)` không? **Có**: receiver bị bỏ qua cho static field và `int -> long` hợp lệ.

## 10. Tài liệu chính thức

- [Oracle Java SE 21 — `java.lang.reflect.Field`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/reflect/Field.html): hợp đồng của `get`, `getInt`, `getLong`, `getDouble`, các primitive getter khác, kiểu receiver và ngoại lệ.
- [Java Language Specification SE 21 — §5.1.2 Widening Primitive Conversion](https://docs.oracle.com/javase/specs/jls/se21/html/jls-5.html#jls-5.1.2): danh sách chuyển đổi hợp lệ và các trường hợp có thể mất độ chính xác.

**Ghi nhớ:** Khi đọc bằng `Field.getInt/getLong/getDouble`, hãy đi từ **kiểu primitive khai báo của field** đến **kiểu trả về của getter** bằng identity hoặc widening. Không có narrowing tự động, không unbox wrapper field, và widening sang floating-point không phải lúc nào cũng giữ chính xác số ban đầu.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="fields-008">Field.set với kiểu tham chiếu hoặc primitive</a>

<details>
<summary>Xem chi tiết</summary>

# Field.set với kiểu tham chiếu hoặc primitive trong Java Reflection

## 1. Đặt vấn đề và hợp đồng của Field.set

`java.lang.reflect.Field` mô tả một field **cụ thể** được khai báo trong class/interface. `Field.set(Object obj, Object value)` đặt giá trị mới cho field đó nếu quyền truy cập, quyền ghi, đối tượng đích và kiểu dữ liệu đều phù hợp. Cùng một API áp dụng cho field **kiểu tham chiếu** (ví dụ `String`, `Number`, `Integer`, `List<?>`, mảng) và field **primitive** (`boolean`, `byte`, `short`, `char`, `int`, `long`, `float`, `double`), nhưng các quy tắc kiểm tra `value` không giống nhau.

Khi viết công cụ mapping, serializer hoặc binder, cần trả lời bốn câu hỏi độc lập:

1. Field có kiểu gì? `field.getType()` trả về kiểu đã khai báo; `field.getType().isPrimitive()` phân biệt nhánh primitive với reference.
2. `obj` trỏ đến đối tượng hợp lệ không? Instance field yêu cầu object của class khai báo (hoặc subtype); với static field `obj` bị bỏ qua, có thể là `null`.
3. Field có được phép ghi không? `getDeclaredField` tìm thấy field private nhưng chưa chắc truy cập được. `trySetAccessible()` có thể thất bại do module; field `final` còn có giới hạn write access.
4. Giá trị truyền vào có tương thích với **kiểu của field** không? Với reference, đó là quan hệ assignability của object. Với primitive, đó là **unboxing** wrapper rồi identity/widening primitive conversion.

`Field.set` không parse số từ chuỗi, không ép kiểu tùy ý, không tự tạo object, không bảo đảm an toàn generic và không bỏ qua giới hạn của Java Platform Module System.

Các chữ ký API liên quan:

~~~java
// Chữ ký API minh họa; không phải chương trình biên dịch độc lập.
public void set(Object obj, Object value)
        throws IllegalArgumentException, IllegalAccessException;
public void setInt(Object obj, int i)
        throws IllegalArgumentException, IllegalAccessException;
~~~

Khi viết `field.set(obj, 42)`, compiler boxing giá trị literal `42` thành `Integer` để truyền qua tham số `Object`. Nếu field thực là `int`, Reflection unboxing `Integer` thành `int` trước khi ghi. Không nhầm boxing tại chỗ gọi với unboxing trong implementation của `Field.set`.

## 2. Quy tắc cho field kiểu tham chiếu

Field tham chiếu có kiểu `T` nhận `null` hoặc một object có thể gán cho `T` theo kiểu runtime. Những giá trị không tương thích khiến `Field.set` ném `IllegalArgumentException`, dù có thể chuyển đổi được qua logic do ứng dụng tự viết.

| Field khai báo | Value đưa vào `set` | Kết quả |
| --- | --- | --- |
| `String name` | `"Lan"` | Thành công vì đúng kiểu. |
| `CharSequence text` | `new StringBuilder("hi")` | Thành công vì implements `CharSequence`. |
| `Number amount` | `Integer.valueOf(10)` | Thành công vì `Integer` là subtype của `Number`. |
| `Object payload` | `new int[] {1, 2}` | Thành công, mảng cũng là object. |
| `Integer boxed` | `Integer.valueOf(10)` | Thành công; `Integer` **không** phải primitive. |
| `Integer boxed` | `Long.valueOf(10)` | Thất bại; không tự ép wrapper `Long` sang `Integer`. |
| `String name` | `null` | Thành công nếu có quyền ghi field. |
| `String name` | `Integer.valueOf(10)` | Thất bại; API không gọi `toString()`. |

Kiểm tra thông thường trước khi ghi reference:

~~~java
// Đoạn chèn vào method đã có Field field, Object obj, Object value.
Class<?> type = field.getType();
if (!type.isPrimitive()) {
    if (value != null && !type.isInstance(value)) {
        throw new IllegalArgumentException("Reference type mismatch");
    }
    field.set(obj, value);
}
~~~

`Class.isAssignableFrom` cũng kiểm tra được quan hệ kiểu khi `value != null`, ví dụ `type.isAssignableFrom(value.getClass())`. Không gọi `value.getClass()` với `null`; cũng đừng dùng `isInstance` để suy ra phép chuyển từ wrapper sang primitive.

**Mảng được xếp vào nhóm reference.** Field `int[] values` có kiểu runtime là array class, tức `valuesField.getType().isPrimitive() == false`. Có thể gán một `int[]` khác hoặc `null`; không gán được `Integer[]` thay `int[]` bằng auto-boxing của từng phần tử. Muốn sửa một phần tử mảng hiện có là thao tác khác (`Array.setInt` hoặc code mảng thông thường), không phải `Field.set` trên field.

**Generic bị erasure:** Một field `List<String> names` vẫn có `getType() == List.class`. `Field.set` không kiểm tra parameter `String` của từng phần tử, do đó việc gán `List<Integer>` có thể qua được kiểm tra kiểu runtime dù sau này code đọc phần tử như `String` sẽ gặp `ClassCastException`. `getGenericType()` phản ánh khai báo tham số hóa để framework tự xây dựng kiểm tra bổ sung.

## 3. Quy tắc cho field primitive: unboxing và widening

`Field.set` nhận `Object value` nên muốn ghi field primitive thì đối tượng giá trị thông thường là một trong tám wrapper: `Boolean`, `Byte`, `Short`, `Character`, `Integer`, `Long`, `Float`, `Double`. Reflection thử unboxing, sau đó dùng identity hoặc **widening primitive conversion** phù hợp với Java. API không thực hiện narrowing dù số thực tế có thể nằm gọn trong miền đích.

| Kiểu primitive sau khi unbox | Field đích cho phép |
| --- | --- |
| `boolean` (từ `Boolean`) | `boolean` |
| `byte` (từ `Byte`) | `byte`, `short`, `int`, `long`, `float`, `double` |
| `short` (từ `Short`) | `short`, `int`, `long`, `float`, `double` |
| `char` (từ `Character`) | `char`, `int`, `long`, `float`, `double` |
| `int` (từ `Integer`) | `int`, `long`, `float`, `double` |
| `long` (từ `Long`) | `long`, `float`, `double` |
| `float` (từ `Float`) | `float`, `double` |
| `double` (từ `Double`) | `double` |

Ví dụ có thể lý giải theo thứ tự:

- `int count` nhận `Integer.valueOf(7)`, `Byte.valueOf((byte) 7)`, `Character.valueOf('A')`: identity hoặc widening đến `int`.
- `long elapsed` nhận `Integer.valueOf(7)`: `Integer → int → long`.
- `double ratio` nhận `Float.valueOf(1.5f)`: `Float → float → double`.
- `int count` **không** nhận `Long.valueOf(7L)`: `long → int` là narrowing bị cấm.
- `short small` **không** nhận `Integer.valueOf(1)`: số `1` nhỏ vẫn không tạo quyền narrowing `int → short`.
- `boolean active` chỉ nhận `Boolean` phù hợp; giá trị `"true"` và `Integer.valueOf(1)` không được tự parse/coerce.
- Mọi field primitive đều **không** nhận `null`: không unbox được, `Field.set` ném `IllegalArgumentException`. Field reference nhận `null` nếu quyền ghi hợp lệ.

Widening là quy tắc **kiểu**, không phải cam kết không mất precision. Ví dụ `long → float` được phép nhưng số nguyên lớn có thể mất độ chính xác; công cụ nghiệp vụ có thể áp dụng quy tắc chặt hơn khi cần bảo toàn giá trị.

Một công cụ nhận dữ liệu người dùng từ `String`/JSON cần **converter tường minh** trước khi gọi `set`. Chẳng hạn parse `"42"` thành `Integer.valueOf(42)`, kiểm tra tràn số rồi mới ghi `int`; không truyền thẳng `"42"` vào `Field.set` mong API tự xử lý.

## 4. Các phương thức setXxx cho primitive

Ngoài `set(Object, Object)`, `Field` cung cấp `setBoolean`, `setByte`, `setShort`, `setChar`, `setInt`, `setLong`, `setFloat` và `setDouble`. Kiểu của tham số thứ hai trong các phương thức này là primitive tương ứng.

~~~java
// Mảnh minh họa các API, không phải source độc lập.
field.setBoolean(target, true);
field.setByte(target, (byte) 3);
field.setShort(target, (short) 3);
field.setChar(target, 'A');
field.setInt(target, 3);
field.setLong(target, 3L);
field.setFloat(target, 1.5f);
field.setDouble(target, 1.5);
~~~

Phải phân biệt **kiểu giá trị nguồn** và **kiểu field đích**. Ví dụ `setInt(target, 9)` trên field `long` thành công nhờ `int → long`, nhưng `setLong(target, 9L)` trên field `int` thất bại vì narrowing. Tương tự, `setDouble` không tự hạ xuống field `float`.

**Không dùng `setInt` để ghi field `Integer`:** Field `Integer` là reference, không phải primitive `int`. Trên Java 17/21, `setInt` vào field `Integer` ném `IllegalArgumentException`; dùng `set(target, Integer.valueOf(9))`. Trên field `Number`, cũng hãy dùng `set` với đối tượng `Integer` thay vì setter primitive. Chọn API dựa vào `field.getType()`:

~~~java
// Mảnh minh họa: field và target đã được xác định, kiểm tra quyền riêng.
Class<?> type = field.getType();
if (type == int.class) {
    field.setInt(target, 9);
} else if (type == Integer.class) {
    field.set(target, Integer.valueOf(9));
} else {
    // Kiểm tra / chuyển đổi theo kiểu khai báo thực tế.
}
~~~

`int.class` và `Integer.class` là hai đối tượng `Class` khác nhau; Reflection không coi chúng là một khai báo field.

## 5. Ví dụ Java 17+ hoàn chỉnh: các phép ghi hợp lệ và không hợp lệ

Tạo file `FieldSetTypesDemo.java`:

~~~java
import java.lang.reflect.Field;

public class FieldSetTypesDemo {
    static class Sample {
        private Number amount;
        private String label = "initial";
        private int pieces;
        private long ticks;
        private double price;
        private boolean available;
        private Integer boxed;
        private static int visits = 1;
    }

    @FunctionalInterface
    interface WriteAction {
        void run() throws IllegalAccessException;
    }

    static Field field(String name) throws NoSuchFieldException {
        Field result = Sample.class.getDeclaredField(name);
        if (!result.trySetAccessible()) {
            throw new IllegalStateException("Cannot access: " + name);
        }
        return result;
    }

    static void expectFailure(String label, WriteAction action) {
        try {
            action.run();
            System.out.println(label + " -> unexpected success");
        } catch (IllegalAccessException
                 | IllegalArgumentException
                 | NullPointerException error) {
            System.out.println(label + " -> "
                    + error.getClass().getSimpleName());
        }
    }

    public static void main(String[] args) throws Exception {
        Sample sample = new Sample();
        Field amount = field("amount");
        Field label = field("label");
        Field pieces = field("pieces");
        Field ticks = field("ticks");
        Field price = field("price");
        Field available = field("available");
        Field boxed = field("boxed");
        Field visits = field("visits");

        // Reference: object tương thích, hoặc null.
        amount.set(sample, Integer.valueOf(25));
        label.set(sample, null);
        boxed.set(sample, Integer.valueOf(12));

        // Primitive: unboxing rồi widening nếu cần.
        pieces.set(sample, Byte.valueOf((byte) 7));  // byte -> int
        ticks.setInt(sample, 9);                     // int -> long
        price.set(sample, Float.valueOf(1.5f));     // float -> double
        available.setBoolean(sample, true);

        // Static field: target là null.
        visits.setInt(null, 4);

        System.out.println("amount=" + sample.amount
                + " (" + sample.amount.getClass().getSimpleName() + ")");
        System.out.println("label=" + sample.label
                + ", pieces=" + sample.pieces
                + ", ticks=" + sample.ticks
                + ", price=" + sample.price
                + ", available=" + sample.available
                + ", boxed=" + sample.boxed
                + ", visits=" + Sample.visits);

        expectFailure("reference wrong type",
                () -> amount.set(sample, "not a Number"));
        expectFailure("primitive narrowing",
                () -> pieces.set(sample, Long.valueOf(10L)));
        expectFailure("primitive null",
                () -> pieces.set(sample, null));
        expectFailure("instance null target",
                () -> label.set(null, "new"));
        expectFailure("primitive setter on wrapper",
                () -> boxed.setInt(sample, 8));
    }
}
~~~

Biên dịch, chạy:

~~~bash
javac -encoding UTF-8 FieldSetTypesDemo.java
java FieldSetTypesDemo
~~~

Đầu ra:

~~~text
amount=25 (Integer)
label=null, pieces=7, ticks=9, price=1.5, available=true, boxed=12, visits=4
reference wrong type -> IllegalArgumentException
primitive narrowing -> IllegalArgumentException
primitive null -> IllegalArgumentException
instance null target -> NullPointerException
primitive setter on wrapper -> IllegalArgumentException
~~~

Giải thích kết quả:

- `amount` khai báo `Number` nhưng giữ object `Integer`; đây là gán reference tương thích.
- `pieces` khai báo `int`, nhận `Byte` rồi unboxing thành `byte` và widening thành `int`.
- `ticks` khai báo `long`, nhận giá trị nguồn primitive `int` thông qua `setInt`.
- `price` khai báo `double`, nhận `Float` unboxing và widening.
- `boxed` là reference `Integer` nên `set` nhận `Integer` thành công; riêng `setInt` không áp dụng cho field reference.
- `set(sample, null)` là yêu cầu ghi **giá trị null**, còn `set(null, "new")` cung cấp **target null**. Điều thứ hai gây `NullPointerException` với instance field. Với static field, target null là hợp lệ.

`trySetAccessible()` trong ví dụ có thể bật vì mã và class model thuộc cùng unnamed module; chương trình không cần thư viện bên ngoài, không truy cập field nội bộ JDK. Ở ứng dụng module tách biệt, cần kiểm tra `exports`/`opens` và caller thực tế.

## 6. Field thuộc lớp cha, field bị che và kiểu đích

`Field` ràng buộc với **khai báo field gốc**, không tự chọn lại theo tên ở kiểu runtime của `obj`. Nếu hai tầng phân cấp cùng tên `id`, `Parent.class.getDeclaredField("id")` và `Child.class.getDeclaredField("id")` là hai đối tượng `Field` khác nhau, có thể mang **hai kiểu khác nhau**. Các luật type conversion áp dụng theo `field.getType()` của từng đối tượng `Field`.

Ví dụ độc lập, lưu `HiddenFieldDemo.java`:

~~~java
public class HiddenFieldDemo {
    static class Parent {
        int id = 1;
    }
    static class Child extends Parent {
        long id = 2;
    }

    public static void main(String[] args) throws Exception {
        Child child = new Child();
        Parent.class.getDeclaredField("id").setInt(child, 5);
        Child.class.getDeclaredField("id").setLong(child, 8L);

        System.out.println("Parent.id=" + ((Parent) child).id);
        System.out.println("Child.id=" + child.id);
    }
}
~~~

Chạy:

~~~bash
javac -encoding UTF-8 HiddenFieldDemo.java
java HiddenFieldDemo
~~~

Output:

~~~text
Parent.id=5
Child.id=8
~~~

Một công cụ tự duyệt superclass cần nêu rõ chính sách khi field trùng tên: chọn field gần class con nhất, chọn field của cha hoặc báo xung đột. Không nên dùng tên `id` một mình để quyết định kiểu giá trị mà không xác định `getDeclaringClass()`.

## 7. Quyền truy cập, final và lỗi runtime

Chọn value đúng kiểu **chưa đủ** để `Field.set` thành công.

| Lỗi hoặc điều kiện | Ý nghĩa thực tế |
| --- | --- |
| `NoSuchFieldException` lúc tra cứu | Sai tên hoặc tìm ở class không khai báo field đó; `getDeclaredField` không tự duyệt cha. |
| `trySetAccessible() == false` | Không thể bỏ qua access check, thường do module/package không mở. |
| `InaccessibleObjectException` khi `setAccessible(true)` | Deep reflection bị Java Platform Module System chặn. |
| `IllegalAccessException` ở `set` | Field không cho phép ghi hoặc access check không đạt. |
| `IllegalArgumentException` ở `set` | Sai kiểu value, không unboxing/widening được hoặc target không đúng loại. |
| `NullPointerException` ở `set` | Target là null cho instance field. |
| `ExceptionInInitializerError` | Khởi tạo class của static field bị lỗi. |

Các field **`static final`**, field `final` trong record và field `final` trong hidden class không có quyền ghi qua `Field.set`, ngay cả khi `setAccessible(true)` đã bật được quyền đọc. `private final` instance trong class thông thường có quy tắc đặc biệt ở Java 17: đôi khi Reflection cho phép ghi nếu mở quyền hợp lệ, nhưng sửa sau khi object được công bố có thể tạo hành vi khó đoán; không dùng cách này để cập nhật trạng thái nghiệp vụ.

Khi target khác `null`, nó phải là instance của class khai báo (hoặc subtype phù hợp). Khi static field được ghi, đối tượng target bị bỏ qua; thao tác có thể khiến class khai báo được khởi tạo. Vì vậy, không coi mọi lỗi tại `Field.set` là “ép kiểu thất bại”.

Quyền `setAccessible` không phải quyền cập nhật tất cả mọi field. Trong JPMS, mở private member ở module khác thường cần package được `opens` đến module của caller; `exports` cho public API không tự cấp deep reflection.

## 8. Thiết kế một bộ chuyển dữ liệu có chủ đích

Với mapper/binder, luồng xử lý nên dựa vào **kiểu đích**, không chỉ dựa vào kiểu nguồn:

~~~text
Field + target + rawValue
   |
   +-- Xác định declaringClass, static/instance, final và quyền truy cập
   |
   +-- Class<?> type = field.getType()
   |
   +-- type.isPrimitive() ?
          |
          +-- false: rawValue == null hoặc type.isInstance(rawValue)?
          |             đúng -> field.set(target, rawValue)
          |             sai  -> converter riêng hoặc lỗi
          |
          +-- true: rawValue là wrapper tương ứng?
                        unboxing -> identity/widening hợp lệ?
                           đúng -> field.set hoặc setXxx
                           sai  -> converter có range check hoặc lỗi
~~~

Các quyết định phải được ghi rõ ở tầng nghiệp vụ:

- Với primitive nhận input `null`: báo thiếu giá trị hay dùng default đã thống nhất; không âm thầm biến `null` thành `0` hay `false`.
- Với chuỗi số: dùng parser phù hợp, xử lý overflow và precision; `Field.set` không parse.
- Với generic: có thể cần kiểm tra `getGenericType()` và từng phần tử, vì runtime field check không đủ.
- Với field final hoặc object bất biến: ưu tiên constructor, factory, builder hoặc tạo object mới.
- Với conversion thu hẹp: chỉ cho phép khi chính sách nghiệp vụ yêu cầu và có kiểm tra overflow, không giả định `Field.set` hỗ trợ.
- Với lỗi: báo vị trí `declaringClass.fieldName`, kiểu đích và kiểu nguồn, tránh log giá trị nhạy cảm.
- Với hiệu năng: cache metadata khi có nhiều lần gán, nhưng chú ý vòng đời `ClassLoader` và quyền truy cập.

## 9. Các bẫy thường gặp

1. **Nhầm `Integer` với `int`:** `Integer.class.isPrimitive() == false`. `setInt` không phải lối tắt an toàn cho field `Integer`; dùng `set` với `Integer`.
2. **Nhầm boxing với chuyển đổi tùy ý:** `field.set(obj, 3)` có boxing ở Java caller, nhưng field `short` vẫn không nhận `Integer(3)` qua narrowing `int → short`.
3. **Nhầm `null` ở hai tham số:** `set(obj, null)` có thể ghi field reference; `set(null, value)` ném lỗi cho instance field.
4. **Nhầm String parse với primitive widening:** `"5"` không được Reflection biến thành `int 5`. Việc chuyển kiểu dữ liệu đầu vào thuộc về bộ chuyển của ứng dụng.
5. **Nhầm generic declaration với runtime check:** `List<String>` không bảo đảm giá trị đưa vào bằng `Field.set` chứa toàn String.
6. **Nhầm array primitive với primitive field:** `int[]` là reference; một mảng `Integer[]` không tự thay thế được `int[]`.
7. **Nhầm quyền truy cập với quyền ghi:** `trySetAccessible` thành công chưa chắc field final/modifier/module/target hợp lệ.
8. **Bỏ qua mất precision ở widening số:** một phép `long → float` hợp lệ theo kiểu vẫn có thể mất thông tin số học.

## 10. Bài tập và kiểm chứng

- Với field `int`, thử các wrapper `Byte`, `Short`, `Character`, `Integer`, `Long`, `Float`, `Double`; lý giải từng kết quả từ bảng widening.
- Với field `long`, so sánh `setInt(target, 9)` và `setDouble(target, 9.0)`.
- Với field `Integer`, so sánh `set(target, Integer.valueOf(9))` và `setInt(target, 9)`.
- Với field `CharSequence`, thử `String`, `StringBuilder`, `null` và `Integer`.
- Với field `List<String>`, gán `List<Integer>` rồi đọc như String để thấy hậu quả của generic erasure.
- Với `static int counter`, gọi `setInt(null, 4)`; so sánh với `int counter` instance, target `null`.
- Mở rộng `HiddenFieldDemo` bằng `getDeclaringClass()` và chứng minh mỗi `Field` mang kiểu từ khai báo riêng.

## 11. Tài liệu tham khảo

- [Java SE 17 — Field](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Field.html): `set`/`setXxx`, primitive unboxing/widening, target, exception, write access của field final.
- [Java SE 17 — AccessibleObject](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/AccessibleObject.html): `trySetAccessible` và quyền deep reflection dưới JPMS.
- [Java Language Specification `5.1.2](https://docs.oracle.com/javase/specs/jls/se17/html/jls-5.html#jls-5.1.2): các chuyển đổi widening primitive hợp lệ.

**Ghi nhớ:** Field tham chiếu theo **khả năng gán object/null**; field primitive theo **wrapper → unboxing → identity/widening**. Xác định rõ kiểu field và target, sau đó mới chọn `set` hoặc `setXxx` và xử lý quyền ghi.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="fields-009">Các phương thức setInt, setLong và giới hạn widening</a>

<details>
<summary>Xem chi tiết</summary>

# Field.setInt(), Field.setLong() và giới hạn widening trong Java Reflection

## 1. Học gì và vì sao cần hiểu quy tắc chuyển kiểu?

Với Java Reflection, đối tượng `java.lang.reflect.Field` đại diện cho một trường (field) đã được khai báo trong lớp hoặc interface. Ngoài `Field.set(Object, Object)` nhận giá trị dưới dạng đối tượng, Java cung cấp các phương thức chuyên cho kiểu nguyên thủy (primitive) như `setByte`, `setShort`, `setChar`, `setInt`, `setLong`, `setFloat` và `setDouble`.

Khi đã lấy được `Field`, ta có thể muốn ghi một giá trị số mà không cần đóng hộp tường minh thành `Integer` hoặc `Long`:

~~~java
Field field = Counter.class.getField("total"); // total là public long
field.setInt(counter, 42);                      // hợp lệ: int -> long
~~~

Đoạn trên chỉ minh họa khi đã có một đối tượng `counter` thuộc kiểu `Counter`. Ví dụ hoàn chỉnh, biên dịch được ở phần 5.

Điểm quan trọng: **kiểu của giá trị truyền vào phương thức `setXxx` không cần trùng hoàn toàn với kiểu khai báo của trường, nhưng chỉ được chuyển bằng phép đồng nhất kiểu (identity) hoặc mở rộng kiểu nguyên thủy (widening primitive conversion).** Reflection từ chối thu hẹp kiểu (narrowing), kể cả khi giá trị cụ thể nằm trong miền biểu diễn của kiểu đích.

Bài học này giải thích hai phương thức trọng tâm `setInt` và `setLong`, cách xét hướng chuyển đổi, sự khác biệt giữa `getXxx` và `setXxx`, tác động của precision và các lỗi thực tế.

## 2. Cơ chế của hai phương thức

Hai khai báo trong API `Field`:

~~~java
void setInt(Object obj, int i)
        throws IllegalArgumentException, IllegalAccessException;

void setLong(Object obj, long l)
        throws IllegalArgumentException, IllegalAccessException;
~~~

- `obj` là đối tượng chứa trường instance. Với trường `static`, có thể truyền `null` vì không có đối tượng nhận; với trường instance, truyền `null` gây `NullPointerException`.
- `i` là một **giá trị `int`**; `l` là một **giá trị `long`**. Loại tham số được cố định bởi chữ ký phương thức, không được suy từ trường đích.
- `Field` biết kiểu đích qua `getType()`; ở thời điểm ghi, Java kiểm tra giá trị đầu vào có thể được gán cho trường bằng phép đồng nhất hoặc widening primitive không.
- Nếu kiểu đích cho phép, giá trị được chuyển đổi rồi ghi vào trường. Nếu cần narrowing hoặc kiểu đích không tương thích, phương thức ném `IllegalArgumentException`. Nếu không có quyền ghi do kiểm tra truy cập hoặc tính `final`, có thể gặp `IllegalAccessException`.

Hình dung chiều dữ liệu:

~~~text
setInt(receiver, int)   -- identity / widening --> primitive field
setLong(receiver, long) -- identity / widening --> primitive field

Không có nhánh tự động narrowing để "ép vừa" trường đích.
~~~

**Phân biệt với ép kiểu trong Java:** phép ép tường minh như `(byte) 200` hoặc `(int) someLong` được thực hiện trước khi gọi setter, do lập trình viên yêu cầu. Bản thân `setInt`/`setLong` không thực hiện ép kiểu thu hẹp.

### Bảng tương thích quan trọng nhất

| Kiểu trường được khai báo | `setInt(obj, int)` | `setLong(obj, long)` | Lý do |
| --- | --- | --- | --- |
| `byte` | ❌ | ❌ | `int -> byte`, `long -> byte` đều là narrowing |
| `short` | ❌ | ❌ | Không chấp nhận `int/long -> short` |
| `char` | ❌ | ❌ | `int/long -> char` không phải widening |
| `int` | ✅ | ❌ | `int -> int` là identity; `long -> int` là narrowing |
| `long` | ✅ | ✅ | `int -> long` là widening; `long -> long` là identity |
| `float` | ✅ | ✅ | `int -> float` và `long -> float` là widening |
| `double` | ✅ | ✅ | `int -> double` và `long -> double` là widening |
| `boolean` | ❌ | ❌ | Không có chuyển đổi số sang `boolean` |
| `Integer`, `Long`, `Number` hoặc kiểu tham chiếu khác | ❌ | ❌ | Hai phương thức `setInt`/`setLong` dành cho trường primitive, không phải trường wrapper/reference |

Ở đây ✅ có nghĩa **hợp lệ về kiểu chuyển đổi** nếu đối tượng nhận và quyền truy cập cũng hợp lệ; nó không bảo đảm lời gọi luôn thành công khi trường `private`, `final` hoặc đối tượng không đúng kiểu.

Một cách ghi nhớ ngắn:

- `setInt` ghi được vào: **`int` → `long` → `float` → `double`**.
- `setLong` ghi được vào: **`long` → `float` → `double`**.
- **Không được đi ngược chiều về kiểu nhỏ hơn**, bất kể số cụ thể là `0`, `7` hay nằm trong khoảng của `byte`.

## 3. Widening là quy tắc kiểu, không phải so sánh kích thước giá trị

Theo quy tắc chuyển đổi kiểu nguyên thủy của Java, những hướng widening số thường gặp gồm:

~~~text
byte  -> short -> int -> long -> float -> double
          ^       ^       ^       ^        ^
char  ------------+-------+-------+--------+
~~~

Sơ đồ chỉ diễn đạt các hướng đi tiêu biểu; `char` **không** widening sang `byte` hay `short`. Bảng đầy đủ các hướng trực tiếp:

| Kiểu nguồn | Kiểu đích chấp nhận qua widening |
| --- | --- |
| `byte` | `short`, `int`, `long`, `float`, `double` |
| `short` | `int`, `long`, `float`, `double` |
| `char` | `int`, `long`, `float`, `double` |
| `int` | `long`, `float`, `double` |
| `long` | `float`, `double` |
| `float` | `double` |
| `double` | Không có kiểu primitive khác rộng hơn |
| `boolean` | Không widening sang kiểu số nào |

Mỗi kiểu cũng luôn cho phép phép chuyển đồng nhất sang chính nó. `setInt` bắt đầu từ nguồn `int`, còn `setLong` bắt đầu từ nguồn `long`; vì vậy chỉ cần xét hai hàng này cộng identity.

**Tại sao `setInt(fieldByte, 7)` lại lỗi dù số 7 vừa `byte`?** Java có quy tắc *thu hẹp hằng số tại thời điểm biên dịch* cho một số phép gán (ví dụ `byte b = 7;`). Đó là một quy tắc có điều kiện trong ngữ cảnh gán của mã nguồn, **không phải** quy tắc được `Field.setInt` áp dụng cho một `int` ở runtime. Reflection không nhìn vào giá trị rồi tự rút về `byte`.

Hai ví dụ độc lập:

~~~java
byte b = 7;                        // mã nguồn hợp lệ vì hằng int đặc biệt
Field f = Example.class.getField("b");  // Example có public byte b
f.setInt(example, 7);             // IllegalArgumentException
f.setByte(example, (byte) 7);     // đúng phương thức cho trường byte
~~~

Đoạn mã trên là minh họa, giả định `example` là instance hợp lệ của `Example`; chương trình đầy đủ ở phần 5.

Tương tự, `setLong(fieldInt, 5L)` bị từ chối, ngay cả khi `5L` nằm trong miền `int`. Nếu nghiệp vụ cho phép cắt giá trị, bạn phải đưa ra kiểm tra phạm vi và phép chuyển đổi tường minh trước; để `setInt` nhận `int` chỉ sau khi đã xác minh.

## 4. Gọi setter đúng cách và phân biệt với Field.set(Object, Object)

### 4.1. Chọn overload theo kiểu nguồn và kiểu đích

~~~java
Field f = Data.class.getField("total"); // public long total
Data d = new Data();

f.setInt(d, 123);                       // hợp lệ: int -> long
f.setLong(d, 123L);                     // hợp lệ: long -> long
~~~

Nếu trường là `public int count`:

~~~java
Field f = Data.class.getField("count"); // public int count
f.setInt(d, 123);                       // hợp lệ
f.setLong(d, 123L);                     // IllegalArgumentException
~~~

**Một bẫy cú pháp:** `f.setLong(d, 123)` vẫn biên dịch, vì trình biên dịch chuyển literal `123` từ `int` sang tham số `long` của phương thức. Khi `setLong` thực sự chạy, nó đang ghi **`long`** chứ không “ghi `int`”, nên một trường `int` vẫn bị từ chối. Tên setter quyết định kiểu nguồn mà runtime xét.

### 4.2. `set(Object, Object)` có thể unboxing rồi widening

Với một trường primitive, `Field.set(obj, value)` có thể **mở hộp (unboxing)** giá trị wrapper sau đó widening khi cần:

~~~java
Field total = Data.class.getField("total"); // long
total.set(d, Integer.valueOf(25));          // Integer -> int -> long: hợp lệ
~~~

Tương đương về kiểu giá trị với `total.setInt(d, 25)`. Ngược lại, với một trường reference:

~~~java
Field boxed = Data.class.getField("boxed"); // Integer boxed
boxed.set(d, Integer.valueOf(25));          // hợp lệ: đặt tham chiếu Integer
boxed.setInt(d, 25);                        // IllegalArgumentException
~~~

**Phân biệt hai khái niệm:** boxing `int -> Integer` thường xảy ra khi truyền đối số cho phương thức nhận `Object`, nhưng không có chuyện `Field.setInt` tự đổi một field khai báo `Integer` thành primitive. Setter chuyên biệt làm việc theo các quy tắc primitive field.

Khi chương trình nhận dữ liệu không biết kiểu trước, nên kiểm tra `field.getType()` và xử lý chuyển đổi một cách có chủ đích thay vì thử setter tùy ý rồi bắt lỗi.

### 4.3. Đừng đảo chiều quy tắc giữa get và set

- `field.setInt(obj, 7)`: nguồn là `int`, hỏi trường đích có nhận được `int` không.
- `field.getLong(obj)`: nguồn là **kiểu đang khai báo của trường**, hỏi giá trị lấy ra có widening sang `long` được không.

Do đó, `getLong` trên trường `int` hợp lệ, nhưng `setLong` trên trường `int` không hợp lệ. `getInt` trên trường `long` cũng bị từ chối vì `long -> int` là narrowing. Phần 5 cho ví dụ thực nghiệm về bất đối xứng này.

## 5. Chương trình Java 17+ hoàn chỉnh, chạy được

Chương trình này dùng **trường public, không final** để quan sát quy tắc chuyển kiểu mà không bị rối với quyền truy cập; có thêm ví dụ trường `static`, boxing và chiều của getter. Các dòng in lỗi được chuẩn hóa theo tên loại ngoại lệ để kết quả không phụ thuộc thông điệp lỗi của JDK.

Tạo tệp **`WideningFieldDemo.java`**:

~~~java
import java.lang.reflect.Field;

public class WideningFieldDemo {
    static class Metrics {
        public int count;
        public long total;
        public float roundedFloat;
        public double roundedDouble;
        public byte tiny;
        public char symbol;
        public Integer boxed;
        public static long shared;
    }

    @FunctionalInterface
    interface FieldAction {
        void run() throws IllegalAccessException;
    }

    static void attempt(String label, FieldAction action) {
        try {
            action.run();
            System.out.println(label + ": OK");
        } catch (IllegalArgumentException ex) {
            System.out.println(label + ": IllegalArgumentException");
        } catch (IllegalAccessException ex) {
            System.out.println(label + ": IllegalAccessException");
        }
    }

    public static void main(String[] args) throws Exception {
        Metrics m = new Metrics();
        Class<Metrics> type = Metrics.class;

        Field count = type.getField("count");
        Field total = type.getField("total");
        Field roundedFloat = type.getField("roundedFloat");
        Field roundedDouble = type.getField("roundedDouble");
        Field tiny = type.getField("tiny");
        Field symbol = type.getField("symbol");
        Field boxed = type.getField("boxed");
        Field shared = type.getField("shared");

        count.setInt(m, 7);
        total.setInt(m, 42);
        roundedFloat.setInt(m, 16_777_217);
        roundedDouble.setLong(m, 9_007_199_254_740_993L);
        shared.setLong(null, 99);

        System.out.println("count=" + m.count
                + ", total=" + m.total
                + ", shared=" + Metrics.shared);
        System.out.println("int->float rounded=" + (int) m.roundedFloat);
        System.out.println("long->double rounded=" + (long) m.roundedDouble);

        attempt("int->byte", () -> tiny.setInt(m, 7));
        attempt("int->char", () -> symbol.setInt(m, 65));
        attempt("long->int", () -> count.setLong(m, 8L));
        attempt("int->Integer", () -> boxed.setInt(m, 3));

        attempt("Integer->long via set",
                () -> total.set(m, Integer.valueOf(88)));
        System.out.println("total after set=" + m.total);

        System.out.println("getLong(int field)=" + count.getLong(m));
        attempt("getInt(long field)",
                () -> System.out.println(total.getInt(m)));
    }
}
~~~

Biên dịch và chạy từ thư mục chứa tệp (JDK 17 trở lên):

~~~bash
javac --release 17 WideningFieldDemo.java
java WideningFieldDemo
~~~

**Kết quả stdout đã xác minh trên JDK 21 khi biên dịch với `--release 17`:**

~~~text
count=7, total=42, shared=99
int->float rounded=16777216
long->double rounded=9007199254740992
int->byte: IllegalArgumentException
int->char: IllegalArgumentException
long->int: IllegalArgumentException
int->Integer: IllegalArgumentException
Integer->long via set: OK
total after set=88
getLong(int field)=7
getInt(long field): IllegalArgumentException
~~~

### Vì sao chương trình cho kết quả này?

1. `count.setInt(m, 7)` ghi nguyên giá trị vào trường `int`; `total.setInt(m, 42)` ghi vào `long` bằng widening. `shared.setLong(null, 99)` ghi vào trường `static long` mà không cần instance.
2. `roundedFloat.setInt(m, 16_777_217)` **được phép về kiểu** nhưng `float` không biểu diễn chính xác số này; khi ép lại sang `int` để quan sát, nhận `16_777_216`.
3. `roundedDouble.setLong(m, 9_007_199_254_740_993L)` **được phép về kiểu**, nhưng `double` làm tròn xuống giá trị biểu diễn gần nhất là `9_007_199_254_740_992`.
4. Bốn dòng lỗi tiếp theo chứng minh `int -> byte`, `int -> char`, `long -> int` đều không hợp lệ, và trường `Integer` không được nhận qua `setInt`.
5. `total.set(m, Integer.valueOf(88))` thành công vì `Field.set` mở hộp từ `Integer` thành `int` rồi widening sang `long`.
6. `count.getLong(m)` đọc từ `int` rồi widening sang `long`. Trái lại `total.getInt(m)` đòi hỏi `long -> int` nên thất bại.

Chạy ví dụ bằng classpath như lệnh trên sẽ tránh các rào cản truy cập giữa named module khác nhau; các trường được công khai để chỉ tập trung vào chuyển kiểu.

## 6. Widening không phải lời hứa giữ nguyên mọi chữ số

Một nhầm lẫn phổ biến là: “widening nghĩa là kiểu đích to hơn, vậy không bao giờ mất số liệu”. Thực tế, **các phép widening số nguyên sang số thực dấu phẩy động có thể mất độ chính xác (precision)**.

| Phép chuyển | Có hợp lệ? | Giá trị có được giữ chính xác cho mọi đầu vào? |
| --- | --- | --- |
| `int -> long` | Có | Có, mọi giá trị `int` biểu diễn được bằng `long` |
| `int -> float` | Có | Không, `float` chỉ có khoảng 24 bit precision ở phần định trị |
| `int -> double` | Có | Có, mọi `int` biểu diễn chính xác được bằng `double` |
| `long -> float` | Có | Không, có thể làm tròn |
| `long -> double` | Có | Không, `double` có khoảng 53 bit precision; các `long` lớn có thể làm tròn |

**Ví dụ mất precision:** `16_777_217` khác `16_777_216` một đơn vị, nhưng `float` ở vùng giá trị đó không chứa được số lẻ này chính xác. Tương tự, `9_007_199_254_740_993L` nằm vượt khoảng nguyên liên tiếp mà `double` có thể biểu diễn chính xác.

Widening không ném lỗi vì mất precision: phép chuyển được phép theo **loại kiểu**; kết quả số thực được làm tròn theo quy tắc Java. Nếu dữ liệu là số lượng chính xác, ID, tiền tệ hoặc số nguyên lớn, cần chọn kiểu lưu trữ phù hợp. Không chọn `float` hoặc `double` chỉ vì setter cho phép chuyển từ `int`/`long`.

Một lưu ý khác: ép kiểu tường minh trước khi gọi setter vẫn có thể gây mất giá trị. Ví dụ:

~~~java
long huge = 3_000_000_000L;
int narrowed = (int) huge; // ép kiểu thu hẹp có thể đổi giá trị
countField.setInt(instance, narrowed);
~~~

Lời gọi `setInt` ở dòng cuối không thể phục hồi thông tin đã mất tại phép ép `(int) huge`. Với nghiệp vụ cần chuyển an toàn, có thể kiểm tra trước bằng `Math.toIntExact(huge)`; phương thức này ném `ArithmeticException` nếu `long` nằm ngoài miền `int`.

## 7. Lỗi runtime: loại giá trị, quyền truy cập và đối tượng nhận

`IllegalArgumentException` từ setter thường báo **kiểu không tương thích**, nhưng không phải mọi lỗi reflection đều do widening.

| Hiện tượng | Nguyên nhân thường gặp | Cách kiểm tra |
| --- | --- | --- |
| `IllegalArgumentException` với `setInt` hoặc `setLong` | Kiểu trường không cho phép identity/widening từ `int`/`long` | In `field.getType()`, dùng bảng phần 2 |
| `IllegalArgumentException` dù field là kiểu tương thích | `obj` không phải instance đúng của kiểu khai báo trường | So `field.getDeclaringClass()` và kiểu thực của receiver |
| `NullPointerException` | Truyền `null` cho field instance | Cung cấp instance, chỉ truyền `null` cho static |
| `IllegalAccessException` | Field không thể ghi do kiểm tra quyền truy cập, hoặc không có quyền ghi field `final` | Xem modifier, package, module và chính sách ghi |
| `NoSuchFieldException` | Tên field sai, hoặc `getField` chỉ tìm field public phù hợp | Xác minh tên; dùng `getDeclaredField` cho field khai báo trực tiếp không public |
| `ExceptionInInitializerError` | Việc thao tác field `static` kích hoạt khởi tạo lớp và khởi tạo thất bại | Xem nguyên nhân khởi tạo lớp |

Đối với **field private**, việc lấy được `Field` từ `getDeclaredField` không tự mở quyền ghi. Gọi `setAccessible(true)` hoặc `trySetAccessible()` chỉ có thể bỏ một số kiểm tra quyền khi JPMS cho phép; chúng **không** chuyển narrowing thành widening.

Đối với **field final**, ngoài kiểm tra quyền, API có giới hạn nghiêm ngặt khi ghi; một số dạng field final luôn không thể ghi thông qua cơ chế này, kể cả khi đã tắt kiểm tra truy cập. Đừng dùng reflection để sửa hằng số hay bất biến nội bộ. Trong chương trình minh họa, các field cần ghi được khai báo không final.

Với trường `static`, tham số `obj` bị bỏ qua để xác định instance, nhưng nên truyền `null` cho dễ đọc và tránh tạo đối tượng giả. Ghi static field có thể kích hoạt khởi tạo lớp nếu chưa khởi tạo.

## 8. Cách chọn API khi kiểu Field chỉ biết ở runtime

Nếu đang viết công cụ gán cấu hình, bộ ánh xạ dữ liệu hay kiểm thử reflection, nên phân biệt kiểu đích ngay từ đầu:

~~~java
Class<?> targetType = field.getType();

if (targetType == int.class) {
    field.setInt(receiver, intValue);
} else if (targetType == long.class) {
    field.setLong(receiver, longValue);
} else {
    // Xử lý các kiểu khác theo hợp đồng dữ liệu;
    // không tự coi mọi kiểu số là tương thích.
}
~~~

Đây là **mã minh họa**: giả sử đã có `field`, `receiver`, `intValue`, `longValue` và xử lý ngoại lệ thích hợp. Logic mẫu kiểm tra **kiểu đích chính xác**, phù hợp khi mỗi nguồn đã được kiểm chứng sẵn. Nếu muốn cho một nguồn `int` ghi vào `long`, `float`, `double`, hãy mở rộng rõ ràng các nhánh và quyết định chính sách precision; nếu muốn cho `long` ghi vào `int`, bắt buộc kiểm tra phạm vi và dùng phép chuyển tường minh.

Một quy trình an toàn:

1. **Xác định đích:** lấy `field.getType()`, `getModifiers()`, `getDeclaringClass()`; phân biệt primitive với wrapper và instance với static.
2. **Xác định nguồn:** một giá trị `int`, `long` hay một đối tượng wrapper thật sự? Với dữ liệu văn bản, parse dữ liệu theo hợp đồng thay vì đoán.
3. **Quyết định conversion:** chỉ dùng identity/widening được phép, đồng thời đánh giá có chấp nhận làm tròn khi sang `float`/`double` không.
4. **Kiểm tra quyền ghi:** field public, private hay final? Bên gọi có quyền phản chiếu phù hợp không? Hạn chế bỏ kiểm tra truy cập.
5. **Thực hiện và xử lý lỗi:** dùng setter đúng, ghi nhận lỗi kiểu và quyền truy cập riêng để xử lý được nguyên nhân.

Khi làm framework ánh xạ JSON, ORM hoặc mapper, chiến lược tương thích kiểu có thể rộng hơn cơ chế `Field.setInt`. Chẳng hạn converter nghiệp vụ có thể parse chuỗi `"42"` hoặc chuyển `long` sang `int` sau khi kiểm tra miền. Đó là **lớp chuyển đổi do ứng dụng tự viết**, không phải tính năng mà `setInt` tự cung cấp.

## 9. Bài tập thực hành và đáp án

**Bài 1.** Trường `public long total`. Dự đoán `setInt(obj, -5)` và `setLong(obj, -5L)` có được phép không.

**Đáp án:** Cả hai hợp lệ. `int -> long` là widening, `long -> long` là identity; giá trị `-5` được giữ nguyên.

**Bài 2.** Trường `public short age`. `setInt(obj, 12)` có thành công vì `12` nằm trong miền `short` không? Nếu không, nên làm gì?

**Đáp án:** Không, nó ném `IllegalArgumentException`. Dùng `setShort(obj, (short) 12)` sau khi bảo đảm miền giá trị của đầu vào, hoặc chuyển đổi có kiểm tra phạm vi ở tầng nghiệp vụ.

**Bài 3.** Trường `public int count`. Đoạn `setLong(obj, 3)` có hợp lệ không? Có gì khác `setInt(obj, 3)`?

**Đáp án:** `setLong(obj, 3)` vẫn biên dịch do literal được widening sang tham số `long`, nhưng setter ném `IllegalArgumentException` vì trường đích `int`. `setInt(obj, 3)` thành công.

**Bài 4.** Trường `public double amount`. Có thể gọi `setLong(obj, 9_007_199_254_740_993L)` không? Giá trị đọc lại có chắc chính xác không?

**Đáp án:** Gọi hợp lệ vì `long -> double` là widening, nhưng giá trị sẽ bị làm tròn trong ví dụ này; không bảo đảm giữ nguyên mọi bit số nguyên.

**Bài 5.** Trường `public Integer boxed`. Phương thức nào phù hợp giữa `setInt(obj, 42)` và `set(obj, Integer.valueOf(42))`?

**Đáp án:** Dùng `set(obj, Integer.valueOf(42))` để gán tham chiếu. `setInt` chuyên cho primitive field, nên không gán trực tiếp cho field khai báo `Integer`.

**Bài 6.** Trường `public int n`. Vì sao `getLong(obj)` thành công nhưng `setLong(obj, 5L)` thất bại?

**Đáp án:** Getter chuyển **kiểu trường `int` → kiểu trả về `long`** nên widening. Setter chuyển **kiểu tham số `long` → kiểu trường `int`** nên narrowing. Hướng conversion khác nhau.

**Bài 7.** Dùng `getDeclaredField("count")` lấy một field private kiểu `int`, rồi gọi `setLong(obj, 5L)` sau khi `trySetAccessible()` thành công. Có được ghi không?

**Đáp án:** Vẫn không, vì yêu cầu mở truy cập chỉ xử lý quyền kiểm tra của ngôn ngữ; `long -> int` vẫn là narrowing bị `Field.setLong` từ chối. Phải chọn kiểu đầu vào và phương thức thích hợp.

## 10. Ghi nhớ và tài liệu đối chiếu

**Quy tắc cốt lõi:** `Field.setInt` chỉ ghi vào field primitive `int`, `long`, `float`, `double`; `Field.setLong` chỉ ghi vào `long`, `float`, `double`. Runtime xét **hướng từ kiểu tham số setter sang kiểu field**, không xét giá trị có “vừa” kiểu nhỏ hơn hay không. Widening sang số thực đôi khi mất precision nhưng vẫn hợp lệ. Quyền truy cập, `final` và đối tượng nhận là các điều kiện bổ sung, độc lập với loại conversion.

Tài liệu chuẩn để tra cứu:

- [`java.lang.reflect.Field` — Java SE API](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/reflect/Field.html): các phương thức `setInt`, `setLong`, `set(Object,Object)`, `getInt` và `getLong`.
- [Java Language Specification `5.1.2 — Widening Primitive Conversion](https://docs.oracle.com/en/java/javase/26/docs/specs/jls/jls-5.html#jls-5.1.2): các hướng widening và quy tắc precision.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="fields-010">Giá trị null với field tham chiếu và primitive</a>

<details>
<summary>Xem chi tiết</summary>

# Giá trị `null` với field tham chiếu và primitive trong Java Reflection

Khi thao tác với `java.lang.reflect.Field`, cần phân biệt **kiểu khai báo của field**, **giá trị thực tế**, và **đối tượng chứa field**. Một field có kiểu tham chiếu có thể chứa `null`; field có kiểu nguyên thủy (*primitive*) không thể chứa `null`. Tuy nhiên, cả hai đều được đọc qua `Field.get(Object)`, vốn có **kiểu trả về `Object`**. Chính điều đó dễ gây hiểu nhầm khi viết mapper, serializer hoặc framework Reflection.

Bài học giải thích vì sao `Field.get()` của primitive trả về wrapper thay vì `null`, vì sao `Field.set(..., null)` thất bại trên primitive, sự khác nhau giữa `Integer` và `int`, cách Reflection chuyển đổi kiểu số, và ý nghĩa hoàn toàn khác của tham số receiver `null`.

## 1. Trước tiên: `null` là giá trị của tham chiếu, không phải của primitive

Java có tám kiểu primitive: `boolean`, `byte`, `short`, `char`, `int`, `long`, `float`, `double`. Primitive lưu giá trị thuộc miền kiểu của nó; **không có trạng thái `null`**. Ngược lại, field kiểu tham chiếu như `String`, `Integer`, `List<String>` hoặc kiểu ứng dụng có thể chứa một object reference hoặc `null`.

| Field khai báo | Kiểu field thực tế | Giá trị mặc định trước khi gán tường minh | Có thể gán `null`? |
|---|---|---|---|
| `String name;` | Tham chiếu `String` | `null` | Có |
| `Integer age;` | Tham chiếu wrapper `Integer` | `null` | Có |
| `int age;` | Primitive `int` | `0` | Không |
| `boolean enabled;` | Primitive `boolean` | `false` | Không |
| `char letter;` | Primitive `char` | `'\\u0000'` | Không |
| `double amount;` | Primitive `double` | `0.0` | Không |

Trong ví dụ trên, giá trị mặc định áp dụng cho **instance field** của object đã được cấp phát hoặc **static field** khi chuẩn bị class theo quy tắc của JVM; **biến cục bộ** không được tự động gán giá trị mặc định để có thể sử dụng ngay.

Đừng nhầm hai khai báo:

```java
Integer quantity = null; // hợp lệ: Integer là kiểu tham chiếu
int count = 0;           // hợp lệ: int là primitive
// int missing = null;   // lỗi biên dịch: null không chuyển thành int
```

`Integer` là một lớp wrapper của `int`, nhưng `Integer.class.isPrimitive()` trả `false`. Một wrapper `Integer` có thể `null`; một primitive `int` thì không.

## 2. Đọc bằng `Field.get`: primitive được boxing

Chữ ký quan trọng:

```java
Object value = field.get(receiver);
```

`Field.get(Object obj)` trả về `Object`. Nếu field là kiểu tham chiếu, kết quả chính là giá trị tham chiếu mà field đang giữ, **có thể `null`**. Nếu field là primitive, Reflection **boxing** giá trị thành wrapper tương ứng trước khi trả về.

| Kiểu field được khai báo | Giá trị trong field | `Field.get(...)` trả về |
|---|---|---|
| `String` | `null` | `null` |
| `Integer` | `null` | `null` |
| `Integer` | `Integer.valueOf(5)` | Một `Integer` có giá trị `5` |
| `int` | `5` | Một `Integer` có giá trị `5`; không phải `null` |
| `boolean` | `false` | Một `Boolean` có giá trị `false` |
| `char` | `'A'` | Một `Character` có giá trị `'A'` |

Vì vậy, cách kiểm tra `if (field.get(obj) == null)` có thể phát hiện một **tham chiếu null**, nhưng **không thể** phát hiện trạng thái “chưa gán” trên primitive. Nếu primitive mang giá trị `0`, `false` hoặc `'\\u0000'`, đó vẫn là **giá trị hợp lệ** của field.

Đặc biệt, Java Reflection **không** tự coi `0` hay `false` là `null`. Nếu một hệ thống muốn thể hiện trạng thái “không có dữ liệu”, nó cần quy ước riêng, wrapper nullable, kiểu `Optional` phù hợp hoặc metadata khác; không thể dựa vào primitive để chứa `null`.

### Đọc kiểu nguyên thủy với các getter chuyên biệt

`Field` có các phương thức `getBoolean`, `getByte`, `getShort`, `getChar`, `getInt`, `getLong`, `getFloat`, `getDouble`. Những phương thức này trả về **primitive** tương ứng, không trả wrapper hay `null`.

```java
Field score = Person.class.getField("score"); // giả sử public int score
int result = score.getInt(person);
```

Đây là đoạn minh họa, giả định `Person` và `person` đã được khai báo. `getInt` có thể đọc những primitive field chuyển được sang `int` bằng **widening primitive conversion** (ví dụ field `byte` hoặc `short`); nó **không** tự unbox một **field khai báo `Integer`**.

Ví dụ, với field khai báo `Integer nullableScore`, `field.get(person)` trả `Integer` hoặc `null`; còn `field.getInt(person)` ném **`IllegalArgumentException`**, kể cả khi `nullableScore` thực tế khác `null`. Căn cứ là **kiểu khai báo của field**, không phải wrapper object tình cờ nằm trong đó.

## 3. Ghi bằng `Field.set`: xử lý `null` theo kiểu đích

Chữ ký:

```java
field.set(receiver, value);
```

`receiver` là object sở hữu **instance field** hoặc có thể là `null` đối với **static field**. `value` là giá trị **muốn ghi vào field**. Hai tham số này hoàn toàn khác vai trò.

### Field tham chiếu nhận `null`

```java
Field name = Person.class.getField("name"); // giả sử public String name
name.set(person, null);                     // hợp lệ nếu có quyền ghi
```

Một tham chiếu có thể nhận `null`, kể cả wrapper như `Integer` hoặc `Boolean`. Ngoài `null`, giá trị ghi phải tương thích với kiểu khai báo của field theo quy tắc gán/Reflection. Một field `String` không thể nhận một `Integer` chỉ vì cả hai đều là `Object`.

### Primitive field từ chối `null`

```java
Field age = Person.class.getField("age"); // giả sử public int age
age.set(person, null);                    // ném IllegalArgumentException
```

Vì `int` không có `null`, `Field.set` không thể biến `null` thành `0` hay một giá trị mặc định khác. Thao tác ném **`IllegalArgumentException`**. Đây là lỗi **chuyển đổi giá trị muốn ghi**, không phải do Reflection không tìm thấy field.

Cần tránh suy luận “Java tự unboxing nên `null` được chuyển thành primitive mặc định”: unboxing một wrapper `null` trong biểu thức Java thông thường cũng không tạo ra giá trị hợp lệ.

### Ghi primitive qua wrapper và widening

Khi `value` là wrapper **khác null**, `Field.set` có thể unbox rồi thực hiện **widening primitive conversion** nếu kiểu đích cho phép:

```java
Field count = Person.class.getField("count"); // giả sử public int count
count.set(person, Integer.valueOf(11));       // thành công
count.set(person, Short.valueOf((short) 12)); // thành công: short -> int
count.set(person, Long.valueOf(13L));         // ném IllegalArgumentException
```

Đây là ví dụ minh họa. `long -> int` là **narrowing**, nên không được tự chuyển trong `Field.set`. Không có chuyện Reflection tự động dùng `String.valueOf` hoặc parse chuỗi:

```java
// Giả sử count có kiểu int:
count.set(person, "42"); // KHÔNG tự parse; ném IllegalArgumentException
```

Nếu dữ liệu bên ngoài là `String`, bước **parse và validate** phải làm trước khi ghi. Nếu input là `null` mà field đích là primitive, mapper phải đặt quy tắc rõ ràng: từ chối thiếu dữ liệu, bỏ qua assignment để giữ giá trị hiện tại, hoặc chọn một mặc định được đặc tả; không nên lặng lẽ đổi `null` thành `0`/ `false` mà không có hợp đồng dữ liệu.

### Setter chuyên biệt

`Field.setInt(receiver, 10)` hoặc `Field.setBoolean(receiver, true)` nhận **primitive** ngay ở tham số thứ hai, không có chỗ cho `null`. Việc chuyển đổi kiểu vẫn tuân theo quy tắc phù hợp của các specialized setters. `setInt` không dùng để gán một field **khai báo kiểu `Integer`**, vì field đó là kiểu tham chiếu.

## 4. Ví dụ Java 17+ hoàn chỉnh, đã kiểm tra biên dịch và stdout

Lưu thành tệp **`FieldNullDemo.java`**:

```java
import java.lang.reflect.Field;

public class FieldNullDemo {
    public static class Sample {
        public String nickname;
        public Integer nullableCount;
        public int count = 7;
        public boolean enabled;
        public static String appName;
        public static int globalCount = 3;
    }

    public static void main(String[] args) throws ReflectiveOperationException {
        Sample sample = new Sample();
        Class<Sample> type = Sample.class;
        Field nickname = type.getField("nickname");
        Field nullableCount = type.getField("nullableCount");
        Field count = type.getField("count");
        Field enabled = type.getField("enabled");
        Field appName = type.getField("appName");
        Field globalCount = type.getField("globalCount");

        System.out.println("reference initial: " + nickname.get(sample));
        System.out.println("wrapper initial: " + nullableCount.get(sample));
        Object countObject = count.get(sample);
        System.out.println("primitive get: " + countObject + " / " + countObject.getClass().getSimpleName());
        System.out.println("boolean get: " + enabled.get(sample));
        System.out.println("types: " + count.getType().getName() + ", " + nullableCount.getType().getName());
        System.out.println("count primitive: " + count.getType().isPrimitive());
        System.out.println("nullableCount primitive: " + nullableCount.getType().isPrimitive());

        nickname.set(sample, null);
        nullableCount.set(sample, null);
        System.out.println("both reference null: " + (nickname.get(sample) == null && nullableCount.get(sample) == null));

        try {
            count.set(sample, null);
        } catch (IllegalArgumentException e) {
            System.out.println("primitive rejects null: " + e.getClass().getSimpleName());
        }

        count.set(sample, Integer.valueOf(11));
        System.out.println("count via wrapper: " + count.getInt(sample));

        count.set(sample, Short.valueOf((short) 12));
        System.out.println("count via widening: " + count.getInt(sample));

        try {
            count.set(sample, Long.valueOf(13L));
        } catch (IllegalArgumentException e) {
            System.out.println("narrowing rejected: " + e.getClass().getSimpleName());
        }

        try {
            nullableCount.getInt(sample);
        } catch (IllegalArgumentException e) {
            System.out.println("typed getter on wrapper field: " + e.getClass().getSimpleName());
        }

        System.out.println("static reference before: " + appName.get(null));
        appName.set(null, "demo");
        System.out.println("static reference after: " + appName.get(null));
        System.out.println("static primitive: " + globalCount.getInt(null));

        try {
            nickname.get(null);
        } catch (NullPointerException e) {
            System.out.println("instance receiver null: " + e.getClass().getSimpleName());
        }
    }
}
```

Biên dịch và chạy trong thư mục chứa tệp, sử dụng JDK 17 hoặc mới hơn:

```bash
javac FieldNullDemo.java
java FieldNullDemo
```

**Stdout:**

```text
reference initial: null
wrapper initial: null
primitive get: 7 / Integer
boolean get: false
types: int, java.lang.Integer
count primitive: true
nullableCount primitive: false
both reference null: true
primitive rejects null: IllegalArgumentException
count via wrapper: 11
count via widening: 12
narrowing rejected: IllegalArgumentException
typed getter on wrapper field: IllegalArgumentException
static reference before: null
static reference after: demo
static primitive: 3
instance receiver null: NullPointerException
```

**Giải thích output:**

1. `nickname` và `nullableCount` đều là **reference fields**, được mặc định bằng `null`. `enabled` là primitive boolean nên mặc định `false`.
2. `count` khai báo `int` và chứa `7`; `Field.get` boxing thành `Integer`. Bởi vậy `countObject.getClass().getSimpleName()` in `Integer`, không có giá trị `null`.
3. `Field.getType()` cho biết **kiểu khai báo**: `int` đối với `count`, `java.lang.Integer` đối với `nullableCount`. `isPrimitive()` phân biệt hai trường hợp ngay cả khi cả hai có thể đọc qua `Object`.
4. Ghi `null` cho hai field tham chiếu thành công. Ghi `null` cho primitive `count` ném `IllegalArgumentException`.
5. `Integer(11)` được unbox thành `int`; `Short(12)` được unbox rồi widening từ `short` sang `int`; `Long(13)` không thể narrowing sang `int` theo API này.
6. `nullableCount.getInt(sample)` thất bại **vì declared type là `Integer`**, không phải vì giá trị field đang `null`. Ngay cả nếu field chứa `Integer.valueOf(5)`, specialized getter này vẫn không đọc được field tham chiếu.
7. `appName.get(null)` và `appName.set(null, "demo")` thao tác với **static field**; `null` ở đây là **receiver**, không phải giá trị phải ghi. Ngược lại, `nickname.get(null)` là instance field có receiver `null`, nên ném `NullPointerException`.

## 5. Hai vị trí `null` trong `Field.get`/`Field.set` có ý nghĩa khác nhau

Nhìn chữ ký:

```java
Object read = field.get(receiver);
field.set(receiver, value);
```

Hãy phân biệt hai trường hợp hoàn toàn độc lập.

| Thao tác | `receiver` | `value` | Kết quả |
|---|---|---|---|
| Đọc instance field | Object instance hợp lệ | Không áp dụng | Thành công nếu có quyền đọc |
| Đọc instance field | `null` | Không áp dụng | `NullPointerException` |
| Đọc static field | `null` | Không áp dụng | Hợp lệ; receiver bị bỏ qua |
| Ghi instance reference field | Object instance hợp lệ | `null` | Hợp lệ nếu field ghi được |
| Ghi instance primitive field | Object instance hợp lệ | `null` | `IllegalArgumentException` |
| Ghi static reference field | `null` | `null` | Hợp lệ nếu field ghi được |
| Ghi static primitive field | `null` | `null` | `IllegalArgumentException` |

Với static field, tham số receiver của `Field.get`/`Field.set` **không được dùng để chỉ instance** và có thể truyền `null`. Khi truy cập static field, JVM có thể phải **khởi tạo lớp khai báo** nếu chưa được khởi tạo; lấy metadata `Field` trước đó không tự đồng nghĩa đã chạy static initializer.

Với instance field, receiver phải là instance tương thích với lớp khai báo field. Nếu truyền **đối tượng không phù hợp**, Reflection có thể ném `IllegalArgumentException`. Nếu receiver là `null`, lỗi là `NullPointerException`. Đừng nhầm hai lỗi receiver này với `IllegalArgumentException` do **value không phù hợp kiểu primitive** trong `Field.set`.

Ví dụ rất dễ bị đọc nhầm:

```java
Field global = Sample.class.getField("appName");
global.set(null, null); // receiver null: hợp lệ cho static;
                        // value null: hợp lệ vì appName là String
```

Ở đây `global.set(null, null)` hoàn toàn có thể hợp lệ nếu field là **static, kiểu tham chiếu và có quyền ghi**.

## 6. Kiểm tra kiểu khai báo trước khi xử lý dữ liệu nullable

Trong code generic mapper, type của field là nguồn thông tin đáng tin cậy hơn việc suy từ giá trị hiện tại:

```java
// Đoạn minh họa trong một mapper: field đã được tìm,
// target là instance phù hợp; incoming đến từ bên ngoài.
Class<?> targetType = field.getType();

if (incoming == null && targetType.isPrimitive()) {
    throw new IllegalArgumentException(
            "Missing value for primitive field: " + field.getName());
}

field.set(target, incoming);
```

Đoạn này **không phải chương trình chạy độc lập**; các biến `field`, `target`, `incoming` là đầu vào của logic mapper. Nó chủ động từ chối input `null` trước khi gọi `Field.set` với primitive. Khi `incoming` khác `null`, vẫn cần bảo đảm object có kiểu tương thích với field (bao gồm unboxing/widening đúng luật); kiểm tra `null` **không thay thế toàn bộ validation**.

Nếu hệ thống cần phân biệt **“không cung cấp giá trị”** với **“cung cấp giá trị `0`”**, chỉ dùng primitive field là chưa đủ. Các phương án thường gặp:

- Dùng `Integer`, `Boolean`, `Double`... để thể hiện trạng thái nullable; quyết định khi nào phải từ chối `null`.
- Theo dõi sự hiện diện của input bằng key presence, dữ liệu request hoặc một trường cờ riêng, thay vì coi `0` là “không tồn tại”.
- Áp dụng mặc định **ở tầng business validation** một cách có chủ đích. Ví dụ, với một field `int retries`, thiếu dữ liệu có thể bị từ chối hoặc được gán `3` theo hợp đồng. Reflection tự nó không quyết định mặc định đó.
- Với object bất biến hoặc record, ưu tiên constructor/factory để thiết lập state hợp lệ thay vì cố ghi sâu vào field `final`.

Cẩn trọng khi dữ liệu từ JSON/database phân biệt `null` với giá trị thiếu: `null`, không có key và `0` là **ba trạng thái khác nhau** tùy schema. Reflection chỉ thực hiện đọc/ghi theo kiểu Java; không tự suy diễn ngữ nghĩa nghiệp vụ.

## 7. Access, `final` và lỗi không liên quan đến `null`

Trước khi đọc/ghi thành công, Reflection còn kiểm tra visibility và module access:

- `getField("name")` tra cứu field **public**; `getDeclaredField("name")` tra cứu field khai báo trực tiếp, bao gồm `private`. Tìm được private field **không tự có quyền** đọc/ghi nó.
- `Field.get`/`Field.set` có thể ném **`IllegalAccessException`** nếu caller không đủ quyền. Với named modules, `setAccessible(true)` có thể bị từ chối bằng **`InaccessibleObjectException`** nếu package chưa `opens` phù hợp.
- `final` không thay đổi quy tắc `null` của field; nhưng việc **ghi** `final` bằng Reflection còn có giới hạn riêng. Đặc biệt không nên coi `setAccessible(true)` là cách bảo đảm thay đổi mọi trường final, record component hay internal state.
- `getType()` lấy kiểu khai báo; nó không đọc giá trị và không cần receiver. `Field.get(null)` trên một static field có thể kích hoạt initialization của class đích.
- Nếu lỗi xảy ra trong quá trình khởi tạo tĩnh, thao tác trên static field có thể thất bại vì initializer của lớp; đó không phải bằng chứng rằng static receiver `null` bị cấm.

| Lỗi | Ngữ cảnh điển hình | Phân biệt với `null` value |
|---|---|---|
| `IllegalArgumentException` | Ghi `null` vào primitive, ghi wrapper không tương thích, narrowing bị cấm hoặc receiver sai kiểu | Cần xác định lỗi do **value** hay **receiver** |
| `NullPointerException` | Gọi `Field.get(null)`/`Field.set(null, ...)` trên **instance field** | `null` ở **receiver**, không phải field value |
| `IllegalAccessException` | Không được phép đọc/ghi member | Lỗi access, không phải primitive không có null |
| `NoSuchFieldException` | Không tìm được field đúng tên/phạm vi tra cứu | Xảy ra tại bước lookup |
| `InaccessibleObjectException` | Không thể bật deep access theo JPMS bằng `setAccessible(true)` | Lỗi encapsulation |
| `ExceptionInInitializerError` | Static initializer ném exception trong quá trình khởi tạo class | Có thể xuất hiện khi thực sự sử dụng static field |

## 8. Những nhầm lẫn thường gặp

1. **“`Field.get` trả `Object`, nên primitive field cũng có thể cho `null`.”** Sai: primitive được **boxing** thành wrapper không null.
2. **“`Integer` là primitive.”** Sai: `Integer` là reference type và có thể chứa `null`.
3. **“`Field.set(obj, null)` sẽ tự gán `0` cho `int`.”** Sai: thao tác ném `IllegalArgumentException`.
4. **“`Field.getInt` đọc được field khai báo `Integer` nếu wrapper không null.”** Sai: getter chuyên biệt này yêu cầu kiểu **primitive** có conversion phù hợp.
5. **“`Field.set` nhận mọi kiểu số và tự ép về primitive.”** Sai: chỉ unboxing/widening hợp lệ; **narrowing** không tự xảy ra.
6. **“`Field.get(null)` trả null vì receiver null.”** Sai: với **static field**, receiver null là hợp lệ và kết quả phụ thuộc giá trị field. Với instance field, receiver null gây lỗi.
7. **“`0` tương đương một field không có dữ liệu.”** Sai: `0` là giá trị primitive hợp lệ; missing/null cần biểu diễn ở mức model/schema khác nếu có ý nghĩa.
8. **“Field public luôn ghi được.”** Chưa đủ: quyền module, `final`, kiểu value và khả năng tương thích của receiver đều có thể ảnh hưởng.
9. **“`null` luôn dẫn đến `NullPointerException`.”** Sai: đối với `Field.set(receiver, null)` trên primitive thường là **`IllegalArgumentException`**; `NullPointerException` áp dụng trong trường hợp receiver instance bằng `null`.
10. **“Gọi `getClass()` lên giá trị vừa đọc luôn an toàn.”** Sai: với **reference field đang null**, `field.get(receiver)` trả `null`; gọi `getClass()` trên kết quả đó gây `NullPointerException`. Dùng `field.getType()` nếu muốn biết **declared type**.

## 9. Câu hỏi tự kiểm tra

**Câu 1.** Một class có `public int n;` (không gán tường minh). `Field.get(object)` trả giá trị nào?

**Đáp án:** Một `Integer` có giá trị `0` do boxing primitive `int`. Không phải `null`.

**Câu 2.** Một class có `public Integer n;` (không gán tường minh). `Field.get(object)` và `Field.getInt(object)` có gì khác?

**Đáp án:** `get` trả `null`. `getInt` ném `IllegalArgumentException` vì declared type là `Integer` (reference), dù sau này field được gán một wrapper không null.

**Câu 3.** `Field.set(instance, null)` có luôn gây lỗi không?

**Đáp án:** Không. Với reference field có thể ghi, việc gán `null` hợp lệ. Với primitive field, `null` bị từ chối bằng `IllegalArgumentException`.

**Câu 4.** `field.get(null)` có luôn gây `NullPointerException` không?

**Đáp án:** Không. Với static field, `null` là receiver hợp lệ. Với instance field, receiver null dẫn đến `NullPointerException`.

**Câu 5.** Field `public int n;` có được nhận `Short.valueOf((short) 5)` qua `Field.set` không? Có được nhận `Long.valueOf(5L)` không?

**Đáp án:** `Short` được unbox rồi widening `short -> int`, nên có thể gán. `Long` đòi hỏi narrowing `long -> int`, nên `Field.set` ném `IllegalArgumentException`.

**Câu 6.** Một serializer nhận JSON `{"age": null}` nhưng Java model khai báo `int age`. Reflection tự chọn xử lý ra sao?

**Đáp án:** `Field.set(target, null)` không thể đặt primitive `int` thành null và sẽ ném `IllegalArgumentException`. Serializer/application phải áp dụng schema: từ chối, đặt mặc định tường minh, hoặc đổi model sang `Integer` nếu nullable là trạng thái hợp lệ.

## Tài liệu chuẩn

- Java SE API — `java.lang.reflect.Field`: `get(Object)`, `getInt(Object)`, `set(Object, Object)`, `setInt(Object, int)`, `getType()` và các exception.
- Java SE API — `java.lang.Class`: `isPrimitive()`.
- Java Language Specification — §4.2 Primitive Types and Values; §4.3 Reference Types and Values; §4.12.5 Initial Values of Variables; §5.1.2 Widening Primitive Conversion; §5.1.7 Boxing Conversion; §5.1.8 Unboxing Conversion.

**Ghi nhớ:** `null` là trạng thái của **tham chiếu**; primitive không lưu được `null`. `Field.get` boxing primitive thành wrapper không null; `Field.set(receiver, null)` chỉ hợp lệ nếu **kiểu field** và quyền ghi cho phép. `receiver == null` lại là câu chuyện khác: dùng được với **static field**, nhưng không dùng được để đọc/ghi **instance field**.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="fields-011">Truy cập static field có thể kích hoạt khởi tạo lớp</a>

<details>
<summary>Xem chi tiết</summary>

# Truy cập static field có thể kích hoạt khởi tạo lớp trong Java Reflection

## 1. Vấn đề: lấy Field và đọc Field là hai thời điểm khác nhau

Trong Java Reflection, có sự khác biệt quan trọng giữa **tìm metadata của một field** và **thực sự đọc hoặc ghi giá trị của field đó**. Với field `static`, việc đọc hoặc ghi qua `java.lang.reflect.Field` có thể khiến JVM **khởi tạo lớp khai báo field** trước khi trả về giá trị hoặc thực hiện phép gán.

```java
Class<?> type = Sample.class;                       // Chưa yêu cầu khởi tạo Sample
Field field = type.getDeclaredField("value");       // Chỉ lấy metadata
Object value = field.get(null);                     // Nếu Sample chưa init: kích hoạt init
```

Đoạn mã trên là **minh họa**, yêu cầu có lớp `Sample` với field `value` và cần `import java.lang.reflect.Field;`; quyền truy cập cũng phải hợp lệ. Với field `static`, đối số receiver của `Field.get(Object)` có thể là `null` vì giá trị thuộc về lớp, không thuộc về một đối tượng cụ thể.

Điểm cần phân biệt: việc khởi tạo lớp xảy ra **trước lần truy cập giá trị đầu tiên cần khởi tạo**, không nhất thiết ngay khi JVM load class, khi Java tạo đối tượng `Class<?>` hoặc khi Reflection tạo đối tượng `Field`.

### Mục tiêu

Sau bài học, bạn có thể:

- Giải thích vì sao `getDeclaredField()` chỉ tìm metadata nhưng `Field.get(null)` hay `Field.set(null, value)` có thể chạy các static initializer.
- Phân biệt **loading, linking, initialization**, và cách đọc `static final` compile-time constant so với đọc bằng Reflection.
- Dự đoán thứ tự output khi lớp có `static` block và phép gán field `static`.
- Xác định **lớp khai báo field** bằng `Field.getDeclaringClass()` khi có kế thừa.
- Tránh vô tình kích hoạt side effect, lỗi khởi tạo hoặc rủi ro khi framework quét metadata.

## 2. Ba giai đoạn: loading, linking và initialization

Một lớp thường trải qua các bước cơ bản sau trong vòng đời JVM:

| Giai đoạn | Ý nghĩa | Có nhất thiết chạy code `static` do người dùng viết? |
| --- | --- | --- |
| **Loading** | JVM tìm/đọc biểu diễn class và tạo đối tượng runtime `Class` tương ứng | Không |
| **Linking** | Verification, preparation và resolution (resolution có thể diễn ra muộn); preparation cấp phát và đặt giá trị mặc định cho các field `static` | Không chạy phần thân static initializer như khi initialization |
| **Initialization** | JVM thực thi phương thức khởi tạo lớp `<clinit>` do compiler tổng hợp từ static field initializer và static initializer block | **Có**, nếu lớp có code khởi tạo tương ứng |

Ví dụ:

```java
class Config {
    static int port = parsePort();  // Lệnh này chạy khi initialize Config

    static {
        System.out.println("Config initialized");
    }

    private static int parsePort() {
        return 8080;
    }
}
```

Khi initialization của `Config` diễn ra, câu lệnh gán `port = parsePort()` và static block được thực thi theo **thứ tự văn bản xuất hiện trong lớp**. Trong ví dụ này `parsePort()` chạy trước thông báo `Config initialized`.

**Không nên coi “class đã được load” là “class đã được initialize”.** Một framework có thể load lớp để kiểm tra tên, annotation hoặc thành viên mà chưa yêu cầu chạy logic khởi tạo; nhưng khi framework truy cập giá trị field `static`, ranh giới này thay đổi.

JVM quản lý initialization cho từng định nghĩa lớp ở runtime (gắn với class loader xác định). Một lần initialization thành công chỉ diễn ra một lần cho định nghĩa lớp đó; đó không đồng nghĩa mọi lần dùng tên lớp trong source đều gây khởi tạo lại.

## 3. Các thao tác Reflection nào có thể kích hoạt initialization?

Xét:

```java
class Settings {
    static int timeout = computeTimeout();

    private static int computeTimeout() {
        System.out.println("Calculating timeout");
        return 10;
    }
}
```

| Thao tác | Có yêu cầu initialize `Settings` nếu chưa được initialize? | Vì sao |
| --- | --- | --- |
| `Settings.class` | Không | Class literal lấy đối tượng `Class`, không phải truy cập giá trị `static` |
| `Settings.class.getDeclaredField("timeout")` | Không | Tìm metadata của field |
| `Settings.class.getDeclaredFields()` | Không | Liệt kê metadata của các field khai báo trực tiếp |
| `field.getName()`, `field.getType()`, `field.getModifiers()` | Không phải thao tác đọc giá trị field | Xem đặc điểm member; một số kiểu phụ thuộc có thể được load/resolved |
| `field.get(null)` | **Có** | Đọc giá trị field `static` qua Reflection |
| `field.getInt(null)` | **Có** | Đọc chuyên biệt kiểu nguyên thủy `int` |
| `field.set(null, value)` | **Có** nếu phép ghi được phép | Ghi giá trị field `static` |
| `field.setInt(null, value)` | **Có** nếu phép ghi được phép | Ghi `static int` bằng API chuyên biệt |
| `Settings.timeout` | **Có** vì `timeout` không phải compile-time constant | Dùng field `static` không-constant trong code thường |
| `Class.forName("...", false, loader)` | Không yêu cầu initialization | Lấy/load class với cờ `initialize=false` |
| `Class.forName("...")` | **Có**, đối với lớp chưa được initialize | Biến thể một tham số chủ động yêu cầu khởi tạo lớp được đặt tên |

Bảng này giả định thao tác thực sự đi tới lần truy cập hợp lệ; nếu field không tồn tại, caller không có quyền, loại đối số sai hoặc một lỗi xảy ra sớm hơn, đừng suy ra rằng initialization **nhất định** đã diễn ra. Mục tiêu là nêu tác dụng của **thao tác thành công trên field static**.

Không nhầm `Class.getDeclaredField` với `Field.get`: hai phương thức khác lớp và khác ý nghĩa. `getDeclaredField` có thể tìm cả member `private` được khai báo ngay trong class; điều đó **không cấp quyền tự động** để sau này gọi `get` hoặc `set`.

## 4. Ví dụ Java 17+: thấy rõ thời điểm khởi tạo

Lưu nguyên chương trình vào tệp **`StaticFieldInitDemo.java`**. Bốn trường hợp được tách rõ: đọc constant bình thường, tra cứu metadata, đọc static field bằng Reflection và ghi static field bằng Reflection.

```java
import java.lang.reflect.Field;

public class StaticFieldInitDemo {
    static class Target {
        static {
            System.out.println("Target: <clinit> block");
        }

        public static int number = loadNumber();
        public static final int COMPILE_CONSTANT = 42;

        private static int loadNumber() {
            System.out.println("Target: loadNumber()");
            return 7;
        }
    }

    static class Constants {
        static {
            System.out.println("Constants: <clinit> block");
        }

        public static final String TEXT = "hello";
    }

    static class Writable {
        static {
            System.out.println("Writable: <clinit> block");
        }

        public static int value = 1;
    }

    public static void main(String[] args) throws Exception {
        // Compile-time constant đọc trực tiếp có thể được inline.
        System.out.println("direct constant: " + Target.COMPILE_CONSTANT);

        // Lấy Class và metadata Field chưa đọc giá trị static.
        Class<?> clazz = Target.class;
        Field number = clazz.getDeclaredField("number");
        System.out.println("metadata: " + number.getName());
        System.out.println("before first Field.getInt");

        // Field.getInt(null) bắt đầu initialize Target.
        System.out.println("reflected number: " + number.getInt(null));
        System.out.println("reflected number again: " + number.getInt(null));

        // Đọc constant bằng Java thông thường không initialize Constants.
        System.out.println("direct other constant: " + Constants.TEXT);

        // Reflection vẫn chủ động đọc field static, nên initialize Constants.
        Field constant = Constants.class.getDeclaredField("TEXT");
        System.out.println("reflected constant: " + constant.get(null));

        // Lấy Field không init Writable, nhưng việc ghi qua Field.setInt có.
        Field writable = Writable.class.getDeclaredField("value");
        System.out.println("before Field.setInt");
        writable.setInt(null, 25);
        System.out.println("updated value: " + writable.getInt(null));
    }
}
```

Biên dịch và chạy bằng **JDK 17+**:

```bash
javac StaticFieldInitDemo.java
java StaticFieldInitDemo
```

**Kết quả chính xác** khi chạy chương trình độc lập:

```text
direct constant: 42
metadata: number
before first Field.getInt
Target: <clinit> block
Target: loadNumber()
reflected number: 7
reflected number again: 7
direct other constant: hello
Constants: <clinit> block
reflected constant: hello
before Field.setInt
Writable: <clinit> block
updated value: 25
```

### 4.1. Vì sao hai dòng đầu chưa in `Target: <clinit> block`?

`Target.COMPILE_CONSTANT` là **constant variable**: field `final` có kiểu primitive/String và được khởi tạo bằng **constant expression**. Java compiler có thể dùng trực tiếp hằng số trong bytecode của caller thay vì lệnh đọc field ở runtime. Lệnh `Target.class` cũng không tự yêu cầu initialization. `clazz.getDeclaredField("number")` chỉ tạo metadata.

Vì thế trước lời gọi `Field.getInt(null)`, chương trình đã in thông tin về field nhưng `Target` vẫn chưa initialize.

### 4.2. Vì sao output static initializer chen giữa hai dòng khi đọc field?

Biểu thức:

```java
System.out.println("reflected number: " + number.getInt(null));
```

phải đánh giá `number.getInt(null)` trước khi in cả chuỗi. Lần đầu đọc này yêu cầu initialize `Target`; JVM chạy static block trước, tiếp đến initializer của `number` gọi `loadNumber()`. Sau khi khởi tạo thành công, `getInt(null)` trả `7` và Java mới in `reflected number: 7`.

Lần đọc thứ hai thấy `Target` đã initialize nên **không chạy lại** static block hoặc `loadNumber()`.

### 4.3. Vì sao đọc một constant qua Reflection vẫn làm lớp initialize?

`Constants.TEXT` là constant variable được compiler inline khi đọc trực tiếp, nên dòng `direct other constant: hello` không khởi tạo `Constants`. Tuy nhiên:

```java
Field constant = Constants.class.getDeclaredField("TEXT");
System.out.println(constant.get(null));
```

gọi `Field.get(null)` thật sự. Quy tắc truy cập reflective vào field `static` yêu cầu initialize **lớp khai báo field**, kể cả khi field là compile-time constant. Do đó `Constants: <clinit> block` xuất hiện trước `reflected constant: hello`.

**Hệ quả:** Một framework chỉ “đọc giá trị hằng số” bằng Reflection vẫn có thể chạy code `static` bên cạnh hằng số đó. Đừng dựa vào việc field có `final` để suy ra rằng lần đọc reflective luôn vô hại.

### 4.4. Vì sao ghi cũng làm lớp initialize?

`writable.setInt(null, 25)` trước tiên đảm bảo `Writable` được initialize (ban đầu `value=1`), rồi thực hiện phép ghi được phép. Sau đó `getInt(null)` đọc `25`. Không có dòng in thứ hai từ static block của `Writable` vì initialization không diễn ra lại.

## 5. Compile-time constant và static final không hằng là hai trường hợp khác nhau

Không phải mọi `static final` đều được coi là constant variable.

```java
class Flags {
    static {
        System.out.println("Flags initialized");
    }

    static final int FIXED = 100;                 // Constant expression
    static final String TITLE = "core" + "-api";  // Constant expression
    static final Integer BOXED = 100;             // Không phải primitive/String
    static final int COMPUTED = Integer.parseInt("100");
                                                  // Không phải constant expression
    static int DYNAMIC = 100;                     // Không final
}
```

| Truy cập bằng code Java bình thường | Thông thường yêu cầu initialize `Flags`? |
| --- | --- |
| `Flags.FIXED` | Không; compile-time constant có thể được inline |
| `Flags.TITLE` | Không; constant expression kiểu `String` |
| `Flags.BOXED` | Có; `Integer` không phải kiểu cho constant variable |
| `Flags.COMPUTED` | Có; `Integer.parseInt(...)` không phải constant expression |
| `Flags.DYNAMIC` | Có; `static` field không phải constant variable |

Ngược lại, `Field.get(null)` đối với **bất kỳ field static nào** ở bảng trên đều yêu cầu initialization của declaring class trước khi đọc, nếu lớp chưa được initialize và thao tác có quyền thực hiện.

Ngoài vấn đề initialization, việc inline constant còn liên quan tương thích binary: class đã biên dịch trước có thể giữ giá trị hằng cũ cho đến khi được biên dịch lại. Đây là lý do không nên dùng việc đổi một `public static final` constant rồi quan sát output để suy luận đơn giản về load/initialize.

## 6. Khởi tạo lớp nào khi có kế thừa?

Mục tiêu initialization là **lớp khai báo field static được truy cập**, không đương nhiên là lớp bạn dùng làm điểm xuất phát tra cứu.

```java
class ParentConfig {
    static {
        System.out.println("ParentConfig initialized");
    }

    public static int port = 8080;
}

class ChildConfig extends ParentConfig {
    static {
        System.out.println("ChildConfig initialized");
    }
}
```

Ví dụ các dòng trong một phương thức có `throws Exception`:

```java
Field field = ChildConfig.class.getField("port");
System.out.println(field.getDeclaringClass().getSimpleName());
System.out.println(field.getInt(null));
```

Nếu chưa lớp nào được initialize, ý nghĩa là:

- `getField("port")` có thể tìm field `public` được kế thừa; `getDeclaringClass()` cho biết field thật sự nằm ở `ParentConfig`.
- `field.getInt(null)` yêu cầu initialize `ParentConfig` (nơi field được khai báo).
- Chỉ vì lấy field qua `ChildConfig.class` không có nghĩa `ChildConfig` cần initialize. Đọc field static được thừa kế không tự buộc subclass phải chạy static block.

Với field bị ẩn (*field hiding*), luôn kiểm tra `getDeclaringClass()`. Nếu `ChildConfig` khai báo một field `port` khác, cần chọn chính xác `Field` nào muốn đọc; việc chọn sai owner có thể gây khởi tạo lớp khác dự kiến.

Khi initialize một **class**, JVM cũng phải đảm bảo superclass cần thiết đã được initialize theo quy tắc chuẩn; một số interface có default method cũng có thể tham gia quá trình. Vì vậy lần đọc field static khai báo ở subclass có thể kéo theo quá trình khởi tạo liên quan trong phân cấp, nhưng không nên đảo chiều suy luận rằng đọc field static của superclass luôn initialize mọi subclass.

## 7. Loading/metadata an toàn hơn initialization, nhưng cần hiểu giới hạn

Nếu nhiệm vụ chỉ là **khám phá cấu trúc lớp**, có thể trì hoãn initialization:

```java
String binaryName = "example.Settings";
ClassLoader loader = Thread.currentThread().getContextClassLoader();

Class<?> clazz = Class.forName(binaryName, false, loader);
Field field = clazz.getDeclaredField("timeout");

System.out.println(field.getName());
System.out.println(field.getDeclaringClass().getName());
```

Đây là đoạn minh họa, giả định có lớp `example.Settings` trong classpath/module path và quyền tra cứu phù hợp; cần import `java.lang.reflect.Field`. `Class.forName(name, false, loader)` không chủ động initialize lớp được gọi tên. Tương tự, việc xem metadata cơ bản của `Field` không đọc giá trị static. Dù vậy, loading, linking, resolution hay các hook/class-loader tùy chỉnh vẫn có thể làm các công việc khác: “chưa initialize lớp đích” **không có nghĩa mọi thao tác liên quan đều tuyệt đối không có side effect**.

Một số cách yêu cầu initialization một cách chủ động:

```java
Class.forName("example.Settings");      // Thông thường yêu cầu initialize
// Hoặc thực hiện một thao tác đọc/ghi static field hợp lệ.
```

Hãy chọn chính sách rõ ràng: nếu plugin scanner chỉ lập danh sách field, không cần đọc mọi giá trị `static`. Nếu ứng dụng thực sự cần giá trị runtime, chấp nhận rằng đây là thao tác có thể khởi tạo class.

## 8. Thứ tự khởi tạo và giá trị mặc định

Trong **preparation**, JVM gán giá trị mặc định ban đầu cho các field `static`: `0` cho số nguyên, `false` cho `boolean`, `null` cho tham chiếu. Trong **initialization**, code do compiler tạo từ static initializer chạy theo thứ tự source; đây là lúc các phép gán tùy biến được thực thi.

Ví dụ:

```java
class Ordered {
    static int first = compute();
    static int later = 20;

    private static int compute() {
        return later + 1;
    }
}
```

Khi `Ordered` được initialize, phép gán `first = compute()` thực thi trước `later = 20`. Tại thời điểm `compute()` đọc `later`, field có giá trị mặc định `0`, nên `first` nhận `1`. Sau đó `later` trở thành `20`. Hai field đều là `static`, nhưng thứ tự initializer vẫn quan trọng.

Trong ví dụ này một lượt reflective đọc `Ordered.first` sau initialization sẽ nhận `1`. Đừng dùng Reflection để suy luận rằng các initializer chạy “cùng lúc” hoặc rằng một field luôn thấy mọi field khác đã được gán giá trị cuối cùng.

JLS có các chi tiết đặc biệt về constant variables, superclass initialization và interface initialization. Với trường hợp thông dụng trong bài, điều cốt lõi là JVM hoàn thành initialization cần thiết **trước khi truy cập static field phản chiếu thành công**.

## 9. Nếu static initialization thất bại thì sao?

Static initializer có thể gọi cấu hình, đọc file, tạo connection, gọi thư viện native hoặc thực hiện phép tính có thể lỗi. Khi Reflection là thao tác đầu tiên yêu cầu initialize lớp, lỗi có thể xuất hiện ngay tại `Field.get(null)` hoặc `Field.set(null, value)`, dù bản thân field hợp lệ.

Ví dụ một lớp có khởi tạo thất bại:

```java
class Broken {
    static int value = initialize();

    private static int initialize() {
        throw new IllegalStateException("missing configuration");
    }
}
```

Nếu là lần đầu code có quyền gọi:

```java
Field field = Broken.class.getDeclaredField("value");
field.getInt(null);
```

Việc tra cứu metadata ở dòng đầu có thể thành công; dòng thứ hai yêu cầu initialization, khiến lỗi khởi tạo lan ra. Khi initializer ném `RuntimeException` như ví dụ, lần khởi tạo đầu thường biểu hiện bằng `ExceptionInInitializerError` bao bọc nguyên nhân. JVM đánh dấu class ở trạng thái khởi tạo lỗi; các lần sử dụng chủ động về sau trong cùng định nghĩa class thường nhận `NoClassDefFoundError` cho class không thể initialize. Không thể “thử đọc lại Field” để class tự khởi tạo từ đầu. Một field khác của cùng class cũng không giải quyết được điều đó.

Nếu gặp tình huống này:

1. Kiểm tra stack trace và **cause** gốc của lỗi khởi tạo, đặc biệt phần chạy trong `<clinit>`.
2. Phân biệt lỗi **tra cứu metadata** (`NoSuchFieldException`), lỗi **quyền truy cập** (`IllegalAccessException`) và lỗi **initialization** (có thể là `ExceptionInInitializerError` hoặc `Error` liên quan).
3. Tránh bọc mọi lỗi thành “field không tồn tại”: cách đó che nguyên nhân thật và làm người đọc tưởng Reflection API bị lỗi.
4. Thiết kế static initialization nhẹ, xác định và ít phụ thuộc tài nguyên môi trường. Dời logic tốn kém/dễ lỗi sang pha khởi tạo có kiểm soát khi thích hợp.

Đoạn `Broken` trên là minh họa lỗi; **không** chạy trong chương trình ví dụ phần 4, để giữ stdout phần 4 xác định và thành công.

## 10. Quyền truy cập, final và class initialization là ba vấn đề khác nhau

`Field` có thể mô tả một field `public`, `private`, `static` hoặc `final`, nhưng các thuộc tính này liên quan đến những quy tắc riêng:

- **Khả năng tìm thấy:** `getDeclaredField` tìm field khai báo trực tiếp, bất kể `public`/`private`; `getField` tìm field `public` theo quy tắc phân cấp.
- **Khả năng đọc/ghi:** `Field.get`/`Field.set` áp dụng kiểm tra truy cập từ caller; `setAccessible`/`trySetAccessible` có quy tắc riêng, chịu giới hạn module trên Java hiện đại.
- **Thời điểm initialize:** Khi một thao tác đọc/ghi field `static` được phép thực hiện, lớp khai báo field được initialize khi cần.
- **Bất biến `final`:** Không được giả định `Field.set` có thể thay đổi `static final`. Trong Java hiện đại, static final là nhóm field không thể được Reflection cho phép ghi theo cách thông thường, kể cả khi cố bật accessible override.

Ví dụ đối với field `static int` công khai:

```java
Field f = MyType.class.getDeclaredField("counter");
int oldValue = f.getInt(null);
f.setInt(null, oldValue + 1);
```

Đây là đoạn minh họa trong phương thức có xử lý ngoại lệ và có lớp `MyType` phù hợp; lần `getInt(null)` sẽ initialize lớp khai báo field khi cần. Hai thao tác get-then-set này **không phải phép tăng nguyên tử**, vì còn những luồng khác có thể can thiệp.

Đối với `private static`, việc lấy metadata vẫn có thể thành công nhưng gọi `get`/`set` từ caller không có quyền sẽ thất bại. Không nên khẳng định class đã initialize chỉ vì một lệnh truy cập **bị từ chối trước khi đến giai đoạn đọc/ghi**; xác định lỗi xảy ra ở bước nào.

## 11. Vấn đề đồng thời: khởi tạo một lần không có nghĩa field thread-safe

JVM phối hợp việc khởi tạo class giữa các thread: khi một thread đang thực hiện initialization của một class, các thread khác cần sử dụng class đó phải tuân theo cơ chế chờ/đồng bộ khởi tạo theo quy tắc JVM. Khi initialization hoàn tất thành công, những thread dùng class không tự chạy lại static initializer.

Tuy nhiên, sau đó:

```java
// Hai thao tác tách biệt, không bảo đảm atomic increment:
int count = field.getInt(null);
field.setInt(null, count + 1);
```

một phép đọc rồi ghi qua Reflection vẫn có thể **race**. `static` không tự làm field `volatile`, không khiến phép cập nhật read-modify-write trở thành atomic, và không thay thế đồng bộ của ứng dụng.

Nếu nhiều thread cùng sửa trạng thái tĩnh, cân nhắc `synchronized`, `volatile` đúng mục tiêu visibility, `AtomicInteger` hoặc cơ chế đồng bộ phù hợp. Tránh dùng quá trình initialization như khóa bảo vệ cho toàn bộ vòng đời mutable static state.

Một mối nguy khác là **circular initialization**: initializer của lớp A dùng static state của B trong khi B dùng ngược static state của A. Khi đường đi phụ thuộc nhau, kết quả có thể bất ngờ do thứ tự gán và giá trị mặc định. Do vậy logic `<clinit>` nên tối giản.

## 12. Mẫu lựa chọn API theo mục đích

| Mục đích | Cách tiếp cận | Tác động cần lưu ý |
| --- | --- | --- |
| Liệt kê field phục vụ tài liệu | `type.getDeclaredFields()`, đọc tên/kiểu/modifier | Không yêu cầu đọc giá trị field, thường tránh init lớp đích |
| Xác định lớp sở hữu field | `field.getDeclaringClass()` | Giúp biết class nào sẽ là đối tượng initialization |
| Lấy giá trị `static int` | `field.getInt(null)` | Có thể initialize declaring class |
| Lấy giá trị `static` kiểu tham chiếu | `field.get(null)` | Có thể initialize declaring class |
| Ghi `static int` có quyền ghi | `field.setInt(null, value)` | Có thể initialize trước khi gán; cần đảm bảo thread safety riêng |
| Chỉ load để tra metadata | `Class.forName(name, false, loader)` | Chủ động tránh initialize class được đặt tên |
| Cố gọi vào `static final` bằng Reflection để sửa | **Không nên thiết kế theo hướng này** | Bị giới hạn quyền ghi, không bảo đảm hợp lệ hoặc an toàn |

Trong công cụ khám phá plugin, serializer, dependency-injection container hoặc diagnostic scanner, “chỉ xem tên field” và “lấy giá trị static field để log” là hai quyết định rất khác nhau. Hãy đặt rõ ngưỡng cho phép kích hoạt khởi tạo, nhất là khi lớp có static block kết nối dịch vụ, đọc môi trường hay cấu hình.

## 13. Lỗi phổ biến và câu hỏi tự kiểm tra

### Lỗi thường gặp

1. **Nhầm `getDeclaredField` với `Field.get`:** tên gần giống nhưng một bên trả metadata, một bên truy cập giá trị và có thể trigger init.
2. **Tin rằng `static final` luôn vô hại:** đọc constant trực tiếp có thể được inline; reflective get vẫn có thể initialize class.
3. **Khởi tạo nhầm owner khi kế thừa:** field được lấy từ `Child.class` có thể thực sự thuộc `Parent`; kiểm tra `getDeclaringClass()`.
4. **Dùng `Class.forName(name)` khi chỉ cần cấu trúc:** biến thể mặc định yêu cầu initialization; dùng `Class.forName(name, false, loader)` khi chỉ cần xem metadata.
5. **Nuốt lỗi `ExceptionInInitializerError`:** kết luận “Reflection không đọc được” mà không xem nguyên nhân static initialization.
6. **Cho rằng initialization là đồng bộ mọi cập nhật `static`:** nó chỉ phối hợp quá trình initialization, không thay thế synchronization cho các thao tác tiếp theo.
7. **Cố ghi `static final`:** quyền tìm kiếm field không đồng nghĩa quyền sửa; static final không hỗ trợ ghi reflective thông thường.

### Tự kiểm tra

**Câu 1.** Nếu `Config.class.getDeclaredField("port")` trả về `Field`, điều đó chứng minh static block của `Config` đã chạy chưa?

**Đáp án:** Chưa. Truy vấn metadata không phải yêu cầu đọc/ghi giá trị `static`.

**Câu 2.** Vì sao đọc `public static final int VERSION = 3;` qua code Java thông thường có thể không initialize lớp, nhưng `field.getInt(null)` thì có?

**Đáp án:** `VERSION` là compile-time constant, compiler có thể inline giá trị; `Field.getInt(null)` vẫn là truy cập reflective thực sự tới field `static` và yêu cầu initialize declaring class.

**Câu 3.** `Field.setInt(null, 25)` chạy khi class chưa initialize thì static initializer diễn ra trước hay sau phép ghi?

**Đáp án:** Initialization cần thiết diễn ra **trước** khi phép ghi field thành công; static initializer có thể đặt giá trị ban đầu, sau đó `setInt` ghi đè thành `25`.

**Câu 4.** Nếu field `public static int port` khai báo ở `Parent` và tìm được bằng `Child.class.getField("port")`, lớp nào được initialize do reflective read field đó?

**Đáp án:** `Parent` là declaring class. Không mặc định rằng `Child` cũng initialize.

**Câu 5.** Nếu lần đầu `Field.get(null)` gây `ExceptionInInitializerError`, có nên thử lại ngay với một `Field` khác thuộc cùng class để khởi tạo lại không?

**Đáp án:** Không. Sau khi class initialization thất bại, class nằm ở trạng thái lỗi trong runtime; cần sửa nguyên nhân và có chiến lược khởi động/lifecycle thích hợp.

**Ghi nhớ:** Java Reflection có thể quan sát cấu trúc lớp mà chưa chạy mã khởi tạo, nhưng **đọc/ghi một field `static` bằng `Field.get`/`Field.set` là một hành động runtime có thể kích hoạt initialization của lớp khai báo field**. Kiểm tra `getDeclaringClass()`, phân biệt compile-time constant với reflective access, và chủ động quản lý side effect từ static initializer khi thiết kế công cụ dùng Reflection.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="fields-012">Field volatile và phân biệt thao tác đọc ghi với cập nhật nguyên tử</a>

<details>
<summary>Xem chi tiết</summary>

# Field `volatile`: đọc/ghi và cập nhật nguyên tử trong Java Reflection

## 1. Vấn đề: `volatile` không có nghĩa là `++` nguyên tử

Với một trường Java khai báo `volatile`, Java Memory Model bảo đảm **ngữ nghĩa đọc/ghi volatile**: khả năng nhìn thấy thay đổi giữa các luồng (*visibility*), một số ràng buộc thứ tự (*ordering*), và tính nguyên tử của **một lần đọc hoặc một lần ghi**. Nhưng `volatile` không biến **chuỗi nhiều thao tác** thành một giao dịch nguyên tử (*atomic transaction*).

Điều đó vẫn đúng khi đọc/ghi trường bằng Reflection:

~~~java
Field field = Counter.class.getDeclaredField("value");
int before = field.getInt(counter);
field.setInt(counter, before + 1);
~~~

Đây chỉ là đoạn minh họa, cần đặt trong phương thức có quyền truy cập thích hợp và xử lý/khai báo checked exceptions. Nếu `Counter.value` là `volatile int`, `getInt` là một lần đọc volatile và `setInt` là một lần ghi volatile. **Hai lời gọi tách rời không tạo thành một phép tăng nguyên tử.**

Hai luồng có thể cùng đọc `0` rồi cùng ghi `1`, khiến một lần tăng bị mất (*lost update*).

> **Phân biệt:** *Atomic read/write* của một field khác với *atomic read–modify–write* (RMW) như `incrementAndGet`, `getAndAdd` hoặc `compareAndSet`.

## 2. Đọc metadata và giá trị của Field volatile

Cho lớp sau:

~~~java
class Counter {
    public volatile int value;
}
~~~

Ta có thể xem modifier và thao tác với instance:

~~~java
Field field = Counter.class.getDeclaredField("value");
boolean declaredVolatile = Modifier.isVolatile(field.getModifiers());

int value = field.getInt(counter);
field.setInt(counter, 5);
~~~

Các ví dụ này là đoạn trích Java, không phải chương trình độc lập; cần import `java.lang.reflect.Field`, `java.lang.reflect.Modifier`, có biến `counter`, và xử lý ngoại lệ phù hợp.

- `getDeclaredField("value")` tìm trường được khai báo tại lớp, kể cả private; `getField` chỉ tìm trường public đủ điều kiện. Tra cứu được metadata **không đồng nghĩa** có quyền đọc/ghi.
- `Modifier.isVolatile(field.getModifiers())` kiểm tra field được khai báo `volatile`. `volatile` là modifier của field, không phải “chế độ bật” cho từng đối tượng `Field`.
- `getInt(counter)` và `setInt(counter, number)` đọc/ghi trường kiểu `int`. Các accessor `get`/`set` tổng quát cũng truy cập trường nền; chúng không làm mất ngữ nghĩa volatile vốn có.
- Với field **instance**, receiver phải có kiểu phù hợp với lớp khai báo; field **static** có thể được truy cập bằng receiver `null` (`Field.get/set` bỏ qua giá trị receiver cho static field).
- Field private có thể cần `trySetAccessible()` hay `setAccessible(true)`; quyền deep reflection còn chịu giới hạn module. **Mở quyền truy cập không biến read–modify–write thành nguyên tử.**
- `Field` cung cấp các phép đọc/ghi đơn lẻ, không có `Field.incrementAndGet()` hoặc `Field.compareAndSet()`.

Nếu field volatile nhưng bạn thực hiện `field.getInt(...)`, tính toán, rồi `field.setInt(...)`, không có gì ngăn luồng khác xen vào giữa hai lời gọi.

## 3. `volatile` thực sự bảo đảm điều gì?

### 3.1. Visibility và happens-before

Một lần đọc/ghi volatile là *synchronization action*. Theo Java Memory Model, một lần ghi vào biến volatile *synchronizes-with* các lần đọc cùng biến volatile về sau trong synchronization order, tạo quan hệ *happens-before*. Điều đó cho phép truyền trạng thái qua một cờ báo:

~~~java
class Message {
    int data;
    volatile boolean ready;
}

// Luồng gửi:
message.data = 42;
message.ready = true;

// Luồng nhận:
if (message.ready) {
    System.out.println(message.data);
}
~~~

Đoạn trên mô tả hai luồng, không phải chương trình Java độc lập. Khi luồng nhận thấy `ready == true` từ lần ghi của luồng gửi, các ghi trước đó như `data = 42` được công bố tới những lần đọc sau của luồng nhận. Đây là giao thức *publication* đơn giản; nếu có các ghi cạnh tranh khác, phải xem lại bất biến và thứ tự của toàn bộ chương trình.

Đừng diễn giải thành “mọi field trong object đều volatile”. Nếu `volatile List<String> items`, phép đọc/ghi **tham chiếu `items`** có ngữ nghĩa volatile; nhưng `items.add(x)` vẫn phụ thuộc vào an toàn luồng của chính collection. `ArrayList` không trở thành thread-safe vì biến tham chiếu được khai báo volatile.

### 3.2. Nguyên tử cho một lần đọc hoặc một lần ghi

Một lần đọc/ghi giá trị của **chính biến volatile** có tính nguyên tử tương ứng với kiểu của nó. `volatile long` và `volatile double` được bảo đảm không bị chia thành hai nửa giá trị không nhất quán. Điều này khác bảo đảm cho chuỗi **đọc → tính toán → ghi**.

Với `volatile int count`, một lệnh `count = 7` là một ghi volatile; `int x = count` là một đọc volatile; nhưng `count++` vẫn gồm đọc giá trị cũ, tính giá trị mới, ghi lại.

### 3.3. Không phải khóa

`volatile` không cung cấp *mutual exclusion*. Nó không tự bảo vệ bất biến gồm nhiều biến, chẳng hạn “`available + reserved == total`”. Nếu cần cập nhật hai trường như một transaction, hãy xét `synchronized`/`Lock` hoặc một trạng thái bất biến thay thế nguyên tử.

## 4. Timeline của một lần tăng bị mất

Ban đầu `value = 0`. Hai luồng A, B cùng thực hiện:

~~~java
int oldValue = field.getInt(counter);       // volatile read
field.setInt(counter, oldValue + 1);        // volatile write
~~~

Một cách sắp xen kẽ hợp lệ:

| Bước | Luồng A | Luồng B | Giá trị chung |
| --- | --- | --- | --- |
| 1 | Đọc `0` | — | 0 |
| 2 | — | Đọc `0` | 0 |
| 3 | Ghi `1` | — | 1 |
| 4 | — | Ghi `1` | 1 |

Kết quả cuối là **1**, dù có hai yêu cầu tăng; mỗi đọc/ghi volatile đơn lẻ đều đúng quy tắc JMM. Dù mã là `x++`, `x += 1`, `getInt`/`setInt` hay `getVolatile`/`setVolatile`, nếu không có CAS, thao tác atomic RMW hoặc khóa, *lost update* vẫn có thể xảy ra.

## 5. Chương trình Java 17+ tái hiện lỗi một cách xác định

Lưu thành **`VolatileReflectionDemo.java`**:

~~~java
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

public class VolatileReflectionDemo {
    public static class Counter {
        public volatile int value;
    }

    public static void main(String[] args) throws Exception {
        Counter counter = new Counter();
        Field field = Counter.class.getDeclaredField("value");

        System.out.println("volatile: "
                + Modifier.isVolatile(field.getModifiers()));

        // Buộc cả hai luồng đọc 0 trước khi một luồng được ghi.
        CyclicBarrier bothRead = new CyclicBarrier(2);
        Runnable unsafe = () -> {
            try {
                int snapshot = field.getInt(counter);
                bothRead.await();
                field.setInt(counter, snapshot + 1);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };

        Thread a = new Thread(unsafe, "unsafe-A");
        Thread b = new Thread(unsafe, "unsafe-B");
        a.start();
        b.start();
        a.join();
        b.join();
        System.out.println("Field get+set: " + field.getInt(counter));

        // Cùng field, nhưng dùng phép tăng nguyên tử.
        counter.value = 0;
        AtomicIntegerFieldUpdater<Counter> updater =
                AtomicIntegerFieldUpdater.newUpdater(
                        Counter.class, "value");

        Runnable safe = () -> {
            for (int i = 0; i < 1000; i++) {
                updater.incrementAndGet(counter);
            }
        };

        Thread c = new Thread(safe, "safe-C");
        Thread d = new Thread(safe, "safe-D");
        c.start();
        d.start();
        c.join();
        d.join();
        System.out.println("AtomicIntegerFieldUpdater: "
                + field.getInt(counter));

        // Một phép RMW nguyên tử qua VarHandle.
        VarHandle handle = MethodHandles.lookup().findVarHandle(
                Counter.class, "value", int.class);

        int oldValue = (int) handle.getAndAdd(counter, 1);
        int newValue = (int) handle.getVolatile(counter);
        System.out.println("VarHandle old=" + oldValue
                + ", new=" + newValue);
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac VolatileReflectionDemo.java
java VolatileReflectionDemo
~~~

Đầu ra chính xác:

~~~text
volatile: true
Field get+set: 1
AtomicIntegerFieldUpdater: 2000
VarHandle old=2000, new=2001
~~~

**Phần 1 — lost update:** `CyclicBarrier` khiến cả hai luồng đều phải hoàn thành `field.getInt(counter)` trước khi luồng nào gọi `setInt`. Cả hai đều lưu `snapshot = 0`; hai thao tác ghi kế tiếp đều ghi 1. Kết quả **luôn 1 trong thí nghiệm này**; ta không phụ thuộc vào lịch chạy ngẫu nhiên để phát hiện race.

**Phần 2 — cập nhật nguyên tử:** Hai luồng cùng tăng 1000 lần bằng `AtomicIntegerFieldUpdater.incrementAndGet`. Mỗi lần tăng là một read–modify–write nguyên tử trên cùng field, tổng cộng đúng **2000**.

**Phần 3 — VarHandle:** `getAndAdd(counter, 1)` trả giá trị trước cập nhật (2000) và cộng thêm 1 như một thao tác nguyên tử; `getVolatile(counter)` đọc 2001. Cả hai API đều giữ quy tắc kiểu field và quyền truy cập thích hợp.

Không dùng `Thread.sleep` để “thử may rủi”; barrier giúp chứng minh rõ lỗi. Chương trình dùng field public để tách bạch lỗi về concurrency khỏi lỗi Java access control.

## 6. Ba lựa chọn sửa lỗi

### 6.1. `AtomicIntegerFieldUpdater`

Khi muốn giữ một trường instance `volatile int` trong class hiện hữu, sử dụng:

~~~java
AtomicIntegerFieldUpdater<Counter> updater =
        AtomicIntegerFieldUpdater.newUpdater(Counter.class, "value");

int incremented = updater.incrementAndGet(counter);
int previous = updater.getAndIncrement(counter);
boolean swapped = updater.compareAndSet(counter, previous + 1, 50);
~~~

Đoạn code minh họa dùng lớp `Counter` của ví dụ đầy đủ và cần ngữ cảnh Java phù hợp. `compareAndSet` chỉ trả `true` nếu giá trị hiện tại đúng bằng `expected`; ví dụ không khẳng định CAS phải thành công dưới cạnh tranh.

Điều kiện: trường phải là **instance** `volatile int` (không phải static), với quyền truy cập được phép. Có `AtomicLongFieldUpdater` và `AtomicReferenceFieldUpdater` cho các kiểu tương ứng. Factory có thể báo lỗi nếu field không đúng kiểu/không volatile/không truy cập được.

Một updater chỉ giúp các đường cập nhật sử dụng đúng thao tác nguyên tử. Nếu phần còn lại của ứng dụng vẫn đọc rồi tự ghi lại bằng `setInt` mà không phối hợp, dữ liệu vẫn có thể mất cập nhật.

### 6.2. `VarHandle`: tránh nhầm access mode và thao tác nguyên tử

Có thể lấy VarHandle cho trường, nếu `Lookup` có quyền:

~~~java
VarHandle vh = MethodHandles.lookup().findVarHandle(
        Counter.class, "value", int.class);

// Hai thao tác RIÊNG LẺ: không phải increment nguyên tử.
int before = (int) vh.getVolatile(counter);
vh.setVolatile(counter, before + 1);

// Một thao tác RMW nguyên tử:
int previous = (int) vh.getAndAdd(counter, 1);
boolean success = vh.compareAndSet(counter, previous + 1, 10);
~~~

Đây là đoạn minh họa, không phải chương trình chạy độc lập. `compareAndSet` chỉ thành công nếu giá trị hiện tại đúng với `expected`.

`VarHandle` còn có các access mode như `get`/`set` (plain), `getOpaque`/`setOpaque`, `getAcquire`/`setRelease`, `getVolatile`/`setVolatile`, bên cạnh những chế độ atomic RMW. Các mode có memory ordering khác nhau: **đừng cho rằng mọi cách truy cập qua VarHandle tự động có volatile semantics**. Nếu cần đọc/ghi trường volatile và sự phối hợp giữa luồng, chọn access mode có hợp đồng memory ordering phù hợp.

`VarHandle.getAndAdd` hoặc `compareAndSet` là công cụ phù hợp cho một biến; không có nghĩa cả một nghiệp vụ với nhiều field được bảo vệ.

### 6.3. `AtomicInteger` hoặc khóa

Nếu có thể đổi mô hình đối tượng, một trường kiểu `AtomicInteger` diễn đạt mục đích rõ hơn:

~~~java
private final java.util.concurrent.atomic.AtomicInteger count =
        new java.util.concurrent.atomic.AtomicInteger();

void increase() {
    count.incrementAndGet();
}
~~~

Đây là đoạn minh họa bên trong class. Với nhiều biến cần cập nhật nhất quán, một khối `synchronized` hoặc `Lock` có thể thuận tiện hơn CAS trên từng biến đơn lẻ.

## 7. Reflection: quyền truy cập và thread safety là hai trục khác nhau

Một số trường hợp thường gây nhầm:

| Tình huống | Điều thực sự xảy ra |
| --- | --- |
| `Modifier.isVolatile(field.getModifiers()) == true` | Trường được khai báo volatile; không chứng minh được thao tác đọc–tính–ghi là nguyên tử. |
| `field.trySetAccessible() == true` | Được phép bỏ qua kiểm tra truy cập Java trên `Field` đó; không tạo khóa. |
| `field.getInt(target)` rồi `field.setInt(target, x)` | Hai thao tác riêng; có thể bị luồng khác xen giữa. |
| `getVolatile` rồi `setVolatile` trên VarHandle | Từng thao tác có ngữ nghĩa volatile nhưng cặp thao tác vẫn không RMW nguyên tử. |
| `getAndAdd` hoặc `compareAndSet` | Cập nhật nguyên tử đối với biến đích theo hợp đồng API. |
| `volatile List<?> items` | Công bố/đọc tham chiếu với volatile semantics; không bảo đảm các lần sửa collection đều thread-safe. |
| `volatile int x; volatile int y;` | Không tự bảo đảm bất biến chung giữa `x` và `y`. |

Đừng lẫn lỗi truy cập Reflection (`IllegalAccessException`, `IllegalArgumentException` khi receiver không hợp lệ) với race condition. Sửa quyền truy cập bằng `setAccessible` không chữa được *lost update*.

**Giới hạn kiểu:** một field không thể vừa `final` vừa `volatile`; Java cấm kết hợp hai modifier ấy. `AtomicIntegerFieldUpdater` yêu cầu field instance và không áp dụng trực tiếp cho `static volatile int`. Với trường static, có thể dùng `static final AtomicInteger`, khóa, hoặc một `VarHandle` static phù hợp.

## 8. Checklist chọn giải pháp

1. Chỉ cần một luồng thông báo cờ trạng thái cho các luồng khác? `volatile` có thể đủ khi thiết kế giao thức visibility/ordering chuẩn.
2. Cần đọc/ghi **một giá trị riêng lẻ** với visibility giữa luồng? Trường volatile và accessor tương thích là lựa chọn đơn giản.
3. Cần `count++`, `count += delta`, “nếu vẫn bằng X thì đổi thành Y”? Chọn `AtomicInteger`, `AtomicIntegerFieldUpdater`, `VarHandle.getAndAdd`/`compareAndSet`, hoặc khóa.
4. Cần cập nhật nhiều field đồng thời theo một bất biến? Xét khóa hoặc đối tượng trạng thái bất biến được thay nguyên tử.
5. Tên field chỉ biết lúc runtime? Kiểm tra `getType()`, `getModifiers()`, instance/static và quyền truy cập, rồi chọn công cụ phù hợp; `Field.get/set` không có CAS.
6. Viết test concurrency? Cố gắng tạo lịch xen kẽ kiểm soát được như `CyclicBarrier`, thay vì dựa vào một lần chạy stress không thấy lỗi.

### Tự kiểm tra

- Hai luồng thực hiện `volatileCount++` khi ban đầu bằng 0. Kết quả chắc chắn 2? **Không**, có thể 1 vì mất cập nhật.
- `Field.getInt`/`setInt` trên `volatile int` có bỏ qua visibility của volatile? **Không**, nhưng hai lời gọi vẫn độc lập.
- `VarHandle.getVolatile` rồi `setVolatile` có thay được `getAndAdd`? **Không**, vì không phải atomic RMW.
- `AtomicIntegerFieldUpdater` làm việc với `static volatile int`? **Không**, chỉ với instance field thỏa điều kiện.
- `volatile List` có biến `ArrayList.add` thành thread-safe? **Không**, cần an toàn luồng của collection hoặc đồng bộ riêng.

**Kết luận:** `volatile` bảo đảm ngữ nghĩa cho **từng lần đọc/ghi field** và quan hệ visibility/ordering giữa luồng. Reflection vẫn tôn trọng thuộc tính ấy. Muốn cập nhật mà không mất giá trị, phải dùng **atomic read–modify–write** hoặc **đồng bộ hóa** thích hợp.

### Tài liệu chuẩn

- [Java Language Specification 17 — §17.4 Memory Model](https://docs.oracle.com/javase/specs/jls/se17/html/jls-17.html#jls-17.4)
- [JDK 17 — `java.lang.reflect.Field`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Field.html)
- [JDK 17 — `AtomicIntegerFieldUpdater`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/atomic/AtomicIntegerFieldUpdater.html)
- [JDK 17 — `VarHandle`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/invoke/VarHandle.html)
- [JDK 17 — `AtomicInteger`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/atomic/AtomicInteger.html)


</details>

- [Quay lại đầu trang](#back-to-top)

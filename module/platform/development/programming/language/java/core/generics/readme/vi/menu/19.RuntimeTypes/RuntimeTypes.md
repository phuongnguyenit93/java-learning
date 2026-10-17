<a id="back-to-top"></a>

# Kiểu lúc chạy

## Menu
- [Kiểu có đầy đủ thông tin lúc chạy (reifiable type) là gì](#runtime-types-001)
- [Phân loại kiểu thông thường, raw type, wildcard không giới hạn và kiểu mảng](#runtime-types-002)
- [Vì sao List&lt;String&gt; không có đủ thông tin kiểu lúc chạy](#runtime-types-003)
- [Class literal: String.class, List.class và giới hạn của List&lt;String&gt;.class](#runtime-types-004)
- [instanceof với List&lt;?&gt; và các trường hợp kiểu tham số hóa hợp lệ](#runtime-types-005)
- [Phân biệt kiểm tra kiểu tổng thể với xác minh kiểu của mọi phần tử](#runtime-types-006)
- [Giới hạn của mẫu kiểu và ép kiểu sau xóa kiểu](#runtime-types-007)
- [Không suy ra đối số kiểu tùy ý từ getClass() của đối tượng](#runtime-types-008)

## <a id="runtime-types-001">Kiểu có đầy đủ thông tin lúc chạy (reifiable type) là gì</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểu có đầy đủ thông tin lúc chạy (reifiable type) là gì trong Java?

Trong Java, **reifiable type** là kiểu có biểu diễn đầy đủ tại thời điểm chạy (*runtime*) để JVM thực hiện các phép kiểm tra kiểu phù hợp với kiểu đó. Ngược lại, nhiều kiểu liên quan đến generic, ví dụ `List<String>` hoặc biến kiểu `T`, là **non-reifiable**: sau quá trình *type erasure*, JVM không có đủ thông tin về đối số kiểu cụ thể để kiểm tra trực tiếp toàn bộ kiểu generic như trình biên dịch đã kiểm tra ở mã nguồn.

Ví dụ, một đối tượng `ArrayList<String>` và một đối tượng `ArrayList<Integer>` đều có runtime class `ArrayList`. JVM có thể kiểm tra chúng có phải `List` hay không, nhưng không thể chỉ dùng `instanceof` trên một tham chiếu `Object` để xác nhận danh sách thứ nhất thực sự tuân thủ `List<String>`.

Khái niệm này quan trọng khi học `instanceof`, class literal, generic array, raw type, wildcard và reflection.

> Điểm dễ nhầm: **`List<?>` là reifiable**, nhưng điều đó **không có nghĩa JVM biết kiểu phần tử của danh sách**. `List<?>` chỉ yêu cầu kiểm tra đây là một `List`, không yêu cầu xác định đối số kiểu cụ thể.

## 1. “Có đầy đủ thông tin lúc chạy” là đầy đủ cho phép kiểm tra nào?

Ở mã nguồn, `List<String>` giúp `javac` biết rằng một lời gọi `add` phải nhận `String` và kết quả `get` được xem là `String`. Nhưng JVM dùng runtime class và các descriptor đã xóa kiểu để hoạt động. Đối số `String` của biến `List<String>` thông thường không được lưu thành một class riêng mang tên `List<String>`.

Xét đoạn minh họa:

```java
import java.util.ArrayList;
import java.util.List;

List<String> names = new ArrayList<>();
List<Integer> numbers = new ArrayList<>();

boolean sameClass = names.getClass() == numbers.getClass(); // true
```

Đây là snippet đặt trong một phương thức Java hợp lệ. Cả hai đối tượng đều được tạo bằng `ArrayList`; khác biệt đối số generic thuộc thông tin kiểu ở nguồn, không tạo hai runtime classes riêng.

Vì vậy, **reifiable** không có nghĩa “lưu đầy đủ mọi chi tiết kiểu xuất hiện trong mã nguồn”, mà là kiểu có đủ cấu trúc runtime cần thiết theo định nghĩa ngôn ngữ. `List<?>` chỉ mô tả kiểu tham số hóa mà đối số kiểu không bị ràng buộc; runtime không cần phân biệt `String`, `Integer` hay kiểu phần tử cụ thể nào khác để kiểm tra một đối tượng là `List<?>`.

Đối chiếu với **`List<String>`**, để kiểm tra đúng nghĩa thì phải xác định cả tham số `String`. Loại kiểm tra ấy không được bảo đảm bằng phép kiểm tra runtime thông thường trên một đối tượng danh sách đã xóa kiểu.

## 2. Nhận diện nhanh các nhóm reifiable

Theo **JLS §4.7**, các nhóm reifiable thường gặp là primitive, class/interface không generic, raw type, parameterized type mà **tất cả đối số** đều là wildcard không giới hạn `?`, và mảng có component type reifiable. Kiểu lồng nhau còn cần xét các thành phần tên kiểu theo quy tắc JLS.

| Kiểu ví dụ | Reifiable? | Giải thích |
| --- | --- | --- |
| `int`, `String`, `Runnable`, `String[]` | Có | Primitive, kiểu không generic hoặc mảng có component runtime phù hợp |
| Raw `List`, `Map` | Có | Có runtime class/interface nhưng thao tác raw có thể phát cảnh báo unchecked |
| `List<?>`, `Map<?, ?>`, `List<?>[]` | Có | Wildcard không bound và kiểu thành phần reifiable |
| `List<String>`, `Map<String,Integer>` | Không | Type arguments cụ thể không được kiểm tra bởi runtime class của instance |
| `List<? extends Number>`, `List<? super Integer>` | Không | Wildcard có cận, khác `?` không giới hạn |
| `T`, `T[]`, `List<String>[]` | Không | Type variable hoặc component type non-reifiable |

Ba bẫy quan trọng: `List<? extends Object>` không thuộc nhóm unbounded wildcard như `List<?>`; `List<List<?>>` không reifiable vì type argument ngoài là `List<?>` chứ không phải một dấu `?`; và bound `<T extends Number>` **không biến T thành reifiable**.

Bảng này chỉ phục vụ nhận diện khái niệm. Bài phân loại kiểu tiếp theo triển khai chi tiết trường hợp raw/wildcard/mảng cùng runtime array component, Reflection và tác động lên API.


## 3. So sánh kiểu reifiable và non-reifiable qua `instanceof`

Giả sử dữ liệu chỉ có kiểu tĩnh là `Object`:

```java
Object value = java.util.List.of("Java", "Generics");

boolean a = value instanceof java.util.List<?>; // Hợp lệ
boolean b = value instanceof java.util.List;    // Hợp lệ, dùng raw type
// boolean c = value instanceof java.util.List<String>; // Lỗi biên dịch
```

Lời gọi `instanceof List<?>` hỏi **đối tượng có phải một `List` không?** Không phải “mọi phần tử có phải `String` không?”. `instanceof List` cũng kiểm tra được class/interface sau erasure, nhưng nên ưu tiên `List<?>` để giữ cách diễn đạt phù hợp với generic.

Một phản chứng quan trọng:

```java
Object value = java.util.List.of(10, 20, 30);

System.out.println(value instanceof java.util.List<?>); // true
```

Kết quả là `true` mặc dù các phần tử ở đây là `Integer`, hoàn toàn không phải `String`. `List<?>` không khẳng định một kiểu phần tử nào.

Dạng kiểm tra thông dụng trong mã Java 17+ là pattern matching:

```java
if (value instanceof java.util.List<?> items) {
    System.out.println("So phan tu: " + items.size());
}
```

`items` có kiểu `List<?>`; đọc một phần tử sẽ cho một giá trị dùng được dưới kiểu `Object`, và không thể thêm tùy ý `String` hoặc `Integer` qua biến này.

**Lưu ý về kiểm tra kiểu có generic:** diễn đạt “mọi lần viết `instanceof List<String>` đều lỗi trong bất kỳ ngữ cảnh nào” là quá đơn giản; Java hiện đại còn xét *cast compatibility* và kiểu tĩnh của biểu thức. Trường hợp cần ghi nhớ để kiểm tra một đối tượng chưa rõ kiểu, như `Object value` ở trên, là **không thể xác minh một đối số generic cụ thể bằng `instanceof`**. Đừng sử dụng một trường hợp compiler đã biết kiểu tĩnh để suy diễn rằng runtime có thể đọc `String` bên trong một `List`.

## 4. Vì sao `List<?>` là reifiable nhưng `List<String>` thì không?

Hai kiểu đều sử dụng interface `List` ở runtime, song yêu cầu kiểm tra khác nhau:

```text
Object value = ...

value instanceof List<?>
  -> Kiểm tra đối tượng implements List
  -> Không yêu cầu xác định kiểu phần tử

value instanceof List<String>
  -> Muốn kiểm tra cả đối số String
  -> Runtime class List không lưu đối số String riêng cho instance
  -> Không có kiểm tra runtime trực tiếp tương ứng
```

Sự khác biệt nằm ở **mức khẳng định của kiểu**. `List<?>` đưa ra yêu cầu yếu hơn: “là danh sách của một kiểu phần tử chưa biết”. JVM có thể kiểm tra phần thô `List` và đủ đáp ứng kiểm tra. Ngược lại, `List<String>` yêu cầu ràng buộc cụ thể không được lưu đầy đủ trong runtime class.

Điều này cũng giải thích tại sao `Map<?, ?>` reifiable: cả kiểu khóa và kiểu giá trị đều không được ràng buộc, nên kiểm tra runtime chỉ cần xác nhận đây là `Map`.

Không được diễn giải dấu `?` là “chấp nhận ghi mọi loại phần tử”. Trong `List<?>`, compiler coi kiểu phần tử là một kiểu chưa biết; bạn có thể đọc giá trị như `Object` nhưng **không được thêm đối tượng tùy ý** (thông thường chỉ có thể thêm `null` qua phương thức `add`). Đây là khác biệt căn bản so với raw `List`, nơi các thao tác ghi có thể tạo **unchecked warning** và heap pollution.

## 5. Mảng là ví dụ trực quan nhất về runtime type checks

Mảng Java mang thông tin **component type** ở runtime. Vì vậy mảng `String[]` có thể được kiểm tra như `String[]`, dù được giữ trong biến kiểu `Object` hoặc `Object[]`.

```java
Object value = new String[] { "An", "Binh" };

System.out.println(value instanceof String[]); // true
System.out.println(value instanceof Object[]); // true
```

Array là **covariant**, nên một `String[]` có thể được tham chiếu qua `Object[]`. Nhưng runtime vẫn biết array thực có component type `String` và có thể từ chối một thao tác ghi sai kiểu:

```java
Object[] slots = new String[1];

try {
    slots[0] = 42;
} catch (ArrayStoreException exception) {
    System.out.println("ArrayStoreException");
}
```

Kết quả là `ArrayStoreException`: `Integer` không thể lưu vào array runtime `String[]`.

Đối chiếu với generic collection:

```java
java.util.List<String> values = new java.util.ArrayList<>();
// values.add(42); // Bị compiler từ chối ngay khi có thông tin generic đúng
```

Array kiểm tra component type ở runtime; generic collection chủ yếu dựa vào kiểm tra kiểu ở nguồn. Khi chương trình dùng raw type hoặc unchecked conversion để lách generic, lỗi có thể chỉ lộ ra dưới dạng `ClassCastException` khi đọc phần tử, thay vì `ArrayStoreException` lúc ghi.

## 6. Vì sao `new List<?>[2]` được phép còn `new List<String>[2]` thì không?

Đây là hệ quả thực tế quan trọng của reifiability.

```java
// Hợp lệ:
java.util.List<?>[] groups = new java.util.List<?>[2];
groups[0] = java.util.List.of("Java");
groups[1] = java.util.List.of(17);

// Không biên dịch:
// java.util.List<String>[] strings = new java.util.List<String>[2];
```

Phần tử của `List<?>[]` có type `List<?>`, thuộc nhóm reifiable. Tại runtime, array đó có component class là **`List`**, nên mọi đối tượng thực hiện `List` đều phù hợp với kiểu phần tử của array.

Còn `List<String>` là non-reifiable. Nếu cho tạo trực tiếp array `List<String>[]`, kiểm tra gán phần tử vào array không thể xác minh đối số `String` của từng danh sách. Java từ chối **generic array creation** với kiểu thành phần như vậy để tránh tạo cảm giác an toàn kiểu sai.

Điều đáng chú ý là `List<?>[]` cho phép đặt cả `List<String>` lẫn `List<Integer>`, vì nó không ràng buộc kiểu phần tử bên trong từng list. Nó không làm các phần tử trở thành `List<Object>`, cũng không cho phép tùy ý ghi một `Integer` vào một `List<String>` thông qua tham chiếu `List<?>`.

Ngoài ra, **không tạo được `new List<String>[2]` không có nghĩa một biến khai báo `List<String>[]` tuyệt đối không bao giờ tồn tại**. Mã cũ hoặc unchecked cast có thể tạo ra các biến mang kiểu đó; chúng đòi hỏi kiểm soát heap pollution cẩn thận. Quy tắc cần nhớ ở đây là Java **cấm trực tiếp tạo array có component type non-reifiable**.

## 7. Type variable `T` có reifiable không?

Không. Cho dù có upper bound, type variable vẫn không chứa một kiểu cụ thể duy nhất tại runtime để dùng như một component type hay class literal:

```java
// Snippet: ba dòng có chú thích lỗi không thể bật lên để biên dịch.
class Container<T extends Number> {
    void inspect(Object value) {
        // if (value instanceof T) { } // Không hợp lệ
        // Class<?> type = T.class;      // Không hợp lệ
        // T[] elements = new T[10];     // Generic array creation không hợp lệ
    }
}
```

Ở tầng erasure, `T extends Number` được xóa về `Number`. Nhưng điều đó **không biến `T` thành `Number` về mặt nghĩa của kiểu tham số hóa ở nguồn**: một `Container<Integer>` vẫn muốn `T = Integer`, trong khi một `Container<Double>` dùng `T = Double`.

Nếu yêu cầu thực tế chỉ là hỏi “`value` có phải `Number` không?”, có thể viết `value instanceof Number`; nhưng câu trả lời đó không chứng minh `value` là **kiểu cụ thể `T`**.

Một cách phổ biến để kiểm tra một kiểu class cụ thể do caller cung cấp là truyền **type token**:

```java
static <T> boolean matches(Class<T> token, Object value) {
    return token.isInstance(value);
}

// matches(String.class, "Java") -> true
// matches(Integer.class, "Java") -> false
```

`Class<T>` giúp API giữ thông tin kiểu của **class cụ thể** khi caller truyền `String.class` hoặc `Integer.class`. Nhưng không thể dùng một class literal `List<String>.class` để nhận token đại diện đầy đủ cho `List<String>`; `List.class` chỉ đại diện cho raw runtime interface `List`.

Khi cần dựng `T`, thay vì `new T()` có thể yêu cầu `Supplier<T>` hoặc factory; đây là giải pháp cho vấn đề **không biết constructor/kiểu cụ thể**, không phải cách làm cho mọi kiểu generic tự nhiên trở thành reifiable.

## 8. Chương trình Java 17+ quan sát reifiable type trực tiếp

Lưu toàn bộ mã sau vào **`ReifiableTypeDemo.java`**:

```java
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ReifiableTypeDemo {
    static final class Fields {
        List<String> named;
        List<?> unknown;
    }

    public static void main(String[] args) throws NoSuchFieldException {
        Object names = new ArrayList<String>(List.of("An", "Binh"));
        Object numbers = new ArrayList<Integer>(List.of(1, 2));
        Object map = Map.of("a", 1);

        System.out.println("List<?> runtime check: "
                + (names instanceof List<?>));
        System.out.println("Map<?, ?> runtime check: "
                + (map instanceof Map<?, ?>));
        System.out.println("Same ArrayList class: "
                + (names.getClass() == numbers.getClass()));

        List<?>[] groups = new List<?>[2];
        groups[0] = List.of("Java");
        groups[1] = List.of(17);

        System.out.println("List<?>[] runtime check: "
                + (groups instanceof List<?>[]));
        System.out.println("Array component: "
                + groups.getClass().getComponentType().getTypeName());

        Object[] slots = new String[1];
        try {
            slots[0] = 42;
        } catch (ArrayStoreException exception) {
            System.out.println("Array store: ArrayStoreException");
        }

        Class<?> token = String.class;
        System.out.println("Class token checks String: "
                + token.isInstance("ok"));

        Field field = Fields.class.getDeclaredField("named");
        System.out.println("Field erased type: "
                + field.getType().getTypeName());
        System.out.println("Field generic metadata: "
                + field.getGenericType().getTypeName());

        Object mixed = List.of("Java", 42);
        System.out.println("Mixed list matches List<?>: "
                + (mixed instanceof List<?>));
    }
}
```

Biên dịch và chạy:

```bash
javac -encoding UTF-8 ReifiableTypeDemo.java
java ReifiableTypeDemo
```

Kết quả chuẩn:

```text
List<?> runtime check: true
Map<?, ?> runtime check: true
Same ArrayList class: true
List<?>[] runtime check: true
Array component: java.util.List
Array store: ArrayStoreException
Class token checks String: true
Field erased type: java.util.List
Field generic metadata: java.util.List<java.lang.String>
Mixed list matches List<?>: true
```

Giải thích theo thứ tự:

1. `List<?>` và `Map<?, ?>` cho phép kiểm tra interface runtime mà không đòi nhận diện type argument.
2. `ArrayList<String>` và `ArrayList<Integer>` đều là `ArrayList`, nên `getClass()` bằng nhau. Đối số kiểu generic không khiến JVM tự sinh hai runtime classes.
3. `new List<?>[2]` hợp lệ; array runtime có component type là interface `java.util.List`.
4. `Object[] slots = new String[1]` giữ nguyên runtime array type `String[]`; ghi số nguyên bị từ chối bằng `ArrayStoreException`.
5. `String.class` là class token mang thông tin `String` thực sự; `Class.isInstance("ok")` kiểm tra thành công.
6. `Field.getType()` cho kiểu đã xóa `List`, còn `Field.getGenericType()` có thể đọc metadata khai báo trường `List<String>`.
7. Một danh sách có lẫn `String` và `Integer` vẫn thỏa `instanceof List<?>`; điều này minh họa rằng **reifiable không phải xác thực loại phần tử**.

Chương trình cố tình dùng `List.of("Java", 42)` trong ví dụ cuối để chứng minh phép kiểm tra `List<?>` chấp nhận danh sách có phần tử nhiều kiểu. Nó không tuyên bố đây là `List<String>`.

## 9. Reflection vẫn thấy generic: có mâu thuẫn với non-reifiable không?

Không. Java phân biệt **metadata khai báo generic** với **runtime type identity của một đối tượng**.

Trong ví dụ:

```java
class Example {
    java.util.List<String> names;
}
```

Nếu class được biên dịch với metadata `Signature` tương ứng, reflection qua `Field.getGenericType()` có thể đọc `List<String>`. Nhưng `Field.getType()` chỉ cho runtime class `List`.

Metadata cho biết **trường được khai báo** có type argument `String`. Nó không tự động bảo đảm rằng một đối tượng được đặt vào trường qua mã thiếu an toàn luôn chỉ chứa `String`. Tương tự, việc một subclass được khai báo rõ `extends Parent<String>` có thể được phản chiếu ở `getGenericSuperclass()`; không có nghĩa mọi instance của `Parent<T>` đều mang một runtime class riêng theo `T`.

Đối với một đối tượng thông thường:

```java
Object value = new java.util.ArrayList<String>();
Class<?> runtime = value.getClass(); // java.util.ArrayList
```

Bạn không thể suy ra `String` chỉ từ `runtime` theo cơ chế `getClass()` thông thường. Một `ArrayList<Integer>` cũng trả class ấy.

**Vì vậy:** một non-reifiable parameterized type có thể xuất hiện trong **metadata của khai báo**, nhưng vẫn không đủ để JVM thực hiện trực tiếp phép kiểm tra kiểu `List<String>` trên một biến `Object`.

## 10. Reifiable type, raw type, wildcard và erasure khác nhau thế nào?

Bốn khái niệm dễ gặp cùng lúc nhưng trả lời bốn câu hỏi khác nhau:

| Khái niệm | Câu hỏi nó trả lời | Ví dụ |
| --- | --- | --- |
| **Type erasure** | Compiler biến kiểu generic thành descriptor/kiểu đã xóa ra sao? | `List<String>` → `List`, `T` → `Object` hoặc bound |
| **Reifiable type** | JVM có đủ biểu diễn runtime để kiểm tra kiểu này không? | `String[]`, `List<?>`, `Map<?, ?>` |
| **Raw type** | Có đang dùng một class/interface generic mà không ghi type argument không? | `List`, `Map` |
| **Unbounded wildcard** | Kiểu tham số hóa có chấp nhận một type argument chưa biết không? | `List<?>`, `Map<?, ?>` |

Raw `List` là reifiable, nhưng raw type có thể gây **unchecked warnings** khi thao tác dữ liệu generic. `List<?>` cũng reifiable, đồng thời vẫn giữ quy tắc generic giúp ngăn thêm phần tử không rõ kiểu.

Điểm đáng nhớ: **reifiable không đồng nghĩa “an toàn hơn trong mọi thao tác”**. Một raw `List` reifiable mà vẫn có thể cho phép `raw.add(42)` vào một đối tượng thực chất đang được mã khác xem như `List<String>`. Ngược lại, `List<String>` non-reifiable nhưng có kiểm tra kiểu mạnh ở mã nguồn Java thông thường.

## 11. `Class<String>` có phải reifiable type không?

Đây là trường hợp thú vị: `String.class` là một biểu thức class literal hợp lệ, và **kiểu tĩnh của biểu thức** thường được trình biên dịch biểu diễn là `Class<String>`. Nhưng chính kiểu tham số hóa `Class<String>` lại **không phải** một parameterized type có toàn bộ đối số là unbounded wildcard, nên không thuộc nhóm reifiable generic type.

Điều này không mâu thuẫn: class literal `String.class` tạo/tham chiếu đối tượng `Class` mô tả **class runtime `String`**, không mô tả một runtime class riêng tên `Class<String>`. Mệnh đề `Class<String>` non-reifiable nói về việc kiểm tra nguyên cả kiểu generic `Class<String>` trên một đối tượng không rõ kiểu; nó không làm mất khả năng dùng `String.class` làm token.

Ví dụ:

```java
Class<String> stringType = String.class;
System.out.println(stringType.isInstance("text")); // true

Class<?> unknownClass = stringType; // Hợp lệ
```

Không có cú pháp class literal `List<String>.class` tương ứng để JVM biểu diễn đầy đủ một `List` có phần tử kiểu `String`. Nếu thư viện serialization cần giữ cả `List<String>`, nó thường yêu cầu `java.lang.reflect.Type`, `ParameterizedType` hoặc một cấu trúc *type token* riêng có thể giữ metadata cấp khai báo; việc kiểm tra/chuyển đổi từng phần tử vẫn phụ thuộc hợp đồng của thư viện.

## 12. Những giới hạn thực tế do kiểu non-reifiable

### 12.1. `instanceof` không thay thế kiểm tra phần tử

```java
Object value = java.util.List.of(1, 2);

// Hợp lệ:
if (value instanceof java.util.List<?> list) {
    for (Object element : list) {
        System.out.println(element);
    }
}

// Không hợp lệ nếu value có kiểu tĩnh Object:
// if (value instanceof java.util.List<String>) { ... }
```

Nếu phải xác thực một danh sách chỉ chứa `String`, cần kiểm tra thực tế các phần tử hoặc dùng đối tượng do một API đáng tin đã bảo đảm loại dữ liệu. Chỉ biết `instanceof List<?>` là chưa đủ.

### 12.2. Không tạo generic array có component type non-reifiable

```java
// Không hợp lệ:
// java.util.List<String>[] array = new java.util.List<String>[3];

// Hợp lệ:
java.util.List<?>[] array = new java.util.List<?>[3];
```

Nếu thuật toán phải chứa nhiều danh sách kiểu `String`, thường dễ giữ an toàn kiểu hơn bằng **`List<List<String>>`** thay vì cố tạo `List<String>[]`.

### 12.3. Không dùng type variable như một class runtime cụ thể

```java
// Trong một generic class:
class Sample<T> {
    // Class<?> cls = T.class;      // Không hợp lệ
    // T value = new T();           // Không hợp lệ
    // T[] values = new T[4];       // Không hợp lệ
}
```

Với các trường hợp cần tạo đối tượng, truyền `Supplier<T>` hoặc factory. Với trường hợp cần runtime class check, truyền `Class<T>` nếu `T` được đại diện bằng một class cụ thể, hoặc cơ chế `Type` có thông tin rộng hơn cho loại dữ liệu generic phức tạp.

### 12.4. Không suy ra an toàn generic từ một cast unchecked

```java
Object input = java.util.List.of(42);

// Chỉ minh họa hành vi chưa an toàn, có unchecked cast:
java.util.List<String> strings = (java.util.List<String>) input;
// String s = strings.get(0); // Có thể ném ClassCastException
```

Cast có thể thành công vì runtime chỉ kiểm tra được `List`, không kiểm tra type argument `String`. Khi cần một `String` từ collection đang chứa `Integer`, thao tác đọc lại thất bại.

Đó là lý do compiler cảnh báo **unchecked**: có một phần của cam kết generic mà runtime không chứng minh được.

## 13. Những nhầm lẫn thường gặp

**“Reifiable nghĩa là runtime lưu toàn bộ generic type argument.”** Sai. `List<?>` reifiable nhưng không lưu và xác nhận một loại phần tử cụ thể.

**“`List<String>` không reifiable nghĩa là compiler không thể kiểm tra an toàn kiểu.”** Sai. `List<String>` vẫn được compiler kiểm tra chặt chẽ; hạn chế nằm ở biểu diễn và kiểm tra runtime của kiểu parameterized đầy đủ.

**“Chỉ class không generic mới reifiable.”** Sai. Raw types, primitive types, mảng có element type reifiable và kiểu như `List<?>` hoặc `Map<?, ?>` đều có thể reifiable.

**“`List<? extends Number>` reifiable vì vẫn kiểm tra được `List`.”** Sai theo định nghĩa: đối số wildcard bị bound, không phải unbounded `?`; bản thân kiểu parameterized đầy đủ không thuộc nhóm reifiable.

**“Mọi array có generic đều bị cấm.”** Sai. `new List<?>[2]` hợp lệ; bị cấm là tạo trực tiếp array với component type non-reifiable như `List<String>`.

**“Reflection đọc được `List<String>` nên mọi instance đều biết mình là `List<String>`.”** Sai. Reflection có thể đọc metadata **khai báo** dù runtime class của một `ArrayList` không chứa đối số kiểu riêng cho từng instance.

**“`List<?>` tương đương raw `List`.”** Sai. Cả hai reifiable nhưng kiểm tra generic khi **ghi dữ liệu** rất khác nhau: `List<?>` giữ cơ chế capture type, raw type bỏ một phần bảo đảm.

**“Chỉ cần `Class<T>` thì có thể kiểm tra `List<String>` như một class runtime riêng.”** Sai. `Class<T>` phù hợp với một class cụ thể như `String`; một kiểu generic lồng với type arguments phải được mô tả và xác thực bằng cơ chế khác nếu cần.

## 14. Bài tập tự kiểm tra

**Câu 1.** `String[]` có reifiable không? Vì sao?

Có. `String` có runtime class rõ ràng và array mang runtime component type `String`.

**Câu 2.** `Map<?, ?>` có reifiable không?

Có. Cả hai đối số kiểu đều là wildcard không bound.

**Câu 3.** `List<? extends Number>` có reifiable không?

Không. Đây không phải `List<?>`; wildcard có upper bound cụ thể.

**Câu 4.** `List<?>[]` có thể tạo bằng `new List<?>[3]` không?

Có. Component type `List<?>` reifiable nên tạo array dạng này hợp lệ.

**Câu 5.** Vì sao `List<String>[]` không thể được tạo trực tiếp bằng `new List<String>[3]`?

Vì component type `List<String>` non-reifiable. Array runtime không thể kiểm tra đầy đủ type argument `String` khi lưu phần tử.

**Câu 6.** Một trường khai báo `List<String>` được reflection báo `ParameterizedType`. Điều đó có biến `List<String>` thành reifiable không?

Không. Metadata về khai báo khác với khả năng runtime kiểm tra đầy đủ kiểu của một đối tượng bất kỳ.

**Câu 7.** `Object x = List.of(42); x instanceof List<?>;` có kết quả gì?

`true`, bởi `x` là một `List`. Phép kiểm tra không yêu cầu phần tử là `String` hay bất kỳ type argument cụ thể nào.

## 15. Cách ghi nhớ và áp dụng

Hãy tự hỏi hai câu trước khi dùng một kiểu generic trong biểu thức runtime:

1. **Runtime cần kiểm tra gì?** Chỉ là đối tượng thuộc `List`, thuộc class `String`, hay còn cần chứng minh từng type argument cụ thể?
2. **Thông tin ấy nằm ở runtime object/class hay chỉ ở khai báo generic?** `String[]` có runtime component class; `List<String>` thông thường không có runtime class riêng kèm tham số `String`.

Khi chỉ cần hỏi đối tượng có phải một collection không, dùng `instanceof List<?>`. Khi cần xác nhận mỗi phần tử đúng kiểu, hãy kiểm tra từng phần tử hoặc giữ dữ liệu trong một API có hợp đồng generic an toàn. Khi cần tạo array, chọn element type reifiable hoặc cân nhắc dùng collection thay thế. Khi cần reflection, phân biệt `Class` của runtime và `Type` metadata mô tả khai báo generic.

**Kết luận:** một **reifiable type** có đủ biểu diễn tại runtime để thực hiện phép kiểm tra kiểu tương ứng. Trong Java, kiểu nguyên thủy, kiểu không generic, raw type, tham số hóa chỉ bằng wildcard không bound và array của kiểu reifiable là các nhóm quan trọng. Các kiểu như `List<String>`, `List<? extends Number>` và type variable `T` là non-reifiable. Hiểu sự khác biệt giúp giải thích chính xác các quy tắc của `instanceof`, generic array creation, type erasure, unchecked cast và reflection.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="runtime-types-002">Phân loại kiểu thông thường, raw type, wildcard không giới hạn và kiểu mảng</a>

<details>
<summary>Xem chi tiết</summary>

# Phân loại kiểu thông thường, raw type, wildcard không giới hạn và kiểu mảng ở runtime

## 1. Vì sao cần phân loại kiểu khi làm việc với Java Generics?

Java Generics kiểm tra quan hệ kiểu rất chặt ở **thời điểm biên dịch**, nhưng nhiều thông tin về tham số kiểu bị xóa (*type erasure*) khi mã nguồn chuyển thành bytecode. Vì vậy câu hỏi “Java có biết kiểu này lúc chạy không?” phải được trả lời chính xác theo **từng loại kiểu**.

Ví dụ:

```java
List<String> names = List.of("Lan", "Minh");
Object object = names;
```

Ở source, `names` có kiểu `List<String>`. Ở runtime, đối tượng `List` vẫn có class cụ thể của nó, nhưng không có một class riêng cho `List<String>` bên cạnh `List<Integer>` chỉ vì chúng dùng type argument khác nhau.

Trái lại, một mảng `String[]` vẫn giữ thành phần runtime là `String`, nên JVM có thể ngăn việc ghi `Integer` vào mảng này qua một alias `Object[]`.

Khái niệm trung tâm của bài là **reifiable type** — kiểu mà biểu diễn runtime còn đủ để dùng trong những thao tác yêu cầu kiểu có thể xác định ở runtime, theo quy tắc của Java Language Specification (JLS). Bài tập trung vào bốn nhóm trong tên đề:

1. **Kiểu thông thường**: kiểu primitive và các class/interface không generic như `int`, `String`, `Runnable`.
2. **Raw type**: tên generic được dùng không có type argument, như raw `List` hoặc `Map`.
3. **Parameterized type chỉ có wildcard không giới hạn**: ví dụ `List<?>`, `Map<?, ?>`.
4. **Kiểu mảng**: `String[]`, `int[]`, `List<?>[]`, `List<String>[]`, `T[]` và tác động của kiểu thành phần.

Sau bài này bạn sẽ biết phân loại các kiểu thành **reifiable** hay **non-reifiable**, hiểu sự khác nhau giữa `instanceof`, `Class`, Reflection `Type` và kiểm tra phần tử mảng, đồng thời giải thích được các lỗi `generic array creation` và unchecked cast.

## 2. Phải phân biệt ba mức thông tin kiểu

Một biến Java có thể liên quan đến ba lớp thông tin khác nhau:

| Mức | Ví dụ | Điều nó biểu đạt |
| --- | --- | --- |
| **Kiểu tại source (compile time)** | `List<String>` | Compiler kiểm tra các lời gọi `add`, `get`, phép gán và quan hệ generic dựa trên `String`. |
| **Kiểu/class runtime** | Đối tượng thuộc một implementation của `List` | JVM xác định class thật của đối tượng, dùng cho dispatch, `instanceof`, reflection, cast; type argument `String` không tự trở thành một class specialization. |
| **Metadata khai báo generic** | Field khai báo `List<String>` hoặc `T[]` | Class file có thể giữ Signature attribute; Reflection đọc lại *khai báo* này qua `getGenericType()`, không phải chuyên biệt hóa của từng instance. |

Ba mức đó không mâu thuẫn nhau:

```java
List<String> a = new ArrayList<>();
List<Integer> b = new ArrayList<>();
```

`a` và `b` khác kiểu tại source nhưng `a.getClass() == b.getClass()` là `true`: hai đối tượng đều được tạo từ class `ArrayList` thông thường. Thông tin generic của **khai báo** vẫn có thể hiện diện ở field/method/class qua metadata, song không chứng minh runtime biết phần tử của từng `ArrayList` thuộc kiểu nào.

### 2.1. Reifiable không có nghĩa “không dùng generic”

`List<?>` là một kiểu generic với wildcard nhưng **reifiable**. Ngược lại `List<String>` là một kiểu tham số hóa có type argument cụ thể nhưng **không reifiable**.

Tính reifiable nói về **dạng thông tin runtime được Java cho phép kiểm tra**, không phải “mã này có generic hay không”, “collection đang chứa phần tử gì”, hoặc “có thể thêm mọi đối tượng hay không”.

### 2.2. Reifiable cũng không đồng nghĩa kiểm tra hết dữ liệu bên trong

Một biểu thức `object instanceof List<?>` có thể xác minh đối tượng là một `List` runtime. Nó **không** duyệt từng phần tử để xác minh cùng kiểu, và không bảo đảm collection hoàn toàn không chứa `null`.

Dù `List<?>` reifiable, Java vẫn không biết `?` đại diện cho `String`, `Integer` hay một loại tham chiếu khác ở một instance cụ thể. Vì vậy reifiable là điều kiện về **kiểu được kiểm tra**; muốn bảo đảm dữ liệu đầu vào thì còn cần hợp đồng và kiểm tra phần tử.

## 3. Danh sách các kiểu reifiable theo Java

Các nhóm reifiable cơ bản của JLS gồm:

- Kiểu class hoặc interface **không generic**, ví dụ `String`, `Number`, `Runnable`.
- Parameterized type mà **mọi** type argument là wildcard không giới hạn `?`, ví dụ `List<?>`, `Map<?, ?>`.
- **Raw type** của class/interface generic, ví dụ `List`, `Map`.
- **Primitive type**, ví dụ `int`, `boolean`, `double`.
- **Kiểu mảng** có kiểu thành phần reifiable, ví dụ `String[]`, `int[]`, `List<?>[]`, `List[][]`.
- Các dạng kiểu lồng nhau đáp ứng điều kiện reifiable của từng phần trong tên kiểu theo các quy tắc JLS.

Ngoài ra `Object`, `String[]` hay `List<?>` có thể là reifiable; type variable `T`, `List<String>` và `List<? extends Number>` **không** thuộc nhóm này.

### 3.1. Bảng phân loại cụ thể

| Kiểu viết trong Java | Reifiable? | Giải thích ngắn |
| --- | --- | --- |
| `int` | Có | Primitive có kiểu runtime xác định. |
| `boolean`, `double` | Có | Primitive. |
| `String`, `Integer`, `Object` | Có | Class thông thường, không có type parameter tại chính kiểu này. |
| `Runnable` | Có | Interface không generic. |
| `String[]`, `int[]` | Có | Thành phần reifiable, JVM biết component type. |
| `List` | Có | Raw type của `List<E>`; hợp lệ về khả năng xác định runtime nhưng không an toàn như `List<T>` có tham số kiểu. |
| `Map` | Có | Raw type. |
| `List<?>` | Có | Mọi type argument là wildcard **không giới hạn**. |
| `Map<?, ?>` | Có | Hai type argument đều là `?`. |
| `List<?>[]`, `Map<?, ?>[]` | Có | Thành phần là kiểu reifiable. |
| `List[]` | Có | Mảng của raw type `List`; có thể sinh cảnh báo `rawtypes`. |
| `List<String>` | Không | Tham số `String` bị xóa, không tạo một specialization runtime riêng. |
| `Map<String, Integer>` | Không | Các type argument cụ thể không reifiable. |
| `List<? extends Number>` | Không | Wildcard **có giới hạn**, không thuộc nhóm “mọi argument đều là wildcard không giới hạn”. |
| `List<? super Integer>` | Không | Wildcard có giới hạn dưới. |
| `T` trong `<T>` | Không | Type variable không phải một kiểu reifiable. |
| `T[]` | Không | Kiểu thành phần `T` không reifiable, dù erasure của nó có thể là `Object`. |
| `List<String>[]` | Không | Thành phần `List<String>` không reifiable. |
| `List<?>[][]` | Có | Mỗi tầng mảng có thành phần reifiable. |
| `List<String>[][]` | Không | Thành phần cuối `List<String>` không reifiable. |

Với `T extends Number`, `T` và `T[]` **vẫn không reifiable**; erasure của `T` có thể thành `Number` và erasure của `T[]` thành `Number[]`, nhưng erasure không biến một type variable thành một kiểu bạn có thể dùng trực tiếp với `new T[5]`.

### 3.2. Một wildcard giới hạn làm thay đổi kết luận

Đối chiếu:

```java
List<?> a;                      // Reifiable
List<? extends Number> b;       // Non-reifiable
List<? super Integer> c;        // Non-reifiable
Map<?, ?> d;                    // Reifiable
Map<String, ?> e;               // Non-reifiable
```

`? extends Number` có nghĩa “một subtype chưa biết của `Number`”; đây là ràng buộc compile time có ý nghĩa khác với `?` không giới hạn. Việc JVM có thể thấy `List` hoặc `Map` sau erasure không đủ để giữ toàn bộ thông tin type argument có giới hạn ở mỗi object.

## 4. Nhóm A — Kiểu class, interface và primitive thông thường

Những kiểu như:

```java
String text = "Java";
int count = 10;
Runnable action = () -> {};
```

có biểu diễn runtime tương ứng. Ví dụ:

```java
Object x = "Java";

System.out.println(x instanceof String);  // true
System.out.println(String.class.getName()); // java.lang.String
```

Một `String` thực sự là một instance class `String`, và toán tử `instanceof` kiểm tra kiểu runtime phù hợp.

Với primitive, không thể viết `x instanceof int`; `instanceof` hoạt động với **reference types** trong các dạng hợp lệ của toán tử, và primitive không là object để kiểm tra như vậy. Tuy nhiên `int.class` là class literal hợp lệ, trả về một đối tượng `Class` đại diện cho primitive `int`.

```java
System.out.println(int.class.isPrimitive()); // true
```

Cần phân biệt `int` với wrapper `Integer`: `Integer` là một class tham chiếu, có thể kiểm tra qua `instanceof Integer`, trong khi `int` là primitive. Boxing/unboxing có thể chuyển đổi giữa hai dạng trong những ngữ cảnh Java cho phép, nhưng chúng không phải một kiểu duy nhất.

### 4.1. Kiểu thông thường vẫn có thể triển khai generic API

`String` không có type parameter của chính nó, nhưng nó triển khai các interface như `Comparable<String>`. Điều đó không khiến `String` trở thành một class generic không reifiable.

Một class/interface **không generic** có thể sử dụng generic trong method hoặc implement generic interface. Phân loại reifiable cần nhìn vào **kiểu đang được hỏi**, không suy luận rằng “đã đụng Generics thì toàn bộ class không reifiable”.

## 5. Nhóm B — Raw type

Raw type xuất hiện khi bỏ toàn bộ type argument của một khai báo generic:

```java
List rawList;
Map rawMap;
```

Cả raw `List` và raw `Map` là reifiable. Điều này giải thích tại sao các thao tác liên quan runtime có thể dùng dạng raw:

```java
Object value = List.of("Java");

boolean isList = value instanceof List;    // Hợp lệ
Class<?> type = List.class;                 // Hợp lệ
```

Nhưng **reifiable không chứng minh type safety** khi dùng API generic. Raw type có thể gây cảnh báo `[rawtypes]` khi khai báo và `[unchecked]` khi gọi member bằng đối số mà compiler không thể kiểm tra.

```java
List raw = new ArrayList();
raw.add("Java");
raw.add(99); // Unchecked call có thể xảy ra
```

Khi cần nhận một danh sách bất kỳ mà chỉ đọc dữ liệu, `List<?>` thường tốt hơn:

```java
static int count(List<?> values) {
    return values.size();
}
```

`List<?>` giữ ràng buộc generic, tránh quyền ghi tùy tiện mà raw type vô tình cho phép.

### 5.1. Raw type không phải `List<Object>`

`List<Object>` là parameterized type có argument cụ thể `Object`, **không reifiable**. `List<?>` và raw `List` là reifiable nhưng có quy tắc compile time khác nhau.

```java
List<String> names = List.of("Lan");
List<?> unknown = names;             // Hợp lệ
// List<Object> objects = names;      // Không hợp lệ: generic bất biến
```

Khi viết API, chọn `List<?>` vì muốn đọc phần tử chưa biết kiểu; chọn `List<Object>` khi thật sự cần một list có thể chứa `Object` và có quyền ghi tương ứng; tránh raw `List` trong code mới trừ tương thích legacy có lý do.

### 5.2. Runtime class không phân biệt raw và parameterized instances

```java
List<String> texts = new ArrayList<>();
List<Integer> numbers = new ArrayList<>();

boolean sameClass = texts.getClass() == numbers.getClass(); // true
```

Một `ArrayList` không mang class runtime riêng cho `String` và `Integer`; generic type argument không được chuyên biệt hóa thành hai implementation class khác nhau.

`List<?>` có thể được kiểm tra runtime như một `List`, nhưng điều đó không xác định type argument nguồn. Reflection trên **khai báo** `List<?> field` có thể ghi nhận dấu `?`, điều này khác với việc một object tự lưu “tôi là List<?>”.

## 6. Nhóm C — Wildcard không giới hạn `?`

`List<?>` là một parameterized type với argument wildcard không giới hạn. Đây là một trong những ví dụ quan trọng vì nó vừa reifiable vừa giữ được nhiều quy tắc kiểm tra generics.

```java
Object value = List.of("A", 17);

if (value instanceof List<?> items) {
    System.out.println(items.size());
}
```

`instanceof List<?>` hợp lệ; pattern variable `items` được xem là một `List<?>`. Bạn có thể:

- Gọi `size`, `isEmpty`, duyệt các phần tử dưới dạng `Object`.
- Đọc phần tử: `Object element = items.get(0)`.
- Không được `items.add("text")` hay `items.add(17)`, vì compiler không biết kiểu phần tử cụ thể của danh sách được capture.
- Có thể thêm `null` theo quy tắc kiểu trong một list mutable; nhưng list thực tế có thể không hỗ trợ thêm hoặc không chấp nhận null. Ví dụ `List.of(...)` là unmodifiable.

### 6.1. Wildcard không giới hạn và `instanceof`

Cách kiểm tra từ một `Object`:

```java
Object incoming = List.of("a", "b");

if (incoming instanceof List<?> list) {
    for (Object item : list) {
        System.out.println(item);
    }
}
```

Bạn không thể dùng `incoming instanceof List<String>` trong ngữ cảnh này để kiểm tra tất cả phần tử là `String`. Với biến `Object` và Java 17, compiler từ chối phép kiểm tra kiểu tham số hóa đó vì runtime không thể xác minh argument `String`.

```java
// Với biến có kiểu Object: lỗi biên dịch trên Java 17.
// if (incoming instanceof List<String>) { ... }
```

Chi tiết cú pháp `instanceof` với một số kiểu tham số hóa đã được Java điều chỉnh qua các phiên bản ngôn ngữ, đặc biệt với quan hệ kiểu đã chứng minh được ở compile time. Điều này **không làm cho JVM bắt đầu kiểm tra type argument của một `List` runtime**. Khi cần kiểm tra dữ liệu nhận từ nguồn chưa tin cậy, `instanceof List<?>` rồi xác minh từng phần tử.

Ví dụ một hàm chấp nhận duy nhất danh sách chuỗi:

```java
static List<String> checkStrings(Object incoming) {
    if (!(incoming instanceof List<?> rawValues)) {
        throw new IllegalArgumentException("Expected a List");
    }

    List<String> result = new ArrayList<>();
    for (Object value : rawValues) {
        if (!(value instanceof String text)) {
            throw new IllegalArgumentException("Non-String element: " + value);
        }
        result.add(text);
    }
    return List.copyOf(result);
}
```

Đây là **đoạn method minh họa**, cần đặt trong một class và import `java.util.List`, `java.util.ArrayList`. Hàm tạo bản sao đã kiểm tra từng phần tử; `List.copyOf` từ chối null và trả list unmodifiable. Nếu contract cho phép null hoặc yêu cầu mutable, phải chọn chiến lược copy thích hợp.

### 6.2. `Class<?>` cũng là một kiểu reifiable

```java
Class<?> runtimeClass = incoming.getClass();
```

`Class<?>` chứa một wildcard không giới hạn, nên bản thân kiểu `Class<?>` reifiable. Tuy nhiên một `Class<?>` chỉ cho bạn biết runtime class của object — ví dụ implementation `List` — mà không tự suy ra type argument `String` của một biến source đã bị erase.

Một `Class<String>` có type argument cụ thể, nên không phải kiểu reifiable theo quy tắc tổng quát cho parameterized types. Dù có thể viết `String.class` và gán cho `Class<String>` nhờ chữ ký API của class literal, đừng kết luận điều đó tạo nên một runtime class riêng đại diện cho *tham số hóa của `Class`*.

## 7. Nhóm D — Mảng: kiểu thành phần tồn tại ở runtime

Java mảng là object và JVM giữ một **runtime component type** xác định. Ví dụ:

```java
String[] names = new String[2];

System.out.println(names.getClass().isArray());               // true
System.out.println(names.getClass().getComponentType());      // class java.lang.String
System.out.println(names instanceof String[]);                // true
```

Mảng còn có đặc tính **covariance**:

```java
String[] strings = new String[1];
Object[] objects = strings;     // Hợp lệ
```

Dù tham chiếu `objects` có kiểu tĩnh là `Object[]`, đối tượng thực vẫn là `String[]`. Nếu ghi sai kiểu:

```java
objects[0] = 42; // ArrayStoreException lúc chạy
```

JVM có đủ thông tin runtime để ngăn `Integer` bị ghi vào mảng có component type `String`.

### 7.1. Mảng của kiểu reifiable

| Cách tạo | Hợp lệ? | Runtime component type |
| --- | --- | --- |
| `new int[3]` | Có | `int` |
| `new String[3]` | Có | `String` |
| `new Object[3]` | Có | `Object` |
| `new List[3]` | Có, raw-type warning có thể xuất hiện | `List` |
| `new List<?>[3]` | Có | `List` |
| `new Map<?, ?>[3]` | Có | `Map` |
| `new List<String>[3]` | Không | Component `List<String>` không reifiable. |
| `new List<? extends Number>[3]` | Không | Component không reifiable do wildcard có giới hạn. |
| `new T[3]` trong method `<T>` | Không | `T` là type variable không reifiable. |

Từ góc nhìn runtime, mảng được tạo bằng `new List<?>[3]` vẫn là mảng có component class `List`. Điều này giải thích tại sao một `List<?>[]` có thể chứa một `List<String>` và một `List<Integer>` ở hai phần tử khác nhau mà không có kiểm tra wildcard argument riêng tại runtime.

### 7.2. Mảng wildcard và raw array có cùng runtime class

```java
List<?>[] wildcardArray = new List<?>[2];
List[] rawArray = new List[2];

boolean sameRuntimeClass =
        wildcardArray.getClass() == rawArray.getClass(); // true
```

Ở source, `List<?>[]` giữ nhiều ràng buộc generic hơn raw `List[]`. Nhưng kiểu phần tử runtime của cả hai là `List`, nên chúng dùng cùng array class. Có thể kiểm tra bằng:

```java
wildcardArray.getClass().getComponentType() == List.class
```

Mỗi ô của `wildcardArray` chỉ được JVM kiểm tra xem object được ghi có là một `List` phù hợp hay không. Nó không có cách xác minh type argument của phần tử list như `String` hay `Integer`.

### 7.3. Vì sao `new List<String>[2]` bị cấm?

Giả sử Java cho phép một mảng như thế:

```java
// Đây là giả định không hợp lệ ở Java:
List<String>[] strings = new List<String>[2];
Object[] objects = strings;
objects[0] = List.of(42);
String first = strings[0].get(0);
```

Do generic argument `String` không được kiểm tra ở runtime, việc ghi `List<Integer>` vào array class chỉ có component raw `List` sẽ không bị array store check chặn theo generic argument. Sau đó đoạn đọc `String` có thể ném `ClassCastException`.

Vì vậy Java **cấm trực tiếp cấp phát** mảng có component là `List<String>` (một loại không reifiable). Cấm tạo không đồng nghĩa kiểu khai báo hoàn toàn bị cấm:

```java
List<String>[] reference; // Khai báo biến được phép
```

Nhưng `new List<String>[2]` vẫn là lỗi `generic array creation`. Các phép cast unchecked có thể khiến giá trị kiểu này xuất hiện qua API cũ, và cần tránh giả định chúng an toàn.

### 7.4. `T[]` với type variable vẫn là trường hợp đặc biệt

Trong một class generic:

```java
class Buffer<T> {
    T[] data;

    // T[] create(int size) {
    //     return new T[size]; // Lỗi: generic array creation
    // }
}
```

Khi `T` không bound, erasure của nó là `Object`; với `<T extends Number>`, erasure là `Number`. Nhưng class không biết runtime component class cụ thể mà caller muốn, nên compiler không cho cấp phát `new T[size]`.

Một cách nhận mảng đúng type là **factory**:

```java
static <T> T[] create(int size, java.util.function.IntFunction<T[]> factory) {
    return factory.apply(size);
}

// String[] names = create(3, String[]::new);
```

Đoạn trên là method minh họa cần đặt trong một class. Bên gọi cung cấp mảng có component thật `String`. Cách khác dùng `Array.newInstance` với type token `Class<T>`, nhưng cast sang `T[]` cần được đóng gói cẩn thận và có thể sinh unchecked warning.

### 7.5. Reifiable array vẫn có thể gây ArrayStoreException

Tính reifiable giúp array runtime biết component class; nó **không chứng minh mọi lệnh ghi vào alias đều an toàn**. Cú pháp `Object[] alias = new String[1]` cho phép alias rộng hơn, nhưng runtime có store check.

Đồng thời `List<?>[]` reifiable không ngăn bạn tạo alias rồi ghi một **List có kiểu phần tử khác**: tất cả đều là `List` theo component runtime. Nếu thiết kế cần một tập các `List<String>` thực sự an toàn, cân nhắc `List<List<String>>` hoặc kiểm tra element/nested element khi xây dữ liệu.

## 8. Reflection: kiểu sau erasure và generic declaration

`java.lang.reflect.Field` có hai API dễ bị nhầm:

```java
field.getType()        // Class<?> của kiểu field ở runtime, sau erasure
field.getGenericType() // java.lang.reflect.Type từ khai báo generic, nếu có
```

Nếu một field khai báo `List<String>`, `getType()` cho biết raw `List`, còn `getGenericType()` có thể trả một `ParameterizedType` đại diện cho `List<String>` từ Signature metadata. Điều này mô tả **kiểu đã khai báo cho field**, không xác minh rằng các phần tử của một object được gán vào field đúng kiểu.

### 8.1. Ví dụ đối chiếu các field

```java
static final class Examples<T> {
    String plain;
    List raw;
    List<?> unknown;
    List<String> names;
    String[] plainArray;
    List<?>[] wildcardArray;
    List<String>[] parameterizedArray;
    T element;
    T[] variableArray;
}
```

Đây là một lớp lồng được đưa vào chương trình hoàn chỉnh bên dưới. Khi duyệt từng field bằng `getType()` và `getGenericType()`:

| Khai báo field | `getType()` biểu diễn | `getGenericType()` biểu diễn |
| --- | --- | --- |
| `String plain` | `String` | `String` |
| `List raw` | `List` | `List` |
| `List<?> unknown` | `List` | `List<?>` |
| `List<String> names` | `List` | `List<String>` |
| `String[] plainArray` | `String[]` | `String[]` |
| `List<?>[] wildcardArray` | `List[]` | `List<?>[]` |
| `List<String>[] parameterizedArray` | `List[]` | `List<String>[]` |
| `T element` | `Object` nếu `T` không bound | `T` |
| `T[] variableArray` | `Object[]` nếu `T` không bound | `T[]` |

Với một method generic, tương tự có `getParameterTypes()`/`getGenericParameterTypes()` và `getReturnType()`/`getGenericReturnType()`. Đó là thông tin hữu ích cho framework phân tích API nhưng không thay thế việc xác minh dữ liệu runtime.

### 8.2. `Type` có nhiều dạng

Một `Type` từ `getGenericType()` có thể là:

- `Class<?>` cho kiểu thông thường hoặc raw/array class thích hợp.
- `ParameterizedType` cho khai báo như `List<String>` hoặc `List<?>`.
- `TypeVariable<?>` cho `T`.
- `GenericArrayType` cho các mảng chứa thành phần generic không thể biểu diễn trực tiếp như một `Class` tương ứng, ví dụ `T[]` hoặc `List<String>[]`.
- `WildcardType` khi đi vào type argument wildcard của một `ParameterizedType`.

Đừng dùng cách ép `(Class<?>) field.getGenericType()` cho mọi field: nó có thể ném `ClassCastException` khi thực tế là `ParameterizedType` hoặc `GenericArrayType`. Khi làm framework, kiểm tra `instanceof` từng nhánh của `Type` và xem bound/type arguments với API tương ứng.

### 8.3. Metadata không biến thành validation runtime

Giả sử field khai báo `List<String> names`, Reflection trả `ParameterizedType` có argument `String`, nhưng một raw alias có thể làm nhiễm phần tử sai kiểu vào list được gán. Khi đó metadata vẫn nói đúng rằng **field đã được khai báo** là `List<String>` — nó không kiểm tra mọi phần tử hiện có.

Nếu cần nhận dữ liệu từ JSON, JDBC hoặc legacy API, bạn phải dùng parser, schema hoặc kiểm tra từng phần tử đúng với cấu trúc generic mong đợi. Chỉ đọc `getGenericType()` không làm dữ liệu tự type-safe.

## 9. Chương trình Java 17 hoàn chỉnh để tự phân loại

Tệp **`ReifiableTypesDemo.java`** sau kết hợp `instanceof List<?>`, mảng wildcard/raw, runtime component check và Reflection. Bản thân nó cố ý chứa hai chỗ raw type để so sánh, nên khi bật `-Xlint:rawtypes` sẽ có cảnh báo; các cảnh báo đó là một phần của minh họa.

```java
import java.lang.reflect.Field;
import java.util.List;

public class ReifiableTypesDemo {
    static final class Examples<T> {
        String plain;
        List raw;
        List<?> unknown;
        List<String> names;
        String[] plainArray;
        List<?>[] wildcardArray;
        List<String>[] parameterizedArray;
        T element;
        T[] variableArray;
    }

    public static void main(String[] args) throws Exception {
        Object mixed = List.of("Java", 17);
        System.out.println("instanceof-List<?>=" + (mixed instanceof List<?>));

        List<?>[] wildcardArray = new List<?>[2];
        wildcardArray[0] = List.of("Java");
        wildcardArray[1] = List.of(17);
        List[] rawArray = new List[2];
        System.out.println("same-array-class="
                + (wildcardArray.getClass() == rawArray.getClass()));
        System.out.println("array-component="
                + wildcardArray.getClass().getComponentType().getTypeName());
        System.out.println("elements="
                + wildcardArray[0].get(0) + "," + wildcardArray[1].get(0));

        Object[] alias = new String[1];
        try {
            alias[0] = 99;
        } catch (ArrayStoreException e) {
            System.out.println("store-check=" + e.getClass().getSimpleName());
        }

        String[] fields = {"plain", "raw", "unknown", "names", "plainArray",
                "wildcardArray", "parameterizedArray", "element", "variableArray"};
        for (String name : fields) {
            Field field = Examples.class.getDeclaredField(name);
            System.out.println(name + ": raw=" + field.getType().getTypeName()
                    + "; generic=" + field.getGenericType().getTypeName());
        }
    }
}
```

Biên dịch bằng Java 17 hoặc JDK hỗ trợ `--release 17`:

```bash
javac --release 17 -Xlint:rawtypes ReifiableTypesDemo.java
java ReifiableTypesDemo
```

Compiler phát **3 cảnh báo `rawtypes`** với source trên: một ở field `List raw`, hai ở dòng `List[] rawArray = new List[2]` (tại cả khai báo và biểu thức tạo mảng). Chúng là cảnh báo, không phải lỗi biên dịch.

Stdout chính xác của chương trình:

```text
instanceof-List<?>=true
same-array-class=true
array-component=java.util.List
elements=Java,17
store-check=ArrayStoreException
plain: raw=java.lang.String; generic=java.lang.String
raw: raw=java.util.List; generic=java.util.List
unknown: raw=java.util.List; generic=java.util.List<?>
names: raw=java.util.List; generic=java.util.List<java.lang.String>
plainArray: raw=java.lang.String[]; generic=java.lang.String[]
wildcardArray: raw=java.util.List[]; generic=java.util.List<?>[]
parameterizedArray: raw=java.util.List[]; generic=java.util.List<java.lang.String>[]
element: raw=java.lang.Object; generic=T
variableArray: raw=java.lang.Object[]; generic=T[]
```

Đọc ba nhóm kết quả:

- `instanceof-List<?>=true` chỉ xác nhận `mixed` là một `List`, không kiểm tra đồng nhất kiểu phần tử.
- `same-array-class=true`, `array-component=java.util.List` cho thấy cả mảng raw và mảng wildcard có thành phần runtime là `List`.
- `getType()` cho các field generic là kiểu đã xóa (`List`, `Object`, `Object[]`), trong khi `getGenericType()` hiển thị khai báo có `?`, `String`, `T` từ metadata.

Dòng `store-check=ArrayStoreException` chứng minh một `String[]` có component runtime `String` dù alias của nó là `Object[]`: JVM kiểm tra phần tử được ghi vào.

## 10. Các lỗi biên dịch dự kiến khi dùng kiểu không reifiable

Đây là **đoạn mã cố tình sai**, chỉ dùng để quan sát diagnostic. Không đặt chung với chương trình chạy thành công phía trên.

Tệp **`InvalidReifiable.java`**:

```java
import java.util.List;

class InvalidReifiable {
    static <T> void run(Object x) {
        List<String>[] a = new List<String>[2];
        T[] b = new T[2];
        if (x instanceof List<String>) {
            System.out.println(x);
        }
    }
}
```

Biên dịch:

```bash
javac --release 17 InvalidReifiable.java
```

Compiler từ chối ba chỗ:

```text
error: generic array creation
    List<String>[] a = new List<String>[2];

error: generic array creation
    T[] b = new T[2];

error: Object cannot be safely cast to List<String>
    if (x instanceof List<String>) {
```

Giải thích:

1. `List<String>` không reifiable nên không được làm component cho biểu thức `new List<String>[2]`.
2. `T` là type variable không reifiable nên không cấp phát `new T[2]`.
3. Từ một giá trị tĩnh kiểu `Object`, JVM không có cách kiểm tra đầy đủ type argument `String` của một `List` bằng `instanceof List<String>`.

Đừng “sửa” thành unchecked cast `List<String>[] a = (List<String>[]) new List<?>[2]` mà không có invariant chứng minh. Cast ấy không làm JVM biết `String` trong component type và có thể dẫn tới heap pollution.

## 11. Cách sử dụng kiểu reifiable trong một số API

### 11.1. Với `instanceof`

Dùng kiểm tra kiểu có thể xác định ở runtime:

```java
if (value instanceof String text) {
    System.out.println(text.length());
}

if (value instanceof List<?> elements) {
    System.out.println(elements.size());
}
```

Với array:

```java
if (value instanceof String[] words) {
    System.out.println(words.length);
}
```

Các ví dụ là **mảnh code để đặt trong method**, với `value` có kiểu tham chiếu thích hợp và import `java.util.List`. Nếu muốn phân loại chi tiết hơn một `List<?>`, cần kiểm tra phần tử hoặc dùng metadata/protocol từ nguồn dữ liệu.

### 11.2. Với `Class` object và reflection

Một `Class<?>` có thể mô tả `String.class`, `List.class` hoặc `String[].class`. Nhưng không có class literal `List<String>.class` đại diện cho một specialization runtime của list.

```java
Class<?> stringClass = String.class;
Class<?> listClass = List.class;
Class<?> arrayClass = String[].class;
```

Nếu một framework cần giữ **cả cây kiểu** `List<String>` hoặc `Map<String,List<Integer>>`, nó cần một `Type`, `ParameterizedType`, hoặc cơ chế *type token* dựa trên khai báo generic. Việc đó phục vụ mô tả/giải mã kiểu; bản thân `Class` object của một `ArrayList` đã tạo vẫn không tự ghi nhớ tham số kiểu của biến source.

### 11.3. Với mảng và generic varargs

Varargs trong bytecode dựa trên mảng. Một method như:

```java
static <T> void examine(List<T>... lists) {
    // ...
}
```

có component generic không reifiable nên có thể xuất hiện warning về heap pollution hoặc unchecked array creation. Không nên gắn `@SafeVarargs` chỉ để im compiler: annotation là lời cam kết về cách method sử dụng mảng, không làm kiểu `List<T>` reifiable.

Khi chỉ cần giữ nhiều danh sách cùng loại, cân nhắc collection lồng nhau:

```java
List<List<String>> groups = new ArrayList<>();
groups.add(List.of("a", "b"));
```

Điều này loại bỏ nhu cầu tạo một `List<String>[]` trực tiếp. Nhưng một `List<List<String>>` vẫn không phải kiểu reifiable khi xét toàn bộ type arguments; tính an toàn của nó đến từ kiểm tra generic ở compile time và việc kiểm soát dữ liệu, không phải có `List<List<String>>` class riêng ở runtime.

## 12. Các ngộ nhận và lỗi thường gặp

### 12.1. “Raw type reifiable nghĩa là raw type tốt cho type safety”

Sai. Raw `List` reifiable về **hình dạng runtime** nhưng bỏ type argument khỏi kiểm tra compile time, cho phép unchecked operation. Chọn `List<?>` khi cần nhận list không biết kiểu phần tử và không cần thêm phần tử tùy ý.

### 12.2. “`List<?>` reifiable nghĩa là biết kiểu các phần tử”

Sai. `?` chính là một kiểu chưa biết. JVM nhận diện được `List` nhưng không suy ra một type argument cụ thể. Nếu cần `List<String>`, hãy kiểm tra từng phần tử rồi tạo một danh sách có hợp đồng rõ ràng.

### 12.3. “`List<String>` không reifiable nghĩa là không được khai báo biến này”

Sai. `List<String>` là nền tảng generic type safety khi biên dịch. Bạn hoàn toàn có thể khai báo `List<String>` hoặc method trả `List<String>`, chỉ không thể dùng một số thao tác runtime yêu cầu reifiable type như `new List<String>[n]` và một số dạng runtime type check.

### 12.4. “Mảng chỉ là list cũ nên JVM không biết kiểu phần tử”

Sai. Mảng có component class runtime. `String[]` bảo vệ việc ghi nhầm bằng `ArrayStoreException`. Tuy vậy mảng có component `List` không biết type argument của **từng list bên trong**.

### 12.5. “`T extends Number` làm `T` reifiable”

Sai. Bound cho compiler biết phép toán nào được phép trên `T` và quyết định erasure; nó không tạo `Class<T>` runtime mới cho type variable. `new T[n]` vẫn không hợp lệ. Dùng factory hoặc class token nếu cần cấp phát.

### 12.6. “Reflection đọc ra `List<String>` nên JVM kiểm tra element type ở runtime”

Sai. `Field.getGenericType()` đọc khai báo generic từ class metadata. Nếu dữ liệu bị ghi sai qua raw alias hoặc được nạp từ nguồn bên ngoài không kiểm tra, metadata ấy vẫn tồn tại mà các phần tử có thể sai kiểu.

### 12.7. “`List<?>[]` và `List[]` cùng class nên phải dùng raw type”

Sai. Hai kiểu có thể cùng array class runtime nhưng có **quy tắc kiểm tra source khác nhau**. Dùng `List<?>[]` diễn đạt rằng phần tử là các List không biết generic argument; raw `List[]` bỏ nhiều kiểm tra và có thể tạo warning.

### 12.8. “Erasure biến runtime `String[]` thành `Object[]`”

Sai. Erasure áp dụng cho **biểu diễn generic của chữ ký**, không tự động đổi component runtime của object array đã được tạo. Với `<T> T[] echo(T[] array)`, descriptor có thể dùng `Object[]`, song nếu bên gọi truyền `String[]`, đối tượng vẫn là một `String[]` thật.

## 13. Checklist phân loại khi đọc code hoặc sửa lỗi

Với bất kỳ kiểu nghi vấn nào, làm theo thứ tự:

1. **Xem cấu trúc kiểu:** primitive, class/interface không generic, raw type, parameterized type, type variable hay array?
2. Nếu là parameterized type, **mọi argument có đúng là `?` không giới hạn?** Nếu có, đó là trường hợp reifiable; nếu có `String`, `? extends Number` hay `? super Integer`, không.
3. Nếu là array, **phân loại component type** trước rồi lặp lại quy tắc theo từng chiều; `List<?>[]` reifiable, `List<String>[]` không.
4. Nếu là `T`, **đừng nhầm erasure với reifiability**. Bound chỉ cho biết kiểu thay thế của `T` trong erasure, không làm `T` trở thành một class runtime cụ thể.
5. Nếu cần `instanceof` hoặc cấp phát array, kiểm tra quy tắc ngôn ngữ cho biểu thức đó trước khi viết code.
6. Nếu xem Reflection, **tách `Class<?>` runtime khỏi `Type`/Signature metadata**.
7. Nếu chuyển dữ liệu generic từ nguồn bên ngoài, **kiểm tra từng phần tử** hoặc dùng parser/schema rõ ràng khi cần hợp đồng chặt hơn.
8. Nếu gặp unchecked cast, raw alias hay generic array creation, ưu tiên sửa thiết kế API và bảo toàn thông tin kiểu từ nguồn đến đích.

Bảng tra nhanh:

| Nhu cầu | Kiểu/API nên cân nhắc | Lưu ý |
| --- | --- | --- |
| Chỉ cần biết một object có phải `List` | `instanceof List<?>` | Không chứng minh phần tử là `String`. |
| Nhận mọi danh sách để đọc | `List<?>` | Tránh raw type khi không cần khả năng ghi. |
| Lưu danh sách chuỗi có kiểm tra compile time | `List<String>` | Non-reifiable không làm mất giá trị type safety. |
| Mảng chuỗi thật ở runtime | `String[]` | Store check có thể gây `ArrayStoreException` khi ghi sai. |
| Mảng các list chưa rõ type argument | `List<?>[]` | Runtime chỉ kiểm tra phần tử là `List`. |
| Cần tạo mảng `T[]` | `IntFunction<T[]>`, array factory | Bên gọi tạo mảng có component runtime mong muốn. |
| Cần đọc khai báo generic của field | `Field.getGenericType()` | Là metadata khai báo, không validate instance data. |
| Cần đọc class thực hoặc component array | `getClass()`, `getComponentType()` | Thấy erasure của generic và component class runtime. |

## 14. Câu hỏi tự kiểm tra

**Câu 1.** Trong bốn kiểu `List`, `List<?>`, `List<String>` và `List<? extends Number>`, kiểu nào reifiable?

<details>
<summary>Đáp án</summary>

Raw `List` và `List<?>` reifiable. `List<String>` không reifiable vì có argument cụ thể; `List<? extends Number>` không reifiable vì wildcard bị giới hạn.

</details>

**Câu 2.** `new List<?>[2]` và `new List<String>[2]` khác nhau ở đâu?

<details>
<summary>Đáp án</summary>

`List<?>` reifiable nên có thể làm component của array creation; JVM dùng component runtime `List`. `List<String>` không reifiable, nên biểu thức `new List<String>[2]` bị từ chối với lỗi `generic array creation`.

</details>

**Câu 3.** Vì sao `String[]` có thể ném `ArrayStoreException` khi tham chiếu là `Object[]`?

<details>
<summary>Đáp án</summary>

Mảng là covariant; `Object[]` có thể trỏ tới một `String[]`. JVM giữ component runtime là `String`, do đó từ chối ghi `Integer` vào mảng dù kiểu tĩnh của alias là `Object[]`.

</details>

**Câu 4.** `Field.getGenericType()` trả `java.util.List<java.lang.String>`. Điều đó có xác nhận danh sách hiện tại chỉ chứa `String` không?

<details>
<summary>Đáp án</summary>

Không. Nó mô tả kiểu khai báo của field qua generic Signature metadata. Nội dung hiện tại vẫn có thể bị heap pollution nếu code dùng raw type, unchecked cast hoặc nguồn dữ liệu không được kiểm tra.

</details>

**Câu 5.** Với `<T extends Number>`, vì sao không tạo `new T[5]` ngay trong generic method dù `T` có erasure là `Number`?

<details>
<summary>Đáp án</summary>

`T` vẫn là type variable không reifiable. Erasure về `Number` không cho biết caller cần `Integer[]`, `Double[]` hay một subtype array nào khác. Dùng `IntFunction<T[]>` hoặc cơ chế cấp phát có runtime component type cụ thể khi cần mảng.

</details>

**Câu 6.** `List<?>[]` và `List[]` có thể cùng runtime class không? Nếu có, nên dùng kiểu nào trong code mới?

<details>
<summary>Đáp án</summary>

Có: mảng runtime của cả hai có component `List`. Trong code mới, `List<?>[]` thường có hợp đồng compile time rõ hơn nếu các phần tử là List có type argument chưa biết; raw `List[]` có thể phát sinh `rawtypes`/`unchecked` warnings.

</details>

## 15. Ghi nhớ trọng tâm

Một kiểu **reifiable** là kiểu có biểu diễn runtime phù hợp với các thao tác cần xác định kiểu ở runtime. Các nhóm chính là primitive, class/interface không generic, raw type, parameterized type chỉ chứa wildcard **không giới hạn**, và array có component reifiable.

`List<String>` có giá trị lớn ở compile time dù non-reifiable; `List<?>` reifiable nhưng không cho biết type argument cụ thể; raw `List` reifiable nhưng có thể làm mất kiểm tra generic; `String[]` reifiable và được JVM kiểm tra component khi ghi, còn `List<String>[]` không thể cấp phát trực tiếp.

Khi debug, hãy luôn tách **kiểu khai báo ở source**, **class/array component thực sự tại runtime** và **metadata generic mà Reflection đọc được**. Sự phân biệt này giúp bạn chọn đúng `instanceof`, tránh mảng generic không hợp lệ, và thiết kế các ranh giới kiểm tra dữ liệu type-safe.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="runtime-types-003">Vì sao List&lt;String&gt; không có đủ thông tin kiểu lúc chạy</a>

<details>
<summary>Xem chi tiết</summary>

# Vì sao `List<String>` không có đủ thông tin kiểu lúc chạy?

## 1. Câu hỏi thực chất là “thông tin kiểu nằm ở đâu?”

Trong Java, khai báo `List<String>` cho trình biên dịch biết danh sách được **dự định chứa các phần tử `String`**. Nhờ vậy, compiler kiểm tra các thao tác thêm phần tử, các phép gán và kiểu trả về của `get()`:

```java
List<String> names = new ArrayList<>();
names.add("An");             // Hợp lệ.
String first = names.get(0); // Hợp lệ; kiểu tĩnh của get(0) là String.
names.add(42);               // Lỗi biên dịch: Integer không phải String.
```

Đây là đoạn minh họa, phải đặt trong một phương thức và import `java.util.List`, `java.util.ArrayList` nếu muốn tự biên dịch. Dòng `names.add(42)` được cố ý đưa vào để minh họa lỗi, không phải mã chạy được.

Câu hỏi xuất hiện khi ứng dụng nhận một object bất kỳ và muốn biết: **“Object này là `List<String>` hay `List<Integer>`?”** Trong trường hợp danh sách generic thông thường, câu trả lời không nằm đầy đủ trong chính object khi chương trình chạy.

Điểm cần ghi nhớ ngay:

> Java Generics thông thường dùng **type erasure** (xóa kiểu): `List<String>` và `List<Integer>` là các kiểu nguồn khác nhau để compiler kiểm tra, nhưng cả hai đều dùng cùng một kiểu `List` sau xóa đối số kiểu. `ArrayList<String>` và `ArrayList<Integer>` do cùng class `ArrayList` tạo ra không có một “nhãn” kiểu phần tử riêng gắn với từng instance.

Điều đó **không** có nghĩa Java không còn bất kỳ thông tin generic nào trong mọi class file. Thông tin khai báo generic có thể được lưu trong metadata `Signature`. Sự khác nhau giữa **thông tin khai báo của field/class/method** và **kiểu tham số của từng object ở runtime** là chìa khóa của bài học này.

## 2. Phân biệt kiểu tĩnh của biến với class runtime của object

Xét:

```java
List<String> a = new ArrayList<>();
List<Integer> b = new ArrayList<>();
```

Hai biến có các **kiểu tĩnh** khác nhau:

- `a`: `List<String>`.
- `b`: `List<Integer>`.

Trình biên dịch sẽ chấp nhận `a.add("hello")` và `b.add(100)`, nhưng từ chối `a.add(100)` và `b.add("hello")`.

Tuy nhiên, object được tạo ở vế phải của cả hai khai báo đều do **cùng class runtime `ArrayList`** tạo ra. `a.getClass()` và `b.getClass()` vì thế có thể trả về cùng một đối tượng `Class` đại diện cho `java.util.ArrayList`.

Ví dụ độc lập trong tệp **`RuntimeClassDemo.java`**:

```java
import java.util.ArrayList;
import java.util.List;

public class RuntimeClassDemo {
    public static void main(String[] args) {
        List<String> names = new ArrayList<>();
        List<Integer> scores = new ArrayList<>();

        names.add("Mai");
        scores.add(10);

        System.out.println(names.getClass() == scores.getClass());
        System.out.println(names.getClass().getName());
        System.out.println(scores.getClass().getName());
        System.out.println(names instanceof List<?>);
    }
}
```

Biên dịch và chạy bằng JDK 17 trở lên:

```bash
javac RuntimeClassDemo.java
java RuntimeClassDemo
```

Stdout:

```text
true
java.util.ArrayList
java.util.ArrayList
true
```

Giải thích:

1. `getClass()` trả về class runtime thực của object, ở đây là `java.util.ArrayList`.
2. Hai biến có cùng runtime class vì cùng dùng `new ArrayList<>()`.
3. `List<?>` là kiểu kiểm tra được bằng `instanceof`: nó chỉ nói object là một `List` có đối số kiểu **chưa biết**.
4. `instanceof List<?>` **không** kiểm tra xem từng phần tử có phải `String` hay `Integer`.

Dấu `<>` ở `new ArrayList<>()` là **diamond operator**: compiler suy luận đối số kiểu từ ngữ cảnh mã nguồn. Nó không tạo ra một class runtime chuyên biệt cho `String`.

Lưu ý: hai danh sách có *implementation* khác nhau, ví dụ `ArrayList<String>` và `LinkedList<String>`, sẽ có runtime class khác nhau; đó là khác biệt **class triển khai**, không phải bằng chứng runtime về `String`.

## 3. Type erasure làm gì với `List<String>`?

Generic `List<E>` là một interface khai báo tham số kiểu `E`. Khi dùng `List<String>`, compiler thay `E` bằng `String` để kiểm tra mã nguồn. Khi sinh bytecode, Java áp dụng **xóa kiểu** theo những quy tắc xác định.

Đối với kiểu tham số hóa, erasure bỏ đối số kiểu:

| Kiểu Java ở mã nguồn | Kiểu tương ứng sau erasure |
|---|---|
| `List<String>` | `List` |
| `List<Integer>` | `List` |
| `List<?>` | `List` |
| `List<? extends Number>` | `List` |
| `ArrayList<String>` | `ArrayList` |
| `Map<String, Integer>` | `Map` |
| `List<List<String>>` | `List` |

Đối với biến kiểu (*type variable*), erasure thường là erasure của giới hạn đứng đầu:

- `<T>`: `T` bị xóa về `Object`.
- `<T extends Number>`: `T` bị xóa về `Number`.
- `<T extends Number & Comparable<T>>`: `T` bị xóa về `Number`.

Với interface `List<E>`, tham số `E` không có giới hạn tường minh, vì vậy erasure của `E` là `Object`. Khai báo phương thức như `E get(int index)` ở interface khi nhìn theo bytecode thường có kiểu trả về đã xóa là `Object`. Khi mã nguồn dùng `List<String>`, compiler biết lời gọi `get()` được kỳ vọng là `String` và có thể thêm cast tương ứng tại nơi sử dụng.

Ví dụ mô hình tinh giản:

```java
// Mô hình giải thích, KHÔNG phải mã nguồn thật của java.util.List.
interface ListModel {
    Object get(int index);
    boolean add(Object item);
}
```

Mô hình này chỉ cho thấy các vị trí chứa `E` có thể xuất hiện dưới dạng `Object` sau erasure; `List` thật còn có nhiều phương thức và các quy tắc bytecode khác. Không nên dùng nó như định nghĩa đầy đủ của Collections Framework.

### Vì sao Java chọn cách này?

Erasure giúp các thư viện generic làm việc với hệ thống class, JVM và mã Java cũ theo mô hình tương thích đã được thiết kế khi Generics được bổ sung vào ngôn ngữ. Đổi lại, generic Java thông thường **không được chuyên biệt hóa thành một class riêng cho mỗi đối số kiểu**.

Đó là lý do một instance `new ArrayList<String>()` không tự mang đủ thông tin để hỏi “Đối số generic nào đã được suy luận tại lệnh `new`?”

## 4. “Không có thông tin” không đồng nghĩa với “không kiểm tra kiểu”

Câu nói “Generics bị xóa” đôi khi bị hiểu sai thành “`List<String>` không có tác dụng”. Thực tế, phần lớn lợi ích nằm **trước và trong quá trình biên dịch**:

- Compiler từ chối truyền `Integer` vào API nhận `List<String>` nếu đường đi kiểu được kiểm tra bình thường.
- `String value = list.get(0)` được kiểm tra như lời gọi trả `String`.
- Trong một phương thức `<T>`, compiler giữ quan hệ cùng biến kiểu `T` giữa nhiều tham số và kiểu trả về.
- Wildcard như `List<?>`, `List<? extends Number>` và `List<? super Integer>` đặt thêm các giới hạn an toàn khi đọc/ghi.
- Các cast cần thiết có thể được tạo trong bytecode để duy trì ngữ nghĩa của mã đã được type-check.

Tuy nhiên, kiểm tra compile time không thể bảo vệ tuyệt đối khi chương trình đi qua **raw type**, **unchecked cast**, reflection hoặc dữ liệu bên ngoài được giải mã không an toàn. Đó là cách một object `List<String>` ở mức mã nguồn vẫn có thể chứa một phần tử không phải `String`.

## 5. Ví dụ heap pollution: danh sách sai kiểu nhưng chỉ lỗi khi đọc

Tạo tệp **`HeapPollutionDemo.java`**:

```java
import java.util.ArrayList;
import java.util.List;

public class HeapPollutionDemo {

    @SuppressWarnings({"rawtypes", "unchecked"})
    static void insertThroughRawType(List<String> values) {
        List raw = values;
        raw.add(99);
    }

    public static void main(String[] args) {
        List<String> words = new ArrayList<>();
        words.add("safe");

        insertThroughRawType(words);

        System.out.println(words.size());
        System.out.println(words.get(0));

        try {
            String second = words.get(1);
            System.out.println(second);
        } catch (ClassCastException ex) {
            System.out.println("ClassCastException");
        }
    }
}
```

Chạy:

```bash
javac HeapPollutionDemo.java
java HeapPollutionDemo
```

Stdout:

```text
2
safe
ClassCastException
```

Diễn biến:

1. `words` có kiểu tĩnh `List<String>`.
2. Phương thức `insertThroughRawType` cố ý dùng raw `List` để bỏ mất đối số kiểu.
3. `raw.add(99)` thêm `Integer`; ở runtime, collection bình thường không tự kiểm tra rằng object từng được dùng qua biến `List<String>`.
4. Kích thước danh sách trở thành `2`; đọc phần tử đầu (`String`) vẫn hoạt động.
5. `words.get(1)` có kiểu tĩnh `String`; khi nhận kết quả thực `Integer`, phép chuyển kiểu cần thiết thất bại và sinh `ClassCastException`.

Đây là **heap pollution**: một biến có kiểu tham số hóa trỏ tới dữ liệu không còn phù hợp với hợp đồng kiểu của biến.

Annotation `@SuppressWarnings` chỉ được đặt trên phương thức cố ý minh họa hành vi nguy hiểm để tránh nhiễu output cảnh báo trong ví dụ. Trong ứng dụng thật, **không nên dùng raw type hoặc suppress unchecked warning tùy tiện**. Bảo vệ ranh giới dữ liệu đúng cách quan trọng hơn việc làm mất cảnh báo.

Bài học quan trọng: Java không bắt buộc lỗi xuất hiện ngay tại `raw.add(99)`. Việc dùng `String` ở nơi đọc mới có thể làm cast thất bại.

## 6. Vì sao không viết được `instanceof List<String>`?

Giả sử một API nhận:

```java
Object input = new ArrayList<String>();
```

Mục tiêu là kiểm tra chính xác liệu `input` có phải danh sách các chuỗi hay không. Thử viết:

```java
// Cố ý không hợp lệ trong Java:
boolean isStringList = input instanceof List<String>;
```

Compiler từ chối dạng kiểm tra này vì `List<String>` **không phải kiểu reifiable**: JVM không thể xác minh đầy đủ đối số generic `String` chỉ từ runtime type của object.

Các biểu thức hợp lệ có ý nghĩa khác:

```java
boolean a = input instanceof List;
boolean b = input instanceof List<?>;
```

Chúng có thể xác minh object thuộc `List`, nhưng không xác minh loại phần tử.

Từ Java 16 trở đi, pattern matching `instanceof` có thể dùng với `List<?>`:

```java
if (input instanceof List<?> values) {
    System.out.println(values.size());
}
```

Ở đây biến pattern `values` có kiểu `List<?>`. Khi đọc `values.get(0)`, compiler chỉ có thể dựa vào những điều đã biết về một đối số kiểu chưa xác định; với `List<?>`, kết quả an toàn để nhận là `Object`.

### Kiểu reifiable là gì?

Một kiểu là **reifiable** khi biểu diễn kiểu của nó đủ hiện diện tại runtime theo các quy tắc của ngôn ngữ. Ví dụ thường gặp:

- `String`, `Integer` và các class/interface không tham số hóa.
- `int`, `boolean` và các primitive types.
- Raw `List`.
- `List<?>`, `Map<?, ?>`: tham số hóa chỉ dùng wildcard không giới hạn ở các vị trí đối số kiểu thích hợp.
- Mảng có kiểu phần tử reifiable, chẳng hạn `String[]` hoặc `List<?>[]`.

Ngược lại, `List<String>`, `List<Integer>`, `List<? extends Number>` không phải kiểu reifiable theo nghĩa có thể kiểm tra đầy đủ cấu trúc generic tại runtime.

Hãy phân biệt **kiểm tra loại container** với **kiểm tra từng phần tử**. `input instanceof List<?>` chỉ thực hiện việc đầu tiên.

## 7. Kiểm tra dữ liệu runtime đúng cách với `List<?>`

Nếu chương trình nhận một object từ plugin, thư viện cũ hoặc một API kém kiểu hóa, và nghiệp vụ yêu cầu **mọi phần tử đều là `String`**, ta có thể kiểm tra **nội dung thực tế**.

Tạo tệp **`CheckListElementsDemo.java`**:

```java
import java.util.List;

public class CheckListElementsDemo {

    static boolean containsOnlyStrings(Object input) {
        if (!(input instanceof List<?> values)) {
            return false;
        }

        for (Object value : values) {
            if (!(value instanceof String)) {
                return false;
            }
        }
        return true;
    }

    public static void main(String[] args) {
        System.out.println(containsOnlyStrings(List.of("An", "Binh")));
        System.out.println(containsOnlyStrings(List.of("An", 10)));
        System.out.println(containsOnlyStrings(List.of()));
        System.out.println(containsOnlyStrings("not a list"));
    }
}
```

Biên dịch và chạy:

```bash
javac CheckListElementsDemo.java
java CheckListElementsDemo
```

Stdout:

```text
true
false
true
false
```

Ý nghĩa của ví dụ:

- Nếu không phải `List<?>`, trả `false`.
- Nếu là `List<?>`, duyệt từng phần tử và kiểm tra bằng `instanceof String`.
- Danh sách rỗng trả `true` vì không có phần tử nào vi phạm quy tắc “mọi phần tử đều là String”.

**Cảnh báo về ý nghĩa:** Hàm trên kiểm tra *nội dung đang có*, không chứng minh danh sách được **khai báo** là `List<String>`. Một `List<Object>` chứa toàn chuỗi cũng đạt kiểm tra. Danh sách rỗng không có phần tử để suy ra bất kỳ type argument nào.

Ví dụ này xem `null` là không hợp lệ, bởi `null instanceof String` là `false`. Nếu bài toán cho phép phần tử `null`, phải thay điều kiện thành `value != null && !(value instanceof String)` để chỉ bác bỏ những phần tử khác `String`, đồng thời giữ chính sách nhất quán ở các lớp xử lý khác.

Thêm nữa, kiểm tra trên một collection **đang bị thread khác chỉnh sửa** không bảo đảm nội dung không thay đổi ngay sau đó. Nếu cần dữ liệu ổn định, có thể tạo một bản sao đã được xác minh, đồng bộ hoặc áp dụng hợp đồng bất biến phù hợp.

## 8. Tạo bản sao được kiểm tra bằng `Class<T>`

Đối với các trường hợp biết phần tử mong đợi là một **class runtime cụ thể**, có thể truyền `Class<T>` vào API và tạo một bản sao đã kiểm tra.

Tệp **`CheckedCopyDemo.java`**:

```java
import java.util.ArrayList;
import java.util.List;

public class CheckedCopyDemo {

    static <T> List<T> checkedCopy(List<?> source, Class<T> type) {
        List<T> result = new ArrayList<>();

        for (Object element : source) {
            result.add(type.cast(element));
        }

        return List.copyOf(result);
    }

    public static void main(String[] args) {
        List<String> words = checkedCopy(
                List.of("red", "blue"), String.class);
        System.out.println(words);

        try {
            checkedCopy(List.of("red", 3), String.class);
        } catch (ClassCastException ex) {
            System.out.println("ClassCastException");
        }
    }
}
```

Biên dịch và chạy:

```bash
javac CheckedCopyDemo.java
java CheckedCopyDemo
```

Stdout:

```text
[red, blue]
ClassCastException
```

Ở đây:

1. `source` là `List<?>`, nên không giả định trước loại phần tử.
2. `type.cast(element)` kiểm tra runtime rằng từng phần tử phù hợp `Class<T>`.
3. Các phần tử qua kiểm tra được đưa vào `List<T>` mới.
4. `List.copyOf` tạo danh sách không thể chỉnh sửa qua API chuẩn và từ chối `null`; trong ví dụ đầu vào không chứa `null`.

Đây là cách chuyển một ranh giới nhận dữ liệu chưa biết kiểu thành một kết quả có hợp đồng rõ ràng, **dựa trên việc xác minh thực tế**.

Nhưng `Class<T>` chỉ mô tả một class hoặc array class có thể có `Class` token. Bạn không thể cung cấp `List<String>.class` như một `Class<List<String>>` cụ thể: parameterized type này không có class literal riêng. Khi cần mô tả các kiểu lồng nhau như `List<List<String>>` hay `Map<String, List<Integer>>`, ta cần biểu diễn kiểu ở mức `java.lang.reflect.Type` hoặc một schema phù hợp, và vẫn phải kiểm tra dữ liệu theo cấu trúc tương ứng.

## 9. Vì sao `List<String>.class` không tồn tại?

Java cho phép các class literal như:

```java
String.class
Integer.class
java.util.List.class
String[].class
```

Nhưng không cho:

```java
// Cố ý không hợp lệ:
List<String>.class
List<Integer>.class
```

Nguyên nhân trực tiếp là `List<String>` và `List<Integer>` **không có hai đối tượng `Class` runtime tách biệt**. `List.class` đại diện cho raw `java.util.List`, chứ không phải một phần tử kiểu `String`.

Đó cũng là lý do đoạn sau là sai về mặt mô hình:

```java
// Cố ý không hợp lệ:
Class<List<String>> listClass = List<String>.class;
```

Một `Class<?>` biểu thị runtime class, còn `java.lang.reflect.Type` có thể biểu thị cả cấu trúc generic **được khai báo**. Hai khái niệm này liên quan nhưng khác phạm vi thông tin.

Không nên cố sửa bằng cast unchecked một `Class<?>` để giả vờ có `Class<List<String>>`: kiểu biến có vẻ “đẹp” hơn không tạo kiểm tra runtime cho những đối số generic đã bị xóa.

## 10. Generic `Signature`: tại sao reflection đôi khi vẫn thấy `List<String>`?

Java class file có thể lưu metadata generic trong thuộc tính **`Signature`**. Metadata này ghi cấu trúc **khai báo generic** trên class, field hoặc method khi thích hợp.

Ví dụ:

```java
import java.util.List;

class Catalog {
    List<String> names;
    List<Integer> counts;
}
```

Compiler có thể ghi:

- `names`: field descriptor `Ljava/util/List;`, generic signature `Ljava/util/List<Ljava/lang/String;>;`.
- `counts`: field descriptor `Ljava/util/List;`, generic signature `Ljava/util/List<Ljava/lang/Integer;>;`.

**Descriptor** biểu diễn kiểu JVM dùng cho field. **Signature** giữ thông tin `String` hoặc `Integer` từ khai báo của field. Reflection có thể đọc Signature bằng `Field.getGenericType()`.

Đây không phải “đối tượng List tự nhớ nó chứa gì”. Nếu bạn gán cùng một object `ArrayList` cho một field khai báo `List<String>` (qua lời gán an toàn hoặc mã unchecked), reflection trên field sẽ thấy **khai báo của field**, không đọc danh sách để suy ra kiểu từ từng phần tử.

### Ví dụ reflection trên hai field

Tạo tệp **`FieldMetadataDemo.java`**:

```java
import java.lang.reflect.Field;
import java.util.List;

public class FieldMetadataDemo {

    static class Registry {
        List<String> names;
        List<Integer> numbers;
    }

    public static void main(String[] args) throws Exception {
        Field names = Registry.class.getDeclaredField("names");
        Field numbers = Registry.class.getDeclaredField("numbers");

        System.out.println(names.getType().getTypeName());
        System.out.println(names.getGenericType().getTypeName());
        System.out.println(numbers.getType().getTypeName());
        System.out.println(numbers.getGenericType().getTypeName());
    }
}
```

Chạy:

```bash
javac FieldMetadataDemo.java
java FieldMetadataDemo
```

Stdout:

```text
java.util.List
java.util.List<java.lang.String>
java.util.List
java.util.List<java.lang.Integer>
```

`getType()` cho `Class<?>` tương ứng với kiểu đã xóa. `getGenericType()` cho `Type`, có khả năng diễn đạt cấu trúc generic trong khai báo. Cả hai cùng trả lời những câu hỏi đúng trong **bối cảnh khác nhau**.

Ví dụ này có thể chạy khi chưa hề tạo instance `Registry` hay instance `List`, chứng minh metadata nằm trên **khai báo field của class**, không phụ thuộc vào giá trị danh sách tại runtime.

## 11. Metadata superclass là thông tin khai báo, không phải nhãn của từng instance

Một subclass có thể ghi rõ đối số generic trong quan hệ kế thừa:

```java
class StringList extends java.util.ArrayList<String> {}
class GenericList<E> extends java.util.ArrayList<E> {}
```

`StringList.class.getGenericSuperclass().getTypeName()` có thể trả `java.util.ArrayList<java.lang.String>`, trong khi cùng phép đọc metadata ở `GenericList.class` trả `java.util.ArrayList<E>`. Class đầu **khai báo** String; class sau vẫn có type variable E và không tự chuyển thành Integer theo từng `new GenericList<Integer>()`.

Đó là thông tin `Signature` trong class file của **lớp con**, không phải bằng chứng những phần tử thực đã được kiểm tra. Bài chuyên về `getClass()` sẽ phân tích sâu sự khác nhau giữa class instance, subclass cố định type argument và anonymous subclass được tạo trong một generic factory.


## 12. Anonymous subclass và kỹ thuật type capture

Một số thư viện dùng **type reference/type token** bằng lớp con vô danh để giữ cấu trúc generic trong khai báo superclass. Đó là một cách chủ động tạo **class mới có khai báo** mang kiểu mong muốn.

Tạo tệp **`TypeCaptureDemo.java`**:

```java
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

public class TypeCaptureDemo {

    static abstract class TypeRef<T> {
        private final Type captured;

        protected TypeRef() {
            Type parent = getClass().getGenericSuperclass();

            if (!(parent instanceof ParameterizedType parameterized)) {
                throw new IllegalStateException(
                        "Subclass must declare a concrete type argument");
            }

            captured = parameterized.getActualTypeArguments()[0];
        }

        final Type type() {
            return captured;
        }
    }

    public static void main(String[] args) {
        TypeRef<List<String>> words = new TypeRef<List<String>>() {};
        TypeRef<Map<String, List<Integer>>> nested =
                new TypeRef<Map<String, List<Integer>>>() {};

        System.out.println(words.type().getTypeName());
        System.out.println(nested.type().getTypeName());
    }
}
```

Biên dịch và chạy:

```bash
javac TypeCaptureDemo.java
java TypeCaptureDemo
```

Stdout:

```text
java.util.List<java.lang.String>
java.util.Map<java.lang.String, java.util.List<java.lang.Integer>>
```

Cơ chế:

1. `TypeRef<T>` là một class generic.
2. `new TypeRef<List<String>>() {}` khai báo một **anonymous subclass** có generic superclass là `TypeRef<List<String>>`.
3. `getClass()` trong constructor cho class runtime của anonymous subclass.
4. `getGenericSuperclass()` đọc **khai báo** superclass của class vô danh.
5. `ParameterizedType.getActualTypeArguments()[0]` lấy đối số kiểu `List<String>` dưới dạng `Type`.
6. `Type.getTypeName()` trình bày cấu trúc generic có thể lồng nhiều tầng.

Đây là kỹ thuật nền tảng trong một số thư viện serialization/deserialization; một số thư viện đặt tên lớp hỗ trợ là `TypeReference` hoặc `TypeToken`.

**Giới hạn quan trọng:** Kỹ thuật này chỉ giữ thông tin **thực sự được ghi trong quan hệ khai báo superclass**. Nếu một type argument vẫn là `T` của một phương thức generic, type capture không tự biến `T` thành `String` ở runtime. Ngoài ra, type reference không tự xác thực dữ liệu JSON hoặc collection; nó chỉ cung cấp **mô tả kiểu mong đợi** cho mã khác thực hiện công việc.

Mẫu `TypeRef` trên cố tình đơn giản: nó giả định anonymous subclass trực tiếp mở rộng `TypeRef<...>`. Một framework thực tế có thể cần xử lý nhiều tầng kế thừa và ánh xạ type variables kỹ hơn.

## 13. `ParameterizedType`, `TypeVariable` và `WildcardType` không phải `Class`

Khi đọc metadata generic qua reflection, dữ liệu trả về thường có kiểu interface `java.lang.reflect.Type`. Một `Type` không nhất thiết là `Class<?>`.

Các dạng thường gặp:

| Kiểu ở khai báo | Reflection thường biểu diễn |
|---|---|
| `String` | `Class<?>` |
| `List<String>` | `ParameterizedType` |
| `T` trong `class Box<T>` | `TypeVariable<?>` |
| `? extends Number` | `WildcardType` |
| `T[]` với `T` là type variable | `GenericArrayType` |
| `String[]` | `Class<?>` |

Ví dụ đoạn mã phân tích generic field:

```java
Type type = field.getGenericType();

if (type instanceof ParameterizedType parameterized) {
    Type raw = parameterized.getRawType();
    Type[] arguments = parameterized.getActualTypeArguments();
    // Phân tích raw và từng argument theo yêu cầu ứng dụng.
}
```

Đây là **mảnh code minh họa**, cần đặt trong class/method có biến `field` kiểu `java.lang.reflect.Field` và import các kiểu reflection tương ứng.

Việc một `Type` mô tả `List<String>` **không có nghĩa** phép `type instanceof Class<?>` phải đúng. `ParameterizedType` lưu cấu trúc tham số hóa chứ không phải tạo `Class<List<String>>` mới.

Cũng không nên phân tích `type.getTypeName()` bằng cách cắt chuỗi tùy tiện: generic type có thể gồm wildcard, giới hạn, mảng, kiểu lồng và type variables. Khi viết framework, ưu tiên mô hình có cấu trúc của API Reflection.

## 14. Thông tin `List<String>` của biến cục bộ thì sao?

Câu lệnh:

```java
List<String> names = new ArrayList<>();
```

nếu nằm trong một method, giúp compiler kiểm tra kiểu **biến cục bộ** `names`. Nhưng từ một object `ArrayList`, API Reflection thông thường **không thể hỏi ngược** rằng nó từng được gán vào biến cục bộ `names` khai báo `List<String>`.

Một số class file có thể chứa metadata debug dành cho local variables, như `LocalVariableTable` hoặc `LocalVariableTypeTable` khi compiler/cấu hình ghi chúng. Dữ liệu này có thể hỗ trợ công cụ debug, nhưng:

- Không nhất thiết hiện diện trong mọi bản build.
- Gắn với vị trí/thời gian sống của biến cục bộ trong **method bytecode**, không tự gắn vào object.
- Không phải API tiêu chuẩn để đọc type argument của một object `List` khi ứng dụng chạy.
- Không nên dùng làm hợp đồng của thư viện nghiệp vụ.

Thông tin `Signature` ở **field/method/class declaration** có vai trò khác với metadata local-variable phục vụ debug. Đừng gộp chúng thành một khái niệm duy nhất “JVM luôn nhớ kiểu generic”.

## 15. Generic arrays: sự khác biệt có ích để đối chiếu

Java arrays có cơ chế runtime khác generic collections:

```java
String[] names = new String[2];
Object[] objects = names;
```

Mảng `String[]` có component type runtime là `String`, nên JVM có thể kiểm tra phép gán phần tử. Với generic `List<String>`, không có runtime component class riêng cho `String` trong `ArrayList` thông thường.

Tạo tệp **`ArrayVsGenericDemo.java`**:

```java
import java.util.ArrayList;
import java.util.List;

public class ArrayVsGenericDemo {
    public static void main(String[] args) {
        String[] names = new String[2];
        Object[] objects = names;

        try {
            objects[0] = 123;
        } catch (ArrayStoreException ex) {
            System.out.println("ArrayStoreException");
        }

        List<String> words = new ArrayList<>();
        words.add("ok");

        System.out.println(names.getClass().getComponentType().getName());
        System.out.println(words.getClass().getName());
    }
}
```

Biên dịch và chạy:

```bash
javac ArrayVsGenericDemo.java
java ArrayVsGenericDemo
```

Stdout:

```text
ArrayStoreException
java.lang.String
java.util.ArrayList
```

Giải thích:

- `String[]` là mảng có component type reifiable. Nếu một tham chiếu `Object[]` thực sự trỏ tới `String[]`, nạp `Integer` vào mảng sẽ bị `ArrayStoreException`.
- `ArrayList<String>` vẫn chỉ có runtime class là `ArrayList`. Danh sách không dùng kiểu phần tử generic của biến để kiểm tra `add(...)` bằng một runtime component type tương tự mảng.

Đó cũng là một phần lý do Java **cấm trực tiếp tạo mảng của kiểu generic không reifiable** như:

```java
// Cố ý không hợp lệ:
List<String>[] array = new List<String>[10];
```

Sự kết hợp giữa tính hiệp biến (*covariance*) của mảng và cơ chế erasure của generic có thể tạo vấn đề an toàn kiểu. Nên ưu tiên dùng `List<List<String>>` hoặc một cấu trúc phù hợp thay vì tìm cách lách giới hạn mảng generic bằng các unchecked casts.

## 16. `Collections.checkedList` có giải quyết hết bài toán không?

Java cung cấp các checked collection wrappers như `Collections.checkedList(List<E>, Class<E>)`. Wrapper này thực hiện một phần kiểm tra kiểu **ở runtime khi thao tác thêm/sửa phần tử đi qua wrapper**.

Ví dụ **`CheckedListDemo.java`**:

```java
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CheckedListDemo {
    public static void main(String[] args) {
        List<String> checked =
                Collections.checkedList(new ArrayList<>(), String.class);

        checked.add("safe");
        System.out.println(checked);

        try {
            @SuppressWarnings({"rawtypes", "unchecked"})
            List raw = checked;
            raw.add(42);
        } catch (ClassCastException ex) {
            System.out.println("ClassCastException");
        }

        System.out.println(checked);
    }
}
```

Chạy:

```bash
javac CheckedListDemo.java
java CheckedListDemo
```

Stdout:

```text
[safe]
ClassCastException
[safe]
```

Điểm đúng cần rút ra:

- `checkedList` giữ một `Class<E>` token để kiểm tra các thao tác phù hợp **đi qua chính wrapper**.
- Dù caller dùng raw reference trỏ tới wrapper, thao tác chèn giá trị sai lớp vẫn có thể bị từ chối ở runtime.
- Wrapper **không biến kiểu tham số hóa `List<String>` thành reified** trong ngôn ngữ Java.
- Nếu bên ngoài còn giữ tham chiếu trực tiếp tới **backing list** và chỉnh sửa nó mà bỏ qua wrapper, các kiểm tra của wrapper không bảo vệ được những thay đổi ấy.
- Các phần tử đã nằm trong backing list trước khi tạo wrapper không nhất thiết được wrapper quét và xác thực toàn bộ ngay lúc khởi tạo.

Vì vậy, checked wrapper là một công cụ bảo vệ ranh giới cụ thể, không phải cơ chế phục hồi generic type argument từ mọi `List` runtime.

## 17. Những sai lầm thường gặp khi suy luận kiểu của `List`

**Sai lầm 1 — “`getClass()` trả về `ArrayList<String>`.”** Không. Trong ví dụ thông thường, `getClass()` trả `Class` của `ArrayList`. Thông tin `<String>` không tạo class runtime mới.

**Sai lầm 2 — “`instanceof List<String>` sẽ kiểm tra được phần tử.”** Không. Kiểu này không reifiable, nên phép kiểm tra trực tiếp không hợp lệ. `instanceof List<?>` chỉ xác minh container.

**Sai lầm 3 — “Mọi thông tin generic bị mất khỏi mọi class file.”** Không. `Signature` có thể lưu generic declaration của field/method/class, và reflection có thể đọc chúng.

**Sai lầm 4 — “Nếu reflection thấy `List<String>` ở field, object chắc chắn chỉ chứa `String`.”** Không. Reflection mô tả **kiểu được khai báo**; raw/unchecked access hoặc dữ liệu không được kiểm tra có thể làm collection chứa giá trị sai.

**Sai lầm 5 — “Kiểm tra phần tử đầu tiên là đủ.”** Không. Danh sách có thể rỗng, có nhiều kiểu phần tử, hoặc được chỉnh sửa sau khi kiểm tra. Kiểm tra toàn bộ chỉ chứng minh điều kiện trên dữ liệu được quan sát tại thời điểm kiểm tra.

**Sai lầm 6 — “Danh sách rỗng tự chứng minh `List<String>`.”** Không. Một danh sách rỗng đồng thời thỏa vị từ “mọi phần tử là String” và “mọi phần tử là Integer”; không thể suy ra generic argument nguồn từ việc không có phần tử.

**Sai lầm 7 — “Type token tự động xác thực mọi kiểu lồng nhau.”** Không. `String.class` kiểm tra được `String`, nhưng `List<String>.class` không tồn tại. Kiểu generic lồng nhau cần biểu diễn bằng `Type` hoặc schema, và cần mã kiểm tra/giải mã thực tế.

**Sai lầm 8 — “Anonymous subclass chứng minh các phần tử đã an toàn.”** Không. Nó có thể giữ lại khai báo `List<String>` trong generic superclass metadata; nội dung collection vẫn cần hợp đồng và xác minh riêng.

**Sai lầm 9 — “Thấy `@SuppressWarnings("unchecked")` là chắc chắn an toàn.”** Không. Annotation chỉ tắt cảnh báo biên dịch được chỉ định; nó không thêm kiểm tra runtime nào.

**Sai lầm 10 — “Type erasure khiến generics không hữu dụng.”** Không. Generics giúp bắt lỗi sớm trong mã được kiểm tra bình thường và duy trì quan hệ kiểu nhất quán giữa tham số, kết quả và thao tác trên collection.

## 18. Tình huống áp dụng: chọn công cụ đúng câu hỏi

| Câu hỏi bạn cần trả lời | Cách phù hợp |
|---|---|
| “Object này có phải một `List` không?” | `instanceof List<?>` |
| “Mọi phần tử hiện tại có phải `String` không?” | Duyệt `List<?>`, kiểm tra từng phần tử và chính sách `null` |
| “Tôi muốn có một `List<String>` mới được xác minh.” | Tạo bản sao kiểm tra bằng `String.class` trước khi dùng |
| “Field này được khai báo là `List<String>` hay `List<Integer>`?” | Reflection: `Field.getGenericType()` |
| “Subclass này khai báo kiểu generic gì cho superclass?” | `Class.getGenericSuperclass()` |
| “Tôi cần mô tả `Map<String, List<Integer>>` cho thư viện giải mã.” | `Type`/type reference hoặc schema phù hợp |
| “Tôi có thể lấy `Class<List<String>>` từ `List<String>.class` không?” | Không; không tồn tại class literal đó |
| “Muốn cấm thêm phần tử sai qua wrapper runtime.” | `Collections.checkedList` với class token |
| “Muốn an toàn kiểu khi code mới làm việc với danh sách.” | Giữ `List<T>` hoặc `List<?>`, tránh raw types |
| “Có dữ liệu từ JSON/API chưa tin cậy.” | Giải mã theo schema/type và xác minh dữ liệu thực tế |

Một quy tắc thiết kế thực dụng: **để compiler kiểm tra kiểu khi có thể; truyền metadata/type token khi thư viện thật sự cần biết kiểu; kiểm tra nội dung tại biên giới dữ liệu chưa tin cậy**. Ba hoạt động này có vai trò riêng và thường bổ sung cho nhau.

## 19. Tự kiểm tra hiểu biết

**Câu 1.** Với:

```java
List<String> a = new ArrayList<>();
List<Integer> b = new ArrayList<>();
```

`a.getClass() == b.getClass()` trả gì?

**Đáp án:** `true`, vì hai object cùng được tạo từ `ArrayList`.

**Câu 2.** Vì sao compiler cho `String s = a.get(0)` dù method `List.get` ở bytecode liên quan tới `Object`?

**Đáp án:** Compiler dùng kiểu tĩnh `List<String>` để biết kết quả mong đợi là `String` và có thể tạo cast tương ứng trong bytecode.

**Câu 3.** Có thể viết `obj instanceof List<String>` trong Java 17 để xác minh loại phần tử không?

**Đáp án:** Không. `List<String>` không phải kiểu reifiable dùng được trong kiểm tra runtime kiểu này.

**Câu 4.** `obj instanceof List<?>` chứng minh điều gì?

**Đáp án:** `obj` là một `List` (không null) có đối số kiểu chưa biết; không chứng minh phần tử đều là `String`.

**Câu 5.** `Field.getGenericType()` trả `List<String>`. Điều này nói về object trong field hay khai báo của field?

**Đáp án:** Nói về **kiểu generic được khai báo của field**, không chứng nhận nội dung thực tế của object.

**Câu 6.** Tại sao `getGenericSuperclass()` của một subclass có thể báo `ArrayList<String>`?

**Đáp án:** Vì quan hệ `extends ArrayList<String>` là một phần của **khai báo class con**, có thể được giữ trong generic metadata.

**Câu 7.** Một hàm kiểm tra từng phần tử cho kết quả `true` với danh sách rỗng. Nó chứng minh danh sách là `List<String>` không?

**Đáp án:** Không. Không có phần tử sai không có nghĩa metadata kiểu của đối tượng đã được phục hồi.

**Câu 8.** `Class<String>` khác `Type` chứa `List<String>` thế nào?

**Đáp án:** `Class<String>` đại diện cho class runtime `String`; `Type` có thể diễn tả cấu trúc generic của một khai báo, trong đó `List<String>` là một `ParameterizedType`.

**Câu 9.** Một `Collections.checkedList` có bảo vệ các thay đổi đi trực tiếp vào backing list mà không qua wrapper không?

**Đáp án:** Không; kiểm tra của wrapper chỉ áp dụng khi các thao tác phù hợp được thực hiện thông qua wrapper.

**Câu 10.** Vì sao raw `List` có thể làm một `List<String>` chứa `Integer`?

**Đáp án:** Raw type làm mất kiểm tra kiểu phần tử ở compile time tại điểm sử dụng; object `ArrayList` thường không có runtime generic argument `String` để chặn thao tác thêm sai kiểu.

## 20. Bài thực hành theo từng bước

1. **Quan sát class runtime:** Chạy `RuntimeClassDemo`, thay `List<String>` bằng `List<Double>` và giải thích vì sao `getClass()` vẫn không hiển thị type argument.
2. **Chứng minh erasure:** Biên dịch `FieldMetadataDemo`, chạy `javap -p -s FieldMetadataDemo` hoặc xem class nested `FieldMetadataDemo$Registry`; xác định field descriptor chứa `List` chứ không chứa `<String>`.
3. **Đọc Signature:** Chạy `javap -p -v 'FieldMetadataDemo$Registry'` và tìm chuỗi generic Signature của hai field.
4. **Thử `instanceof`:** Viết một phiên bản dùng `instanceof List<String>` và quan sát lỗi compiler; thay bằng `instanceof List<?>` và kiểm tra từng phần tử.
5. **Kiểm tra heap pollution:** Chạy `HeapPollutionDemo`, bỏ phần catch trong một bản sao thử nghiệm để xem vị trí runtime phát sinh lỗi.
6. **Chủ động giữ type metadata:** Chạy `TypeCaptureDemo`, thử đổi kiểu thứ hai thành `Map<Long, List<String>>` và quan sát `getTypeName()`.
7. **Thực hành biên giới dữ liệu:** Mở rộng `checkedCopy` để từ chối `null` bằng chính sách rõ ràng; đối chiếu với `List.copyOf`, vốn cũng từ chối phần tử `null`.
8. **Kiểm tra giới hạn wrapper:** Tạo một backing `ArrayList<String>`, bọc bằng `Collections.checkedList`, sau đó xác minh rằng mọi thao tác thêm qua wrapper được kiểm tra; giải thích rủi ro nếu vẫn còn đường truy cập không an toàn vào backing list.

Trong tất cả bài tập, hãy phân biệt **điều compiler chứng minh**, **kiểu JVM biết lúc chạy**, và **điều chương trình tự kiểm tra trong dữ liệu**.

## 21. Tổng kết

`List<String>` có ý nghĩa rất lớn **ở mã nguồn Java**: nó cho compiler biết loại phần tử dự kiến và cho phép phát hiện sai kiểu sớm. Nhưng Java Generics thông thường bị xóa kiểu khi biên dịch; một instance `ArrayList` không tự mang đối số `<String>` như một phần đầy đủ của runtime class identity. Vì thế `getClass()`, `instanceof` hay class literal thông thường không thể khôi phục chắc chắn `String` từ một danh sách bất kỳ.

Một số generic information **vẫn còn trong class file** dưới dạng `Signature` của khai báo class, field hoặc method. Reflection và type-reference pattern có thể đọc phần metadata **đã được khai báo** ấy, nhưng không biến mọi generic object thành reified object.

Khi cần xác minh loại phần tử thực tế, hãy kiểm tra dữ liệu bằng `List<?>` và cơ chế như `Class<T>.cast`, tạo bản sao an toàn hoặc dùng wrapper có kiểm tra runtime ở ranh giới thích hợp. Đừng nhầm metadata khai báo với việc xác minh từng phần tử.

### Tài liệu chuẩn để đối chiếu

- [Java Language Specification §4.6 — Type Erasure](https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html#jls-4.6): nguyên tắc xóa parameterized types và type variables.
- [Java Language Specification §4.7 — Reifiable Types](https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html#jls-4.7): các kiểu có đủ thông tin runtime theo đặc tả.
- [Java Language Specification §4.8 — Raw Types](https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html#jls-4.8): tương thích mã cũ và nguy cơ unchecked.
- [Java Language Specification §15.20.2 — Type Comparison Operator `instanceof`](https://docs.oracle.com/javase/specs/jls/se17/html/jls-15.html#jls-15.20.2): điều kiện dùng `instanceof`.
- [JVM Specification §4.7.9 — `Signature` Attribute](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-4.html#jvms-4.7.9): metadata generic trong class file.
- [Java SE 17 `java.lang.reflect.Type`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Type.html): biểu diễn kiểu generic qua Reflection.
- [Java SE 17 `java.util.Collections.checkedList`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collections.html#checkedList(java.util.List,java.lang.Class)): wrapper kiểm tra phần tử khi chạy.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="runtime-types-004">Class literal: String.class, List.class và giới hạn của List&lt;String&gt;.class</a>

<details>
<summary>Xem chi tiết</summary>

# Class literal: `String.class`, `List.class` và giới hạn của `List<String>.class`

## Mục tiêu học tập

Trong Java, cú pháp `SomeType.class` trả về một đối tượng `Class` đại diện cho một kiểu có thể được nhận diện bằng class literal. Khi đi từ `String.class` sang `List.class` rồi thử viết `List<String>.class`, bạn chạm tới khác biệt cốt lõi giữa **kiểu trong mã nguồn** và **thông tin kiểu có thể kiểm tra lúc chạy** của Java Generics.

Sau bài này, bạn có thể:

1. Hiểu `String.class` và `List.class` là gì, kiểu generic của mỗi biểu thức là gì và vì sao `List<String>.class` không hợp lệ.
2. Phân biệt `Class<T>`, `Class<?>`, `Type` và `ParameterizedType`, cùng giới hạn của từng API.
3. Giải thích liên hệ giữa **type erasure**, **reifiable type** và cú pháp class literal.
4. Sử dụng `Class.isInstance`, `Class.cast` và `Class<T>` để kiểm tra loại đối tượng cụ thể; nhận ra khi nào chúng không thể kiểm tra phần tử của collection.
5. Thu thập thông tin generic từ khai báo bằng reflection hoặc một type reference, và tránh coi metadata đó là bằng chứng dữ liệu runtime đã đúng kiểu.

## 1. Class literal là gì?

Class literal có cú pháp kết thúc bằng `.class`, ví dụ:

```java
String.class
Integer.class
java.util.List.class
int.class
String[].class
void.class
```

Những biểu thức này đại diện cho các kiểu mà Java có thể biểu diễn bằng đối tượng `java.lang.Class` tương ứng. Ví dụ:

```java
Class<String> stringType = String.class;
Class<?> listType = java.util.List.class;

System.out.println(stringType.getName());
System.out.println(listType.getName());
```

Đoạn mã minh họa trên đặt bên trong một phương thức, có stdout:

```text
java.lang.String
java.util.List
```

`String.class` không tạo một `String` mới, còn `List.class` không tạo một `List` mới. Chúng là **đối tượng mô tả kiểu** (*class tokens*), dùng cho reflection, xác thực runtime, tìm metadata, hoặc cung cấp thông tin loại đối tượng cho API có tham số kiểu.

Đối với kiểu tham chiếu không generic, kiểu của biểu thức `String.class` là `Class<String>`. Với một tên kiểu generic được viết **không có đối số kiểu**, như `List.class`, biểu thức tương ứng với `Class<List>` (trong đó `List` là raw type), và ta thường gán nó vào `Class<?>` khi không cần thao tác cụ thể với raw generic:

```java
Class<?> listToken = java.util.List.class;
```

Việc dùng `Class<?>` ở đây giúp code khai báo rõ: biến chứa một class token có kiểu thành phần chưa biết. Nó không biến token thành `Class<List<String>>`.

## 2. Vì sao có `String.class` và `List.class` nhưng không có `List<String>.class`?

Một hiểu nhầm phổ biến là: vì mã Java cho phép khai báo `List<String>` nên chắc phải có biểu thức `List<String>.class`. Thực tế:

```java
String.class             // Hợp lệ
java.util.List.class     // Hợp lệ

// Những biểu thức sau KHÔNG HỢP LỆ trong Java:
// java.util.List<String>.class
// java.util.List<?>.class
```

**`List<String>.class` không tồn tại trong cú pháp class literal của Java.** Sau type erasure, `List<String>` và `List<Integer>` không có hai lớp runtime riêng. Chúng đều sử dụng cùng kiểu runtime được biểu diễn bởi `java.util.List.class`.

Bảng đối chiếu:

| Biểu thức / kiểu | Có class literal? | Ý nghĩa |
| --- | --- | --- |
| `String.class` | Có | Token của lớp String. |
| `List.class` | Có | Token của kiểu `List`, không lưu đối số `String` hay `Integer`. |
| `List<String>.class` | Không | Java không cung cấp class literal cho tham số hóa cụ thể này. |
| `List<?>.class` | Không | `List<?>` có tính chất reifiable, nhưng cú pháp class literal không cho viết trực tiếp dạng tham số hóa này. |
| `String[].class` | Có | Token của kiểu mảng có thành phần String. |
| `int.class` | Có | Token của kiểu nguyên thủy `int`. |
| `T.class`, với `T` là type variable | Không | Tham số kiểu T không được hiện thực thành một class token tách biệt. |

**Không đánh đồng “reifiable” với “viết được `.class` ở mọi dạng cú pháp”.** `List<?>` là một reifiable type theo phân loại của Java vì các đối số kiểu đều là wildcard không giới hạn; phép `instanceof List<?>` là hợp lệ. Nhưng biểu thức `List<?>.class` vẫn không hợp lệ về cú pháp. Khi cần class token của lớp chứa, ta viết `List.class`.

### Kiểu reifiable liên quan gì ở đây?

Một kiểu được gọi là *reifiable* nếu thông tin kiểu của nó có đủ biểu diễn để có thể quan sát/kiểm tra thích hợp tại runtime. Ví dụ:

- Kiểu nguyên thủy, như `int`.
- Kiểu class/interface không generic, như `String`.
- Raw type, như `List`.
- Một kiểu generic với **mọi** đối số là wildcard không giới hạn, như `List<?>`.
- Mảng có thành phần thuộc kiểu reifiable, như `String[]`.

`List<String>` **không** phải reifiable type: JVM không giữ và kiểm tra đầy đủ đối số `String` trong một đối tượng `List` thông thường. Vì thế không thể áp dụng cùng kiểu kiểm tra runtime của `String.class` cho toàn bộ `List<String>` thông qua một class literal.

## 3. Hiểu sâu hơn về type erasure

Giả sử có hai khai báo:

```java
List<String> words = new ArrayList<>();
List<Integer> numbers = new ArrayList<>();
```

Ở compile time, compiler xem `words` và `numbers` là hai tham số hóa khác nhau, từ đó từ chối `words.add(42)` hoặc `numbers.add("A")`. Nhưng kiểu `ArrayList` thực hiện việc lưu đối tượng theo cấu trúc có thể dùng chung cho nhiều đối số kiểu. Thông tin `String` hoặc `Integer` không làm nảy sinh hai lớp `ArrayList<String>` và `ArrayList<Integer>` độc lập ở JVM.

Sơ đồ khái niệm:

```text
Mã nguồn:
    List<String> words        List<Integer> numbers
           |                           |
           | kiểm tra generic          | kiểm tra generic
           v                           v
      ArrayList<String>          ArrayList<Integer>
           \                           /
            \       type erasure      /
             v                       v
             cùng lớp java.util.ArrayList tại runtime
                         |
                         v
                  ArrayList.class
```

Đối với một đối tượng `List<String>`, các phương thức thông thường như `get(int)` không trả về một `String` có thể suy luận độc lập từ đối tượng `Class` của danh sách. Descriptor của `List.get` trả về `Object` sau erasure; trình biên dịch chèn các phép cast thích hợp ở những vị trí chương trình cần `String`.

**Hệ quả:** `list.getClass()` trả về class runtime của implementation, ví dụ một lớp `ArrayList` hoặc implementation khác. Nó không biến thành một `Class<List<String>>`. Ngay cả khi `getClass()` cho bạn biết đối tượng là `ArrayList`, class token đó không tiết lộ từng phần tử hiện có có phải `String` hay không.

Điều này cũng giải thích tại sao:

```java
Object value = List.of("A", "B");
System.out.println(value instanceof List<?>);
```

là hợp lệ và in `true`. Runtime có thể kiểm tra đối tượng triển khai `List`. Nó **không kiểm tra** bên trong danh sách chỉ có String. Nếu đổi danh sách thành `List.of(1, 2)`, phép kiểm tra `instanceof List<?>` vẫn là `true`.

Không thể viết một phép kiểm tra chung `value instanceof List<String>` để xác minh mọi phần tử String. Với các trường hợp yêu cầu kiểm tra phần tử, hãy duyệt và xác thực các đối tượng thực tế.

## 4. `Class<T>` và `Class<?>` dùng vào việc gì?

`java.lang.Class<T>` là một API generic mà tham số `T` diễn đạt lớp/kiểu được mô tả. Hai ví dụ quen thuộc:

```java
Class<String> stringClass = String.class;
Class<Integer> integerClass = Integer.class;
```

Với `Class<T>`, Java cung cấp các thao tác tiện lợi:

- `boolean isInstance(Object obj)`: cho biết đối tượng có là instance hợp lệ của kiểu runtime do token đại diện hay không.
- `T cast(Object obj)`: kiểm tra/cast theo class token rồi trả về giá trị được gán kiểu T tại compile time; có thể ném `ClassCastException`.
- `boolean isAssignableFrom(Class<?> cls)`: xem class do `cls` mô tả có thể gán cho class/interface đang xét hay không, theo quan hệ kiểu runtime.
- `getName()`, `getDeclaredFields()`, `getDeclaredMethods()` và nhiều phương thức reflection khác.

Ví dụ minh họa có thể đặt trong phương thức:

```java
Class<String> textType = String.class;
System.out.println(textType.isInstance("hello")); // true
System.out.println(textType.isInstance(123));     // false
String value = textType.cast("hello");            // Hợp lệ
// String invalid = textType.cast(123);           // ClassCastException
```

`Class.cast` không phải phép chuyển đổi giá trị. Nó không thể chuyển số 123 thành chuỗi `"123"`. Nó kiểm tra đối tượng thực tế có khớp kiểu runtime không. Với giá trị `null`, `Class.cast(null)` trả về `null`, trong khi `isInstance(null)` trả về `false`.

Nếu không biết trước lớp nào được dùng, `Class<?>` là lựa chọn phù hợp:

```java
Class<?> runtimeType = List.class;
System.out.println(runtimeType.isInstance(List.of(1, 2))); // true
```

Vì token đại diện cho kiểu `List`, nó chỉ xác định rằng giá trị là một List. Nó không nói những phần tử của list đó thuộc `String`, `Integer` hay một kiểu khác.

### Tại sao không dùng một `Class<List<String>>` tùy ý?

`Class<List<String>>` là một kiểu tham số hóa có thể được viết trong **khai báo kiểu Java**, nhưng bạn không thể lấy giá trị này từ `List<String>.class` vì biểu thức đó không tồn tại. Việc ép từ `List.class` qua unchecked cast để “giả” ra `Class<List<String>>` không thêm thông tin phần tử vào class token:

```java
// KHÔNG NÊN DÙNG để chứng minh phần tử là String.
// @SuppressWarnings("unchecked")
// Class<List<String>> pretend =
//         (Class<List<String>>) (Class<?>) List.class;
```

Đoạn minh họa trên được chú thích để không gây cảnh báo hay ngụ ý rằng nó bảo đảm an toàn. Ở runtime, token vẫn là `List.class` và không có cơ chế tự kiểm tra sâu `List<String>`.

## 5. Chương trình Java 17 hoàn chỉnh

Ví dụ sau trình bày các phép kiểm tra bằng `Class<?>`, đọc khai báo field bằng reflection, giữ metadata generic bằng type reference và kiểm tra **từng phần tử** trước khi trả về một `List<String>` được xác thực.

**Tệp `ClassLiteralDemo.java`:**

```java
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ClassLiteralDemo {
    static final class PersonData {
        List<String> names = List.of("An", "Binh");
    }

    static final class GenericBox<T> {
        List<T> items;
    }

    abstract static class TypeRef<T> {
        private final Type captured;

        protected TypeRef() {
            Type parent = getClass().getGenericSuperclass();
            if (!(parent instanceof ParameterizedType parameterized)) {
                throw new IllegalStateException("Missing type argument");
            }
            this.captured = parameterized.getActualTypeArguments()[0];
        }

        Type type() {
            return captured;
        }
    }

    static <T> T castKnown(Class<T> type, Object value) {
        return type.cast(value);
    }

    static <E> List<E> checkedCopy(List<?> values, Class<E> elementType) {
        List<E> result = new ArrayList<>();
        for (Object value : values) {
            result.add(elementType.cast(value));
        }
        return List.copyOf(result);
    }

    public static void main(String[] args) throws ReflectiveOperationException {
        Class<String> textClass = String.class;
        Class<?> listClass = List.class;
        System.out.println("textClass=" + textClass.getName());
        System.out.println("listClass=" + listClass.getName());
        System.out.println("isList=" + listClass.isInstance(List.of("A")));
        System.out.println("isString=" + textClass.isInstance(42));

        Field field = PersonData.class.getDeclaredField("names");
        System.out.println("rawField=" + field.getType().getTypeName());
        System.out.println("genericField=" + field.getGenericType().getTypeName());

        Field variableField = GenericBox.class.getDeclaredField("items");
        System.out.println("genericVariable=" + variableField.getGenericType().getTypeName());

        Type captured = new TypeRef<List<String>>() {}.type();
        System.out.println("captured=" + captured.getTypeName());

        List<?> mixed = List.of("A", 42);
        System.out.println("listTest=" + List.class.isInstance(mixed));
        try {
            checkedCopy(mixed, String.class);
        } catch (ClassCastException exception) {
            System.out.println("badElement=" + exception.getClass().getSimpleName());
        }

        System.out.println("validated="
                + checkedCopy(List.of("A", "B"), String.class));
        System.out.println("castKnown="
                + castKnown(String.class, "hello").toUpperCase(Locale.ROOT));
        System.out.println("arrayToken=" + String[].class.getTypeName());
    }
}
```

**Biên dịch và chạy** với JDK 17 trở lên:

```bash
javac --release 17 ClassLiteralDemo.java
java ClassLiteralDemo
```

**Stdout đã được kiểm chứng:**

```text
textClass=java.lang.String
listClass=java.util.List
isList=true
isString=false
rawField=java.util.List
genericField=java.util.List<java.lang.String>
genericVariable=java.util.List<T>
captured=java.util.List<java.lang.String>
listTest=true
badElement=ClassCastException
validated=[A, B]
castKnown=HELLO
arrayToken=java.lang.String[]
```

Phân tích các phần chính:

1. `textClass` là `Class<String>`, còn `listClass` là `Class<?>` trỏ tới `List.class`. Mỗi token cho biết một kiểu runtime, không tạo đối tượng dữ liệu.
2. `List.class.isInstance(List.of("A"))` cho `true`, còn `String.class.isInstance(42)` cho `false`.
3. `field.getType()` của `PersonData.names` chỉ trả về class/interface raw `List`. `field.getGenericType()` đọc **khai báo field** và cho `List<String>` dưới dạng `Type`.
4. `GenericBox<T>.items` được khai báo với `T` chưa được thay bằng loại cụ thể trong metadata của lớp generic gốc; bởi vậy `getGenericType()` phản ánh `List<T>`.
5. `TypeRef<List<String>>` giữ lại một khai báo generic cụ thể thông qua lớp con ẩn danh. Kết quả `Type` in ra `List<String>`.
6. `List.class.isInstance(mixed)` chỉ nhìn cấu trúc ngoài, nên vẫn là `true` khi danh sách chứa một `Integer`.
7. `checkedCopy` kiểm tra từng đối tượng bằng `elementType.cast(value)`. Đến `Integer`, nó ném `ClassCastException`. Với danh sách chỉ chứa String, nó tạo bản sao không sửa được.
8. `String[].class` là token của mảng String, và `getTypeName()` cho tên kiểu mảng thân thiện `java.lang.String[]`.

### Phân biệt hai loại thông tin đang được hiển thị

Hai dòng:

```text
rawField=java.util.List
genericField=java.util.List<java.lang.String>
```

**không mâu thuẫn**. `getType()` truy vấn kiểu class/interface runtime của field, còn `getGenericType()` trả về mô tả kiểu **đã được khai báo ở nguồn và lưu trong class-file metadata**. Metadata có ích cho framework và công cụ phân tích, nhưng không phải phép chứng minh rằng **mọi giá trị hiện nằm trong field** đã được kiểm tra là `String`.

## 6. Những dòng cố ý không biên dịch

Ví dụ sau là **mã lỗi có chủ đích**, không thêm vào chương trình chạy ở phần 5.

**Tệp `InvalidClassLiterals.java`:**

```java
import java.util.List;

class InvalidClassLiterals {
    Class<List<String>> token = List<String>.class;
}
```

Biên dịch:

```bash
javac --release 17 InvalidClassLiterals.java
```

`javac` từ chối ở `List<String>.class`; trong bản kiểm chứng Java 17 compiler mode, lỗi cú pháp có phần chính:

```text
error: <identifier> expected
```

Thông điệp và dấu mũ đánh dấu ký tự có thể khác theo compiler; nguyên nhân không đổi là class literal không nhận dạng `List<String>` theo cách viết này.

**Tệp `InvalidGenericToken.java`:**

```java
class InvalidGenericToken {
    <T> Class<T> token() {
        return T.class;
    }
}
```

Biên dịch:

```bash
javac --release 17 InvalidGenericToken.java
```

Lỗi trọng tâm đã kiểm chứng:

```text
error: cannot select from a type variable
```

`T` là biến kiểu ở compile time, không phải một lớp runtime có class literal riêng. Cách sửa là nhận token từ caller:

```java
static <T> Class<T> token(Class<T> type) {
    return type;
}
```

Đây là khai báo phương thức minh họa, đặt trong một lớp. Caller có thể gọi `token(String.class)` và nhận lại `Class<String>`. Giá trị này xác định lớp String, nhưng `Class<T>` vẫn không biểu diễn đầy đủ `List<String>`.

**Tệp `InvalidWildcardLiteral.java`:**

```java
import java.util.List;

class InvalidWildcardLiteral {
    Class<?> token = List<?>.class;
}
```

Đoạn này cũng **không biên dịch** với `javac --release 17`. Mặc dù `List<?>` là một *reifiable type*, cú pháp `List<?>.class` không được phép. Nếu cần token, dùng `List.class`; nếu cần kiểm tra một đối tượng là List mà không yêu cầu phần tử cụ thể, có thể dùng `obj instanceof List<?>`.

## 7. Vì sao `instanceof List<?>` hợp lệ mà `List<String>.class` không?

`instanceof` hỏi liệu **đối tượng** có thuộc một kiểu mà runtime có thể kiểm tra được hay không.

```java
Object candidate = List.of("A");
boolean listLike = candidate instanceof List<?>;
System.out.println(listLike);
```

Kết quả:

```text
true
```

`List<?>` không yêu cầu runtime phân biệt loại phần tử. Vì vậy phép kiểm tra dạng wildcard không giới hạn có thể thực hiện bằng thông tin kiểu `List`.

Ngược lại:

```java
// Không phải một phép kiểm tra generic runtime hợp lệ:
// boolean stringsOnly = candidate instanceof List<String>;
```

Nhìn chung, kiểu `List<String>` không reifiable và runtime không thể chứng minh mọi phần tử có kiểu String chỉ bằng `instanceof`. Quy tắc compiler cho `instanceof` còn có một số trường hợp đặc biệt liên quan đến kiểu tĩnh của toán hạng và phép chuyển đổi cast; **không nên** dựa vào những trường hợp đặc biệt ấy để suy rằng JVM kiểm tra được đối số generic.

Để xác thực một danh sách String nhận từ nguồn chưa rõ kiểu, hãy kiểm tra hai tầng:

1. Kiểm tra hoặc yêu cầu đối tượng ngoài là `List<?>`.
2. Duyệt phần tử và kiểm tra mỗi giá trị với `String.class.isInstance` hoặc `String.class.cast` theo hợp đồng, rồi đưa vào cấu trúc typed được kiểm soát.

Mẫu này là chức năng của `checkedCopy` trong chương trình phần 5. Nó tạo ra bảo đảm dựa trên **dữ liệu đã duyệt**, không phụ thuộc vào việc cố tạo một `Class<List<String>>` giả.

## 8. `Class`, `Type` và `ParameterizedType` khác nhau thế nào?

Reflection của Java dùng nhiều abstraction vì một `Class<?>` không đủ biểu diễn tất cả kiểu ở mã nguồn.

| Khái niệm | Biểu diễn | Biết được gì? | Giới hạn |
| --- | --- | --- | --- |
| `Class<?>` | Class token, ví dụ `List.class` | Danh tính kiểu runtime, superclass, interface, phương thức, field... | Không lưu đối số `String` cho từng List object. |
| `Type` | Interface chung trong `java.lang.reflect` | Mô tả nhiều loại kiểu reflective | Không phải API kiểm tra phần tử runtime. |
| `ParameterizedType` | Ví dụ type mô tả `List<String>` | Raw type, các đối số kiểu trong một khai báo generic | Metadata khai báo không bảo đảm dữ liệu thực đúng kiểu. |
| `TypeVariable<?>` | Ví dụ `T` trong `GenericBox<T>` | Tên biến kiểu và các bound khai báo | Không tự trở thành Class<String> khi có đối tượng GenericBox<String>. |
| `WildcardType` | Ví dụ `? extends Number` | Giới hạn trên/dưới của wildcard khai báo | Không thay thế kiểm tra dữ liệu. |
| `GenericArrayType` | Ví dụ mảng có thành phần là biến kiểu | Kiểu thành phần generic trong khai báo | Không nhất thiết là `Class<?>` của mảng reifiable. |

Ví dụ đọc kiểu từ field:

```java
Field field = PersonData.class.getDeclaredField("names");
Type declared = field.getGenericType();

if (declared instanceof ParameterizedType parameterized) {
    System.out.println(parameterized.getRawType());
    System.out.println(parameterized.getActualTypeArguments()[0]);
}
```

Đây là đoạn đặt trong `ClassLiteralDemo.main`, nơi `PersonData` đã được định nghĩa và `Field`/`Type`/`ParameterizedType` đã được import. Kết quả trong chương trình minh họa tương ứng là raw type `interface java.util.List` và đối số `class java.lang.String` khi in `Type.toString()`.

**Khi nào xuất hiện metadata generic?** Trong class file, compiler có thể lưu `Signature` attribute mô tả tham số generic của field, method, superclass hoặc interface. Reflection đọc phần metadata đó, khác với việc xét class token runtime của một đối tượng cụ thể.

Ví dụ:

```java
class Holder<T> {
    List<T> items;
}
```

Nếu bạn tạo `new Holder<String>()`, runtime vẫn dùng cùng `Holder.class` với các instantiation tham số kiểu khác. Metadata trong khai báo `Holder.items` là `List<T>`, không tự hóa thành `List<String>` trên từng instance. Muốn biết T gắn với `String` trong một type hierarchy, cần có thông tin về lớp con generic đã chuyên biệt hóa, hoặc caller cung cấp một `Type`/token mô tả sự chuyên biệt hóa.

## 9. Type reference lưu được `List<String>` ở đâu?

Một mẫu kỹ thuật thường gặp trong thư viện JSON và reflection là **type reference** (đôi khi được gọi *super type token*): tạo một subclass ẩn danh của một lớp generic rồi đọc generic superclass đã khai báo.

Mẫu từ phần 5:

```java
Type captured = new TypeRef<List<String>>() {}.type();
System.out.println(captured.getTypeName());
```

Kết quả:

```text
java.util.List<java.lang.String>
```

Lý do cách này hoạt động:

1. `new TypeRef<List<String>>() {}` tạo một lớp con ẩn danh với superclass được khai báo có đối số `List<String>`.
2. Thông tin tham số hóa của superclass được lưu trong metadata class file của lớp ẩn danh đó.
3. `getClass().getGenericSuperclass()` đọc một `ParameterizedType` đại diện cho `TypeRef<List<String>>`.
4. `getActualTypeArguments()[0]` lấy `Type` đại diện cho `List<String>`.

Đây là **thông tin về khai báo generic của class**, không phải cơ chế JVM tạo class runtime mới cho `List<String>`. Nó không tự xác minh một danh sách đang chứa các chuỗi hay cho phép `List<String>.class`.

Trong ứng dụng dùng JSON serializer/deserializer, thư viện có thể nhận một `Type` như vậy để hướng dẫn cách đọc từng phần tử. Khi đó **thư viện** đảm nhận việc đọc và chuyển dữ liệu theo kiểu được mô tả; việc type reference tồn tại không khiến mọi danh sách bất kỳ tự đúng kiểu.

Không thể viết một implementation type reference ngây thơ rồi kỳ vọng lúc nào cũng biết tham số kiểu T. Nếu lớp con không cung cấp đối số kiểu cụ thể trong generic superclass, hoặc cấu trúc kế thừa phức tạp hơn giả định của ví dụ, việc lấy metadata cần logic bổ sung. Ở đây `TypeRef` chủ động kiểm tra `ParameterizedType` để báo lỗi nếu thiếu thông tin.

## 10. `Class<T>` hữu ích trong API generic, nhưng có giới hạn

Một API cần kiểm tra đối tượng thuộc loại đơn giản có thể yêu cầu token từ người gọi:

```java
static <T> T checkedCast(Class<T> type, Object value) {
    return type.cast(value);
}
```

Caller:

```java
String name = checkedCast(String.class, "An");
// String invalid = checkedCast(String.class, 42); // ClassCastException.
```

Tương tự, có thể nhận class token để tạo mảng đúng kiểu phần tử bằng `Array.newInstance`:

```java
@SuppressWarnings("unchecked")
static <T> T[] newArray(Class<T> componentType, int length) {
    return (T[]) java.lang.reflect.Array.newInstance(componentType, length);
}
```

Đây là phương thức minh họa độc lập. Runtime kiểm tra đúng loại phần tử mảng, nên `String[]` thực sự có thành phần String. Cast generic dùng trong implementation được giới hạn vào kết quả `Array.newInstance` với token caller cung cấp.

Tuy nhiên, đối với `List<String>`, một `Class<T>` duy nhất như `List.class` chỉ cho biết loại **container**. Không thể đưa `String.class` vào `List.class` để nó biến thành class literal của `List<String>`. Nếu cần bảo đảm sâu, chữ ký API nên nhận riêng:

```java
static <E> List<E> checkedCopy(
        List<?> input, Class<E> elementType) {
    List<E> result = new ArrayList<>();
    for (Object element : input) {
        result.add(elementType.cast(element));
    }
    return List.copyOf(result);
}
```

Đoạn này là phần rút từ chương trình hoàn chỉnh phần 5. Nó kiểm tra từng phần tử và trả về một danh sách mới. `List.copyOf` không cho phần tử `null`, vì vậy nếu hợp đồng cần hỗ trợ null phải thiết kế biến thể thích hợp.

### `Class.isInstance` có thể kiểm tra gì?

- `String.class.isInstance("A")` → `true`.
- `String.class.isInstance(1)` → `false`.
- `List.class.isInstance(List.of("A"))` → `true`.
- `List.class.isInstance(List.of(1))` → **cũng là `true`**.
- `String[].class.isInstance(new String[0])` → `true`.
- `List.class.isInstance(null)` → `false`.

Trường hợp `List.class` minh họa giới hạn quan trọng nhất của bài: class token đại diện loại container, không biểu diễn điều kiện về tất cả phần tử generic.

## 11. Mảng và generic: điểm tương phản hữu ích

Mảng Java giữ loại phần tử runtime. Ví dụ:

```java
String[] words = new String[2];
Object[] objects = words;
objects[0] = "OK";
// objects[1] = Integer.valueOf(1); // ArrayStoreException lúc chạy.
```

Nếu bỏ chú thích ở dòng cuối, JVM kiểm tra loại mảng String[] tại runtime và ném `ArrayStoreException` khi chèn Integer. Mảng là *covariant*, vì `String[]` gán được cho `Object[]`; việc kiểm tra runtime bù lại nguy cơ ghi phần tử sai kiểu.

Ngược lại, generic collections như `List<String>` và `List<Integer>` là **invariant** và phần lớn được triển khai bằng erasure, không có cơ chế tương đương để tự kiểm tra phần tử String của một raw `ArrayList` khi thêm qua alias raw.

Một số quy tắc liên quan:

```java
String[].class             // Hợp lệ.
java.util.List[].class     // Hợp lệ, dùng raw List ở thành phần mảng.

// java.util.List<String>[].class  // Không phải class literal hợp lệ.
// new java.util.List<String>[5]   // Không được phép tạo generic array kiểu này.
```

Khi cần danh sách có phần tử String, hãy dùng `List<String>`; khi cần mảng String, dùng `String[]`. Không cố ép `Class<List<String>>` từ `List[].class`: token mảng chỉ mô tả mảng các List ở mức erasure.

## 12. Những hiểu nhầm thường gặp

| Nhận định | Vì sao chưa đúng | Cách hiểu đúng |
| --- | --- | --- |
| “`List<String>.class` chỉ là cách viết dài của `List.class`.” | Biểu thức đầu **không hợp lệ** về cú pháp Java. | Sử dụng `List.class` cho kiểu raw runtime, và `Type` để mô tả khai báo tham số hóa. |
| “`List.class` phân biệt List String với List Integer.” | Không có đối số generic trong class token. | `List.class` nhận diện loại container. |
| “`List<?>` là raw List.” | Wildcard vẫn duy trì kiểm tra generic đối với phép ghi. | `List<?>` là typed view với phần tử chưa biết; `List` là raw type. |
| “`List<?>.class` phải hợp lệ vì List<?> reifiable.” | Reifiable không đồng nghĩa mọi cách viết đều được class-literal grammar cho phép. | Viết `List.class`; dùng `instanceof List<?>` khi muốn kiểm tra kiểu ngoài. |
| “`T.class` dùng được trong method generic.” | T không có class token tách biệt sau erasure. | Nhận `Class<T>` từ caller hoặc dùng mô hình `Type` phù hợp. |
| “Reflection thấy `List<String>` nghĩa là List đó đã được kiểm tra sâu.” | Reflection đọc metadata khai báo, không duyệt nội dung đối tượng. | Xác thực từng phần tử tại ranh giới dữ liệu. |
| “Ép `Class<?>` thành `Class<List<String>>` sẽ tăng an toàn.” | Cast không thêm thông tin runtime. | Tránh unchecked cast; dùng `Type` và validator khi cần. |
| “TypeRef biến `List<String>` thành class runtime mới.” | TypeRef lưu metadata của subclass khai báo generic. | Dùng nó để truyền mô tả kiểu cho thư viện hiểu `Type`, không phải thay `Class`. |
| “`Class.cast` có thể chuyển Integer thành String.” | Cast kiểm tra khả năng gán, không chuyển giá trị. | Nếu cần chuyển đổi, dùng quy tắc chuyển đổi rõ ràng riêng. |

## 13. Quy trình chọn công cụ đúng

**Trường hợp A — cần kiểm tra đối tượng thuộc lớp cụ thể:** dùng `String.class`, `Class<T>.isInstance` hoặc `Class<T>.cast`.

**Trường hợp B — cần kiểm tra một đối tượng có phải collection:** dùng `obj instanceof List<?>` hoặc `List.class.isInstance(obj)`.

**Trường hợp C — cần xác nhận các phần tử của một collection là String:** nhận `List<?>` rồi kiểm tra từng phần tử, copy sang `List<String>` đã được xác thực. Nếu dữ liệu không tin cậy, xác định chính sách đối với null, subtype và giá trị sai.

**Trường hợp D — cần đọc generic được viết trên field/method/superclass:** dùng `getGenericType()`, `getGenericReturnType()`, `getGenericParameterTypes()` hoặc `getGenericSuperclass()` và làm việc với `Type`/`ParameterizedType` khi phù hợp.

**Trường hợp E — thư viện cần mô tả `List<String>` để deserialize JSON:** truyền `Type`/type reference theo API của thư viện; việc xác thực/chuyển đổi phần tử thuộc về quá trình xử lý dữ liệu của thư viện.

**Trường hợp F — generic method cần tạo/check đối tượng kiểu T:** cho caller truyền `Class<T>` nếu T là class token có thể đại diện; không dùng `T.class`.

Lưu ý về thiết kế: đừng đưa quá nhiều tham số `Class<?>` vào public API chỉ vì generic của Java không reified. Hãy xác định rõ yêu cầu là **kiểm tra một lớp**, **mô tả kiểu generic** hay **xác thực dữ liệu thực** rồi chọn abstraction thích hợp.

## 14. Bài tập tự kiểm tra

**Bài 1.** `List<String>.class` và `List<Integer>.class` có trả về hai class token khác nhau không?

**Đáp án:** Không có biểu thức nào hợp lệ. Cả hai loại danh sách được mô tả ở mức container bằng `List.class`; đối số generic khác nhau được kiểm tra ở compile time và có thể tồn tại trong metadata khai báo.

**Bài 2.** Vì sao đoạn sau không thể xác nhận một danh sách chứa toàn String?

```java
Object data = List.of(1, 2);
System.out.println(List.class.isInstance(data));
```

**Đáp án:** Nó chỉ kiểm tra đối tượng triển khai `List`, nên in `true`. Không có kiểm tra phần tử bằng `String.class`.

**Bài 3.** `Field.getType()` và `Field.getGenericType()` khác nhau ra sao khi field khai báo `List<String>`?

**Đáp án:** `getType()` trả về class/interface `List`; `getGenericType()` trả về một `Type` có thể là `ParameterizedType` lưu mô tả `List<String>` từ khai báo.

**Bài 4.** Tại sao `T.class` không dùng được trong `<T> void process(T value)`?

**Đáp án:** Type variable T không được reify thành class token riêng. Có thể viết `<T> void process(T value, Class<T> type)` khi API cần token cụ thể do caller cung cấp.

**Bài 5.** `List<?>.class` và `obj instanceof List<?>` có cùng tính hợp lệ không?

**Đáp án:** Không. `List<?>.class` không hợp lệ; `instanceof List<?>` hợp lệ để kiểm tra đối tượng thuộc loại List mà không yêu cầu phần tử cụ thể.

**Bài 6.** `new TypeRef<List<String>>() {}.type()` có chứng minh mọi phần tử của một danh sách nhập từ network là String không?

**Đáp án:** Không. Nó lưu mô tả generic trong metadata của một subclass. Muốn chắc chắn dữ liệu đúng, chương trình hoặc thư viện deserializer vẫn phải kiểm tra và xử lý từng phần tử.

**Bài 7.** Tại sao `String[].class` hợp lệ trong khi `List<String>.class` không hợp lệ?

**Đáp án:** Mảng String có kiểu thành phần String được lưu tại runtime; một tham số hóa `List<String>` thông thường bị erasure và không có class token riêng cho đối số String.

## 15. Ghi nhớ

- `String.class` là `Class<String>`, biểu diễn kiểu String.
- `List.class` biểu diễn kiểu `List` ở runtime, **không** biểu diễn `List<String>`.
- `List<String>.class`, `List<?>.class` và `T.class` đều không phải class literal hợp lệ theo cách viết đó.
- `List<?>` là một kiểu dùng được trong chương trình và `instanceof List<?>` có thể kiểm tra loại container, nhưng **không kiểm tra kiểu phần tử**.
- `Type` và `ParameterizedType` giúp đọc mô tả generic từ **khai báo**, không thay thế xác thực dữ liệu thực tế.
- Khi cần một danh sách được tin cậy là `List<String>` từ nguồn chưa rõ kiểu, hãy dùng `Class<String>` để kiểm tra **từng phần tử**, sau đó giữ kết quả dưới API generic có kiểu rõ ràng.

Ranh giới quan trọng nhất là: **class literal mô tả lớp runtime; generic type argument có thể là thông tin ở compile time hoặc metadata; còn tính đúng kiểu của dữ liệu thực cần được đảm bảo bằng hợp đồng và kiểm tra phù hợp.**


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="runtime-types-005">instanceof với List&lt;?&gt; và các trường hợp kiểu tham số hóa hợp lệ</a>

<details>
<summary>Xem chi tiết</summary>

# instanceof với List<?> và các trường hợp kiểu tham số hóa hợp lệ trong Java Generics

## 1. Vấn đề: tại sao instanceof List<?> hợp lệ nhưng instanceof List<String> thường bị từ chối?

`instanceof` là toán tử Java dùng để hỏi liệu một tham chiếu **không null** có thể được nhìn như một kiểu được chỉ định tại runtime hay không:

~~~java
Object value = List.of("Java", "Kotlin");

System.out.println(value instanceof List);     // true
System.out.println(value instanceof List<?>);  // true

// System.out.println(value instanceof List<String>); // Không biên dịch.
~~~

Khi một đối tượng được tạo từ `ArrayList<String>` hay `List.of("Java")`, JVM vẫn không duy trì toàn bộ type arguments của một đối tượng collection để kiểm tra mọi phần tử. Theo cách Java generics được triển khai bằng **type erasure**, runtime có thể kiểm tra được đối tượng có triển khai `List` hay không, nhưng không thể phân biệt đáng tin cậy `List<String>` với `List<Integer>` chỉ bằng một phép kiểm tra runtime trên đối tượng đó.

`List<?>` là trường hợp đặc biệt hữu ích: đây là **parameterized type với unbounded wildcard**. Nó được coi là **reifiable type** theo quy tắc ngôn ngữ, tức có thể dùng ở vế phải `instanceof` từ một biểu thức kiểu `Object`. Tuy nhiên dấu `?` nghĩa là “kiểu phần tử chưa biết”, **không** có nghĩa “mọi phần tử đã được kiểm tra và hợp lệ với String”.

Bài này giải thích:
- Kiểu nào có thể dùng trong `instanceof`.
- Vì sao `List<?>`, `Map<?, ?>`, `Class<?>` và `List<?>[]` có thể hợp lệ.
- Vì sao `Object instanceof List<String>` không hợp lệ.
- Ngoại lệ quan trọng từ Java 16 trở đi: **một số kiểu tham số hóa cụ thể cũng được phép** khi kiểu tĩnh của biểu thức bên trái bảo đảm có casting conversion phù hợp.
- Pattern matching cho `instanceof` trong Java 17.
- Cách **kiểm tra từng phần tử** nếu nghiệp vụ thực sự cần `List<String>`, kể cả cấu trúc lồng nhau.

## 2. Type erasure và giới hạn kiểm tra runtime

Giả sử hai biến:

~~~java
List<String> words = new ArrayList<>();
List<Integer> numbers = new ArrayList<>();
~~~

Ở compile time, `List<String>` và `List<Integer>` là hai kiểu tham số hóa khác nhau. Compiler ngăn những lời gọi như `words.add(123)`; nhờ thế code thông thường dùng đúng generic không cần tự cast khi đọc.

Nhưng các đối tượng `ArrayList` của hai biến không phải các lớp runtime chuyên biệt hoàn toàn khác nhau theo T. Với những khai báo trên:

~~~java
System.out.println(words.getClass() == numbers.getClass()); // true
~~~

Cùng lớp runtime không đồng nghĩa cùng kiểu phần tử ở compile time. JVM không có cơ chế `instanceof List<String>` tổng quát để duyệt toàn bộ phần tử và chứng minh chúng là String.

**Hai câu hỏi rất khác nhau:**

1. “Đối tượng có phải một `List` không?” — `instanceof List<?>` kiểm tra được.
2. “Mỗi phần tử trong List có đúng là String không?” — cần kiểm tra phần tử hoặc dựa vào một API có bất biến kiểu đáng tin.

Ví dụ:

~~~java
Object mixed = List.of("Java", 7);
boolean isList = mixed instanceof List<?>; // true.
~~~

`mixed` chứa cả String và Integer nhưng vẫn là một List, vì vậy kết quả `true` là chính xác. Nếu code chỉ nhìn vào `isList` mà ép toàn bộ dữ liệu thành `List<String>`, nó đã đưa ra một kết luận không được phép kiểm tra hỗ trợ.

## 3. Reifiable type nghĩa là gì trong ngữ cảnh này?

Trong Java Language Specification, một kiểu **reifiable** là kiểu có đủ biểu diễn cần thiết ở runtime theo các quy tắc của ngôn ngữ. Những nhóm quan trọng khi học `instanceof`:

- Kiểu nguyên thủy (không phải kiểu tham chiếu ở RHS `instanceof` trong Java 17; không viết `value instanceof int`).
- Class/interface không generic như `String`, `Number` hoặc `Runnable`.
- Raw type của một generic class/interface như `List`, `Map` hoặc `ArrayList`.
- Parameterized type mà **tất cả các type arguments đều là unbounded wildcard**, như `List<?>` và `Map<?, ?>`.
- Array có element type reifiable, như `String[]` hoặc `List<?>[]`.

Đối với toán tử `instanceof` trên một tham chiếu `Object`, các kiểu thực dụng ở RHS thường gặp là:

~~~java
Object input = List.of("A");

boolean a = input instanceof List;             // Hợp lệ: raw List.
boolean b = input instanceof List<?>;          // Hợp lệ: wildcard không giới hạn.
boolean c = input instanceof ArrayList<?>;     // Hợp lệ về cú pháp; kết quả tùy runtime class.
boolean d = input instanceof Map<?, ?>;        // Hợp lệ: cả hai đối số wildcard.
boolean e = input instanceof Class<?>;         // Hợp lệ: Class<T> với wildcard.
boolean f = input instanceof List<?>[];        // Hợp lệ: array của reifiable component type.
~~~

Đây là các **phép kiểm tra hợp lệ về mặt biên dịch**, không có nghĩa tất cả trả `true`. Với `input` là List.of("A"), kết quả `input instanceof ArrayList<?>` thường là false, vì API List.of không hứa trả ArrayList cụ thể. `input instanceof Map<?, ?>` và `input instanceof List<?>[]` cũng false.

`List<?>` là parameterized type, **không phải raw type**. Nếu chỉ cần biết đối tượng là List rồi đọc phần tử dưới dạng Object, `List<?>` thường truyền đạt ý định tốt hơn `List` raw.

### Những kiểu tham số hóa không reifiable theo nghĩa thông thường

Ví dụ:

~~~java
List<String>
List<Integer>
Map<String, Integer>
List<? extends Number>
Map<String, List<Integer>>
~~~

Các kiểu này chứa một hoặc nhiều type arguments cụ thể hoặc wildcard có cận khác unbounded wildcard. Chúng không cung cấp một bài kiểm tra runtime tổng quát qua `Object`. Bởi vậy các đoạn dưới là **cố ý không biên dịch trên Java 17**:

~~~java
Object value = List.of("Java");

// boolean a = value instanceof List<String>;
// boolean b = value instanceof Map<String, Integer>;
// boolean c = value instanceof List<? extends Number>;
~~~

Không được sửa bằng cách ép `(List<String>) value` trước khi kiểm tra, vì unchecked cast không xác nhận toàn bộ phần tử; nó chỉ chuyển rủi ro thành ngoại lệ ở nơi dùng phần tử.

## 4. Bảng phân biệt các RHS của instanceof

| Kiểm tra (giả sử `Object value`) | Hợp lệ Java 17? | Thực tế kiểm tra |
| --- | --- | --- |
| `value instanceof String` | Có | Object là String hoặc subtype của String (thực tế String là final). |
| `value instanceof List` | Có | Object triển khai List; raw type làm mất biểu đạt generic. |
| `value instanceof List<?>` | Có | Object triển khai List, không biết kiểu phần tử. |
| `value instanceof ArrayList<?>` | Có | Runtime class phù hợp ArrayList, không biết kiểu phần tử. |
| `value instanceof Map<?, ?>` | Có | Object triển khai Map, chưa biết kiểu khóa/giá trị. |
| `value instanceof Class<?>` | Có | Object là đối tượng Class, không biết T. |
| `value instanceof List<?>[]` | Có | Object là array có thành phần runtime phù hợp kiểu List; không kiểm tra phần tử từng List. |
| `value instanceof List<String>` | Không | Không thể xác minh tham số String qua một Object. |
| `value instanceof Map<String, Integer>` | Không | Không thể xác minh key/value generic qua Object. |
| `value instanceof List<? extends Number>` | Không | Không có kiểm tra runtime trực tiếp cho cận generic đó qua Object. |

Bảng trên cố ý giữ **kiểu tĩnh vế trái là Object**. Nếu vế trái đã có kiểu tham số hóa cụ thể, kết quả hợp lệ về cú pháp có thể khác theo các quy tắc `casting conversion`; xem phần tiếp theo.

## 5. Ngoại lệ quan trọng: kiểu tham số hóa cụ thể đôi khi hợp lệ với instanceof

Không nên học một quy tắc tuyệt đối rằng **“`instanceof List<String>` lúc nào cũng bất hợp lệ”**.

Từ **Java 16**, hạn chế cũ buộc kiểu RHS của toán tử `instanceof` luôn phải reifiable đã được nới lỏng khi kiểu nguồn có thể được chuyển sang kiểu đích bằng một **casting conversion** hợp lệ. Điều này đáng chú ý khi vế trái đã có generic type đủ cụ thể.

Ví dụ **biên dịch được bằng Java 17**:

~~~java
List<String> known = new ArrayList<>(List.of("Java"));

boolean sameType = known instanceof List<String>;
boolean concrete = known instanceof ArrayList<String>;
~~~

Trong tình huống này, compiler **đã biết** biến `known` là `List<String>`. Kiểm tra `ArrayList<String>` xem đối tượng runtime có thuộc class ArrayList phù hợp hay không; type argument String đã được bảo đảm ở mức compile-time của tham chiếu nguồn theo hệ thống kiểu thông thường.

Mẫu pattern matching cũng hợp lệ vì nguồn `List<String>` chưa nhất thiết là `ArrayList<String>`:

~~~java
if (known instanceof ArrayList<String> actual) {
    System.out.println(actual.get(0)); // Java.
}
~~~

Điểm cốt lõi:

- Compiler dùng **kiểu tĩnh vế trái** để quyết định có casting conversion thích hợp với kiểu RHS hay không.
- Runtime vẫn chủ yếu kiểm tra raw class/interface của đối tượng.
- Phép kiểm tra **không chứng minh lại type arguments** từ nội dung object. Nếu collection bị nhiễm dữ liệu vì raw/unchecked operation, `instanceof ArrayList<String>` vẫn có thể true trong khi đọc phần tử String gây `ClassCastException`.

### Vì sao Object instanceof List<String> vẫn sai?

~~~java
Object unknown = new ArrayList<String>();

// boolean bad = unknown instanceof List<String>; // Không biên dịch.
boolean good = unknown instanceof List<?>;        // Hợp lệ.
~~~

Dù người viết nhìn thấy initializer chứa String, **kiểu tĩnh của biến unknown là Object**. Compiler không dùng một ký ức về initializer để chứng minh an toàn của phép kiểm tra `List<String>` tại lần dùng biến sau. Nó chỉ có thông tin Object và không thể cho phép kiểm tra tham số String như một test runtime tổng quát.

Tương tự:

~~~java
List<?> unknownElement = new ArrayList<String>();

// boolean bad = unknownElement instanceof ArrayList<String>; // Không biên dịch.
boolean good = unknownElement instanceof ArrayList<?>;        // Hợp lệ.
~~~

Wildcard cho biết phần tử chưa xác định; compiler không thể kết luận type parameter là String chỉ từ kiểu `List<?>`.

**Quy trình nhận diện:** trước khi hỏi “kiểu RHS có hợp lệ với `instanceof` không?”, phải xem **cả kiểu RHS lẫn kiểu tĩnh biểu thức LHS**. Nếu LHS là Object, hãy ưu tiên RHS reifiable; nếu LHS đã có tham số generic cụ thể, Java 16+ có thể chấp nhận RHS tham số hóa tương thích.

## 6. Pattern matching for instanceof trong Java 17

Java 17 hỗ trợ viết:

~~~java
Object input = List.of("A", "B");

if (input instanceof List<?> list) {
    System.out.println(list.size());
    Object first = list.get(0);
}
~~~

Ở đây compiler đồng thời:
1. Kiểm tra input có phải List không.
2. Ràng buộc biến mới `list` trong nhánh mà pattern đúng.
3. Gán kiểu tĩnh `List<?>` cho `list`.

Không cần viết dạng cũ:

~~~java
if (input instanceof List<?>) {
    List<?> list = (List<?>) input;
    System.out.println(list.size());
}
~~~

Cả hai đều hợp lệ, nhưng pattern matching gọn hơn và giới hạn phạm vi biến hợp lý hơn.

### Phạm vi của pattern variable và short-circuit

~~~java
Object input = List.of("A", "B");

if (input instanceof List<?> list && !list.isEmpty()) {
    System.out.println(list.get(0));
}
~~~

`list` chỉ có thể dùng trong nhánh mà pattern match đã thành công, và biểu thức `&&` dùng short-circuit nên đoạn sau được đánh giá khi `list` đã có giá trị hợp lệ.

Không được dùng biến pattern ở nơi compiler không chứng minh được nó đã được khởi tạo:

~~~java
// if (input instanceof List<?> list || list.isEmpty()) { }
// Không biên dịch: ở vế phải của ||, list chưa chắc đã được gán.
~~~

### Pattern đã luôn đúng có thể bị từ chối

Trong Java 17:

~~~java
List<String> already = List.of("Java");

// if (already instanceof List<String> same) { }
// Lỗi Java 17: pattern này là unconditional pattern.
~~~

Lỗi trên có tính chất **khác** với `Object instanceof List<String>`. Kiểu nguồn `List<String>` đã là subtype của kiểu pattern `List<String>`, khiến pattern trở thành dạng không được phép trong `instanceof` của source level 17. Ngược lại, phép kiểm tra biểu thức truyền thống `already instanceof List<String>` có thể hợp lệ khi quy tắc casting conversion thỏa mãn.

Một cách pattern hữu ích là kiểm tra **kiểu con chưa chắc đúng**:

~~~java
List<String> already = new ArrayList<>();

if (already instanceof ArrayList<String> array) {
    System.out.println(array.size());
}
~~~

Tình huống này hợp lệ vì không phải mọi List<String> đều là ArrayList<String>. Lưu ý Java ở các phiên bản sau có thể phát triển quy tắc pattern; bài nhắm tới chương trình Java 17+ và trình bày riêng ràng buộc source level 17.

## 7. List<?> cho phép đọc gì và chặn thao tác ghi nào?

Sau khi thành công với:

~~~java
Object candidate = List.of("Java", 5);

if (candidate instanceof List<?> list) {
    Object first = list.get(0); // Hợp lệ.
    int count = list.size();     // Hợp lệ.

    // list.add("Kotlin");       // Không biên dịch.
}
~~~

Với `List<?>`, compiler biết danh sách có **một kiểu phần tử nào đó**, nhưng không biết kiểu chính xác là gì. Vì thế phần tử đọc được an toàn dưới dạng Object; ngược lại, thêm một String tùy ý có thể phá kiểu phần tử vốn là Integer ở một đối tượng khác.

Đừng nhầm với:

~~~java
List<Object> objects = new ArrayList<>();
objects.add("Java");
objects.add(5);
~~~

`List<Object>` là một parameterized type có E đúng bằng Object, còn `List<?>` chứa một type argument chưa xác định. Do generics có tính invariant, một `List<String>` không thể gán trực tiếp cho `List<Object>` nhưng có thể gán cho `List<?>`:

~~~java
List<String> words = List.of("A");

// List<Object> wrong = words; // Không biên dịch.
List<?> safeView = words;       // Hợp lệ.
~~~

`list.add(null)` có thể hợp lệ về **kiểu tĩnh** đối với wildcard nhưng không đảm bảo thành công: `List.of` không cho sửa đổi, và nhiều collection có chính sách null riêng. Bài này tránh thao tác ghi vào cấu trúc chưa biết type arguments.

## 8. Sau `instanceof List<?>`, cách tạo `List<T>` đã xác minh

Một pattern `candidate instanceof List<?> input` chỉ kiểm tra **container**. Nếu API cần thực sự có `List<String>`, nó phải kiểm tra các phần tử rồi xây kết quả typed mới, không cast unchecked toàn bộ danh sách.

Với class token `Class<T>`: (1) xác nhận đối tượng là `List<?>`; (2) duyệt từng `Object` và yêu cầu `elementType.isInstance(item)`; (3) dùng `elementType.cast(item)` để đưa giá trị hợp lệ vào `ArrayList<T>` mới; (4) nếu dùng `List.copyOf`, công bố rõ chính sách **không nhận null** và không sửa đổi danh sách kết quả.

Ví dụ `checkedList(List.of("A","B"), String.class)` tạo một kết quả String typed, còn danh sách `List.of("A", 1)` phải bị từ chối dù vẫn khớp `instanceof List<?>`. Phương thức `checkedList` được triển khai đầy đủ, chạy độc lập ở chương trình ngay bên dưới. Bài chuyên về xác minh **mọi phần tử** trình bày thêm chân lý rỗng, null, nghiệp vụ và concurrent mutation.

## 9. Generic lồng nhau phải kiểm tra từng tầng

Để nhận `Map<String, List<Integer>>` từ `Object`, `candidate instanceof Map<?, ?>` chỉ là kiểm tra đầu tiên. Cần kiểm tra **mỗi key** là `String`, **mỗi value** là `List<?>`, rồi **mỗi phần tử** của list con là `Integer`. Kết quả typed mới được tạo sau khi các tầng dữ liệu phù hợp; không thể dùng `(Map<String,List<Integer>>) candidate` như một validator.

Chương trình hoàn chỉnh dưới đây cài đặt `checkedScores` và đối chiếu `nestedOdd=[1, 3]`. Việc tạo bản sao tránh chia sẻ cấu trúc collection có thể sửa với nguồn nhưng không tự tạo deep copy phần tử, cũng không thay thế yêu cầu nhất quán khi nguồn đang bị sửa đồng thời.


## 10. Chương trình Java 17+ hoàn chỉnh, có thể biên dịch và chạy

Lưu tệp **InstanceofGenericsDemo.java**:

~~~java
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class InstanceofGenericsDemo {
    static <T> List<T> checkedList(
            Object candidate, Class<T> elementType) {

        if (!(candidate instanceof List<?> input)) {
            throw new IllegalArgumentException("List required");
        }

        List<T> result = new ArrayList<>();

        for (Object item : input) {
            if (!elementType.isInstance(item)) {
                throw new IllegalArgumentException("Unexpected element");
            }
            result.add(elementType.cast(item));
        }

        return List.copyOf(result);
    }

    static Map<String, List<Integer>> checkedScores(Object candidate) {
        if (!(candidate instanceof Map<?, ?> input)) {
            throw new IllegalArgumentException("Map required");
        }

        Map<String, List<Integer>> result = new HashMap<>();

        for (Map.Entry<?, ?> entry : input.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                throw new IllegalArgumentException("String key required");
            }

            result.put(
                    key,
                    checkedList(entry.getValue(), Integer.class)
            );
        }

        return Map.copyOf(result);
    }

    public static void main(String[] args) {
        Object unknown = List.of("Java", 7);

        System.out.println(
                "listWildcard=" + (unknown instanceof List<?>)
        );
        System.out.println(
                "mapWildcard=" + (unknown instanceof Map<?, ?>)
        );
        System.out.println(
                "nullMatch=" + (((Object) null) instanceof List<?>)
        );

        if (unknown instanceof List<?> list) {
            System.out.println("size=" + list.size());
            System.out.println("first=" + list.get(0));
        }

        List<String> known =
                new ArrayList<>(List.of("Java"));

        // Java 16+: hợp lệ vì LHS đã có generic type String.
        System.out.println(
                "knownSubtype=" +
                (known instanceof ArrayList<String>)
        );

        if (known instanceof ArrayList<String> array) {
            System.out.println("patternFirst=" + array.get(0));
        }

        Object arrayCandidate = new List<?>[] {
                List.of("x"), List.of(4)
        };
        System.out.println(
                "wildcardArray=" +
                (arrayCandidate instanceof List<?>[])
        );

        System.out.println(
                "classWildcard=" +
                (String.class instanceof Class<?>)
        );

        System.out.println(
                "validated=" +
                checkedList(List.of("A", "B"), String.class)
        );

        try {
            checkedList(unknown, String.class);
        } catch (IllegalArgumentException error) {
            System.out.println("invalid=IllegalArgumentException");
        }

        Map<String, List<Integer>> scores =
                checkedScores(
                        Map.of(
                                "odd", List.of(1, 3),
                                "even", List.of(2, 4)
                        )
                );
        System.out.println("nestedOdd=" + scores.get("odd"));

        // Thao tác raw sau đây cố ý làm nhiễm dữ liệu của List<String>.
        List raw = known;
        raw.add(42);

        // instanceof không xác minh type argument String ở runtime.
        System.out.println(
                "stillArrayList=" +
                (known instanceof ArrayList<String>)
        );

        try {
            String bad = known.get(1);
            System.out.println(bad);
        } catch (ClassCastException error) {
            System.out.println("polluted=ClassCastException");
        }
    }
}
~~~

Biên dịch ở mức ngôn ngữ Java 17 bằng JDK có hỗ trợ `--release 17`:

~~~bash
javac --release 17 -Xlint:unchecked InstanceofGenericsDemo.java
java InstanceofGenericsDemo
~~~

Compiler phát **unchecked warning** ở `raw.add(42)` vì lời gọi trên raw List. Đây là cảnh báo của quá trình biên dịch, không phải stdout chương trình.

Stdout chính xác:

~~~text
listWildcard=true
mapWildcard=false
nullMatch=false
size=2
first=Java
knownSubtype=true
patternFirst=Java
wildcardArray=true
classWildcard=true
validated=[A, B]
invalid=IllegalArgumentException
nestedOdd=[1, 3]
stillArrayList=true
polluted=ClassCastException
~~~

### Phân tích những dòng quan trọng

**`listWildcard=true`:** đầu vào là một List, bất kể nó chứa String và Integer. Kiểu `List<?>` chấp nhận mọi loại phần tử nhưng không xác thực loại cụ thể.

**`mapWildcard=false`:** đối tượng này là List, không phải Map. Việc `Map<?, ?>` có cú pháp hợp lệ không quyết định kết quả `true`.

**`nullMatch=false`:** `instanceof` trên null luôn trả false trong các ví dụ này; kiểm tra không ném NullPointerException chỉ vì tham chiếu null.

**`knownSubtype=true` và `patternFirst=Java`:** biến có kiểu `List<String>`, nhưng runtime object là ArrayList. Java 17 cho phép kiểm tra `ArrayList<String>` theo quy tắc casting conversion.

**`wildcardArray=true`:** JVM kiểm tra cấu trúc array với component type tương thích List; nó không xác thực generic element type trong từng List của array.

**`validated=[A, B]`:** hàm kiểm tra dữ liệu thực tế chỉ chấp nhận các phần tử String và tạo danh sách mới.

**`invalid=IllegalArgumentException`:** `unknown` chứa Integer nên validator từ chối; `instanceof List<?>` vẫn đã thành công ở bước trước.

**`nestedOdd=[1, 3]`:** validator lồng nhau kiểm tra Map, key String, List và từng Integer trước khi tạo giá trị có kiểu.

**`stillArrayList=true` nhưng `polluted=ClassCastException`:** alias raw đưa một Integer vào ArrayList vốn được giữ qua `List<String>`. `instanceof ArrayList<String>` không phát hiện sai phần tử; lỗi xuất hiện khi đọc như String và kiểm tra kiểu runtime thất bại. Đây là minh chứng rằng kiểm tra lớp runtime không bằng xác thực generic arguments.

## 11. Array của wildcard-parameterized types

Một trường hợp thường bị bỏ sót:

~~~java
List<?>[] lists = new List<?>[2];
lists[0] = List.of("A");
lists[1] = List.of(123);

Object value = lists;
boolean isListArray = value instanceof List<?>[]; // true.
~~~

`List<?>` là reifiable theo ngôn ngữ, do đó array `List<?>[]` hợp lệ. Runtime kiểm tra array có component type phù hợp `List`; **không** chứng minh phần tử của từng danh sách có một type argument cụ thể.

Ngược lại, không được tạo array generic cụ thể trực tiếp:

~~~java
// List<String>[] illegal = new List<String>[2];
// Lỗi: generic array creation.
~~~

Và khi vế trái có kiểu Object, phép kiểm tra `value instanceof List<String>[]` không được dùng như một cách xác minh mảng danh sách chuỗi.

Mảng trong Java mang thông tin kiểu thành phần runtime và có thể có kiểm tra `ArrayStoreException`, trong khi generics dùng erasure. `List<?>[]` phối hợp hai cơ chế nhưng không vượt qua giới hạn kiểm tra sâu của generic arguments.

Nếu không cần mảng, nhiều API hiện đại thuận tiện và nhất quán hơn khi dùng `List<List<?>>` hoặc một cấu trúc generic phù hợp thay vì generic array.

## 12. Raw List và List<?> trong instanceof khác nhau thế nào?

Hai kiểm tra sau đều có thể nhận ra một đối tượng triển khai List:

~~~java
Object input = List.of("Java");

boolean rawCheck = input instanceof List;
boolean wildcardCheck = input instanceof List<?>;
~~~

Khác biệt đáng chú ý nằm ở **cách biểu đạt kiểu** và hành vi khi ta tiếp tục sử dụng kết quả:

~~~java
if (input instanceof List<?> list) {
    Object element = list.get(0);
    // list.add("new"); // Compiler chặn.
}
~~~

`List<?>` giữ lại thông tin rằng danh sách có một kiểu phần tử chưa biết. Nếu cast sang raw `List` rồi gọi add, code có thể mất kiểm tra generic và phát cảnh báo unchecked.

~~~java
if (input instanceof List) {
    List raw = (List) input;
    // raw.add(42); // Có thể sinh unchecked warning và gây nhiễm dữ liệu.
}
~~~

Bản thân `instanceof List` không phải luôn có lỗi hoặc bắt buộc sinh warning. Vấn đề là sử dụng **raw type** trong code mới không cung cấp sự an toàn generic như wildcard.

Với Java 17, khuyến nghị thường là dùng `instanceof List<?> list` cho mục đích nhận biết một collection chưa biết kiểu phần tử, rồi xác minh từng phần tử nếu miền nghiệp vụ yêu cầu một loại cụ thể.

## 13. Các trường hợp hợp lệ và không hợp lệ khác

### 13.1. Multiple unbounded wildcards

~~~java
Object value = Map.of("one", 1);

if (value instanceof Map<?, ?> map) {
    System.out.println(map.size());
}
~~~

`Map<?, ?>` hợp lệ vì cả key type và value type đều không xác định. Nhưng `Map<String, ?>` từ một biến Object không được tự động chấp nhận như một phép xác nhận String cho key.

### 13.2. Kiểm tra Optional<?> và Class<?>

~~~java
Object maybe = java.util.Optional.of("Java");
boolean optional = maybe instanceof java.util.Optional<?>;

Object classObject = String.class;
boolean classToken = classObject instanceof Class<?>;
~~~

Các phép kiểm tra trên hợp lệ; với Optional<?> không thể kết luận giá trị bên trong là String, cũng như với Class<?> chưa có type parameter T cụ thể.

### 13.3. instanceof không chấp nhận primitive ở RHS trong Java 17

~~~java
Object value = Integer.valueOf(3);

boolean boxed = value instanceof Integer; // Hợp lệ.
// boolean primitive = value instanceof int; // Không biên dịch ở Java 17.
~~~

Generic type arguments cũng không được thay thế bằng primitive:

~~~java
// List<int> invalid; // Không hợp lệ: type argument cần reference type.
List<Integer> valid = List.of(1, 2, 3);
~~~

### 13.4. Kiểu đã biết vẫn không có quyền kiểm tra mọi tham số tùy ý

~~~java
List<String> known = List.of("Java");

// boolean wrong = known instanceof ArrayList<Integer>;
// Không có casting conversion phù hợp giữa tham số String và Integer.
boolean possible = known instanceof ArrayList<String>;
~~~

Sự nới lỏng Java 16+ **không có nghĩa** mọi parameterized RHS đều hợp lệ. Hệ thống kiểu vẫn xét quan hệ có thể cast được giữa vế trái và RHS.

## 14. Những lỗi compiler cần phân biệt

**Lỗi 1 — không thể kiểm tra type argument từ Object**

~~~java
Object object = List.of("Java");
// boolean invalid = object instanceof List<String>;
~~~

Trình biên dịch Java 17 có thể báo:

~~~text
Object cannot be safely cast to List<String>
~~~

Nguyên nhân: không có casting conversion phù hợp để làm phép kiểm tra tham số String an toàn từ Object.

**Lỗi 2 — nguồn List<?> nhưng đích muốn String**

~~~java
List<?> unknown = List.of("Java");
// boolean invalid = unknown instanceof ArrayList<String>;
~~~

Compiler không biết wildcard thật sự là String; từ `List<?>` không có bằng chứng an toàn cho kiểu tham số hóa cụ thể `ArrayList<String>`.

**Lỗi 3 — pattern không thêm bất kỳ kiểm tra kiểu có ý nghĩa nào trong Java 17**

~~~java
List<String> known = List.of("Java");
// if (known instanceof List<String> same) { }
// Java 17 báo lỗi pattern đã luôn đúng theo kiểu tĩnh.
~~~

Đừng nhầm với dạng toán tử boolean truyền thống vốn có thể được phép. Ví dụ `known instanceof ArrayList<String> array` là pattern có ý nghĩa vì chỉ một số List mới là ArrayList.

**Lỗi 4 — tin rằng instanceof List<?> chứng minh phần tử là String**

~~~java
Object input = List.of("Java", 7);

if (input instanceof List<?> list) {
    // String first = (String) list.get(1); // Runtime ClassCastException.
}
~~~

Cần validator khi muốn bảo đảm loại phần tử, không chỉ cast.

**Lỗi 5 — dùng instanceof Map<?,?> rồi ép thẳng thành Map<String,List<Integer>>**

~~~java
Object input = Map.of("scores", List.of("wrong"));

if (input instanceof Map<?, ?> map) {
    // Unchecked cast bên dưới không kiểm tra sâu:
//  Map<String, List<Integer>> invalid =
//          (Map<String, List<Integer>>) map;
}
~~~

Bản thân Map tồn tại, nhưng key/value generic chưa được kiểm tra. Hãy kiểm tra key và list con rồi xây cấu trúc có kiểu.

## 15. Quy trình ra quyết định khi viết instanceof với generics

1. **Ghi kiểu tĩnh của vế trái.** Nếu là Object, không suy luận type arguments từ runtime object.
2. **Xác định mục tiêu kiểm tra.** Chỉ hỏi “đây có phải List không?” hay phải xác nhận từng phần tử là String?
3. **Với Object và collection không biết type arguments**, dùng `List<?>` hoặc `Map<?, ?>`.
4. **Nếu vế trái đã có parameterized type**, xem kiểu RHS có casting conversion hợp lệ hay không; Java 16+ có thể cho phép kiểm tra tham số cụ thể trong bối cảnh này.
5. **Nếu muốn binding pattern**, kiểm tra thêm quy tắc flow scoping và unconditional pattern của source version. Trên Java 17, một pattern mà kiểu nguồn đã là subtype của kiểu pattern có thể bị từ chối.
6. **Không xem instanceof là validator tổng quát cho nested generics.** Muốn `List<String>` hoặc `Map<String,List<Integer>>` thật sự sạch, phải xác nhận từng tầng/phần tử.
7. **Dùng API an toàn và tạo bản sao** khi nhận dữ liệu từ vùng không tin cậy. Hạn chế raw types, unchecked casts và mutable aliases.
8. **Kiểm chứng bằng compiler mục tiêu**, ví dụ `javac --release 17`, thay vì suy luận từ mã chạy ở JDK phiên bản khác.

Một chú ý về bảo trì: cú pháp pattern matching và quy tắc đối với unconditional patterns có thay đổi theo phiên bản Java. Bài dùng Java 17 làm chuẩn cho ví dụ chạy; khi áp dụng trên JDK mới hơn, nên kiểm tra chẩn đoán theo `--release` hoặc `--source` tương ứng.

## 16. Câu hỏi tự kiểm tra

**Câu 1.** `Object value` chứa một `List<String>`. Có thể viết `value instanceof List<String>` không?

**Đáp án:** Không trong Java 17. Kiểu tĩnh Object không cung cấp thông tin để kiểm tra tham số String bằng phép test runtime. Dùng `value instanceof List<?>` rồi xác thực từng phần tử nếu cần.

**Câu 2.** `value instanceof List<?>` trả true có chứng minh tất cả phần tử là String không?

**Đáp án:** Không. Nó chỉ kiểm tra runtime object triển khai List; List có thể chứa String, Integer hoặc dữ liệu khác.

**Câu 3.** Vì sao `List<String> known` lại có thể dùng `known instanceof ArrayList<String>`?

**Đáp án:** Từ Java 16, `instanceof` có thể dùng một non-reifiable parameterized type khi kiểu tĩnh LHS cho phép casting conversion phù hợp tới RHS. Kiểu String đã được mô tả bởi tham chiếu List<String>, còn runtime kiểm tra đối tượng có phải ArrayList.

**Câu 4.** Nếu `known instanceof ArrayList<String>` true, nó có phát hiện được Integer được đưa vào qua raw alias không?

**Đáp án:** Không. Runtime test không xác thực kiểu từng phần tử. Đọc phần tử bị nhiễm như String có thể ném ClassCastException.

**Câu 5.** `Map<?, ?>` có tự chứng minh map chứa `String` key và `List<Integer>` value không?

**Đáp án:** Không. Cần kiểm tra các entry, key, List và các Integer bên trong.

**Câu 6.** Có thể dùng `value instanceof List<?>[]` từ Object không?

**Đáp án:** Có. Array với component type List<?> là reifiable, nhưng runtime không kiểm tra kiểu phần tử nằm trong từng List của array.

**Câu 7.** Tại sao `if (known instanceof List<String> same)` bị từ chối ở Java 17 khi known đã là List<String>?

**Đáp án:** Đây là unconditional type pattern theo kiểu tĩnh; quy tắc pattern matching của Java 17 từ chối nó. Lỗi khác với việc không thể cast an toàn từ Object sang List<String>.

**Câu 8.** `List<?>` và raw `List` khác nhau ra sao sau kiểm tra instanceof?

**Đáp án:** Chúng có thể kiểm tra cùng runtime List, nhưng `List<?>` giữ thông tin một kiểu phần tử chưa biết và chặn thêm tùy ý; raw List bỏ type argument và dễ tạo thao tác unchecked.

**Câu 9.** Cách đáng tin để nhận dữ liệu `Object` và tạo `List<String>` không có phần tử sai kiểu?

**Đáp án:** Dùng `instanceof List<?>`, duyệt từng phần tử kiểm tra `String.class.isInstance`, sao chép các phần tử đã hợp lệ vào `List<String>`, và quy định rõ chính sách null/mutation.

## 17. Ghi nhớ

**`instanceof List<?>` là hợp lệ và hữu ích** vì kiểm tra được runtime List mà không hứa hẹn điều gì về kiểu phần tử. `List<String>` không phải một bài kiểm tra runtime tổng quát từ `Object`.

Tuy nhiên, **Java 16+ không cấm mọi parameterized RHS**: khi vế trái đã có generic type phù hợp, phép kiểm tra như `List<String> x; x instanceof ArrayList<String>` có thể biên dịch. Compiler dùng kiểu tĩnh của biểu thức để xét casting conversion, nhưng runtime vẫn không xác thực nội dung generic arguments.

Muốn chứng minh dữ liệu nhiều tầng thật sự phù hợp `List<String>` hoặc `Map<String,List<Integer>>`, phải **kiểm tra từng phần tử và xây kết quả có kiểu rõ ràng**. Phân biệt được ba tầng — **kiểu tĩnh**, **điều kiện instanceof tại runtime**, và **xác thực nội dung** — là chìa khóa để sử dụng generics an toàn.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="runtime-types-006">Phân biệt kiểm tra kiểu tổng thể với xác minh kiểu của mọi phần tử</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt kiểm tra kiểu tổng thể với xác minh kiểu của mọi phần tử trong Java Generics

Khi nhận một giá trị kiểu `Object` từ API, JSON, reflection, mã cũ hoặc một thư viện bên ngoài, ta thường muốn biết: “Đây có phải danh sách chứa toàn `String` hay không?”. Câu hỏi này thực chất gồm **hai yêu cầu kiểm tra khác nhau**:

1. **Kiểm tra kiểu của đối tượng bên ngoài**: giá trị có phải một đối tượng triển khai `List` không?
2. **Xác minh từng phần tử**: mỗi phần tử trong danh sách thực tế có thỏa điều kiện “là `String`” không?

Trong Java, kiểm tra thứ nhất có thể viết bằng `value instanceof List<?>`. Kiểm tra thứ hai cần **duyệt các phần tử và kiểm tra từng giá trị**, chẳng hạn dùng `instanceof String` hoặc `String.class.isInstance(element)`.

```java
// Snippet minh họa trong một phương thức; import java.util.List.
Object value = List.of("Java", 42);

boolean isList = value instanceof List<?>; // true
// Không thể suy ra tất cả phần tử đều là String.
```

Đối tượng trên là một `List` hợp lệ, nhưng chứa `Integer` ở vị trí thứ hai. Vì vậy, `isList == true` **không chứng minh** danh sách là `List<String>` theo nghĩa dữ liệu hiện có đều là `String`.

Đây là khác biệt cốt lõi giữa **kiểm tra hình dạng/kiểu bên ngoài** và **xác minh nội dung dữ liệu**.

## 1. Hai mức kiểm tra trả lời hai câu hỏi khác nhau

Giả sử:

```java
Object input = java.util.List.of("An", 123);
```

Kiểm tra tổng thể:

```java
if (input instanceof java.util.List<?> values) {
    System.out.println("Đây là danh sách với " + values.size() + " phần tử");
}
```

Compiler có thể gán `input` cho biến mẫu `values` kiểu `List<?>` khi kiểm tra thành công. Nhưng kiểu `List<?>` chỉ nói rằng có một **kiểu phần tử chưa biết**; nó không giới hạn kiểu đó thành `String`.

Kiểm tra từng phần tử:

```java
// Snippet tiếp tục trong nhánh đã biết values là List<?>.
boolean allStrings = true;

for (Object element : values) {
    if (!(element instanceof String)) {
        allStrings = false;
        break;
    }
}
```

Với `["An", 123]`, kết quả là `false`. Với `["An", "Binh"]`, kết quả là `true`. Hai đoạn đều biên dịch theo cú pháp Java 17+ khi được đặt vào phương thức hợp lệ, nhưng kiểm tra tổng thể và kiểm tra phần tử có phạm vi khác nhau.

| Giá trị thật | `instanceof List<?>` | Mọi phần tử là `String`? |
| --- | --- | --- |
| `List.of("An", "Binh")` | `true` | `true` |
| `List.of("An", 42)` | `true` | `false` |
| `List.of(1, 2)` | `true` | `false` |
| `List.of()` | `true` | `true` theo điều kiện “mọi phần tử”, vì danh sách rỗng không có phần tử phản ví dụ |
| `"An"` | `false` | Không phải danh sách, nên kiểm tra tổng hợp phải từ chối |
| `null` | `false` | Không phải danh sách, nên kiểm tra tổng hợp phải từ chối |

**Lưu ý với danh sách rỗng:** `allStrings(emptyList)` thường trả `true` theo quy tắc logic “mọi phần tử đều thỏa”; điều đó **không cung cấp bằng chứng có tồn tại phần tử String**. Nếu nghiệp vụ yêu cầu danh sách không rỗng, cần bổ sung điều kiện `!list.isEmpty()`.

## 2. Vì sao `instanceof List<String>` không thay việc kiểm tra từng phần tử?

Generics Java thông thường sử dụng **type erasure**. Kiểu tham số hóa `List<String>` được kiểm tra ở mã nguồn, nhưng runtime class của một `ArrayList<String>` vẫn là `ArrayList`, giống đối tượng `ArrayList<Integer>` tạo từ cùng class.

```java
// Snippet minh họa:
java.util.List<String> text = new java.util.ArrayList<>();
java.util.List<Integer> numbers = new java.util.ArrayList<>();

boolean sameClass = text.getClass() == numbers.getClass(); // true
```

Thông tin type argument `String` hoặc `Integer` không khiến Java sinh một class runtime riêng cho mỗi cách tham số hóa. Đối với giá trị chỉ có kiểu tĩnh `Object`, câu lệnh sau **không hợp lệ**:

```java
Object value = java.util.List.of("An");
// boolean result = value instanceof java.util.List<String>; // Lỗi biên dịch
```

Trong khi đó:

```java
Object value = java.util.List.of("An");
boolean result = value instanceof java.util.List<?>; // Hợp lệ, true
```

`List<?>` là **reifiable type**: phép kiểm tra runtime không đòi xác minh một type argument cụ thể. Nó chỉ cần kiểm tra object thực hiện `List`. Còn `List<String>` không phải một parameterized type reifiable để kiểm tra kiểu phần tử `String` trực tiếp theo cách này.

Một số trường hợp `instanceof` với biểu thức đã có kiểu tĩnh rất cụ thể được các phiên bản Java hiện đại xử lý theo quy tắc tương thích kiểu; nhưng điều đó **không** tạo ra cơ chế runtime tổng quát để xác nhận tất cả phần tử của một `Object` là `String`. Khi nhận dữ liệu chưa biết, hãy dùng `List<?>` rồi xác minh nội dung.

## 3. Dữ liệu phải đi qua hai bước kiểm tra

Một hàm xác nhận đúng mục tiêu có thể viết:

```java
// Snippet phương thức có thể đặt trong một class Java.
static boolean isListOfStrings(Object input) {
    if (!(input instanceof java.util.List<?> items)) {
        return false;
    }

    for (Object item : items) {
        if (!(item instanceof String)) {
            return false;
        }
    }

    return true;
}
```

**Bước 1:** nếu `input` không phải `List`, trả `false`. Nhờ pattern matching của `instanceof`, khi vào sau điều kiện `if`, biến `items` đã có kiểu `List<?>` hợp lệ.

**Bước 2:** vòng `for` đọc từng giá trị thành `Object` và hỏi `item instanceof String`. Bất kỳ giá trị sai kiểu nào cũng khiến kết quả là `false`.

**Bước 3:** nếu vòng lặp kết thúc không thấy phản ví dụ, trả `true`. Với danh sách rỗng, vòng lặp không chạy và hàm trả `true`, phù hợp cách hiểu “mọi phần tử thỏa điều kiện”.

Phương thức này trả về một **kết quả xác minh tại thời điểm duyệt**. Nó không thay đổi generic type được khai báo trên tham chiếu cũ và không bảo vệ một danh sách có thể bị sửa ngay sau khi kiểm tra.

### Có thể dùng Stream không?

Có:

```java
static boolean isListOfStrings(Object input) {
    return input instanceof java.util.List<?> items
            && items.stream().allMatch(String.class::isInstance);
}
```

`allMatch` trả `true` khi **mọi** phần tử thỏa predicate; với stream rỗng, kết quả cũng là `true`. Cú pháp ngắn hơn nhưng vẫn thực hiện kiểm tra trên từng phần tử cho đến khi có thể quyết định kết quả.

Về mặt hành vi, `allMatch` là **short-circuiting terminal operation**: có thể dừng ngay khi gặp phần tử không phù hợp. Không nên xem việc dùng stream như một phép kiểm tra type argument được JVM cung cấp sẵn; nó vẫn phải xét dữ liệu.

## 4. Kiểm tra mọi phần tử không có nghĩa danh sách đã trở thành `List<String>`

Xét:

```java
Object input = java.util.List.of("An", "Binh");

if (input instanceof java.util.List<?> items) {
    boolean verified = items.stream().allMatch(String.class::isInstance);

    // Dù verified là true, kiểu tĩnh của items vẫn là List<?>.
    // java.util.List<String> result = items; // Không biên dịch
}
```

Kết quả boolean là một khẳng định về **những phần tử vừa kiểm tra**, nhưng compiler Java không có cơ chế tự chuyển kiểu tĩnh `List<?>` thành `List<String>` chỉ vì một phép kiểm tra bằng vòng lặp trả `true`. Java không phải một hệ thống kiểu có khả năng suy luận tự động điều kiện “mọi phần tử trong collection đều String” thành thay đổi type argument của biến.

Có ba hướng tùy mục tiêu:

1. Chỉ cần kết luận đúng/sai: giữ kết quả boolean và tiếp tục làm việc qua `List<?>`.
2. Cần dùng một `List<String>` có dữ liệu đáng tin: **kiểm tra từng phần tử rồi sao chép sang danh sách mới với kiểu đúng**.
3. Cần tránh sao chép vì hiệu năng: chỉ dùng một cast được kiểm soát khi có hợp đồng dữ liệu đủ mạnh và kiểm soát được mọi cách danh sách có thể bị sửa; đây là tình huống có điều kiện an toàn chặt chẽ, không phải mặc định.

Đừng viết `(List<String>) items` rồi cho rằng phép cast tự quét từng phần tử. Java không có kiểm tra runtime đầy đủ cho type argument `String` của `List<String>`.

## 5. Kiểm tra rồi sao chép sang danh sách có kiểu đáng tin

Khi xử lý dữ liệu bên ngoài, phương án rõ ràng là **tạo bản sao được xác thực**. Với `Class<T>` từ caller, có thể viết một adapter generic:

```java
// Snippet phương thức; cần java.util.List và java.util.ArrayList.
static <T> java.util.List<T> validatedCopy(Object value, Class<T> expected) {
    if (!(value instanceof java.util.List<?> items)) {
        throw new IllegalArgumentException("Not a list");
    }

    java.util.List<T> result = new java.util.ArrayList<>();
    for (int i = 0; i < items.size(); i++) {
        Object item = items.get(i);
        if (!expected.isInstance(item)) {
            throw new IllegalArgumentException("Invalid element at " + i);
        }
        result.add(expected.cast(item));
    }

    return java.util.List.copyOf(result);
}
```

Lời gọi:

```java
// Snippet trong phương thức:
java.util.List<String> names =
        validatedCopy(java.util.List.of("An", "Binh"), String.class);
```

Cơ chế:

- `value instanceof List<?> items` xác nhận cấu trúc bên ngoài.
- `expected.isInstance(item)` kiểm tra class runtime của **mỗi** phần tử. Với `String.class`, chỉ các instance `String` được chấp nhận; `null` bị từ chối vì `Class.isInstance(null)` là `false`.
- `expected.cast(item)` thực hiện cast được kiểm tra; compiler biết kết quả có kiểu `T`, nhờ token `Class<T>`.
- `List.copyOf(result)` tạo kết quả không sửa được và không chứa `null`. Nó là **bản sao nông** (*shallow copy*): nếu các phần tử là đối tượng mutable thì nội trạng của chúng vẫn có thể thay đổi; riêng `String` là bất biến.

Vì danh sách kết quả mới không dùng chung bộ chứa phần tử có thể sửa với nguồn, việc sửa cấu trúc danh sách nguồn **sau khi sao chép xong** không chèn được một phần tử sai kiểu vào danh sách kết quả.

Nhưng **kiểm tra từng phần tử không tự động chống race** khi một luồng khác đang sửa danh sách *trong quá trình duyệt*. Với collection có thể thay đổi đồng thời, cần đồng bộ hóa, snapshot nhất quán theo hợp đồng nguồn, hoặc yêu cầu ownership thích hợp trước khi duyệt. Một vòng lặp đọc thông thường không hứa cung cấp snapshot nguyên tử.

## 6. Chương trình Java 17+ chạy đầy đủ

Lưu toàn bộ chương trình sau trong **`OverallVsElementsDemo.java`**:

```java
public class OverallVsElementsDemo {
    static boolean allStrings(Object value) {
        if (!(value instanceof java.util.List<?> items)) {
            return false;
        }
        for (Object item : items) {
            if (!(item instanceof String)) {
                return false;
            }
        }
        return true;
    }

    static <T> java.util.List<T> validatedCopy(
            Object value, Class<T> expected) {
        if (!(value instanceof java.util.List<?> items)) {
            throw new IllegalArgumentException("Not a list");
        }

        java.util.List<T> result = new java.util.ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            Object item = items.get(i);
            if (!expected.isInstance(item)) {
                throw new IllegalArgumentException(
                        "Invalid element at " + i);
            }
            result.add(expected.cast(item));
        }

        return java.util.List.copyOf(result);
    }

    static boolean nestedStrings(Object value) {
        if (!(value instanceof java.util.List<?> rows)) {
            return false;
        }
        for (Object row : rows) {
            if (!(row instanceof java.util.List<?> cells)) {
                return false;
            }
            for (Object cell : cells) {
                if (!(cell instanceof String)) {
                    return false;
                }
            }
        }
        return true;
    }

    public static void main(String[] args) {
        Object mixed = java.util.List.of("Java", 42);
        Object valid = java.util.List.of("An", "Binh");
        Object empty = java.util.List.of();

        System.out.println("Mixed instanceof List<?>: "
                + (mixed instanceof java.util.List<?>));
        System.out.println("Mixed all Strings: " + allStrings(mixed));
        System.out.println("Valid all Strings: " + allStrings(valid));
        System.out.println("Empty all Strings: " + allStrings(empty));

        try {
            validatedCopy(mixed, String.class);
        } catch (IllegalArgumentException exception) {
            System.out.println("Mixed copy rejected: "
                    + exception.getMessage());
        }

        java.util.List<String> copy = validatedCopy(valid, String.class);
        System.out.println("Validated copy: " + copy);
        System.out.println("Copy shares list object: " + (copy == valid));

        Object goodNested = java.util.List.of(
                java.util.List.of("A"), java.util.List.of("B"));
        Object badNested = java.util.List.of(
                java.util.List.of("A"), java.util.List.of(7));

        System.out.println("Nested valid: " + nestedStrings(goodNested));
        System.out.println("Nested invalid: " + nestedStrings(badNested));

        Object array = new String[] { "x" };
        System.out.println("String[] runtime check: "
                + (array instanceof String[]));
    }
}
```

Chạy bằng JDK 17 trở lên:

```bash
javac -encoding UTF-8 OverallVsElementsDemo.java
java OverallVsElementsDemo
```

Standard output:

```text
Mixed instanceof List<?>: true
Mixed all Strings: false
Valid all Strings: true
Empty all Strings: true
Mixed copy rejected: Invalid element at 1
Validated copy: [An, Binh]
Copy shares list object: false
Nested valid: true
Nested invalid: false
String[] runtime check: true
```

Các kết quả minh họa những điều quan trọng:

- `mixed instanceof List<?>` là `true` mặc dù `mixed` chứa `Integer`.
- `allStrings(mixed)` trả `false` sau khi gặp `42`, còn `allStrings(valid)` là `true`.
- Danh sách rỗng cho kết quả `true` với điều kiện “mọi phần tử là String”. Nếu cần không rỗng, hãy kiểm tra riêng.
- `validatedCopy(mixed, String.class)` từ chối phần tử ở chỉ số 1 và báo vị trí rõ ràng.
- Bản sao xác thực không cùng đối tượng với danh sách nguồn.
- Hai lớp dữ liệu `List<List<String>>` cần hai tầng kiểm tra: danh sách ngoài chứa các danh sách con, rồi mỗi danh sách con chứa đúng `String`.
- `String[]` có kiểu thành phần nhận biết ở runtime; đó là cơ chế khác với việc kiểm tra generic element của `List<String>`.

## 7. Tình huống danh sách rỗng, phần tử null và kiểm tra định lượng

### 7.1. “Mọi phần tử thỏa” khác “có ít nhất một phần tử thỏa”

Với `List.of()`, kết quả của `allMatch(String.class::isInstance)` là `true`; đây là **chân lý rỗng** (*vacuous truth*) của mệnh đề “mọi phần tử”.

Nếu yêu cầu nghiệp vụ là **danh sách phải có ít nhất một chuỗi**, hãy viết:

```java
static boolean nonEmptyStringList(Object input) {
    return input instanceof java.util.List<?> list
            && !list.isEmpty()
            && list.stream().allMatch(String.class::isInstance);
}
```

Đừng dùng `anyMatch` thay `allMatch` nếu hợp đồng yêu cầu **mọi** phần tử đều thỏa. `anyMatch` chỉ cần **ít nhất một** phần tử là `String`.

Ví dụ với `["An", 42]`: `anyMatch(String.class::isInstance)` là `true` nhưng `allMatch(String.class::isInstance)` là `false`.

### 7.2. Null có được chấp nhận không?

Biểu thức `null instanceof String` là `false`, và `String.class.isInstance(null)` cũng là `false`. Do đó cả `allStrings` lẫn `validatedCopy` ở trên có **hợp đồng nghiêm ngặt không chấp nhận null**.

Cần nói rõ vì một biến `List<String>` ở mã nguồn Java **có thể chứa null** nếu collection cho phép: type argument `String` tự nó không có nghĩa “non-null String”. Nếu nghiệp vụ cho phép `null`, cần diễn đạt predicate rõ ràng:

```java
static boolean stringsOrNull(Object input) {
    return input instanceof java.util.List<?> list
            && list.stream().allMatch(
                    element -> element == null || element instanceof String);
}
```

Không nên dùng nguyên `List.copyOf` cho danh sách có thể chứa null: API này **không chấp nhận phần tử null**. Khi cho phép null, có thể sao chép vào `ArrayList<String>` sau khi kiểm tra, rồi áp dụng một chiến lược không sửa được phù hợp nếu cần.

### 7.3. Kiểm tra mọi phần tử chỉ là một loại validation

Một danh sách chỉ chứa `String` vẫn có thể sai nghiệp vụ: chuỗi rỗng, quá dài, không đúng định dạng hoặc chứa giá trị trùng lặp. Bài toán “type validation” chỉ xác nhận **loại phần tử**; validation nghiệp vụ có thể cần những predicate bổ sung:

```java
// Snippet minh họa:
boolean valid = input instanceof java.util.List<?> list
        && !list.isEmpty()
        && list.stream().allMatch(
                item -> item instanceof String s
                        && !s.isBlank()
                        && s.length() <= 50);
```

Những yêu cầu này phải đến từ hợp đồng dữ liệu cụ thể, không nên tự áp chúng khi chỉ được hỏi “có phải danh sách String không?”.

## 8. Dữ liệu lồng nhau: phải kiểm tra từng tầng

Giả sử muốn xác thực dữ liệu có cấu trúc gần với `List<List<String>>`:

```java
Object rows = java.util.List.of(
        java.util.List.of("A", "B"),
        java.util.List.of("C"));
```

Kiểm tra `rows instanceof List<?>` chỉ nói rằng **bên ngoài** là danh sách. Nó không chứng minh:

1. Từng phần tử bên ngoài là một danh sách khác.
2. Từng phần tử của các danh sách con là `String`.

Vì vậy, cần kiểm tra lần lượt:

```java
static boolean nestedStrings(Object value) {
    if (!(value instanceof java.util.List<?> rows)) {
        return false;
    }
    for (Object row : rows) {
        if (!(row instanceof java.util.List<?> cells)) {
            return false;
        }
        for (Object cell : cells) {
            if (!(cell instanceof String)) {
                return false;
            }
        }
    }
    return true;
}
```

Mỗi tầng generic bị xóa kiểu đều cần được xác minh theo cấu trúc thực tế nếu đầu vào chưa đáng tin. Với dữ liệu lồng nhau nhiều tầng, có thể thiết kế validator đệ quy hoặc parser có schema; nhưng việc xác minh đúng **cấu trúc và từng lá** vẫn là trách nhiệm của cơ chế validation.

Cùng ý tưởng áp dụng cho `Map<String, Integer>`: `value instanceof Map<?, ?>` chỉ kiểm tra là một `Map`. Muốn xác nhận `String` ở khóa và `Integer` ở giá trị, cần duyệt `entrySet()` và kiểm tra **cả key lẫn value**.

## 9. Kiểm tra class với `Class<T>`: hữu ích nhưng có giới hạn

`Class<T>` cung cấp token của một class runtime cụ thể:

```java
static <T> boolean everyElementIs(
        Object input, Class<T> expected) {
    return input instanceof java.util.List<?> list
            && list.stream().allMatch(expected::isInstance);
}
```

Ví dụ `everyElementIs(List.of(1, 2), Number.class)` là `true`, vì `Integer` là subtype của `Number`. `Class.isInstance` kiểm tra **khả năng gán tương thích** theo runtime class, không đòi `element.getClass() == Number.class`.

Nếu nghiệp vụ đòi đúng class cụ thể và **không cho phép subclass**, cần quy tắc khác, ví dụ `element != null && element.getClass() == expected`. Đây là một lựa chọn thiết kế, thường không cần trong API hướng đối tượng thông thường.

Nhưng một token `Class<T>` **không mô tả đầy đủ kiểu tham số hóa lồng nhau**. Không có class literal Java dạng `List<String>.class` để xác minh tất cả phần tử. `List.class` chỉ giúp xác nhận class/interface bên ngoài; token `String.class` giúp kiểm tra một phần tử tại một thời điểm.

Muốn xác minh `List<List<String>>` thì cần thông tin cấu trúc “list của list của string” và các bước kiểm tra tương ứng, không thể chỉ truyền `List.class` một lần.

## 10. Kiểm tra tổng thể của array khác collection generic như thế nào?

Array Java mang **runtime component type**, còn collection generic thông thường không lưu type argument cụ thể như một phần runtime class của từng instance.

```java
Object value = new String[] { "A", "B" };

System.out.println(value instanceof String[]); // true
```

Với mảng `String[]`, JVM có thể xác minh object là mảng chứa phần tử kiểu `String` theo runtime component type. Nếu một tham chiếu `Object[]` trỏ tới mảng `String[]` thực tế, cố ghi `Integer` sẽ gây `ArrayStoreException`:

```java
Object[] array = new String[1];

try {
    array[0] = 42;
} catch (ArrayStoreException exception) {
    System.out.println("Rejected by runtime array type");
}
```

Mảng có kiểm tra kiểu thành phần khi ghi. Trong khi đó, nếu mã raw hoặc unchecked operation đưa `Integer` vào đối tượng `List<String>`, generic collection không có kiểm tra kiểu phần tử được duy trì bởi runtime container theo cách mảng có. Lỗi có thể xuất hiện lúc cast trên đường đọc dữ liệu thành `String`.

Cũng cần hiểu `String[]` là một array có component type reifiable, còn `List<String>` **không phải kiểu reifiable**. Java cho phép `new List<?>[3]` vì `List<?>` reifiable, nhưng không cho phép tạo trực tiếp `new List<String>[3]`.

Tuy vậy, kiểm tra `instanceof String[]` chỉ khẳng định runtime array component type. Nó không tự đánh giá những điều kiện nghiệp vụ như độ dài, phần tử rỗng, quy tắc định dạng hay chính sách cho phép `null`.

## 11. Vì sao unchecked cast không chứng minh kiểu mọi phần tử?

Xét đoạn cố ý không an toàn:

```java
// Snippet minh họa cảnh báo unchecked, không phải mẫu nên dùng.
Object value = java.util.List.of("Java", 42);

@SuppressWarnings("unchecked")
java.util.List<String> casted = (java.util.List<String>) value;

// String second = casted.get(1); // Có thể ném ClassCastException
```

Cast `(List<String>) value` không kiểm tra từng phần tử là `String`. Khi runtime chỉ có thể xác nhận phần raw `List`, việc tin tưởng type argument `String` là **unchecked**. `@SuppressWarnings("unchecked")` chỉ che cảnh báo, không làm cast trở nên có khả năng kiểm tra generic contents.

Nếu cố lấy `casted.get(1)` thành `String`, compiler có thể chèn `checkcast String`, và phần tử `42` khiến `ClassCastException` xuất hiện.

**Quy tắc:** muốn xác minh toàn bộ phần tử, phải có bằng chứng từ cấu trúc dữ liệu đáng tin hoặc tự kiểm tra; một unchecked cast không tạo ra bằng chứng đó.

Tương tự, raw type có thể gây **heap pollution**:

```java
// Snippet mô tả dữ liệu bị làm bẩn.
java.util.List<String> names = new java.util.ArrayList<>();
java.util.List raw = names;
raw.add(42); // Unchecked warning, nhưng có thể ghi Integer vào List<String>
```

Điều này cho thấy việc kiểm tra một `List` bên ngoài hợp lệ hoàn toàn không đủ để bảo đảm các đối tượng bên trong phù hợp với một type argument mong muốn.

## 12. Kiểm tra một lần chưa đủ nếu dữ liệu có thể thay đổi

Giả sử một thread xác nhận mọi phần tử là `String`, nhưng thread khác sau đó thay đổi danh sách qua raw reference. Kiểm tra đã đúng **tại một thời điểm**, song danh sách hiện tại có thể không còn đúng.

Ngay cả khi chỉ một luồng, một API khác giữ alias tới `ArrayList` cũng có thể thêm phần tử sau khi hàm validation trả về.

Nếu cần một kết quả ổn định:

1. **Nắm rõ quyền sở hữu dữ liệu**: ai có thể thay đổi collection?
2. **Kiểm tra trong thời điểm/điều kiện nhất quán**: không duyệt danh sách đang bị thay đổi không được kiểm soát rồi tưởng đó là một snapshot nguyên tử.
3. **Sao chép các phần tử đã qua kiểm tra** vào collection mới có kiểu tương ứng, thay vì cast tham chiếu gốc.
4. **Ngăn sửa cấu trúc bản sao** nếu kết quả phải giữ hợp đồng lâu dài: dùng `List.copyOf` khi phần tử không null, hoặc view không sửa được trên bản sao phù hợp.

`List.copyOf` là bản sao **nông** và không làm các object bên trong trở nên bất biến. Với `String`, phần tử vốn bất biến nên kết quả tương đối đơn giản; với `List<Person>`, một `Person` có thể vẫn thay đổi nội trạng. Validation kiểu class không đồng nghĩa validation toàn bộ trạng thái của object.

Một số API như `Collections.checkedList(list, String.class)` tạo wrapper kiểm tra kiểu của **các lần thêm qua wrapper đó**, nhưng không xác nhận tự động mọi dữ liệu đã có trong list và không ngăn một alias khác ghi thẳng vào list gốc. Đây là cơ chế phòng ngừa tương lai, khác với việc duyệt để xác minh mọi phần tử hiện có.

## 13. Reflection và generic metadata không xác nhận nội dung

Reflection có thể đọc thông tin một trường được **khai báo** là `List<String>`:

```java
class Sample {
    java.util.List<String> names;
}
```

`Sample.class.getDeclaredField("names").getGenericType()` có thể trả về dạng biểu diễn `List<String>` trong metadata `Signature`, còn `getType()` thường trả về raw runtime interface `List`.

Nhưng thông tin đó mô tả **kiểu khai báo của trường**, không phải bằng chứng collection gắn vào trường lúc chạy chỉ chứa `String`. Raw type, unchecked conversion, reflection hoặc nguồn dữ liệu không tin cậy vẫn có thể phá bất biến generic mà compiler đã bảo vệ trong mã nguồn bình thường.

Vì thế, reflection giúp hiểu **hợp đồng kiểu dự kiến**, nhưng khi nhận dữ liệu từ ranh giới không đáng tin, việc xác thực các phần tử vẫn phụ thuộc dữ liệu thực tế và chiến lược kiểm tra đã chọn.

## 14. Thiết kế API: cần kiểm tra kiểu ở đâu?

Nếu API nhận một `List<String>` từ **mã nguồn Java được biên dịch an toàn**, compiler đã kiểm tra phần lớn quan hệ type argument ở điểm gọi. Không cần chạy kiểm tra toàn bộ phần tử ở mọi phương thức chỉ để lặp lại công việc của compiler, trừ khi API có yêu cầu khác (như không cho null, phải đúng định dạng, chống dữ liệu bị làm bẩn).

Nếu API nhận `Object`, raw `List`, dữ liệu JSON được ánh xạ động hoặc đầu vào có thể qua unchecked operation, hãy xác định rõ:

| Trường hợp | Kiểm tra phù hợp | Ý nghĩa |
| --- | --- | --- |
| Chỉ cần biết có phải danh sách | `instanceof List<?>` | Kiểm tra interface bên ngoài |
| Cần mọi phần tử là String | Kiểm tra ngoài, rồi `allMatch(String.class::isInstance)` | Xác minh dữ liệu tại thời điểm duyệt |
| Cần ít nhất một phần tử và mọi phần tử là String | Thêm `!list.isEmpty()` | Tránh việc danh sách rỗng qua validation |
| Cho phép phần tử null | Kiểm tra `item == null` **hoặc** `item instanceof String` | Nêu rõ null policy |
| Cần trả `List<String>` ổn định | Xác minh, cast từng phần tử rồi sao chép | Có collection mới mang kiểu tĩnh đúng |
| Cần `List<List<String>>` | Kiểm tra từng tầng và các phần tử ở lá | Xác minh cấu trúc lồng nhau |
| Cần xác minh nghiệp vụ | Thêm điều kiện nội dung/format/giới hạn | Loại phần tử đúng vẫn có thể sai nghiệp vụ |

Nếu dữ liệu đã đi qua một parser/schema validator tin cậy có hợp đồng rõ ràng, việc xác minh có thể được đặt tại **ranh giới nhập dữ liệu**, rồi luồng nội bộ duy trì invariants bằng type system và ownership. Không nhất thiết phải lặp kiểm tra nặng khắp mọi tầng ứng dụng.

## 15. Những sai lầm thường gặp

**Sai lầm 1: “`instanceof List<?>` trả true nghĩa là `List<String>`.”** Không. Nó chỉ xác nhận object thực hiện `List`, không kiểm tra type argument.

**Sai lầm 2: “Chỉ cần phần tử đầu tiên là String.”** Không. Danh sách có thể bắt đầu bằng `String` rồi chứa `Integer` ở vị trí sau, nên kiểm tra phải bao phủ mọi phần tử được yêu cầu.

**Sai lầm 3: “`anyMatch` và `allMatch` giống nhau.”** Không. `anyMatch` kiểm tra tồn tại ít nhất một phần tử thỏa; `allMatch` yêu cầu tất cả thỏa.

**Sai lầm 4: “Danh sách rỗng phải bị coi là không phải `List<String>`.”** Điều đó là quyết định hợp đồng. Mệnh đề “mọi phần tử là String” đúng trên tập rỗng; nếu yêu cầu có dữ liệu, cần thêm `!isEmpty()`.

**Sai lầm 5: “Generic `List<String>` mặc định cấm null.”** Không. Java type argument `String` không tự cấm null. Phải đặt quy tắc riêng nếu không cho phép null.

**Sai lầm 6: “Cast `(List<String>)` đã kiểm tra mọi phần tử.”** Không. Do type erasure, type argument không được xác minh như một phần của cast thông thường.

**Sai lầm 7: “Reflection thấy `List<String>` nghĩa là dữ liệu chắc chắn chỉ chứa String.”** Không. Reflection có thể thấy metadata khai báo, không xác nhận nội dung runtime.

**Sai lầm 8: “Kiểm tra xong thì collection mãi mãi an toàn.”** Không nếu collection hoặc đối tượng bên trong còn mutable, có alias hoặc có thao tác không an toàn. Cần kiểm soát mutation hoặc snapshot.

**Sai lầm 9: “`Class<T>` biểu diễn được mọi generic type lồng nhau.”** Không. `Class<String>` giúp kiểm tra `String`; kiểu như `List<List<String>>` đòi mô tả cấu trúc và validation nhiều tầng.

## 16. Bài tập tự kiểm tra

**Câu 1.** `Object x = List.of("A", 7); x instanceof List<?>;` là `true` hay `false`?

`true`: lớp bao ngoài là một danh sách. Kết quả không phụ thuộc việc danh sách chứa kiểu phần tử nào.

**Câu 2.** Với `List.of("A", 7)`, `allMatch(String.class::isInstance)` cho kết quả gì?

`false`, vì `7` là `Integer`, không phải `String`.

**Câu 3.** Vì sao `List.of().stream().allMatch(String.class::isInstance)` là `true`?

Vì không có phần tử phản ví dụ; đây là định nghĩa của kiểm tra “mọi phần tử” trên tập rỗng.

**Câu 4.** `List<String>` có cho phép `null` không?

Kiểu `List<String>` tự nó không cấm `null`. Khả năng chèn null phụ thuộc implementation và hợp đồng collection; `List.of` và `List.copyOf` không cho phép phần tử null.

**Câu 5.** Nếu đã kiểm tra `List<?>` chứa toàn `String`, có thể gán trực tiếp nó cho `List<String>` không?

Không. Kiểu tĩnh vẫn là `List<?>`; cần cơ chế chuyển đổi có chứng minh an toàn như kiểm tra từng phần tử rồi sao chép.

**Câu 6.** `Object x = List.of(List.of("A"), List.of(9)); x instanceof List<?>;` có đủ để xác nhận `List<List<String>>` không?

Không. Cần kiểm tra từng phần tử ngoài là `List<?>` và tất cả phần tử của từng danh sách con là `String`.

**Câu 7.** `String[]` khác `List<String>` ở khả năng kiểm tra runtime như thế nào?

`String[]` giữ component type `String` tại runtime; `List<String>` không có runtime class riêng giữ type argument `String` cho phép `instanceof List<String>` như cách kiểm tra với `Object` chưa biết kiểu.

## 17. Quy tắc áp dụng

```text
Object đầu vào
    |
    v
instanceof List<?> ?
    | không -> từ chối đầu vào
    | có
    v
Duyệt từng phần tử
    | gặp sai kiểu -> từ chối / trả false
    | tất cả hợp lệ
    v
Nếu chỉ cần câu trả lời -> true
Nếu cần List<T> đáng tin -> cast từng phần tử và tạo bản sao phù hợp
```

**Kết luận:** `instanceof List<?>` chỉ kiểm tra **đối tượng tổng thể** có là danh sách hay không. Khi hợp đồng yêu cầu **mọi phần tử** mang một kiểu cụ thể, Java phải kiểm tra các phần tử thật hoặc dựa vào một nguồn dữ liệu đã bảo đảm hợp đồng. Type erasure khiến một type argument như `String` không được xác minh trực tiếp qua runtime class của `List`. Hãy phân biệt kiểm tra bên ngoài, kiểm tra từng phần tử và tạo bản sao đã xác thực; đồng thời nêu rõ cách xử lý danh sách rỗng, null, dữ liệu lồng nhau và mutation.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="runtime-types-007">Giới hạn của mẫu kiểu và ép kiểu sau xóa kiểu</a>

<details>
<summary>Xem chi tiết</summary>

# Giới hạn của mẫu kiểu và ép kiểu sau xóa kiểu trong Java

Java cho phép kiểm tra kiểu lúc chạy bằng `instanceof`, ép kiểu bằng cast và từ Java 16 có **pattern matching for `instanceof`** để kết hợp phép kiểm tra với việc tạo biến đã được thu hẹp kiểu. Nhưng Java Generics thông thường dùng **type erasure (xóa kiểu)**: JVM thường không lưu đối số kiểu cụ thể như `String` trong runtime class của một `ArrayList<String>`.

Vì vậy, có sự khác biệt rất lớn giữa hai câu hỏi:

- “Giá trị này có phải một `List`?” — có thể kiểm tra trực tiếp bằng `instanceof List<?>`.
- “Giá trị này có phải một `List<String>` chứa toàn phần tử `String`?” — không thể kết luận chỉ từ phép `instanceof` trên một giá trị `Object`; cần hợp đồng dữ liệu an toàn hoặc kiểm tra phần tử.

Đặc biệt, **ép kiểu `(List<String>) value` không phải phép xác thực mọi phần tử**. Với nguồn dữ liệu không đáng tin, ép kiểu unchecked có thể thành công trước rồi phát sinh `ClassCastException` ở chỗ đọc phần tử.

## 1. Phép `instanceof` kiểm tra được điều gì?

Toán tử `instanceof` dựa trên thông tin kiểu còn có thể kiểm tra tại runtime. Khi giá trị là một đối tượng chứa thông tin runtime class, ta thường kiểm tra được lớp hoặc interface không cần type argument cụ thể:

```java
// Các câu lệnh minh họa; import java.util.List.
Object text = "Java";
Object items = List.of("An", 42);

boolean a = text instanceof String;  // true
boolean b = items instanceof List<?>; // true
boolean c = items instanceof List;    // true, nhưng List là raw type
```

Với `items`, hai biểu thức kiểm tra `List` trả `true` vì đối tượng thực là một danh sách. Điều này **không chứng minh phần tử thứ hai là `String`**; trong ví dụ đó phần tử thứ hai là `Integer`.

Kiểu `List<?>` là một **reifiable type** vì wildcard `?` không bị ràng buộc: kiểm tra runtime chỉ cần xác nhận đây là một `List`. Trong khi đó, `List<String>` đòi biết đối số kiểu cụ thể `String`, thứ mà đối tượng `List` thông thường không mang như một class runtime riêng.

Một kiểu reifiable không có nghĩa dữ liệu luôn an toàn. Raw `List` cũng reifiable, nhưng gọi `raw.add(42)` có thể gây heap pollution nếu cùng danh sách đang được mã khác sử dụng dưới kiểu `List<String>`.

## 2. Pattern matching for `instanceof` bổ sung điều gì?

Trước khi có pattern matching, lập trình viên thường viết:

```java
// Snippet đặt trong một phương thức có biến Object value.
if (value instanceof String) {
    String text = (String) value;
    System.out.println(text.length());
}
```

Từ Java 16 (và dùng bình thường trên Java 17+), có thể viết:

```java
if (value instanceof String text) {
    System.out.println(text.length());
}
```

`String text` là **type pattern**. Khi phép kiểm tra thành công, Java tạo một pattern variable `text` đã có kiểu `String`, nhờ đó tránh phải cast lặp lại.

Mẫu kiểu chỉ tạo biến khi **phép kiểm tra tương ứng hợp lệ**. Nó không thêm năng lực đọc type argument generic bị xóa. Chẳng hạn:

```java
Object value = java.util.List.of("An");

if (value instanceof java.util.List<?> items) {
    System.out.println(items.size()); // Hợp lệ
}

// Không hợp lệ với Object value:
// if (value instanceof java.util.List<String> items) { ... }
```

Trong nhánh kiểm tra `List<?> items`, compiler biết `items` là `List<?>`; mỗi `items.get(i)` chỉ được dùng như `Object` nếu chưa kiểm tra thêm. Nó **không suy ra `items` là `List<String>`**.

## 3. Vì sao mẫu `List<String>` không dùng như mẫu `List<?>`?

Với một giá trị kiểu `Object`, đoạn sau không thể dùng làm phép kiểm tra phần tử runtime:

```java
Object input = java.util.List.of("A", "B");

// KHÔNG BIÊN DỊCH trong ngữ cảnh này:
// if (input instanceof java.util.List<String> strings) {
//     System.out.println(strings.get(0));
// }
```

Lý do: `List<String>` là **non-reifiable parameterized type**. JVM không có đủ thông tin từ riêng runtime class `List` để xác định mọi phần tử tuân thủ `String`. Java sẽ không chấp nhận một kiểm tra `instanceof` như thể có thể kiểm tra điều đó.

Cách viết hợp lệ:

```java
if (input instanceof java.util.List<?> values) {
    for (Object element : values) {
        if (element instanceof String text) {
            System.out.println(text.toUpperCase(java.util.Locale.ROOT));
        } else {
            System.out.println("Phần tử không phải String");
        }
    }
}
```

Lúc này có **hai tầng kiểm tra**: tầng ngoài chứng minh là `List`; tầng trong dùng `instanceof String` cho từng giá trị. Đối số `String` của kiểu generic ban đầu không được JVM tự khôi phục.

### Một sắc thái quan trọng của `instanceof` từ Java 16

Với một biểu thức có **kiểu tĩnh đã biết rất cụ thể**, các quy tắc tương thích kiểu khi biên dịch có thể cho phép một số cách dùng `instanceof` liên quan kiểu tham số hóa non-reifiable. Điều đó không tạo phép kiểm tra type argument mới ở runtime.

Do đó, không nên ghi nhớ quá mức rằng “mọi cú pháp `instanceof List<String>` đều sai trong mọi ngữ cảnh”. Quy tắc thực hành chính xác cho việc kiểm tra một giá trị `Object` chưa biết là: **dùng kiểu reifiable như `List<?>` rồi xác minh nội dung nếu cần**. Với type pattern, compiler còn áp dụng quy tắc riêng về tính tương thích và pattern variable.

## 4. Pattern variable có phạm vi sử dụng theo luồng điều khiển

Biến được khai báo trong type pattern không có phạm vi giống biến local bình thường. Nó **chỉ được dùng khi compiler chứng minh phép kiểm tra pattern đã thành công**.

### 4.1. Nhánh `if`

```java
static void show(Object input) {
    if (input instanceof String text) {
        System.out.println(text.length()); // text đã được xác nhận là String
    }
    // System.out.println(text); // Không hợp lệ: text không có phạm vi tại đây
}
```

### 4.2. Kết hợp với `&&`

```java
static boolean nonEmptyList(Object input) {
    return input instanceof java.util.List<?> values
            && !values.isEmpty();
}
```

Vế phải `&&` chỉ chạy khi vế trái `true`, nên biến pattern `values` chắc chắn đã được gán và có thể dùng ở vế phải.

Không được tự thay `&&` bằng `||` rồi dùng biến ở vế phải:

```java
// KHÔNG BIÊN DỊCH:
// return input instanceof java.util.List<?> values
//         || values.isEmpty();
```

Khi toán tử `||` đánh giá vế phải, vế trái đã `false`; biến pattern chưa có bảo đảm được tạo.

### 4.3. Guard clause với `!` và `return`

```java
static int listSizeOrZero(Object input) {
    if (!(input instanceof java.util.List<?> values)) {
        return 0;
    }
    return values.size(); // Hợp lệ vì đường đến đây bảo đảm pattern khớp
}
```

Kiểu kiểm tra này hữu ích khi cần kiểm tra dữ liệu ở đầu hàm rồi sử dụng biến đã thu hẹp ở phần còn lại.

### 4.4. `null` không khớp type pattern

```java
Object input = null;
boolean matched = input instanceof String; // false
```

Pattern matching không tự xử lý `null` theo kiểu một `String` hợp lệ. Nếu `null` là giá trị được phép về nghiệp vụ, cần một nhánh hoặc chính sách riêng.

### 4.5. Pattern hiển nhiên đúng cũng có thể bị từ chối

Nếu biểu thức đã có kiểu tĩnh là subtype của pattern type, một type pattern như `String s; if (s instanceof String text)` không được phép vì phép thu hẹp kiểu đã hiển nhiên ở nguồn. Điều này là quy tắc **tính tương thích của pattern tại compile time**, không liên quan đến việc JVM quên mất `String`.

## 5. Ép kiểu sau erasure: phần kiểm tra được và phần không kiểm tra được

Một cast Java có thể là **checked cast** hoặc **unchecked cast**, tùy việc JVM có đủ thông tin để kiểm tra toàn bộ kiểu đích hay không.

Ví dụ checked cast của một class reifiable:

```java
Object input = "Java";
String text = (String) input; // Runtime kiểm tra được String
```

Nếu `input` thực tế là `Integer`, cast sang `String` sẽ ném `ClassCastException`.

Nhưng với một kiểu generic cụ thể:

```java
Object input = java.util.List.of("Java", 42);

// Đây là UNCHECKED cast, compiler sẽ cảnh báo nếu không suppress.
java.util.List<String> names = (java.util.List<String>) input;
```

Cast này không thể xác minh mọi phần tử thành `String`; nó chỉ kiểm tra được phần thích hợp của kiểu runtime, ví dụ đối tượng có là `List` hay không. Dữ liệu sai kiểu có thể nằm trong list và chưa gây lỗi ngay tại câu lệnh cast.

Sau đó:

```java
// Snippet tiếp tục:
String second = names.get(1); // ClassCastException khi Integer bị xem là String
```

Compiler xem kết quả `get` của `List<String>` là `String`. Bytecode sau erasure có thể chứa thao tác `checkcast java/lang/String` ở vị trí tiêu thụ kết quả. Vì thế **địa điểm lỗi runtime có thể khác với vị trí cast unchecked ban đầu**.

Cần phân biệt ba điều:

1. Cast tới `String` có thể xác minh kiểu đối tượng ngay tại thời điểm cast.
2. Cast tới `List<String>` không xác minh phần `String` trong type argument theo cách đó.
3. `List<?> values = (List<?>) input;` có kiểu đích reifiable: cast kiểm tra được đối tượng có phải `List`, nhưng vẫn không xác minh loại của từng phần tử.

Điểm (3) thường là cách làm tốt khi tiếp nhận một danh sách từ kiểu `Object` mà chưa biết kiểu phần tử.

## 6. Chương trình Java 17+ chạy được: pattern và unchecked cast

Lưu toàn bộ mã sau thành **`PatternErasureDemo.java`**:

```java
import java.util.ArrayList;
import java.util.List;

public class PatternErasureDemo {
    static boolean allStrings(Object input) {
        if (!(input instanceof List<?> values)) {
            return false;
        }
        for (Object value : values) {
            if (!(value instanceof String)) {
                return false;
            }
        }
        return true;
    }

    static List<String> checkedCopy(Object input) {
        if (!(input instanceof List<?> values)) {
            throw new IllegalArgumentException("Not a list");
        }

        List<String> result = new ArrayList<>();
        for (int i = 0; i < values.size(); i++) {
            Object value = values.get(i);
            if (!(value instanceof String s)) {
                throw new IllegalArgumentException("Bad element at " + i);
            }
            result.add(s);
        }
        return List.copyOf(result);
    }

    public static void main(String[] args) {
        Object mixed = new ArrayList<>(List.of("Java", 42));

        System.out.println("Outer List<?>: "
                + (mixed instanceof List<?>));
        System.out.println("All Strings: " + allStrings(mixed));
        System.out.println("Null is List<?>: " + (null instanceof List<?>));

        if (mixed instanceof List<?> values) {
            System.out.println("List size: " + values.size());
            Object first = values.get(0);
            System.out.println("First is String: "
                    + (first instanceof String));
        }

        try {
            @SuppressWarnings("unchecked")
            List<String> assumed = (List<String>) mixed;
            String second = assumed.get(1);
            System.out.println(second);
        } catch (ClassCastException exception) {
            System.out.println("Unchecked read: ClassCastException");
        }

        try {
            checkedCopy(mixed);
        } catch (IllegalArgumentException exception) {
            System.out.println("Validation: " + exception.getMessage());
        }

        System.out.println("Checked copy: "
                + checkedCopy(List.of("An", "Binh")));
        System.out.println("Empty all Strings: "
                + allStrings(List.of()));

        try {
            String.class.cast(42);
        } catch (ClassCastException exception) {
            System.out.println("Class token: ClassCastException");
        }
    }
}
```

Chạy với JDK 17 trở lên:

```bash
javac -encoding UTF-8 -Xlint:unchecked PatternErasureDemo.java
java PatternErasureDemo
```

Standard output:

```text
Outer List<?>: true
All Strings: false
Null is List<?>: false
List size: 2
First is String: true
Unchecked read: ClassCastException
Validation: Bad element at 1
Checked copy: [An, Binh]
Empty all Strings: true
Class token: ClassCastException
```

Bài thử cho thấy:

- `instanceof List<?>` xác nhận lớp ngoài mà không khẳng định mọi phần tử là `String`.
- Pattern variable `values` có kiểu `List<?>`, vì vậy cần đọc phần tử thành `Object` trước khi kiểm tra `instanceof String`.
- Ép `mixed` thành `List<String>` không tự quét danh sách; khi đọc `Integer` qua kiểu `String`, `ClassCastException` mới xuất hiện.
- `checkedCopy` thực hiện kiểm tra từng phần tử, báo lỗi tại vị trí 1 nếu sai kiểu, và trả một `List<String>` mới khi toàn bộ dữ liệu thỏa.
- `allStrings(List.of())` là `true` theo logic “mọi phần tử đều là String” của tập rỗng. Nếu yêu cầu dữ liệu không rỗng, cần kiểm tra thêm.
- `String.class.cast(42)` kiểm tra được class runtime cụ thể và ném `ClassCastException` ngay lúc cast.

Tệp có một lớp `public` trùng tên file, dùng những API và cú pháp có trong Java 17. Lệnh `-Xlint:unchecked` hữu ích khi chủ động thử các cast không được suppress; trong ví dụ chạy, `@SuppressWarnings("unchecked")` chỉ được dùng ở đúng khai báo cố ý không an toàn để tập trung minh họa hành vi.

## 7. Vì sao `Class<T>` không tự giải quyết được generic lồng nhau?

Nếu cần kiểm tra một phần tử theo class runtime, `Class<T>` là cơ chế hữu ích:

```java
static <T> T castOne(Object input, Class<T> type) {
    return type.cast(input);
}

// castOne("Java", String.class) -> "Java"
// castOne(42, String.class) -> ném ClassCastException
```

`String.class` mô tả class `String` tại runtime. Nhưng không có class literal Java `List<String>.class` để kiểm tra đối số `String` trong một collection một cách tương đương.

Có thể dùng `List.class.isInstance(input)` để xác nhận lớp ngoài, và `String.class.isInstance(item)` để xác nhận từng phần tử. Với generic lồng nhau như `List<List<String>>`, phải kiểm tra lần lượt danh sách ngoài, các danh sách con và các phần tử ở lá.

Ngoài ra, `Class<T>` thường kiểm tra theo **tính tương thích của class**. `Number.class.isInstance(Integer.valueOf(42))` là `true`, dù runtime class cụ thể của giá trị là `Integer`. Nếu hợp đồng yêu cầu **đúng chính xác một class**, cần tự quy định chính sách chặt hơn, thay vì mặc định cho mọi subclass là sai.

## 8. Vấn đề với type variable `T` trong pattern và cast

Một phương thức generic không thể trực tiếp dùng type variable như class runtime để kiểm tra:

```java
// Snippet minh họa phương thức generic KHÔNG HỢP LỆ:
static <T> boolean isType(Object input) {
    // return input instanceof T; // Không thể kiểm tra type variable T
    return false;
}
```

Câu `return false` chỉ giúp snippet có thân phương thức hợp lệ; dòng quan trọng là lệnh `instanceof T` bị chú thích vì sai. Dù `T extends Number`, điều đó không cho biết `T` là `Integer`, `Double` hay một subtype khác khi chạy.

Mẫu `input instanceof T item` cũng không giúp lấy lại kiểu đã bị xóa. Nếu cần kiểm tra class của giá trị do caller yêu cầu, hãy truyền một token:

```java
static <T> boolean matches(Object input, Class<T> type) {
    return type.isInstance(input);
}
```

Token này giải quyết kiểm tra **một class cụ thể**. Đừng suy ra nó đại diện được một type argument lồng nhau như `Map<String, List<Integer>>`.

Tương tự, một cast sang `T` trong mã generic có thể chỉ được kiểm tra theo bound đã xóa hoặc trở thành unchecked trong những tình huống nhất định. Nó không phải bằng chứng độc lập rằng đối tượng thỏa mọi ràng buộc kiểu mà caller suy luận được ở cấp mã nguồn.

## 9. Raw type và heap pollution làm cast càng dễ đánh lừa

Generic bình thường ngăn nhiều lỗi lúc biên dịch:

```java
java.util.List<String> names = new java.util.ArrayList<>();
names.add("An");
// names.add(42); // Lỗi biên dịch
```

Nhưng một raw alias có thể ghi dữ liệu không tương thích:

```java
java.util.List<String> names = new java.util.ArrayList<>();
java.util.List raw = names;
raw.add(42); // Unchecked warning
// String s = names.get(0); // Có thể ném ClassCastException
```

Khi đối tượng được dùng dưới kiểu `List<String>` nhưng chứa `Integer`, ta có **heap pollution**. Việc `names` ở nguồn trông đúng generic không bảo đảm mọi phần tử mãi mãi đúng nếu có đường truy cập không an toàn.

Cũng không nên nghĩ rằng cast `(List<String>) raw` sẽ sửa heap pollution. Cast ấy có thể không ném lỗi ngay, vì runtime không xác minh tham số `String` của list. Muốn khôi phục tính đáng tin, cần kiểm tra nội dung rồi sao chép/chuẩn hóa dữ liệu vào một collection mới.

### Cảnh báo unchecked nói gì?

`unchecked` nghĩa là **compiler không thể chứng minh đầy đủ an toàn kiểu** của phép chuyển hoặc lời gọi. Nó là cảnh báo hữu ích, không nhất thiết là lỗi cú pháp.

Các hướng xử lý thường là:

1. Thay raw `List` bằng `List<?>` nếu chỉ đọc hoặc cần kiểm tra cấu trúc.
2. Dùng kiểu tham số hóa rõ ràng tại API nhận/trả dữ liệu.
3. Duyệt, kiểm tra và chuyển từng phần tử khi đọc dữ liệu chưa đáng tin.
4. Thu hẹp phạm vi `@SuppressWarnings("unchecked")` vào đoạn được chứng minh an toàn và giải thích invariant.
5. Tránh cast unchecked dây chuyền làm các tầng API sau hiểu sai tính an toàn dữ liệu.

## 10. Khi nào có thể chuyển từ `List<?>` sang `List<String>`?

Sau khi kiểm tra:

```java
Object input = java.util.List.of("An", "Binh");
if (input instanceof java.util.List<?> values) {
    boolean allStrings = values.stream()
            .allMatch(String.class::isInstance);
    // values vẫn có kiểu tĩnh List<?> dù allStrings là true.
}
```

Compiler Java **không tự nâng kiểu** từ `List<?>` thành `List<String>` chỉ vì một boolean predicate đã trả `true`. Có hai phương án đáng cân nhắc:

- Nếu cần trả về `List<String>` có bảo đảm, hãy kiểm tra **từng phần tử** rồi thêm vào một `ArrayList<String>` mới. Kết quả có kiểu tĩnh đúng, không cần unchecked cast; có thể dùng `List.copyOf` nếu muốn collection kết quả không thể sửa cấu trúc.
- Nếu chỉ cần đọc mà không cần khẳng định toàn bộ phần tử, giữ `List<?>` và kiểm tra từng giá trị lúc sử dụng.

Ngay cả khi tất cả phần tử đang là String, một danh sách **mutable** có thể bị thay đổi qua alias khác sau lúc kiểm tra. Vì vậy, kiểm tra rồi cast tham chiếu gốc sang `List<String>` có thể không an toàn nếu người khác còn sửa danh sách. Một bản sao sau validation giúp tách cấu trúc kết quả khỏi nguồn, nhưng việc tạo snapshot nhất quán vẫn đòi hỏi kiểm soát concurrent mutation trong thời gian duyệt.

### Trường hợp `null`

`String.class.isInstance(null)` là `false`, và `null instanceof String` cũng là `false`. Vì vậy, validator trong ví dụ chạy coi `null` là không hợp lệ.

Một `List<String>` nói chung **không mặc nhiên cấm null**: Java type parameter không phải non-null type annotation. Nếu nghiệp vụ cho phép null, phải mô tả predicate riêng như `value == null || value instanceof String`; đồng thời nhớ `List.copyOf` từ chối phần tử null.

## 11. Tại sao `List<?>[]` có thể tạo nhưng `List<String>[]` không?

Mảng Java biết **runtime component type**, trong khi generic element type `List<String>` không reifiable. Vì thế:

```java
// Hợp lệ:
java.util.List<?>[] groups = new java.util.List<?>[2];
groups[0] = java.util.List.of("An");
groups[1] = java.util.List.of(42);

// Không hợp lệ:
// java.util.List<String>[] names = new java.util.List<String>[2];
```

`List<?>` là reifiable, nên array có component type phù hợp để runtime kiểm tra. Nhưng `List<String>` không reifiable, khiến tạo trực tiếp array với thành phần `List<String>` bị cấm.

Một khác biệt nổi bật:

```java
Object[] array = new String[1];
try {
    array[0] = 42;
} catch (ArrayStoreException exception) {
    System.out.println("ArrayStoreException");
}
```

Với array runtime `String[]`, JVM bắt lỗi ngay khi ghi giá trị sai loại. Với `ArrayList<String>` bị raw alias làm bẩn, lỗi có thể chỉ xuất hiện khi đọc qua cast thành `String`. Cả hai cơ chế đều liên quan kiểm tra kiểu, nhưng **loại thông tin runtime được giữ là khác nhau**.

## 12. Reflection nhìn thấy `List<String>` có mâu thuẫn với erasure không?

Không. Class file có thể chứa metadata về **khai báo generic**, ví dụ thuộc tính `Signature`. Reflection có thể đọc kiểu khai báo của trường:

```java
class FieldHolder {
    java.util.List<String> names;
}
```

Ví dụ, `FieldHolder.class.getDeclaredField("names").getGenericType()` có thể mô tả `List<String>`; trong khi `getType()` phản ánh raw interface `List`.

Thông tin về khai báo không có nghĩa rằng mọi đối tượng list gắn vào trường đều mang một runtime class riêng `List<String>`, hoặc mọi giá trị được đưa vào qua raw/unchecked code đều là `String`.

Vì vậy, nếu nhận một đối tượng chưa rõ nguồn, reflection chỉ giúp biết **hợp đồng được khai báo ở đâu đó**; việc xác minh dữ liệu thực tế vẫn cần kiểm tra tương ứng.

## 13. Những lỗi hiểu sai phổ biến

**Nhầm 1 — “Pattern matching `instanceof` đọc được type argument generic.”** Sai. Nó chỉ gắn một biến khi phép kiểm tra kiểu runtime hợp lệ; `List<?> values` không trở thành `List<String>`.

**Nhầm 2 — “Dùng `instanceof List<?>` là đủ để biết danh sách chứa toàn String.”** Sai. Nó chỉ xác nhận kiểu tổng thể của object.

**Nhầm 3 — “Ép kiểu `(List<String>)` kiểm tra từng phần tử.”** Sai. Đây có thể là unchecked cast; lỗi kiểu có thể lộ ra khi lấy phần tử thành `String`.

**Nhầm 4 — “Pattern variable sử dụng được ở mọi nơi sau `if`.”** Sai. Phạm vi phụ thuộc luồng điều khiển; `&&` và guard clause có quy tắc khác `||`.

**Nhầm 5 — “Type variable `T` là một class runtime dùng trực tiếp cho `instanceof`.”** Sai. `T` không phải reifiable type và không có `T.class` thông thường.

**Nhầm 6 — “Báo `ClassCastException` tại thời điểm cast chưa xảy ra thì dữ liệu đã đúng kiểu.”** Sai. Unchecked cast có thể không kiểm tra được type argument; dữ liệu sai chỉ bộc lộ khi dùng đến.

**Nhầm 7 — “`List<String>` không reifiable nên compiler không kiểm tra generic.”** Sai. Hệ thống kiểu generic vẫn kiểm tra tại compile time; non-reifiable nói tới khả năng kiểm tra đầy đủ tại runtime.

**Nhầm 8 — “Reflection thấy `List<String>` nên có thể ép mọi list bất kỳ sang `List<String>`.”** Sai. Metadata của khai báo khác với chứng cứ từng phần tử của object runtime.

**Nhầm 9 — “Sau khi validate toàn bộ list, mọi lần đọc tương lai đều an toàn.”** Không bảo đảm nếu list bị sửa qua alias khác. Cần kiểm soát mutation hoặc tạo snapshot/bản sao đáng tin.

## 14. Bài tập tự kiểm tra

**Câu 1.** `Object x = List.of("A", 7); x instanceof List<?>;` cho kết quả gì?

`true`. Chỉ kiểu bên ngoài được kiểm tra; `Integer` trong danh sách không làm `instanceof List<?>` thành `false`.

**Câu 2.** Với `Object x`, vì sao `x instanceof List<String> names` không phải cách kiểm tra toàn bộ phần tử?

Vì type argument `String` của `List<String>` không reifiable ở runtime theo cách kiểm tra ấy. Dùng `List<?>` và kiểm tra từng phần tử.

**Câu 3.** `Object x = List.of("A", 7); List<String> y = (List<String>) x;` có nhất thiết ném exception ngay ở cast không?

Không. Cast có thể thành công khi object là `List`; `ClassCastException` có thể chỉ xảy ra khi lấy phần tử `Integer` để dùng như `String`.

**Câu 4.** Tại sao trong `input instanceof List<?> values && !values.isEmpty()`, biến `values` được dùng trong vế phải?

Vì toán tử `&&` chỉ đánh giá vế phải khi pattern ở vế trái đã khớp, nên `values` có phạm vi hợp lệ ở đó.

**Câu 5.** `Class<String>` có cho phép kiểm tra `List<String>` toàn bộ chỉ bằng một lệnh `cast` không?

Không. `String.class` kiểm tra một đối tượng kiểu `String`; nó không mô tả đầy đủ tham số kiểu bên trong một `List` và không quét mọi phần tử.

**Câu 6.** Nếu hàm đã kiểm tra mọi phần tử `String`, tại sao `List<?> values` vẫn không tự thành `List<String>`?

Vì Java không dùng kết quả boolean của một validator bất kỳ để tự thay đổi generic type argument trong hệ thống kiểu tĩnh. Cần chuyển đổi có kiểm tra hoặc giữ kiểu wildcard.

## 15. Cách áp dụng khi viết API

Khi nhận dữ liệu, quyết định rõ **cần biết gì**:

1. Chỉ muốn biết đây là `List`: `input instanceof List<?>` là thích hợp.
2. Muốn xử lý mỗi phần tử tùy runtime class: dùng `List<?> values`, rồi pattern `element instanceof String s` hoặc pattern cho class phù hợp.
3. Muốn bảo đảm **mọi** phần tử là `String`: duyệt hết các phần tử và từ chối dữ liệu không thỏa.
4. Muốn trả `List<String>` đáng tin: kiểm tra và sao chép phần tử thành collection mới có kiểu tĩnh `List<String>`.
5. Muốn kiểm tra đối tượng kiểu `T` do caller chỉ định: dùng `Class<T>` khi `T` là class có thể được biểu diễn bằng token ấy; với generic lồng nhau, dùng schema hoặc validator cấu trúc rõ ràng.
6. Đang thấy `unchecked` warning: kiểm tra nguyên nhân và ranh giới tin cậy; không coi `@SuppressWarnings` là biện pháp làm sạch dữ liệu.

**Kết luận:** mẫu kiểu `instanceof` giúp Java viết mã kiểm tra class/interface runtime ngắn gọn và an toàn hơn, nhưng không đảo ngược **type erasure** để lấy lại đối số `T` hay `List<String>` của từng instance generic. Cast tới generic parameterized type có thể mang cảnh báo unchecked và không kiểm tra từng phần tử. Khi hợp đồng đòi một kiểu phần tử cụ thể, hãy kết hợp kiểm tra tổng thể, xác minh dữ liệu thực tế và chuyển đổi/sao chép có kiểm soát.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="runtime-types-008">Không suy ra đối số kiểu tùy ý từ getClass() của đối tượng</a>

<details>
<summary>Xem chi tiết</summary>

# Không suy ra đối số kiểu tùy ý từ `getClass()` của đối tượng

## 1. Mục tiêu học tập

Trong Java Generics, kiểu `Box<String>` và `Box<Integer>` giúp trình biên dịch kiểm tra những lời gọi ở **mã nguồn**. Nhưng phương thức `Object.getClass()` không tự trả về thông tin rằng một instance cụ thể của `Box` đã được khai báo là `Box<String>` hay `Box<Integer>`. Đây là giới hạn căn bản khi suy luận **đối số kiểu** (*type arguments*) ở runtime.

Sau bài này, bạn có thể:

- Phân biệt **runtime class**, **tham số kiểu khai báo** `T` và **đối số kiểu tại chỗ sử dụng** như `String`.
- Giải thích chính xác vì sao `box.getClass()` không phục hồi một cách tổng quát đối số generic gắn với biến `Box<String>`.
- Dùng `getClass()`, `getTypeParameters()`, `Field.getGenericType()`, `getGenericSuperclass()` và `ParameterizedType` đúng phạm vi.
- Nhận biết ngoại lệ **metadata từ khai báo lớp con** hoặc anonymous subclass: đôi khi reflection đọc được `Box<String>`, nhưng không phải lúc nào cũng xác định được `T` cụ thể.
- Chỉ ra vì sao kiểu thực của giá trị bên trong (`String`) **không đủ** để chứng minh kiểu generic của container.
- Chủ động bảo toàn metadata bằng `Class<T>` hoặc mẫu **type reference** khi chương trình thực sự cần thông tin kiểu ở runtime.
- Tránh các thiết kế reflection, serializer hoặc factory dựa trên suy đoán từ `getClass()`.

Bài sử dụng **Java 17+**. Các ví dụ hoàn chỉnh đã được biên dịch, chạy và đối chiếu với output thực tế.

## 2. Ba khái niệm cần tách rời

Xét lớp:

```java
class Box<T> {
    private T value;
    Box(T value) { this.value = value; }
    T get() { return value; }
}
```

Và hai chỗ sử dụng:

```java
Box<String> word = new Box<>("Java");
Box<Integer> count = new Box<>(42);
```

Ba câu hỏi khác nhau:

| Khái niệm | Ví dụ | Có ý nghĩa gì? |
| --- | --- | --- |
| **Runtime class** | `word.getClass()` | Định nghĩa lớp của đối tượng đang chạy: `Box` |
| **Tham số kiểu trong khai báo** | `T` của `class Box<T>` | Biến kiểu trong khai báo lớp, không phải giá trị `String` hoặc `Integer` |
| **Đối số kiểu trong mã nguồn** | `Box<String>`, `Box<Integer>` | Lựa chọn kiểu của một biểu thức, biến hoặc quan hệ kế thừa để compiler kiểm tra |
| **Runtime class của giá trị** | `word.get().getClass()` | Lớp thật của một giá trị hiện chứa trong hộp: `String`, nếu giá trị khác `null` |

**Câu kết luận trung tâm:** `word.getClass()` không tương đương một phép toán tưởng tượng `getActualTypeArgument(word)`. Java không có API chuẩn nào có thể khôi phục đối số generic tùy ý của **mọi** instance chỉ bằng `Object.getClass()`.

Cả hai lệnh `new Box<>("Java")` và `new Box<>(42)` tạo đối tượng của cùng runtime class `Box`, giả sử chúng tham chiếu cùng định nghĩa class trong cùng ngữ cảnh nạp lớp. Chúng là **hai instance độc lập**, nhưng có cùng `Class` metadata cho `Box`.

## 3. Vì sao `getClass()` không khôi phục đối số generic?

Java generics chủ yếu được triển khai theo **type erasure (xóa kiểu)**. Khi `T` không có giới hạn trên tường minh, `T` có erasure là `Object`. Compiler kiểm tra kiểu ở source, rồi sinh bytecode sử dụng các JVM descriptors thích hợp.

Mã nguồn:

```java
class Box<T> {
    private T value;
    Box(T value) { this.value = value; }
    T get() { return value; }
}
```

Bản phác thảo **chỉ để hiểu cơ chế** (không phải một lớp `BoxErased` thực sự phải tồn tại):

```java
class BoxErasedSketch {
    private Object value;
    BoxErasedSketch(Object value) { this.value = value; }
    Object get() { return value; }
}
```

Ở cấp JVM descriptor, những nơi dùng `T` không giới hạn tương ứng với `Object`:

| Thành phần | Dạng trong source | Descriptor JVM |
| --- | --- | --- |
| Field | `T value` | `Ljava/lang/Object;` |
| Constructor | `Box(T value)` | `(Ljava/lang/Object;)V` |
| Getter | `T get()` | `()Ljava/lang/Object;` |

Type erasure không có nghĩa mọi `String` bị biến thành đối tượng mới thuộc `Object.class`. Một `String` lưu trong field vẫn là `String` thật, và có thể được cast về `String` khi cần. Điều biến mất khỏi runtime class chung là **đối số kiểu cụ thể của mỗi cách sử dụng** `Box<T>`.

Vì không sinh một runtime class `Box<String>` riêng, phép so sánh thông thường sau trả `true`:

```java
Box<String> a = new Box<>("A");
Box<Integer> b = new Box<>(1);
boolean sameRuntimeClass = a.getClass() == b.getClass(); // true
```

Đừng nhầm `a.getClass() == b.getClass()` với `a == b`: hai biến `a`, `b` ở ví dụ là hai đối tượng khác nhau. Ngoài ra, Java có thể từ chối so sánh trực tiếp `a == b` vì hai kiểu tham số hóa không tương thích; muốn kiểm tra identity instance bất kể kiểu nguồn, có thể gán chúng vào `Object` trước.

## 4. Chương trình đầy đủ: kiểm chứng bằng Java và reflection

Tạo tệp **`RuntimeTypeArgumentDemo.java`**:

```java
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Objects;

public class RuntimeTypeArgumentDemo {
    static class Box<T> {
        private T value;
        Box(T value) { this.value = value; }
        T get() { return value; }
    }

    static final class StringBox extends Box<String> {
        StringBox(String value) { super(value); }
    }

    static <T> Box<T> makeAnonymous(T value) {
        return new Box<T>(value) {};
    }

    abstract static class TypeRef<T> {
        private final Type type;

        protected TypeRef() {
            Type parent = getClass().getGenericSuperclass();
            if (!(parent instanceof ParameterizedType p)) {
                throw new IllegalStateException(
                        "Missing parameterized superclass");
            }
            type = p.getActualTypeArguments()[0];
        }

        Type getType() { return type; }
    }

    public static void main(String[] args)
            throws ReflectiveOperationException {
        Box<String> text = new Box<>("Java");
        Box<Integer> number = new Box<>(42);

        System.out.println("same class: "
                + (text.getClass() == number.getClass()));
        System.out.println("class: " + text.getClass().getSimpleName());
        System.out.println("type parameter: "
                + Box.class.getTypeParameters()[0].getName());

        Field field = Box.class.getDeclaredField("value");
        System.out.println("erased field: "
                + field.getType().getTypeName());
        System.out.println("generic field: "
                + field.getGenericType().getTypeName());
        System.out.println("value classes: "
                + text.get().getClass().getSimpleName() + ", "
                + number.get().getClass().getSimpleName());
        System.out.println("null value: "
                + (new Box<String>(null).get() == null));

        Box<String> subclass = new StringBox("A");
        System.out.println("subclass class: "
                + subclass.getClass().getSimpleName());
        System.out.println("subclass superclass: "
                + subclass.getClass()
                        .getGenericSuperclass().getTypeName());

        Box<String> anonymous = new Box<String>("B") {};
        System.out.println("anonymous superclass: "
                + anonymous.getClass()
                        .getGenericSuperclass().getTypeName());

        Box<String> factoryBox = makeAnonymous("C");
        System.out.println("factory superclass: "
                + factoryBox.getClass()
                        .getGenericSuperclass().getTypeName());

        TypeRef<List<String>> captured = new TypeRef<List<String>>() {};
        System.out.println("captured nested type: "
                + captured.getType().getTypeName());
        System.out.println("generic list class only: "
                + List.of("X").getClass().getSimpleName());
    }
}
```

Biên dịch và chạy từ thư mục chứa tệp:

```bash
javac -Xlint:all RuntimeTypeArgumentDemo.java
java RuntimeTypeArgumentDemo
```

**Stdout đã kiểm chứng (JDK 17 ở môi trường kiểm thử):**

```text
same class: true
class: Box
type parameter: T
erased field: java.lang.Object
generic field: T
value classes: String, Integer
null value: true
subclass class: StringBox
subclass superclass: RuntimeTypeArgumentDemo$Box<java.lang.String>
anonymous superclass: RuntimeTypeArgumentDemo$Box<java.lang.String>
factory superclass: RuntimeTypeArgumentDemo$Box<T>
captured nested type: java.util.List<java.lang.String>
generic list class only: List12
```

**Chú ý tính di động của output:** dòng cuối `generic list class only: List12` cho biết tên lớp hiện thực nội bộ của `List.of("X")` trong JDK được kiểm thử; tên ấy **không phải hợp đồng API và có thể khác theo bản JDK**. Đừng sử dụng tên `List12` cho nghiệp vụ hay dùng nó để đoán kiểu phần tử.

### Đọc đúng từng bằng chứng

**`same class: true`:** `new Box<String>(...)` và `new Box<Integer>(...)` đều tạo `Box`. JVM không phải chuyên biệt hóa class theo từng đối số kiểu.

**`type parameter: T`:** `Box.class.getTypeParameters()` trả thông tin về tham số kiểu **được khai báo trong class**. Nó trả tên `T`, không trả `String` chỉ vì ta đang giữ `Box<String>`.

**`erased field: java.lang.Object`:** `Field.getType()` cho kiểu runtime bị xóa của field. Với field `T value` không bound, đây là `Object`.

**`generic field: T`:** `Field.getGenericType()` đọc metadata generic về **khai báo field**, nên có thể trả một `TypeVariable` tên `T`. Hai kết quả `Object` và `T` trả lời **hai câu hỏi khác nhau**.

**`value classes: String, Integer`:** `text.get().getClass()` và `number.get().getClass()` trả runtime class của **giá trị bên trong** chứ không trả type argument đã được gắn vào bản thân `Box`.

**`null value: true`:** với `new Box<String>(null)`, không có giá trị để gọi `getClass()`. Ngay cả nếu có giá trị, loại thực tại một thời điểm không chứng minh hợp đồng generic tổng quát.

Các dòng metadata lớp con và anonymous class được giải thích ở các phần sau; chúng là những trường hợp **có thông tin generic trong khai báo**, không bác bỏ type erasure.

## 5. Đối số kiểu của biến không phải một phần runtime identity

Hãy phân biệt:

```java
Box<String> first = new Box<>("A");
Box<CharSequence> second = new Box<>("B");
```

Một hộp `Box<CharSequence>` có thể hiện đang chứa `String` vì `String` implement `CharSequence`. Nếu bạn nhìn `second.get().getClass()`, kết quả có thể là `String.class`, nhưng điều đó không có nghĩa `second` được khai báo là `Box<String>` — nó có thể là `Box<CharSequence>`.

Ví dụ khác:

```java
Box<Number> measurement = new Box<>(Integer.valueOf(5));
Object item = measurement.get();
System.out.println(item.getClass().getSimpleName()); // Integer
```

Mặc dù giá trị thật là `Integer`, loại đối số nguồn vẫn là `Number`. Đoán `T = Integer` từ giá trị hiện tại sẽ sai.

Ngoài ra:

- Một `Box<Object>` có thể chứa `String` hôm nay, rồi `Integer` ngày mai nếu lớp có setter.
- Một `Box<String>` có thể chứa `null`, khiến `getClass()` của giá trị không dùng được.
- Một container có thể chứa subtype của kiểu đối số: quan sát subtype không chứng minh kiểu generic là subtype đó.
- Việc dùng unchecked cast hoặc raw type có thể phá hợp đồng generic, khiến kiểu giá trị không còn khớp với kiểu nguồn đã khai báo.

**Không tồn tại phép suy luận ngược tổng quát:** từ một vài giá trị runtime, không thể luôn xác định duy nhất kiểu tham số hóa mà compiler đã dùng cho biến hoặc biểu thức ở source.

## 6. `getClass()` trả cái gì chính xác?

`Object.getClass()` trả một đối tượng `java.lang.Class` đại diện cho runtime class **thực của receiver**. Nó không nhận đối số type token và không khai báo API kiểu `Class<Box<String>>` cho mọi lần gọi trên `Box<String>`.

Ví dụ:

```java
Box<String> text = new Box<>("Java");
System.out.println(text.getClass() == Box.class); // true
```

Nếu là lớp con thực sự:

```java
Box<String> special = new StringBox("Java");
System.out.println(special.getClass() == Box.class); // false
```

Trong ví dụ thứ hai, `getClass()` trả `StringBox.class` vì instance được tạo bởi constructor `StringBox`, không phải vì tham số hóa `String` khiến Java sinh class mới.

Điều này cũng đúng khi biến có kiểu interface:

```java
List<String> list = new java.util.ArrayList<>();
System.out.println(list.getClass().getName()); // java.util.ArrayList
```

Ở đây `getClass()` biết implementation runtime là `ArrayList`, không tự suy ra kiểu `String`. Một `ArrayList<Integer>` tạo trực tiếp từ cùng class loader cũng có runtime class `ArrayList`.

Không nên phụ thuộc tên lớp implementation của factory như `List.of(...)` để suy ra kiểu phần tử: Java được quyền lựa chọn implementation nội bộ khác nhau và có thể dựa vào kích thước danh sách, không phải generic type argument.

## 7. Reflection có thể đọc khai báo generic, không đọc một type argument “bí mật” của mọi instance

Một số API reflection thường bị hiểu quá mức:

| API | Đọc thông tin gì? | Có tự khôi phục `Box<String>` từ một instance `new Box<>("A")` không? |
| --- | --- | --- |
| `obj.getClass()` | Runtime class của đối tượng | Không |
| `Box.class.getTypeParameters()` | Các **type variables** khai báo trên `Box<T>` | Không; trả `T` |
| `Box.class.getDeclaredField("value").getType()` | Kiểu runtime/erasure của field | Không; trả `Object` |
| `Field.getGenericType()` | Generic type trong **khai báo field** | Không; ở `T value` trả `T` |
| `SomeSubclass.class.getGenericSuperclass()` | Generic superclass **được ghi trong khai báo lớp con** | Đôi khi trả `Box<String>` nếu lớp con khai báo như vậy |
| `getGenericInterfaces()` | Generic interfaces của **khai báo runtime class** | Đọc metadata class/interface, không phải mọi cách sử dụng instance |
| `Field.getGenericType()` trên `Box<String> field` | Metadata kiểu của **field khai báo rõ `Box<String>`** | Biết kiểu field, không kiểm chứng mọi trạng thái runtime |

Cụ thể, khi bạn khai báo:

```java
class Holder {
    Box<String> named;
}
```

Reflection có thể đọc `Holder.class.getDeclaredField("named").getGenericType()` và thấy `Box<java.lang.String>` vì **field `named` được khai báo là `Box<String>` trong source**. Nhưng nếu lấy riêng object đang nằm trong field và chỉ gọi `obj.getClass()`, thông tin tham số hóa của **field tham chiếu ban đầu** không nhất thiết còn xác định được.

Điểm này rất quan trọng trong serializer, dependency injection, mapping: metadata của **vị trí khai báo** (field, method parameter, method return type, supertype) khác metadata của **runtime instance**.

## 8. Khi metadata của lớp con có thể chứa `String`

Trong chương trình thực hành:

```java
static final class StringBox extends Box<String> {
    StringBox(String value) { super(value); }
}
```

Khi biên dịch `StringBox`, quan hệ `extends Box<String>` là **khai báo tường minh**. Class file có thể giữ generic `Signature` của quan hệ kế thừa. Vì thế:

```java
Box<String> box = new StringBox("A");
Type genericParent = box.getClass().getGenericSuperclass();
System.out.println(genericParent.getTypeName());
```

có thể in:

```text
RuntimeTypeArgumentDemo$Box<java.lang.String>
```

Tên lớp ngoài tương ứng chương trình ở phần 4. Ở đây có một chuỗi kiểm tra:

1. `box.getClass()` xác định runtime class là `StringBox`.
2. `StringBox.class.getGenericSuperclass()` đọc **khai báo kế thừa** của `StringBox`.
3. Khai báo ấy chứa `Box<String>`, do vậy reflection nhìn thấy `String`.

Đây **không** phải phép khôi phục `T` tùy ý từ một đối tượng trực tiếp của `Box<T>`. Nó là đọc một metadata tường minh đã được lưu trên lớp con. Nếu object được tạo bằng `new Box<>("A")`, runtime class của nó là `Box`, không có khai báo lớp con `extends Box<String>` để đọc.

Với cấu trúc kế thừa phức tạp như `Child<U> extends Box<List<U>>`, metadata có thể chứa **biến kiểu `U`**, không phải một loại `List<String>` cụ thể. Muốn resolve đến một kiểu cụ thể cần biết chuỗi binding của biến kiểu qua các lớp — và đôi khi không thể resolve vì thông tin nguồn không hiện diện trong khai báo runtime.

## 9. Anonymous subclass: trường hợp capture có điều kiện

Một kỹ thuật quen thuộc:

```java
Box<String> anonymous = new Box<String>("B") {};
Type t = anonymous.getClass().getGenericSuperclass();
System.out.println(t.getTypeName());
```

Trong chương trình ở phần 4, kết quả là:

```text
RuntimeTypeArgumentDemo$Box<java.lang.String>
```

Cú pháp dấu ngoặc nhọn cuối `{ }` tạo **anonymous subclass** của `Box<String>`, không đơn thuần tạo instance trực tiếp của `Box`. Compiler có class file riêng cho anonymous subclass và metadata generic của **quan hệ superclass** được ghi vào đó. Vì vậy reflection có thể đọc `String`.

So sánh cẩn thận:

```java
Box<String> ordinary = new Box<>("A");
Box<String> anonymous = new Box<String>("B") {};
```

- `ordinary.getClass()` là `Box.class`.
- `anonymous.getClass()` là một anonymous subclass khác `Box.class`.
- `anonymous.getClass().getGenericSuperclass()` có thể chứa `Box<String>` bởi chính khai báo của subclass.
- Cả hai đều dùng generics; không có cơ chế tự ghi `String` vào mọi instance của `Box`.

### Bẫy quan trọng: anonymous subclass bên trong phương thức generic

Đoạn này có vẻ tạo `Box<String>` khi gọi với chuỗi:

```java
static <T> Box<T> makeAnonymous(T value) {
    return new Box<T>(value) {};
}

Box<String> box = makeAnonymous("C");
```

Tuy nhiên class anonymous được biên dịch trong một ngữ cảnh mà superclass biểu diễn là **`Box<T>`**, không phải `Box<String>` cho từng lời gọi. Tại thời điểm biên dịch, `T` là type variable; JVM không tạo lại class anonymous mới mỗi khi `makeAnonymous` được suy luận `T = String`.

Vì vậy output thực của ví dụ đầy đủ:

```text
factory superclass: RuntimeTypeArgumentDemo$Box<T>
```

Bài học: có `ParameterizedType` trong reflection chưa đủ để kết luận **actual type argument là một `Class` cụ thể**. `getActualTypeArguments()[0]` đôi khi là `TypeVariable` như `T`, hoặc một `ParameterizedType` lồng nhau, không nhất thiết `String.class`.

## 10. `ParameterizedType` không luôn trả về `Class<?>`

Khi gọi:

```java
Type parent = StringBox.class.getGenericSuperclass();
if (parent instanceof ParameterizedType p) {
    Type argument = p.getActualTypeArguments()[0];
    System.out.println(argument.getTypeName()); // java.lang.String
}
```

`getActualTypeArguments()` có kiểu trả về `Type[]`, **không phải** `Class<?>[]`. Một phần tử của mảng có thể là:

- `Class<?>` — ví dụ `java.lang.String`;
- `ParameterizedType` — ví dụ `java.util.List<java.lang.String>`;
- `TypeVariable<?>` — ví dụ `T` trong quan hệ `Box<T>`;
- `WildcardType` — ví dụ đối số `? extends Number`;
- `GenericArrayType` — ví dụ một kiểu mảng có thành phần là type variable trong khai báo thích hợp.

Cho nên cast bừa `(Class<?>) p.getActualTypeArguments()[0]` có thể gây `ClassCastException`, hoặc lấy được một `Class` nhưng vẫn bỏ sót kiểu lồng nhau. Reflection API mô tả **cấu trúc kiểu đã khai báo**, không hứa mọi nhánh cuối đều là `Class`.

Với trường hợp metadata chứa `T` chưa được resolve, muốn xác định giá trị thực phải có binding rõ ràng từ khai báo/đối số truyền vào. Nếu binding không tồn tại ở runtime, thuật toán reflection dù phức tạp cũng không thể “đoán” chính xác cho mọi instance.

## 11. Type reference: giữ thông tin kiểu phức tạp theo chủ đích

Java cho phép khai báo một anonymous subclass **có chứa type argument cụ thể trong metadata**. Mẫu khái niệm:

```java
abstract class TypeRef<T> {
    private final Type type;

    protected TypeRef() {
        Type supertype = getClass().getGenericSuperclass();
        if (!(supertype instanceof ParameterizedType p)) {
            throw new IllegalStateException("No parameterized superclass");
        }
        type = p.getActualTypeArguments()[0];
    }

    Type type() {
        return type;
    }
}
```

Đây là đoạn trích thiết kế (cần import `Type`, `ParameterizedType` và đặt trong tệp phù hợp); chương trình hoàn chỉnh chứa `TypeRef<T>` nằm ở mục 4.

Tại điểm sử dụng **khai báo cụ thể**:

```java
TypeRef<List<String>> ref = new TypeRef<List<String>>() {};
System.out.println(ref.type().getTypeName());
```

Output trong chương trình hoàn chỉnh, với phương thức đặt tên `getType()`, là:

```text
captured nested type: java.util.List<java.lang.String>
```

Đây là lý do một số thư viện serialization cung cấp các lớp kiểu như **type token / type reference** để biểu diễn `List<String>`. Quan trọng: chúng hoạt động bằng cách **lưu metadata kiểu khi code được biên dịch**, không khôi phục mọi đối số generic chỉ từ runtime `ArrayList` ngẫu nhiên.

**Giới hạn cần nhớ:** nếu tạo `new TypeRef<T>() {}` **bên trong phương thức generic có type variable `T` chưa cụ thể**, metadata có thể chỉ giữ chữ `T`; việc gọi phương thức với `String` không tự khiến anonymous subclass trở thành `TypeRef<String>` ở runtime.

Khi dùng các framework thật, hãy tuân thủ cách đăng ký type metadata mà framework hỗ trợ và tránh mặc định rằng object runtime có thể tự mô tả đầy đủ type arguments.

## 12. Giải pháp trực tiếp cho một kiểu runtime: truyền `Class<T>`

Nếu ứng dụng cần kiểm tra dữ liệu đến từ `Object`, một giải pháp đơn giản là yêu cầu caller truyền **`Class<T>` token**.

Tạo tệp **`ExplicitTypeTokenDemo.java`**:

```java
import java.util.List;
import java.util.Objects;

public class ExplicitTypeTokenDemo {
    static final class TypedBox<T> {
        private final Class<T> type;
        private T value;

        TypedBox(Class<T> type, T value) {
            this.type = Objects.requireNonNull(type);
            this.value = type.cast(value);
        }

        Class<T> type() { return type; }
        T value() { return value; }

        void replaceFromExternal(Object input) {
            value = type.cast(input);
        }
    }

    public static void main(String[] args) {
        TypedBox<String> words = new TypedBox<>(String.class, "Java");
        TypedBox<Integer> numbers = new TypedBox<>(Integer.class, 7);

        System.out.println("same box runtime class: "
                + (words.getClass() == numbers.getClass()));
        System.out.println("stored tokens: "
                + words.type().getSimpleName() + ", "
                + numbers.type().getSimpleName());

        try {
            words.replaceFromExternal(42);
        } catch (ClassCastException ex) {
            System.out.println("wrong scalar: "
                    + ex.getClass().getSimpleName());
        }

        List<?> values = List.class.cast(List.of(1, 2));
        System.out.println("list token accepts integers: "
                + (values.get(0) instanceof Integer));
    }
}
```

Chạy:

```bash
javac -Xlint:all ExplicitTypeTokenDemo.java
java ExplicitTypeTokenDemo
```

**Stdout đã kiểm chứng:**

```text
same box runtime class: true
stored tokens: String, Integer
wrong scalar: ClassCastException
list token accepts integers: true
```

Ở đây:

- `TypedBox<String>` và `TypedBox<Integer>` vẫn dùng **cùng runtime class `TypedBox`**.
- Mỗi **instance** lưu một field `Class<T> type` được truyền tường minh lúc khởi tạo. Đó là **trạng thái của instance**, không phải `getClass()` tự khôi phục `T`.
- `Class.cast` kiểm tra object ở runtime đối với class token tương ứng: yêu cầu `String` nhưng nhận `Integer` sẽ ném `ClassCastException`.
- `List.class` chỉ nhận biết đối tượng có là một `List` hay không. Token này không thể kiểm tra tất cả phần tử là `String`, nên `List<Integer>` cũng được chấp nhận ở mức class token `List.class`.

Điểm cuối cực kỳ quan trọng: `Class<T>` hoạt động với **lớp có thể kiểm tra ở runtime** như `String.class` hoặc `Integer.class`, không đại diện đầy đủ cho `List<String>` vì không có class literal `List<String>.class`.

Khi cần kiểm tra dữ liệu của một list raw, bạn có thể **duyệt và validate từng phần tử** trước khi công bố một `List<String>` an toàn. Type reference mô tả metadata phức tạp, nhưng metadata đơn thuần không tự xác thực dữ liệu nếu không có bước kiểm tra tương ứng.

## 13. Field, method và kiểu khai báo: biết “ở đâu” có metadata

Không phải mọi trường hợp erasure đều xóa tất cả metadata generic khỏi class file. Reflection phân biệt hai nhóm:

**Nhóm A — Type erasure/descriptor mà JVM dùng cho lời gọi.** Ví dụ field `T value` không bound có kiểu erased là `Object`. Method `T get()` có descriptor trả `Object`.

**Nhóm B — Generic `Signature` mô tả một khai báo.** Class file do `javac` tạo có thể ghi rằng `Box` khai báo `T`, field được khai báo kiểu `T`, hoặc `StringBox` khai báo `extends Box<String>`.

Bạn có thể kiểm tra trực tiếp bằng:

```bash
javap -p -s 'RuntimeTypeArgumentDemo$Box'
```

Trích kết quả từ bản chạy đã kiểm tra:

```text
class RuntimeTypeArgumentDemo$Box<T> {
  private T value;
    descriptor: Ljava/lang/Object;

  RuntimeTypeArgumentDemo$Box(T);
    descriptor: (Ljava/lang/Object;)V

  T get();
    descriptor: ()Ljava/lang/Object;
}
```

Dòng hiển thị `T` trong signature không mâu thuẫn với descriptor `Object`. `javap` có thể đọc generic metadata để trình bày khai báo; JVM descriptor vẫn dùng kiểu erased.

Để quan sát metadata của lớp con, dùng:

```bash
javap -v -p 'RuntimeTypeArgumentDemo$StringBox'
```

Tìm `Signature` mô tả quan hệ `extends Box<String>`. Bạn đang đọc **khai báo của StringBox**, không phải một thuộc tính riêng biệt được gắn vào mọi `Box<String>` tạo trực tiếp.

Một lưu ý liên quan bridge method: ghi đè generic ở lớp con đôi khi khiến compiler tạo bridge để bảo toàn quan hệ override sau erasure. Việc có bridge cũng **không** đồng nghĩa nó lưu type argument của mọi instance; bridge phục vụ dispatch/compatibility ở runtime.

## 14. Thiết kế API: chọn metadata vừa đủ, đúng nguồn tin

### Trường hợp 1 — Chỉ cần dùng kiểu ở compile time

Viết lớp/phương thức generic bình thường:

```java
static <T> T identity(T value) {
    return value;
}
```

Compiler có thể suy luận `T`; ứng dụng không cần lưu token khi không có nghiệp vụ kiểm tra kiểu runtime.

### Trường hợp 2 — Cần kiểm tra object đầu vào từ dữ liệu động

Nhận `Class<T>`, ví dụ `String.class`, rồi dùng `type.cast(value)`. Phù hợp khi type target là class có thể kiểm tra được.

### Trường hợp 3 — Cần mô tả `List<String>`, `Map<String, Integer>`

Dùng API giữ một đối tượng `Type`/`ParameterizedType` hoặc type-reference ở vị trí khai báo cụ thể. Nếu cần **xác thực dữ liệu**, phải thực hiện các bước kiểm tra phần tử và key/value phù hợp với metadata đó. Chỉ giữ `Type` không tự ngăn lỗi dữ liệu.

### Trường hợp 4 — Biết tham số generic từ khai báo subclass

Có thể đọc `getGenericSuperclass()` hoặc `getGenericInterfaces()`, xử lý `ParameterizedType`, `TypeVariable` và hierarchy. Đây là công việc đọc metadata class graph, không phải phép suy luận đơn giản từ `getClass()`.

### Trường hợp 5 — Đã có một instance `Box<T>` thông thường và không có thông tin phụ

Chấp nhận giới hạn: **không đủ dữ liệu để suy ra `T` tùy ý**. Đừng đoán bằng phần tử đầu tiên, giá trị hiện tại, tên implementation, hoặc unchecked cast. Nếu nghiệp vụ cần type argument ở runtime, sửa hợp đồng API để mang token/metadata từ đầu.

## 15. Những lỗi phổ biến và cách sửa

**Lỗi 1: Coi `getClass()` của `Box<String>` sẽ trả `Box<String>.class`.**

Không tồn tại class literal `Box<String>.class` theo cách đó. `Box.class` mô tả runtime class chung. Chọn một token riêng hoặc khai báo subclass lưu metadata nếu cần thông tin generic.

**Lỗi 2: Dùng `Box.class.getTypeParameters()[0]` và nghĩ đó là `String`.**

API ấy trả `TypeVariable` được khai báo, tên thường là `T`. Nó không phụ thuộc vào object `new Box<>("...")` đang được inspect.

**Lỗi 3: Dùng `box.get().getClass()` để khôi phục `T`.**

Giá trị có thể là subtype, có thể `null`, hoặc container có thể thay đổi nội dung. Lớp thực của phần tử không xác định được hợp đồng đối số generic.

**Lỗi 4: Coi mọi `getGenericSuperclass()` là `ParameterizedType`.**

Nó có thể trả `Class` hoặc các dạng `Type` khác tùy hierarchy. Kiểm tra kiểu bằng `instanceof` trước khi dùng `getActualTypeArguments()`.

**Lỗi 5: Ép `Type` thành `Class<?>` vô điều kiện.**

Đối số thực tế có thể là `ParameterizedType`, `TypeVariable`, wildcard hoặc generic array type. Dùng API phù hợp cho từng nhánh và luôn tính tới trường hợp không resolve được.

**Lỗi 6: Anonymous subclass trong generic factory phải bắt được kiểu `String`.**

Nếu factory khai báo `<T>` và tạo `new Box<T>() {}`, metadata ghi `Box<T>`. Runtime không tự chuyên biệt hóa class anonymous cho mỗi lần gọi.

**Lỗi 7: Đánh đồng type metadata với validation dữ liệu.**

`TypeRef<List<String>>` có thể mô tả kiểu mong muốn, nhưng danh sách nhận từ dữ liệu không kiểm soát vẫn có thể chứa `Integer`. Muốn an toàn cần kiểm tra thực tế, nhất là khi đi qua raw types/unchecked casts.

**Lỗi 8: Phụ thuộc `ArrayList`/`List.of` implementation class để biết kiểu phần tử.**

Implementation class chỉ cho biết lớp thực hiện collection. Các type arguments không được suy ra từ tên class nội bộ.

**Lỗi 9: Nhầm `Class<T>` lưu trong `TypedBox` với `TypedBox.class`.**

Field `type` có thể khác nhau trên từng instance, dù `getClass()` của mọi `TypedBox` vẫn giống nhau. Giải pháp đến từ **token được truyền tường minh**, không từ generic erasure tự đảo ngược.

## 16. Bài tập kiểm tra hiểu biết

### Bài 1 — Cùng class, khác kiểu nguồn

```java
Box<String> a = new Box<>("A");
Box<Integer> b = new Box<>(1);
```

`a.getClass() == b.getClass()` cho kết quả gì?

**Đáp án:** `true`, nếu cả hai tạo trực tiếp cùng runtime class `Box`. Kiểu generic dùng để kiểm tra compile time không sinh hai lớp runtime tách biệt.

### Bài 2 — Kiểu giá trị không xác định kiểu đối số

```java
Box<Number> number = new Box<>(Integer.valueOf(8));
```

Nếu `number.get().getClass()` là `Integer.class`, có suy ra `Box<Integer>` được không?

**Đáp án:** không; khai báo nguồn là `Box<Number>`, còn giá trị hiện tại có runtime class `Integer`.

### Bài 3 — Metadata của lớp con

Một lớp khai báo `class S extends Box<String> { ... }`. `S.class.getGenericSuperclass()` có thể cho biết gì?

**Đáp án:** khai báo superclass là `Box<String>` nhờ generic `Signature` của **class S**. Điều này không có nghĩa mọi instance được tạo trực tiếp bằng `new Box<>(...)` đều mang metadata tương tự.

### Bài 4 — Bẫy factory

Giả sử `static <T> Box<T> make(T value) { return new Box<T>(value) {}; }`. Gọi `make("text")` thì superclass signature của anonymous subclass chứa `String` hay `T`?

**Đáp án:** chứa `T` theo khai báo generic tại nơi compiler tạo anonymous class; không tự tạo signature mới theo mỗi lời gọi với type inference.

### Bài 5 — Đối số generic lồng nhau

`new TypeRef<List<String>>() {}` giữ loại metadata nào? Vì sao `List.class` chưa đủ?

**Đáp án:** giữ một `Type` biểu diễn `List<String>` trong khai báo anonymous subclass. `List.class` chỉ chỉ ra lớp/interface raw `List`; nó không mang `String` là đối số generic.

### Bài 6 — Thiết kế kiểm tra tại runtime

Một API nhận `Object` từ nguồn ngoài và mong `String`. Bạn chọn cách nào?

**Gợi ý đáp án:** nhận một `Class<String>` như `String.class`, dùng `type.cast(obj)` và xử lý `ClassCastException` phù hợp ở boundary. Không dựa vào `getClass()` của container để suy ra kiểu mong muốn.

### Bài 7 — Kiểm tra reflection an toàn

Khi lấy `Type type = someClass.getGenericSuperclass()`, có nên viết luôn `((ParameterizedType) type).getActualTypeArguments()[0]`?

**Đáp án:** không nên cast vô điều kiện. Kiểm tra `type instanceof ParameterizedType` trước; và ngay cả khi đúng, argument có thể là `TypeVariable`, `ParameterizedType`, `WildcardType` hoặc kiểu khác thay vì `Class`.

### Bài 8 — Field metadata và object metadata

Một field được khai báo `Box<String> named` có thể hiển thị `Box<String>` qua `Field.getGenericType()`. Nếu lấy object trong field rồi chỉ gọi `getClass()`, có chắc được `String` không?

**Đáp án:** không. Reflection đọc thông tin từ **chữ ký khai báo field**, trong khi `getClass()` trả runtime class của object. Hai nguồn dữ liệu khác nhau.

## 17. Checklist thực hành

Trước khi dùng `getClass()` để quyết định kiểu dữ liệu, hãy tự hỏi:

1. Mình đang cần runtime **class thực** hay **đối số kiểu generic ở source**?
2. Kiểu này có reifiable ở runtime không, hay đã bị erasure?
3. Thông tin mình đọc từ reflection thuộc **class declaration**, **field/method signature**, hay chính object?
4. Nếu `getGenericSuperclass()` trả `ParameterizedType`, argument là `Class` hay một `TypeVariable` chưa resolve?
5. Có cách hợp lý hơn để yêu cầu caller truyền `Class<T>` hoặc `Type` từ đầu không?
6. Nếu data đến từ nguồn ngoài/raw type, đã validate dữ liệu thật hay mới chỉ thu thập metadata?
7. Nếu object đang chứa `null` hoặc subtype, logic suy luận có còn đúng không?
8. Đã có ví dụ kiểm thử với hai kiểu tham số hóa tạo từ cùng class và một generic factory chưa?

## Ghi nhớ

**`Object.getClass()` nói về lớp runtime của object, không phải một phép giải mã tổng quát các đối số kiểu Java Generics.** Type erasure cho phép `Box<String>` và `Box<Integer>` dùng chung runtime class `Box`; những thông tin như `String` có thể còn trong metadata **khai báo subclass, field hoặc type reference** khi được ghi rõ từ lúc biên dịch, nhưng không phải thuộc tính tự sinh của mọi instance. Nếu bài toán thực sự cần biết kiểu runtime, hãy **truyền token hoặc metadata tường minh**, và kiểm tra dữ liệu tại nơi cần thiết, thay vì đoán kiểu generic từ `getClass()`.


</details>

- [Quay lại đầu trang](#back-to-top)

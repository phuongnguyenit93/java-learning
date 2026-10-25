<a id="back-to-top"></a>

# Phương thức generic

## Menu
- [Khai báo &lt;T&gt; trước kiểu trả về](#generic-methods-001)
- [Phương thức generic trong lớp generic hoặc lớp thông thường](#generic-methods-002)
- [Phân biệt phương thức có tham số kiểu riêng với phương thức dùng T của lớp](#generic-methods-003)
- [Quan hệ kiểu giữa nhiều đối số và giá trị trả về](#generic-methods-004)
- [Phương thức generic static và các hàm tiện ích](#generic-methods-005)
- [Truyền đối số kiểu tường minh khi gọi phương thức](#generic-methods-006)
- [Constructor generic và phân biệt với constructor của lớp generic](#generic-methods-007)
- [Thiết kế phương thức tạo đối tượng không phụ thuộc ép kiểu](#generic-methods-008)

## <a id="generic-methods-001">Khai báo &lt;T&gt; trước kiểu trả về</a>

<details>
<summary>Xem chi tiết</summary>

# Khai báo `<T>` trước kiểu trả về trong phương thức generic Java

Phương thức generic (*generic method*) là phương thức **tự khai báo một hoặc nhiều tham số kiểu**, nhờ vậy cùng một cách cài đặt có thể áp dụng với nhiều kiểu dữ liệu mà vẫn giữ thông tin kiểu ở thời điểm biên dịch. Dấu hiệu cú pháp quan trọng nhất là phần khai báo `<T>` **đặt sau các modifier và trước kiểu trả về**:

```java
public static <T> T identity(T value) {
    return value;
}
```

Trong chữ ký trên, `<T>` là **nơi khai báo** tham số kiểu `T`; chữ `T` tiếp theo là **kiểu trả về**, còn `T value` là **kiểu của tham số đầu vào**. Không có hai kiểu `T` khác nhau: cùng một ký hiệu mô tả mối liên hệ giữa đầu vào và đầu ra của một lời gọi.

## 1. Đọc cú pháp từng thành phần

Khuôn dạng phổ biến của một phương thức generic có thân:

```java
[modifiers] <TypeParameters> ReturnType methodName(Parameters)
        [throws Exceptions] {
    // Thân phương thức
}
```

Các dấu ngoặc vuông ở đây chỉ là ký hiệu **mô tả phần tùy chọn**, không phải cú pháp Java có thể sao chép nguyên xi. Trong mã thực:

```java
public static <T> T identity(T value) {
    return value;
}
```

| Thành phần | Ý nghĩa |
|---|---|
| `public static` | Modifier: phạm vi truy cập và tính chất static |
| `<T>` | Khai báo tham số kiểu thuộc **chính phương thức** |
| `T` sau `<T>` | Kiểu trả về; ở đây phương thức trả về giá trị thuộc kiểu được suy luận cho `T` |
| `identity` | Tên phương thức |
| `T value` | Tham số có kiểu `T` và tên biến `value` |
| `return value;` | Trả lại đúng giá trị đầu vào, nên phù hợp với kiểu trả về `T` |

Khi người gọi truyền một `String`, trình biên dịch có thể suy luận `T = String` cho lời gọi đó; khi truyền `Integer`, `T` có thể là `Integer` cho lời gọi khác. Bạn **không phải viết một phương thức riêng** cho từng kiểu.

Ví dụ cách gọi (các dòng này cần nằm trong một method và nhìn thấy `identity`):

```java
String text = identity("Java");
Integer count = identity(10);
```

Hai phép gán đều được kiểm tra kiểu; không cần cast từ `Object`. Literal `10` có kiểu primitive `int`, nhưng tham số kiểu Java generics là kiểu tham chiếu: autoboxing cho phép suy luận `Integer`.

**Quy tắc nhận diện:** nếu dấu `<...>` trong một khai báo phương thức đứng **trước kiểu trả về**, đó thường là phần khai báo type parameter của chính phương thức. Nếu `<...>` đứng sau tên lớp lúc khai báo `class Box<T>`, nó thuộc lớp. Còn `List<String>` ở kiểu trả về hoặc tham số là một **parameterized type sử dụng type argument**, không phải đang khai báo một tên `T` mới.

## 2. Vì sao `<T>` phải đứng trước kiểu trả về?

Java cần biết `T` là **tên kiểu được khai báo** trước khi bạn sử dụng nó trong kiểu trả về hoặc danh sách tham số. Đặt type parameter list ở vị trí đúng giúp trình biên dịch phân biệt khai báo generic method với phương thức thông thường.

Cú pháp hợp lệ:

```java
static <T> T id(T input) {
    return input;
}

public <T> void print(T input) {
    System.out.println(input);
}

private static <T> java.util.List<T> one(T input) {
    return java.util.List.of(input);
}
```

Đây là **các phương thức minh họa**, cần được đặt trong một class. Ba biến thể minh họa ba tình huống:

1. `static <T> T id(...)` trả về chính kiểu `T`.
2. `public <T> void print(...)` khai báo `T` dù kiểu trả về là `void`; type parameter **không buộc** phải xuất hiện ở kết quả.
3. `private static <T> List<T> one(...)` dùng kiểu trả về có chứa `T`. Phần khai báo `<T>` vẫn đứng trước toàn bộ `List<T>`.

Cách viết **không hợp lệ**:

```java
// static T <T> id(T input) { return input; }
// Sai thứ tự: <T> không thể đặt sau kiểu trả về.

// static T id(T input) { return input; }
// Sai nếu không có khai báo T trong phạm vi phù hợp.

// static T id<T>(T input) { return input; }
// Java không khai báo type parameter của method sau tên method.
```

Đoạn trên chỉ là **ví dụ lỗi được comment**, không phải tệp chương trình chạy độc lập. Ở một ngôn ngữ khác, cú pháp tương tự `methodName<T>` có thể là cú pháp khai báo generic; Java dùng vị trí riêng của nó là `<T>` trước kiểu trả về.

### Vị trí của annotation và modifier

Thông thường modifier xuất hiện trước phần khai báo tham số kiểu:

```java
public final <T> T convert(T value) {
    return value;
}
```

Trong class không phải `final`, phương thức instance `final` này có thể khai báo `<T>` và trả về `T`. Nếu có chú thích như `@Deprecated`, nó cũng thuộc phần khai báo/phương thức và đứng trước phần cú pháp phương thức phù hợp:

```java
@Deprecated
public static <T> T oldIdentity(T value) {
    return value;
}
```

Không cần thêm `extends Object` sau mọi `T`; nếu không ghi bound, `Object` là upper bound ngầm định theo ngữ nghĩa erasure.

## 3. Type parameter khác return type và type argument như thế nào?

Ba vai trò rất dễ lẫn khi bắt đầu học:

- **Type parameter declaration:** `<T>` trong `static <T> T id(T input)`; đây là **khai báo tên kiểu** cho method.
- **Return type:** `T` ngay sau `<T>`; đây là **nơi dùng** tên kiểu đã khai báo để mô tả kiểu của kết quả.
- **Type argument:** `<String>` ở lời gọi có chỉ rõ tham số kiểu như `Demo.<String>id("hi")`; đây là **giá trị kiểu được chọn cho tham số `T`**, không phải khai báo tên mới.

Phân biệt hai chữ ký:

```java
static <T> T echo(T x) {
    return x;
}

static String literal() {
    return "fixed";
}
```

`echo` là generic method vì nó **tự khai báo** `<T>`. `literal` không phải generic method vì không khai báo type parameter, dù kiểu trả về của nó là kiểu tham chiếu `String`.

Chữ ký sau dùng generic class chuẩn nhưng **không tự khai báo type parameter của method**:

```java
static java.util.List<String> words() {
    return java.util.List.of("a", "b");
}
```

Đây là method thông thường, có kiểu trả về là **parameterized type `List<String>`**. Ngược lại, `static <T> List<T> singleton(T value)` là generic method vì method có phần khai báo `<T>`.

Một phương thức generic cũng có thể trả về kiểu cố định:

```java
static <T> int sizeOf(java.util.List<T> items) {
    return items.size();
}
```

`sizeOf` vẫn là generic method dù trả về `int`. Ở các thiết kế khác, nếu `T` không tạo thêm quan hệ kiểu cần thiết, có thể đơn giản hóa thành `int sizeOf(List<?> items)`; đó là quyết định thiết kế API, không phải giới hạn cú pháp.

## 4. Ví dụ Java 17 chạy độc lập: static, instance và nhiều tham số kiểu

Lưu toàn bộ chương trình sau vào tệp **`GenericMethodDeclarationDemo.java`**:

```java
import java.util.List;
import java.util.Map;

public class GenericMethodDeclarationDemo {

    static <T> T identity(T value) {
        return value;
    }

    static <T> void display(T value) {
        System.out.println("display: " + value);
    }

    static <T> T first(List<T> items) {
        return items.get(0);
    }

    static <A, B> Map.Entry<A, B> pair(A left, B right) {
        return Map.entry(left, right);
    }

    static <T extends Number> double twice(T number) {
        return number.doubleValue() * 2;
    }

    static <T> List<T> duplicate(T value) {
        return List.of(value, value);
    }

    static final class Holder<T> {
        private final T value;

        Holder(T value) {
            this.value = value;
        }

        T get() {
            return value;
        }

        <U> U echo(U input) {
            return input;
        }

        static <V> V echoStatic(V input) {
            return input;
        }
    }

    public static void main(String[] args) {
        String word = identity("Java");
        System.out.println("identity: " + word);

        Integer number =
                GenericMethodDeclarationDemo.<Integer>identity(7);
        System.out.println("explicit: " + number);

        display(3.5);
        System.out.println("first: " + first(List.of("red", "blue")));

        Map.Entry<String, Integer> entry = pair("age", 36);
        System.out.println("pair: " + entry.getKey()
                + "=" + entry.getValue());

        System.out.println("twice: " + twice(4));
        System.out.println("duplicate: " + duplicate("Hi"));

        Holder<String> holder = new Holder<>("box");
        System.out.println("holder: " + holder.get());
        System.out.println("instance generic: " + holder.echo(42));
        System.out.println("static generic: " + Holder.echoStatic(true));
    }
}
```

Biên dịch và chạy bằng JDK 17 trở lên trong thư mục chứa tệp:

```bash
javac GenericMethodDeclarationDemo.java
java GenericMethodDeclarationDemo
```

**Stdout chính xác:**

```text
identity: Java
explicit: 7
display: 3.5
first: red
pair: age=36
twice: 8.0
duplicate: [Hi, Hi]
holder: box
instance generic: 42
static generic: true
```

Giải thích theo từng cách khai báo:

1. `static <T> T identity(T value)` khai báo `T` ở **mức method**. `identity("Java")` cho phép suy luận `T = String`; `GenericMethodDeclarationDemo.<Integer>identity(7)` chọn `T = Integer` một cách tường minh. Số `7` được boxing phù hợp.
2. `static <T> void display(T value)` minh họa generic method **không trả về `T`**. Ở lời gọi `display(3.5)`, đối số primitive `double` được biểu diễn qua `Double` cho type parameter.
3. `static <T> T first(List<T> items)` giữ quan hệ **kiểu phần tử ↔ kiểu trả về**. `List.of("red", "blue")` tạo danh sách chứa chuỗi, nên kết quả `first(...)` có kiểu tĩnh `String`. Hàm giả định danh sách không rỗng: nếu rỗng, `get(0)` ném `IndexOutOfBoundsException`.
4. `static <A, B> Map.Entry<A, B> pair(A left, B right)` khai báo **hai type parameter**; `A` và `B` có thể độc lập. `Map.entry` trả entry không hỗ trợ `setValue` và không chấp nhận khóa hay giá trị `null`.
5. `static <T extends Number> double twice(T number)` khai báo type parameter với **upper bound**. Vì vậy `T` được bảo đảm có phương thức `doubleValue()`; hàm trả về `double` cố định, không phải `T`.
6. `static <T> List<T> duplicate(T value)` trả về **kiểu tham số hóa chứa `T`**, không trả về trực tiếp `T`. `List.of(value, value)` không chấp nhận `null` và tạo danh sách không hỗ trợ thay đổi.
7. `Holder<T>` khai báo `T` ở **mức class**. `get()` dùng `T` của class nhưng không khai báo type parameter mới, nên **không phải generic method theo nghĩa tự khai báo `<...>`**.
8. `<U> U echo(U input)` là **instance generic method**: `U` thuộc riêng lời khai báo method. Dù `holder` là `Holder<String>`, `holder.echo(42)` vẫn có thể làm việc với `Integer`.
9. `static <V> V echoStatic(V input)` khai báo type parameter riêng cho method static. Nó không cần một đối tượng `Holder<T>` cụ thể.

Chú ý `<T>`, `<U>`, `<A, B>` và `<V>` là những **phần khai báo trước kiểu trả về**. Đây là điểm thống nhất trong mọi biến thể trên.

## 5. Suy luận kiểu và cách chỉ định type argument khi gọi method

Trong phần lớn lời gọi generic method, không cần gõ type argument tường minh:

```java
String s = identity("hello");
Integer n = identity(5);
```

Trình biên dịch suy luận các type argument bằng thông tin từ **kiểu đối số** và, khi thích hợp, **ngữ cảnh kiểu đích (target typing)**. Việc suy luận là một quá trình kiểm tra ràng buộc kiểu của Java, không chỉ đơn giản thay `T` bằng lớp runtime của đối tượng.

Nếu cần chọn kiểu một cách tường minh, cú pháp thường là:

```java
String text = GenericMethodDeclarationDemo.<String>identity("hello");
Integer number = GenericMethodDeclarationDemo.<Integer>identity(5);
```

Đây là những dòng minh họa có thể đặt trong method của chương trình đầy đủ. Phần `<String>` hoặc `<Integer>` **ở lời gọi** là *type argument tường minh*; khác với `<T>` **ở khai báo**. Đối với static method, dùng tên lớp để chỉ rõ nơi gọi giúp cú pháp dễ đọc.

Một cách suy luận hữu ích với kiểu đích:

```java
static <T> java.util.List<T> empty() {
    return java.util.List.of();
}
```

Khi dùng `List<String> strings = empty();`, trình biên dịch có thể suy luận `T = String` từ kiểu của biến bên trái, dù phương thức không có tham số đầu vào. Với `var strings = empty();`, không có `List<String>` làm kiểu đích, nên kết quả có thể được suy luận rộng hơn, chẳng hạn `List<Object>` trong ví dụ này. Nếu cần `String`, viết kiểu đích tường minh hoặc chỉ định type argument: `var strings = GenericMethodDeclarationDemo.<String>empty();` (sau khi thêm method `empty` vào lớp).

**Hạn chế của `var`:** `var` là suy luận kiểu của **biến cục bộ**, không phải khai báo generic parameter. `var` không tự biến method thành generic và không làm type argument xuất hiện ở runtime.

## 6. Phạm vi của `T`: method và class độc lập

Phân biệt hai nơi **khai báo** là đủ để dùng đúng cú pháp:

```java
class Box<T> {
    T get() { throw new UnsupportedOperationException(); }

    <U> U echo(U input) { return input; }

    static <V> V staticEcho(V input) { return input; }
}
```

- `T` thuộc `Box<T>`: `get()` chỉ **sử dụng** type variable của lớp, không tự khai báo generic method.
- `U` thuộc riêng `echo`: mỗi lời gọi có thể suy luận `U` khác nhau; không phụ thuộc vào type argument `T` của instance.
- `V` thuộc riêng static method. Static context **không dùng trực tiếp `T` của lớp**; cần type parameter thuộc chính method nếu muốn generic.
- Một method khai báo `<T>` trùng tên với type parameter class có thể **che khuất tên**, nhưng cách viết khác như `<U>` dễ đọc hơn.

Đây là **ranh giới phạm vi** cần nhớ khi viết đúng thứ tự khai báo; việc phối hợp kiểu lớp, kiểu method và shadowing được phân tích sâu ở bài riêng trong chương.

## 7. Nhiều tham số kiểu và type bound đều đặt trước kiểu trả về

Một method có thể khai báo nhiều tham số kiểu cách nhau bởi dấu phẩy:

```java
static <K, V> java.util.Map.Entry<K, V> entry(K key, V value) {
    return java.util.Map.entry(key, value);
}
```

Thứ tự `K, V` trong phần khai báo có ý nghĩa xác lập các **tên và vị trí**; method sử dụng đúng các tên này trong kiểu trả về và tham số. Hai tham số độc lập: có thể truyền `String` và `Integer`, hoặc cùng là `String`.

Khi cần giới hạn kiểu của một tham số, dùng **bound** ngay tại khai báo:

```java
static <N extends Number> double doubled(N value) {
    return value.doubleValue() * 2;
}
```

`N extends Number` cho phép gọi `doubleValue()`. Type argument phải đáp ứng upper bound `Number` hoặc subtype; một `String` không phù hợp.

Có thể dùng nhiều bound:

```java
static <T extends Number & Comparable<T>> T max(T a, T b) {
    return a.compareTo(b) >= 0 ? a : b;
}
```

Đây là method minh họa cần đặt trong class; một số wrapper số như `Integer` thỏa các bound. Nếu một class xuất hiện ở nhiều bound của cùng tham số kiểu, **class bound phải đứng đầu**, sau đó mới đến interface bound phân tách bằng `&`. Không đặt `super` như một lower bound của named type parameter: `? super T` là cú pháp wildcard ở nơi dùng kiểu, không phải `<T super Number>`.

Các chữ ký minh họa:

```java
static <T> void consume(T value) { }

static <T, R> R transform(T input, java.util.function.Function<T, R> fn) {
    return fn.apply(input);
}

static <T extends CharSequence> int length(T value) {
    return value.length();
}
```

Chúng cho thấy `<...>` trước kiểu trả về là **phần khai báo**, còn kiểu trả về có thể là `void`, một tham số kiểu khác (`R`) hoặc kiểu cố định (`int`). Bound cho phép bên trong method sử dụng những khả năng được hợp đồng kiểu bảo đảm.

## 8. Phương thức generic và constructor generic: cùng vị trí khai báo, khác kiểu trả về

Một constructor cũng có thể tự khai báo type parameter nhưng **không có return type**:

```java
class Message {
    private final String text;
    <U> Message(U value) { text = String.valueOf(value); }
    String text() { return text; }
}
```

So sánh `<U> Message(U value)` (constructor) với `<T> T identity(T x)` (method). Với **method**, danh sách type parameters nằm **trước kiểu trả về**; với **constructor**, nó nằm **trước tên constructor** vì không có kiểu trả về. Hai trường hợp có thể cùng xuất hiện trong một chương trình, nhưng không nên gọi constructor là “method không có return type”.

## 9. Type erasure và giới hạn ở runtime

Tham số kiểu `<T>` bảo đảm các quan hệ type-safe tại **compile time**; trong Java 17, kiểu generic thông thường được biên dịch theo **type erasure**. Với `T` không có bound, chữ ký JVM thường sử dụng `Object`, còn compiler có thể chèn casts ở caller.

Vì `T` không tự mang một class token ở runtime, không được dùng tùy tiện `T.class`, `new T()` hoặc `new T[10]`; khi cần, caller phải cung cấp `Class<T>`, array factory hay `Supplier<T>` phù hợp. Hai overload chỉ khác một `List<String>` và `List<Integer>` cũng có thể đụng nhau sau erasure.

Đối với bài này, hãy nhớ **erasure không thay đổi vị trí cú pháp `<T>`**. Những giới hạn runtime là bối cảnh quan trọng nhưng không thay thế quy tắc khai báo trước return type.

## 10. Khi nào cần khai báo `<T>`, khi nào dùng kiểu cụ thể hay wildcard?

Không cần biến mọi method thành generic. Hỏi: **`T` kết nối những vị trí kiểu nào?**

| Mục tiêu | Chữ ký tiêu biểu | Lý do |
| --- | --- | --- |
| Giữ kiểu đầu vào ở kết quả | `static <T> T identity(T x)` | Input và output cùng `T` |
| Lấy một phần tử mà giữ kiểu | `static <T> T first(List<T> xs)` | Danh sách và kết quả liên kết |
| Chỉ in một object | `static void print(Object x)` | Không cần quan hệ generic |
| Đếm phần tử list bất kỳ | `static int size(List<?> xs)` | Không cần đặt tên element type |
| Dùng một bound cụ thể để tính | `static double twice(Number n)` | Kiểu `Number` đủ để gọi `doubleValue()` |

Các chữ ký trên là mẫu **thiết kế**, không phải một source file độc lập. `static <T> void print(T x)` vẫn hoàn toàn hợp lệ; chỉ là thường thừa nếu `T` không thêm quan hệ kiểu có ích. Chọn chữ ký đơn giản nhất mà không đánh mất những bảo đảm compiler cần giữ.

## 11. Lỗi thường gặp về vị trí khai báo `<T>`

**Lỗi 1 — Đặt `<T>` sau kiểu trả về.** Viết `static T <T> echo(T x)` là sai thứ tự cú pháp; viết `static <T> T echo(T x)`.

**Lỗi 2 — Quên khai báo `<T>` trong method độc lập.** Viết `static T echo(T x)` khi `T` không được khai báo phù hợp sẽ gây lỗi không tìm thấy kiểu `T`; sửa bằng `static <T> T echo(T x)`.

**Lỗi 3 — Nghĩ `List<String>` trong kết quả đã biến phương thức thành generic.** `static List<String> names()` trả về parameterized type cụ thể, nhưng không tự khai báo tham số kiểu nào. Generic method thực sự có phần khai báo `<T>`, `<K,V>`... riêng.

**Lỗi 4 — Nghĩ kiểu trả về bắt buộc phải là `T`.** `static <T> void print(T x)`, `static <T> List<T> one(T x)` và `static <T extends Number> double twice(T x)` đều là generic method hợp lệ.

**Lỗi 5 — Dùng class `T` trong static method.** `class Box<T> { static T get(...); }` không hợp lệ vì static không gắn với một cách tham số hóa instance. Static generic method phải khai báo type parameter của chính nó.

**Lỗi 6 — Lẫn type parameter của class và của method cùng tên `T`.** `class Box<T> { <T> T echo(T x) { return x; } }` có thể tạo shadowing khiến `T` method khác `T` class. Dùng `<U>` khi muốn rõ sự độc lập.

**Lỗi 7 — Tưởng literal primitive biến `T` thành `int`.** `identity(5)` dùng boxing và `Integer` trong ngữ cảnh generic. Không viết `<int>`; type argument Java generics phải là reference type.

**Lỗi 8 — Dùng `<T>` để overload method vốn trùng erasure.** `void log(Object)` và `<T> void log(T)` không cùng tồn tại hợp lệ trong một lớp nếu tạo name clash như ví dụ ở trên.

**Lỗi 9 — Dùng type argument tường minh ở lời gọi sai vị trí.** Khi cần chỉ định type argument của static method, dùng `ClassName.<String>method(...)`; đừng viết kiểu khai báo `method<String>(...)` theo cú pháp ngôn ngữ khác.

**Lỗi 10 — Tưởng generic method luôn tạo hoặc biết kiểu runtime của `T`.** Erasure ngăn `new T()` hoặc `T.class` trực tiếp. Nếu bài toán yêu cầu tạo đối tượng, có thể truyền `Supplier<T>` hoặc factory; nếu cần kiểm tra runtime type, truyền `Class<T>` và kiểm tra cẩn thận.

## 12. Câu hỏi tự kiểm tra kèm đáp án

1. **Trong `static <T> T identity(T value)`, chỗ nào khai báo `T`?** Phần `<T>` đứng trước kiểu trả về.
2. **Chữ `T` ngay sau `<T>` nghĩa là gì?** Kiểu trả về, sử dụng tham số kiểu vừa khai báo.
3. **`static <T> void print(T x)` có phải generic method không?** Có, vì method tự khai báo `<T>`; không bắt buộc trả `T`.
4. **`static List<String> words()` có phải generic method không?** Không, method không khai báo tham số kiểu mới; nó chỉ trả về một parameterized type.
5. **`class Box<T> { T get() {...} }` thì `get()` có tự khai báo generic parameter không?** Không; `get()` dùng `T` thuộc lớp. Bản thân `Box` là generic class.
6. **`class Box<T> { static <U> U echo(U x) {...} }` hợp lệ không?** Có; `U` do static method tự khai báo.
7. **Từ `identity("A")`, compiler suy luận gì?** Có thể suy luận `T = String` cho lời gọi; kết quả dùng được dưới kiểu `String`.
8. **Viết type argument tường minh của static method ở đâu?** Sau tên lớp/dấu chấm và trước tên method, ví dụ `Demo.<String>identity("A")`.
9. **Đổi `static <T> T first(List<T> xs)` thành `static <T> Object first(List<T> xs)` có gì mất?** Kiểu trả về không còn thể hiện trực tiếp `T`; người gọi chỉ thấy `Object`, mất bảo đảm kiểu đầu ra cụ thể.
10. **`<K, V> Map.Entry<K, V> pair(K k,V v)` có phải hai phương thức generic không?** Không; một phương thức generic khai báo hai type parameter `K` và `V`.
11. **Vì sao không viết `new T()` trong một method chỉ có `<T>`?** Do erasure và không có thông tin constructor của kiểu cụ thể; cần nhận factory/`Supplier<T>` hoặc thông tin runtime bổ sung.
12. **Một constructor generic đặt `<T>` ở đâu?** Trước tên constructor vì constructor không có kiểu trả về; khác với method có dạng `<T> ReturnType name(...)`.

Hãy nhớ mẫu cú pháp trọng tâm: **`[modifier] <T> ReturnType methodName(...)`**. Phần `<T>` **khai báo tên kiểu** cho phương thức; phần `ReturnType` **mô tả thứ method trả về** và có thể là `T`, `List<T>`, `void` hoặc kiểu cố định. Khi phân biệt đúng hai vị trí này, bạn sẽ đọc và viết được generic method với một hay nhiều tham số kiểu mà vẫn giữ an toàn kiểu.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="generic-methods-002">Phương thức generic trong lớp generic hoặc lớp thông thường</a>

<details>
<summary>Xem chi tiết</summary>

# Phương thức generic trong lớp generic hoặc lớp thông thường (Java)

## 1. Khái niệm và mục đích

**Phương thức generic** (*generic method*) là phương thức **tự khai báo một hoặc nhiều tham số kiểu** như `<T>`, `<K,V>` hay `<T extends Number>`. Tham số kiểu ấy chỉ có phạm vi trong khai báo phương thức và được compiler suy luận hoặc xác định theo từng lần gọi. Một phương thức generic có thể nằm trong:

- Lớp thông thường (không khai báo tham số kiểu);
- Lớp generic, đồng thời dùng hoặc không dùng tham số kiểu của lớp;
- Lớp tiện ích với phương thức `static`;
- Interface (bao gồm phương thức abstract, default hoặc static), miễn là chữ ký phù hợp.

Ví dụ đặc trưng:

```java
// Đoạn minh họa; đặt trong một lớp có import java.util.List.
static <T> T first(List<T> items) {
    if (items.isEmpty()) {
        throw new java.util.NoSuchElementException("Empty list");
    }
    return items.get(0);
}
```

Ký hiệu `<T>` **đứng trước kiểu trả về** `T`. Nó **khai báo** tham số kiểu của phương thức. `List<T>` liên kết kiểu đầu vào với kiểu kết quả: đưa vào `List<String>` sẽ nhận `String`; đưa vào `List<Integer>` sẽ nhận `Integer`. Trình biên dịch kiểm tra các quan hệ này trước khi chạy.

```java
// Trong một phương thức/chương trình Java:
String name = first(List.of("Lan", "Mai"));
Integer score = first(List.of(8, 9));
// String wrong = first(List.of(8, 9)); // Lỗi vì không tương thích String.
```

Trong phần trên, `first` không cần biết lớp chứa nó có generic hay không. Điều quan trọng là **chính phương thức khai báo `<T>`**. Ngược lại, một phương thức chỉ tham chiếu `T` được khai báo bởi lớp không tự động trở thành generic method.

Bài học sử dụng cú pháp chạy được trên **Java 17+**, phân biệt các mảnh minh họa với ví dụ đầy đủ `GenericMethodLesson.java` ở mục 11.

## 2. Cú pháp: tham số kiểu, kiểu trả về và danh sách tham số

Phương thức có thể khai báo type parameters dù **class chứa nó có hoặc không có `<T>`**:

```java
static <T> T first(java.util.List<T> xs) {
    return xs.get(0);
}

static <K, V> java.util.Map.Entry<K, V> pair(K k, V v) {
    return java.util.Map.entry(k, v);
}
```

Trong cả hai chữ ký, phần `<...>` đặt trước return type **thuộc riêng method**. Đừng nhầm `List<T>`/`Map.Entry<K,V>` với nơi khai báo type parameter: chúng là vị trí sử dụng type variables. Trọng tâm tiếp theo là nơi method được đặt — lớp thường, lớp generic hoặc interface — và scope của từng type variable.

## 3. Phương thức generic trong lớp thông thường

Lớp không có tham số kiểu vẫn có thể sở hữu nhiều phương thức generic, mỗi phương thức tự quyết định tham số của mình. Cách này phù hợp các thao tác tiện ích, factory hoặc chuyển đổi dữ liệu.

```java
import java.util.*;

final class CollectionTools {
    private CollectionTools() {}

    static <T> List<T> singletonMutable(T element) {
        List<T> result = new ArrayList<>();
        result.add(element);
        return result;
    }

    static <K, V> Map<K, V> oneEntry(K key, V value) {
        Map<K, V> result = new LinkedHashMap<>();
        result.put(key, value);
        return result;
    }

    static <T> T last(List<T> values) {
        if (values.isEmpty()) {
            throw new NoSuchElementException("Empty list");
        }
        return values.get(values.size() - 1);
    }
}
```

Các lời gọi:

```java
List<String> words = CollectionTools.singletonMutable("java");
List<Integer> numbers = CollectionTools.singletonMutable(42);
Map<String, Integer> stock = CollectionTools.oneEntry("book", 5);
String lastWord = CollectionTools.last(List.of("A", "B")); // "B"
```

`singletonMutable` trả danh sách có thể sửa, khác với `List.of(element)` là factory tạo list không sửa được. Việc xây dựng `new ArrayList<>()` vẫn an toàn: kiểu `T` của phương thức cung cấp ràng buộc cho danh sách mới.

`oneEntry` cho thấy nhiều tham số kiểu có ý nghĩa: `K` (key) và `V` (value) được suy luận độc lập. Nếu khai báo `Map<T,T>` thay vì `Map<K,V>`, ta sẽ làm mất khả năng diễn tả rõ quan hệ hai kiểu khác nhau hoặc buộc compiler tìm một kiểu chung rộng hơn.

Một lớp thông thường còn có **phương thức instance generic**:

```java
class Formatter {
    <T> String describe(T item) {
        return "Value: " + item;
    }
}

String s = new Formatter().describe(100); // "Value: 100"
```

`Formatter` không có `<T>` sau tên lớp, nhưng `describe` có `<T>` trước `String` nên vẫn là generic method. Ở đây type parameter không bắt buộc để tạo chuỗi vì có thể nhận `Object`; nếu không cần ràng buộc kiểu nào, chọn `Object` có thể đơn giản hơn. Generic đem lại giá trị cao nhất khi **liên kết nhiều vị trí kiểu** hoặc cho phép bound bảo đảm thao tác cần thiết.

## 4. Ba loại phương thức có thể có trong một lớp generic

Trong một lớp generic, bạn có thể gặp ba trường hợp:

```java
class Container<T> {
    private final T value;
    Container(T value) { this.value = value; }

    T get() { return value; }                  // Dùng T của class.
    <U> U echo(U input) { return input; }       // Tự khai báo U.
    static <V> V staticEcho(V x) { return x; }  // Static generic method.
}
```

| Dạng | Phạm vi type variable | Điểm đáng chú ý |
| --- | --- | --- |
| `T get()` | `T` của class | **Không** tự khai báo generic method |
| `<U> U echo(U)` | `U` của method | Lời gọi có thể suy luận `U` độc lập với `T` |
| `static <V> V staticEcho(V)` | `V` của method | Không thể dùng trực tiếp instance type variable của class |

Vì vậy, `Container<String>` có thể gọi `echo(42)`, nhưng `get()` vẫn trả kiểu tĩnh `String`. Đây là so sánh cần thiết để tiếp tục đọc các ví dụ trong **lớp thường** và **lớp generic** mà không lẫn phạm vi.

## 5. Không nhầm `T` của lớp với `T` của phương thức

Một method có thể khai báo type parameter **trùng tên** với type parameter của class. Trong method đó, tên mới che khuất tên ở phạm vi class:

```java
class ConfusingBox<T> {
    T original;
    ConfusingBox(T value) { original = value; }
    <T> T echo(T input) { return input; }
}
```

`ConfusingBox<String>` vẫn lưu `String` trong `original`, nhưng `echo(123)` có thể suy luận `Integer` cho **method-level `T`**. Đây không phải thay đổi kiểu của class. Trong code production, chọn tên `<U>` cho method để không gây hiểu nhầm. Bài đối chiếu phạm vi ở chương này tiếp tục phân tích rõ cả việc kết hợp `T` của class và `R` của method.

## 6. Suy luận tham số kiểu tại từng lời gọi

Compiler xem **kiểu đối số** và khi thích hợp **target type** để suy luận type parameters của generic method:

```java
static <T> java.util.List<T> singleton(T x) {
    return java.util.List.of(x);
}
java.util.List<String> a = singleton("Java");
java.util.List<Number> b = singleton(42);
```

Hai phép gán minh họa target typing; hãy đặt lời gọi trong một method và khai báo `singleton` ở class phù hợp. Khi suy luận không biểu đạt rõ kiểu mong muốn, caller có thể chọn type witness như `SomeClass.<Number>singleton(42)` (nếu tên class đúng). Việc chỉ định `<Number>` không ép kiểu một object sai thành `Number`.

Điều quan trọng đối với bài này: **sự suy luận của method không phụ thuộc vào việc class chứa nó có generic hay không**. Cú pháp và tình huống dùng explicit type arguments được trình bày riêng trong chương.

## 7. Generic method có bound: giới hạn khả năng của `T`

Phương thức `<T> T identity(T value)` chỉ giả định `T` là reference type. Nếu thuật toán cần gọi API đặc thù, khai báo **upper bound**:

```java
import java.util.*;

final class NumberMath {
    static <T extends Number> double sum(Collection<T> values) {
        double total = 0;
        for (T v : values) {
            total += v.doubleValue();
        }
        return total;
    }
}

// Trong một phương thức:
double a = NumberMath.sum(List.of(1, 2, 3));      // 6.0
double b = NumberMath.sum(List.of(1.5, 2.5));     // 4.0
// NumberMath.sum(List.of("A", "B"));              // Lỗi bound.
```

`T extends Number` bảo đảm có `doubleValue()`. Dù `Number` là lớp, ở danh sách type parameter vẫn dùng từ khóa `extends`. Nếu cần nhiều bound, ví dụ `<T extends Number & Comparable<T>>`, bound lớp phải đứng trước các interface.

### Tìm giá trị lớn nhất theo thứ tự tự nhiên

```java
// Minh họa; trong một lớp Java đã import java.util.*.
static <T extends Comparable<? super T>> T maximum(Collection<? extends T> items) {
    Iterator<? extends T> it = items.iterator();
    if (!it.hasNext()) {
        throw new NoSuchElementException("Empty collection");
    }
    T best = it.next();
    while (it.hasNext()) {
        T next = it.next();
        if (next.compareTo(best) > 0) {
            best = next;
        }
    }
    return best;
}

// Integer highest = maximum(List.of(4, 9, 3)); // 9
```

`Comparable<? super T>` linh hoạt hơn `Comparable<T>` khi một kiểu con kế thừa khả năng so sánh được định nghĩa cho kiểu cha. `Collection<? extends T>` cho phép đọc phần tử là `T` hoặc kiểu con; ở đây chúng chỉ được duyệt, không thêm vào nguồn. Nếu nhu cầu thông thường chỉ là lấy max, hãy dùng API chuẩn `Collections.max`.

Đừng tạo bound chỉ để tránh viết cast khi thiết kế chưa rõ: bound phải biểu diễn **hành vi thuật toán thực sự cần**.

## 8. Generic method và wildcard: thể hiện luồng dữ liệu

Khi phương thức chỉ đọc và đưa dữ liệu sang đích, có thể kết hợp type parameter với wildcard:

```java
// Minh họa trong lớp có import java.util.List.
static <T> void appendAll(
        List<? extends T> source,
        List<? super T> destination) {
    for (T element : source) {
        destination.add(element);
    }
}

List<Integer> numbers = List.of(1, 2);
List<Number> results = new ArrayList<>();
appendAll(numbers, results);  // results = [1, 2].
```

Đây là nguyên tắc **PECS — Producer Extends, Consumer Super**:

- `source` chỉ cung cấp phần tử `T`: `? extends T` cho phép nguồn có phần tử kiểu con của `T`;
- `destination` nhận phần tử `T`: `? super T` cho phép nơi nhận có phần tử `T` hoặc một kiểu cha thích hợp;
- tham số kiểu `<T>` nối quan hệ giữa hai bên, giúp compiler kiểm tra thao tác sao chép.

Không thể thay trực tiếp `List<Integer>` bằng `List<Number>` vì Java generics mặc định **invariant**: mặc dù `Integer extends Number`, `List<Integer>` không kế thừa `List<Number>`.

### Khi nào dùng type parameter, khi nào dùng wildcard?

So sánh hai chữ ký:

```java
static void printEach(List<?> items) {
    for (Object item : items) {
        System.out.println(item);
    }
}

static <T> T getFirst(List<T> items) {
    if (items.isEmpty()) throw new NoSuchElementException();
    return items.get(0);
}
```

`printEach` không cần giữ quan hệ kiểu giữa đầu vào và đầu ra, nên `List<?>` đủ rõ. `getFirst` cần bảo đảm kiểu trả về khớp phần tử đầu vào, nên dùng `<T>`.

Một quy tắc thực hành: **khai báo tham số kiểu khi có quan hệ cần giữ**, ví dụ giữa hai tham số, giữa đầu vào với kết quả, hoặc khi một bound cần giới hạn API. Nếu chỉ đọc một collection không biết kiểu rồi xử lý như `Object`, wildcard thường đơn giản hơn.

## 9. Generic method trong interface, implement và override

Interface **không cần là generic** để chứa phương thức generic:

```java
interface Converter {
    <T> String stringify(T value);

    static <T> T keep(T value) {
        return value;
    }
}

final class DefaultConverter implements Converter {
    @Override
    public <T> String stringify(T value) {
        return String.valueOf(value);
    }
}
```

Chữ ký `<T> String stringify(T value)` thuộc về từng lời gọi; một implementation phải tuân thủ hợp đồng generic khi override. Với ví dụ này, vì kết quả luôn `String` và logic chỉ cần `Object`, interface `String stringify(Object value)` cũng là một thiết kế hợp lý nếu không cần giữ generic contract; phần minh họa ở đây nhấn mạnh rằng **interface bình thường vẫn được khai báo generic method**.

Một interface generic cũng có thể có phương thức dùng type parameter của interface và một generic method riêng:

```java
interface Processor<T> {
    T read();

    <R> R transform(java.util.function.Function<? super T, ? extends R> mapper);
}

final class FixedProcessor<T> implements Processor<T> {
    private final T value;

    FixedProcessor(T value) { this.value = value; }

    @Override
    public T read() { return value; }

    @Override
    public <R> R transform(java.util.function.Function<? super T, ? extends R> mapper) {
        return mapper.apply(value);
    }
}
```

Ở `FixedProcessor<String>`, `read()` trả `String`; `transform(String::length)` suy luận `R = Integer`. Việc override phải bảo toàn quan hệ tham số kiểu trong chữ ký. Thay bằng raw type hoặc cast không kiểm soát có thể gây cảnh báo unchecked và lỗi runtime.

### Có thể overload theo `T` hay không?

Không thể khai báo đồng thời trong cùng một lớp:

```java
// Sai: sau type erasure cả hai có cùng chữ ký runtime.
// static void process(List<String> values) {}
// static void process(List<Integer> values) {}
```

Generics dùng **type erasure** nên hai overload sẽ gây *name clash*. Nếu logic thực sự khác nhau, đặt tên phương thức khác hoặc thêm dấu hiệu phân biệt có ý nghĩa; nếu logic giống nhau, dùng một generic method, ví dụ `static <T> void process(List<T> values)`.

## 10. Type erasure, static và những hạn chế khi triển khai

Với một method khai báo `<T>`, Java 17 kiểm tra generic constraints ở source rồi dùng **type erasure** trong bytecode. `T` không bound thường erased thành `Object`; bounded `T` sử dụng bound thích hợp. Điều này có một số giới hạn có liên quan tới mọi loại class:

- Không được dùng `new T()`, `T.class` hoặc trực tiếp tạo `new T[n]` khi `T` chưa reifiable. Có thể truyền `Supplier<T>`, `Class<T>` hoặc array factory.
- Type arguments không tạo overload khác nhau nếu **cùng erased parameter signature**.
- Generic varargs với non-reifiable types cần chú ý heap pollution và compiler warning.
- Trong generic class, một method `static` không thể trực tiếp sử dụng type parameter **của class**, nhưng nó có thể tự khai báo `<U>`.

Những điều này giúp đọc compiler diagnostics, nhưng **không định nghĩa generic method theo lớp chứa**: một generic method vẫn là method tự khai báo type parameters, dù nằm ở class thường, class generic hay interface.

## 11. Chương trình hoàn chỉnh chạy với Java 17+

Tạo đúng tệp **`GenericMethodLesson.java`** dưới đây. Chương trình so sánh phương thức generic trong lớp thông thường, generic method trong lớp generic, phương thức instance dùng `T` của lớp, phương thức `static` độc lập, bounded generic method và wildcard sao chép.

```java
import java.util.*;
import java.util.function.Function;

public class GenericMethodLesson {
    static final class Utility {
        private Utility() {}

        static <T> List<T> singletonMutable(T value) {
            List<T> result = new ArrayList<>();
            result.add(value);
            return result;
        }

        static <T> T first(List<T> items) {
            if (items.isEmpty()) {
                throw new NoSuchElementException("Empty list");
            }
            return items.get(0);
        }

        static <T extends Comparable<? super T>> T maximum(List<? extends T> items) {
            if (items.isEmpty()) {
                throw new NoSuchElementException("Empty list");
            }
            T best = items.get(0);
            for (T item : items) {
                if (item.compareTo(best) > 0) {
                    best = item;
                }
            }
            return best;
        }

        static <T> void copy(
                List<? extends T> source,
                List<? super T> destination) {
            for (T item : source) {
                destination.add(item);
            }
        }
    }

    static final class Holder<T> {
        private final T value;

        Holder(T value) {
            this.value = value;
        }

        // Dùng tham số kiểu T của lớp; không khai báo <T> mới.
        T get() {
            return value;
        }

        // Generic method của riêng lớp, U độc lập với T.
        <U> Holder<U> map(Function<? super T, ? extends U> mapper) {
            return new Holder<>(mapper.apply(value));
        }

        <U> U apply(Function<? super T, ? extends U> mapper) {
            return mapper.apply(value);
        }

        // Phương thức static generic không phụ thuộc type T của Holder.
        static <V> V echo(V item) {
            return item;
        }
    }

    public static void main(String[] args) {
        List<String> words = Utility.singletonMutable("Java");
        words.add("Generics");
        System.out.println("utility: " + words);
        System.out.println("first: " + Utility.first(words));

        Holder<String> original = new Holder<>("Hello");
        Holder<Integer> length = original.map(String::length);
        System.out.println("holder: " + original.get());
        System.out.println("mapped: " + length.get());
        System.out.println("applied: " + original.apply(String::toUpperCase));
        System.out.println("static: " + Holder.echo(42));

        List<Integer> scores = List.of(3, 9, 5);
        System.out.println("max: " + Utility.maximum(scores));

        List<Number> copied = new ArrayList<>();
        Utility.copy(scores, copied);
        System.out.println("copied: " + copied);
    }
}
```

Lưu mã nguồn bằng UTF-8, biên dịch và chạy:

```bash
javac -Xlint:all GenericMethodLesson.java
java GenericMethodLesson
```

Stdout dự kiến chính xác:

```text
utility: [Java, Generics]
first: Java
holder: Hello
mapped: 5
applied: HELLO
static: 42
max: 9
copied: [3, 9, 5]
```

**Phân tích từng luồng kiểu:**

- `Utility` là lớp thông thường. `singletonMutable`, `first`, `maximum` và `copy` đều là các phương thức static generic **tự khai báo tham số kiểu**.
- `Utility.singletonMutable("Java")` suy luận `T = String`; `words.add("Generics")` hợp lệ, còn `words.add(123)` sẽ bị từ chối ở compile-time.
- `Holder<String>` quyết định `T = String` cho getter `get()`. Phương thức generic `map` có tham số kiểu **`U` mới**, được suy luận là `Integer` từ `String::length`, do đó trả `Holder<Integer>`.
- `original.apply(String::toUpperCase)` suy luận `U = String`, nhưng `original.apply(String::length)` có thể suy luận `U = Integer` ở một lời gọi khác; không có mâu thuẫn với `T = String` của lớp.
- `Holder.echo(42)` tự suy luận `V = Integer` mà không cần đối tượng `Holder<T>`.
- `maximum` có bound `Comparable<? super T>` để gọi `compareTo` và xác định phần tử lớn nhất.
- `copy` nhận nguồn `List<Integer>` rồi ghi vào đích `List<Number>`. Hai wildcard giữ ràng buộc an toàn theo hướng đọc/ghi.

Không có cast thủ công trong chương trình. Mọi ràng buộc được mô tả bằng kiểu của lớp hoặc phương thức; đó là lợi ích lớn nhất của việc thiết kế API generic rõ ràng.

## 12. Trường hợp lỗi và cách sửa

| Triệu chứng | Nguyên nhân | Cách sửa |
| --- | --- | --- |
| `static T first(T value)` trong lớp thông thường báo không tìm thấy `T` | Phương thức chưa khai báo type parameter | Viết `static <T> T first(T value)`. |
| `static T get()` trong `class Box<T>` không biên dịch | Tham số kiểu của lớp không dùng được trong static context | Cho phương thức khai báo type parameter riêng hoặc dùng kiểu cụ thể. |
| `<T> T echo(T value)` bên trong `Box<T>` gây hiểu nhầm | `T` của phương thức che khuất `T` của lớp | Đổi tên tham số kiểu của phương thức thành `<U>`. |
| Caller phải cast sau khi gọi hàm trả `Object` | Chữ ký không giữ quan hệ kiểu đầu vào–đầu ra | Dùng `<T> T first(List<T>)` nếu mục đích yêu cầu trả đúng kiểu phần tử. |
| Lời gọi `List<Number> xs = List<Integer>` thất bại | Generics invariant | Dùng wildcard `? extends T`/`? super T` theo hướng xử lý. |
| Generic method dùng `t.compareTo(x)` nhưng compiler báo không tồn tại | `T` không có bound bảo đảm `Comparable` | Khai báo `<T extends Comparable<? super T>>` phù hợp. |
| Phương thức tạo `new T()` bị lỗi | Type erasure không cho biết constructor của `T` | Nhận `Supplier<? extends T>` hoặc một factory khác. |
| Overload `method(List<String>)` và `method(List<Integer>)` bị name clash | Cùng chữ ký sau erasure | Đổi tên, thiết kế overload khác biệt thực sự hoặc hợp nhất logic. |
| Generic method trả `null`, caller gặp `NullPointerException` | Type parameter không đảm bảo non-null | Định nghĩa hợp đồng null rõ ràng; dùng validation hoặc `Optional<T>` khi có ý nghĩa. |
| Compiler cảnh báo unchecked khi dùng `List` raw | Mất tham số kiểu tại một điểm của luồng dữ liệu | Dùng `List<T>`, `List<?>` hoặc kiểm tra/chuyển đổi dữ liệu legacy. |
| Generic varargs phát cảnh báo | Mảng và generic có quy tắc reification khác nhau | Ưu tiên collection hoặc thiết kế varargs an toàn, đánh giá `@SafeVarargs` đúng điều kiện. |
| Phương thức `<T> void print(T value)` gây khó hiểu nhưng không tạo quan hệ kiểu | Tham số kiểu có thể thừa | Dùng `Object` nếu chỉ xử lý object chung, giữ generic khi thực sự có ràng buộc/quan hệ. |

### Quy trình đọc một chữ ký generic

Gặp phương thức sau:

```java
static <T extends Number, R> List<R> mapNumbers(
        List<? extends T> values,
        Function<? super T, ? extends R> converter);
```

Hãy đọc từ trái sang phải:

1. `<T extends Number, R>` khai báo **hai tham số kiểu thuộc phương thức**; `T` là một kiểu con của `Number`.
2. `List<R>` là kiểu trả về; mỗi phần tử kết quả mang kiểu `R`.
3. `List<? extends T>` là nguồn chỉ cần đọc như `T`.
4. `Function<? super T, ? extends R>` nhận một giá trị `T` (hoặc theo kiểu cha thích hợp) và trả `R` (hoặc kiểu con).
5. Compiler nối các điều kiện ở một lần gọi cụ thể, ví dụ nguồn `List<Integer>` cùng bộ chuyển `Number::doubleValue` có thể tạo kết quả được yêu cầu theo `Double`.

Không cần ghi nhớ mọi kiểu thư viện cùng lúc. Hãy tìm nơi **khai báo** type parameter, nơi nó được **sử dụng**, và mối quan hệ nào compiler đang bảo vệ.

## 13. Bài tập củng cố

**Bài 1 — Lớp thường, phương thức generic.** Trong một lớp `ArrayTools` không generic, viết `static <T> T last(List<T> items)` có kiểm tra danh sách rỗng. Gọi với `List<String>` và `List<Long>`. Giải thích vì sao một lớp thông thường không ngăn cản phương thức khai báo `<T>`.

**Bài 2 — Kiểu lớp và kiểu phương thức.** Viết `Result<T>` có `T value`, `T get()` và `<R> Result<R> map(Function<? super T, ? extends R>)`. Với `Result<String>` chứa `"123"`, biến đổi bằng `Integer::parseInt` rồi kiểm tra kết quả có kiểu `Result<Integer>`. Nêu rõ `T` và `R` thuộc về đâu.

**Bài 3 — Phương thức static generic.** Tạo `static <K,V> Map<K,V> singleMap(K key, V value)`. Gọi bằng khóa `String` và giá trị `Integer`. Giải thích vì sao không cần chuyển `Map<Object,Object>` rồi cast.

**Bài 4 — Bounded type.** Viết `static <T extends Comparable<? super T>> T min(List<? extends T> items)` trả phần tử nhỏ nhất của danh sách không rỗng. Dùng `compareTo`, thử với `List<Integer>` và `List<String>`.

**Bài 5 — PECS.** Viết `static <T> void moveAll(Collection<? extends T> from, Collection<? super T> into)`. Dùng `Collection<Integer>` làm nguồn và `Collection<Number>` làm đích. Hỏi vì sao `Collection<Number>` không nhận trực tiếp một `Collection<Integer>` ở mọi API.

**Bài 6 — Phân tích lỗi.** Dự đoán điều gì xảy ra với đoạn này:

```java
class Box<T> {
    T value;
    Box(T value) { this.value = value; }

    <T> T echo(T item) { return item; }
    // static T missing() { return null; }
}

Box<String> b = new Box<>("Java");
Integer i = b.echo(10);
```

Định hướng: `b.echo(10)` hợp lệ vì `T` của phương thức là type parameter mới; `missing` (nếu bỏ comment) sẽ không biên dịch vì sử dụng `T` của lớp trong `static` context.

Kết thúc phần học, hãy tự giải thích được ba trường hợp: **phương thức dùng `T` đã được lớp khai báo**, **phương thức tự khai báo `<U>` độc lập**, và **phương thức generic `static` trong lớp thường hoặc lớp generic**. Đây là chìa khóa để viết các hàm tái sử dụng mà compiler vẫn kiểm tra được quan hệ kiểu.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="generic-methods-003">Phân biệt phương thức có tham số kiểu riêng với phương thức dùng T của lớp</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt phương thức có tham số kiểu riêng với phương thức dùng `T` của lớp

Java cho phép khai báo **tham số kiểu trên lớp** và **tham số kiểu riêng trên phương thức**. Hai cách có thể xuất hiện trong cùng một class, nhưng có phạm vi và ý nghĩa khác nhau. Trong `class Box<T>`, phương thức `T get()` **dùng `T` của lớp** nên không tự khai báo type parameter. Còn phương thức `<U> U echo(U input)` **là generic method**, tự khai báo `U` và có thể suy luận một kiểu `U` mới cho từng lời gọi.

Mục tiêu của bài học: đọc đúng hai dạng chữ ký, chọn nơi khai báo type parameter, hiểu kiểu được xác định lúc sử dụng, dùng phương thức `static` hợp lệ, kết hợp `T` của lớp với `R` của method, và tránh lỗi che khuất tên type parameter. Các ví dụ theo Java 17 trở lên.

## 1. Khác biệt nằm ở nơi khai báo

```java
class Box<T> {
    private T value;

    Box(T value) {
        this.value = value;
    }

    T get() {
        return value;
    }

    void set(T value) {
        this.value = value;
    }

    <U> U echo(U input) {
        return input;
    }
}
```

`<T>` ngay sau `Box` **khai báo một tham số kiểu thuộc class**. Field `T value`, constructor `Box(T)`, `get()` và `set(T)` đều sử dụng `T` ấy. `get()` và `set()` **không phải generic method theo nghĩa tự khai báo type parameter**: không có `<...>` riêng trước kiểu trả về.

Ngược lại, `<U>` đứng trước kiểu trả về `U` trong `<U> U echo(U input)` là phần khai báo type parameter **của chính method**. `U` độc lập với `T`: khi một `Box<String>` gọi `echo(42)`, trình biên dịch có thể suy luận `U = Integer` mà không đổi `T = String`.

```java
Box<String> text = new Box<>("Java");
String a = text.get();           // T của lớp = String.
text.set("Generics");            // Phải truyền String.
// text.set(42);                 // Lỗi biên dịch: set đòi String.

Integer b = text.echo(42);       // U của lần gọi này = Integer.
Boolean c = text.echo(true);     // U của lần gọi khác = Boolean.
```

Các đoạn code ngắn này là minh họa trong cùng ngữ cảnh khai báo `Box<T>`; ví dụ `Main.java` hoàn chỉnh nằm ở phần 5.

| Tiêu chí | Dùng `T` của lớp | Tự khai báo type parameter của method |
| --- | --- | --- |
| Vị trí khai báo | `class Box<T>` | `<U>` trước kiểu trả về |
| Ví dụ | `T get()`, `void set(T)` | `<U> U echo(U input)` |
| Phạm vi kiểu | Dùng chung trong các instance member của `Box<T>` | Chỉ trong chữ ký và thân method khai báo nó |
| Chọn kiểu như thế nào? | Theo `Box<String>`, `Box<Integer>`… | Thường suy luận tại **mỗi lời gọi** |
| Có thể là static? | Không được tham chiếu `T` class trực tiếp từ static member | Có, nếu static method tự khai báo `<U>` |
| Quan hệ với state của object | Có thể đọc/ghi field `T` | Chỉ liên hệ với state khi chữ ký/thân method biểu diễn rõ quan hệ |

**Tiêu chí nhận biết nhanh:** nhìn phần khai báo ngay trước kiểu trả về. Việc xuất hiện một chữ `T` trong kiểu trả về hoặc tham số **chưa đủ** để kết luận method tự generic.

## 2. Phương thức sử dụng `T` của lớp

```java
final class Holder<T> {
    private T current;

    Holder(T initial) {
        this.current = initial;
    }

    T read() {
        return current;
    }

    void replace(T next) {
        current = next;
    }
}
```

Tại nơi sử dụng:

```java
Holder<String> name = new Holder<>("An");
String old = name.read();
name.replace("Bình");
// name.replace(18); // Sai: kiểu T của tham chiếu là String.

Holder<Integer> age = new Holder<>(18);
Integer value = age.read();
age.replace(21);
```

Cùng một định nghĩa `Holder<T>` tái sử dụng cho cả chuỗi và số, nhưng bên gọi được kiểm tra theo **đối số kiểu của từng tham chiếu**. Tham số kiểu `T` gắn với hợp đồng của `Holder<T>`: `replace` phải nhận đúng kiểu mà `read` trả về.

Tình huống nhiều type parameter cũng tương tự:

```java
class Pair<K, V> {
    private final K key;
    private final V value;

    Pair(K key, V value) {
        this.key = key;
        this.value = value;
    }

    K key() {
        return key;
    }

    V value() {
        return value;
    }
}
```

Cả `key()` và `value()` dùng type parameter được khai báo trên `Pair<K,V>`, **không tự khai báo generic type parameter**. Đối với `Pair<String,Integer>`, kiểu trả về lần lượt là `String` và `Integer`.

Một constructor thông thường của generic class có thể nhận `T` của lớp mà **không phải constructor generic riêng**. Constructor chỉ là generic constructor nếu chính nó khai báo danh sách type parameter riêng trước tên constructor.

## 3. Phương thức tự generic dù lớp không generic

Lớp thông thường không cần khai báo type parameter để chứa generic method:

```java
class ArrayTools {
    static <E> E first(java.util.List<E> xs) {
        return xs.get(0);
    }
}
```

Ở đây `E` chỉ tồn tại trong `first`. Khi truyền `List<String>`, kết quả có kiểu tĩnh `String`; khi truyền `List<Integer>`, kết quả có thể là `Integer`. Bài này dùng ví dụ để **đối chiếu với `T get()` của generic class**, chứ không yêu cầu class `ArrayTools` phải có `<E>` ở tên lớp.

## 4. Kết hợp `T` của class với `<R>` riêng của method

Nhiều API cần giữ kiểu dữ liệu đang lưu trong object nhưng trả một kiểu khác sau khi biến đổi:

```java
import java.util.function.Function;

final class TransformBox<T> {
    private final T value;

    TransformBox(T value) {
        this.value = value;
    }

    <R> R transform(Function<? super T, ? extends R> converter) {
        return converter.apply(value);
    }
}
```

`T` khai báo ở **class**: đầu vào của converter phải xử lý được đối tượng `T` đang lưu. `<R>` khai báo ở **method**: kết quả có thể thay đổi tùy lần gọi.

```java
TransformBox<String> box = new TransformBox<>("Java");
String upper = box.transform(String::toUpperCase);
Integer length = box.transform(String::length);
```

`T` luôn là `String` trong hai lời gọi, còn `R` lần lượt là `String` và `Integer`. `String::length` trả `int`, và boxing giúp kết quả phù hợp với `Integer`.

Trong chữ ký `Function<? super T, ? extends R>`:

- `? super T` cho phép converter chấp nhận `T` hoặc một kiểu cha phù hợp, nghĩa là có thể **tiêu thụ** giá trị đang lưu.
- `? extends R` cho phép converter trả `R` hoặc subtype của `R`, nghĩa là có thể **sản xuất** kết quả tương thích.
- `R transform(...)` liên kết đầu ra với method-level type parameter; không thay đổi type argument `T` của object.

Một ví dụ khác là kết hợp `T` và `U` trong kiểu trả về:

```java
record Tuple<A, B>(A first, B second) {}

class TupleMaker<T> {
    private final T saved;

    TupleMaker(T saved) {
        this.saved = saved;
    }

    <U> Tuple<T, U> pairWith(U extra) {
        return new Tuple<>(saved, extra);
    }
}
```

`TupleMaker<String>.pairWith(7)` suy luận `U = Integer`, trả `Tuple<String,Integer>`. Đây là mối quan hệ kiểu hữu ích: phần thứ nhất có kiểu `T` của object, phần thứ hai có kiểu riêng của lời gọi. Đoạn minh họa không phải chương trình chạy độc lập.

## 5. Ví dụ hoàn chỉnh: một class có cả hai kiểu phương thức

Tạo tệp tên chính xác **`Main.java`** với toàn bộ code bên dưới. Chương trình minh họa phương thức dùng `T` của class, method `<R>` dùng cả `T` và `R`, method `<U>` độc lập với `T`, cùng một static generic method `<E>`.

```java
import java.util.List;
import java.util.function.Function;

public class Main {
    record Pair<L, R>(L left, R right) {}

    static final class Box<T> {
        private T value;

        Box(T value) {
            this.value = value;
        }

        T get() {
            return value;
        }

        void set(T replacement) {
            this.value = replacement;
        }

        <R> R map(Function<? super T, ? extends R> mapper) {
            return mapper.apply(value);
        }

        <U> Pair<T, U> pairWith(U extra) {
            return new Pair<>(value, extra);
        }

        <U> U echo(U arg) {
            return arg;
        }

        static <E> E first(List<E> items) {
            if (items.isEmpty()) {
                throw new IllegalArgumentException("empty");
            }
            return items.get(0);
        }
    }

    public static void main(String[] args) {
        Box<String> text = new Box<>("Java");

        System.out.println("initial: " + text.get());
        System.out.println("upper: " + text.map(String::toUpperCase));
        System.out.println("length: " + text.map(String::length));

        Pair<String, Integer> pair = text.pairWith(7);
        System.out.println("pair: " + pair.left() + "/" + pair.right());

        System.out.println("echo: " + text.<Boolean>echo(true));
        System.out.println("first: " + Box.first(List.of(10, 20)));

        text.set("Generics");
        System.out.println("updated: " + text.get());

        Box<Integer> number = new Box<>(25);
        System.out.println("formatted: " + number.map(n -> "N=" + n));

        // text.set(42); // Không biên dịch: T của text là String.
    }
}
```

Biên dịch và chạy trên **JDK 17 trở lên**:

```bash
javac -encoding UTF-8 Main.java
java Main
```

Stdout:

```text
initial: Java
upper: JAVA
length: 4
pair: Java/7
echo: true
first: 10
updated: Generics
formatted: N=25
```

### Đọc từng chữ ký trong ví dụ

| Phương thức | Nguồn của type parameter | Ý nghĩa đối với `Box<String> text` |
| --- | --- | --- |
| `T get()` | `T` thuộc class | Trả `String` |
| `void set(T)` | `T` thuộc class | Nhận `String`; không nhận `Integer` |
| `<R> R map(Function<? super T, ? extends R>)` | `T` thuộc class, `R` thuộc method | Đầu vào converter tương thích `String`, còn đầu ra theo `R` |
| `<U> Pair<T,U> pairWith(U)` | `T` thuộc class, `U` thuộc method | Trả cặp `String` và kiểu bổ sung |
| `<U> U echo(U)` | Chỉ `U` thuộc method | Cho phép `Boolean`, `Integer`... |
| `static <E> E first(List<E>)` | Chỉ `E` thuộc static method | Không phụ thuộc type argument của bất kỳ `Box` nào |

`text.<Boolean>echo(true)` minh họa **chỉ định type argument cho một instance generic method**, khác với `new Box<String>(...)` là chọn type argument của class. Phần `<Boolean>` sau dấu chấm không thay đổi `text` thành `Box<Boolean>`.

Dòng `text.set("Generics")` cập nhật field qua method dùng `T` của class. Ngược lại, `echo` chỉ trả nguyên đối số mà nó nhận: `text.echo(42)` không khiến `text` đổi kiểu hay trở thành `Box<Integer>`.

## 6. Static method và phạm vi `T`

Một method `static` thuộc class, nên không được truy cập trực tiếp type variable `T` của `class Box<T>` như thể đó là tham số kiểu sẵn có cho static context:

```java
class Box<T> {
    // static T bad(T input) { return input; } // Không hợp lệ.
    static <U> U ok(U input) { return input; } // Hợp lệ.
}
```

`U` được khai báo bởi chính static method; nó không phụ thuộc vào type argument của một instance `Box<String>` hoặc `Box<Integer>`.

## 7. Che khuất tên: `<T>` trên method không phải `T` class

Java **cho phép** một method khai báo tham số kiểu tên `T` ngay trong `class Container<T>`. Khi đó `T` trên method **che khuất** `T` của class trong phạm vi method:

```java
class ShadowDemo<T> {
    private T stored;

    ShadowDemo(T stored) {
        this.stored = stored;
    }

    <T> T echo(T value) {
        return value;
    }
}
```

Giả sử:

```java
ShadowDemo<String> demo = new ShadowDemo<>("Java");
Integer n = demo.echo(42); // Hợp lệ: T của echo = Integer.
```

Ở đây, kiểu của `stored` là `String` còn `T` trong `echo` là `Integer`. Hai tham số kiểu chỉ tình cờ **trùng tên**, không đồng nhất.

Tác hại tiềm ẩn: nếu người đọc tưởng `echo(T)` nhận kiểu của field `stored`, họ có thể viết caller sai hoặc implementation cần cast không an toàn. Nên đặt tên method-level parameter khác, ví dụ `<U> U echo(U value)`. Nếu mong method nhận đúng kiểu của field, **bỏ khai báo `<T>` trên method** và chỉ viết `T echo(T value)`.

Không cố gắng trả `stored` từ một method có `<T> T echo(...)` nếu chưa có ràng buộc thích hợp: `T` của field và `T` của method là hai biến kiểu khác nhau.

## 8. Type inference xảy ra ở hai điểm khác nhau

**Class-level inference:** `Box<String> x = new Box<>("Java")` dùng diamond `<>` để suy luận type argument của **class** từ ngữ cảnh. Khi gọi `x.get()`, `T` của class đã có ý nghĩa `String` trong kiểu của `x`.

**Method-level inference:** `x.echo(10)` suy luận type parameter riêng của `echo` tại **lời gọi ấy**, có thể thành `Integer` dù `x` là `Box<String>`. Nếu caller chọn `x.<Long>echo(10L)`, đó là explicit type argument của method, không đổi type argument của class.

Cơ chế type inference là phần của compiler, **không tạo specialized runtime class** cho mỗi cách dùng.

## 9. Bound của class và bound của method cũng độc lập

Nếu mọi object `NumberBox<T>` phải chứa số:

```java
class NumberBox<T extends Number> {
    private final T value;

    NumberBox(T value) {
        this.value = value;
    }

    double asDouble() {
        return value.doubleValue();
    }
}
```

Bound `T extends Number` khai báo ở **class**. `asDouble()` sử dụng khả năng `doubleValue()` do bound bảo đảm, nhưng nó vẫn **không là một generic method tự khai báo tham số mới**.

Nếu một phương thức độc lập chỉ cần đối số có `length()`:

```java
class TextTools {
    static <S extends CharSequence> int length(S value) {
        return value.length();
    }
}
```

Bound `<S extends CharSequence>` là **của method**, không đòi `TextTools` generic. Hai loại bound còn có thể kết hợp:

```java
class NumberBox<T extends Number> {
    private final T saved;

    NumberBox(T saved) {
        this.saved = saved;
    }

    <U extends CharSequence> String join(U suffix) {
        return saved + ":" + suffix;
    }
}
```

Ở đây `T` được cố định theo mỗi `NumberBox<T>`, còn `U` được chọn ở lời gọi `join`, nhưng phải là kiểu đáp ứng `CharSequence`.

## 10. Lỗi thường gặp và cách sửa

**Lỗi 1 — nhầm phương thức sử dụng `T` thành generic method:** `T get()` trong `Box<T>` dùng type parameter từ class; chỉ khi method tự viết `<U>` hoặc `<R>` trước kiểu trả về thì nó mới khai báo type parameter riêng.

**Lỗi 2 — viết `static T` với `T` chỉ thuộc class:** static method không có instance `T`. Hãy biến nó thành instance method nếu dùng state `T`, hoặc tự khai báo `static <U> U...` nếu thuật toán độc lập.

**Lỗi 3 — vô tình che khuất tên `T`:** viết `<T> T echo(T)` trong `class Box<T>` tạo type parameter **riêng**; ưu tiên `<U>` để dễ phân biệt hoặc bỏ `<T>` nếu muốn dùng type parameter lớp.

**Lỗi 4 — phương thức không có cơ chế bảo đảm kiểu đầu ra:** `<R> R read()` nếu chỉ ép từ `Object` mà không kiểm chứng có thể sinh unchecked warning và `ClassCastException`. Generics không biến dữ liệu thực thành kiểu caller kỳ vọng.

**Lỗi 5 — hai overload trùng erasure:** trong `class Clash<T>` với `T` không bound, hai method `void use(T value)` và `<U> void use(U value)` cùng bị xóa về `use(Object)`; chúng **không thể cùng tồn tại** với chữ ký như vậy. Cần chọn hợp đồng khác hoặc đổi tên method theo ý nghĩa.

**Lỗi 6 — dùng `U` ngoài phạm vi:** một method khai báo `<U>` không khiến field hay method khác được dùng `U`; nếu nhiều thành viên cần giữ cùng quan hệ kiểu, hãy khai báo tham số ở class hoặc interface.

**Lỗi 7 — tưởng type argument method đổi type của object:** `text.echo(42)` không đổi `Box<String>` thành `Box<Integer>`. Field `T` và method parameter `U` vẫn độc lập trừ khi chữ ký ràng buộc rõ.

**Lỗi 8 — nhầm generics với validation:** `Box<String>` vẫn có thể giữ `null` nếu code cho phép; `first(List<E>)` vẫn cần kiểm tra rỗng. Kiểm tra kiểu không thay thế ràng buộc dữ liệu nghiệp vụ.

**Lỗi 9 — cho rằng mỗi type argument tạo class runtime riêng:** Java dùng cơ chế *type erasure* cho phần lớn generics. `Box<String>` và `Box<Integer>` không tạo hai class runtime mới; method `<U>` cũng không được nhân bản một bytecode cho mỗi `U`.

## 11. Tự kiểm tra

**Câu 1:** `class Box<T> { T get() { ... } }` có generic method `get` không?

**Đáp án:** Không. `get()` chỉ sử dụng `T` của class. Nó không tự khai báo danh sách type parameter nào.

**Câu 2:** Trong `Box<String> text`, vì sao `text.set(10)` sai nhưng `Integer x = text.echo(10)` hợp lệ?

**Đáp án:** `set(T)` yêu cầu `String` theo class-level `T`; `echo` khai báo `<U>` riêng và suy luận `U = Integer`. Lời gọi `echo` không thay kiểu của object.

**Câu 3:** `<R> R map(Function<? super T, ? extends R>)` sử dụng bao nhiêu tham số kiểu và ở đâu?

**Đáp án:** Có `T` của class và `R` của method. Tương ứng, input của converter liên hệ kiểu dữ liệu đang lưu, output được xác định theo từng lời gọi.

**Câu 4:** Có thể khai báo `static <U> U identity(U value)` trong class không generic không?

**Đáp án:** Có. Type parameter `U` đã được method tự khai báo; class không cần `<U>`.

**Câu 5:** `class ShadowDemo<T> { <T> T echo(T x) { return x; } }` có dùng `T` class bên trong `echo` không?

**Đáp án:** Không. `T` method che khuất `T` class. Hãy đổi tên type parameter của method nếu muốn giữ rõ quan hệ.

**Câu 6:** Trong `Box<String> text`, lời gọi nào hợp lệ?

```java
// A: text.set(9);
// B: text.<Integer>echo(9);
// C: text.<Integer>echo("9");
```

**Đáp án:** B hợp lệ. A sai vì `set` yêu cầu `String`; C sai vì đã chỉ định `U = Integer` nhưng truyền `String`.

## Kết luận

**Phương thức sử dụng `T` của class** lấy quan hệ kiểu từ khai báo như `class Box<T>`; ví dụ `T get()` và `void set(T)`. **Generic method có tham số kiểu riêng** khai báo trực tiếp `<U>` hoặc `<R>` trước kiểu trả về; kiểu ấy có thể được suy luận cho mỗi lần gọi, kể cả khi class không generic. Khi cần biến đổi dữ liệu đang lưu, một method có thể kết hợp **`T` của class** và **`R` của method** trong cùng chữ ký. Đặt type parameter ở đúng phạm vi giúp hợp đồng kiểu rõ ràng, tránh lỗi static, shadowing và cast không an toàn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="generic-methods-004">Quan hệ kiểu giữa nhiều đối số và giá trị trả về</a>

<details>
<summary>Xem chi tiết</summary>

# Quan hệ kiểu giữa nhiều đối số và giá trị trả về trong phương thức Generic của Java

Generic method không chỉ giúp viết một phương thức dùng được cho nhiều kiểu dữ liệu. Giá trị lớn hơn là **mô tả mối quan hệ kiểu giữa các tham số và kết quả ngay trong chữ ký**, để Java compiler kiểm tra từng lời gọi. Một phương thức `static <T> T first(T a, T b)` dùng chung biến kiểu `T` cho cả hai đối số và kết quả. Một phương thức `static <A, B, R> R combine(A a, B b, BiFunction<A, B, R> f)` cho phép kiểu của hai đầu vào khác nhau nhưng vẫn ràng buộc kiểu kết quả với phép biến đổi.

Bài này hướng dẫn đọc, thiết kế và kiểm chứng những quan hệ đó bằng Java 17+: **biến kiểu chung, nhiều biến kiểu độc lập, suy luận kiểu, kiểu đích, bounded type parameter, wildcard, collection, method reference, giới hạn ở runtime và các lỗi thường gặp**. Trọng tâm là chữ ký của **phương thức generic** và cách lời gọi buộc compiler tìm một phép gán kiểu hợp lệ.

## 1. Phương thức generic thực sự “liên kết” những gì?

Cấu trúc khai báo:

```java
static <T> T method(T first, T second)
```

Đây là chữ ký minh họa, chưa có thân. Hãy tách thành bốn phần:

- `static`: modifier của phương thức.
- `<T>`: khai báo **type parameter** mang tên `T` cho chính phương thức.
- `T` ở vị trí kiểu trả về: kết quả được xem có kiểu `T` tại điểm gọi.
- `T first, T second`: hai tham số cùng tham chiếu tới một biến kiểu `T`.

Điều này tạo các ràng buộc kiểu compile time. Nó **không** hứa hai đối số phải cùng lớp runtime, không hứa kết quả bằng đối số nào, và không làm `T` tồn tại như một class chuyên biệt ở JVM.

So sánh với:

```java
static Object selectObject(Object first, Object second)
```

Chữ ký `Object` chỉ cam kết kiểu trả về là `Object`; người gọi không giữ được mối quan hệ với một biến kiểu được suy luận. Vì vậy:

```java
String a = select("Lan", "Mai");      // Với <T> T select(T,T), hợp lệ.
Object b = selectObject("Lan", "Mai"); // Hợp lệ.
// String c = selectObject("Lan", "Mai"); // Lỗi nếu không cast.
```

Các dòng này minh họa việc sử dụng, giả sử `select`/`selectObject` đã được định nghĩa như chữ ký.

### Phương thức generic khác generic class

`class Box<T>` có `T` được gắn với cách sử dụng `Box<String>` hoặc `Box<Integer>`. Còn `static <T> T identity(T x)` khai báo `T` cho **từng invocation**. Cùng một method có thể được gọi lần lượt với `String` rồi `Integer`. Không cần có `Box<T>` hay một class generic mới viết được generic method.

## 2. Ví dụ chạy được: cùng một T liên kết hai đối số và kết quả

Tạo `ChooseDemo.java`:

```java
public class ChooseDemo {
    static <T> T first(T left, T right) {
        return left;
    }

    static <T> T last(T left, T right) {
        return right;
    }

    public static void main(String[] args) {
        String name = first("Lan", "Mai");
        Integer count = last(10, 20);

        System.out.println(name);
        System.out.println(count);
    }
}
```

Biên dịch, chạy:

```bash
javac ChooseDemo.java
java ChooseDemo
```

`stdout`:

```text
Lan
20
```

Lần theo kiểu của lời gọi đầu:

1. Đọc khai báo `<T>`: type parameter của method.
2. Đối số `"Lan"` và `"Mai"` đều có kiểu `String`.
3. Kiểu đích của biểu thức gán cũng là `String`.
4. `javac` có thể chọn `T = String` thỏa các ràng buộc.
5. Biểu thức `first(...)` được xem có kiểu `String`; không cần ép kiểu tường minh.

Lời gọi `last(10, 20)` dùng hai primitive `int`; boxing và type inference cho phép kết quả được sử dụng như `Integer`. Không có “phiên bản last riêng của Integer” được sinh ra theo kiểu chuyên biệt hóa generic.

**Điểm cần phân biệt:** vì thân `first` thật sự `return left` nên kết quả bằng giá trị `left`. Chữ ký `<T> T first(T,T)` tự nó chỉ bảo đảm **kiểu của kết quả**, không chứng minh kết quả bắt buộc bằng `left`. Một triển khai khác có thể trả `right` hoặc ném exception.

## 3. “Cùng T” không đồng nghĩa đối số phải có exact runtime class giống nhau

Một sai lầm phổ biến là nghĩ `<T> T choose(T a, T b)` chỉ nhận hai đối tượng có cùng class. Trên thực tế, **method type inference giải một hệ ràng buộc kiểu**; Java có thể tìm một kiểu cha chung hoặc một kiểu giao phù hợp cho các biểu thức truyền vào.

Ví dụ:

```java
static <T> T chooseFirst(T a, T b) {
    return a;
}

Number number = chooseFirst(1, 2.5);
```

Đây là đoạn minh họa đặt trong class; `1` được boxing thành kiểu thích hợp, `2.5` thành `Double`. Kiểu `Number` ở vế trái là một kiểu đích hợp lệ; compiler không yêu cầu `Integer.class == Double.class`. Giá trị thực của `number` vẫn là đối tượng mang giá trị `1`.

### Khi các đối số khác nhau và không có kiểu đích cụ thể

Cho:

```java
var value = chooseFirst("hello", 42);
```

Không nên tự suy luận rằng `value` chắc chắn có kiểu `String` hoặc `Integer`. Type inference có thể chọn một common supertype hay intersection type phù hợp, tùy các ràng buộc của invocation và cách biểu diễn kiểu cụ thể. Với `var`, kiểu biến được suy luận từ biểu thức khởi tạo, không phải từ một generic type argument mà bạn chỉ định sẵn.

Nếu nghiệp vụ cần “hai đối số phải có kiểu chính xác bằng nhau ở runtime”, một chữ ký dùng chung `T` **không** phải phép kiểm tra identity class. Bạn cần ràng buộc nghiệp vụ rõ hơn hoặc validation thích hợp; ngay cả một `<T>` chung có thể được suy luận thành supertype của hai đầu vào.

### Giới hạn của suy luận kiểu

Suy luận kiểu hoạt động từ **kiểu tĩnh** của biểu thức, quy tắc subtyping, khả năng áp dụng method và expected target type. Nó không đọc nội dung chuỗi, không biết một `Number` thực sự được gán từ `Integer` trước đó để “tự quyết định” kiểu nghiệp vụ cụ thể, và không duyệt dữ liệu trong một collection để bảo đảm đồng nhất phần tử.

## 4. Nếu muốn hai đối số độc lập, khai báo hai biến kiểu

Chữ ký:

```java
static <A, B> String describe(A first, B second)
```

cho phép hai tham số có kiểu độc lập. `A` và `B` đều được khai báo riêng; kiểu trả về cụ thể là `String` nên không phụ thuộc vào lựa chọn `A`/`B`.

Nếu kết quả cũng cần tham số hóa:

```java
static <A, B> Pair<A, B> pair(A first, B second)
```

thì kiểu trả về **giữ cả hai quan hệ**. Với `pair("Lan", 9)`, compiler suy luận `A=String`, `B=Integer` và kết quả có kiểu `Pair<String,Integer>`.

### Chương trình hoàn chỉnh: hai đầu vào, hai components trong kết quả

Tạo `PairFactoryDemo.java`:

```java
public class PairFactoryDemo {
    record Pair<A, B>(A first, B second) {
    }

    static <A, B> Pair<A, B> pair(A first, B second) {
        return new Pair<>(first, second);
    }

    public static void main(String[] args) {
        Pair<String, Integer> score = pair("Lan", 9);
        Pair<Long, Boolean> status = pair(100L, true);

        System.out.println(score.first());
        System.out.println(score.second() + 1);
        System.out.println(status.first() + ": " + status.second());
    }
}
```

Chạy:

```bash
javac PairFactoryDemo.java
java PairFactoryDemo
```

`stdout`:

```text
Lan
10
100: true
```

Bảng ràng buộc:

| Vị trí | Khai báo | Khi gọi `pair("Lan", 9)` |
| --- | --- | --- |
| `A` | Method `<A,B>` | `String` |
| `B` | Method `<A,B>` | `Integer` |
| `A first` | Tham số thứ nhất | Nhận `String` |
| `B second` | Tham số thứ hai | Nhận `Integer` |
| `Pair<A,B>` | Kiểu trả về | `Pair<String,Integer>` |

`Pair<A,B>` của record và `<A,B>` của static method là **hai nơi khai báo khác nhau**; tên trùng vì cách đặt trong ví dụ. Ở vị trí `Pair<A,B>` bên trong method, `A`/`B` là type arguments truyền cho record, lấy từ type parameters của **method**.

## 5. Liên kết đầu vào và đầu ra bằng ba biến kiểu: A, B, R

Nhiều thuật toán biến đổi **hai đầu vào thành một kết quả**. Nếu kết quả không cùng kiểu với một trong hai đầu vào, nên khai báo type parameter riêng cho kiểu kết quả.

Chữ ký nền tảng:

```java
static <A, B, R> R combine(
        A first,
        B second,
        java.util.function.BiFunction<? super A, ? super B, ? extends R> operation)
```

Ý nghĩa:

- `A`: kiểu đầu vào thứ nhất.
- `B`: kiểu đầu vào thứ hai.
- `R`: kiểu kết quả.
- `operation`: một hàm chấp nhận các giá trị `A`, `B` và trả về giá trị tương thích với `R`.
- `R combine(...)`: kết quả lời gọi giữ đúng kiểu `R` mà compiler suy luận.

Tạo `CombineDemo.java`:

```java
import java.util.function.BiFunction;

public class CombineDemo {
    static <A, B, R> R combine(
            A first,
            B second,
            BiFunction<? super A, ? super B, ? extends R> operation) {
        return operation.apply(first, second);
    }

    public static void main(String[] args) {
        String label = combine("ID-", 17, (prefix, number) -> prefix + number);
        Integer total = combine(4, 7, Integer::sum);

        System.out.println(label);
        System.out.println(total);
    }
}
```

Biên dịch/chạy:

```bash
javac CombineDemo.java
java CombineDemo
```

`stdout`:

```text
ID-17
11
```

Ở lời gọi thứ nhất, một nghiệm tự nhiên là `A=String`, `B=Integer`, `R=String`. Ở lời gọi thứ hai, `A=Integer`, `B=Integer`, `R=Integer`. Cùng một phương thức áp dụng cho hai quan hệ kiểu khác nhau.

### Tại sao `BiFunction<? super A, ? super B, ? extends R>` thay vì `BiFunction<A,B,R>`?

Cả hai có thể mô tả quan hệ cơ bản. Wildcard tăng khả năng nhận hàm có kiểu tham số rộng hơn hoặc kết quả hẹp hơn:

- `? super A`: hàm có thể chấp nhận `A` hoặc một supertype thích hợp của `A`.
- `? super B`: tương tự với đầu vào thứ hai.
- `? extends R`: hàm tạo giá trị kiểu `R` hoặc subtype của `R`.

Việc chọn wildcard là thiết kế API linh hoạt theo hướng **consumer super, producer extends**. Tại lớp học cơ bản, hãy nhận diện trước `A,B,R` liên kết ba vị trí; tiếp theo mới phân tích variance.

### Có phải R hoàn toàn độc lập?

`R` được khai báo độc lập nhưng ràng buộc bởi kiểu trả về của `operation` và kiểu mục tiêu của toàn lời gọi. Nếu hàm trả `String`, bạn không thể tùy ý gán trực tiếp kết quả cho `Integer` mà không có một phép chuyển đổi hợp lệ. Generic không tự biến một `String` thành `Integer` chỉ vì caller yêu cầu `R=Integer`.

## 6. Quan hệ đầu vào–đầu ra trong Generic Method làm việc với List

Generic method rất hữu ích khi container đầu vào và container đầu ra phải giữ quan hệ kiểu.

### 6.1. Trả về phần tử giữ nguyên kiểu nguồn

```java
static <T> T first(java.util.List<T> source) {
    if (source.isEmpty()) {
        throw new java.util.NoSuchElementException("Empty list");
    }
    return source.get(0);
}
```

Đây là method minh họa cần đặt trong class. Với `List<String>`, kiểu trả về ở điểm gọi là `String`; với `List<Integer>` là `Integer`. Nếu thay chữ ký bằng `Object first(List<?> source)`, caller chỉ nhận `Object` và đánh mất mối liên hệ kiểu đầu vào–đầu ra.

### 6.2. Hai danh sách cùng T: thêm giá trị từ nguồn sang đích

Một phiên bản đơn giản:

```java
static <T> void appendSame(
        java.util.List<T> target,
        java.util.List<T> source) {
    target.addAll(source);
}
```

Chữ ký này đòi quan hệ kiểu chặt hơn cần thiết. Nếu nguồn là `List<Integer>` và đích là `List<Number>`, API nghiệp vụ vẫn có thể thêm `Integer` vào `Number`, nhưng hai tham số `List<T>` cùng một `T` không chấp nhận hai generic instantiations đó vì `List<Integer>` và `List<Number>` bất biến.

Phiên bản linh hoạt:

```java
static <T> void append(
        java.util.List<? super T> target,
        java.util.List<? extends T> source) {
    for (T item : source) {
        target.add(item);
    }
}
```

`source` cung cấp các phần tử tương thích với `T`; `target` nhận các phần tử `T`. Cùng named type parameter `T` **liên kết hai wildcard khác nhau**.

Tạo `AppendDemo.java`:

```java
import java.util.ArrayList;
import java.util.List;

public class AppendDemo {
    static <T> void append(
            List<? super T> target,
            List<? extends T> source) {
        for (T item : source) {
            target.add(item);
        }
    }

    public static void main(String[] args) {
        List<Integer> source = List.of(2, 4, 6);
        List<Number> target = new ArrayList<>();
        target.add(1.5);

        append(target, source);
        System.out.println(target);
    }
}
```

Chạy:

```bash
javac AppendDemo.java
java AppendDemo
```

`stdout`:

```text
[1.5, 2, 4, 6]
```

Điều quan trọng là **mối quan hệ cho phép truyền phần tử an toàn**, không phải ép mọi phần tử trở thành cùng runtime class. Ở lời gọi này có thể lấy `T=Integer`; `List<Integer>` làm producer, `List<Number>` làm consumer. `target` là mutable, còn `source` bất biến từ `List.of` nhưng chỉ bị đọc nên phù hợp.

### 6.3. Danh sách đầu vào kiểu T, danh sách kết quả kiểu R

Khi biến đổi từng phần tử, quan hệ đúng là `List<T>` → `List<R>` qua một hàm `T → R`.

Tạo `MapListDemo.java`:

```java
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class MapListDemo {
    static <T, R> List<R> map(
            List<? extends T> source,
            Function<? super T, ? extends R> mapper) {
        List<R> result = new ArrayList<>();
        for (T item : source) {
            result.add(mapper.apply(item));
        }
        return result;
    }

    public static void main(String[] args) {
        List<Integer> numbers = List.of(3, 5, 8);
        List<String> labels = map(numbers, n -> "n=" + n);
        List<Integer> lengths = map(List.of("Java", "API"), String::length);

        System.out.println(labels);
        System.out.println(lengths);
    }
}
```

Chạy:

```bash
javac MapListDemo.java
java MapListDemo
```

`stdout`:

```text
[n=3, n=5, n=8]
[4, 3]
```

Chữ ký thể hiện:

| Ký hiệu | Nơi khai báo | Vị trí được ràng buộc |
| --- | --- | --- |
| `T` | `<T,R>` của method | Phần tử đọc từ `source`; tham số đầu vào của `mapper` |
| `R` | `<T,R>` của method | Kết quả `mapper`; phần tử `List<R>` trả về |
| `? extends T` | Wildcard dùng trong List | Cho phép nguồn có element type tương thích `T` |
| `? super T` | Wildcard dùng trong Function | Cho phép mapper xử lý một `T` |
| `? extends R` | Wildcard dùng trong Function | Cho phép mapper tạo ra subtype của `R` |

Cách tự hỏi: “Nếu caller truyền `List<Integer>` và mapper tạo `String`, liệu kiểu trả về của tôi giữ được `List<String>` không?” Với `List<R>`, có. Nếu khai báo `List<Object>`, thông tin quan hệ kiểu bị mất.

## 7. Liên hệ nhiều đối số với kiểu trả về bằng một record

Có lúc bạn muốn giữ cả hai giá trị và thêm kết quả tính toán. Mỗi phần có thể là một kiểu riêng; record tổng hợp cho phép biểu diễn quan hệ đó rõ ràng.

Tạo `EvaluationDemo.java`:

```java
import java.util.function.BiFunction;

public class EvaluationDemo {
    record Evaluation<A, B, R>(A first, B second, R result) {
    }

    static <A, B, R> Evaluation<A, B, R> evaluate(
            A first,
            B second,
            BiFunction<? super A, ? super B, ? extends R> function) {
        return new Evaluation<>(first, second, function.apply(first, second));
    }

    public static void main(String[] args) {
        Evaluation<String, Integer, String> e =
                evaluate("item-", 5, (s, n) -> s + n);

        System.out.println(e.first());
        System.out.println(e.second());
        System.out.println(e.result());
    }
}
```

Chạy:

```bash
javac EvaluationDemo.java
java EvaluationDemo
```

`stdout`:

```text
item-
5
item-5
```

`Evaluation<A,B,R>` **giữ cả ba quan hệ** giữa đối số đầu vào và kết quả. Cần phân biệt `<A,B,R>` trong khai báo method với `<A,B,R>` trong khai báo record: chúng được khai báo ở các phạm vi riêng; method dùng những biến kiểu của chính mình làm type arguments khi tạo `Evaluation`.

Ưu điểm của chữ ký `Evaluation<A,B,R>` so với `Object[]` hoặc `Map<String,Object>` là caller nhìn thấy kiểu chính xác của từng component bằng `first()`, `second()`, `result()` mà không phải đoán và cast.

## 8. Bounded type parameter: liên hệ kiểu và khả năng thao tác

Một type parameter có thể được ràng buộc bởi class/interface. Khi đó compiler vừa theo dõi quan hệ kiểu giữa nhiều đối số, vừa biết thao tác nào được phép.

Chữ ký:

```java
static <T extends Comparable<? super T>> T max(T left, T right)
```

Nghĩa là:

- `T` là một biến kiểu có tên, được khai báo tại method.
- Bound `Comparable<? super T>` cho phép so sánh một `T` với một `T` khác theo `compareTo`.
- Hai đối số `left`, `right` và kết quả cùng dùng `T`.
- Không phải mọi object đều thỏa bound; compiler từ chối các invocation không tìm được type argument hợp lệ.

Tạo `GenericMaxDemo.java`:

```java
public class GenericMaxDemo {
    static <T extends Comparable<? super T>> T max(T left, T right) {
        return left.compareTo(right) >= 0 ? left : right;
    }

    public static void main(String[] args) {
        Integer largerNumber = max(7, 12);
        String laterWord = max("apple", "banana");

        System.out.println(largerNumber);
        System.out.println(laterWord);
    }
}
```

Chạy:

```bash
javac GenericMaxDemo.java
java GenericMaxDemo
```

`stdout`:

```text
12
banana
```

Với `Integer`, `compareTo` so sánh số; với `String`, so sánh thứ tự từ điển theo `String.compareTo`. Compiler dùng bound để cho phép `left.compareTo(right)`. Hợp đồng kiểu **không bảo đảm cách sắp xếp phù hợp với mọi nghiệp vụ**: cách so sánh thực tế đến từ triển khai `Comparable`.

### Khi kết quả nên là kiểu Number, không phải T

So sánh hai chữ ký:

```java
static <T extends Number> T choose(T a, T b)
static <T extends Number> double average(T a, T b)
```

Đây là hai chữ ký minh họa. Ở `choose`, kiểu kết quả liên kết với `T`. Ở `average`, kết quả luôn là primitive `double`, mặc dù compiler chỉ chấp nhận đầu vào là kiểu con của `Number`. Hãy chọn kiểu trả về theo **hợp đồng thật**; không viết `T` chỉ vì phương thức đã có `<T>`.

Trong trường hợp có nhiều đầu vào kiểu số khác nhau, `<T extends Number>` có thể tìm nghiệm kiểu chung; nếu thuật toán chỉ cần gọi `doubleValue()` và luôn trả `double`, một chữ ký `double average(Number a, Number b)` có thể đơn giản hơn. Không nên làm phương thức generic khi không cần bảo toàn quan hệ kiểu.

## 9. Kiểu đích (target type) tham gia suy luận kiểu trả về

Một generic method có thể không nhận đối số nhưng vẫn suy luận được type parameter nhờ **expected type** của biểu thức.

Ví dụ JDK `Collections.emptyList()` có chữ ký gần với:

```java
public static <T> java.util.List<T> emptyList()
```

Khi viết:

```java
java.util.List<String> names = java.util.Collections.emptyList();
```

compiler dùng target type `List<String>` để suy luận `T=String`. Không cần phương thức nhận một `String` để biết `T` ở invocation này.

Tạo `TargetTypingDemo.java`:

```java
import java.util.Collections;
import java.util.List;

public class TargetTypingDemo {
    static <T> List<T> none() {
        return Collections.emptyList();
    }

    static <T> T require(T value) {
        if (value == null) {
            throw new IllegalArgumentException("null");
        }
        return value;
    }

    public static void main(String[] args) {
        List<String> words = none();
        List<Integer> numbers = TargetTypingDemo.<Integer>none();
        String value = require("Java");

        System.out.println(words.size());
        System.out.println(numbers.size());
        System.out.println(value);
    }
}
```

Chạy:

```bash
javac TargetTypingDemo.java
java TargetTypingDemo
```

`stdout`:

```text
0
0
Java
```

Điểm cần ghi nhớ:

1. `none()` khai báo `<T>` và trả `List<T>`.
2. Trong `List<String> words = none();`, kiểu mục tiêu giúp suy luận `T=String`.
3. Cú pháp `TargetTypingDemo.<Integer>none()` truyền **type witness** tường minh; đây là lựa chọn tại invocation.
4. `require("Java")` suy luận `T=String` từ đối số.
5. Chữ ký generic không tự cấm null; `require` kiểm tra null bằng logic runtime riêng.

### Explicit type witness khi nào cần?

Có thể viết `SomeClass.<String>method(args)` để buộc type parameter của method nhận giá trị `String`. Thông thường compiler suy luận được, nên type witness chỉ hữu ích khi làm rõ hoặc xử lý trường hợp ràng buộc phức tạp. Nếu witness không thỏa bound hoặc đối số, compilation thất bại.

### `var` và ngữ cảnh

`var x = none();` không có target type `List<String>` để buộc `T` như ví dụ trên; compiler có thể suy luận `List<Object>` trong trường hợp kiểu không bị ràng buộc cụ thể. Đừng dựa vào `var` để tự bảo toàn kiểu phần tử mong muốn khi initializer thiếu thông tin; dùng khai báo rõ `List<String>` hoặc type witness nếu cần.

## 10. Phân biệt “T được liên kết” với “method thực sự biến đổi đúng”

Một generic method có thể có chữ ký hợp lệ nhưng logic sai. Ví dụ:

```java
static <T> T choose(T first, T second, boolean pickFirst) {
    return pickFirst ? first : second;
}
```

Method trả đúng kiểu `T` nhưng chữ ký không nói nên chọn giá trị nào. Chỉ phần implementation định nghĩa quy tắc. Vì vậy khi đọc API phải tách:

- **Ràng buộc kiểu:** loại đối số và kiểu kết quả compiler chấp nhận.
- **Ràng buộc dữ liệu:** giá trị nào được chọn, điều kiện null, kích thước collection, thứ tự, ngữ nghĩa equals, ngoại lệ.
- **Đặc tính runtime:** mutable/immutable, side effect, tính thread-safe và type erasure.

Generic giúp trình biên dịch chứng minh một tập quan hệ kiểu; nó không chứng minh thuật toán nghiệp vụ đúng.

## 11. Vai trò của wildcard khi liên kết nhiều loại đối số

Phương thức generic và wildcard là hai công cụ bổ sung cho nhau.

### 11.1. Type parameter nối các vị trí

```java
static <T> void move(
        java.util.List<? super T> destination,
        java.util.List<? extends T> source)
```

`T` là **điểm nối**: phần tử đọc từ source phải có thể truyền an toàn vào destination. Hai wildcard có thể đại diện cho hai type arguments cụ thể khác nhau.

### 11.2. Wildcard không phải một biến kiểu có tên

`List<?>` cho biết phần tử thuộc một kiểu chưa biết; hai lần dùng `?` ở hai tham số không tự đồng nghĩa cùng một kiểu. Nếu phương thức cần liên hệ nhiều vị trí với cùng một type, đặt tên cho type parameter thường rõ hơn.

So sánh:

```java
static <T> T getFirst(java.util.List<T> list)
static Object getFirstUnknown(java.util.List<?> list)
```

Chữ ký thứ nhất giữ quan hệ giữa kiểu phần tử và kiểu trả về. Chữ ký thứ hai chỉ bảo đảm trả `Object`, vì wildcard không xuất hiện như một biến có tên ở return type. Hai chữ ký này là minh họa và cần thêm thân phương thức để biên dịch độc lập.

### 11.3. PECS không phải luật “mọi tham số đều wildcard”

Nếu một method cần vừa lấy ra vừa đưa vào container chính xác kiểu `T`, `List<T>` có thể phù hợp hơn. Nếu method chỉ nhận nguồn để đọc và đích để ghi, `extends`/`super` thường làm API chấp nhận được nhiều type arguments mà vẫn an toàn.

Một method có thể kết hợp type parameter có tên cho **kết quả** và wildcard cho **đầu vào**. Chìa khóa là vai trò producer/consumer ở từng tham số, không phải làm chữ ký dài nhất có thể.

## 12. API JDK: đọc chữ ký quan hệ nhiều đối số và kết quả

### 12.1. `Collections.max` và Comparator

Một overload quan trọng của `Collections.max` có hình dạng:

```java
public static <T> T max(
        java.util.Collection<? extends T> coll,
        java.util.Comparator<? super T> comp)
```

Ý nghĩa: collection cung cấp các phần tử kiểu tương thích `T`; comparator có thể so sánh `T` hoặc một supertype phù hợp; kết quả là một phần tử được nhìn như `T` tại compile time. Nếu collection rỗng, method ném `NoSuchElementException`; nếu comparator không phù hợp logic nghiệp vụ, kiểu generic không sửa được thuật toán so sánh.

Tạo `CollectionsMaxDemo.java`:

```java
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class CollectionsMaxDemo {
    public static void main(String[] args) {
        List<String> words = List.of("tea", "java", "coffee");
        Comparator<String> byLength = Comparator.comparingInt(String::length);

        String longest = Collections.max(words, byLength);
        System.out.println(longest);
    }
}
```

Chạy `javac CollectionsMaxDemo.java` và `java CollectionsMaxDemo`:

```text
coffee
```

`T=String` liên kết collection, comparator và kết quả. Chữ ký không nói comparator phải so theo chữ cái; `Comparator.comparingInt(String::length)` mới định nghĩa thứ tự theo độ dài.

### 12.2. `Collections.copy`

Chữ ký:

```java
public static <T> void copy(
        java.util.List<? super T> dest,
        java.util.List<? extends T> src)
```

`src` sản xuất các giá trị `T`, `dest` tiêu thụ `T`. Chữ ký trả `void`, nghĩa là **không phải generic nào cũng liên kết T với kiểu trả về**. Hành vi của `Collections.copy` là thay các vị trí tương ứng ở danh sách đích, với điều kiện đích đủ lớn; nó không tự thêm vào một danh sách rỗng như `addAll`.

### 12.3. `Optional.map`

Bên trong `Optional<T>` có method dạng:

```java
public <U> java.util.Optional<U> map(
        java.util.function.Function<? super T, ? extends U> mapper)
```

`T` thuộc **generic class** `Optional<T>`, còn `U` thuộc **method** `map`. Một instance `Optional<String>` khi gọi `map(String::length)` có thể trả `Optional<Integer>`. Nếu mapper trả null, `Optional.map` tạo Optional rỗng theo API; generic type không tự cấm null.

### 12.4. `Map.computeIfAbsent`

`Map<K,V>` có method `V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction)`.

- `K` và `V` là type parameters **của Map**, không do method này khai báo.
- Tham số `key` kiểu `K`, hàm mapping tiêu thụ `K`, sinh kết quả tương thích `V`.
- Method trả `V` theo type argument của Map hiện tại.

Ví dụ `Map<String,Integer> counts` có thể dùng `computeIfAbsent("key", String::length)` để tạo Integer nếu chưa có mapping phù hợp. Việc lưu kết quả, xử lý null và lời gọi mapping function phải theo hợp đồng `Map`/implementation; chữ ký kiểu không mô tả đầy đủ side effect.

### 12.5. `Comparator.comparing`

Overload phổ biến:

```java
public static <T, U extends Comparable<? super U>>
java.util.Comparator<T> comparing(
        java.util.function.Function<? super T, ? extends U> keyExtractor)
```

`T` là kiểu đối tượng đem so sánh, `U` là kiểu khóa trích ra. `U` phải có khả năng tự so sánh theo bound. Kết quả là `Comparator<T>`, vì comparator áp dụng cho **đối tượng nguồn**, không phải cho khóa `U`. Tách vai trò từng biến giúp đọc được các API có hai hoặc ba type parameters mà không nhầm kiểu đầu vào với kết quả.

## 13. Các lỗi biên dịch minh họa quan hệ kiểu

Đừng chạy nguyên các đoạn lỗi cùng ví dụ thành công; những dòng sau được comment để cho thấy compiler sẽ phản ứng ra sao.

### Lỗi 1: quên `<T>`

```java
// static T identity(T value) { return value; } // T chưa khai báo.
static <T> T identity(T value) { return value; }
```

Khi type parameter không được khai báo ở class/interface/method hay scope hợp lệ, tên `T` không có nghĩa.

### Lỗi 2: đòi kết quả khác kiểu mà chữ ký chưa cho phép

```java
static <T> T identity(T value) { return value; }

// Integer n = String.valueOf(identity("hello")); // Sai: String không gán Integer.
```

Phải dùng một phép biến đổi thật nếu muốn `String` → `Integer`; đừng ép generic inference làm công việc chuyển dữ liệu.

### Lỗi 3: gán hai List invariant

```java
java.util.List<Integer> ints = java.util.List.of(1, 2);
// java.util.List<Number> nums = ints; // Không hợp lệ.
java.util.List<? extends Number> numsView = ints;
```

`Integer extends Number` không làm `List<Integer>` là subtype của `List<Number>`. Wildcard `? extends Number` cho phép đọc mỗi phần tử như `Number`.

### Lỗi 4: hiểu sai kiểu trả về của function

Nếu `Function<T,R>` trả `R`, không thể mong `List<R>` tự trở thành `List<T>` khi `R` khác `T`. Đọc điểm dùng chung biến kiểu giữa mapper và kết quả: một API `map` đúng phải sử dụng cùng `R` ở `Function` và `List<R>`.

### Lỗi 5: gán `T` của class cho static method

```java
class Holder<T> {
    // static T echo(T value) { return value; } // Không được dùng class T.
    static <U> U echo(U value) { return value; }
}
```

`T` ở class thuộc ngữ cảnh tham số hóa instance; static method phải tự khai báo `<U>` nếu cần type parameter.

### Lỗi 6: raw type và unchecked conversion

```java
java.util.List<String> names = new java.util.ArrayList<>();
java.util.List raw = names;
raw.add(123);
```

Đoạn này có thể biên dịch với cảnh báo, nhưng đưa `Integer` vào cấu trúc được caller tin là chứa `String`. Khi lấy ra thành `String` có thể ném `ClassCastException`. Tránh raw type để không phá quan hệ kiểu mà generic method dựa vào.

## 14. Type erasure và giới hạn của quan hệ kiểu

Generics Java chủ yếu dùng type erasure: type variable không có bound thường xóa thành `Object`, biến có upper bound xóa về cận đầu; compiler chèn cast khi cần và có thể tạo bridge method.

Ví dụ:

```java
static <T> T first(T a, T b) { return a; }
```

Về mặt diễn giải, runtime signature sau erasure dùng kiểu `Object` ở những vị trí liên quan, dù caller được compiler kiểm tra với type argument cụ thể. Vì thế một generic method có thể dùng chung bytecode cho các invocation `String` và `Integer` mà không tạo ra hai bản sao runtime.

Hệ quả:

- `<T>` không tạo `T.class` hoặc cho phép `new T()`.
- Không thể dựa vào `instanceof List<String>` để xác minh đối số kiểu của một List ở runtime; dùng `List<?>` để kiểm tra lớp/structure rồi xác thực dữ liệu nếu cần.
- Compiler có thể phát hiện lỗi kiểu ngay khi gọi method, nhưng runtime `ClassCastException` vẫn có thể xuất hiện khi hợp đồng bị phá bởi raw type, unchecked cast, reflection hoặc dữ liệu ngoại lai.
- Metadata generic của các **khai báo** có thể được phản chiếu qua Reflection; điều đó khác với việc mỗi đối tượng List mang type argument runtime riêng.

Một API nhận dữ liệu chưa được kiểm chứng từ bên ngoài không nên chỉ viết cast `(List<String>) value` và coi là an toàn. Cần kiểm tra các phần tử thực tế, xây dựng collection có kiểu rõ ràng, hoặc dùng cơ chế runtime checked phù hợp.

## 15. Khi nào nên dùng T chung, khi nào dùng A/B/R độc lập?

Đây là lựa chọn thiết kế quan trọng nhất của bài.

| Mục tiêu | Chữ ký phù hợp | Lý do |
| --- | --- | --- |
| Nhận một giá trị và trả cùng kiểu | `<T> T identity(T value)` | Nối đầu vào và kết quả bằng `T` |
| Chọn một trong hai giá trị tương thích | `<T> T choose(T a, T b)` | Hai tham số và kết quả cùng một nghiệm kiểu `T` |
| Đóng gói hai giá trị khác kiểu | `<A,B> Pair<A,B> pair(A a, B b)` | Giữ riêng hai kiểu trong kết quả |
| Tạo kết quả từ hai kiểu đầu vào | `<A,B,R> R combine(A a,B b,BiFunction<? super A,? super B,? extends R> fn)` | Kết quả `R` liên kết với mapper |
| Chuyển List<T> sang List<R> | `<T,R> List<R> map(List<? extends T> src,Function<? super T,? extends R> fn)` | T giữ input, R giữ output |
| Chuyển phần tử từ nguồn vào đích | `<T> void append(List<? super T> dst,List<? extends T> src)` | T kết nối producer/consumer |
| Tính trung bình của số | `double average(Number a,Number b)` | Không cần generic nếu kiểu trả về không cần liên kết với đầu vào |
| So sánh hai đối tượng có khả năng so sánh | `<T extends Comparable<? super T>> T max(T a,T b)` | Bound cấp khả năng `compareTo` và giữ kiểu kết quả |

**Quy tắc đọc:** nếu cùng tên `T` xuất hiện ở nhiều vị trí, hãy chỉ ra **quan hệ** mà nó diễn đạt. Nếu khai báo ba type parameters `A,B,R`, hãy chỉ ra **vai trò độc lập** và những đối số/hàm nào nối chúng. Nếu không có mối quan hệ cần bảo vệ, một kiểu cụ thể có thể rõ hơn generic.

## 16. Phương pháp phân tích chữ ký dài trong sáu bước

Lấy chữ ký:

```java
static <A, B, R> java.util.List<R> zip(
        java.util.List<? extends A> left,
        java.util.List<? extends B> right,
        java.util.function.BiFunction<? super A, ? super B, ? extends R> merger)
```

Đây là chữ ký minh họa cho thuật toán ghép hai danh sách theo vị trí; cần có thân method để chạy.

**Bước 1 — tìm nơi khai báo:** `<A,B,R>` thuộc method `zip`. Chúng độc lập ở đầu khai báo.

**Bước 2 — tìm từng vị trí xuất hiện:** `A` trong `left` và input thứ nhất của `merger`; `B` trong `right` và input thứ hai; `R` trong output của `merger` và kiểu phần tử danh sách trả về.

**Bước 3 — xác định vai trò wildcard:** `left` và `right` là producers; `merger` tiêu thụ một `A` và một `B`, sau đó tạo một `R`. Wildcard `extends` trên List và `super` trên đầu vào của function diễn đạt đúng chiều dữ liệu.

**Bước 4 — áp dụng một lời gọi:** Nếu `left` là `List<String>`, `right` là `List<Integer>` và merger tạo `String`, một nghiệm hợp lệ là `A=String`, `B=Integer`, `R=String`.

**Bước 5 — nhìn kiểu đích:** Caller có thể nhận `List<String>` mà không cần cast. Kiểu trả về không phải `List<Object>`, vì `R` giữ quan hệ với mapper.

**Bước 6 — không tự suy diễn hành vi ngoài chữ ký:** Chữ ký không nói nếu hai danh sách có độ dài khác nhau thì zip dừng ở bên ngắn, ném exception hay thêm phần tử mặc định. Điều đó phải được định nghĩa bởi triển khai và tài liệu API. Type system không tự quy định chính sách độ dài.

### Chương trình zip hoàn chỉnh

Tạo `ZipDemo.java`:

```java
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

public class ZipDemo {
    static <A, B, R> List<R> zip(
            List<? extends A> left,
            List<? extends B> right,
            BiFunction<? super A, ? super B, ? extends R> merger) {
        int size = Math.min(left.size(), right.size());
        List<R> result = new ArrayList<>(size);

        for (int i = 0; i < size; i++) {
            result.add(merger.apply(left.get(i), right.get(i)));
        }
        return result;
    }

    public static void main(String[] args) {
        List<String> names = List.of("Lan", "Mai", "An");
        List<Integer> scores = List.of(8, 9);
        List<String> summaries = zip(
                names, scores,
                (name, score) -> name + "=" + score);

        System.out.println(summaries);
    }
}
```

Chạy:

```bash
javac ZipDemo.java
java ZipDemo
```

`stdout`:

```text
[Lan=8, Mai=9]
```

Ở đây **thân method** chủ động dùng `Math.min` và chỉ ghép phần tử ở các vị trí tồn tại trong cả hai danh sách. Mẫu này minh họa rất rõ: chữ ký đảm bảo quan hệ kiểu giữa nguồn và kết quả; implementation quyết định cách xử lý chênh lệch kích thước.

## 17. Đánh giá chất lượng chữ ký khi thiết kế API

Khi viết generic method có nhiều đối số, hãy tự hỏi:

1. Có bao nhiêu **loại dữ liệu độc lập** cần biểu diễn? Nếu đầu vào thực sự khác loại, đừng ép chúng cùng `T` một cách không cần thiết.
2. Kết quả có phải cùng kiểu với một đầu vào không? Nếu có, hãy nối bằng cùng `T`. Nếu kết quả là kiểu mới, cân nhắc `R`.
3. Một tham số là producer hay consumer? Chọn `? extends T`/`? super T` để linh hoạt đúng hướng khi cần.
4. Thuật toán yêu cầu khả năng gì trên `T`? Nếu cần `compareTo` hoặc `doubleValue`, dùng bounded type parameter phù hợp.
5. Kiểu trả về có đủ cụ thể để caller không cần unchecked cast không? Một return `Object` không cần thiết thường là tín hiệu mất thông tin.
6. Có phụ thuộc vào subtype/invariance của collection không? `List<Integer>` không là `List<Number>`.
7. API có cần validation runtime độc lập với generic type không (null, size, mutable, dữ liệu ngoại lai)?
8. Dấu `<T>` có đang dùng chỉ để phức tạp hóa một method trả kiểu cụ thể? Nếu không có quan hệ kiểu thực sự, bỏ generic có thể tốt hơn.

Đừng tối ưu để chữ ký “trông thông minh”; ưu tiên loại ràng buộc mà caller có thể hiểu và compiler bảo vệ.

## 18. Bài tập tự kiểm tra kèm lời giải

### Bài 1 — Xác định T

```java
static <T> T second(T a, T b) {
    return b;
}
```

Hỏi: `String word = second("A","B")` có hợp lệ không? `Integer number = second(1,2)` thì sao? Kết quả là gì? Cả hai đều hợp lệ; `T` được suy luận tương ứng `String`, `Integer`; kết quả lần lượt `"B"` và `2`. `return b` là ý nghĩa của thân method, tách biệt với hợp đồng chữ ký.

### Bài 2 — Hai biến kiểu hay một?

Với mục tiêu giữ một `String` và một `Integer` trong cùng kết quả có accessor đúng kiểu, nên dùng `<T> T choose(T,T)` hay `<A,B> Pair<A,B> pair(A,B)`? Chọn `pair`: hai giá trị độc lập về kiểu và được giữ ở hai components khác nhau, còn `choose` chỉ trả một kết quả kiểu `T`.

### Bài 3 — Tìm một nghiệm inference

```java
static <T> T first(T a, T b) { return a; }
Number result = first(1, 2.5);
```

Đây là các đoạn minh họa. Vì sao không bắt buộc hai đối số cùng class runtime? Compiler tìm một `T` thỏa kiểu tĩnh của cả hai và target type `Number`; các wrapper số có thể cùng tương thích `Number`. Không cần runtime classes bằng nhau.

### Bài 4 — Liên kết collection

Hàm `append` nhận `List<? super T>` và `List<? extends T>`. Có thể gọi với đích `List<Number>` và nguồn `List<Integer>` không? Có. Nếu thay bằng hai `List<T>` thì sao? Vì invariance, không còn diễn đạt sự linh hoạt đó trực tiếp.

### Bài 5 — Kiểu kết quả mới

```java
static <T, R> java.util.List<R> map(
        java.util.List<T> src,
        java.util.function.Function<T, R> f)
```

Hỏi: với `List<String>` và `String::length`, kết quả kiểu gì? Compiler có thể suy luận `T=String`, `R=Integer`, trả `List<Integer>`. Nếu dùng `List<? extends T>` và `Function<? super T,? extends R>`, API chấp nhận được nhiều biến thể subtype hơn mà giữ quan hệ kết quả.

### Bài 6 — T có bound

Vì sao `<T extends Comparable<? super T>> T max(T,T)` có thể gọi `compareTo`, trong khi `<T> T max(T,T)` không được compiler bảo đảm? Vì bound cung cấp hợp đồng `Comparable`. Nếu bỏ bound, `T` có thể là kiểu không có `compareTo`.

### Bài 7 — Đọc chữ ký dài

Với `<A,B,R> List<R> zip(List<? extends A>,List<? extends B>,BiFunction<? super A,? super B,? extends R>)`, hãy ghi riêng: nguồn của `A`, nguồn của `B`, nơi `R` xuất hiện, chiều dữ liệu của wildcard và điều API **chưa** bảo đảm (ví dụ xử lý hai danh sách dài ngắn khác nhau). Đối chiếu với `ZipDemo` để kiểm tra.

## 19. Kết luận

Một generic method có thể biểu diễn những mối quan hệ kiểu mà các kiểu cụ thể hoặc `Object` không diễn đạt được: **một `T` nối nhiều tham số với kết quả; `A` và `B` giữ các kiểu đầu vào khác nhau; `R` nối kết quả của mapper với return type; wildcard mở rộng miền đối số có thể truyền an toàn; bound cho phép thao tác trên biến kiểu**.

Khi đọc chữ ký, luôn tìm **nơi khai báo từng biến kiểu, tất cả vị trí nó xuất hiện và nghiệm type inference ở một lời gọi cụ thể**. Khi thiết kế, giữ đúng mối quan hệ cần compiler bảo vệ, giải thích riêng hành vi runtime và tránh giả định rằng “cùng T” nghĩa là cùng lớp runtime. Làm được vậy, bạn có thể tự tin viết những API từ `identity`, `pair`, `combine` đến `append`, `map` và `zip` mà caller nhận được kiểu chính xác, không phải ép kiểu thủ công.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="generic-methods-005">Phương thức generic static và các hàm tiện ích</a>

<details>
<summary>Xem chi tiết</summary>

# Phương thức generic `static` và các hàm tiện ích trong Java

Trong Java, nhiều thao tác không cần gắn với một đối tượng cụ thể: lấy phần tử đầu tiên của một danh sách, đổi chỗ hai phần tử, tạo một cặp khóa–giá trị, trả lại dữ liệu đầu vào hoặc tìm giá trị lớn nhất. Những thao tác này rất phù hợp với **phương thức generic `static`** (*static generic method*): phương thức có thể áp dụng cho nhiều kiểu dữ liệu, đồng thời compiler vẫn kiểm tra quan hệ kiểu giữa đối số và kết quả.

Bài học đi từ cú pháp `static <T>` đến cách thiết kế một lớp hàm tiện ích, cách Java suy luận đối số kiểu ở mỗi lời gọi, việc khai báo nhiều tham số kiểu, giới hạn kiểu (`bound`), quy tắc với collection, và các sai lầm liên quan đến `static`, raw type, type erasure.

## 1. Một phương thức `static` generic là gì?

Xét phương thức trả lại chính đối tượng được truyền vào:

```java
public static <T> T identity(T value) {
    return value;
}
```

Cách đọc từ trái sang phải:

- `public`: phương thức có thể được gọi từ lớp khác.
- `static`: phương thức thuộc về **lớp khai báo**, nên có thể gọi qua tên lớp mà không cần tạo đối tượng chứa phương thức.
- `<T>`: **khai báo tham số kiểu của riêng phương thức**. Đây là dấu hiệu khiến phương thức trở thành generic.
- `T` đứng trước `identity`: kiểu trả về của phương thức.
- `T value`: kiểu tham số giá trị. Cùng ký hiệu `T` tạo quan hệ “trả ra cùng kiểu đã nhận” theo hợp đồng ở thời điểm biên dịch.

Nếu phương thức được đặt trong lớp `GenericUtils`, có thể gọi:

```java
// Đoạn minh họa nằm trong một phương thức khác:
String language = GenericUtils.identity("Java");
Integer version = GenericUtils.identity(17);
```

Trình biên dịch suy luận `T = String` ở lời gọi đầu và `T = Integer` ở lời gọi sau. Đây là **hai lần sử dụng của cùng một phương thức**; không cần định nghĩa riêng `identity(String)` và `identity(Integer)`.

Phương thức `static` bình thường không có `<T>`:

```java
public static String upper(String value) {
    return value.toUpperCase();
}
```

Phương thức này chỉ nhận `String` nên không phải phương thức generic.

Điểm khác biệt chính nằm ở **khai báo tham số kiểu của phương thức** và cách tham số đó liên kết những vị trí trong chữ ký, chứ không nằm riêng ở từ khóa `static`.

## 2. Vị trí chính xác của `<T>` trong khai báo phương thức

Cú pháp tổng quát:

```java
[modifiers] <TypeParameters> ReturnType methodName(parameters) {
    // body
}
```

Đây là **mẫu cú pháp**, không phải một đoạn Java biên dịch độc lập. Với một phương thức `static` cụ thể:

```java
static <T> T first(T a, T b) {
    return a;
}
```

Những hình thức khác cũng hợp lệ:

```java
static <T> void consume(T item) {
    System.out.println(item);
}

static <K, V> String describe(K key, V value) {
    return key + "=" + value;
}

static <N extends Number> double twice(N value) {
    return value.doubleValue() * 2;
}
```

Cả ba là **phương thức minh họa**, cần đặt trong thân một lớp hoặc record hợp lệ.

| Cú pháp | Ý nghĩa |
|---|---|
| `static <T> T first(T a, T b)` | Một tham số kiểu `T`; kết quả và hai đối số liên hệ qua `T`. |
| `static <T> void consume(T item)` | Phương thức vẫn generic dù trả về `void`. |
| `static <K,V> String describe(K key,V value)` | Hai tham số kiểu độc lập, kết quả là `String`. |
| `static <N extends Number> double twice(N value)` | `N` phải thỏa giới hạn kiểu `Number`. |

Không thể bỏ `<T>` rồi viết `static T identity(T x)` nếu `T` chưa được khai báo hợp lệ trong phạm vi phương thức. Việc có chữ `T` ở tham số hoặc giá trị trả về không tự động khai báo tham số kiểu.

Trong lời khai báo, danh sách `<T>` phải đứng **trước kiểu trả về** và sau các modifier như `public static`. Với phương thức generic, `<T>` thuộc về phương thức ấy, khác với `class Box<T>` vốn khai báo tham số kiểu cho cả lớp.

## 3. Lớp tiện ích: vì sao thường dùng `final` và constructor `private`?

Một lớp tập hợp các hàm `static` dùng chung có thể có dạng:

```java
public final class GenericUtils {
    private GenericUtils() {
        throw new AssertionError("Utility class");
    }

    public static <T> T identity(T value) {
        return value;
    }
}
```

Đây là lớp hoàn chỉnh nhưng mới có một phương thức. Những quyết định thiết kế:

1. `public final class`: lớp là nơi chứa các hàm tiện ích, không dự kiến mở rộng bằng kế thừa.
2. Constructor `private`: mã bên ngoài không thể tạo đối tượng `GenericUtils` chỉ để gọi các hàm `static`; thông báo lỗi trong constructor giúp bảo vệ ý định nếu có lời gọi không mong muốn từ chính bên trong lớp.
3. Phương thức `static`: gọi bằng `GenericUtils.identity(...)`, không cần đối tượng `new GenericUtils()`.
4. Tham số kiểu `<T>`: phạm vi nằm ở từng phương thức. **Không cần** khai báo `class GenericUtils<T>` để viết các utility method generic.

Đây là **quy ước thiết kế**, không phải một quy tắc bắt buộc của ngôn ngữ. Một lớp ứng dụng có trạng thái vẫn có thể chứa phương thức generic `static`. Chỉ dùng kiểu utility class khi các thao tác thực sự không phụ thuộc trạng thái của đối tượng.

## 4. Một bộ hàm tiện ích Java 17 hoàn chỉnh

Hãy tạo tệp **`GenericUtils.java`**:

```java
import java.util.List;
import java.util.Map;

public final class GenericUtils {
    private GenericUtils() {
        throw new AssertionError("Utility class");
    }

    // Giữ quan hệ kiểu giữa đầu vào và kết quả.
    public static <T> T identity(T value) {
        return value;
    }

    // Tạo danh sách một phần tử, không cho phép phần tử null.
    public static <T> List<T> only(T value) {
        return List.of(value);
    }

    // Chấp nhận danh sách chứa T hoặc kiểu con của T, chỉ đọc.
    public static <T> T firstOrThrow(List<? extends T> items) {
        if (items.isEmpty()) {
            throw new IllegalArgumentException("Empty list");
        }
        return items.get(0);
    }

    // Hoán đổi phần tử trong danh sách có thể chỉnh sửa.
    public static <T> void swap(List<T> items, int left, int right) {
        T previous = items.get(left);
        items.set(left, items.get(right));
        items.set(right, previous);
    }

    // So sánh theo thứ tự tự nhiên.
    public static <T extends Comparable<? super T>> T maximum(
            List<? extends T> items) {
        if (items.isEmpty()) {
            throw new IllegalArgumentException("Empty list");
        }

        T best = items.get(0);
        for (int i = 1; i < items.size(); i++) {
            T candidate = items.get(i);
            if (candidate.compareTo(best) > 0) {
                best = candidate;
            }
        }
        return best;
    }

    // Hai tham số kiểu độc lập.
    public static <K, V> Map.Entry<K, V> entry(K key, V value) {
        return Map.entry(key, value);
    }
}
```

Đây là một **lớp tiện ích thực sự có thể biên dịch trên Java 17**. Các phương thức không giữ trạng thái và không cần tạo instance `GenericUtils`.

### Cách chạy và kết quả

Tạo thêm tệp **`GenericUtilsDemo.java`** cùng thư mục:

```java
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class GenericUtilsDemo {
    public static void main(String[] args) {
        String name = GenericUtils.identity("spring");
        Integer count = GenericUtils.identity(7);
        List<String> one = GenericUtils.only("lesson");
        String first = GenericUtils.firstOrThrow(
                List.of("core", "generics"));

        List<String> colors = new ArrayList<>(
                List.of("red", "blue"));
        GenericUtils.swap(colors, 0, 1);

        Integer biggestNumber = GenericUtils.maximum(
                List.of(9, 4, 11));
        String biggestWord = GenericUtils.maximum(
                List.of("Go", "Java", "Kotlin"));

        Map.Entry<String, Integer> version =
                GenericUtils.entry("JDK", 17);

        System.out.println(name.toUpperCase());
        System.out.println(count);
        System.out.println(one);
        System.out.println(first);
        System.out.println(colors);
        System.out.println(biggestNumber);
        System.out.println(biggestWord);
        System.out.println(version.getKey() + "=" + version.getValue());
    }
}
```

Biên dịch và chạy:

```bash
javac -encoding UTF-8 GenericUtils.java GenericUtilsDemo.java
java GenericUtilsDemo
```

`stdout`:

```text
SPRING
7
[lesson]
core
[blue, red]
11
Kotlin
JDK=17
```

Vì `List.of(...)` trả về danh sách không thể chỉnh sửa cấu trúc hoặc phần tử qua `set`, ví dụ tạo `new ArrayList<>(...)` trước khi gọi `swap`. Nếu truyền trực tiếp `List.of("red", "blue")` vào `swap` thì compiler vẫn có thể chấp nhận kiểu `List<String>`, nhưng chương trình sẽ ném `UnsupportedOperationException` khi gọi `set`. **Generics kiểm tra kiểu phần tử, không bảo đảm collection có thể sửa đổi.**

Các tiện ích `only` và `entry` sử dụng `List.of` và `Map.entry` trong JDK. Hai factory này không nhận đối số `null` và tạo đối tượng không sửa đổi được theo cách tương ứng; nếu truyền `null`, lời gọi có thể ném `NullPointerException`. Cần thể hiện rõ các điều kiện đầu vào trong API thay vì mặc định chúng được generics kiểm tra.

## 5. Mỗi lời gọi suy luận đối số kiểu thế nào?

Khi thấy:

```java
String text = GenericUtils.identity("Java");
Integer number = GenericUtils.identity(42);
```

trình biên dịch phân tích chữ ký `<T> T identity(T value)` cùng kiểu của đối số và **kiểu đích** nơi kết quả được sử dụng. Trong hai ví dụ, nó suy luận `T` lần lượt là `String` và `Integer`.

Ta có thể chỉ định đối số kiểu tường minh bằng **type witness**:

```java
String text = GenericUtils.<String>identity("Java");
Integer number = GenericUtils.<Integer>identity(42);
```

Dấu `<String>` được đặt **ngay trước tên phương thức**, sau dấu chấm của tên lớp. Cú pháp này khác với `new Box<String>(...)` của constructor và khác `new Box<>(...)` dùng diamond.

Một ví dụ có lỗi:

```java
// Integer broken = GenericUtils.<Integer>identity("Java");
```

Đây là mã lỗi được chú thích: nếu ép `T = Integer` một cách tường minh mà vẫn truyền chuỗi, compiler từ chối vì `String` không tương thích với tham số `Integer`.

### Khi nào nên viết type witness?

Hầu hết trường hợp **không cần** vì Java suy luận được kiểu. Type witness giúp minh họa hoặc làm rõ một lời gọi có ngữ cảnh suy luận phức tạp, đặc biệt khi có nhiều tham số generic, method overload hay type bound. Không nên viết type witness ở mọi lời gọi chỉ để lặp thông tin compiler đã biết.

Ví dụ một giá trị có kiểu rộng:

```java
Object value = "Java";
Object same = GenericUtils.identity(value);
```

Tại điểm gọi, **kiểu tĩnh** của `value` là `Object` nên quan hệ kiểu suy luận theo `Object`; phương thức không tự nhận ra đây chắc chắn là `String` chỉ vì đối tượng lúc chạy có lớp `String`.

## 6. Phân biệt tham số kiểu của lớp và của phương thức `static`

Một lớp generic có tham số kiểu riêng:

```java
final class Box<T> {
    private final T value;

    Box(T value) {
        this.value = value;
    }

    T get() {
        return value;
    }
}
```

`T` được gắn với **một cách tham số hóa của lớp**, chẳng hạn `Box<String>`. Phương thức `static` lại thuộc về **lớp khai báo**, không thuộc riêng `Box<String>` hay `Box<Integer>`. Vì thế, trong lớp generic không thể dùng trực tiếp `T` của lớp ở một thành phần `static`:

```java
// Minh họa các khai báo sai khi đặt trong class Box<T>:
// private static T shared;
// static T readShared() { return null; }
```

Để xây dựng một phương thức tạo hộp `static`, khai báo **tham số kiểu riêng** cho phương thức:

```java
static <U> Box<U> of(U value) {
    return new Box<>(value);
}
```

Ở đây `<U>` thuộc về phương thức `of`, còn `Box<U>` mô tả kiểu trả về. Lớp không cần dùng chính ký hiệu `U`; một tên như `TValue` cũng hợp lệ miễn dùng nhất quán.

### Factory method chạy được

Tạo tệp **`StaticFactoryDemo.java`**:

```java
public class StaticFactoryDemo {
    static final class Box<T> {
        private final T value;

        private Box(T value) {
            this.value = value;
        }

        T get() {
            return value;
        }

        static <U> Box<U> of(U value) {
            return new Box<>(value);
        }
    }

    public static void main(String[] args) {
        Box<String> language = Box.of("Java");
        Box<Integer> year = Box.of(2026);

        System.out.println(language.get().toUpperCase());
        System.out.println(year.get() + 1);
    }
}
```

Biên dịch và chạy:

```bash
javac -encoding UTF-8 StaticFactoryDemo.java
java StaticFactoryDemo
```

`stdout`:

```text
JAVA
2027
```

Phương thức `of` có thể tạo `Box<String>` hay `Box<Integer>` từ cùng một định nghĩa. Đây là trường hợp quen thuộc của **generic static factory method**. Với thiết kế thư viện, phương thức factory còn có thể kiểm tra đầu vào, trả về các implementation khác nhau hoặc tái sử dụng đối tượng khi hợp đồng cho phép; những hành vi đó phải được cài đặt rõ, không tự đến từ tính generic.

## 7. Phương thức tiện ích có nhiều tham số kiểu

Không phải mọi tiện ích chỉ cần `T`. Để tạo cặp khóa–giá trị:

```java
public static <K, V> Map.Entry<K, V> entry(K key, V value) {
    return Map.entry(key, value);
}
```

`<K, V>` **khai báo hai tham số kiểu độc lập**. Với `GenericUtils.entry("JDK", 17)`:

- `K` tương ứng với `String`.
- `V` tương ứng với `Integer`.
- Kiểu trả về là `Map.Entry<String, Integer>`.

Tại đây `Map.Entry` là interface lồng trong API `java.util.Map`, có thể dùng làm đối tượng kết quả cho một cặp khóa–giá trị. Factory `Map.entry(...)` của JDK trả một entry không sửa đổi được bằng `setValue` và không cho phép khóa hoặc giá trị `null`.

Nếu tiện ích chỉ dùng một kiểu cho cả hai đối số:

```java
public static <T> boolean sameReference(T left, T right) {
    return left == right;
}
```

đây là **đoạn phương thức minh họa**, trả về so sánh **cùng tham chiếu**, không phải so sánh nội dung như `Objects.equals`. Compiler có thể suy luận một kiểu cha chung cho hai đối số khác kiểu; việc dùng cùng một chữ `T` không tự bảo đảm hai đối tượng cùng lớp runtime.

Chỉ tạo nhiều tham số kiểu khi cần biểu đạt **quan hệ kiểu có ý nghĩa**. Không cần generic nếu hàm không truyền hay dùng thông tin kiểu:

```java
public static int countCharacters(String text) {
    return text.length();
}
```

Hàm trên chỉ xử lý `String`; biến nó thành generic không đem lại lợi ích tự nhiên.

## 8. Ràng buộc kiểu cho thuật toán cần khả năng so sánh

Một phương thức `identity(T)` không yêu cầu thao tác riêng của `T`. Nhưng thuật toán tìm giá trị lớn nhất cần một cách so sánh. Chữ ký trong `GenericUtils`:

```java
public static <T extends Comparable<? super T>> T maximum(
        List<? extends T> items)
```

Đây là **chữ ký minh họa**, thân phương thức đã có trong tệp `GenericUtils.java`.

Các thành phần:

1. `<T extends Comparable<? super T>>` là một **bounded type parameter**: kiểu `T` phải có khả năng so sánh với giá trị kiểu `T` thông qua hợp đồng `Comparable` phù hợp.
2. `List<? extends T>` cho phép phương thức **đọc** các giá trị có kiểu phù hợp với `T`. Nó không cho phép tự do thêm một `T` tùy ý vào danh sách nguồn.
3. `T maximum(...)` trả về giá trị theo kiểu `T` mà compiler có thể xác định từ ngữ cảnh lời gọi.
4. `candidate.compareTo(best) > 0` thể hiện thứ tự tự nhiên được chính kiểu đối tượng cung cấp.

Ví dụ:

```java
Integer largest = GenericUtils.maximum(List.of(3, 8, 5));
String latest = GenericUtils.maximum(
        List.of("Alpha", "Beta", "Gamma"));
```

Đoạn nằm trong một phương thức và sử dụng tệp `GenericUtils.java` ở trên; cần import `java.util.List`. `largest` là `8`, `latest` là `"Gamma"` theo thứ tự từ điển của `String.compareTo`. Nếu dùng một loại đối tượng không triển khai quan hệ `Comparable` thích hợp, compiler không chấp nhận lời gọi theo chữ ký này.

### Trường hợp không có thứ tự tự nhiên

Nếu kiểu dữ liệu không có `Comparable` hoặc người dùng muốn một quy tắc thứ tự khác, tiện ích có thể nhận **`Comparator<? super T>`** thay vì bắt `T` có thứ tự tự nhiên:

```java
import java.util.Comparator;
import java.util.List;

public final class ComparisonUtils {
    private ComparisonUtils() { }

    public static <T> T maximumBy(
            List<? extends T> items,
            Comparator<? super T> comparator) {
        if (items.isEmpty()) {
            throw new IllegalArgumentException("Empty list");
        }

        T best = items.get(0);
        for (int i = 1; i < items.size(); i++) {
            T candidate = items.get(i);
            if (comparator.compare(candidate, best) > 0) {
                best = candidate;
            }
        }
        return best;
    }
}
```

Đây là lớp đầy đủ trong **`ComparisonUtils.java`**, độc lập với các ví dụ khác. Ví dụ gọi nằm trong một phương thức sau khi import `java.util.List` và `java.util.Comparator`:

```java
String longest = ComparisonUtils.maximumBy(
        List.of("A", "Java", "Code"),
        Comparator.comparingInt(String::length));
```

`longest` có giá trị `"Java"`: cả `"Java"` và `"Code"` đều dài 4 ký tự, nhưng thuật toán chỉ thay `best` khi giá trị so sánh **lớn hơn** (không thay khi bằng), vì vậy phần tử xuất hiện trước được giữ lại.

Đây vẫn là một **static generic utility** với tham số kiểu `<T>`. Việc dùng `Comparator` tách **thuật toán** ra khỏi **quy tắc so sánh**, phù hợp với các tiện ích có thể tái sử dụng.

## 9. Kiểu ở nơi đọc và nơi ghi: vì sao utility nên nghĩ tới wildcard?

Xét hai thao tác khác nhau:

- `firstOrThrow(List<? extends T>)` chỉ cần **đọc** một phần tử và trả về `T`.
- `swap(List<T>, int, int)` cần cả đọc và **ghi trở lại** các phần tử cùng danh sách.

Trong utility đầu, wildcard `? extends T` diễn đạt một nguồn chỉ cần cung cấp các giá trị thuộc `T` hoặc kiểu con. Trong utility thứ hai, ta dùng `List<T>` để đọc và đặt lại các giá trị đúng cùng một kiểu đã liên kết với danh sách.

Một phương thức chép phần tử từ nguồn sang đích có thể dùng nguyên tắc thường nhớ là **PECS: Producer Extends, Consumer Super**:

```java
public static <T> void copyAll(
        List<? extends T> source,
        List<? super T> target) {
    for (T item : source) {
        target.add(item);
    }
}
```

Đây là **đoạn phương thức minh họa**, phải khai báo trong lớp hợp lệ và import `java.util.List`. Ví dụ:

```java
List<Integer> numbers = List.of(1, 2, 3);
List<Number> output = new ArrayList<>();

copyAll(numbers, output);
System.out.println(output); // [1, 2, 3]
```

Đoạn trên cần nằm trong một phương thức của lớp chứa `copyAll`, đồng thời import `List` và `ArrayList`. `source` cung cấp `Integer`, còn `target` chấp nhận `Number`. Về trách nhiệm của utility, việc gắn quan hệ kiểu bằng `<T>` giúp compiler kiểm tra điều kiện tương thích mà không yêu cầu người gọi ép kiểu.

Không nên áp dụng wildcard máy móc. Nếu API cần chính xác một kiểu `T` ở nhiều vị trí, `List<T>` có thể rõ ràng hơn; nếu chỉ cần đọc hoặc chỉ cần ghi theo quan hệ phân cấp, wildcard giúp mở rộng phạm vi sử dụng hợp lệ.

## 10. Java đã có nhiều generic static utility

Trước khi viết lại một tiện ích, kiểm tra JDK có cung cấp sẵn không:

| API | Ví dụ sử dụng | Công dụng |
|---|---|---|
| `Collections.emptyList()` | `List<String> xs = Collections.emptyList();` | Danh sách rỗng có kiểu phần tử được suy luận; không thể sửa đổi. |
| `Collections.singletonList("x")` | `List<String> xs = Collections.singletonList("x");` | Danh sách một phần tử, không thể sửa đổi. |
| `List.of("a", "b")` | `List<String> xs = List.of("a", "b");` | Factory tạo danh sách không thể sửa đổi; từ chối phần tử `null`. |
| `Map.entry("k", 1)` | `Map.Entry<String, Integer> e = Map.entry("k", 1);` | Tạo entry theo hai đối số kiểu được suy luận. |
| `Comparator.comparingInt(...)` | `Comparator<String> c = Comparator.comparingInt(String::length);` | Tạo comparator từ hàm lấy khóa số nguyên. |
| `Optional.ofNullable(x)` | `Optional<String> o = Optional.ofNullable(x);` | Tạo optional có hoặc không có giá trị, cho phép đầu vào `null`. |

Các lời gọi trong bảng là **đoạn mã minh họa bên trong một phương thức**, với những import cần thiết từ `java.util`. Đây là các factory hoặc utility API của JDK, nhiều phương thức tự khai báo tham số kiểu nên tự suy luận được ở điểm gọi.

Lưu ý: `List.of(...)` và `Map.entry(...)` xuất hiện từ Java 9, nên có sẵn trong Java 17. Các tiện ích có thể trả về cấu trúc **không sửa đổi được**, chứ không phải mọi collection từ factory đều có thể thêm/xóa/sắp xếp tại chỗ. Hãy đọc hợp đồng mutability của API trước khi sử dụng.

## 11. Phương thức generic `static` và method reference

Một phương thức generic `static` cũng có thể được sử dụng thông qua **method reference** nếu ngữ cảnh cung cấp kiểu hàm mục tiêu.

Tạo tệp **`MethodReferenceDemo.java`**:

```java
import java.util.function.Function;

public class MethodReferenceDemo {
    static <T> T identity(T value) {
        return value;
    }

    public static void main(String[] args) {
        Function<String, String> textIdentity =
                MethodReferenceDemo::identity;
        Function<Integer, Integer> numberIdentity =
                MethodReferenceDemo::identity;

        System.out.println(textIdentity.apply("Java"));
        System.out.println(numberIdentity.apply(17) + 1);
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 MethodReferenceDemo.java
java MethodReferenceDemo
```

`stdout`:

```text
Java
18
```

`Function<String, String>` và `Function<Integer, Integer>` là **hai kiểu đích khác nhau** cho cùng một method reference `MethodReferenceDemo::identity`. Trình biên dịch dựa vào kiểu đích để suy luận tham số kiểu của lần sử dụng tương ứng.

Một tiện ích generic hữu ích khi nhiều nơi gọi có các kiểu dữ liệu khác nhau nhưng vẫn chia sẻ được quy tắc tính toán. Tuy nhiên, không nên dùng generic để che mất nghĩa nghiệp vụ: ví dụ một phương thức thực sự chỉ hỗ trợ chuỗi thì chữ ký `String` cụ thể thường rõ hơn `<T>` kết hợp ép kiểu.

## 12. Giới hạn và lỗi thường gặp

### 12.1. `static` không được dùng tham số kiểu của instance class

Sai:

```java
class Box<T> {
    // static T cached;               // Sai
    // static T createDefault() { ... } // Sai
}
```

Một phương thức `static` cần khai báo tham số kiểu của chính nó (như `static <U> Box<U> of(U x)`) hoặc làm việc với kiểu cụ thể. Nguyên nhân không phải do thiếu constructor, mà vì `T` của `Box<T>` thuộc về phần kiểu của mỗi lần sử dụng lớp, còn thành phần `static` không gắn với một lần tham số hóa duy nhất.

### 12.2. Không thể tạo đối tượng từ `new T()` hoặc mảng trực tiếp `new T[n]`

Do Java dùng type erasure, phương thức generic không biết constructor của `T` chỉ nhờ tham số kiểu:

```java
// Trong một phương thức khai báo <T>:
// T object = new T();  // Không hợp lệ
// T[] values = new T[3]; // Không hợp lệ
```

Nếu thực sự cần tạo đối tượng kiểu `T`, có thể nhận một factory như `Supplier<? extends T>` từ người gọi. Ví dụ phương thức:

```java
public static <T> T create(
        java.util.function.Supplier<? extends T> factory) {
    return factory.get();
}
```

Phương thức trên cần đặt trong một lớp; người dùng cung cấp constructor reference hoặc lambda có thể tạo kiểu thích hợp. Các vấn đề liên quan tới tạo mảng generic thường cần xử lý thông qua mảng kiểu có thể kiểm tra runtime, API collection hoặc factory phù hợp; không nên ép kiểu mảng không được kiểm tra chỉ để tránh lỗi biên dịch.

### 12.3. Trùng chữ ký sau type erasure

Hai phương thức sau **không thể cùng khai báo** trong một lớp:

```java
// Ví dụ lỗi, không đặt vào mã chạy:
static <T> void show(T value) { }
static void show(Object value) { }
```

Vì `T` không có bound bị xóa về `Object`, hai chữ ký JVM trùng nhau sau *type erasure*. Một ví dụ khác:

```java
// Ví dụ lỗi: cả hai cùng erasure theo List.
static void process(List<String> input) { }
static void process(List<Integer> input) { }
```

Compiler phát hiện **name clash**. Muốn xử lý theo nhiều loại dữ liệu, hãy chọn chữ ký API không xung đột, tên phương thức khác hoặc thiết kế generic phù hợp. Không thể dùng mỗi đối số kiểu generic khác nhau làm tiêu chí overload ở runtime.

### 12.4. `List.of(...)` không thể dùng cho utility sửa đổi danh sách

```java
// GenericUtils.swap(List.of("A", "B"), 0, 1);
```

Dòng trên hợp lệ về **quan hệ kiểu**, nhưng khi thực thi sẽ ném `UnsupportedOperationException` do danh sách không hỗ trợ `set`. Để dùng `swap` ở ví dụ này, hãy tạo danh sách có thể sửa đổi:

```java
List<String> values = new ArrayList<>(List.of("A", "B"));
GenericUtils.swap(values, 0, 1);
```

Hai dòng cần import `List` và `ArrayList`, đồng thời chạy với lớp `GenericUtils` đã định nghĩa. Generic chỉ bảo đảm quan hệ kiểu, không bảo đảm mutability hay tính hợp lệ của chỉ số. Nếu `left` hoặc `right` ngoài biên, `get`/`set` ném `IndexOutOfBoundsException`.

### 12.5. Primitive và `null` vẫn cần lưu ý

Không thể dùng `<int>` làm đối số kiểu vì primitive không hợp lệ ở generic; thông thường dùng `Integer` và Java tự boxing/unboxing.

Còn `identity(null)` có thể hợp lệ khi compiler suy luận được kiểu phù hợp, và `identity` sẽ trả lại `null`. Ngược lại, `only(null)` sử dụng `List.of(null)` nên ném `NullPointerException`. Hai phương thức cùng generic nhưng **chính phần cài đặt và hợp đồng API** quyết định chúng xử lý `null` thế nào.

### 12.6. Raw type và ép kiểu không an toàn

Mã sử dụng `List` raw hoặc cast không được kiểm tra có thể phá ràng buộc phần tử, làm xuất hiện `ClassCastException` khi đọc. Nên biên dịch với những cảnh báo phù hợp, chẳng hạn:

```bash
javac -Xlint:rawtypes,unchecked GenericUtils.java GenericUtilsDemo.java
```

Lệnh này không thay đổi cách utility vận hành; nó yêu cầu compiler thông báo rõ hơn về các thao tác generic không an toàn nếu có.

## 13. Quy trình thiết kế một generic static utility

Khi muốn viết hàm dùng lại cho nhiều kiểu, có thể đi theo thứ tự:

1. **Xác định thao tác độc lập trạng thái:** ví dụ đọc phần tử đầu, đổi chỗ, tạo cặp, tìm giá trị lớn nhất.
2. **Xác định quan hệ kiểu trong API:** dữ liệu đầu vào và đầu ra dùng cùng `T`, hay phải có hai tham số `<K,V>`?
3. **Đặt `<T>` trước kiểu trả về:** `static <T> T identity(T value)`. Nếu phương thức trả `void` vẫn khai báo `<T>`.
4. **Thêm bound khi thuật toán thật sự cần:** `<T extends Comparable<? super T>>` cho thứ tự tự nhiên; đừng thêm bound tùy tiện.
5. **Xét chiều đọc/ghi của collection:** dùng `? extends T` cho nguồn đọc, `? super T` cho đích nhận dữ liệu khi phù hợp.
6. **Đưa điều kiện runtime vào hợp đồng:** danh sách có rỗng không, có sửa đổi được không, chỉ số có hợp lệ không, giá trị có được `null` không?
7. **Ưu tiên API JDK đã có:** `Collections`, `List.of`, `Map.entry` hoặc các tiện ích khác trước khi viết bản sao.
8. **Giữ chữ ký đơn giản và kiểm tra compiler:** tránh raw type, cast không kiểm tra và overload xung đột sau erasure.
9. **Dùng tên lớp khi gọi:** `GenericUtils.swap(...)` nói rõ phương thức thuộc lớp tiện ích, giúp mã dễ tìm và bảo trì.

Không cần chuyển mọi phương thức `static` thành generic. Nếu hàm chỉ xử lý `String` hoặc `int` theo nghiệp vụ riêng, một chữ ký kiểu cụ thể có thể đúng và dễ hiểu hơn.

## 14. Tự kiểm tra

**Câu 1.** Trong chữ ký `static <T> T identity(T value)`, tại sao có cả `<T>` lẫn `T` đứng trước tên phương thức?

**Câu 2.** Vì sao `GenericUtils.identity("Java")` và `GenericUtils.identity(42)` dùng cùng một định nghĩa phương thức mà kết quả có kiểu tĩnh khác nhau?

**Câu 3.** Với `class Holder<T>`, tại sao không thể khai báo `static T value;` nhưng có thể khai báo `static <U> Holder<U> of(U value)`?

**Câu 4.** Lợi ích của chữ ký `copyAll(List<? extends T> source, List<? super T> target)` là gì?

**Câu 5.** Tại sao `GenericUtils.swap(List.of("a", "b"), 0, 1)` gây lỗi khi chạy, dù Java Generics chấp nhận kiểu `List<String>`?

**Câu 6.** Vì sao `static <T> void show(T value)` và `static void show(Object value)` không thể cùng tồn tại trong một lớp?

**Gợi ý đáp án:** (1) `<T>` khai báo tham số kiểu của phương thức, `T` trước tên là kiểu trả về. (2) Java suy luận `T` riêng cho từng lời gọi dựa trên đối số và ngữ cảnh. (3) Thành phần `static` không thuộc một cách tham số hóa cụ thể của lớp; `<U>` là tham số kiểu độc lập của factory. (4) Diễn đạt mối quan hệ kiểu cho phép chép từ nguồn cung cấp `T` sang đích chấp nhận `T`, kể cả qua quan hệ kiểu cha–con hợp lệ. (5) `List.of` tạo danh sách không sửa đổi được; generic không đảm bảo hỗ trợ `set`. (6) Sau type erasure, `T` không bị ràng buộc được xóa về `Object`, gây trùng chữ ký.

**Ghi nhớ:** Một phương thức generic `static` kết hợp **hàm không phụ thuộc instance** với **kiểm tra quan hệ kiểu ở compile time**. Cú pháp `static <T>` cho phép mỗi lời gọi dùng kiểu phù hợp mà không phải tạo lớp hoặc overload riêng; chất lượng của utility nằm ở một hợp đồng kiểu rõ ràng, điều kiện runtime minh bạch và khả năng tái sử dụng mà không đánh mất an toàn kiểu.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="generic-methods-006">Truyền đối số kiểu tường minh khi gọi phương thức</a>

<details>
<summary>Xem chi tiết</summary>

# Truyền đối số kiểu tường minh khi gọi phương thức generic trong Java

Thông thường, khi gọi một phương thức generic, Java tự **suy luận đối số kiểu (type argument)** từ đối số truyền vào và ngữ cảnh. Tuy nhiên, người viết mã cũng có thể **chỉ định trực tiếp kiểu cho tham số kiểu của phương thức**. Kỹ thuật này thường được gọi là *explicit method type arguments* hoặc *type witness*.

Ví dụ, với phương thức:

```java
static <T> T identity(T value) {
    return value;
}
```

hai lời gọi sau đều hợp lệ khi viết trong lớp `Demo` có khai báo phương thức trên:

```java
String a = Demo.identity("Java");           // Compiler suy luận T = String
String b = Demo.<String>identity("Java");   // Chỉ định T = String tường minh
```

Điểm cần ghi nhớ: trong lời gọi tường minh, **`<String>` được viết sau tên lớp/đối tượng và dấu chấm, ngay trước tên phương thức**. Nó là đối số kiểu của **method**, khác với `<T>` nằm trước kiểu trả về trong **khai báo** method.

## 1. Khai báo `<T>` và truyền `<String>` là hai hành động khác nhau

Chữ ký phương thức generic có phần khai báo tham số kiểu:

```java
static <T> T identity(T value) {
    return value;
}
```

- `<T>`: **khai báo** tham số kiểu có tên `T`, dùng bên trong chữ ký và thân phương thức.
- `T` đứng sau `<T>`: **kiểu trả về** của phương thức.
- `T value`: **kiểu của tham số đầu vào**. Hai vị trí `T` biểu diễn cùng một tham số kiểu.
- `Demo.<String>identity("Hello")`: **sử dụng** phương thức bằng cách cung cấp đối số kiểu `String` thay cho `T`.
- `Demo.identity("Hello")`: cũng sử dụng phương thức, nhưng để trình biên dịch suy luận `T` dựa trên ràng buộc.

Tức là một generic method được **khai báo một lần**, rồi có thể được gọi nhiều lần với những kiểu khác nhau. Việc chỉ định `<String>` không tạo phiên bản mã nguồn riêng hay thay đổi class runtime; Java generics thông thường vẫn tuân theo cơ chế **type erasure**.

Bảng đối chiếu:

| Ngữ cảnh | Cú pháp | Vai trò của phần trong `<...>` |
|---|---|---|
| Khai báo generic method | `static <T> T identity(T value)` | Khai báo tên tham số kiểu `T` |
| Gọi để compiler suy luận | `Demo.identity("Hi")` | Không ghi type argument, suy luận khi biên dịch |
| Gọi và chỉ định kiểu | `Demo.<String>identity("Hi")` | Cung cấp `String` cho tham số kiểu `T` |
| Khai báo generic class | `class Box<T> { ... }` | Khai báo `T` thuộc lớp |
| Dùng generic class | `Box<String> box` | Chọn `String` cho `T` thuộc lớp |
| Gọi method nhiều type parameter | `Demo.<String, Integer>entry("age", 36)` | Chọn theo thứ tự `K = String`, `V = Integer` |

Không nên gọi `<T>` trong chữ ký là “truyền đối số kiểu”: đó là **khai báo tham số kiểu**. Ngược lại, `<String>` tại điểm gọi mới là **type argument tường minh**.

## 2. Cú pháp lời gọi tường minh cho static method

Với một static generic method, cách viết dễ hiểu và ổn định nhất là **dùng tên lớp trước dấu chấm**:

```java
Demo.<String>identity("Java");
Demo.<Integer>identity(42);
Demo.<Number>identity(42);
```

Các dòng trên là **minh họa lời gọi**; cần có lớp `Demo` chứa `static <T> T identity(T value)` và đặt lời gọi trong phương thức/khối hợp lệ.

`Demo.<String>identity("Java")` có nghĩa: “gọi `identity` và yêu cầu sử dụng `T = String`”. Trong trường hợp `Demo.<Number>identity(42)`, literal `42` được autobox thành `Integer`; đối tượng `Integer` có thể được sử dụng tại vị trí yêu cầu `Number`. Do đó kiểu trả về ở điểm gọi là `Number`, dù object thực tế là `Integer`.

Cú pháp **sai**:

```java
// Demo.identity<String>("Java"); // Sai thứ tự theo cú pháp Java
// Demo<String>.identity("Java"); // Sai với class Demo không generic
// Demo.<int>identity(42);        // Sai: primitive không phải type argument
```

Trong Java, type argument tường minh **không đặt sau tên phương thức như một số ngôn ngữ khác**. Cần ghi `Demo.<String>identity(...)`, không ghi `Demo.identity<String>(...)`. Type argument cũng phải là kiểu tham chiếu phù hợp; `Integer` được phép, `int` không được phép.

**Có bắt buộc chỉ định không?** Không. `Demo.identity("Java")` thường ngắn, rõ ràng và được compiler suy luận chính xác. Type witness hữu ích khi muốn làm rõ ý định kiểu hoặc khi suy luận mặc định không cho kiểu cần dùng.

## 3. Instance generic method: đặt `<T>` sau dấu chấm của đối tượng

Phương thức instance cũng có thể tự khai báo tham số kiểu:

```java
class Printer {
    <T> String describe(T value) {
        return "value=" + value;
    }
}
```

Khi sử dụng:

```java
Printer printer = new Printer();

String a = printer.describe(10);               // Suy luận T = Integer
String b = printer.<Integer>describe(10);      // Chọn T tường minh
String c = new Printer().<String>describe("x");
```

Đây là các **đoạn code kết hợp minh họa**, cần đặt trong bối cảnh lớp và phương thức thích hợp. Khác biệt giữa static và instance:

- Static: `Utility.<T>method(...)` với qualifier là tên lớp (hoặc kiểu chứa method).
- Instance: `receiver.<T>method(...)` với qualifier là biểu thức/tham chiếu đối tượng có method đó.
- Trong phương thức instance của cùng lớp, có thể dùng `this.<T>method(...)`; với method kế thừa được truy cập phù hợp có thể có `super.<T>method(...)`.

**Type witness gắn với lời gọi method**, không gắn với kiểu của biến receiver. Ví dụ một `Holder<String>` có method `<U> U echo(U x)` vẫn cho phép `holder.<Integer>echo(5)`. `String` là type argument của **lớp Holder**, còn `Integer` là type argument riêng của **echo**.

Một lỗi dễ mắc: viết `printer.<String>describe(10)` chỉ vì muốn kết quả là String. Trả về String cố định của method không quyết định tham số kiểu `T`. Khi chỉ định `T = String`, tham số `value` yêu cầu String; truyền `int` không phù hợp. Type witness luôn tuân theo **vị trí `T` thực sự xuất hiện trong chữ ký**.

## 4. Ví dụ Java 17 chạy độc lập: so sánh suy luận và chỉ định tường minh

Lưu nguyên chương trình thành **`ExplicitTypeArgumentsDemo.java`**:

```java
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class ExplicitTypeArgumentsDemo {
    static <T> T identity(T value) {
        return value;
    }

    static <T> List<T> two(T first, T second) {
        return List.of(first, second);
    }

    static <K, V> Map.Entry<K, V> entry(K key, V value) {
        return Map.entry(key, value);
    }

    static <T extends Number> double twice(T number) {
        return number.doubleValue() * 2;
    }

    static final class Holder<T> {
        private final T stored;

        Holder(T stored) {
            this.stored = stored;
        }

        T stored() {
            return stored;
        }

        <U> List<U> duplicate(U value) {
            return List.of(value, value);
        }
    }

    public static void main(String[] args) {
        String inferred = identity("Core");
        String explicit =
                ExplicitTypeArgumentsDemo.<String>identity("Java");
        Number widened =
                ExplicitTypeArgumentsDemo.<Number>identity(42);

        List<Number> numbers =
                ExplicitTypeArgumentsDemo.<Number>two(7, 2.5);

        Map.Entry<String, Integer> record =
                ExplicitTypeArgumentsDemo.<String, Integer>entry(
                        "age", 36);

        Holder<String> holder = new Holder<>("stored");
        List<Integer> repeated = holder.<Integer>duplicate(5);

        var inferredEmpty = Collections.emptyList();
        var typedEmpty = Collections.<String>emptyList();

        System.out.println("inferred: " + inferred);
        System.out.println("explicit: " + explicit);
        System.out.println("widened: " + widened);
        System.out.println("numbers: " + numbers);
        System.out.println("entry: " + record.getKey()
                + "=" + record.getValue());
        System.out.println("class value: " + holder.stored());
        System.out.println("method value: " + repeated);
        System.out.println("empty sizes: "
                + inferredEmpty.size() + "/" + typedEmpty.size());
        System.out.println("bound: "
                + ExplicitTypeArgumentsDemo.<Integer>twice(4));
    }
}
```

Biên dịch/chạy với Java 17 trở lên tại thư mục chứa tệp:

```bash
javac ExplicitTypeArgumentsDemo.java
java ExplicitTypeArgumentsDemo
```

Stdout theo đúng thứ tự:

```text
inferred: Core
explicit: Java
widened: 42
numbers: [7, 2.5]
entry: age=36
class value: stored
method value: [5, 5]
empty sizes: 0/0
bound: 8.0
```

Phân tích những điểm quan trọng:

1. `identity("Core")`: compiler suy luận `T = String` từ đối số mà không cần type witness. `ExplicitTypeArgumentsDemo.<String>identity("Java")` ghi rõ cùng loại ràng buộc.
2. `<Number>identity(42)`: type witness **đặt kiểu tĩnh trả về là `Number`**. Đầu vào `42` được boxing thành `Integer` và có thể truyền cho tham số `Number`. Type witness không biến object runtime thành object `Number` thuần; nó vẫn là instance `Integer`.
3. `<Number>two(7, 2.5)` chọn một kiểu chung `Number` cho cả hai phần tử. `7` và `2.5` được boxing lần lượt thành `Integer` và `Double`, rồi được dùng qua kiểu `Number`. `List.of` ở đây không cho phép sửa nội dung bằng các phương thức thêm/xóa.
4. `<String, Integer>entry("age", 36)` điền **hai type argument theo thứ tự** khai báo `<K, V>`. `Map.entry` tạo một entry không chấp nhận `null` và không hỗ trợ thay `value` qua `setValue`.
5. `Holder<String>` có tham số kiểu lớp `T = String`. Riêng `holder.<Integer>duplicate(5)` chọn `U = Integer` cho method, hoàn toàn độc lập với tham số kiểu của lớp.
6. `Collections.emptyList()` trả danh sách rỗng immutable với method signature `<T> List<T> emptyList()`. Khi dùng `var inferredEmpty`, không có một target type cụ thể để buộc `T = String`, nên type inference chọn `Object` trong ví dụ này. `var typedEmpty = Collections.<String>emptyList()` giữ được kiểu biến là `List<String>` ở compile time. Kích thước hai danh sách đều là 0; khác biệt không hiển thị ở stdout, mà nằm trong **kiểu tĩnh**.
7. `<Integer>twice(4)` cho `T = Integer`, một subtype của `Number`; `doubleValue()` trả `4.0` rồi nhân 2 thành `8.0`.

**Lưu ý:** việc method cho phép chỉ định kiểu rộng hơn (`<Number>`) không đồng nghĩa mọi kiểu bất kỳ đều hợp lệ. `<String>twice("4")` sai vì `String` không thỏa bound `T extends Number`.

## 5. Khi nào explicit type argument thật sự hữu ích?

### 5.1. Khi không có đối số để suy luận `T`

Một ví dụ chuẩn từ Java Collections:

```java
List<String> a = Collections.emptyList();         // Suy luận từ kiểu đích
var b = Collections.<String>emptyList();         // Chọn kiểu tường minh
```

Hai dòng là **đoạn minh họa**, cần import `List` và `Collections` và đặt vào method. Cả hai có kiểu `List<String>`. Với dòng thứ nhất, **target typing** từ bên trái cung cấp ràng buộc; với dòng thứ hai, `<String>` cung cấp ràng buộc ngay tại lời gọi dù kiểu biến được suy luận bằng `var`.

Đặc biệt hữu ích khi generic method **trả về kiểu có `T` nhưng không nhận tham số nào mang `T`**, ví dụ `Collections.<String>emptyList()`. Nếu chỉ gọi `Collections.emptyList()` trong một ngữ cảnh không yêu cầu kiểu phần tử cụ thể, compiler có thể chọn `Object`.

### 5.2. Khi muốn API trả về một kiểu rộng có chủ đích

```java
List<Number> values =
        ExplicitTypeArgumentsDemo.<Number>two(7, 2.5);
```

Trong ví dụ, `Integer` và `Double` đều dùng được qua `Number`. Ghi type witness giúp thể hiện rõ **quyết định dùng một kiểu chung**. Không có nó, compiler vẫn có thể suy luận một common type phù hợp (tùy các ràng buộc/target type); explicit type argument chủ yếu giúp làm rõ hoặc cố định ý định.

`<Number>` không tự ép mọi giá trị thành số hợp lệ: gọi `two("seven", 2.5)` khi yêu cầu `T = Number` sẽ không biên dịch.

### 5.3. Khi ngữ cảnh làm suy luận khó đọc hoặc không đạt kiểu cần dùng

Một lời gọi lồng nhau có thể có nhiều ràng buộc suy luận cùng lúc. Nếu compiler suy luận không như ý, chỉ định type witness **tại chính generic method liên quan** giúp người đọc và trình biên dịch biết kiểu mục tiêu. Ví dụ:

```java
static <T> List<T> empty() {
    return List.of();
}

// Khi dùng:
var texts = Helper.<String>empty();
```

Đây là **minh họa**, giả sử phương thức `empty` nằm trong lớp `Helper` và đã import `List`. `texts` sẽ có kiểu `List<String>`, thay vì để compiler chọn tham số kiểu từ ngữ cảnh nghèo thông tin.

Trước khi thêm explicit type argument, cũng nên cân nhắc **khai báo biến có kiểu đích rõ ràng**:

```java
List<String> texts = Helper.empty();
```

Với kiểu đích có sẵn, compiler thường suy luận được và lời gọi ngắn hơn. Không phải lúc nào type witness cũng giúp chương trình đúng hơn.

### 5.4. Khi viết ví dụ hướng dẫn hoặc kiểm tra generic API

Trong tài liệu API hoặc bài kiểm tra biên dịch, `Utility.<Long>identity(1L)` giúp chỉ ra rằng phương thức đã được gọi với `T = Long`. Tuy nhiên, trong mã sản phẩm, nếu `Utility.identity(1L)` đã rõ và an toàn, thường nên ưu tiên suy luận.

## 6. Explicit type argument phải tuân theo chữ ký method

**Type witness không phải cast**, không dùng để né hợp đồng kiểu.

Xét `static <T> T identity(T input)`, với tệp ví dụ có class `ExplicitTypeArgumentsDemo`:

```java
String text =
        ExplicitTypeArgumentsDemo.<String>identity("hello"); // Hợp lệ

Number number =
        ExplicitTypeArgumentsDemo.<Number>identity(12); // Hợp lệ

// String invalid =
//         ExplicitTypeArgumentsDemo.<String>identity(12); // Không biên dịch
```

Trong trường hợp lỗi, `<String>` yêu cầu tham số đầu vào là `String`, nhưng `12` là `int` và không có chuyển đổi invocation hợp lệ từ `int` sang `String`. Không thể “ra lệnh cho compiler” coi mọi đối số là `String` chỉ bằng cú pháp `<String>`.

Nếu type parameter có bound:

```java
static <T extends Number> double twice(T value) {
    return value.doubleValue() * 2;
}
```

thì chỉ những type argument thỏa bound mới hợp lệ:

```java
double ok = ExplicitTypeArgumentsDemo.<Integer>twice(3);
double alsoOk = ExplicitTypeArgumentsDemo.<Number>twice(3);

// double wrong =
//         ExplicitTypeArgumentsDemo.<String>twice("3"); // Sai bound
```

Các đoạn này là **mã minh họa**; phương thức `twice` ở ví dụ chạy độc lập đã có phần thân đúng. Lưu ý `T = Number` vẫn hợp lệ vì `Number` thỏa upper bound `Number`.

Tương tự, nếu method có **hai tham số kiểu** `<K, V>` thì lời gọi tường minh cần cung cấp cả hai type argument theo đúng thứ tự: `ClassName.<String, Integer>entry(...)`. Không thể chỉ viết `ClassName.<String>entry(...)` và yêu cầu compiler tự điền riêng `V` trong type argument list ấy; Java không cho pha trộn danh sách type argument tường minh không đầy đủ với suy luận các phần còn lại theo cách đó.

## 7. Những ngữ cảnh cần phân biệt

### Generic method khác generic class

```java
class Store<T> {
    T read() {
        return null;
    }

    <U> U echo(U value) {
        return value;
    }
}
```

Trong lớp minh họa, `read()` trả `T` thuộc **class `Store<T>`** và không tự khai báo type parameter mới. Vì vậy `store.<String>read()` **không phải cách chỉ định lại `T` của class**. Kiểu của `T` được chọn khi khai báo biến/đối tượng, ví dụ `Store<Integer> store = new Store<>();`.

Ngược lại, `store.<String>echo("x")` chỉ định type argument riêng cho generic method `echo`, và có thể khác type argument `Integer` của `Store`.

### Generic method có return type cố định

```java
static <T> void print(T value) {
    System.out.println(value);
}
```

Có thể gọi `Utility.<String>print("Hi")` (giả sử method nằm trong `Utility`). Việc method trả `void` không ngăn nó là generic method; type witness vẫn áp dụng lên **tham số đầu vào**. Ngược lại, type witness **không sửa** return type của method: nếu method trả `int`, chọn `<String>` không biến nó thành method trả `String`.

### Generic constructor

Constructor có thể có type parameter riêng; nhưng **cú pháp truyền type argument cho constructor** không giống gọi method sau dấu chấm. Trong lời gọi generic constructor, type arguments có thể đứng sau `new` và trước tên lớp:

```java
class Message {
    <T> Message(T input) {
        System.out.println(input);
    }
}

// new <String> Message("Hi");
```

Đây là **minh họa**, không nằm trong chương trình `ExplicitTypeArgumentsDemo`. Cú pháp `new <String> Message("Hi")` thuộc generic constructor, còn `Utility.<String>method("Hi")` thuộc generic method. Không trộn hai vị trí khai báo/chỉ định này.

### Generic type arguments và primitive

Không thể cung cấp `int` làm type argument: `Utility.<int>identity(5)` sai, `Utility.<Integer>identity(5)` hợp lệ. Nếu gọi `identity(5)` để compiler suy luận, autoboxing cho phép `T` được thể hiện bằng kiểu bao `Integer`, không phải primitive `int`.

## 8. Cú pháp khi gọi instance method qua `this`, `super`, hoặc biểu thức

Bên cạnh tên lớp (static call), lời gọi có receiver cũng nhận type witness:

```java
class Base {
    <T> T copy(T value) {
        return value;
    }
}

class Child extends Base {
    String example() {
        String viaThis = this.<String>copy("one");
        String viaSuper = super.<String>copy("two");
        return viaThis + "/" + viaSuper;
    }
}
```

Hai class trên là **đoạn minh họa**. `Child.example()` trả `"one/two"`. `this.<String>copy(...)` gọi thông qua receiver hiện tại; `super.<String>copy(...)` gọi triển khai có thể truy cập của lớp cha theo quy tắc `super` thông thường. Type witness không thay đổi cơ chế kế thừa hay dispatch mà chỉ làm rõ kiểu cho generic method.

Một biểu thức trả về đối tượng cũng có thể làm receiver:

```java
String a = new Printer().<String>echo("A");
```

Ở đây giả sử `Printer` có một instance method generic `<T> T echo(T value)`. Vị trí quan trọng vẫn là **sau dấu chấm và trước tên method**. Đừng viết `new Printer()<String>.echo("A")`; đó không phải cú pháp method invocation hợp lệ.

## 9. Type inference và overload: đừng dùng type witness như cơ chế ép chọn overload

Khi có nhiều method cùng tên, Java sử dụng các quy tắc **overload resolution** (xét kiểu đối số, phương thức áp dụng được, mức độ cụ thể...) để chọn phương thức tại compile time. Generic type arguments tường minh có thể ảnh hưởng việc một generic candidate có áp dụng được hay không, nhưng **không bảo đảm ép chọn một overload bất kỳ** nếu có các overload khác.

Nếu gặp lời gọi ambiguous hoặc overload không như mong muốn, nên:

1. Kiểm tra **chữ ký** và các bound của những generic method cùng tên.
2. Đọc các kiểu **tĩnh** của đối số và ngữ cảnh trả về.
3. Thử một type witness hợp lệ nếu mục tiêu là đặt type argument cho generic candidate.
4. Khi cần chỉ định overload theo một kiểu tham số rõ ràng, cân nhắc đổi kiểu biểu thức đối số hợp lệ, đổi tên method, hoặc thiết kế lại overload để tránh mơ hồ.

Ví dụ, `<T>` ở một method không tự mang danh tính runtime riêng để overload với `Object`. Hai chữ ký cùng lớp sau **xung đột sau type erasure**, nên không biên dịch:

```java
void log(Object value) { }

<T> void log(T value) { }
```

Đây là **ví dụ lỗi tách biệt**: `T` không bound bị erasure về `Object`, nên hai method có cùng chữ ký JVM. Việc gọi với `<String>` không chữa được lỗi trùng chữ ký ở phần khai báo.

## 10. Type erasure: chỉ định `<T>` không lưu kiểu ở runtime

Khi viết:

```java
String value = Utility.<String>identity("x");
```

trình biên dịch hiểu `T = String` cho lời gọi, dùng để kiểm tra tham số và kiểu trả về. Nhưng generic method trong Java thường được triển khai bằng **type erasure**:

- Type parameter không bound như `T` thường bị erasure thành `Object`.
- Compiler kiểm tra ràng buộc và có thể chèn cast ở nơi lấy kết quả.
- Không tạo một `identity<String>` và một `identity<Integer>` riêng ở runtime chỉ vì type witness khác nhau.
- Không thể hỏi trực tiếp một instance: “method vừa được gọi với `T` nào?” nếu thông tin đó không được mang theo qua cơ chế khác.
- Type witness không biến một phép cast sai thành an toàn; các lỗi kiểu vẫn được phát hiện tại compile time khi có thể.

Để biết một lớp runtime một cách tường minh, API có thể nhận `Class<T>`:

```java
static <T> T checkedCast(Class<T> type, Object value) {
    return type.cast(value);
}
```

Đây là **method minh họa**, phải được khai báo trong class. Cách gọi `Utility.<String>checkedCast(String.class, someObject)` vừa chỉ định type argument `String`, vừa truyền **runtime type token** `String.class`; chính `Class.cast` sẽ kiểm tra object ở runtime. Type witness đơn lẻ không thực hiện bước kiểm tra runtime đó. Khi loại `T` chỉ biểu diễn các kiểu có type argument generic lồng nhau, `Class<T>` cũng không lưu đầy đủ mọi type argument như `List<String>`; cần thiết kế type token phù hợp nếu yêu cầu phức tạp hơn.

## 11. Những lỗi cú pháp và cách dùng thường gặp

**Lỗi 1: Đặt type argument sau tên method.** `Utility.identity<String>("x")` sai cú pháp Java. Sửa thành `Utility.<String>identity("x")`.

**Lỗi 2: Nhầm type argument của class với của method.** `Holder<String>` chọn kiểu cho lớp; `holder.<Integer>duplicate(5)` chọn kiểu riêng cho method. Không thể dùng type witness ở lời gọi để thay đổi tham số kiểu của một class instance đã có.

**Lỗi 3: Chỉ định primitive làm type argument.** `Utility.<int>identity(5)` không hợp lệ. Dùng `<Integer>` hoặc bỏ type witness để compiler suy luận qua boxing.

**Lỗi 4: Chỉ định một phần đối số kiểu của method nhiều tham số kiểu.** Nếu khai báo `<K, V>`, phải ghi đủ `<String, Integer>` khi viết tường minh; không ghi chỉ `<String>`. Nếu muốn suy luận cả hai, bỏ toàn bộ danh sách tường minh.

**Lỗi 5: Chọn type argument không phù hợp kiểu đầu vào.** `Utility.<String>identity(42)` sai vì `42` không truyền được cho tham số `String`. Type witness không phải cast.

**Lỗi 6: Chọn type argument vi phạm bound.** Với `<T extends Number>`, `<String>` không hợp lệ. Bound vẫn được compiler kiểm tra đầy đủ dù type argument được ghi tường minh.

**Lỗi 7: Thêm type witness ở mọi lời gọi mà không có lợi ích.** `Utility.<String>identity("x")` đúng, nhưng `Utility.identity("x")` thường đủ rõ. Explicit type arguments nên giải thích hoặc giải quyết một nhu cầu kiểu thực sự, tránh làm code rối.

**Lỗi 8: Tưởng type witness thay đổi return type cố định.** Với `<T> int size(List<T> input)`, return type luôn là `int`; `Utility.<String>size(...)` không làm return type trở thành `String`.

**Lỗi 9: Lẫn `var` với explicit type argument.** `var x = Collections.emptyList()` để compiler tự suy luận kiểu biến theo lời gọi; `var x = Collections.<String>emptyList()` chỉ định `String` cho method. `var` không tự mang ý nghĩa “`T = String`”.

**Lỗi 10: Hiểu nhầm `List<Integer>` được đổi sang `List<Number>` nhờ witness.** Generics thường invariant. `List<Integer> integers` không thể trực tiếp gán sang `List<Number>`. Type witness ở một method tạo danh sách mới có thể giúp thiết kế **kiểu kết quả** phù hợp, nhưng không biến đổi quan hệ subtype giữa hai kiểu danh sách đã tồn tại.

**Lỗi 11: Nghĩ type argument tường minh làm object runtime tự đổi class.** `<Number>identity(42)` trả về một tham chiếu được nhìn qua `Number` ở compile time; object vẫn thuộc lớp `Integer`. Không có hành vi biến `Integer` thành một instance `Number` riêng.

**Lỗi 12: Tưởng method nào cũng chấp nhận `.<T>`.** Không phải mọi method khai báo type parameter riêng. Nếu chỉ `class Box<T>` mà `T get()` không có `<U>`, không thể dùng `box.<String>get()` để thay `T` của lớp. Xác định đúng **generic method declaration** trước khi gọi.

## 12. Bài luyện tập tự kiểm tra

**Câu 1.** Cho `static <T> T identity(T x)`. Cách nào truyền tường minh `T = String` qua tên lớp `Util`?  
**Đáp án:** `Util.<String>identity("abc")`.

**Câu 2.** `Util.identity("abc")` có khác `Util.<String>identity("abc")` về giá trị trả về trong trường hợp này không?  
**Đáp án:** không; cả hai đều trả chuỗi `"abc"` và có kiểu tĩnh `String` thích hợp. Lời gọi thứ nhất dùng suy luận, lời gọi thứ hai chỉ định.

**Câu 3.** Vì sao `Util.<int>identity(5)` sai?  
**Đáp án:** type argument của generic method trong Java 17 phải là kiểu tham chiếu; dùng `Integer`.

**Câu 4.** Với `static <K, V> Map.Entry<K, V> entry(K k, V v)`, muốn dùng `K = String`, `V = Long` thì viết thế nào?  
**Đáp án:** `Util.<String, Long>entry("id", 1L)`.

**Câu 5.** `Holder<String>` có method `<U> U echo(U x)`. Có thể gọi `holder.<Integer>echo(10)` không?  
**Đáp án:** có; type parameter `U` của method độc lập với type parameter `T` của lớp Holder.

**Câu 6.** Vì sao `var xs = Collections.<String>emptyList()` giữ được `String` ở kiểu tĩnh?  
**Đáp án:** `<String>` chọn `T = String` cho phương thức `<T> List<T> emptyList()`; `var` lấy kiểu biểu thức kết quả là `List<String>`.

**Câu 7.** Có thể viết `Util.<String>identity(42)` để compiler tự biến `42` thành chuỗi không?  
**Đáp án:** không; không có phép chuyển đổi truyền tham số phù hợp từ `int` sang `String`.

**Câu 8.** Với `static <T extends Number> double twice(T value)`, `Util.<Number>twice(5)` hợp lệ không?  
**Đáp án:** có. `Number` thỏa bound; `5` được boxing thành `Integer`, có thể dùng như `Number`.

**Câu 9.** `<String>` ở `Util.<String>identity(...)` có tạo một class/method runtime riêng không?  
**Đáp án:** không; Java generics thông thường dùng type erasure. Type witness phục vụ kiểm tra/suy luận kiểu khi biên dịch.

**Câu 10.** Khi nào nên bỏ type witness?  
**Đáp án:** khi compiler suy luận đúng và lời gọi không gây mơ hồ, ví dụ `identity("abc")`. Chỉ định tường minh khi cần giữ một kiểu cụ thể, làm rõ ý định hoặc giúp kiểm tra ràng buộc.

**Câu 11.** Type argument tường minh được đặt ở đâu khi gọi instance method?  
**Đáp án:** sau biểu thức receiver và dấu chấm, ngay trước tên method: `obj.<String>echo("abc")`.

**Câu 12.** Một method `static List<String> words()` có thể nhận `Util.<Integer>words()` chỉ vì return type là generic `List<String>` không?  
**Đáp án:** không có tham số kiểu method để đặt thành `Integer`; chữ ký `words()` trả một `List<String>` cố định.

Hãy ghi nhớ hai mẫu khác nhau: **khai báo** `static <T> T method(T x)` và **gọi tường minh** `ClassName.<String>method("x")`. Một bên định nghĩa tham số kiểu; bên kia chọn đối số kiểu cho một lời gọi. Trong đa số trường hợp, compiler tự suy luận tốt; khi lựa chọn tường minh, kiểu đã chọn vẫn phải thỏa mọi tham số, bound và quy tắc an toàn kiểu của Java.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="generic-methods-007">Constructor generic và phân biệt với constructor của lớp generic</a>

<details>
<summary>Xem chi tiết</summary>

# Constructor generic và phân biệt với constructor của lớp generic trong Java

## 1. Mục tiêu và câu hỏi trung tâm

Trong Java, **lớp generic** và **constructor generic** đều dùng tham số kiểu nhưng chúng khai báo tham số ấy ở **hai phạm vi khác nhau**:

- `class Box<T>`: `T` là tham số kiểu **của lớp**, được lựa chọn khi sử dụng kiểu `Box<String>`, `Box<Integer>`...
- `<U> Box(U input)`: `U` là tham số kiểu **riêng của constructor**, được suy luận hoặc chỉ định theo **lần tạo đối tượng**.
- `class Box<T> { Box(T value) { ... } }`: constructor dùng `T` của lớp nhưng **không phải generic constructor theo nghĩa khai báo tham số kiểu riêng**.
- Một **lớp thông thường** không có `<T>` vẫn có thể khai báo **constructor generic**.

Sự khác biệt này quan trọng khi đối tượng cần lưu một kiểu `T`, nhưng dữ liệu khởi tạo đến từ kiểu `U` khác và cần chuyển đổi thành `T`. Nó cũng giúp hiểu vì sao có thể kết hợp `new Holder<>(...)` với phương thức chuyển đổi, không cần ép kiểu thủ công.

Bài học tập trung vào **cú pháp constructor**, phạm vi type parameter, type inference, bound, overload và cách ứng dụng. Ví dụ độc lập cuối bài tương thích **Java 17+**.

## 2. Trước hết: constructor của lớp generic chưa chắc là generic constructor

Xét một lớp tổng quát chỉ giữ một giá trị:

```java
// Mảnh mã minh họa, cần đặt trong tệp Java phù hợp.
final class Box<T> {
    private final T value;

    Box(T value) {
        this.value = value;
    }

    T get() {
        return value;
    }
}

Box<String> greeting = new Box<>("Xin chào");
Box<Integer> quantity = new Box<>(5);
```

Có hai tầng cần tách bạch:

1. `<T>` sau `Box` **khai báo tham số kiểu của lớp**.
2. Constructor `Box(T value)` **dùng tham số `T` đã có**, không tự khai báo tham số kiểu nào khác.

`Box<String>` xác lập `T = String`, vì vậy constructor nhận `String`, getter trả `String`. `Box<Integer>` tương tự với `Integer`. Nếu truyền kiểu không phù hợp, compiler từ chối:

```java
// Box<Integer> wrong = new Box<Integer>("hello"); // Lỗi biên dịch.
```

Diamond `<>` trong `new Box<>("Xin chào")` chỉ là ký hiệu **suy luận type argument của lớp** từ ngữ cảnh và đối số constructor. **Có diamond không đồng nghĩa constructor generic.**

So sánh với một lớp không generic:

```java
final class NameTag {
    private final String name;

    NameTag(String name) {
        this.name = name;
    }
}
```

`NameTag(String)` không khai báo tham số kiểu riêng; `NameTag` cũng không có tham số kiểu. Cả hai trường hợp `Box(T)` và `NameTag(String)` đều là những constructor thông thường theo tiêu chí "constructor có khai báo type parameter riêng hay không".

## 3. Cú pháp chính xác của generic constructor

Một constructor được gọi là **generic constructor** khi ngay trên constructor có danh sách khai báo tham số kiểu, ví dụ `<U>` hoặc `<S extends Number>`, **đứng trước tên constructor**:

```java
import java.util.function.Function;

// Mảnh mã minh họa.
final class ConvertedBox<T> {
    private final T value;

    <S> ConvertedBox(S source, Function<? super S, ? extends T> converter) {
        this.value = converter.apply(source);
    }

    T get() {
        return value;
    }
}
```

Trong mã trên:

- `T` thuộc về `ConvertedBox<T>`: nó là kiểu của dữ liệu được lưu và giá trị getter.
- `S` thuộc về constructor `<S> ConvertedBox(...)`: nó là kiểu của **nguồn** ở một lần khởi tạo.
- `Function<? super S, ? extends T>` biểu diễn phép chuyển từ nguồn `S` sang dữ liệu tương thích `T`.
- Sau khi constructor kết thúc, đối tượng vẫn là `ConvertedBox<T>`; nó **không mang thêm tham số lớp `S`**.

Cách sử dụng:

```java
ConvertedBox<Integer> length =
        new ConvertedBox<>("Java", String::length);
Integer n = length.get(); // 4.

ConvertedBox<String> numberText =
        new ConvertedBox<>(42, Object::toString);
String text = numberText.get(); // "42".
```

Ở lời gọi thứ nhất, compiler phải làm cho `T = Integer` phù hợp với kiểu đích của biến, đồng thời suy luận `S = String` cho đối số `"Java"`. Ở lời gọi thứ hai, `T = String` và `S = Integer`. Thay đổi nguồn đầu vào **không thay đổi nghĩa của kiểu đích**.

Cú pháp tổng quát của constructor:

```java
// modifiers <type-parameters> ClassName(parameter-list) throws ExceptionType {
//     // thân constructor
// }
```

**Không khai báo kiểu trả về cho constructor**, kể cả `void`. Trường hợp `<U> Box(U source)` vẫn là constructor vì sau `<U>` là **tên lớp** `Box`, không có kiểu trả về.

## 4. Lớp bình thường vẫn có constructor generic

Lớp không cần khai báo `<T>` mới được phép khai báo constructor có `<U>`. Ví dụ, một lớp chỉ muốn giữ chuỗi mô tả từ nhiều loại nguồn khác nhau:

```java
import java.util.function.Function;

// Mảnh mã minh họa.
final class DisplayText {
    private final String content;

    <S> DisplayText(S source, Function<? super S, String> formatter) {
        this.content = formatter.apply(source);
    }

    String content() {
        return content;
    }
}

// Trong một phương thức Java:
DisplayText count = new DisplayText(123, Object::toString);
DisplayText upper = new DisplayText("java", String::toUpperCase);
```

`DisplayText` là **lớp thông thường**, không có type parameter trên tên lớp. `S` chỉ tồn tại trong khai báo constructor. Vì `formatter` tạo `String`, dữ liệu cuối cùng vẫn luôn được lưu ở trường `String content`.

Điểm ứng dụng: constructor nhận **nhiều nguồn có kiểu khác nhau**, nhưng đối tượng thành phẩm có cấu trúc cố định. Trong một số API, static factory hoặc method generic có tên rõ ràng có thể dễ đọc hơn một constructor chung; chọn dựa trên hợp đồng khởi tạo thực tế.

### Một constructor có thể dùng nhiều tham số kiểu

```java
import java.util.function.BiFunction;

final class RenderedText {
    private final String rendered;

    <A, B> RenderedText(
            A first,
            B second,
            BiFunction<? super A, ? super B, String> format) {
        rendered = format.apply(first, second);
    }

    String value() {
        return rendered;
    }
}

// RenderedText view = new RenderedText("Lan", 9,
//         (name, score) -> name + ": " + score);
// view.value() là "Lan: 9".
```

`A` và `B` là hai tham số kiểu **của constructor**; chúng có thể khác nhau. Vì vậy, `<A,B>` diễn đạt chính xác hơn việc ép cả hai đối số thành cùng một `T`. Với converter, `? super A` và `? super B` cho phép hàm nhận kiểu cha thích hợp, đồng thời đầu ra của `BiFunction` là `String`.

## 5. Lớp generic có thể vừa có constructor thường vừa có constructor generic

Một lớp có thể khai báo hai constructor khác nhau, mỗi constructor phục vụ một đường khởi tạo riêng:

```java
import java.util.Objects;
import java.util.function.Function;

final class Holder<T> {
    private final T value;

    // Constructor thường: chỉ dùng T của lớp.
    Holder(T initial) {
        this.value = Objects.requireNonNull(initial);
    }

    // Constructor generic: khai báo thêm S của riêng constructor.
    <S> Holder(S source, Function<? super S, ? extends T> mapper) {
        this.value = Objects.requireNonNull(mapper.apply(source));
    }

    T get() {
        return value;
    }
}
```

Gọi hai overload:

```java
Holder<String> simple = new Holder<>("Lan");

Holder<Integer> transformed =
        new Holder<>("Spring", String::length);

String a = simple.get();        // "Lan".
Integer b = transformed.get();  // 6.
```

Với overload `Holder(T initial)`, dữ liệu đầu vào đã có kiểu phù hợp `T` nên không cần converter. Với `<S> Holder(S, Function<...>)`, nguồn có thể khác kiểu đích và cần một hàm chuyển đổi tường minh.

Constructor generic **không biến cả lớp thành generic theo một tham số mới**. Lớp chỉ có `T`; `S` của constructor không được truy cập ở getter hay phương thức khác, trừ khi chúng tự khai báo tham số kiểu phù hợp.

### Vì sao không nên khai báo `<T>` trùng tên của lớp?

Cú pháp sau có thể hợp lệ nhưng gây nhầm lẫn:

```java
class Confusing<T> {
    private final T value;

    // <T> ở constructor là T MỚI, che T của lớp trong chữ ký này.
    <T> Confusing(T input) {
        // this.value = input; // Không hợp lệ: hai T không được đảm bảo giống nhau.
        this.value = null; // Chỉ minh họa lỗi thiết kế.
    }
}
```

Hai tên `T` giống nhau không đồng nghĩa chúng cùng một type parameter. `<T>` được constructor khai báo **che khuất** tham số kiểu `T` của lớp bên trong phạm vi constructor, làm mất quan hệ an toàn giữa `input` và trường `value`. Trong thực tế **không nên viết** constructor kiểu này; hãy dùng `<S>` nếu cần một tham số riêng, hoặc bỏ `<...>` nếu đầu vào phải đúng kiểu `T` của lớp.

## 6. Cách compiler suy luận kiểu của lớp và kiểu của constructor

Khi gặp:

```java
Holder<Integer> result = new Holder<>("Java", String::length);
```

Compiler có hai nhiệm vụ liên quan nhưng riêng biệt:

1. Tìm type argument cho **lớp** `Holder<T>`, phù hợp với ngữ cảnh đích `Holder<Integer>`, nghĩa là `T = Integer`.
2. Tìm type argument cho **constructor generic** `<S> Holder(S, Function<? super S, ? extends T>)`, phù hợp với chuỗi nguồn và mapper, nghĩa là `S = String`.

Compiler kiểm tra `String::length` có thể dùng để tạo kết quả kiểu `Integer` cho phương thức `Function.apply` hay không. `String.length()` trả primitive `int`, được boxing thành `Integer` khi cần.

### Diamond `<>` áp dụng cho tham số kiểu của lớp

```java
Holder<String> first = new Holder<>("Hi");
Holder<String> second = new Holder<String>("Hi");
```

`new Holder<>()` bỏ việc lặp lại `String` bên phải khi compiler có đủ ngữ cảnh. Đây là **diamond operator**, không phải danh sách type parameter của constructor. Dù chọn overload generic hay overload thường, biểu thức `new` vẫn tạo đối tượng của kiểu lớp `Holder<T>`.

### Khi dùng `var`, phải xem kiểu suy luận là gì

```java
var text = new Holder<>("Java");
var length = new Holder<>("Java", String::length);
```

Ở dòng đầu, compiler có thể suy luận `Holder<String>` từ constructor nhận giá trị `"Java"`. Dòng thứ hai, chữ ký mapper và target type không tường minh tạo bài toán suy luận phức tạp hơn; đừng mặc định compiler luôn suy luận đúng kiểu cuối cùng mà bạn muốn. Để thể hiện hợp đồng dữ liệu mong muốn, khai báo đích rõ:

```java
Holder<Integer> length = new Holder<>("Java", String::length);
```

Nếu inference trở nên khó đọc hoặc compiler báo không giải được type constraints, có thể truyền type argument tường minh cho lớp hoặc thiết kế factory method có chữ ký rõ hơn.

### Chỉ định type argument của constructor tường minh

Constructor generic hỗ trợ **explicit constructor type arguments** ở dạng sau:

```java
// Giả sử class Holder<T> có generic constructor <S> Holder(S, Function<...>).
Holder<Integer> length =
        new <String> Holder<Integer>("Java", String::length);
```

`<String>` **đặt sau `new` và trước tên lớp** là đối số kiểu **của constructor** `S`. `<Integer>` đi sau `Holder` là đối số kiểu **của lớp** `T`. Đây là cú pháp khác với lời gọi **generic method** `Collections.<String>emptyList()`. Thông thường hãy để Java tự suy luận constructor type arguments cho ngắn gọn; chỉ ghi tường minh khi thực sự giúp chẩn đoán hoặc làm rõ chữ ký.

Không dùng dạng `new Holder<String><Integer>(...)`: Java không đặt danh sách type arguments của constructor sau tên kiểu. Khi chỉ định constructor type arguments tường minh, chọn cú pháp hợp lệ như ví dụ `new <String> Holder<Integer>(...)`.

## 7. Constructor generic có bounded type parameter

Khi quá trình tạo đối tượng cần thao tác chỉ có trên một số loại dữ liệu, thêm upper bound ngay tại type parameter của constructor:

```java
import java.util.Collection;

final class NumericSummary {
    private final double total;

    <N extends Number> NumericSummary(Collection<N> values) {
        double result = 0;
        for (N value : values) {
            result += value.doubleValue();
        }
        this.total = result;
    }

    double total() {
        return total;
    }
}

// NumericSummary ints = new NumericSummary(List.of(1, 2, 3));
// NumericSummary doubles = new NumericSummary(List.of(1.5, 2.5));
// new NumericSummary(List.of("a", "b")); // Không đạt bound Number.
```

`<N extends Number>` nghĩa là **chỉ constructor** yêu cầu nguồn thuộc `Number` hoặc kiểu con; lớp `NumericSummary` không phải lớp generic. Compiler cho phép gọi `doubleValue()` vì bound đã bảo đảm thao tác đó tồn tại. Bound interface cũng dùng từ khóa `extends`, ví dụ `<S extends CharSequence>`.

Nếu cần nhiều bound, dùng `<N extends Number & Comparable<N>>`; bound lớp, nếu có, phải đứng trước các interface. Hãy chọn bound đủ đáp ứng thao tác của constructor, tránh ép kiểu có thể gây lỗi.

### Bounded constructor không phải lớp có bound

Hai thiết kế có khác biệt:

```java
// Lớp giữ nguyên kiểu số T xuyên suốt đời đối tượng.
final class NumberHolder<T extends Number> {
    private final T value;
    NumberHolder(T value) { this.value = value; }
    T get() { return value; }
}

// Lớp không generic, constructor tạm dùng N để đọc giá trị số.
final class DoubleSnapshot {
    private final double value;
    <N extends Number> DoubleSnapshot(N source) {
        value = source.doubleValue();
    }
    double get() { return value; }
}
```

`NumberHolder<Integer>.get()` trả `Integer` theo hợp đồng generic của lớp. `DoubleSnapshot.get()` luôn trả `double`; tham số `N` chỉ có ý nghĩa **trong lúc khởi tạo**. Constructor generic thích hợp nếu dữ liệu từ nhiều kiểu được chuẩn hóa về một representation duy nhất.

## 8. Constructor chaining: `this(...)` và `super(...)`

Một constructor có thể gọi constructor khác của chính lớp bằng `this(...)` hoặc constructor của lớp cha bằng `super(...)`. Với lớp generic, kiểu truyền giữa các bước phải được compiler kiểm tra.

```java
import java.util.function.Function;

class BaseBox<T> {
    private final T value;
    BaseBox(T value) { this.value = value; }
    T get() { return value; }
}

final class ConvertedChild<T> extends BaseBox<T> {
    <S> ConvertedChild(S source, Function<? super S, ? extends T> converter) {
        super(converter.apply(source));
    }
}

// ConvertedChild<Integer> c =
//         new ConvertedChild<>("hello", String::length);
```

Ở đây `T` đến từ lớp con và đi tiếp vào lớp cha `BaseBox<T>`; `S` chỉ thuộc constructor, dùng để chuyển nguồn. Lời gọi `super(...)` là câu lệnh đầu tiên của constructor trên Java 17, đúng cú pháp chuẩn.

Một ví dụ chuyển hướng giữa các overload:

```java
import java.util.function.Function;

final class Payload<T> {
    private final T value;

    Payload(T direct) {
        this.value = direct;
    }

    <S> Payload(S source, Function<? super S, ? extends T> converter) {
        this(converter.apply(source));
    }

    T get() { return value; }
}
```

Constructor generic gọi constructor thường sau khi có dữ liệu `T`. Điều này tránh lặp logic thiết lập trường và kiểm tra invariant; nếu muốn chặn `null`, kiểm tra tại constructor chính, ví dụ `this.value = Objects.requireNonNull(direct)`.

Cần nhớ: mỗi constructor phải kết thúc với toàn bộ trường `final` được gán đúng một lần theo các đường khởi tạo hợp lệ. Việc dùng generic không thay đổi quy tắc definite assignment của Java.

## 9. Khi nào nên dùng static generic factory thay constructor generic?

Constructor generic hợp lý khi mục đích tạo đối tượng có thể biểu diễn rõ qua chữ ký. Nhưng compiler đôi khi khó suy luận quan hệ giữa kiểu nguồn và kiểu đích với diamond, đặc biệt khi có nhiều overload. **Static factory method generic** là lựa chọn thường dễ đọc hơn:

```java
import java.util.function.Function;

final class Result<T> {
    private final T value;

    private Result(T value) {
        this.value = value;
    }

    static <T> Result<T> of(T value) {
        return new Result<>(value);
    }

    static <S, T> Result<T> from(
            S source,
            Function<? super S, ? extends T> converter) {
        return new Result<>(converter.apply(source));
    }

    T get() { return value; }
}

// Result<Integer> length = Result.from("Java", String::length);
// Result<String> rawValue = Result.of("Java");
```

Ở `Result.from`, cả `S` và `T` đều là tham số kiểu **của factory method**. Trong constructor generic của `ConvertedBox<T>`, `T` thuộc lớp còn `S` thuộc constructor. Cả hai cách đều có thể an toàn kiểu; tên phương thức `from` nêu rõ thao tác **chuyển đổi** và giúp API dễ mở rộng hoặc ghi chú chính sách validation.

Factory cũng cho phép trả về subtype, instance cache, object singleton hoặc loại cụ thể khác khi hợp đồng cho phép; constructor luôn khởi tạo lớp cụ thể được viết sau `new`.

### Phân biệt factory generic với diamond

```java
Result<String> one = Result.of("abc");    // Generic method inference.
Result<String> two = new Result<>("abc"); // Chỉ hợp lệ nếu constructor accessible.
```

Nếu constructor là `private` như ví dụ `Result` ở trên, dòng thứ hai **không hợp lệ bên ngoài lớp**. Nó chỉ minh họa rằng có hai cơ chế khác nhau: `of` là method generic, còn `new Result<>(...)` là biểu thức khởi tạo có diamond và bị kiểm soát bởi access modifier. Không thêm mã có lỗi vào chương trình runnable.

## 10. Generic constructor trong record và quy tắc gọi constructor

Với `record` generic, tham số kiểu lớp vẫn thuộc về record:

```java
record Item<T>(T value) {
    // Constructor thông thường tương đương canonical constructor ở góc độ kiểu T.
}
```

Một constructor khác canonical trong record phải **ủy quyền trực tiếp hoặc gián tiếp cho canonical constructor bằng `this(...)`**. Nó cũng có thể khai báo tham số kiểu riêng:

```java
import java.util.function.Function;

record ValueRecord<T>(T value) {
    <S> ValueRecord(S source, Function<? super S, ? extends T> converter) {
        this(converter.apply(source));
    }
}

// ValueRecord<Integer> n = new ValueRecord<>("Java", String::length);
// n.value() trả Integer 4.
```

`T` thuộc record, `S` thuộc constructor bổ sung. Đây là cách áp dụng tương tự lớp generic thông thường, nhưng phải tuân thủ quy tắc constructor của `record`. Không nên tạo một constructor mang kiểu nguồn tùy ý khi một static factory đặt tên rõ hơn và tránh nhập nhằng overload.

## 11. Tham số kiểu không tồn tại như một giá trị runtime

Java generics chủ yếu dùng **type erasure**: type parameter được kiểm tra lúc biên dịch rồi bị thay bằng bound thích hợp trong biểu diễn runtime thông thường. Điều này đúng cả với generic constructor.

```java
// Mảnh minh họa trong một constructor <S> hoặc phương thức <S>:
// S instance = new S();       // Sai: không biết constructor thực sự của S.
// Class<S> clazz = S.class;    // Sai: không có S.class.
// boolean ok = source instanceof S; // Sai với type parameter không reifiable.
```

Nếu cần **tạo mới một `S`** bên trong constructor, truyền factory hoặc thông tin kiểu phù hợp:

```java
import java.util.function.Supplier;

final class ProducedText {
    private final String text;

    <S> ProducedText(Supplier<S> factory) {
        S source = factory.get();
        this.text = String.valueOf(source);
    }

    String text() { return text; }
}

// ProducedText view = new ProducedText(StringBuilder::new);
```

Việc tạo đối tượng được giao cho `Supplier<S>` thay vì cố gọi `new S()`. `String.valueOf(null)` trả chuỗi `"null"`, khác với `Objects.requireNonNull` sẽ ném `NullPointerException`; chọn chính sách rõ ràng theo yêu cầu API.

### Overload theo type parameter có thể đụng erasure

Hai constructor sau **không được khai báo đồng thời**:

```java
// Ví dụ cố ý không biên dịch.
class Invalid {
    <S> Invalid(java.util.List<S> items) {}
    Invalid(java.util.List<String> items) {}
}
// Hai chữ ký sau erasure cùng nhận List nên name clash.
```

Tương tự, `<S> Invalid(S item)` sau erasure thường có parameter `Object` nếu `S` không bound; đừng dựa vào generic parameter để phân biệt overload vốn có chữ ký erasure giống nhau.

## 12. Chương trình Java 17+ chạy độc lập

Tạo tệp **`GenericConstructorDemo.java`** với nội dung sau. Chương trình minh họa constructor thường nhận `T` của lớp, constructor generic nhận `S` riêng, constructor generic trong lớp thông thường, bound và constructor chaining.

```java
import java.util.*;
import java.util.function.Function;

public class GenericConstructorDemo {
    static final class Holder<T> {
        private final T value;

        Holder(T direct) {
            this.value = Objects.requireNonNull(direct);
        }

        <S> Holder(S source, Function<? super S, ? extends T> mapper) {
            this(mapper.apply(source));
        }

        T get() {
            return value;
        }
    }

    static final class DisplayText {
        private final String text;

        <S> DisplayText(S source, Function<? super S, String> format) {
            text = Objects.requireNonNull(format.apply(source));
        }

        String value() {
            return text;
        }
    }

    static final class NumericSummary {
        private final double total;

        <N extends Number> NumericSummary(Collection<N> numbers) {
            double sum = 0;
            for (N number : numbers) {
                sum += number.doubleValue();
            }
            total = sum;
        }

        double total() {
            return total;
        }
    }

    static class BaseBox<T> {
        private final T value;

        BaseBox(T value) {
            this.value = Objects.requireNonNull(value);
        }

        T get() {
            return value;
        }
    }

    static final class ChildBox<T> extends BaseBox<T> {
        <S> ChildBox(S source, Function<? super S, ? extends T> mapper) {
            super(mapper.apply(source));
        }
    }

    record RecordValue<T>(T value) {
        <S> RecordValue(S source, Function<? super S, ? extends T> mapper) {
            this(mapper.apply(source));
        }
    }

    public static void main(String[] args) {
        Holder<String> direct = new Holder<>("Lan");
        Holder<Integer> converted = new Holder<>("Generics", String::length);
        Holder<String> explicit =
                new <Integer> Holder<String>(42, Object::toString);
        System.out.println("direct: " + direct.get());
        System.out.println("converted: " + converted.get());
        System.out.println("explicit: " + explicit.get());

        DisplayText label = new DisplayText(2026, n -> "Year-" + n);
        System.out.println("display: " + label.value());

        NumericSummary numbers = new NumericSummary(List.of(1, 2, 3));
        System.out.println("sum: " + numbers.total());

        ChildBox<Integer> child =
                new ChildBox<>("Java", String::length);
        System.out.println("child: " + child.get());

        RecordValue<Integer> record =
                new RecordValue<>("Spring", String::length);
        System.out.println("record: " + record.value());
    }
}
```

Lệnh biên dịch và thực thi (trong thư mục chứa tệp):

```bash
javac -Xlint:all GenericConstructorDemo.java
java GenericConstructorDemo
```

Stdout của chương trình:

```text
direct: Lan
converted: 8
explicit: 42
display: Year-2026
sum: 6.0
child: 4
record: 6
```

Giải thích kết quả:

1. `direct` gọi constructor thường `Holder(T)`; `T = String` được chọn từ kiểu `Holder<String>`.
2. `converted` gọi generic constructor `<S> Holder(S, Function<...>)`; `S = String` của đầu vào khác `T = Integer` của đối tượng. `"Generics".length()` bằng `8`.
3. `explicit` chỉ định tường minh `S = Integer` qua `new <Integer> Holder<String>(...)`; `T = String` vẫn thuộc về lớp.
4. `DisplayText` không generic nhưng constructor `<S> DisplayText(...)` suy luận `S = Integer` rồi chuyển thành chuỗi.
5. `NumericSummary` nhận dữ liệu có bound `N extends Number` để cộng qua `doubleValue()`.
6. `ChildBox<T>` dùng `S` riêng trong constructor rồi đưa giá trị `T` cho `super(...)` của lớp cha.
7. `RecordValue<T>` có constructor generic và phải ủy quyền tới canonical constructor bằng `this(...)`.

Các ví dụ lỗi biên dịch trong bài được comment riêng; không phải lời gọi cần chạy trong chương trình hoàn chỉnh.

## 13. Cách lựa chọn giữa constructor thường, generic constructor và factory

| Tình huống | Cách diễn đạt phù hợp | Lý do |
| --- | --- | --- |
| Đối tượng lưu đúng giá trị `T` truyền vào | `class Box<T> { Box(T value) {} }` | Constructor thường, quan hệ kiểu trực tiếp, dễ hiểu |
| Đầu vào là `S` khác kiểu `T` nhưng có converter để tạo `T` | `<S> Box(S source, Function<? super S, ? extends T> f)` | Generic constructor nối kiểu nguồn tạm thời với kiểu kết quả |
| Lớp không generic nhưng dữ liệu nguồn có thể mang nhiều kiểu | `class Label { <S> Label(S source, Function<? super S, String> f) {} }` | `S` chỉ cần khi khởi tạo |
| Muốn diễn đạt thao tác `from`/`parse`/`of` rõ ràng, tránh overload khó hiểu | `static <S,T> Result<T> from(S, Function<...>)` | Static generic factory có tên mô tả ý nghĩa |
| Chỉ nhận `Number` và chuyển về `double` | `<N extends Number> Summary(N value)` | Bound bảo đảm API cần thiết khi chuyển |
| Cần tạo dữ liệu kiểu `S` bên trong | Truyền `Supplier<S>` hoặc factory | Erasure không cho phép `new S()` |
| Dữ liệu đầu vào đã cùng kiểu lưu trữ | Constructor `Box(T)` | Không cần thêm tham số kiểu thứ hai |

Một nguyên tắc thiết kế: đừng khai báo constructor generic chỉ để tránh viết `Object`. Nếu `S` không liên kết các vị trí đầu vào hoặc không được bound/callback sử dụng để mang lại kiểm tra kiểu hữu ích, một constructor nhận kiểu phù hợp hoặc một static factory đơn giản hơn có thể rõ ràng hơn.

## 14. Những lỗi thường gặp

| Sai lầm | Điều xảy ra | Cách sửa |
| --- | --- | --- |
| Nghĩ `Box(T)` là generic constructor vì `Box<T>` generic | Nhầm phạm vi type parameter | Constructor chỉ generic khi **tự khai báo** `<S>` hoặc type parameter tương tự |
| Viết `Box<T>(T input)` làm tên constructor | Không phải cú pháp constructor Java | Khai báo `Box(T input)` |
| Viết `<S> void Box(S input)` và tưởng là constructor | Có kiểu trả về `void`, đây là **method**, không phải constructor | Bỏ kiểu trả về: `<S> Box(S input)` |
| Dùng `<T> Box(T input)` trong `class Box<T>` và nghĩ là cùng `T` | `T` của constructor che khuất `T` của lớp | Dùng `Box(T)` hoặc đặt tham số riêng `<S>` |
| Không phân biệt `new Box<>()` với `<S> Box(...)` | Diamond dành cho type arguments của lớp trong lời gọi, `<S>` khai báo tham số của constructor | Xác định rõ vị trí đặt ký hiệu |
| Gọi `new T()` hoặc `new S()` bên trong constructor | Không có thông tin constructor runtime từ type parameter | Truyền `Supplier<T>` hoặc factory |
| Constructor `<N extends Number>` nhận dữ liệu `String` | Không đạt upper bound | Dùng nguồn thuộc `Number` hoặc đổi API chuyển đổi |
| Overload constructor theo `List<String>` và `List<Integer>` | Name clash sau type erasure | Đổi chữ ký thực sự, dùng factory có tên hoặc một constructor generic |
| Constructor generic dùng raw type và unchecked cast | Có thể làm hỏng type safety của đối tượng sau tạo | Giữ tham số hóa ở cả nguồn và converter; xác thực dữ liệu legacy |
| Constructor ghi trường `final` qua đường không nhất quán | Lỗi definite assignment hoặc lỗi logic | Ủy quyền `this(...)` hoặc gán trường đúng một lần trên mỗi đường |
| Record có constructor bổ sung nhưng không gọi `this(...)` | Vi phạm quy tắc non-canonical record constructor | Dùng `this(converted)` |
| Compiler không suy luận được với quá nhiều overload | Các ràng buộc kiểu không xác định hoặc nhập nhằng | Chỉ định kiểu đích rõ, đổi API, thêm type witness nếu phù hợp |

### Đọc một chữ ký không bị nhầm

Với:

```java
class Result<T> {
    <S extends CharSequence> Result(
            S input,
            Function<? super S, ? extends T> transformer) {
        // ...
    }
}
```

Hãy hỏi:

1. **Kiểu lớp là gì?** `T` sau tên `Result<T>`, được xác định bởi `Result<Integer>`, `Result<String>`...
2. **Kiểu riêng của constructor là gì?** `S` trong `<S extends CharSequence>`.
3. **Bound bảo đảm điều gì?** `S` có các hành vi của `CharSequence`.
4. **Nguồn và đích nối nhau ra sao?** `transformer` nhận `S` và trả một giá trị tương thích `T`.
5. **Dữ liệu tồn tại sau khi constructor kết thúc ở kiểu nào?** Trường của đối tượng sẽ mang hợp đồng `T`; tham số `S` chỉ tham gia quá trình tạo.

## 15. Bài tập thực hành

**Bài 1 — Phân loại constructor.** Cho mã:

```java
class Sample<T> {
    Sample(T input) {}
    <U> Sample(U input, java.util.function.Function<U, T> convert) {}
}
```

Hãy chỉ ra constructor nào generic theo định nghĩa khai báo type parameter riêng. Phân biệt `T` của lớp với `U` của constructor.

**Bài 2 — Lớp không generic.** Thiết kế `CsvText` chỉ giữ một `String`. Tạo generic constructor `<S> CsvText(S source, Function<? super S,String> encoder)`. Dùng nó với `Integer` và với một record `Person(String name)`. Kiểm tra kiểu của nguồn được suy luận từng lời gọi.

**Bài 3 — Bound.** Viết `RangeSummary` có `double min` và `double max`. Constructor generic `<N extends Number> RangeSummary(Collection<N> values)` phải kiểm tra collection không rỗng và dùng `doubleValue()` để tính kết quả. Mô tả hợp đồng đối với `NaN` và `null` nếu bạn muốn hỗ trợ dữ liệu dấu phẩy động.

**Bài 4 — Constructor chaining.** Viết `Box<T>` có constructor `Box(T direct)` và constructor `<S> Box(S source, Function<? super S, ? extends T> mapper)`. Sử dụng `this(mapper.apply(source))`, kiểm tra nguồn chuỗi `"123"` và hàm `Integer::parseInt` để tạo `Box<Integer>`. Thử bỏ converter khi nguồn không phù hợp và quan sát thông báo lỗi biên dịch.

**Bài 5 — Chuyển sang factory.** Chuyển `<S> Box(S source, Function<...>)` thành static factory `static <S,T> Box<T> from(S source, Function<? super S,? extends T> mapper)`. So sánh khả năng đọc của lời gọi `Box.from("123", Integer::parseInt)` với lời gọi `new Box<>("123", Integer::parseInt)`.

**Bài 6 — Giải thích hai chữ `String` và `Integer`.** Phân tích:

```java
Holder<Integer> h =
        new <String> Holder<Integer>("Java", String::length);
```

Chữ `String` sau `new` chỉ type argument của **constructor**; chữ `Integer` sau `Holder` chỉ type argument của **lớp**. Giải thích tại sao `h.get()` trả `Integer`.

Sau khi học, hãy tự kiểm chứng bằng cách tìm **nơi khai báo** type parameter: sau tên lớp là kiểu của **đối tượng**, còn ngay trước tên constructor là kiểu **chỉ thuộc lần khởi tạo**. Khi cùng xuất hiện, hai tham số có thể được ràng buộc với nhau bằng converter nhưng vẫn mang phạm vi riêng biệt. Đây là cơ sở để đọc và xây dựng API constructor generic rõ ràng, an toàn kiểu trong Java.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="generic-methods-008">Thiết kế phương thức tạo đối tượng không phụ thuộc ép kiểu</a>

<details>
<summary>Xem chi tiết</summary>

# Thiết kế phương thức tạo đối tượng không phụ thuộc ép kiểu trong Java Generics

## 1. Mục tiêu và động cơ

Khi một API cần tạo đối tượng nhưng người gọi phải liên tục ép kiểu từ Object sang String, Integer hoặc kiểu nghiệp vụ, đó thường là dấu hiệu quan hệ kiểu giữa dữ liệu đầu vào và kết quả trả về chưa được mô tả rõ. **Generic factory method** (phương thức tạo đối tượng có tham số kiểu) cho phép chuyển quan hệ ấy thành hợp đồng do trình biên dịch kiểm tra.

So sánh cách thiết kế:

~~~java
// Thiết kế dựa vào Object, chỉ minh họa:
static Object oldBox(Object value) {
    return value;
}

// Tại nơi gọi:
String text = (String) oldBox("Java");
~~~

Ép kiểu trong ví dụ này có thể biên dịch nhưng nếu giá trị thực tế không tương thích, lỗi chỉ xuất hiện lúc chạy. Một generic method biểu diễn mối quan hệ “nhận T, trả T” trực tiếp:

~~~java
static <T> T identity(T value) {
    return value;
}

// Tại nơi gọi:
String text = identity("Java");
~~~

Trong thực tế, một factory thường tạo **một đối tượng mới có chứa T**, chẳng hạn Box<T>, Pair<K,V> hoặc một collection, thay vì đơn thuần trả lại đầu vào. Điểm chính là **kiểu kết quả được mô tả từ type parameters và các tham số đầu vào**, không đòi người gọi thực hiện cast thủ công.

Bài này hướng dẫn bạn:

- Viết cú pháp **static <T> Box<T> of(T value)** và phân biệt T của method với T của class.
- Dùng suy luận kiểu (*type inference*) và kiểu đích (*target typing*) để nhận kết quả có kiểu chính xác.
- Tạo các đối tượng generic chứa một hoặc nhiều type parameters bằng constructor và diamond operator.
- Thiết kế factory nhận **Supplier<? extends T>**, **Function<? super S, ? extends T>**, hoặc **Class<T>** khi không có sẵn một giá trị T.
- Phân biệt một API thật sự an toàn kiểu với cast bị giấu sau chữ ký generic.
- Xử lý bound, wildcard, null, bất biến của generics và lỗi runtime mà generics không thể tự ngăn.

Các ví dụ đầy đủ hướng đến Java 17 trở lên, có tên tệp, lệnh biên dịch/chạy và stdout xác định.

## 2. Generic factory method là gì?

Factory method là phương thức tạo hoặc cung cấp đối tượng theo một hợp đồng được công bố. Khi phương thức **tự khai báo một hay nhiều type parameters**, ta có generic factory method.

Ví dụ một class lưu giữ giá trị:

~~~java
final class Box<T> {
    private final T value;

    private Box(T value) {
        this.value = value;
    }

    T value() {
        return value;
    }

    static <U> Box<U> of(U value) {
        return new Box<>(value);
    }
}
~~~

Đọc chữ ký **static <U> Box<U> of(U value)**:

1. **<U>** đứng trước kiểu trả về, khai báo một type parameter **của method of**.
2. **Box<U>** là kiểu kết quả: một Box được tham số hóa bằng U.
3. **U value** cho compiler biết giá trị đầu vào và type argument của Box có quan hệ cùng U.
4. **new Box<>(value)** gọi constructor đã biết của Box, dùng diamond để suy luận type argument. Nó **không** tạo một đối tượng của một lớp chưa biết tên U.
5. Constructor private giới hạn cách tạo Box từ ngoài class, khiến factory là điểm vào được kiểm soát.

Một lời gọi:

~~~java
// Minh họa bên trong phương thức:
Box<String> message = Box.of("hello");
Box<Integer> quantity = Box.of(12);
~~~

Compiler suy luận U từ các đối số và ngữ cảnh: message là Box<String>, quantity là Box<Integer>. **Không cần (Box<String>)** ở chỗ nhận kết quả.

Một chi tiết cú pháp: T là tham số kiểu của **class Box<T>**, trong khi U là tham số kiểu riêng của static method of. Static method không được dùng trực tiếp T thuộc class:

~~~java
// Sai trong một generic class:
static Box<T> of(T value) { ... }
~~~

Nếu T chỉ được khai báo bởi Box<T>, static method phải tự khai báo type parameter, như **static <U> Box<U> of(U value)**. Bạn có thể dùng lại tên T ở static method nếu muốn (**static <T> Box<T> of(T value)**); đó vẫn là T **riêng của method**, không phải T của class.

## 3. Tại sao cách trả Object rồi cast lại nguy hiểm?

Một API:

~~~java
static Object createValue(String text) {
    return text;
}
~~~

chỉ cam kết kết quả là Object. Caller phải viết **(String) createValue("x")**. Nếu implementation về sau trả Integer mà vẫn hợp chữ ký Object, cast ở caller có thể ném ClassCastException.

Một sai lầm tinh vi hơn là “ngụy trang” cast trong generic method:

~~~java
@SuppressWarnings("unchecked")
static <T> T unsafeCreate() {
    return (T) "actually a String";
}
~~~

Chữ ký trông generic, nhưng **không có đầu vào, bound hoặc factory chứng minh kết quả thuộc T**. Lời gọi **Integer number = unsafeCreate()** có thể biên dịch, rồi thất bại khi giá trị String được kiểm tra như Integer tại runtime. Phương thức đã chuyển một rủi ro unchecked từ caller vào implementation, **không tạo ra an toàn kiểu**.

Nguyên tắc thiết kế: một generic factory trả T hoặc một kiểu chứa T cần có **nguồn đảm bảo T**. Nguồn đó có thể là một tham số đã có kiểu T, một Supplier<? extends T>, một phép biến đổi Function<S,T>, một constructor đã biết của lớp generic chứa T, hoặc một Class<T> kết hợp kiểm tra/khởi tạo runtime thích hợp.

**Không thể suy ra và tạo một instance bất kỳ của T chỉ từ chữ ký <T> T create()**. Type parameter là ràng buộc ở source code, không phải một đối tượng Class mà JVM tự cung cấp cho phương thức.

## 4. Cú pháp chính xác và vai trò của type inference

Mẫu phổ biến:

~~~java
static <T> Box<T> box(T value) {
    return new Box<>(value);
}

static <K, V> Pair<K, V> pair(K key, V value) {
    return new Pair<>(key, value);
}
~~~

Những khai báo trên giả định Box<T> và Pair<K,V> đã được định nghĩa với constructor tương ứng. Chúng không phải một chương trình độc lập; chương trình hoàn chỉnh nằm ở mục tiếp theo.

Giải thích các vị trí dấu ngoặc góc:

| Biểu thức | Vai trò |
| --- | --- |
| **<T>** trước **Box<T>** | Khai báo type parameter cho method |
| **Box<T>** làm kiểu trả về | Dùng T làm type argument của Box |
| **T value** | Dùng type variable T làm kiểu của tham số giá trị |
| **new Box<>(value)** | Constructor invocation với diamond, suy luận đối số kiểu |
| **Box<String> b = box("x")** | Caller nhận một parameterized type với String |
| **Factory.<String>box("x")** | Caller chỉ định tường minh method type argument String |

Compiler xem chữ ký method, các đối số, bound và kiểu đích để suy luận T. Với **pair("lang", 21)**, kiểu suy luận thông thường là Pair<String, Integer>. Phép boxing chuyển literal int sang Integer khi cần type argument tham chiếu.

Cần phân biệt **generic factory method** và **constructor**. Constructor tên giống class, không khai báo kiểu trả về; static factory có tên riêng, một kiểu trả về và có thể chứa logic validate, cache, chọn implementation hoặc chuyển đổi trước khi trả đối tượng.

Không phải factory nào cũng cần generic: nếu một phương thức luôn tạo StringBuilder không mang quan hệ kiểu với input, **static StringBuilder createBuilder()** đơn giản đã đủ.

## 5. Ví dụ chạy độc lập: factory cho Box<T> và Pair<K,V>

Lưu tệp **GenericFactoryDemo.java**:

~~~java
public class GenericFactoryDemo {
    record Box<T>(T value) {}

    record Pair<K, V>(K key, V value) {}

    static <T> Box<T> box(T value) {
        return new Box<>(value);
    }

    static <K, V> Pair<K, V> pair(K key, V value) {
        return new Pair<>(key, value);
    }

    public static void main(String[] args) {
        Box<String> language = box("Java");
        Box<Integer> version = box(21);
        Pair<String, Integer> release = pair("jdk", 17);

        Box<Number> number = GenericFactoryDemo.<Number>box(3);

        System.out.println(language.value());
        System.out.println(version.value());
        System.out.println(release.key() + ":" + release.value());
        System.out.println(number.value());
    }
}
~~~

Biên dịch và chạy bằng JDK 17+:

~~~bash
javac -encoding UTF-8 GenericFactoryDemo.java
java GenericFactoryDemo
~~~

Stdout chính xác:

~~~text
Java
21
jdk:17
3
~~~

Giải thích:

- **record Box<T>** là một generic record. Factory box(T) tạo Box<T> mà không cần cast.
- **pair(K,V)** kết nối chính xác hai type arguments độc lập vào Pair<K,V>; type arguments của result không bị ép thành Object.
- **Box<String> language** khiến chương trình gọi value() nhận String tĩnh; **Box<Integer> version** nhận Integer.
- **GenericFactoryDemo.<Number>box(3)** cung cấp **explicit type witness**: T được chọn là Number, 3 được boxing thành Integer và gán cho Number. Dữ liệu thực tế bên trong vẫn là một Integer, nhưng kiểu result được công bố là Box<Number>.
- Nếu bỏ type witness, **Box<Number> number = box(3)** cũng là một ví dụ mà compiler có thể suy luận kiểu phù hợp nhờ ngữ cảnh gán. Type witness có ích khi muốn chỉ rõ một lựa chọn kiểu mà không dựa hoàn toàn vào suy luận.

Tại đây, tên record và method chỉ phục vụ ví dụ. Một API có thể chọn những tên dễ hiểu hơn như **of**, **from**, **create**, **empty**, **copyOf** hoặc **parse**, tùy ngữ nghĩa từng factory.

## 6. Dùng phương thức tĩnh để tạo một instance của generic class

Một cách thường dùng là đặt method factory ngay trong class:

~~~java
final class Envelope<T> {
    private final T content;

    private Envelope(T content) {
        this.content = content;
    }

    public T content() {
        return content;
    }

    public static <U> Envelope<U> of(U content) {
        return new Envelope<>(content);
    }
}
~~~

Caller có thể viết:

~~~java
// Minh họa trong thân phương thức:
Envelope<String> text = Envelope.of("hello");
Envelope<Integer> count = Envelope.of(8);
~~~

Đây là **static generic factory method**. Nó tránh cần gọi **new Envelope<String>(...)** trực tiếp, giữ nơi khởi tạo tập trung, đồng thời có thể kiểm tra dữ liệu:

~~~java
public static <U> Envelope<U> nonNull(U content) {
    return new Envelope<>(java.util.Objects.requireNonNull(content, "content"));
}
~~~

Phiên bản nonNull chỉ khác về hợp đồng null. Nó vẫn không cast hoặc đánh mất U. **Objects.requireNonNull** có overload generic giữ nguyên kiểu tĩnh của tham số.

Nếu muốn cho phép null, factory **ofNullable** có thể được đặt tên và thiết kế riêng; tuy nhiên không được suy ra chỉ từ tên rằng kết quả của mọi factory là Optional<T>. Một factory trả chính Envelope<U> thì tên, return type và hợp đồng phải nhất quán.

Khi constructor cần kiểm soát chặt tính hợp lệ hoặc cách chọn implementation, static factory thường hữu ích hơn chỉ expose constructor. Tuy vậy, factory không tự làm mọi object bất biến hoặc thread-safe; những đặc tính đó phụ thuộc cấu trúc đối tượng, field và implementation.

## 7. Nhiều tham số kiểu: từ dữ liệu đầu vào đến đối tượng kết quả

Generic factory có thể khai báo nhiều type parameters để biểu diễn nhiều vai trò:

~~~java
record Entry<K, V>(K key, V value) {}

static <K, V> Entry<K, V> entry(K key, V value) {
    return new Entry<>(key, value);
}
~~~

Caller:

~~~java
// Minh họa:
Entry<String, Integer> e = entry("age", 20);
Entry<Long, String> other = entry(5L, "value");
~~~

K và V được suy luận riêng. Với e, K là String và V là Integer; với other, K là Long, V là String. Không cần cast ở phương thức trả về hay caller.

Có thể sử dụng type parameters đầu vào/đầu ra:

~~~java
static <I, O> O convert(I input,
                         java.util.function.Function<? super I, ? extends O> mapper) {
    return mapper.apply(input);
}
~~~

**Function<? super I, ? extends O>** cho phép bộ biến đổi nhận được I hoặc một kiểu rộng hơn I, và trả được O hoặc một subtype của O. Đây là dạng variance hữu ích để factory/chuyển đổi không ép caller viết lambda chỉ đúng một kiểu tuyệt đối.

Trong một lời gọi:

~~~java
// Minh họa trong phương thức:
Integer length = convert("Java", String::length);
~~~

Compiler suy luận I = String, O phù hợp với Integer; String::length trả int và được boxing thành Integer trong Function.

Khi thiết kế, hãy tự hỏi **mỗi type parameter xuất hiện tại đâu**. Nếu method tự khai báo <T> nhưng T không kết nối được với bất kỳ tham số đầu vào, kiểu đích hay nguồn tạo thực tế, đó là dấu hiệu chữ ký có thể tạo cảm giác an toàn giả.

## 8. Dùng Supplier khi factory phải tự tạo T mà không có sẵn giá trị T

Có những lúc caller muốn method tạo một đối tượng mới thay vì đóng gói đối tượng có sẵn. Java không cho phép viết **new T()** với type variable T vì thông tin constructor cụ thể không thể suy ra từ T tại runtime. Thay vào đó, caller cung cấp một **Supplier** tạo object.

~~~java
static <T> T create(java.util.function.Supplier<? extends T> supplier) {
    return supplier.get();
}
~~~

Từ bên trong, compiler biết **supplier.get()** trả một kiểu là T hoặc subtype của T nên gán được cho T. Người dùng có thể truyền constructor reference:

~~~java
// Minh họa:
StringBuilder builder = create(StringBuilder::new);
~~~

Không có cast. Static type của kết quả được xác lập qua Supplier và ngữ cảnh kiểu đích. Cách tiếp cận này cũng tránh phải yêu cầu mọi T có public no-arg constructor.

Bạn có thể thêm các điều kiện runtime:

~~~java
static <T> T createNonNull(
        java.util.function.Supplier<? extends T> supplier) {
    java.util.Objects.requireNonNull(supplier, "supplier");
    return java.util.Objects.requireNonNull(supplier.get(), "created object");
}
~~~

Factory ở đây bảo đảm khác null **sau khi hàm thành công**; nó không bảo đảm constructor reference/lambda không ném exception, không tạo object mới cho mỗi lần gọi, hoặc không có tác dụng phụ. Những điều đó phụ thuộc Supplier do caller cung cấp.

## 9. Ví dụ chạy độc lập: Supplier và Function

Lưu thành **SupplierFactoryDemo.java**:

~~~java
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

public class SupplierFactoryDemo {
    interface Animal {
        String sound();
    }

    static final class Dog implements Animal {
        @Override
        public String sound() {
            return "woof";
        }
    }

    static <T> T create(Supplier<? extends T> supplier) {
        Objects.requireNonNull(supplier, "supplier");
        return Objects.requireNonNull(supplier.get(), "result");
    }

    static <T> List<T> createMany(int count, Supplier<? extends T> supplier) {
        if (count < 0) {
            throw new IllegalArgumentException("Negative count");
        }
        List<T> result = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            result.add(create(supplier));
        }
        return result;
    }

    static <S, T> T convert(S source,
                            Function<? super S, ? extends T> mapper) {
        return Objects.requireNonNull(mapper, "mapper").apply(source);
    }

    public static void main(String[] args) {
        Animal first = create(Dog::new);
        List<Animal> group = createMany(2, Dog::new);
        Integer length = convert("Java", String::length);

        System.out.println(first.sound());
        System.out.println(group.size());
        System.out.println(group.get(0).sound());
        System.out.println(length);
    }
}
~~~

Chạy:

~~~bash
javac -encoding UTF-8 SupplierFactoryDemo.java
java SupplierFactoryDemo
~~~

Stdout:

~~~text
woof
2
woof
4
~~~

Những điểm cần quan sát:

1. **Dog::new** là constructor reference; nó cung cấp cách tạo Dog. Method create không biết hoặc yêu cầu new T().
2. **Animal first** được gán từ một factory dùng Supplier<Dog>. Generic bound **? extends T** cho phép kết quả là subtype của kiểu đích.
3. **List<Animal> group = createMany(2, Dog::new)** giữ kiểu phần tử công bố là Animal dù object được tạo là Dog.
4. **convert("Java", String::length)** gắn kiểu nguồn String và kiểu kết quả Integer; không cần **(Integer)** hoặc **(String)** tại chỗ chuyển đổi.
5. createMany() từ chối số lượng âm; phần kiểm tra này thuộc contract factory, không do generics quyết định.
6. Supplier có thể trả null; create kiểm tra và từ chối trường hợp đó. Function trong convert thì không kiểm tra giá trị kết quả là null, nên method chỉ cam kết về kiểu tĩnh chứ không mặc nhiên cam kết non-null.

Để có một sản phẩm API hoàn chỉnh, cần xác định rõ factory tạo mới mỗi lần hay được phép tái sử dụng, null được chấp nhận hay không, và checked/unchecked exceptions có thể thoát ra theo đường nào.

## 10. Dùng Class<T> khi cần tạo hoặc kiểm tra kiểu tại runtime

Có tình huống cần biết class cụ thể để khởi tạo hoặc xác minh một Object. **Class<T>** là một *type token* có quan hệ generic với T.

Trường hợp cần kiểm tra một Object có đúng kiểu mong muốn:

~~~java
static <T> T checked(Object source, Class<T> type) {
    return type.cast(source);
}
~~~

**Class.cast** thực hiện kiểm tra cast ở runtime dựa trên class token. Caller:

~~~java
// Minh họa:
String label = checked("Java", String.class);
Integer answer = checked(42, Integer.class);
~~~

Ở đây không có cast tại caller và cũng không có unchecked cast trong method. Nếu source không là instance tương thích, **type.cast(source)** ném ClassCastException. Lưu ý **Class.cast(null)** trả null; nó không phải kiểm tra non-null.

Một dạng cần tạo đối tượng qua reflection:

~~~java
static <T> T construct(Class<T> type)
        throws ReflectiveOperationException {
    return type.getDeclaredConstructor().newInstance();
}
~~~

Phương thức dùng Class<T> để tìm constructor không tham số và gọi nó. Nó chỉ hoạt động nếu constructor phù hợp có thể truy cập trong bối cảnh runtime; không phải mọi T đều có no-arg constructor. Reflection có thể ném các exception liên quan đến constructor, truy cập hoặc khởi tạo. Trường hợp đơn giản có constructor cụ thể, **Supplier<T>** thường rõ ràng và dễ kiểm thử hơn reflection.

**Class<T> không khôi phục đầy đủ type arguments lồng nhau**: ở runtime, **List<String>** và **List<Integer>** có thể là cùng class ArrayList. Không có class literal **List<String>.class**. Nếu cần mô tả type phức tạp ở runtime, phải dùng cơ chế type token/Type chuyên dụng và quy trình kiểm tra phù hợp, nằm ngoài bảo đảm đơn thuần của Class<T>.

## 11. Ví dụ chạy độc lập: type token và tạo đối tượng không cast

Lưu thành **ClassTokenFactoryDemo.java**:

~~~java
public class ClassTokenFactoryDemo {
    static <T> T checked(Object value, Class<T> type) {
        return type.cast(value);
    }

    static <T> T construct(Class<T> type)
            throws ReflectiveOperationException {
        return type.getDeclaredConstructor().newInstance();
    }

    public static void main(String[] args)
            throws ReflectiveOperationException {
        String language = checked("Java", String.class);
        System.out.println(language);

        try {
            Integer invalid = checked("not an integer", Integer.class);
            System.out.println(invalid);
        } catch (ClassCastException exception) {
            System.out.println("type mismatch");
        }

        StringBuilder builder = construct(StringBuilder.class);
        builder.append("created");
        System.out.println(builder);
    }
}
~~~

Lệnh biên dịch/chạy:

~~~bash
javac -encoding UTF-8 ClassTokenFactoryDemo.java
java ClassTokenFactoryDemo
~~~

Stdout:

~~~text
Java
type mismatch
created
~~~

Giải thích:

- **checked("Java", String.class)** trả String; quan hệ giữa Class<String> và return type T được compiler kiểm tra.
- **checked("not an integer", Integer.class)** biên dịch nhưng Class.cast phát hiện type mismatch lúc chạy. Chương trình bắt ClassCastException và in một thông báo cố định.
- **construct(StringBuilder.class)** hoạt động vì StringBuilder có constructor không tham số thích hợp; return type StringBuilder được suy ra từ Class<StringBuilder>.
- Reflection có thể không phù hợp nếu class abstract, interface, không có no-arg constructor, constructor không thể truy cập, hoặc constructor ném exception. Đây không phải factory tạo “mọi T” một cách thần kỳ.

Type token hữu ích khi dữ liệu đầu vào ban đầu chỉ có kiểu Object (ví dụ hệ thống plugin, parser runtime, tích hợp reflection) và cần kiểm tra an toàn. Với dữ liệu đã có generic type đầy đủ ở compile time, nên ưu tiên giữ quan hệ kiểu từ đầu hơn là chuyển thành Object rồi kiểm tra lại.

## 12. Phân biệt factory trả T và factory trả Container<T>

Hai chữ ký trông giống nhau nhưng có khả năng khác nhau:

~~~java
static <T> T create(Supplier<? extends T> supplier) {
    return supplier.get();
}

static <T> Box<T> wrap(T value) {
    return new Box<>(value);
}
~~~

**create** yêu cầu có một nguồn tạo ra chính T (Supplier). Nó không thể tự new T() chỉ từ chữ ký. **wrap** có thể dùng new Box<>(...) vì class Box là một kiểu đã được biết, có constructor tương ứng; T chỉ quyết định *kiểu phần tử bên trong Box*.

Một sai lầm dễ gặp:

~~~java
// Cố ý sai, không biên dịch:
static <T> T impossible() {
    // return new T();
    return null;
}
~~~

Dòng return null sẽ biên dịch, nhưng không phải một factory hữu ích và không tạo đối tượng. Phần **new T()** bị comment vì Java không cho phép khởi tạo type variable trực tiếp. Câu hỏi thiết kế thực sự là *nguồn nào bảo đảm giá trị trả về đúng T?*

Generic factory cũng có thể trả interface của một implementation cụ thể:

~~~java
interface Repository<ID, E> {
    E find(ID id);
}

class MapRepository<ID, E> implements Repository<ID, E> {
    private final java.util.Map<ID, E> values;

    MapRepository(java.util.Map<ID, E> values) {
        this.values = values;
    }

    @Override
    public E find(ID id) {
        return values.get(id);
    }
}

class RepositoryFactory {
    static <ID, E> Repository<ID, E> fromMap(
            java.util.Map<ID, E> data) {
        return new MapRepository<>(data);
    }
}
~~~

Factory công bố **Repository<ID,E>** thay vì class cụ thể **MapRepository<ID,E>**. Caller có thể lập trình theo interface với đúng type arguments, không cần cast giữa implementation và interface. Lưu ý Map.get có thể trả null khi không thấy key, nên quan hệ kiểu an toàn không đồng nghĩa với non-null.

## 13. Target typing và gọi method bằng đối số kiểu tường minh

Thông thường compiler suy luận type arguments từ method arguments và kiểu đích:

~~~java
static <T> java.util.List<T> one(T element) {
    return java.util.List.of(element);
}
~~~

Trong một phương thức:

~~~java
java.util.List<String> names = one("An");
java.util.List<Number> amounts = one(3);
~~~

Compiler có thể dùng cả kiểu của expression đầu vào và kiểu đích List<Number> để chọn T phù hợp. Điều này không có nghĩa runtime đã thay đổi kiểu thực của số 3: boxing tạo Integer và value đó có thể được xem như Number.

Khi muốn thể hiện type argument rõ ràng, dùng **explicit method type argument** sau dấu chấm:

~~~java
java.util.List<Number> amounts = SomeFactories.<Number>one(3);
~~~

Đoạn trên là minh họa với giả định method **one** có thể truy cập trong class SomeFactories. Cách ghi **<Number>** không khai báo tham số mới; nó chỉ chọn T tại điểm gọi.

### Khi dùng var

~~~java
var words = java.util.List.of("A", "B");
var numbers = java.util.List.of(1, 2);
~~~

Compiler suy luận kiểu tĩnh từ initializer. Trong ví dụ này, words là List<String>, numbers là List<Integer>. Không vì dùng var mà bỏ qua generic type safety.

Với factory không có tham số cung cấp kiểu, cần chú ý ngữ cảnh:

~~~java
static <T> java.util.List<T> emptyMutableList() {
    return new java.util.ArrayList<>();
}
~~~

Các cách dùng:

~~~java
java.util.List<String> names = emptyMutableList();
var unknown = emptyMutableList();
~~~

Ở lần đầu, kiểu đích giúp suy luận T là String. Ở lần dùng var, nếu không có ràng buộc đầu vào/đích khác, compiler thường suy luận upper bound Object cho T, nên unknown có kiểu List<Object>. Đây là lý do target typing có thể làm khác kết quả suy luận; API cần được đọc cùng ngữ cảnh.

Đừng cố khắc phục một API có quan hệ kiểu sai bằng cast: nếu caller phải ép **(List<String>)**, hãy kiểm tra trước liệu chữ ký generic đã cung cấp đúng liên hệ giữa đầu vào và đầu ra hay chưa.

## 14. Factory tạo collection: giữ kiểu phần tử và xử lý variance

Factory sao chép collection là ví dụ thực tế của method dùng type parameter T và wildcard.

~~~java
static <T> java.util.List<T> mutableCopy(
        java.util.Collection<? extends T> source) {
    return new java.util.ArrayList<>(source);
}
~~~

**Collection<? extends T>** cho phép đọc các phần tử thuộc T hoặc subtype của T. Ví dụ source là List<Integer>, caller có thể yêu cầu kết quả List<Number>. **ArrayList<>(source)** có constructor nhận collection tương thích và tạo một danh sách mới.

Nếu method nhận **Collection<T>** thay cho **Collection<? extends T>**, nó có thể kém linh hoạt khi muốn chuyển các phần tử subtype thành collection của supertype. Mặc dù Integer extends Number, **List<Integer> không phải List<Number>** do invariant generics.

Đối với một bản sao không cho phép sửa cấu trúc, có thể dùng **List.copyOf**:

~~~java
static <T> java.util.List<T> immutableCopy(
        java.util.Collection<? extends T> source) {
    return java.util.List.copyOf(source);
}
~~~

**List.copyOf** tạo hoặc tái sử dụng một unmodifiable list phù hợp; nó từ chối phần tử null. Nếu cần bảo đảm snapshot độc lập với sự thay đổi của nguồn, hãy xem hợp đồng cụ thể của factory và đối tượng nguồn, thay vì chỉ suy đoán từ tên "copy". Unmodifiable không có nghĩa các object phần tử bên trong trở nên immutable.

## 15. Ví dụ chạy độc lập: copy collection an toàn kiểu

Lưu thành **CollectionFactoryDemo.java**:

~~~java
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class CollectionFactoryDemo {
    static <T> List<T> mutableCopy(Collection<? extends T> source) {
        return new ArrayList<>(source);
    }

    static <T> List<T> immutableCopy(Collection<? extends T> source) {
        return List.copyOf(source);
    }

    public static void main(String[] args) {
        List<Integer> source = List.of(1, 2);
        List<Number> numbers = mutableCopy(source);
        numbers.add(3.5);

        List<Number> fixed = immutableCopy(source);

        System.out.println(numbers);
        System.out.println(fixed);
        System.out.println(source);
    }
}
~~~

Biên dịch/chạy:

~~~bash
javac -encoding UTF-8 CollectionFactoryDemo.java
java CollectionFactoryDemo
~~~

Stdout:

~~~text
[1, 2, 3.5]
[1, 2]
[1, 2]
~~~

**Điểm cần kiểm tra:** source có kiểu List<Integer> nhưng cả mutableCopy và immutableCopy có thể trả List<Number> nhờ chữ ký generic và ngữ cảnh gán. Không có cast không kiểm tra. **numbers.add(3.5)** hợp lệ vì numbers được công bố là List<Number>; source vẫn chỉ chứa 1, 2. Danh sách fixed do List.copyOf tạo không cho thêm phần tử, dù nó cũng có kiểu tĩnh List<Number>.

### Null và collection factory

**new ArrayList<>(source)** chấp nhận phần tử null nếu collection nguồn chứa null; nhưng **List.copyOf(source)** ném NullPointerException khi nguồn null hoặc có phần tử null. Đừng giả định mọi factory generic có cùng chính sách null. Tên method, hợp đồng và tài liệu API cần nêu rõ.

**Collections.emptyList()** và **List.of()** là các ví dụ factory generic tiêu chuẩn trả collection rỗng. Chúng có thể suy luận kiểu phần tử từ kiểu đích:

~~~java
java.util.List<String> emptyA = java.util.Collections.emptyList();
java.util.List<Integer> emptyB = java.util.List.of();
~~~

Cả hai hợp lệ mà không cần cast. Tuy nhiên, list do List.of trả về không cho sửa cấu trúc, nên phải chọn factory theo yêu cầu mutability.

## 16. Factory có bounds: giới hạn kiểu đầu vào đúng nhu cầu

Một factory đôi khi cần API cụ thể trên T. Nếu method chỉ chuyển một giá trị T vào Box<T>, thường không cần bound. Nếu cần dùng Number.doubleValue() hoặc Comparable.compareTo(), khai báo bound:

~~~java
record Measured<T extends Number>(T original, double amount) {}

static <T extends Number> Measured<T> measured(T number) {
    return new Measured<>(number, number.doubleValue());
}
~~~

Với **measured(12)**, T được suy luận Integer và result là Measured<Integer>. **measured("12")** không hợp lệ vì String không phải subtype của Number.

Nhiều bounds:

~~~java
static <T extends Number & Comparable<T>> T greater(T left, T right) {
    return left.compareTo(right) >= 0 ? left : right;
}
~~~

Dấu **&** nối class bound Number với interface bound Comparable<T>; compiler cho phép gọi compareTo, điều mà unbounded T không bảo đảm.

Khi method muốn so sánh theo nhiều chiến lược, có thể **nhận Comparator<? super T>** thay vì buộc T implements Comparable<T>:

~~~java
static <T> T choose(
        T left, T right, java.util.Comparator<? super T> comparator) {
    return comparator.compare(left, right) >= 0 ? left : right;
}
~~~

Comparator cung cấp logic so sánh qua một đối số rõ ràng. Điều này giúp factory/selection method hoạt động với kiểu không có natural ordering. Nhờ type parameter T, kết quả vẫn giữ liên hệ với kiểu các đối số được chọn, không cần cast.

### Bounds trong factory trả interface

Một factory có thể che implementation nhưng vẫn giữ bounds:

~~~java
interface SortedHolder<T> {
    T greatest();
}

class SingleSortedHolder<T extends Comparable<? super T>>
        implements SortedHolder<T> {
    private final T value;

    SingleSortedHolder(T value) {
        this.value = value;
    }

    @Override
    public T greatest() {
        return value;
    }
}

class SortedHolders {
    static <T extends Comparable<? super T>> SortedHolder<T> of(T value) {
        return new SingleSortedHolder<>(value);
    }
}
~~~

Bound của method đủ để constructor/class implementation có thể được áp dụng đúng T. Trả **SortedHolder<T>** giúp caller phụ thuộc vào hợp đồng interface. Với giá trị null, code vẫn có thể tạo holder nếu constructor cho phép; Comparable bound không tự cấm null.

## 17. API thiết kế đúng không có unchecked cast

Một phương thức generic giữ quan hệ kiểu tốt nhất khi **phần thân cũng có bằng chứng kiểu** từ compiler. Các ví dụ:

~~~java
static <T> java.util.Optional<T> optional(T value) {
    return java.util.Optional.ofNullable(value);
}

static <T> java.util.List<T> singleton(T value) {
    return java.util.List.of(value);
}

static <K, V> java.util.Map<K, V> singletonMap(K key, V value) {
    return java.util.Map.of(key, value);
}
~~~

Các phương thức đều không có **(T)** hoặc **@SuppressWarnings("unchecked")**. Compiler biết:

- **Optional.ofNullable** trả Optional<T> theo giá trị đầu vào; nhận null thì trả Optional.empty().
- **List.of(value)** trả một danh sách một phần tử với type argument tương ứng, nhưng **không chấp nhận null**.
- **Map.of(key, value)** trả map bất biến về cấu trúc với type arguments K,V; key/value null bị từ chối.

Điều quan trọng là không phải factory nào cũng chấp nhận null như nhau. Cần nêu rõ contract ngay khi đặt tên và chọn implementation.

Phương thức sau vẫn thiếu bằng chứng kiểu:

~~~java
// Chống chỉ định; chỉ minh họa:
@SuppressWarnings("unchecked")
static <T> java.util.List<T> unsoundList() {
    return (java.util.List<T>) (java.util.List<?>) java.util.List.of("A");
}
~~~

Nếu caller gán sang List<Integer>, dữ liệu String bên trong có thể gây ClassCastException khi lấy phần tử dưới kiểu Integer. Phương thức đã giấu unchecked cast, không thực hiện chuyển đổi String thành Integer.

Một cast có kiểm tra runtime như **Class<T>.cast** là trường hợp khác: nó dùng class token để xác minh tính tương thích tại runtime và ném exception nếu không hợp lệ. Tuy nhiên Class<T> không đủ để kiểm tra mọi parameterized type lồng nhau do erasure.

## 18. Một ví dụ nghiệp vụ: factory tạo response có kiểu

Giả sử ứng dụng cần gói dữ liệu và trạng thái thành một kết quả chung. Một generic record là lựa chọn cô đọng:

~~~java
record Response<T>(boolean success, T data, String message) {}

class Responses {
    static <T> Response<T> success(T data) {
        return new Response<>(true, data, "OK");
    }

    static <T> Response<T> failure(String message) {
        return new Response<>(false, null, message);
    }
}
~~~

Với success(data), compiler suy luận T từ data. Với failure(message), không có đầu vào T nên **kiểu đích** hoặc type witness có vai trò quan trọng:

~~~java
// Minh họa trong phương thức:
Response<String> one = Responses.success("saved");
Response<String> two = Responses.failure("not found");
Response<Integer> three = Responses.<Integer>failure("invalid");
~~~

Chữ ký **failure(String message)** có thể trả Response<T> với data = null, vì null gán được cho kiểu tham chiếu T. Nó **không tự tạo một T**, và contract cần nêu rõ dữ liệu vắng mặt khi success = false. API như vậy thể hiện quan hệ kiểu ở kết quả mà không cần caller ép cast.

Một vấn đề thiết kế là cho phép **success(null)** không. Nếu success có ý nghĩa “có dữ liệu”, có thể dùng Objects.requireNonNull(data) khi tạo Response. Nếu null là trạng thái hợp lệ, cần biểu diễn rõ điều kiện và tránh đồng nhất null với lỗi. Generics không quyết định chính sách nghiệp vụ này.

## 19. Suy luận kiểu không thay thế kiểm tra thiết kế API

Một factory có thể biên dịch nhưng vẫn gây hiểu nhầm:

~~~java
static <T> T fallback(T preferred, T alternative) {
    return preferred != null ? preferred : alternative;
}
~~~

Phương thức có type parameter T liên hệ hai đối số với kiểu trả về. Nhưng caller có thể truyền **String** và **Integer**, khiến compiler suy luận một supertype chung. Khi đó kiểu kết quả không nhất thiết là kiểu cụ thể mà người viết mong đợi.

Ví dụ an toàn:

~~~java
String name = fallback(null, "anonymous");
~~~

Ví dụ cần xem lại ý đồ:

~~~java
Object mixed = fallback("text", 123);
~~~

Java có thể chọn một kiểu chung phù hợp để kết quả gán được vào Object. Đây không phải vi phạm an toàn kiểu; nó chỉ cho thấy **generic method không bảo đảm hai tham số runtime có cùng class**. Nếu nghiệp vụ yêu cầu cùng loại chặt chẽ hơn, cần thiết kế hợp đồng/đầu vào phù hợp và có thể thêm kiểm tra ở ranh giới.

Cũng không nên mặc định rằng viết **var result = factory(...)** luôn cho kiểu mong muốn. Kiểu suy luận phụ thuộc vào initializer; ở những factory chỉ có return-type T mà không có thông tin đầu vào, kiểu đích tường minh có thể làm kết quả rõ ràng hơn.

## 20. Null, exception và mutability trong phương thức tạo đối tượng

**An toàn kiểu** và **chính sách runtime** là hai chiều độc lập. Một generic factory có thể trả đúng Box<String> nhưng vẫn chứa null; một List<Integer> được tạo đúng kiểu có thể không cho phép add; Supplier<T> có thể ném exception.

Nên quyết định rõ những hợp đồng sau khi thiết kế:

| Câu hỏi | Hướng xử lý |
| --- | --- |
| Đầu vào null có hợp lệ? | Dùng requireNonNull hoặc cho phép và ghi rõ |
| Factory có thể trả null? | Cân nhắc tránh null hoặc công bố rõ; Optional khi phù hợp |
| Tạo object mới mỗi lần? | Mô tả nếu là yêu cầu; factory có thể cache hoặc tái sử dụng |
| Constructor có tác dụng phụ? | Supplier và reflection có thể chạy logic constructor |
| Kết quả có mutable? | Xác định qua kiểu/implementation, không qua T |
| Có lỗi checked khi reflection? | Khai báo/thực thi xử lý ReflectiveOperationException phù hợp |
| Có cần kiểm tra kiểu runtime? | Class<T>.cast khi kiểm tra class cụ thể; hiểu giới hạn erasure |
| Kiểu nào được trả ra? | Chữ ký nên giữ type parameters có quan hệ với input/result |

Ví dụ API nhận Supplier nhưng muốn cấm kết quả null:

~~~java
static <T> T newRequired(java.util.function.Supplier<? extends T> factory) {
    java.util.Objects.requireNonNull(factory, "factory");
    return java.util.Objects.requireNonNull(factory.get(), "factory returned null");
}
~~~

Nếu factory.get() tạo một object hợp lệ, return type giữ T; nếu trả null, method ném NPE với thông báo đã khai báo. Đây là một bảo đảm do code thực thi, không phải tính chất tự phát của cú pháp <T>.

Với Optional.ofNullable, null được mã hóa thành Optional.empty() thay vì throw. Hai cách có contract khác nhau; không có một quy tắc áp dụng cho mọi factory.

## 21. Cách đọc và tự xây dựng một generic factory method

Khi gặp yêu cầu “tạo object nhưng không ép kiểu”, hãy làm theo trình tự:

1. **Xác định kết quả cần tạo.** Đó là chính T, một Box<T>, một List<T>, Pair<K,V>, một interface Repository<K,V> hay một response có kiểu?
2. **Tìm nguồn đảm bảo type arguments.** Giá trị đầu vào T, Supplier<T>, Function<S,T>, collection nguồn, type token Class<T>, hoặc implementation có sẵn?
3. **Khai báo type parameters đúng vị trí.** Với static method: **static <T> ReturnType method(...)**. Nếu nhiều quan hệ kiểu độc lập, khai báo <K,V> hoặc <S,T>.
4. **Gắn type variables vào chữ ký đầu vào và kết quả.** Ví dụ <T> Box<T> of(T value) hay <S,T> T map(S source, Function<? super S, ? extends T> mapper).
5. **Viết implementation mà compiler tự chứng minh được kiểu.** Dùng new Box<>(value), supplier.get(), function.apply(), Class.cast... Tránh unchecked cast hay raw type.
6. **Thử lời gọi qua kiểu đích cụ thể.** Gán sang Box<String>, Box<Integer>, List<Number>... và xem compiler kiểm tra được không.
7. **Quy định chính sách runtime.** Null, mutability, số lượng object được tạo, exception và giới hạn reflection.
8. **Chỉ rõ đối số kiểu khi cần.** Dùng TypeName.<T>method(...) hoặc biến đích tường minh nếu suy luận không biểu đạt ý định.

Một chữ ký nên khiến người đọc nhận ra điều gì được bảo đảm. Ví dụ **<T> Box<T> of(T value)** nói trực tiếp rằng Box mang cùng kiểu với value. **<T> T create()** không có nguồn T chỉ nên xuất hiện khi implementation có một cơ chế thực sự bảo đảm kiểu trả ra; nếu thiếu, hãy thêm Supplier hoặc Class token thích hợp.

## 22. Những lỗi thường gặp

### Lỗi 1 — Quên khai báo <T> ở static factory

~~~java
class BadBox<T> {
    // Sai: static method không được dùng trực tiếp T của class.
    // static BadBox<T> of(T value) { return new BadBox<>(); }
}
~~~

Sửa thành **static <U> BadBox<U> of(U value)** với constructor đúng chữ ký. T của class không thuộc phạm vi dùng được của static context theo cách đó.

### Lỗi 2 — Tin rằng return type T cho phép new T()

~~~java
// Sai, chỉ minh họa:
static <T> T create() {
    // return new T();
    return null;
}
~~~

Type erasure khiến T không cung cấp constructor cụ thể. Dùng Supplier<? extends T> hoặc Class<T> kèm reflection có điều kiện, tùy contract.

### Lỗi 3 — Giấu cast bằng @SuppressWarnings("unchecked")

~~~java
@SuppressWarnings("unchecked")
static <T> T misleading(Object value) {
    return (T) value;
}
~~~

Đây chỉ là cast không được compiler xác minh. Caller vẫn có thể yêu cầu T không phù hợp với object, dẫn đến runtime failure. Nếu kiểu đích cần kiểm tra, nhận **Class<T>** và dùng **type.cast(value)**.

### Lỗi 4 — Trả Object thay vì giữ type parameter

~~~java
static Object wrap(Object input) {
    return input;
}
~~~

Phương thức này làm mất type relationship cần thiết cho caller. Nếu yêu cầu giữ kiểu đầu vào làm kiểu trả về, dùng **static <T> T identity(T input)** hoặc một container có **<T> Box<T> of(T input)**.

### Lỗi 5 — Dùng raw type ở bên trong factory

~~~java
// Không khuyến khích:
static <T> java.util.List<T> broken() {
    java.util.List raw = new java.util.ArrayList();
    // return raw; // Unchecked conversion.
    return new java.util.ArrayList<>();
}
~~~

Raw type làm giảm type safety. Hãy khai báo trực tiếp **List<T> result = new ArrayList<>()**, hoặc tạo và trả new ArrayList<T>() với đúng quan hệ kiểu.

### Lỗi 6 — Đồng nhất generic type với tính thay đổi được của collection

**List<T>** chỉ nói kiểu phần tử. **List.of(...)** trả unmodifiable list; **new ArrayList<>(...)** trả mutable list. Factory phải chọn đúng implementation theo nhu cầu, không dựa riêng vào ký hiệu T.

### Lỗi 7 — Quên kiểm tra null

**List.of(value)**, **Map.of(key,value)** và **List.copyOf(source)** có thể ném NPE với null theo hợp đồng. Trong khi **Optional.ofNullable(value)** cố ý chấp nhận null. Caller không thể suy ra chính sách null chỉ bằng type parameter T.

### Lỗi 8 — Nhầm Class<T> với khả năng xác minh mọi kiểu lồng nhau

**Class<List<String>>** không có class literal tương ứng; thông thường chỉ có **List.class** mang thông tin raw class. Class<T>.cast kiểm tra class runtime, không tự xác nhận kiểu phần tử String của một List tùy ý.

### Lỗi 9 — Viết wildcard ở vị trí tham số kiểu chưa có nguồn xác định

Wildcard dùng tại nơi sử dụng các kiểu generic, ví dụ **List<? extends Number>**, và có quy tắc đọc/ghi riêng. Không khai báo method như **static <?> ...** để thay cho **static <T> ...**. Muốn đặt tên mối quan hệ giữa input và result, hãy khai báo type parameter T.

### Lỗi 10 — Lạm dụng một T cho hai vai trò không liên quan

Nếu factory nhận kiểu nguồn S và trả kiểu đích T sau phép biến đổi, dùng **<S,T>** thường rõ hơn một T ép hai phía cùng kiểu. Ví dụ **<S,T> T convert(S value, Function<? super S, ? extends T> mapper)** diễn đạt đúng chuyển đổi khác kiểu.

## 23. Tự kiểm tra

**Câu 1.** Trong **static <T> Box<T> of(T value)**, <T> đầu tiên, Box<T> và T value giữ vai trò gì?

**Đáp án:** <T> khai báo type parameter của method; Box<T> dùng T làm type argument của container trả về; T value là tham số giá trị kiểu T. Chữ ký liên kết kiểu của value và kiểu phần tử của Box.

**Câu 2.** Vì sao **String text = Box.of("Java").value()** có thể làm việc mà không cần cast?

**Đáp án:** Compiler suy luận type argument của generic factory và Box từ đối số "Java"/ngữ cảnh, nên value() có kiểu tĩnh String. Không phải chuyển từ Object một cách không kiểm tra.

**Câu 3.** Tại sao **static <T> T create() { return new T(); }** không hợp lệ?

**Đáp án:** Java không cho phép tạo instance của type variable bằng new T() do type erasure và vì không có constructor được bảo đảm cho mọi T. Cần một nguồn tạo như Supplier<T> hoặc Class<T> với điều kiện khởi tạo phù hợp.

**Câu 4.** Khác biệt giữa **Supplier<T>** và **Class<T>** đối với factory là gì?

**Đáp án:** Supplier cung cấp một hành vi tạo T do caller chỉ rõ; Class<T> mang runtime class token, có thể dùng cho kiểm tra cast hoặc reflection. Cả hai đều có giới hạn/hợp đồng riêng; Class<T> không thể tự xác minh type arguments lồng nhau.

**Câu 5.** Trong **static <K,V> Pair<K,V> pair(K k,V v)**, gọi pair("x", 1) thường suy luận ra kiểu gì?

**Đáp án:** Pair<String, Integer>, vì literal 1 được boxing thành Integer.

**Câu 6.** Tại sao **@SuppressWarnings("unchecked")** không tự khiến một factory trả T trở nên type-safe?

**Đáp án:** Annotation chỉ ẩn cảnh báo; cast chưa được chứng minh có thể thất bại lúc chạy. Factory an toàn cần mối quan hệ kiểu thực giữa đầu vào/nguồn tạo và kết quả.

**Câu 7.** Vì sao method **mutableCopy(Collection<? extends T>)** có thể giúp tạo List<Number> từ List<Integer>?

**Đáp án:** Upper-bounded wildcard cho phép collection nguồn chứa T hoặc subtype của T; constructor ArrayList sao chép các phần tử vào danh sách có kiểu kết quả T được compiler suy luận từ ngữ cảnh.

**Câu 8.** **List.copyOf** có cùng hợp đồng null và mutability với **new ArrayList<>(source)** không?

**Đáp án:** Không. List.copyOf trả unmodifiable list và không chấp nhận phần tử null. ArrayList mới có thể chứa null và cho phép sửa cấu trúc.

**Câu 9.** Với **static <T> Response<T> failure(String message)** chỉ lưu null vào data, làm sao gán cho Response<String>?

**Đáp án:** Dùng **Response<String> r = Responses.failure("error")** nhờ target typing, hoặc **Responses.<String>failure("error")**. Factory không tạo String; nó tạo Response được tham số hóa bằng kiểu mong muốn với data null.

**Câu 10.** Khi nào nên giữ kiểu trả về là một interface generic thay vì implementation class?

**Đáp án:** Khi caller chỉ cần hợp đồng của interface và muốn thay implementation về sau. Ví dụ **Repository<K,V> fromMap(Map<K,V>)** giữ đầy đủ type arguments mà không buộc caller phụ thuộc MapRepository.

## 24. Ghi nhớ cốt lõi

Một generic factory method **an toàn kiểu** không chỉ có ký hiệu **<T>** trong chữ ký; nó phải có cách xác minh hoặc tạo ra đối tượng tương thích với T. Các dạng **<T> Box<T> of(T value)**, **<T> T create(Supplier<? extends T>)**, **<S,T> T convert(S, Function<? super S,? extends T>)** và **<T> T checked(Object, Class<T>)** thể hiện bốn cách giữ hoặc xác lập quan hệ kiểu mà không yêu cầu caller cast.

Hãy ưu tiên để compiler chứng minh phần lớn quan hệ từ chữ ký và implementation; chỉ dùng kiểm tra runtime khi dữ liệu thực sự bắt đầu ngoài hệ thống kiểu (như Object từ nguồn không tin cậy). Định nghĩa riêng chính sách null, exception, mutability và vòng đời object. Với cách thiết kế đó, caller đọc được ngay kết quả sẽ mang kiểu gì, còn sai lệch kiểu được phát hiện sớm thay vì bị che trong một phép ép kiểu không kiểm tra.


</details>

- [Quay lại đầu trang](#back-to-top)

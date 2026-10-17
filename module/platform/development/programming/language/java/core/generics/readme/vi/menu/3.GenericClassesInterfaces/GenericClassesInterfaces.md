<a id="back-to-top"></a>

# Lớp và giao diện

## Menu
- [Thiết kế lớp lưu giữ giá trị Box&lt;T&gt;](#generic-classes-interfaces-001)
- [Dùng T cho trường, tham số, giá trị trả về và constructor](#generic-classes-interfaces-002)
- [Thiết kế lớp nhiều tham số kiểu với Pair&lt;K,V&gt;](#generic-classes-interfaces-003)
- [Khai báo giao diện generic và hợp đồng theo kiểu](#generic-classes-interfaces-004)
- [Triển khai giao diện bằng đối số kiểu cụ thể](#generic-classes-interfaces-005)
- [Triển khai giao diện bằng tham số kiểu của lớp](#generic-classes-interfaces-006)
- [Record generic và các thành phần được tham số hóa](#generic-classes-interfaces-007)
- [Phân biệt kiểu khai báo generic với từng cách sử dụng kiểu đó](#generic-classes-interfaces-008)

## <a id="generic-classes-interfaces-001">Thiết kế lớp lưu giữ giá trị Box&lt;T&gt;</a>

<details>
<summary>Xem chi tiết</summary>

# Thiết kế lớp lưu giữ giá trị `Box<T>` trong Java Generics

Một nhu cầu đơn giản khi thiết kế API là **lưu một giá trị và lấy lại giá trị ấy với đúng kiểu đã khai báo**. Ta có thể viết riêng `StringBox`, `IntegerBox`, `CustomerBox` cho từng kiểu, nhưng các lớp sẽ lặp lại cùng cấu trúc. Ta cũng có thể dùng một trường `Object`, song người gọi thường phải ép kiểu thủ công và có nguy cơ gặp `ClassCastException`.

Java Generics cho phép định nghĩa **một lớp `Box<T>`**: `T` là *tham số kiểu* đại diện cho kiểu giá trị mà hộp lưu giữ. Khi sử dụng `Box<String>`, `Box<Integer>` hoặc `Box<Customer>`, trình biên dịch kiểm tra dữ liệu theo kiểu được chỉ định. Bài học này tập trung vào cách thiết kế lớp, constructor, trường, phương thức và hợp đồng sử dụng của `Box<T>`, cùng các quyết định thực tế như có cho phép `null` hay cho phép thay giá trị hay không.

## 1. Xác định hợp đồng của `Box<T>`

Một `Box<T>` tối thiểu cần trả lời các câu hỏi:

1. **Hộp lưu gì?** Một giá trị kiểu `T`.
2. **Giá trị được đưa vào bằng cách nào?** Qua constructor hoặc phương thức tạo (`factory method`).
3. **Lấy giá trị ra thế nào?** Phương thức `T get()` để người gọi nhận đúng kiểu đã khai báo.
4. **Có thay đổi giá trị sau khởi tạo không?** Ví dụ chính dùng trường `final` và không có setter, tức tham chiếu đến giá trị được cố định sau constructor.
5. **Có nhận `null` không?** Ví dụ chính **không nhận `null`**, nhằm giữ hợp đồng dễ hiểu. Đây là một quyết định thiết kế cụ thể, không phải yêu cầu của generics.

Đối với phiên bản đơn giản, API sẽ có dạng:

```java
public final class Box<T> {
    private final T value;

    public Box(T value) { /* lưu value */ }

    public T get() { /* trả về value */ }
}
```

Đây chỉ là **phác thảo chữ ký**. Phần tiếp theo là mã Java hoàn chỉnh. `T` không phải tên một lớp cụ thể và cũng không phải biến lưu dữ liệu. Nó là tham số kiểu được khai báo ở `<T>`; trong mỗi cách sử dụng như `Box<String>`, trình biên dịch diễn giải `T` theo đối số kiểu tương ứng.

## 2. Cài đặt hoàn chỉnh `Box<T>`

Tạo tệp **`Box.java`**:

```java
import java.util.Objects;

public final class Box<T> {
    private final T value;

    public Box(T value) {
        this.value = Objects.requireNonNull(value, "value");
    }

    public T get() {
        return value;
    }

    public static <U> Box<U> of(U value) {
        return new Box<>(value);
    }
}
```

### Giải thích từng thành phần

**`public final class Box<T>`:** `<T>` khai báo tham số kiểu trong phạm vi lớp; `final` ngăn lớp con kế thừa, giúp giữ thiết kế hộp đơn giản và cố định. Một lớp generic không bắt buộc phải `final`; đây là lựa chọn của ví dụ.

**`private final T value`:** trường có kiểu `T`, được che giấu khỏi mã bên ngoài bằng `private`. `final` bảo đảm tham chiếu này được gán trong quá trình khởi tạo và không được gán lại sau đó. `final` **không** tự khiến đối tượng mà trường trỏ tới trở nên bất biến.

**`public Box(T value)`:** constructor nhận đúng kiểu `T` mà nơi sử dụng lớp đã chỉ định. Ví dụ với `Box<String>`, truyền một `Integer` vào constructor sẽ không tương thích với hợp đồng đó.

**`Objects.requireNonNull(value, "value")`:** từ chối `null` tại nơi khởi tạo, ném `NullPointerException` với thông báo `value`. Quyết định này tránh việc một hộp hợp lệ theo API nhưng không có giá trị. Nếu bài toán cần biểu diễn “có thể không có giá trị”, nên thiết kế và ghi rõ hợp đồng khác, chẳng hạn một lớp cho phép `null` hoặc dùng `Optional<T>` khi phù hợp.

**`public T get()`:** trả về giá trị với kiểu `T` trên giao diện nguồn Java. Với `Box<String>`, người gọi dùng `get()` như một biểu thức `String` mà không cần ép kiểu tường minh.

**`public static <U> Box<U> of(U value)`:** đây là phương thức tạo tiện dụng và **bản thân nó là phương thức generic**. Phương thức `static` không thể sử dụng trực tiếp tham số kiểu `T` của lớp, nên nó khai báo riêng `<U>`. Lời gọi `Box.of("Java")` cho phép trình biên dịch suy luận `U` là `String` trong ngữ cảnh thích hợp.

Thiết kế có cả constructor lẫn factory để minh họa hai cách khởi tạo hợp lệ. Dự án thực tế có thể chọn một cách làm API chính, tùy nhu cầu.

## 3. Sử dụng `Box<T>` với các kiểu dữ liệu khác nhau

Tạo tệp **`BoxDemo.java`** cạnh `Box.java`:

```java
import java.util.List;

public class BoxDemo {
    public static void main(String[] args) {
        Box<String> language = new Box<>("Java");
        Box<Integer> count = Box.of(7);
        Box<List<String>> topics = Box.of(List.of("core", "generics"));
        Box<Double> ratio = Box.of(3.14);

        System.out.println(language.get().toUpperCase());
        System.out.println(count.get() + 5);
        System.out.println(String.join(" | ", topics.get()));
        System.out.println("Box contains: " + ratio.get());
    }
}
```

Chạy bằng **Java 17 trở lên** trong thư mục chứa hai tệp:

```bash
javac -encoding UTF-8 Box.java BoxDemo.java
java BoxDemo
```

Kết quả chuẩn (`stdout`):

```text
JAVA
12
core | generics
Box contains: 3.14
```

Diễn giải các lệnh:

- `new Box<>("Java")` dùng **diamond operator** `<>`. Kiểu đích `Box<String>` giúp trình biên dịch suy luận đối số kiểu của biểu thức khởi tạo.
- `Box.of(7)` suy luận `Integer` cho phương thức factory, bởi `7` có thể được đóng hộp tự động từ `int` sang `Integer`.
- `Box<List<String>>` cho thấy `T` có thể là một **kiểu tham số hóa khác**, không chỉ các lớp đơn giản.
- `Box<Double>` có thể nhận `3.14` qua autoboxing sang `Double`.
- `get()` được gọi qua các biến có kiểu rõ ràng; trình biên dịch xác định được phương thức của kết quả như `String.toUpperCase()` hoặc phép cộng số nguyên mà không cần người gọi viết cast.

Cả bốn cách sử dụng dùng chung **một định nghĩa `Box<T>`**. Ta không cần viết bốn lớp riêng có cùng nhiệm vụ.

## 4. Theo dấu một `T` xuyên suốt hợp đồng `Box`

Trong chính `Box<T>`, **một tham số kiểu** kết nối ba điểm: trường `private final T value` lưu đúng loại, constructor `Box(T value)` nhận đúng loại và getter `T get()` trả đúng loại. Ví dụ:

```java
Box<String> title = new Box<>("Generics");
String text = title.get();
```

Tại điểm dùng `Box<String>`, cả đối số constructor và kết quả getter đều phải tương thích với `String`. Nếu lớp cần setter, chữ ký nên là `void set(T value)` chứ không phải `set(Object)`; bản `Box` cố định giá trị ở bài này cố ý **không** cung cấp setter.

Đây là cách đọc **hợp đồng của hộp**, không phải toàn bộ quy tắc phạm vi type variable: khi thiết kế các lớp phức tạp hơn, còn cần xem `T` trong nhiều method parameters, interface, subclass, bounded type parameters và static generic methods. Những trường hợp đó không làm thay đổi ý chính: giữ cùng `T` ở những vị trí phải liên hệ kiểu, thay vì để rơi về `Object`.

## 5. So sánh với hộp sử dụng `Object`

Một cách cài đặt không dùng generic là:

```java
final class ObjectBox {
    private final Object value;

    ObjectBox(Object value) {
        this.value = value;
    }

    Object get() {
        return value;
    }
}
```

Đây là **lớp minh họa độc lập**; nó không thuộc hai tệp của ví dụ `BoxDemo`. Khi sử dụng trong một phương thức:

```java
ObjectBox raw = new ObjectBox("Java");
String text = (String) raw.get();
```

người gọi phải ép kiểu từ `Object` về `String`. Nếu một nơi khác tạo `ObjectBox` chứa `Integer` mà người đọc vẫn ép sang `String`, lệnh ép có thể ném `ClassCastException` lúc chạy.

Trong phiên bản generic:

```java
Box<String> typed = Box.of("Java");
String text = typed.get();

// Box<String> wrong = Box.of(42); // Không biên dịch: hai kiểu không phù hợp
```

Kiểu dữ liệu sai có thể bị phát hiện tại thời điểm biên dịch, gần nơi tạo hoặc truyền giá trị. Generics đặc biệt có ích khi nhiều thành phần cùng dùng một API nhưng cần hợp đồng kiểu rõ ràng.

## 6. Các lựa chọn thiết kế quan trọng

### 6.1. Hộp cố định giá trị hay hộp cho phép thay thế?

Phiên bản chính dùng `private final T value` và chỉ có `get()`. Người dùng không thể gọi `set(...)` vì API không công bố phương thức thay giá trị. Điều này phù hợp với hộp chứa một giá trị đã xác lập tại lúc tạo.

Nếu yêu cầu bài toán là **thay thế giá trị theo thời gian**, một lớp khác có thể sử dụng trường không có `final` và setter nhận đúng kiểu `T`.

Ví dụ đầy đủ trong tệp **`MutableBox.java`**:

```java
import java.util.Objects;

public final class MutableBox<T> {
    private T value;

    public MutableBox(T value) {
        this.value = Objects.requireNonNull(value, "value");
    }

    public T get() {
        return value;
    }

    public void set(T newValue) {
        this.value = Objects.requireNonNull(newValue, "newValue");
    }
}
```

Ví dụ sử dụng **bên trong một phương thức**, sau khi khai báo lớp trên:

```java
MutableBox<String> status = new MutableBox<>("NEW");
status.set("DONE");
System.out.println(status.get()); // DONE
// status.set(10);                 // Lỗi biên dịch: 10 không phải String
```

Như vậy `set(T)` vẫn giữ được hợp đồng kiểu. Tuy nhiên, hộp có thể thay đổi giá trị đòi hỏi cân nhắc về đồng bộ nếu được chia sẻ giữa các luồng; phiên bản này **không được thiết kế là thread-safe**.

### 6.2. `final` ở trường không bảo đảm bất biến sâu

Xét trường hợp `T` là `List<String>` có thể sửa đổi. Tạo tệp **`AliasingDemo.java`** và dùng cùng `Box.java`:

```java
import java.util.ArrayList;
import java.util.List;

public class AliasingDemo {
    public static void main(String[] args) {
        List<String> source = new ArrayList<>();
        source.add("A");

        Box<List<String>> shared = Box.of(source);
        source.add("B");
        System.out.println(shared.get());

        Box<List<String>> snapshot = Box.of(List.copyOf(source));
        source.add("C");
        System.out.println(snapshot.get());
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 Box.java AliasingDemo.java
java AliasingDemo
```

`stdout`:

```text
[A, B]
[A, B]
```

Giải thích:

- `shared` chứa cùng một tham chiếu `ArrayList` với biến `source`. Dù trường `value` là `final`, việc `source.add("B")` vẫn làm thay đổi đối tượng danh sách mà cả hai tham chiếu cùng trỏ tới.
- `snapshot` chứa bản sao danh sách tạo bằng `List.copyOf(source)`. Khi thêm `"C"` vào `source` sau đó, danh sách trong `snapshot` không đổi. `List.copyOf` tạo danh sách không sửa được qua API danh sách; nó cũng không tự sao chép sâu các phần tử là đối tượng có thể thay đổi.

**Kết luận thiết kế:** `Box<T>` giữ cố định tham chiếu, nhưng không thể tự bảo đảm mọi `T` đều bất biến. Nếu muốn bảo vệ dữ liệu có thể thay đổi, hãy xác định rõ quy tắc tạo bản sao, quyền sở hữu dữ liệu hoặc chọn kiểu giá trị bất biến tại ranh giới sử dụng.

### 6.3. Có cho phép `null` không?

Ví dụ chính chủ ý dùng `Objects.requireNonNull`. Vì vậy:

```java
// Box<String> absent = new Box<>(null); // Biên dịch được, nhưng ném NullPointerException khi chạy
```

Bản thân tham số kiểu `String` không có nghĩa biến không thể chứa `null`; việc từ chối `null` đến từ **logic constructor**.

Một thiết kế khác có thể cho phép `null` bằng cách chỉ gán `this.value = value;`, nhưng khi đó API phải công bố ý nghĩa của việc giá trị vắng mặt. Không nên vừa cho phép `null` vừa ngầm giả định `get()` luôn trả về dữ liệu có thể gọi phương thức.

### 6.4. Có nên viết `equals()`, `hashCode()`, `toString()`?

`Box<T>` ở trên là một lớp thông thường kế thừa hành vi mặc định từ `Object`. Hai hộp chứa hai giá trị bằng nhau **không tự động được coi là bằng nhau theo nội dung**:

```java
Box<String> first = Box.of("Java");
Box<String> second = Box.of("Java");
System.out.println(first.equals(second)); // false với Box hiện tại
```

Nếu ứng dụng cần so sánh hộp theo giá trị, dùng làm khóa map hoặc hiển thị nội dung, có thể thiết kế thêm `equals()`, `hashCode()` và `toString()` theo một hợp đồng nhất quán. Điều này là quyết định về **ngữ nghĩa của đối tượng Box**, tách biệt với khả năng tham số hóa kiểu của generics. Không nên tự động giả định generic class có ngữ nghĩa so sánh giống record.

## 7. Kết hợp với interface generic

Một lớp lưu giữ giá trị có thể triển khai một **interface generic** mô tả khả năng đọc dữ liệu. Ví dụ kiến trúc:

```java
interface ValueSource<T> {
    T get();
}

final class ReadOnlyBox<T> implements ValueSource<T> {
    private final T value;

    ReadOnlyBox(T value) {
        this.value = value;
    }

    @Override
    public T get() {
        return value;
    }
}
```

Đây là **các khai báo lớp/interface minh họa**; chúng có thể đặt chung trong một tệp không công khai `public` hoặc tách thành các tệp phù hợp. `ReadOnlyBox<T> implements ValueSource<T>` nghĩa là đối số kiểu `T` được giữ nhất quán trong hợp đồng interface. Ví dụ `ValueSource<String> source = new ReadOnlyBox<>("Java");` cho phép mã phụ thuộc vào khả năng `get()` mà không cần biết cách lưu giá trị bên trong.

Không nhất thiết tạo interface cho một lớp duy nhất nếu không có nhu cầu trừu tượng hóa. Lựa chọn interface hữu ích khi có nhiều cách cung cấp giá trị chung một hợp đồng, chẳng hạn hộp có sẵn giá trị và một nguồn tính toán giá trị theo yêu cầu.

## 8. Những giới hạn cần hiểu khi thiết kế `Box<T>`

### 8.1. Chỉ được dùng kiểu tham chiếu làm đối số kiểu

`Box<int>` không hợp lệ vì `int` là kiểu nguyên thủy. Dùng lớp bao `Integer`:

```java
Box<Integer> number = Box.of(42);
int value = number.get(); // Tự unboxing Integer -> int
```

Java cũng có các lớp bao như `Long`, `Double`, `Boolean`. Nếu bài toán xử lý số nguyên hoặc số thực ở quy mô lớn, chi phí boxing và cấp phát đối tượng có thể ảnh hưởng hiệu năng; `Box<Integer>` không thay thế mọi trường hợp tối ưu hóa cho primitive.

### 8.2. Hai kiểu tham số hóa khác nhau không tự động tương thích

`String` là kiểu con của `Object`, nhưng **`Box<String>` không phải kiểu con của `Box<Object>`**. Generics Java mặc định **bất biến**:

```java
Box<String> stringBox = Box.of("Java");
// Box<Object> objectBox = stringBox; // Sai: incompatible types
```

Nếu một hàm chỉ cần **đọc** giá trị từ mọi loại hộp, có thể dùng wildcard `Box<?>`:

```java
static void printValue(Box<?> box) {
    System.out.println(box.get());
}
```

Đoạn `printValue` là **phương thức minh họa**, cần đặt trong một lớp. `Box<?>` biểu thị hộp có đối số kiểu chưa biết. Kiểu sử dụng của `box.get()` có thể xem như `Object`; không thể giả định đó là `String` hay `Integer`. Wildcard nên xuất hiện khi nó thật sự diễn đạt nhu cầu linh hoạt ở ranh giới API.

### 8.3. Tham số kiểu bị xóa phần lớn lúc chạy

Java generics chủ yếu được kiểm tra **tại thời điểm biên dịch** và thực thi với cơ chế `type erasure`. Chương trình không tạo ra một lớp runtime `Box<String>` hoàn toàn độc lập với `Box<Integer>`. Không thể viết:

```java
// if (someObject instanceof Box<String>) { ... } // Không hợp lệ
```

vì JVM không giữ thông tin kiểu `String` của `Box<String>` theo cách kiểm tra ấy. Có thể kiểm tra `instanceof Box<?>` để biết đối tượng là một `Box`, nhưng điều đó **không chứng minh** kiểu dữ liệu đang được hộp lưu giữ.

### 8.4. Raw type có thể phá vỡ hợp đồng kiểu

Mã nguồn cũ có thể bỏ đối số kiểu và dùng `Box` thay vì `Box<String>`. Raw type có thể dẫn đến cảnh báo và làm xuất hiện dữ liệu sai kiểu qua những thao tác không được kiểm tra đầy đủ. Hãy tránh:

```java
Box raw = Box.of(10); // Raw type; mất thông tin tham số kiểu tại nơi dùng
```

Thay bằng:

```java
Box<Integer> typed = Box.of(10);
```

Chạy `javac -Xlint:rawtypes,unchecked` giúp phát hiện nhiều đường dùng generic không an toàn. Generics làm giảm đáng kể lỗi cast, nhưng không thể bảo đảm an toàn nếu lập trình viên cố tình bỏ các cảnh báo hoặc dùng ép kiểu không được kiểm tra.

### 8.5. Không dùng `T` của lớp trực tiếp trong thành phần `static`

Đoạn sau là **mã sai**:

```java
class WrongBox<T> {
    // static T shared;        // Sai: static context không thể tham chiếu T của lớp
    // static T value() { ... } // Sai vì lý do tương tự
}
```

Một phương thức `static` generic cần khai báo tham số kiểu của **chính nó**, ví dụ `static <U> Box<U> of(U value)` trong `Box.java`. Điều đó giải thích vì sao factory `of` dùng `U` thay vì dùng trực tiếp `T` của lớp.

## 9. Lỗi phổ biến và cách sửa

| Tình huống | Vì sao có vấn đề? | Cách thiết kế tốt hơn |
|---|---|---|
| `class Box { Object value; }` và liên tục ép kiểu sau `get()` | Hợp đồng không chỉ ra kiểu giá trị ở vị trí sử dụng. | `class Box<T> { T value; }` và `T get()`. |
| `Box<String> b = Box.of(123);` | `Integer` không phù hợp với `String` trong hợp đồng đích. | Truyền `"123"` hoặc dùng `Box<Integer>` tùy dữ liệu thật. |
| `Box<int> b` | Generics yêu cầu kiểu tham chiếu. | `Box<Integer> b`. |
| `private Object value; public T get() { return (T) value; }` | Cài đặt tự tạo cast không được kiểm tra, dễ che giấu dữ liệu sai kiểu. | Lưu trực tiếp trường `T value`; dùng kiểm tra kiểu của compiler. |
| `Box raw = new Box("x");` | Bỏ đối số kiểu làm suy yếu kiểm tra kiểu và gây cảnh báo. | `Box<String> box = new Box<>("x");`. |
| Dùng `final T value` rồi nghĩ `List<T>` bên trong không thể đổi | `final` khóa tham chiếu, không khóa trạng thái đối tượng. | Chọn kiểu bất biến hoặc tạo bản sao phù hợp. |
| Constructor cho phép `null` nhưng getter giả định luôn khác `null` | Hợp đồng vắng mặt dữ liệu không rõ. | Quyết định rõ chính sách `null` ở constructor và API. |
| Kỳ vọng hai `Box` có cùng nội dung tự động `equals` | Generic không tự sinh so sánh theo nội dung. | Định nghĩa ngữ nghĩa giá trị nếu nghiệp vụ cần. |

Đặc biệt, không cần tạo một hệ thống generic phức tạp chỉ để lưu đúng một giá trị. `Box<T>` chỉ là ví dụ nhỏ cho thấy **kiểu dữ liệu trở thành một phần của hợp đồng công khai**; trong ứng dụng thật, chỉ nên thêm phương thức và trừu tượng hóa khi hành vi nghiệp vụ cần đến.

## 10. Tự kiểm tra

1. Trong khai báo `class Box<T>`, `T` đại diện cho điều gì? Nó được sử dụng ở đâu trong `private final T value`, `Box(T value)` và `T get()`?
2. Vì sao `Box<String> a = new Box<>("Java");` đúng, nhưng `Box<String> b = new Box<>(123);` không hợp lệ?
3. Trong phiên bản `Box` dùng `Objects.requireNonNull`, nếu gọi `Box.of(null)` thì lỗi xảy ra ở thời điểm nào và do đâu?
4. Vì sao `Box<List<String>>` có thể chứa một `ArrayList` thay đổi nội dung mặc dù trường `value` là `final`?
5. Vì sao factory `static <U> Box<U> of(U value)` phải khai báo `<U>` độc lập với `T` của lớp?
6. Nếu một hàm chỉ in giá trị của hộp mà không quan tâm kiểu cụ thể, chữ ký `void printValue(Box<?> box)` có lợi ích gì so với nhận `Box<String>`?

**Gợi ý đáp án:** (1) `T` là tham số kiểu của lớp, dùng đồng nhất cho dữ liệu được lưu, giá trị constructor nhận và kiểu getter trả ra. (2) Giá trị `123` không tương thích với `String` được yêu cầu. (3) Ném `NullPointerException` lúc thực thi constructor, vì chính sách không nhận `null` của lớp. (4) `final` không ngăn thay đổi nội dung đối tượng được tham chiếu. (5) Phương thức `static` không thuộc một thực thể `Box<T>` cụ thể nên phải có tham số kiểu riêng. (6) Wildcard biểu thị hàm đọc được mọi hộp mà không giả định loại dữ liệu bên trong.

Khi thiết kế một `Box<T>` tốt, hãy bắt đầu từ hợp đồng đơn giản: **lưu một giá trị đúng kiểu, trả lại đúng kiểu, che giấu trường dữ liệu, quyết định chính sách `null` và khả năng thay thế giá trị**. Generics bảo vệ quan hệ kiểu ở lúc biên dịch; các bảo đảm khác như bất biến sâu, so sánh theo nội dung và an toàn đa luồng cần được thiết kế riêng theo nhu cầu.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="generic-classes-interfaces-002">Dùng T cho trường, tham số, giá trị trả về và constructor</a>

<details>
<summary>Xem chi tiết</summary>

# Dùng `T` cho trường, tham số, giá trị trả về và constructor trong Java

## 1. Vì sao một lớp cần tham số kiểu `T`?

Trong Java, **generic class** nhận tham số kiểu để nhiều đối tượng có cùng cách hoạt động nhưng giữ được hợp đồng dữ liệu khác nhau. Ví dụ, một ô lưu giá trị có thể chứa `String` ở nơi này và `Integer` ở nơi khác mà không cần tạo hai lớp gần giống nhau.

```java
// Mảnh minh họa; đặt trong một lớp hoặc tệp Java thích hợp.
final class Box<T> {
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
}

Box<String> title = new Box<>("Java");
String text = title.get();
title.set("Generics");
// title.set(123); // Lỗi biên dịch: Box<String> chỉ nhận String.

Box<Integer> count = new Box<>(3);
int n = count.get(); // Unboxing từ Integer.
```

`T` là **tên tham số kiểu** (*type parameter*), khai báo sau tên lớp ở `class Box<T>`. `T` không phải từ khóa hay một lớp cụ thể; compiler sử dụng nó để liên kết kiểu của trường, constructor, tham số phương thức và giá trị trả về. Khi sử dụng `Box<String>`, những vị trí mang `T` được kiểm tra nhất quán theo `String`. Với `Box<Integer>`, chúng được kiểm tra theo `Integer`.

Một lớp có thể có nhiều tham số kiểu, chẳng hạn `Pair<K, V>`; `T` chỉ là quy ước phổ biến cho một kiểu chung. Các quy ước khác gồm `E` (element), `K` (key), `V` (value), `R` (result). Hãy dùng tên dễ hiểu nếu quan hệ giữa nhiều kiểu khó theo dõi.

**Bốn vị trí quan trọng nhất của `T` trong một lớp:**

| Vị trí | Ví dụ | Ý nghĩa |
| --- | --- | --- |
| Trường (field) | `private T value;` | Đối tượng lưu một giá trị của kiểu `T`. |
| Tham số phương thức | `void set(T value)` | Chỉ chấp nhận đối số tương thích `T`. |
| Giá trị trả về | `T get()` | Người gọi nhận giá trị đúng kiểu của đối tượng generic. |
| Tham số constructor | `Box(T initialValue)` | Giá trị ban đầu phải tương thích kiểu `T` của đối tượng mới. |

Đây là quan hệ có tính nhất quán: cùng một tham số kiểu xuất hiện ở nhiều vị trí bảo đảm dữ liệu đi vào và đi ra được kiểm tra theo **một hợp đồng**.

## 2. Phạm vi của `T` trong khai báo lớp

Cú pháp nền tảng là:

```java
class ClassName<T> {
    // Có thể sử dụng T ở các trường và phương thức instance.
}
```

`T` thuộc phạm vi khai báo của lớp và có thể xuất hiện trong:

- Kiểu của trường instance: `T value`, `List<T> items`, `Optional<T> result`.
- Tham số của constructor và phương thức instance: `Box(T value)`, `void set(T value)`.
- Kiểu trả về: `T get()`, `List<T> snapshot()`.
- Kiểu tham số hóa khác: `Comparable<T>`, `Function<T, String>` hay `Map<String, T>`.
- Khai báo type bound hoặc việc triển khai/kế thừa kiểu generic, theo những quy tắc riêng ở các mục tiếp theo.

Kiểu `T` chỉ được biết cụ thể khi mã nguồn dùng lớp, ví dụ `Box<String>`. Ở phần thân `Box<T>`, trình biên dịch phải bảo đảm mọi thao tác hợp lệ cho **mọi kiểu `T` được phép**, không chỉ cho `String`. Vì thế:

```java
// Mảnh minh họa bên trong class Box<T>:
// value.length();          // Sai: không biết T có length().
// value.toString();        // Hợp lệ khi value không null vì mọi reference đều có toString().
// T another = value;       // Hợp lệ.
```

Giới hạn này rất có ích: nó ngăn một lớp generic phụ thuộc nhầm vào API chỉ có ở một kiểu dữ liệu cụ thể.

### `T` là kiểu tham chiếu

Generic type arguments phải là **reference types**. Không thể viết `Box<int>` hoặc `List<boolean>`; hãy viết `Box<Integer>` hoặc `List<Boolean>`. Java tự boxing/unboxing trong nhiều ngữ cảnh:

```java
Box<Integer> number = new Box<>(42); // int được boxing thành Integer.
int result = number.get();           // Integer được unboxing thành int.
```

Boxing không biến `T` thành primitive thật bên trong lớp; cần cân nhắc hiệu năng và `null` khi dữ liệu dạng primitive xuất hiện với tần suất cao.

## 3. Dùng `T` làm kiểu của trường

Trường `private T value;` buộc giá trị được lưu phải thuộc kiểu của từng đối tượng `Box<T>`. Trường nên để `private` và chỉ cung cấp thao tác hợp lệ thông qua phương thức:

```java
final class Slot<T> {
    private T value;

    Slot(T initial) {
        this.value = initial;
    }

    T get() {
        return value;
    }

    void set(T replacement) {
        value = replacement;
    }
}
```

Trường của đối tượng `Slot<String>` mang ràng buộc `String` ở mức compile-time. Việc khai báo `private` không tự tạo tính bất biến; `set` vẫn có thể thay thế dữ liệu. Nếu lớp chỉ lưu giá trị cố định, có thể thiết kế trường `private final T value` và bỏ setter:

```java
final class ImmutableBox<T> {
    private final T value;

    ImmutableBox(T value) {
        this.value = value;
    }

    T get() {
        return value;
    }
}
```

`final` bảo đảm **tham chiếu trường không được gán lại sau khi khởi tạo**, không bảo đảm đối tượng được tham chiếu luôn bất biến. Nếu `T` là một `ArrayList` hoặc đối tượng mutable khác, người giữ tham chiếu vẫn có thể sửa nội dung. Muốn snapshot an toàn cho collection, cần xem xét sao chép phòng vệ.

Một trường chứa collection generic cũng có thể sử dụng `T` làm kiểu phần tử:

```java
// Mảnh minh họa, import java.util.*.
final class Bag<T> {
    private final List<T> items = new ArrayList<>();

    void add(T item) {
        items.add(item);
    }

    List<T> snapshot() {
        return List.copyOf(items);
    }
}
```

Ở đây `List<T>` nghĩa là danh sách phần tử `T`, khác với `List<?>` (kiểu phần tử chưa biết) và raw `List` (bỏ thông tin generic). `List.copyOf` tạo danh sách không sửa được và từ chối phần tử `null`, vì vậy hợp đồng `Bag` cần xử lý/chặn `null` nhất quán khi thêm.

## 4. Dùng `T` trong constructor

Constructor thiết lập trạng thái hợp lệ lúc tạo đối tượng. Với lớp generic, constructor có thể nhận tham số `T` và gán vào trường cùng kiểu:

```java
final class Message<T> {
    private final T payload;

    Message(T payload) { // T lấy từ Message<T>.
        this.payload = payload;
    }

    T payload() {
        return payload;
    }
}

Message<String> hello = new Message<>("Xin chào");
Message<Integer> status = new Message<>(200);
// Message<Integer> wrong = new Message<>("OK"); // Không tương thích Integer.
```

**Điểm cú pháp dễ nhầm:** khai báo constructor là `Message(T payload)`, **không phải** `Message<T>(T payload)`. Tham số kiểu của lớp đã được khai báo ở `class Message<T>`. Dấu diamond `<>` chỉ xuất hiện ở **biểu thức tạo đối tượng**, ví dụ `new Message<>("Xin chào")`, để trình biên dịch suy luận type argument.

Có thể viết rõ cả hai phía:

```java
Message<String> a = new Message<String>("Xin chào");
Message<String> b = new Message<>("Xin chào"); // Diamond từ Java 7.
```

Hai biến đều mang hợp đồng `Message<String>`. Tránh dạng raw `new Message("Xin chào")` vì dễ phát sinh cảnh báo unchecked và làm yếu việc kiểm tra kiểu.

### Constructor nhận kiểu có quan hệ với `T`

Một lớp chứa `List<T>` có thể muốn nhận nguồn là collection của một kiểu con `T`. Khi constructor chỉ **đọc** đầu vào, wildcard `? extends T` thường linh hoạt hơn:

```java
// Mảnh minh họa, import java.util.*.
final class Basket<T> {
    private final List<T> items;

    Basket(Collection<? extends T> source) {
        this.items = new ArrayList<>(source);
    }

    List<T> snapshot() {
        return List.copyOf(items);
    }
}

Basket<Number> numbers = new Basket<>(List.of(1, 2, 3));
// Mỗi phần tử Integer được chấp nhận làm Number.
```

`Collection<? extends T>` có thể cung cấp phần tử thuộc `T` hoặc kiểu con của `T`. Trình biên dịch cho phép sao chép chúng vào `List<T>`. Đây là hướng **producer extends**. Khi thiết kế constructor, cần phân biệt rõ yêu cầu sở hữu dữ liệu: `this.items = new ArrayList<>(source)` tạo bản sao danh sách, tránh việc sửa collection nguồn tác động trực tiếp đến dữ liệu trong lớp.

Không nên nhầm constructor thông thường dùng `T` của lớp với **generic constructor có tham số kiểu riêng**:

```java
// Mảnh minh họa; import java.util.function.Function.
final class ConvertedBox<T> {
    private final T value;

    <U> ConvertedBox(U source, Function<? super U, ? extends T> convert) {
        this.value = convert.apply(source);
    }

    T get() {
        return value;
    }
}

// Trong một phương thức:
ConvertedBox<Integer> length = new ConvertedBox<>("Java", String::length);
int n = length.get(); // 4
```

`T` thuộc `ConvertedBox<T>`, còn `U` chỉ thuộc constructor, được suy luận từ đối số `"Java"`. Constructor generic này vẫn là constructor của `ConvertedBox<Integer>` vì `convert` tạo ra `Integer`. Không khai báo lại `<T>` cho constructor chỉ để dùng `T` của lớp: tên cùng chữ có thể **che khuất** tham số kiểu của lớp, gây hiểu sai hợp đồng.

## 5. Dùng `T` làm kiểu tham số phương thức

Setter, update và thao tác thêm vào collection có thể nhận `T` để cấm dữ liệu sai kiểu ngay lúc biên dịch:

```java
final class MutableBox<T> {
    private T value;

    MutableBox(T initial) {
        value = initial;
    }

    void set(T replacement) {
        value = replacement;
    }

    T swap(T replacement) {
        T old = value;
        value = replacement;
        return old;
    }
}

MutableBox<String> label = new MutableBox<>("A");
label.set("B");
String old = label.swap("C"); // old là "B".
// label.set(123);            // Lỗi: Integer không phải String.
```

Ở `swap(T replacement)`, `T` vừa là **kiểu của đầu vào**, vừa là **kiểu kết quả**; đây là quan hệ mà việc thay hết bằng `Object` sẽ làm mất. Compiler biết `MutableBox<String>.swap(...)` trả `String` và chỉ nhận dữ liệu tương thích `String`.

Một tham số `T` không có nghĩa là chỉ được truyền **đúng một lớp runtime**. Nếu `T` là `Number`, có thể truyền các lớp con hợp lệ:

```java
MutableBox<Number> box = new MutableBox<>(1);
box.set(2.5);                // Double là Number.
Number stored = box.swap(3L); // Long là Number.
```

Tuy nhiên `MutableBox<Integer>` không tự động là `MutableBox<Number>`. Generic class Java mặc định **bất biến** (*invariant*). Quy tắc này ngăn việc đưa một `Double` vào đối tượng vốn cam kết chứa `Integer`.

### Không sử dụng `T` như một kiểu chưa có thao tác

Nếu phương thức muốn cộng/trừ giá trị, `T` không tự có toán tử số học:

```java
// Không hợp lệ trong class Accumulator<T>:
// T add(T a, T b) { return a + b; }
```

Phải mô hình hóa đúng thao tác thông qua bound, đối số hàm hoặc chiến lược cộng:

```java
import java.util.function.BinaryOperator;

final class Combiner<T> {
    private final BinaryOperator<T> combine;

    Combiner(BinaryOperator<T> combine) {
        this.combine = combine;
    }

    T join(T a, T b) {
        return combine.apply(a, b);
    }
}

// Combiner<Integer> adder = new Combiner<>(Integer::sum);
// int total = adder.join(2, 3); // 5
```

`BinaryOperator<T>` mô tả cách kết hợp hai giá trị kiểu `T` thành một kết quả cũng kiểu `T`, không phụ thuộc phép toán cụ thể.

## 6. Dùng `T` làm kiểu trả về

Getter và phương thức tính toán có thể trả trực tiếp `T`:

```java
T get() {
    return value;
}
```

Nếu biến tham chiếu là `Box<String>`, biểu thức `get()` được compiler kiểm tra như `String`; caller không cần tự ép từ `Object`:

```java
Box<String> textBox = new Box<>("Generics");
String upper = textBox.get().toUpperCase();
// int impossible = textBox.get(); // Lỗi biên dịch.
```

Getter không nhất thiết phải trả đúng một `T`; nó có thể trả cấu trúc chứa `T` để giữ thông tin phần tử:

```java
// Mảnh minh họa trong class Bag<T>.
List<T> snapshot() {
    return List.copyOf(items);
}
```

Vì kiểu trả về là `List<T>`, người dùng `Bag<String>` nhận `List<String>` và không phải cast từng phần tử. Đây là lợi ích trực tiếp khi kết hợp generics với Java Collections Framework.

### Giá trị có thể vắng mặt

Nếu nghiệp vụ cho phép chưa có dữ liệu, cân nhắc một hợp đồng rõ ràng:

```java
// Mảnh minh họa khi class có field T value, import java.util.Optional.
Optional<T> find() {
    return Optional.ofNullable(value);
}
```

`Optional.ofNullable` trả empty khi `value == null`; `Optional.of(value)` lại ném `NullPointerException` với `null`. Đừng dùng `Optional<T>` cho mọi getter theo thói quen: hãy chọn khi **khả năng vắng mặt** là ý nghĩa thực sự của API. Với constructor và setter, `Objects.requireNonNull(value)` có thể phù hợp nếu lớp không cho phép null.

## 7. `T` trên interface và lớp triển khai

Interface generic diễn đạt hợp đồng cho nhiều kiểu dữ liệu. Interface không có constructor của riêng nó nhưng có thể khai báo phương thức nhận/trả `T`; lớp triển khai mới sở hữu trường và constructor.

```java
interface Store<T> {
    void put(T value);
    T get();
}

final class MemoryStore<T> implements Store<T> {
    private T value;

    MemoryStore(T initialValue) {
        value = initialValue;
    }

    @Override
    public void put(T value) {
        this.value = value;
    }

    @Override
    public T get() {
        return value;
    }
}
```

Người gọi sử dụng interface với kiểu cụ thể:

```java
Store<String> welcome = new MemoryStore<>("Xin chào");
welcome.put("Tạm biệt");
String message = welcome.get();
// welcome.put(123); // Lỗi biên dịch.
```

Interface cho phép triển khai khác nhau giữ cùng hợp đồng, chẳng hạn lưu trong bộ nhớ, đọc từ file hoặc cache. Khi triển khai, có thể **truyền tiếp tham số kiểu** như `class MemoryStore<T> implements Store<T>` hoặc **chốt kiểu** như `class StringStore implements Store<String>`. Nếu chốt kiểu, các phương thức triển khai phải nhận/trả `String`.

Interface thường nên định nghĩa hành vi độc lập với implementation; đừng dùng `Object` cho tham số rồi buộc caller cast chỉ vì nhiều kiểu dữ liệu cùng tham gia.

## 8. `T` qua lớp cha, lớp con và nhiều tham số kiểu

Khi kế thừa generic class, lớp con có thể tiếp tục giữ `T`:

```java
class BaseHolder<T> {
    private final T value;

    BaseHolder(T value) {
        this.value = value;
    }

    T get() {
        return value;
    }
}

class NamedHolder<T> extends BaseHolder<T> {
    private final String name;

    NamedHolder(String name, T value) {
        super(value);
        this.name = name;
    }

    String name() {
        return name;
    }
}
```

`NamedHolder<Integer>` kế thừa `BaseHolder<Integer>`. Lời gọi `super(value)` truyền đúng kiểu `T` tới constructor của lớp cha. Một lớp con cũng có thể cố định type argument:

```java
class TextHolder extends BaseHolder<String> {
    TextHolder(String value) {
        super(value);
    }
}
```

Khi cần mô tả hai loại dữ liệu liên quan, dùng hai tham số kiểu riêng thay vì ép tất cả về một `T`:

```java
record Pair<L, R>(L left, R right) {}

Pair<String, Integer> result = new Pair<>("Tuổi", 20);
String label = result.left();
Integer age = result.right();
```

`record` generic (Java 17+) tạo thành phần bất biến theo tham chiếu, constructor chuẩn và accessor có kiểu `L`/`R`. Với record, khai báo thành phần trong ngoặc của tên record xác định trạng thái; compiler sinh accessor `left()`, `right()` trả đúng kiểu đã khai báo.

## 9. Bound: khi `T` phải hỗ trợ một API cụ thể

Với `class DataBox<T>` không có bound, compiler chỉ biết `T` là một reference type. Nếu logic cần khả năng chuyển số thành `double`, dùng **upper bound**:

```java
final class NumberBox<T extends Number> {
    private final T number;

    NumberBox(T number) {
        this.number = number;
    }

    T get() {
        return number;
    }

    double asDouble() {
        return number.doubleValue();
    }
}

NumberBox<Integer> a = new NumberBox<>(7);
NumberBox<Double> b = new NumberBox<>(2.5);
// NumberBox<String> wrong = new NumberBox<>("7"); // Lỗi bound.
```

Nhờ `T extends Number`, compiler cho phép gọi `doubleValue()` trên trường `number`. Kiểu trả về của `get()` vẫn là kiểu `T` cụ thể, vì vậy `NumberBox<Integer>.get()` trả `Integer` tại compile-time.

Bound có thể là interface: `<T extends Comparable<T>>`, không dùng từ khóa `implements` trong danh sách type parameter. Có thể kết hợp lớp và interface như `<T extends Number & Comparable<T>>`, trong đó bound lớp (nếu có) phải đứng trước các bound interface.

Cần chọn bound theo **hành vi thật sự cần**. Bound quá rộng làm mất sức mạnh kiểm tra kiểu; bound quá hẹp khiến API không nhận các loại dữ liệu có thể xử lý hợp lệ.

## 10. Phân biệt `T` của lớp, phương thức generic và thành viên `static`

Một lớp generic không buộc mọi phương thức bên trong dùng `T`. Phương thức có thể khai báo **tham số kiểu riêng**:

```java
final class Helper<T> {
    private final T value;

    Helper(T value) {
        this.value = value;
    }

    T get() { return value; } // T thuộc Helper<T>.

    static <U> U choose(U first, U second) {
        return first;
    }
}
```

`U` của `choose` thuộc phương thức. Điều này cũng giải thích vì sao phương thức `static` có thể generic:

```java
String word = Helper.choose("A", "B");
Integer number = Helper.choose(1, 2);
```

Ngược lại, kiểu `T` khai báo ở `class Helper<T>` **không được tham chiếu từ ngữ cảnh static của lớp**:

```java
class Wrong<T> {
    // static T shared;         // Lỗi biên dịch.
    // static T build() { ... } // Lỗi: T thuộc lớp generic.
}
```

Lý do: thành viên `static` thuộc về lớp chung, không được nhân bản theo `Helper<String>` và `Helper<Integer>`. Muốn một phương thức static hoạt động với nhiều kiểu, khai báo tham số kiểu riêng, như `static <U> U identity(U value)`.

**Lưu ý về trùng tên:** Có thể viết `<T> T method(T arg)` trong `class Helper<T>`, nhưng `T` mới của phương thức sẽ che khuất `T` của lớp và dễ gây hiểu lầm. Hãy dùng một tên khác (`U`, `R`) khi chúng là hai tham số kiểu độc lập.

## 11. Khi constructor và phương thức làm việc với collection chứa `T`

Một lỗi rất thường gặp là giả sử mọi cấu trúc mang phần tử có kiểu con đều gán được cho cấu trúc kiểu cha:

```java
List<Integer> integers = new ArrayList<>();
// List<Number> nums = integers; // Sai: generics là invariant.
```

Để chỉ **đọc** dữ liệu cho constructor hoặc phương thức, sử dụng `Collection<? extends T>`. Để **ghi** giá trị `T` vào một đích được truyền từ ngoài, dùng `Collection<? super T>` khi thích hợp:

```java
// Mảnh minh họa nằm trong một lớp utility, import java.util.*.
static <T> void copy(
        Collection<? extends T> source,
        Collection<? super T> destination) {
    for (T item : source) {
        destination.add(item);
    }
}

// Trong một phương thức:
List<Integer> source = List.of(2, 4, 6);
List<Number> destination = new ArrayList<>();
copy(source, destination);
```

Nguyên tắc **PECS (Producer Extends, Consumer Super)** giải thích hai phía:

- `source` sinh ra `T`, nên `? extends T` cho phép đọc an toàn.
- `destination` nhận `T`, nên `? super T` cho phép thêm phần tử `T`.

Nếu lớp của bạn muốn vừa đọc, vừa cập nhật giá trị một cách nhất quán, dùng tham số chính xác `T` ở field, setter và getter vẫn là lựa chọn rõ ràng. Wildcard chỉ nên thêm ở nơi nó thực sự mở rộng phạm vi đối số mà không phá hợp đồng.

Một chi tiết thiết kế: `List<T> snapshot()` được trả về từ lớp và là bản sao không sửa được thì caller không thể trực tiếp thay đổi trạng thái bên trong. Nhưng nếu `T` là một đối tượng mutable, có thể vẫn cần sao chép sâu hoặc loại dữ liệu bất biến để ngăn sửa qua alias của từng phần tử.

## 12. Các giới hạn cần hiểu: erasure, tạo đối tượng và mảng

Java generics sử dụng **type erasure** trong biểu diễn runtime thông thường. Compiler kiểm tra ràng buộc kiểu rồi có thể chèn cast/bridge method khi cần. Một lớp `Box<T>` không sinh ra lớp runtime mới riêng cho mỗi `T`.

Vì vậy, đoạn sau không hợp lệ trong một generic class không có công cụ tạo giá trị:

```java
class CannotConstruct<T> {
    // T value = new T();           // Không thể khởi tạo T trực tiếp.
    // Class<T> type = T.class;      // Không có class literal T.class.
    // T[] values = new T[10];      // Không được tạo mảng T trực tiếp.
    // boolean test = value instanceof T; // T không reifiable.
}
```

Muốn constructor tự tạo một giá trị `T`, hãy **truyền factory** có kiểu, chẳng hạn `Supplier<? extends T>`:

```java
import java.util.Objects;
import java.util.function.Supplier;

final class FactoryBox<T> {
    private final T value;

    FactoryBox(Supplier<? extends T> supplier) {
        value = Objects.requireNonNull(supplier.get());
    }

    T get() { return value; }
}

// FactoryBox<StringBuilder> box =
//     new FactoryBox<>(StringBuilder::new);
```

`Supplier<? extends T>` chịu trách nhiệm tạo giá trị thuộc `T` hoặc kiểu con của `T`. Khi cần phản ánh constructor runtime theo `Class<T>`/reflection, cũng phải truyền thông tin kiểu tường minh và xử lý exceptions; không thể suy ra constructor chỉ từ tên tham số kiểu `T` đã bị erase.

Một hạn chế khác là không thể dùng tham số kiểu để phân biệt hai overload nếu sau erasure chúng có chữ ký trùng nhau:

```java
// Không thể khai báo đồng thời trong cùng một lớp:
// void process(List<String> values) {}
// void process(List<Integer> values) {}
// Hai tham số đều erase thành List, gây name clash.
```

Không lạm dụng raw type hoặc unchecked cast để lách hạn chế này: cần đổi tên hoặc thiết kế một phương thức generic thực sự.

## 13. Ví dụ chạy được: trường, constructor, tham số, kết quả và interface

Chương trình sau gom các kiểu thành viên `T` vào một tệp độc lập. Nó đồng thời minh họa trường mutable, lớp chỉ nhận giá trị ban đầu, interface generic, factory và bound.

Tạo tệp **`GenericMembersDemo.java`** với toàn bộ nội dung:

```java
import java.util.*;
import java.util.function.Supplier;

public class GenericMembersDemo {
    interface Store<T> {
        void put(T value);
        T get();
    }

    static final class Slot<T> implements Store<T> {
        private T value;

        Slot(T initialValue) {
            this.value = Objects.requireNonNull(initialValue);
        }

        @Override
        public void put(T value) {
            this.value = Objects.requireNonNull(value);
        }

        @Override
        public T get() {
            return value;
        }

        T swap(T replacement) {
            T previous = value;
            put(replacement);
            return previous;
        }
    }

    static final class Basket<T> {
        private final List<T> items;

        Basket(Collection<? extends T> initial) {
            items = new ArrayList<>(initial);
        }

        void add(T value) {
            items.add(Objects.requireNonNull(value));
        }

        List<T> snapshot() {
            return List.copyOf(items);
        }
    }

    record Person(String name) {}

    static final class FactoryBox<T> {
        private final T value;

        FactoryBox(Supplier<? extends T> factory) {
            value = Objects.requireNonNull(factory.get());
        }

        T get() { return value; }
    }

    static final class NumberBox<T extends Number> {
        private final T number;

        NumberBox(T number) { this.number = number; }
        double asDouble() { return number.doubleValue(); }
    }

    static <U> U select(U first, U second, boolean chooseSecond) {
        return chooseSecond ? second : first;
    }

    public static void main(String[] args) {
        Slot<String> name = new Slot<>("Lan");
        System.out.println("initial: " + name.get());
        System.out.println("previous: " + name.swap("Mai"));
        System.out.println("current: " + name.get());

        Slot<Integer> count = new Slot<>(3);
        count.put(5);
        System.out.println("count: " + count.get());

        Basket<Number> numbers = new Basket<>(List.of(1, 2, 3));
        numbers.add(2.5);
        System.out.println("basket: " + numbers.snapshot());

        FactoryBox<Person> person = new FactoryBox<>(() -> new Person("An"));
        System.out.println("factory: " + person.get().name());

        NumberBox<Integer> numberBox = new NumberBox<>(7);
        System.out.println("asDouble: " + numberBox.asDouble());
        System.out.println("select: " + select("first", "second", true));
    }
}
```

Biên dịch và chạy trên **JDK 17+**:

```bash
javac -Xlint:all GenericMembersDemo.java
java GenericMembersDemo
```

Kết quả stdout chính xác (đã chạy với JDK 21; không có cảnh báo khi biên dịch bằng `-Xlint:all`):

```text
initial: Lan
previous: Lan
current: Mai
count: 5
basket: [1, 2, 3, 2.5]
factory: An
asDouble: 7.0
select: second
```

Giải thích các liên hệ kiểu:

1. `Slot<T>` khai báo `private T value`: constructor `Slot(T)` nhận giá trị ban đầu, `put(T)` cập nhật, `get(): T` đọc và `swap(T): T` trả giá trị cũ. Tất cả cùng chung tham số kiểu của **lớp**.
2. `Store<T>` giữ hợp đồng phương thức không phụ thuộc cách lưu; `Slot<String>` thực thi `Store<String>` mà không dùng cast.
3. `Basket<T>` giữ trường `List<T>`, constructor nhận nguồn `Collection<? extends T>`, getter `snapshot()` trả `List<T>` là một bản sao không sửa được.
4. `FactoryBox<T>` lấy giá trị từ `Supplier<? extends T>` thay vì cố gọi `new T()`; constructor đảm bảo factory trả giá trị không null.
5. `NumberBox<T extends Number>` hạn chế `T` để có quyền gọi `doubleValue()`.
6. `select` là **static generic method**, tự khai báo `<U>`, độc lập với các tham số kiểu của các lớp lồng ở trên.

Ví dụ dùng `Objects.requireNonNull` với `Slot` và `FactoryBox` để quy định không chấp nhận null. Với `Basket`, constructor giả định collection nguồn có phần tử không null vì `snapshot()` dùng `List.copyOf`; trong thiết kế production, nên thống nhất chính sách null ngay lúc nhận nguồn, ví dụ kiểm tra từng phần tử đầu vào.

## 14. Lỗi phổ biến và cách tự kiểm tra

| Sai lầm | Vì sao sai hoặc rủi ro | Cách giải quyết |
| --- | --- | --- |
| `class Box { Object value; }` rồi cast ở getter | Mất quan hệ kiểu giữa đầu vào/đầu ra, dễ `ClassCastException` | Dùng `class Box<T>`, `T value` và `T get()`. |
| `class Box<T> { Box<T>(T value) {} }` | Constructor không ghi type arguments sau tên constructor | Viết `Box(T value)`. |
| `new Box("Java")` | Tạo đối tượng dạng raw, có thể sinh cảnh báo và làm yếu kiểm tra kiểu | Dùng `new Box<>("Java")` với kiểu đích hoặc `new Box<String>("Java")`. |
| `Box<int>` | Generics nhận reference type | Dùng `Box<Integer>`. |
| `static T cache;` trong `class Box<T>` | `T` của lớp không dùng được trong static context | Dùng field instance hoặc phương thức static tự khai báo `<U>`. |
| `T v = new T();` | Không thể tạo `T` trực tiếp vì type erasure | Truyền `Supplier<? extends T>` hoặc factory khác. |
| Getter trả `Object` dù trường là `T` | Người dùng buộc cast và mất type safety | Trả trực tiếp `T` hoặc container `List<T>`/`Optional<T>`. |
| Setter nhận `Object` | Cho phép giá trị không đúng hợp đồng của đối tượng | Dùng `void set(T value)`. |
| Trả thẳng trường `List<T>` mutable | Người gọi có thể sửa trạng thái nội bộ không qua API | Cân nhắc bản sao `List.copyOf` và quản lý phần tử mutable. |
| Sử dụng `List<Integer>` ở nơi yêu cầu `List<Number>` | Generics bất biến | Dùng kiểu thống nhất hoặc wildcard đúng hướng khi chỉ đọc/ghi. |
| `T extends Number` nhưng cố gọi API chỉ có trên `Integer` | Bound chỉ bảo đảm thành viên của `Number` | Đổi API, kiểm tra runtime có chủ đích hoặc chọn bound phù hợp. |
| Dùng `<T>` trên phương thức với cùng tên `T` của lớp | Tham số kiểu mới che khuất tham số lớp | Đặt tên khác (`<U>`), chỉ khai báo generic method khi cần. |
| Getter có thể trả `null` nhưng caller giả định luôn có dữ liệu | Dễ ném `NullPointerException` | Quy định invariant từ constructor hoặc biểu diễn sự vắng mặt bằng `Optional<T>` khi phù hợp. |

Một cách rà soát nhanh lớp generic là vẽ luồng: **giá trị truyền vào constructor/setter → trường `T` → getter hoặc phương thức xử lý → giá trị trả về `T`**. Nếu ở bất kỳ bước nào bạn buộc phải cast từ `Object`, hãy xem xét có chỗ nào đang làm mất tham số kiểu.

## 15. Bài tập thực hành và tiêu chí tự đánh giá

**Bài 1 — Lớp bất biến.** Viết `final class ReadOnlyBox<T>` với trường `private final T value`, constructor `ReadOnlyBox(T value)` và getter `T get()`. Tạo đối tượng `ReadOnlyBox<String>` và `ReadOnlyBox<Integer>`. Thử truyền `String` vào `ReadOnlyBox<Integer>` để quan sát lỗi biên dịch.

**Bài 2 — Setter và giá trị cũ.** Mở rộng `MutableBox<T>` bằng phương thức `T replace(T next)`, trả về giá trị cũ trước khi cập nhật. Thử gọi với `MutableBox<String>`; giải thích vì sao kiểu trả về chính xác là `String` thay vì `Object`.

**Bài 3 — Interface generic.** Định nghĩa `interface Cache<K, V>` với `void put(K key, V value)` và `Optional<V> find(K key)`. Viết implementation dựa trên `Map<K,V>`. Kiểm tra `Cache<String, Integer>` loại bỏ lời gọi có khóa hoặc giá trị sai kiểu ngay lúc biên dịch. Lưu ý `Map<K,V>` dùng hai tham số vì khóa và giá trị là hai loại dữ liệu khác nhau.

**Bài 4 — Constructor với wildcard.** Viết `class Numbers<T extends Number>` có constructor nhận `Collection<? extends T>`. Tạo `Numbers<Number>` từ `List<Integer>`, sau đó trả về tổng dạng `double` bằng cách duyệt các phần tử và gọi `doubleValue()`. Giải thích tại sao `Collection<T>` trong constructor kém linh hoạt hơn khi nhận các nguồn kiểu con.

**Bài 5 — Không thể `new T()`.** Viết `class LazyValue<T>` nhận `Supplier<? extends T>` ở constructor và tạo đối tượng khi lần đầu gọi `T get()`. Tự quyết định cách cache kết quả và liệu có chấp nhận null hay không; phải mô tả rõ hợp đồng của class.

Sau khi làm bài, người học cần trả lời chắc chắn bốn câu: **`T` được khai báo ở đâu? Trường giữ kiểu gì? Constructor và setter nhận kiểu gì? Getter đảm bảo trả kiểu gì?** Khi cả bốn cùng tham chiếu một tham số kiểu, Java có thể kiểm tra trọn tuyến dữ liệu lúc biên dịch, giúp thiết kế lớp và giao diện tổng quát mà vẫn giữ an toàn kiểu.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="generic-classes-interfaces-003">Thiết kế lớp nhiều tham số kiểu với Pair&lt;K,V&gt;</a>

<details>
<summary>Xem chi tiết</summary>

# Thiết kế lớp nhiều tham số kiểu với `Pair<K, V>` trong Java Generics

Một lớp generic không bị giới hạn ở một tham số kiểu `T`. Khi một đối tượng cần lưu hoặc phối hợp **hai giá trị có thể thuộc hai kiểu khác nhau**, ta có thể khai báo `class Pair<K, V>`. Ký hiệu `K` thường gợi *key* (khóa), `V` gợi *value* (giá trị). Hai kiểu được lựa chọn độc lập khi sử dụng lớp; từ đó trình biên dịch kiểm tra kiểu của từng thành phần mà không cần ép kiểu thủ công.

Bài học này đi từ cú pháp `<K, V>`, cách thiết kế lớp `Pair`, khởi tạo và suy luận kiểu, đến factory method, thao tác biến đổi, tính bất biến, hợp đồng `equals()/hashCode()` và quyết định khi nào nên dùng một kiểu cặp tổng quát.

## 1. Vì sao cần hai tham số kiểu?

Một hộp `Box<T>` chứa một giá trị thuộc kiểu `T`; còn một cặp `Pair<K, V>` mô tả **hai vị trí có vai trò và kiểu tách biệt**:

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

Đây là **một định nghĩa lớp minh họa** (chưa có `main`). Trong phần khai báo:

- `<K, V>` khai báo **hai tham số kiểu** thuộc lớp `Pair`, có thể xuất hiện ở trường, constructor và phương thức instance của lớp.
- `K key` biểu thị thành phần đầu mang kiểu `K`; `V value` biểu thị thành phần sau mang kiểu `V`.
- Khi viết `Pair<String, Integer>`, ta thay `K = String` và `V = Integer`; vì vậy `key()` có kiểu tĩnh `String`, còn `value()` có kiểu tĩnh `Integer`.
- Với `Pair<Long, Long>`, hai đối số kiểu giống nhau cũng hợp lệ. Có **hai tham số kiểu** không có nghĩa hai kiểu bắt buộc phải khác nhau.
- Tên `K` và `V` là quy ước. Nếu cặp chỉ biểu diễn “trái/phải” mà không có vai trò khóa–giá trị, `Pair<L, R>` hoặc tên có nghĩa rõ hơn có thể phù hợp.

Nếu dùng `Pair<Object, Object>` để lưu tùy ý, người đọc phải cast khi muốn dùng phương thức đặc thù của từng kiểu; sai kiểu có thể chỉ lộ ra lúc chạy. Nếu dùng `Pair<String, Integer>`, trình biên dịch đã biết kiểu của từng thành phần và phát hiện nhiều lỗi trước khi thực thi.

## 2. Cú pháp khai báo và sử dụng `Pair<K, V>`

Với ví dụ `Pair` ở trên, cú pháp khởi tạo gồm kiểu thực tế cho cả hai tham số:

```java
Pair<String, Integer> age = new Pair<>("Ada", 36);
Pair<Integer, String> code = new Pair<>(404, "Not Found");
Pair<String, String> labels = new Pair<>("vi", "Xin chào");
```

Các dòng trên cần đặt trong một phương thức hoặc vùng khai báo hợp lệ có thể nhìn thấy lớp `Pair`. Đây là **ba lần dùng cùng một định nghĩa lớp**, không tạo ba lớp nguồn khác nhau.

- **Thứ tự quan trọng:** `Pair<String, Integer>` khác `Pair<Integer, String>`. Đổi chỗ đối số kiểu đồng nghĩa đổi vai trò của các phương thức trả về.
- **Diamond operator `<>`:** tại `new Pair<>("Ada", 36)`, trình biên dịch suy luận `K`, `V` dựa vào kiểu đích và biểu thức đối số. Có thể viết đầy đủ `new Pair<String, Integer>("Ada", 36)` nhưng thường dài dòng.
- **Wrapper cho primitive:** `Integer` là kiểu tham chiếu, phù hợp với type argument. `36` là literal `int` và được boxing thành `Integer` khi cần. `Pair<String, int>` không hợp lệ trong Java 17.
- **Hai kiểu có quan hệ độc lập:** khai báo `Pair<String, Integer>` không nói rằng `String` phải kế thừa `Integer` hoặc ngược lại.
- **Dùng đúng kiểu trả về:** `String name = age.key(); Integer years = age.value();` hợp lệ; `String years = age.value();` không biên dịch.

Java generics thông thường dùng **type erasure**, nên `Pair<String, Integer>` và `Pair<Integer, String>` có cùng lớp `Pair` tại runtime; bộ tham số kiểu chủ yếu phục vụ kiểm tra kiểu ở compile time. Đừng dùng `instanceof Pair<String, Integer>` để thử kiểu cụ thể, vì phép thử kiểu tham số hóa đó không hợp lệ.

## 3. Bản thiết kế khuyến nghị: bất biến và API nhỏ

Một `Pair` có thể được viết theo kiểu mutable với setter, nhưng khi nó biểu diễn một giá trị kết hợp, thiết kế **immutable** thường dễ dự đoán và dễ dùng làm kết quả hoặc khóa:

1. `final class` ngăn lớp con thay đổi hợp đồng bằng override.
2. Các trường `private final` không cho gán lại tham chiếu sau khi xây dựng.
3. Không công khai setter.
4. Constructor thiết lập đầy đủ cả hai thành phần.
5. Accessor `key()`, `value()` trả đúng kiểu generic đã khai báo.
6. `equals` và `hashCode` so sánh hai thành phần theo giá trị và nhất quán với nhau.
7. `toString` giúp debug và log; tránh phụ thuộc vào format chuỗi như giao thức trao đổi dữ liệu lâu dài.

**Bất biến ở đây là bất biến nông (shallow immutability).** Nếu `V` là một `ArrayList<String>` có thể sửa, việc `Pair` giữ trường `final` không ngăn người gọi sửa nội dung danh sách đó. Nếu API cần bất biến sâu hơn, hãy giới hạn kiểu thành phần, tạo bản sao phòng vệ hoặc dùng đối tượng thành phần immutable phù hợp.

Cho phép `null` hay không là **quyết định thiết kế**, không phải quy tắc của `Pair<K, V>`. `new Pair<>(null, 3)` có thể hợp lệ nếu constructor không kiểm tra. Nếu nghiệp vụ yêu cầu cả hai thành phần không-null, dùng `Objects.requireNonNull(key, "key")` và `Objects.requireNonNull(value, "value")` trong constructor; mô tả rõ hợp đồng đó trong API.

## 4. Ví dụ Java 17 chạy độc lập: `Pair<K, V>` đầy đủ

Lưu toàn bộ chương trình sau vào tệp **`PairDemo.java`** (Java 17+):

```java
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public class PairDemo {
    public static final class Pair<K, V> {
        private final K key;
        private final V value;

        private Pair(K key, V value) {
            this.key = key;
            this.value = value;
        }

        public static <K, V> Pair<K, V> of(K key, V value) {
            return new Pair<>(key, value);
        }

        public K key() {
            return key;
        }

        public V value() {
            return value;
        }

        public Pair<V, K> swapped() {
            return Pair.of(value, key);
        }

        public <NEW_VALUE> Pair<K, NEW_VALUE> mapValue(
                Function<? super V, ? extends NEW_VALUE> mapper) {
            Objects.requireNonNull(mapper, "mapper");
            return Pair.of(key, mapper.apply(value));
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) {
                return true;
            }
            if (!(object instanceof Pair<?, ?> other)) {
                return false;
            }
            return Objects.equals(key, other.key)
                    && Objects.equals(value, other.value);
        }

        @Override
        public int hashCode() {
            return Objects.hash(key, value);
        }

        @Override
        public String toString() {
            return "(" + key + ", " + value + ")";
        }
    }

    public static void main(String[] args) {
        Pair<String, Integer> person = Pair.of("Ada", 36);
        Pair<Integer, String> reversed = person.swapped();
        Pair<String, String> ageText =
                person.mapValue(age -> age + " years");
        Pair<String, Integer> same = Pair.of("Ada", 36);
        Pair<String, Integer> different = Pair.of("Ada", 37);
        Pair<String, List<Integer>> scores =
                Pair.of("scores", List.of(2, 4, 6));

        System.out.println("person: " + person);
        System.out.println("key upper: " + person.key().toUpperCase());
        System.out.println("next age: " + (person.value() + 1));
        System.out.println("swapped: " + reversed);
        System.out.println("mapped: " + ageText);
        System.out.println("equal: " + person.equals(same));
        System.out.println("different: " + person.equals(different));
        System.out.println("nested: " + scores);
        System.out.println("same type: " + Pair.of(1, 2));
    }
}
```

Biên dịch và chạy trong thư mục chứa tệp:

```bash
javac PairDemo.java
java PairDemo
```

**Stdout dự kiến:**

```text
person: (Ada, 36)
key upper: ADA
next age: 37
swapped: (36, Ada)
mapped: (Ada, 36 years)
equal: true
different: false
nested: (scores, [2, 4, 6])
same type: (1, 2)
```

Giải thích kết quả theo từng cơ chế:

- `Pair.of("Ada", 36)` là **generic static factory method**. Trình biên dịch suy luận `K = String`, `V = Integer` (boxing `36` khi cần). Giá trị nhận được có kiểu `Pair<String, Integer>`.
- `person.key().toUpperCase()` hợp lệ vì `key()` có kiểu `String`. `person.value() + 1` unbox `Integer` thành `int` để cộng ra `37`.
- `swapped()` có kiểu trả về `Pair<V, K>`, tức `Pair<Integer, String>` trong trường hợp này. **Hoán đổi giá trị đi cùng hoán đổi vị trí tham số kiểu**; giữ `Pair<K, V>` ở kiểu trả về sẽ sai.
- `mapValue(...)` giữ nguyên `K` nhưng cho phép đổi `V` sang một kiểu mới `NEW_VALUE`. Lambda biến `36` thành `"36 years"`, nên kết quả là `Pair<String, String>`.
- `equals` kiểm tra nội dung cả hai thành phần, vì thế `person` bằng `same` nhưng khác `different`. `hashCode` nhất quán với `equals`; chương trình không in giá trị hash cụ thể vì không cần phụ thuộc cách biểu diễn đó.
- `Pair<String, List<Integer>>` minh họa **kiểu generic lồng nhau**: `K` là `String`, `V` là `List<Integer>`. Chương trình dùng `List.of(...)` vốn trả một danh sách không cho thay đổi phần tử bằng các lệnh sửa; nhưng bản thân kiểu `Pair` không đòi `V` phải là list immutable.
- `Pair.of(1, 2)` hợp lệ khi `K` và `V` cùng được suy luận là `Integer`. Hai vị trí vẫn độc lập về vai trò dù trùng kiểu.

Đoạn `if (!(object instanceof Pair<?, ?> other))` dùng *pattern matching for instanceof* có trong Java 17. `Pair<?, ?>` chỉ kiểm tra đối tượng thuộc `Pair` mà không giả định đối số kiểu runtime cụ thể. Việc so sánh từng thành phần bằng `Objects.equals` phù hợp khi thành phần có thể `null` và tránh ép kiểu không an toàn.

## 5. Static factory và giới hạn kiểu của thành viên `static`

Một factory như `Pair.of(...)` giúp khởi tạo gọn hơn mà vẫn giữ được kiểu ở cả hai vị trí. Chữ ký:

```java
public static <K, V> Pair<K, V> of(K key, V value) {
    return new Pair<>(key, value);
}
```

Đây là phương thức **trong lớp `Pair<K, V>`** của ví dụ đầy đủ, không phải một hàm Java tự do.

Cặp `<K, V>` sau `static` **khai báo hai tham số kiểu riêng của phương thức**. Nó không sử dụng trực tiếp `K`, `V` của instance `Pair<K, V>`, vì static method thuộc về lớp chứ không thuộc một đối tượng được tham số hóa. Việc phương thức đặt tên giống tham số lớp được phép; phạm vi khai báo là khác nhau. Nếu muốn tránh nhầm khi đọc, có thể khai báo factory là `static <A, B> Pair<A, B> of(A a, B b)`; ý nghĩa không đổi.

Mẫu sai về phạm vi (không biên dịch khi đặt trong `class Pair<K, V>`):

```java
// static Pair<K, V> create(K key, V value) { ... }
// Sai vì static không được dùng trực tiếp type parameters của lớp.
```

Mẫu đúng có phần khai báo type parameter của phương thức: `static <K, V> Pair<K, V> create(K key, V value)`.

Cú pháp gọi có thể viết tường minh bằng type witness `Pair.<String, Integer>of("Ada", 36)`, nhưng trong phần lớn tình huống `Pair.of("Ada", 36)` đã đủ để trình biên dịch suy luận. Một lưu ý quan trọng: **type inference phụ thuộc ngữ cảnh**. Nếu dùng `var pair = Pair.of("Ada", 36)`, kiểu được suy luận theo các biểu thức đối số; nếu cần hợp đồng rộng hơn (ví dụ `Pair<CharSequence, Number>`), hãy khai báo kiểu đích tường minh hoặc gọi factory với type witness phù hợp. Không phải mọi tình huống compiler đều tự đoán đúng dụng ý API.

## 6. Thao tác hai thành phần mà vẫn bảo toàn kiểu

Lợi ích lớn của nhiều tham số kiểu là **phương thức có thể thay đổi một thành phần mà giữ nguyên thành phần khác**:

| Phương thức | Kiểu trả về | Ý nghĩa |
|---|---|---|
| `K key()` | `K` | Đọc thành phần thứ nhất đúng kiểu |
| `V value()` | `V` | Đọc thành phần thứ hai đúng kiểu |
| `Pair<V, K> swapped()` | `Pair<V, K>` | Hoán đổi hai thành phần và hai tham số kiểu |
| `<R> Pair<K, R> mapValue(Function<? super V, ? extends R> f)` | `Pair<K, R>` | Giữ `K`, biến đổi riêng `V` |
| `<R> Pair<R, V> mapKey(Function<? super K, ? extends R> f)` | `Pair<R, V>` | Giữ `V`, biến đổi riêng `K` |

`mapKey` là **gợi ý thiết kế tương tự**, không nằm trong chương trình `PairDemo`. Nếu bổ sung vào thân lớp `Pair`, có thể cài đặt:

```java
public <NEW_KEY> Pair<NEW_KEY, V> mapKey(
        Function<? super K, ? extends NEW_KEY> mapper) {
    Objects.requireNonNull(mapper, "mapper");
    return Pair.of(mapper.apply(key), value);
}
```

Đoạn trên dùng `Function` và `Objects` đã import trong ví dụ đầy đủ, và **cần nằm bên trong lớp `Pair<K, V>`**. Với `Pair<String, Integer> p`, lời gọi `p.mapKey(String::length)` có kiểu `Pair<Integer, Integer>`.

Tại sao `Function<? super V, ? extends R>` thay vì chỉ `Function<V, R>`? Wildcard giúp chấp nhận rộng hơn: phép biến đổi có thể tiêu thụ một **supertype của `V`** và tạo một **subtype của `R`**. Mẫu “đầu vào `? super`, đầu ra `? extends`” hữu ích cho API nhận function. Nếu mới học, có thể bắt đầu bằng `Function<V, R>` rồi mở rộng khi đã hiểu PECS.

Cách viết phương thức phải làm đúng điều được thể hiện bởi kiểu trả về. Ví dụ sau là **lỗi thiết kế**:

```java
// Trong class Pair<K, V>:
// Pair<K, V> swapped() { return Pair.of(value, key); }
// Sai về nguyên tắc tổng quát: Pair.of(value, key) có cặp vai trò V, K.
```

Có thể tình cờ chấp nhận khi `K` và `V` giống nhau, nhưng khai báo `Pair<K, V>` không đúng cho mọi cách tham số hóa. Chữ ký chính xác là `Pair<V, K> swapped()`.

## 7. Kiểm tra `equals`, `hashCode`, `toString` cho Pair

Khi hai đối tượng `Pair` dùng như các **giá trị**, phép so sánh thường cần quan tâm đến **cả key và value**. Ví dụ `Pair.of("A", 1)` nên bằng một `Pair.of("A", 1)` khác nếu chúng có cùng ngữ nghĩa giá trị.

- `equals(Object)` kiểm tra đối tượng cùng loại cặp và dùng `Objects.equals` trên từng thành phần. `Objects.equals(x,y)` cho kết quả hợp lý nếu một hay cả hai thành phần `null`, đồng thời dựa vào `equals` của từng thành phần.
- `hashCode()` phải thỏa: nếu `a.equals(b)` thì `a.hashCode() == b.hashCode()`. `Objects.hash(key, value)` là một cách cài đặt; không nhất thiết mọi hash khác nhau đều chỉ ra đối tượng khác nhau.
- `toString()` nên biểu diễn dễ đọc hai phần tử; `(Ada, 36)` trong ví dụ là **format tự chọn**, không phải quy tắc bắt buộc của Java.

**Không nên chỉ kiểm tra `key` trong `equals`** nếu cặp được mô tả là hai thành phần có giá trị: khi đó `Pair.of("A", 1)` và `Pair.of("A", 2)` sẽ bị coi như bằng nhau dù khác nội dung. Trường hợp miền nghiệp vụ chỉ định danh bằng một trường nên dùng lớp/record domain có hợp đồng equality được đặt tên rõ ràng.

Nếu thành phần là mảng (ví dụ `Pair<String, int[]>`), `Objects.equals` với mảng sử dụng ngữ nghĩa `equals` của mảng (không so sánh từng phần tử như `Arrays.equals`). Nếu cần **deep equality** hoặc so sánh mảng theo nội dung, phải thiết kế riêng cho loại giá trị đó. Vì thế một generic Pair không thể tự biết mọi quy tắc so sánh đặc thù miền dữ liệu.

Mẫu immutable giúp giữ `hashCode` ổn định hơn khi thành phần cũng immutable. Nếu `V` là một danh sách mutable hoặc đối tượng có `equals/hashCode` thay đổi theo trạng thái, việc dùng `Pair` làm khóa `HashMap` vẫn có thể gây lỗi tra cứu sau khi sửa thành phần. `final` trên trường không giải quyết tính mutable bên trong.

## 8. Giới hạn generic: bất biến kiểu và wildcard

Java không cho suy ra `Pair<CharSequence, Number>` từ `Pair<String, Integer>` dù `String` thực hiện `CharSequence` và `Integer` là subtype của `Number`.

Ví dụ **không biên dịch**:

```java
Pair<String, Integer> exact = Pair.of("A", 1);
// Pair<CharSequence, Number> wider = exact; // Không hợp lệ.
```

Ở đây `Pair` là class có hai tham số kiểu **invariant**. Giải pháp phụ thuộc việc muốn làm với cặp:

```java
static void show(
        Pair<? extends CharSequence, ? extends Number> pair) {
    System.out.println(pair.key() + " = " + pair.value());
}
```

Đây là **phương thức minh họa**, cần đặt trong class nhìn thấy `Pair`. Tham số dạng `Pair<? extends CharSequence, ? extends Number>` có thể nhận `Pair<String, Integer>` và đọc qua giao diện kiểu rộng hơn. Trong ví dụ `Pair` bất biến, không có setter; với một Pair mutable, wildcard sẽ còn quyết định những giá trị nào có thể ghi an toàn.

**Không nhầm `Pair<?, ?>` với `Pair<Object, Object>`.** `Pair<?, ?>` chấp nhận một cặp có hai đối số kiểu chưa biết, phù hợp để quan sát như `Object`. `Pair<Object, Object>` là cặp có hai đối số kiểu cụ thể `Object`; hai khai báo không có cùng quan hệ gán.

Bound có thể đặt cho từng tham số khi một loại cặp đặc thù cần ràng buộc, chẳng hạn:

```java
class ScoredPair<K, V extends Number> {
    private final K key;
    private final V score;

    ScoredPair(K key, V score) {
        this.key = key;
        this.score = score;
    }

    double scoreAsDouble() {
        return score.doubleValue();
    }
}
```

Đây là **lớp minh họa riêng**, không nằm trong `PairDemo`. Bound `V extends Number` cho phép truy cập `doubleValue()` và ngăn type argument không thỏa `Number`. Không nên thêm bound chỉ vì tên tham số kiểu là `V`: một `Pair<K, V>` tổng quát có thể cần chứa bất kỳ reference type nào.

## 9. Khi nào dùng Pair, Map.Entry hoặc record?

`Pair<K, V>` phù hợp khi **hai giá trị thật sự được truyền/nhận cùng nhau** nhưng chưa cần thêm hợp đồng nghiệp vụ riêng. Tuy vậy, nó là một cấu trúc tổng quát nên tên `key`/`value` đôi khi làm lu mờ ý nghĩa thực tế, chẳng hạn tọa độ, khoảng thời gian hay thông tin của người dùng.

| Nhu cầu | Lựa chọn có thể phù hợp | Lưu ý |
|---|---|---|
| Cặp giá trị generic nhỏ cần API riêng | `Pair<K, V>` | Dễ kiểm soát hợp đồng bất biến, null, equals và biến đổi |
| Cặp gắn với một entry của map | `Map.Entry<K, V>` | Có ngữ nghĩa khóa–giá trị; `Map.entry(k,v)` tạo entry bất biến và **không chấp nhận null** |
| Chỉ lưu hai thành phần có tên cụ thể trong miền dữ liệu | `record Range(int start, int end)` hoặc record domain | Tên trường `start/end` có nghĩa hơn `key/value` |
| Cặp generic rất đơn giản trên Java 17+ | `record Pair<K, V>(K key, V value)` | Compiler sinh constructor, accessor, `equals`, `hashCode`, `toString`; record là bất biến nông |
| Nhiều trường và quy tắc nghiệp vụ | Lớp/record domain cụ thể | Đặt tên thuộc tính và validation theo đúng nghiệp vụ |

**Record generic** cho phép triển khai ngắn hơn. Ví dụ sau là **một tệp riêng** `Pair.java` và là phương án thay thế cho class `Pair<K, V>` trước đó:

```java
public record Pair<K, V>(K key, V value) {
    public static <K, V> Pair<K, V> of(K key, V value) {
        return new Pair<>(key, value);
    }

    public Pair<V, K> swapped() {
        return new Pair<>(value, key);
    }
}
```

`record` tự sinh accessor `key()`, `value()` và các phương thức giá trị dựa trên thành phần record. Với `Pair.of("A", 1)`, `toString()` do record sinh ra có định dạng kiểu `Pair[key=A, value=1]` (khác với `"(A, 1)"` của `PairDemo`). Không coi hai kiểu `Pair` này có định dạng xuất giống nhau khi thay đổi cách cài đặt.

Dù dùng class hay record, nếu cặp có ý nghĩa đặc thù và nhiều điều kiện bất biến, chẳng hạn `start <= end`, một `Range` với tên thành phần và kiểm tra dữ liệu cụ thể thường truyền đạt hợp đồng tốt hơn `Pair<Integer, Integer>`.

## 10. Cách thiết kế một API Pair dễ dùng

Trước khi đưa `Pair<K, V>` vào một thư viện, nên quyết định nhất quán các điểm sau:

1. **Vai trò:** `K` và `V` có thực sự là key/value không? Nếu là left/right, cân nhắc `Pair<L, R>`; nếu là thuộc tính domain, cân nhắc kiểu domain.
2. **Bất biến:** có cho cập nhật thành phần sau khi tạo không? Với value object, `private final` và không có setter thường phù hợp.
3. **Null:** chấp nhận `null` ở từng vị trí hay ném lỗi sớm bằng `Objects.requireNonNull`? Ghi rõ trong tài liệu API.
4. **Factory và constructor:** `Pair.of` giúp suy luận kiểu gọn; constructor private ngăn khởi tạo ngoài factory. Nếu muốn hỗ trợ `new Pair<>(...)` từ code người gọi, constructor phải có mức truy cập phù hợp.
5. **Equality:** hai thành phần cùng tham gia `equals/hashCode` hay còn có quy tắc domain? Tránh cài đặt nửa vời.
6. **Tính mutable của thành phần:** `final` không làm sâu các object bên trong; ghi rõ giới hạn hoặc dùng bản sao phòng vệ khi cần.
7. **Phép biến đổi:** nếu cho `swapped`, `mapKey`, `mapValue`, kiểu trả về phải biểu đạt chính xác vai trò kiểu trước/sau biến đổi.
8. **Type inference:** để người dùng thường gọi được `Pair.of("A", 1)`; dùng type witness hoặc kiểu đích tường minh ở trường hợp cần mở rộng kiểu.
9. **Không lạm dụng:** khi có ba, bốn thuộc tính hoặc cần các quy tắc nghiệp vụ, một class/record được đặt tên theo domain thường dễ bảo trì hơn các `Pair` lồng nhau.

Một lưu ý riêng về thiết kế thư viện: Java không cung cấp một lớp `Pair<K, V>` đa dụng trực tiếp trong `java.lang`. Tự xây class nhỏ khi có nhu cầu rõ ràng, hoặc dùng kiểu đã có của thư viện đang dùng. Tránh đưa thêm dependency chỉ để lấy một kiểu cặp đơn giản nếu nó không tạo giá trị khác.

## 11. Lỗi phổ biến

**Lỗi 1 — Dùng một type parameter cho cả hai vị trí khi muốn hai kiểu độc lập.**

```java
class SameTypePair<T> {
    T first;
    T second;
}
```

Lớp này buộc hai trường được nhìn qua cùng một `T` ở mỗi cách tham số hóa. Nếu nghiệp vụ muốn một chuỗi và một số với kiểu rõ ràng, `Pair<K, V>` diễn đạt tốt hơn. Một `SameTypePair<Object>` có thể giữ hai object khác kiểu nhưng làm mất thông tin kiểu cụ thể của từng trường.

**Lỗi 2 — Dùng primitive trong type argument:** `Pair<String, int>` không hợp lệ; dùng `Pair<String, Integer>`. Java tự boxing `int` khi cần trong ngữ cảnh khởi tạo hợp lệ.

**Lỗi 3 — Đổi thứ tự `K, V` khi khai báo kết quả:** `Pair<Integer, String>` không gán được sang `Pair<String, Integer>` chỉ vì đều là Pair. Đặc biệt kiểm tra chữ ký các phương thức như `swapped()`.

**Lỗi 4 — Cho rằng `Pair<String, Integer>` là subtype của `Pair<Object, Object>`.** Generic type invariant; dùng wildcard hoặc một kiểu API được thiết kế cho thao tác cần thiết.

**Lỗi 5 — Cố truy cập `K`, `V` của lớp từ static method mà không khai báo riêng.** Static factory phải tự viết `static <K, V> ...` (hoặc tên type parameter riêng khác).

**Lỗi 6 — Viết `equals` chỉ so key hoặc dùng `==` cho wrapper/string.** Nếu cần value equality, so từng thành phần bằng `Objects.equals` và viết `hashCode` nhất quán.

**Lỗi 7 — Tin rằng `final` làm thành phần mutable trở nên bất biến.** `Pair<String, List<Integer>>` có thể chứa một list mutable; người giữ tham chiếu tới list vẫn có thể sửa nó.

**Lỗi 8 — Quên kiểm tra null trong hàm biến đổi.** Nếu `mapper == null`, lời gọi `mapper.apply` ném `NullPointerException`; `Objects.requireNonNull(mapper, "mapper")` làm hợp đồng rõ ràng. Nếu `value` là null, mapper cũng phải xử lý null hoặc API cần cấm null ngay khi tạo.

**Lỗi 9 — Dùng Pair thay lớp domain có nghĩa.** `Pair<Integer, Integer>` không cho người đọc biết đâu là ngày bắt đầu, đâu là ngày kết thúc; kiểu `DateRange` hoặc `Range` thường biểu đạt và kiểm tra dữ liệu tốt hơn.

**Lỗi 10 — Nghĩ type parameter tồn tại nguyên vẹn tại runtime.** Kiểm tra `pair instanceof Pair<?, ?>` hợp lệ nhưng không xác minh từng type argument; không dùng raw Pair và unchecked cast để né kiểm tra biên dịch.

## 12. Bài tập tự kiểm tra có đáp án

1. **`Pair<String, Integer>` có mấy type parameter và mấy type argument?** Định nghĩa `Pair<K, V>` khai báo hai type parameter `K`, `V`; tại cách dùng có hai type argument `String`, `Integer`.
2. **`Pair<Integer, Integer>` có hợp lệ không?** Có. Hai tham số kiểu độc lập không bắt buộc khác kiểu.
3. **`Pair<String, Integer> p = Pair.of("A", 4);` có cần cast khi gọi `p.value()` không?** Không. Compiler biết kiểu trả về `Integer`; gán vào `int` sẽ unbox.
4. **Nếu `p` là `Pair<String, Integer>`, `p.swapped()` thuộc kiểu nào?** `Pair<Integer, String>`. Đảo cả vị trí và quan hệ kiểu.
5. **Tại sao factory viết `static <K, V> Pair<K, V> of(...)`?** Vì static method cần khai báo type parameter riêng, không dùng trực tiếp type parameter ở mức instance của lớp.
6. **`Pair<String, Integer>` có gán cho biến `Pair<CharSequence, Number>` được không?** Không, vì generics invariant; khi chỉ cần đọc có thể nhận bằng `Pair<? extends CharSequence, ? extends Number>`.
7. **`Pair.of("x", 1).equals(Pair.of("x", 1))` trả gì với triển khai ở trên?** `true`, vì cả hai thành phần tương ứng bằng nhau theo `Objects.equals`.
8. **`new Pair<>(...)` có gọi được ngoài class `Pair` trong chương trình đầy đủ không?** Không vì constructor private; dùng `Pair.of(...)`.
9. **`record Pair<K, V>(K key, V value)` có tự động làm `List` bên trong immutable không?** Không; record bất biến nông, các đối tượng thành phần có thể vẫn mutable.
10. **Khi dữ liệu là giá trị “tọa độ x và y”, dùng `Pair<Double, Double>` hay `Point(double x, double y)`?** Cả hai có thể lưu dữ liệu, nhưng `Point` được đặt tên đúng domain, rõ ý nghĩa thành phần và có chỗ diễn đạt validation/hành vi tốt hơn.

Ghi nhớ: **`Pair<K, V>` là một lớp generic duy nhất có hai “ô kiểu” độc lập**. Thiết kế tốt là đặt đúng vai trò `K`/`V`, trả đúng kiểu ở từng accessor, giữ quan hệ kiểu khi hoán đổi hoặc biến đổi, và bảo đảm hợp đồng giá trị rõ ràng. Generics giúp compiler giữ an toàn kiểu; thiết kế API quyết định cặp đó có ý nghĩa và có dễ dùng hay không.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="generic-classes-interfaces-004">Khai báo giao diện generic và hợp đồng theo kiểu</a>

<details>
<summary>Xem chi tiết</summary>

# Khai báo giao diện generic và hợp đồng theo kiểu trong Java

**Generic interface** là giao diện khai báo một hoặc nhiều **tham số kiểu** ngay sau tên, ví dụ `interface Repository<ID, T>`. Interface này mô tả các thao tác có liên hệ kiểu: khóa định danh phải có kiểu `ID`, còn dữ liệu được lưu và trả về có kiểu `T`. Khi sử dụng `Repository<Long, User>`, trình biên dịch kiểm tra rằng phương thức lưu nhận `Long` và `User`, trong khi phương thức tìm kiếm trả về `Optional<User>`.

Giao diện generic đặc biệt hữu ích khi nhiều loại dữ liệu chia sẻ **cùng một hợp đồng**, còn kiểu đầu vào và đầu ra cần giữ nhất quán. Bài này tập trung vào cú pháp khai báo, triển khai bằng `implements`, kế thừa qua `extends`, các loại phương thức được phép khai báo, giới hạn kiểu và những lỗi phổ biến. Ví dụ hoàn chỉnh sử dụng Java 17 trở lên.

## 1. Tại sao cần interface generic?

Giả sử ứng dụng có kho người dùng được đánh ID bằng `Long`, kho sản phẩm được đánh mã bằng `String`. Nếu viết hai interface độc lập, các chữ ký dễ trùng lặp:

```java
interface UserStore {
    void save(Long id, User user);
    User findById(Long id);
}

interface ProductStore {
    void save(String code, Product product);
    Product findById(String code);
}
```

Các đoạn ngắn trong bài là **mã minh họa nằm trong một ngữ cảnh phù hợp**; `User` và `Product` ở trên là các kiểu nghiệp vụ giả định, không phải tệp Java hoàn chỉnh.

Ta có thể mô tả mối quan hệ chung bằng hai tham số kiểu:

```java
interface Repository<ID, T> {
    void save(ID id, T entity);
    java.util.Optional<T> findById(ID id);
}
```

`ID` là kiểu của khóa; `T` là kiểu thực thể. Sử dụng interface bằng cách truyền **đối số kiểu**:

```java
Repository<Long, User> users;
Repository<String, Product> products;
```

Trình biên dịch biết rằng `users.save` nhận `(Long, User)`, còn `products.save` nhận `(String, Product)`. Tên `ID` và `T` chỉ là tham số kiểu của Java, không phải hai biến dữ liệu hay các class được tạo ra ở runtime.

Một phiên bản không generic sẽ làm yếu hợp đồng:

```java
interface WeakRepository {
    void save(Object id, Object entity);
    Object findById(Object id);
}
```

Ở đây, caller phải tự ép kiểu giá trị tìm được và có thể lẫn kiểu khóa với kiểu dữ liệu. Generics giúp chuyển nhiều lỗi kiểu từ lúc chạy sang **lúc biên dịch**. Tuy nhiên, chúng không tự kiểm tra quy tắc nghiệp vụ như ID có tồn tại, entity đã hợp lệ hay người gọi có quyền xem dữ liệu.

## 2. Cú pháp khai báo và cách đọc hợp đồng

Dạng một tham số:

```java
interface Holder<T> {
    void set(T value);
    T get();
}
```

Dạng nhiều tham số:

```java
interface Dictionary<K, V> {
    void put(K key, V value);
    V get(K key);
}
```

| Thành phần | Ý nghĩa |
| --- | --- |
| `interface Holder<T>` | **Khai báo** tham số kiểu `T` trong phạm vi interface |
| `Holder<String>` | **Sử dụng** interface với đối số kiểu `String` |
| `interface Dictionary<K,V>` | Khai báo hai tham số kiểu, với vai trò khóa và giá trị |
| `Dictionary<Long,String>` | Ràng buộc `K = Long`, `V = String` cho tham chiếu cụ thể |
| `new MemoryDictionary<>()` | Toán tử diamond `<>` nhờ ngữ cảnh để suy luận các đối số kiểu của constructor |

Trong `Holder<String>`, trình biên dịch xem `get()` là phương thức trả `String`; trong `Holder<Integer>`, phương thức ấy trả `Integer`. Không cần ép `Object` một cách thủ công khi gọi qua tham chiếu có kiểu đầy đủ.

Các tên thông dụng là `T` (type), `E` (element), `K` (key), `V` (value). Nếu bài toán có nhiều kiểu, một tên diễn tả rõ vai trò như `ID` cũng hợp lệ. Không dùng tên có ý nghĩa nhập nhằng khiến caller khó suy luận hợp đồng.

**Cần nhớ:** tham số generic Java thông thường là kiểu tham chiếu. `Repository<long, User>` không hợp lệ; dùng `Repository<Long, User>`. Boxing/unboxing có thể xảy ra khi truyền số primitive tới vị trí nhận wrapper.

## 3. Chương trình Java 17+ hoàn chỉnh

Chương trình sau minh họa cùng một interface cho kho người dùng (`Long` → `User`) và kho tồn kho (`String` → `Integer`). Hợp đồng đọc được tách khỏi hợp đồng ghi; phương thức `exists` là phương thức `default`. Giao diện `Converter<I,O>` minh họa một phương thức generic riêng bên trong interface generic.

Lưu đúng tên tệp **`Main.java`**:

```java
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class Main {
    record User(long id, String name) {}

    interface ReadRepository<ID, T> {
        Optional<T> findById(ID id);

        default boolean exists(ID id) {
            return findById(id).isPresent();
        }
    }

    interface Repository<ID, T> extends ReadRepository<ID, T> {
        void save(ID id, T entity);
    }

    static final class InMemoryRepository<ID, T>
            implements Repository<ID, T> {
        private final Map<ID, T> entries = new LinkedHashMap<>();

        @Override
        public void save(ID id, T entity) {
            entries.put(
                    Objects.requireNonNull(id, "id"),
                    Objects.requireNonNull(entity, "entity"));
        }

        @Override
        public Optional<T> findById(ID id) {
            return Optional.ofNullable(entries.get(id));
        }
    }

    @FunctionalInterface
    interface Converter<I, O> {
        O convert(I input);

        default <R> Converter<I, R> andThen(
                Converter<? super O, ? extends R> next) {
            Objects.requireNonNull(next, "next");
            return input -> next.convert(convert(input));
        }
    }

    public static void main(String[] args) {
        Repository<Long, User> users = new InMemoryRepository<>();
        users.save(101L, new User(101L, "Lan"));

        System.out.println("user: "
                + users.findById(101L)
                        .map(User::name)
                        .orElse("not found"));
        System.out.println("exists 999: " + users.exists(999L));

        Repository<String, Integer> stock = new InMemoryRepository<>();
        stock.save("SKU-01", 7);
        System.out.println("stock: "
                + stock.findById("SKU-01").orElse(-1));

        Converter<String, Integer> count = String::length;
        Converter<String, String> label =
                count.andThen(n -> "length=" + n);

        System.out.println("length: " + count.convert("Java"));
        System.out.println("label: " + label.convert("Java"));

        // users.save("101", new User(101L, "Lan"));
        // Sai kiểu ID: Repository<Long,User> yêu cầu Long.
    }
}
```

Chạy với JDK 17 trở lên:

```bash
javac -encoding UTF-8 Main.java
java Main
```

Stdout:

```text
user: Lan
exists 999: false
stock: 7
length: 4
label: length=4
```

### Tại sao chương trình này kiểm tra được hợp đồng kiểu?

- `Repository<Long,User>` nghĩa là `save` yêu cầu một `Long` và một `User`; `findById` trả `Optional<User>`. Dòng truyền `"101"` (kiểu `String`) bị để comment vì **không biên dịch** nếu bật lên.
- `Repository<String,Integer>` dùng **cùng định nghĩa** `Repository<ID,T>` và **cùng class triển khai** `InMemoryRepository<ID,T>`, nhưng đối số kiểu khác; `findById("SKU-01")` trả `Optional<Integer>`.
- `InMemoryRepository<ID,T>` giữ hai tham số kiểu khi `implements Repository<ID,T>`; nó lưu dữ liệu trong `Map<ID,T>` và dùng `@Override` để trình biên dịch kiểm tra rằng chữ ký thực thi đúng interface.
- `ReadRepository<ID,T>` định nghĩa API đọc; `Repository<ID,T>` kế thừa qua `extends`. Phương thức `default exists(ID)` hoạt động theo đúng kiểu ID của repository đã tham số hóa.
- `Converter<I,O>` giữ quan hệ kiểu đầu vào và kiểu đầu ra. `count` có kiểu `Converter<String,Integer>`; `count.andThen(...)` tạo `Converter<String,String>` bằng một bước biến đổi kết quả từ `Integer` sang `String`.

`InMemoryRepository` chỉ là ví dụ chạy trong bộ nhớ, không đại diện cho repository truy cập database, không tự bảo đảm transaction hoặc an toàn đa luồng. Quy tắc không cho lưu khóa và entity `null` đến từ `Objects.requireNonNull` trong **implementation**, không phải do cú pháp `<ID,T>`.

## 4. Hai kiểu triển khai interface generic

### 4.1. Lớp triển khai vẫn giữ tham số kiểu

```java
interface Store<K, V> {
    void put(K key, V value);
    V get(K key);
}

class MemoryStore<K, V> implements Store<K, V> {
    private final java.util.Map<K, V> data =
            new java.util.HashMap<>();

    @Override
    public void put(K key, V value) {
        data.put(key, value);
    }

    @Override
    public V get(K key) {
        return data.get(key);
    }
}
```

`MemoryStore<K,V>` tiếp tục là class generic. Ta có thể dùng `Store<String,Integer>` hay `Store<Long,User>` mà không phải thay cấu trúc class. Chữ ký của `put` và `get` phải phù hợp với đúng `K`, `V` đã khai báo.

### 4.2. Lớp triển khai cố định kiểu cụ thể

```java
class StringIntStore implements Store<String, Integer> {
    private final java.util.Map<String, Integer> data =
            new java.util.HashMap<>();

    @Override
    public void put(String key, Integer value) {
        data.put(key, value);
    }

    @Override
    public Integer get(String key) {
        return data.get(key);
    }
}
```

`StringIntStore` không có `<K,V>` trên tên class; nó chọn cố định `String` và `Integer` lúc khai báo `implements Store<String,Integer>`. Vì thế, implementation phải cung cấp các method tương ứng:

- `public void put(String, Integer)`
- `public Integer get(String)`

Không thể lặng lẽ thay `put(String,Integer)` bằng `put(String,String)` rồi coi là đã triển khai phương thức bắt buộc; class sẽ không còn đáp ứng hợp đồng.

**Đặc điểm chung:** phương thức của interface được khai báo abstract là `public` (trừ những trường hợp đặc biệt của các loại phương thức khác), nên method override trong class phải là `public`. `@Override` giúp phát hiện sai tên, sai tham số và các lỗi làm cho method không còn thực sự override.

Nếu class chỉ thực hiện một phần hợp đồng abstract, class đó phải được khai báo `abstract` để class con có thể bổ sung implementation.

## 5. Interface generic kế thừa interface khác

Một interface có thể **kế thừa** một hoặc nhiều interface, qua `extends`, đồng thời giữ hoặc cố định các đối số kiểu.

**Giữ nguyên kiểu giữa các tầng:**

```java
interface Reader<ID, T> {
    java.util.Optional<T> find(ID id);
}

interface Writer<ID, T> {
    void save(ID id, T item);
}

interface ReadWriteStore<ID, T>
        extends Reader<ID, T>, Writer<ID, T> {
}
```

Với `ReadWriteStore<Long, User>`, `find` nhận `Long` trả `Optional<User>`, còn `save` nhận `Long,User`. Mối quan hệ kiểu được truyền xuyên qua từng interface, không cần viết lại tất cả phương thức.

**Cố định đối số kiểu ở interface con:**

```java
interface IdLookup<T> {
    T find(long id);
}

interface UserLookup extends IdLookup<User> {
}
```

`UserLookup` không còn tham số kiểu; nó chọn `T = User`. Lớp implements `UserLookup` phải thực hiện `User find(long id)` khi không có implementation thích hợp được kế thừa.

**Bổ sung hành vi mặc định sau khi chuyên biệt hóa:**

```java
interface Sender<T> {
    void send(T message);
}

interface TextSender extends Sender<String> {
    default void sendUpperCase(String value) {
        send(value.toUpperCase(java.util.Locale.ROOT));
    }
}
```

`TextSender` kế thừa hợp đồng `send(String)` và bổ sung một thao tác cụ thể cho chuỗi. Nó không tự định nghĩa đích gửi; lớp triển khai `TextSender` vẫn phải cung cấp `send(String)`.

Interface có thể kế thừa nhiều interface khi chữ ký tương thích. Nếu hai interface cung cấp các phương thức `default` xung đột, lớp triển khai thường phải override để giải quyết rõ ràng. Không thể tùy tiện thừa hưởng hai cách tham số hóa khác nhau của cùng một generic interface, như `Comparable<String>` và `Comparable<Integer>`, vì có thể xung đột do type erasure.

## 6. Phân biệt type parameter của interface và của method

### 6.1. `T` khai báo trên interface

```java
interface Box<T> {
    void set(T value);
    T get();
}
```

Một instance được xem qua `Box<String>` sử dụng `T = String` ở cả hai method; `Box<Integer>` sử dụng `T = Integer`. `T` thuộc **toàn bộ hợp đồng instance**.

### 6.2. Method tự khai báo `<R>`

```java
interface Mapper<T> {
    <R> R map(
            T source,
            java.util.function.Function<? super T, ? extends R> function);
}
```

`T` được chọn khi sử dụng `Mapper<T>`. Ngược lại, `R` có thể khác giữa các lời gọi của `map`. Dấu `<R>` **phải nằm trước kiểu trả về** `R`; nó là phần khai báo một method generic riêng.

Nếu chỉ cần một thuật toán generic ở đúng một method và không cần giữ cùng `T` xuyên các method, có thể khai báo method-level generic trong một interface không generic:

```java
interface Identity {
    <R> R same(R value);
}
```

Tránh đặt tên phương thức trùng tên tham số kiểu của interface nếu việc đó che khuất ý nghĩa của một tham số kiểu khác; chẳng hạn thêm một `<T>` mới ở cấp method trong `interface Box<T>` sẽ tạo một `T` riêng, dễ làm người đọc hiểu nhầm.

### 6.3. Method `default` dùng kiểu của interface

```java
interface Matcher<T> {
    boolean matches(T value);

    default boolean rejects(T value) {
        return !matches(value);
    }
}
```

`default` có thân phương thức, được lớp triển khai kế thừa hoặc override khi cần. `rejects` vẫn nhận đúng `T`. Đây là cách chia sẻ hành vi mặc định mà không buộc mọi implementation sao chép cùng một đoạn logic.

Trong `Main.java`, `ReadRepository<ID,T>` có `default boolean exists(ID id)` gọi `findById(ID)`. Type parameter không thay đổi giữa hai lời gọi, và implementation không phải viết lại `exists`.

### 6.4. Method `static` tự khai báo type parameter khi cần

Method static của generic interface không thể sử dụng trực tiếp tham số kiểu `T` thuộc instance interface:

```java
interface Factory<T> {
    T create();

    static <R> Factory<R> of(
            java.util.function.Supplier<? extends R> source) {
        java.util.Objects.requireNonNull(source);
        return source::get;
    }
}
```

Ở `of`, `<R>` là tham số kiểu **của phương thức static**, còn `T` thuộc hợp đồng `Factory<T>` khi sử dụng một factory instance. `Factory.of(() -> "ready")` có thể suy luận kiểu `Factory<String>`.

Đoạn giả định sau là lỗi:

```java
// Sai: T chỉ được khai báo trên Factory<T>.
// static T createDefault() { ... }
```

Gọi static method bằng tên interface (`Factory.of(...)`), không gọi như một instance method được kế thừa qua object implementing interface.

## 7. Functional interface vẫn có thể generic

Functional interface có một phương thức abstract chức năng, là target type cho lambda hoặc method reference.

```java
@FunctionalInterface
interface Converter<I, O> {
    O convert(I input);
}
```

Các đối số kiểu đảm bảo lambda được kiểm tra đúng chữ ký:

```java
Converter<String, Integer> count = String::length;
Integer size = count.convert("Java");
```

`String::length` phù hợp vì nhận chuỗi và trả `int` (autobox thành `Integer`). Nếu viết:

```java
// Không biên dịch:
// Converter<String, Integer> wrong = text -> text.toUpperCase();
```

thì kết quả lambda là `String` trong khi `O = Integer`, nên trình biên dịch từ chối.

`@FunctionalInterface` vẫn cho phép `default` và `static` methods, vì các phương thức này không được tính như những abstract method bổ sung trong hợp đồng SAM (single abstract method). Ví dụ `Converter<I,O>` trong chương trình `Main.java` vừa có abstract `convert`, vừa có default `andThen`.

**Kết hợp các converter:** phần chữ ký

```java
default <R> Converter<I, R> andThen(
        Converter<? super O, ? extends R> next)
```

cho phép kết nối đầu ra kiểu `O` của bước thứ nhất với đầu vào mà bước thứ hai chấp nhận, rồi trả về converter kiểu `I -> R`. `? super O`/`? extends R` là wildcard diễn đạt khả năng nhận input và tạo output tương thích; chúng giúp API linh hoạt hơn mà vẫn được kiểm tra kiểu.

## 8. Ràng buộc kiểu bằng `extends`

Không phải lúc nào mọi kiểu `T` đều phù hợp. Nếu implementation cần gọi một method cụ thể trên entity, generic interface có thể khai báo **bound**:

```java
interface Identified<ID> {
    ID id();
}

interface EntityStore<ID, T extends Identified<ID>> {
    void save(T entity);
    java.util.Optional<T> findById(ID id);
}
```

`T extends Identified<ID>` bảo đảm kiểu thực thể có method `id()` trả đúng loại ID. Một record có thể đáp ứng ràng buộc:

```java
record Ticket(Long id, String title) implements Identified<Long> {}

class TicketStore implements EntityStore<Long, Ticket> {
    private final java.util.Map<Long, Ticket> values =
            new java.util.HashMap<>();

    @Override
    public void save(Ticket ticket) {
        values.put(ticket.id(), ticket);
    }

    @Override
    public java.util.Optional<Ticket> findById(Long id) {
        return java.util.Optional.ofNullable(values.get(id));
    }
}
```

Nếu một kiểu chỉ implements `Identified<String>`, nó không được chọn làm `T` trong `EntityStore<Long,T>` trừ khi cũng thỏa mãn bound thích hợp. Bound làm hợp đồng **mạnh hơn**: không chỉ “ID có kiểu `Long`”, mà thực thể phải có khả năng cung cấp ID theo cùng kiểu.

Có thể khai báo nhiều bound khi thực sự cần, ví dụ `T extends Identified<ID> & java.io.Serializable`; nếu danh sách có class bound, class phải xuất hiện đầu tiên rồi mới đến interface bound.

**Không thêm bound để làm mã trông phức tạp hơn.** Nếu method không sử dụng khả năng được bound cung cấp, cân nhắc giữ hợp đồng đơn giản.

## 9. Hợp đồng kiểu không đồng nghĩa với hợp đồng hành vi

`Repository<ID,T>` giúp trả lời:

- `save` phải nhận kiểu khóa và entity nào?
- `findById` trả kiểu dữ liệu nào?
- Lớp triển khai có override đúng các method không?

Nhưng interface **không tự quyết định**:

- `save` ghi đè entity cũ hay từ chối trùng ID;
- `findById` trả `Optional.empty()` khi không có dữ liệu hay phát sinh lỗi hạ tầng;
- có cho phép khóa hoặc giá trị `null` không;
- dữ liệu có được ghi bền vững, có transaction hoặc được bảo vệ trước truy cập đồng thời không;
- quyền đọc và quyền ghi của người dùng được áp dụng thế nào;
- ID được truyền vào có khớp thuộc tính ID bên trong entity không.

Tác giả API phải mô tả và thực thi các quy tắc ấy trong contract/documentation, validation và implementation. Generic interface là **hợp đồng kiểu được trình biên dịch hiểu**, còn semantics nghiệp vụ cần cam kết riêng.

Từ góc nhìn thiết kế, nên dùng tham chiếu kiểu interface khi caller chỉ cần hợp đồng đó:

```java
Repository<Long, User> users = new InMemoryRepository<>();
```

Tác dụng là dễ thay implementation phù hợp mà caller không phụ thuộc trực tiếp vào cấu trúc `Map` nội bộ. Tuy vậy, việc có nhiều interface chỉ mang tính hình thức mà không nêu được một hợp đồng chung thường làm dự án khó đọc hơn.

## 10. Invariance và wildcard khi làm việc qua interface

Generics trong Java **bất biến** theo mặc định. Giả sử `Manager extends Employee`:

```java
Repository<Long, Manager> managers = null;
// Repository<Long, Employee> employees = managers; // Không biên dịch.
```

Lý do: nếu phép gán hợp lệ, caller qua tham chiếu `Repository<Long,Employee>` có thể lưu một `Employee` không phải `Manager` vào kho vốn chỉ chứa `Manager`.

Nếu chỉ đọc, có thể cần một kiểu biến linh hoạt hơn:

```java
ReadRepository<Long, ? extends Employee> readOnly = null;
```

Một `ReadRepository<Long,Manager>` phù hợp để gán cho kiểu đọc rộng hơn ấy, vì biến `readOnly` chỉ biết kết quả là một subtype chưa xác định của `Employee`. Kết quả `findById` có quan hệ với `Optional<? extends Employee>`, nhưng không thể tùy tiện gán thành `Optional<Employee>` do tính bất biến của `Optional`.

Khi thiết kế API, `? extends T` thường hữu ích tại nơi **sản xuất/đọc** `T`; `? super T` thường hữu ích khi **tiêu thụ/ghi** `T`. Quy tắc này định hướng cách dùng, không thay thế việc kiểm tra chữ ký thực tế của từng method.

Raw type làm mất nhiều khả năng kiểm tra:

```java
Repository raw = new InMemoryRepository();
```

Dùng `raw.save(...)` có thể biên dịch cùng cảnh báo `unchecked`, mở đường cho sai kiểu ở runtime. Ưu tiên `Repository<Long,User>` hoặc khai báo có type parameter thích hợp. `Repository<?,?>` tốt hơn raw type khi chỉ muốn giữ một tham chiếu với kiểu không biết trước, nhưng vì chưa biết các kiểu đối số nên không thể tùy ý gọi `save` với giá trị bất kỳ.

## 11. Type erasure và hạn chế ở runtime

Java triển khai generics chủ yếu bằng **type erasure**. Trình biên dịch kiểm tra ràng buộc lúc compile, sau đó phần lớn thông tin đối số kiểu không còn ở object runtime. `InMemoryRepository<Long,User>` và `InMemoryRepository<String,Integer>` đều dùng cùng một class runtime `InMemoryRepository`, chứ không tạo một class JVM mới cho từng cặp kiểu.

Không thể viết điều kiện sau để kiểm tra đầy đủ ID và entity:

```java
// Không biên dịch:
// if (value instanceof Repository<Long, User>) { ... }
```

Cú pháp `value instanceof Repository<?, ?>` có thể kiểm tra đối tượng thuộc loại interface đó, nhưng **không xác minh** tham số thực sự là `Long` và `User`.

Nếu code nhận dữ liệu từ JSON, reflection, raw collection hoặc thư viện cũ, generic type không thay thế validation và kiểm tra kiểu ở ranh giới. Các thư viện JSON có thể cung cấp type reference hoặc schema cho kiểu tham số hóa; API cụ thể phụ thuộc thư viện.

### Bridge methods

Khi lớp triển khai một method generic bằng kiểu cụ thể, compiler đôi khi tạo **bridge method tổng hợp** để giữ đúng cơ chế đa hình sau erasure:

```java
interface Processor<T> {
    T process(T value);
}

class StringProcessor implements Processor<String> {
    @Override
    public String process(String value) {
        return value.trim();
    }
}
```

Bạn chỉ cần triển khai `process(String)`; không tự viết bridge method. Kiến thức này giải thích tại sao khi xem bytecode hoặc Reflection có thể thấy method tổng hợp ngoài method đã khai báo.

### Hai overload chỉ khác đối số kiểu có thể trùng chữ ký

```java
// Không biên dịch:
// interface Bad {
//     void handle(java.util.List<String> names);
//     void handle(java.util.List<Integer> numbers);
// }
```

Sau erasure, cả hai có tham số `List`; Java không thể phân biệt hai overload đó chỉ bằng đối số generic. Cách xử lý là đổi tên method theo ý nghĩa, thiết kế một method generic thích hợp hoặc dùng hai loại tham số thật sự khác nhau.

Một class cũng không thể tùy tiện thực hiện cùng một generic interface ở hai kiểu đối số khác nhau khi dẫn đến xung đột erasure, ví dụ vừa `Comparable<String>` vừa `Comparable<Integer>`.

## 12. Lỗi khai báo và triển khai thường gặp

**Lỗi 1 — dùng `T` khi chưa khai báo:**

```java
// Sai vì T chưa có trong phạm vi:
// interface Broken { T get(); }

// Đúng:
interface Holder<T> {
    T get();
}
```

**Lỗi 2 — quên cố định hoặc chuyển tiếp đối số kiểu trên `implements`:**

```java
// Đúng khi class cũng generic:
class GenericHolder<T> implements Holder<T> {
    private T value;

    @Override
    public T get() {
        return value;
    }
}
```

Với class không generic, phải chọn kiểu cụ thể, ví dụ `implements Holder<String>`. Chỉ viết raw `implements Holder` đánh mất type information và thường gây cảnh báo/khó kiểm tra.

**Lỗi 3 — implementation sai method signature hoặc giảm visibility:** nếu `implements Holder<String>`, phương thức `get()` cần trả `String` hoặc một kiểu trả về tương thích theo quy tắc override, và phải công khai `public`. Không được đổi thành method `private`, cũng không được coi một overload nhận đối số khác là phương thức override đúng.

**Lỗi 4 — đặt type parameter nhầm cấp:** `<R>` trên method cho phép nhiều kiểu kết quả ở nhiều lời gọi; `<T>` trên interface ràng buộc nhiều method theo cùng kiểu. Chọn cấp khai báo đúng quan hệ.

**Lỗi 5 — dùng static method như instance method:** `static` trong interface phải gọi qua tên interface khai báo; type parameter instance của interface không thể tự xuất hiện trong static signature.

**Lỗi 6 — kiểm tra generic type bằng `instanceof` cụ thể:** do erasure, `instanceof Repository<Long,User>` không hợp lệ. Dùng `Repository<?,?>` để kiểm tra dạng interface ở runtime, rồi validate dữ liệu thực nếu cần.

**Lỗi 7 — lạm dụng kiểu `Object` hoặc cast unchecked:** caller mất bảo đảm kiểu, lỗi có thể chuyển từ compile time sang runtime. Tránh ép `(T)` chỉ để làm API trông generic khi không có căn cứ bảo đảm.

**Lỗi 8 — coi interface có thể khởi tạo trực tiếp:** không thể viết `new Repository<Long,User>()` nếu không có thân anonymous class triển khai đủ method. Cần instance của một class implements interface hoặc một cách tạo instance hợp lệ khác.

**Lỗi 9 — bỏ qua hành vi mặc định và quy tắc null:** method `default` có logic cụ thể và cần được đánh giá theo semantics của implementation. `Optional<T>` cũng không tự khiến mọi thao tác `save` hợp lệ: null, quyền truy cập hoặc uniqueness là kiểm tra khác.

## 13. Kiểm tra kiến thức

**Câu 1.** Với `Repository<String, Integer> stock`, `save` và `findById` có kiểu như thế nào?

**Đáp án:** `save(String, Integer)`; `findById(String)` trả `Optional<Integer>`. Kiểu này được suy ra ngay từ hai type arguments của biến `stock`.

**Câu 2.** Vì sao `class X<T> implements Holder<T>` hợp lệ nhưng `class X implements Holder<T>` không hợp lệ nếu `T` không tồn tại trong phạm vi?

**Đáp án:** `X<T>` khai báo `T` ở cấp class; còn `class X` không khai báo type parameter và không có nguồn nào khác cung cấp `T`. Phải giữ `<T>` ở class hoặc thay `T` bằng kiểu cụ thể.

**Câu 3.** Interface có thể vừa `@FunctionalInterface` vừa có phương thức `default` không?

**Đáp án:** Có. Functional interface cần thỏa quy tắc một abstract method chức năng; default và static methods không tính như abstract method bổ sung. Trong ví dụ, `Converter<I,O>` có `convert` abstract và `andThen` default.

**Câu 4.** `Repository<Long,Manager>` có gán thẳng cho `Repository<Long,Employee>` nếu `Manager` kế thừa `Employee` không?

**Đáp án:** Không, vì generics mặc định bất biến. Phép gán sẽ cho phép bên nhận ghi `Employee` không phải `Manager` vào repository dành cho `Manager`.

**Câu 5.** Tại sao `EntityStore<ID,T extends Identified<ID>>` mạnh hơn `Repository<ID,T>`?

**Đáp án:** Bound đòi hỏi `T` có khả năng cung cấp ID cùng kiểu `ID`, nên implementation có thể gọi `entity.id()` và nhận về ID đúng kiểu. `Repository<ID,T>` không tự ràng buộc thực thể có method như vậy.

**Câu 6.** Interface generic có thể xác nhận mọi đối số kiểu lúc runtime bằng `instanceof` không?

**Đáp án:** Không. Java generics chủ yếu dùng type erasure; có thể kiểm tra `Repository<?,?>`, nhưng không thể kiểm tra `Repository<Long,User>` bằng `instanceof` thông thường. Dữ liệu từ bên ngoài vẫn cần validation theo schema/hợp đồng thích hợp.

## Kết luận

Giao diện generic cho phép mô tả hợp đồng **theo quan hệ kiểu**, ví dụ `Repository<ID,T>`, thay vì buộc mọi phương thức nhận và trả `Object`. Khi một class `implements` interface hoặc một interface `extends` interface khác, các tham số kiểu có thể được **giữ nguyên, chuyên biệt hóa hoặc ràng buộc thêm**. Điều này hỗ trợ tái sử dụng implementation, phát hiện sai kiểu sớm và thay thế implementation qua một API ổn định. Khi thiết kế, hãy tách bạch type parameter ở cấp interface và method, tận dụng `default`/`static` đúng mục đích và viết rõ những cam kết nghiệp vụ mà hệ thống generic của Java không tự bảo đảm.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="generic-classes-interfaces-005">Triển khai giao diện bằng đối số kiểu cụ thể</a>

<details>
<summary>Xem chi tiết</summary>

# Triển khai giao diện bằng đối số kiểu cụ thể trong Java Generics

Một **giao diện generic** định nghĩa hợp đồng hành vi bằng tham số kiểu, chẳng hạn `Processor<T>`, `Converter<I, O>` hoặc `Repository<E>`. Khi một lớp viết `implements Processor<String>`, lớp ấy chọn **đối số kiểu cụ thể** `String` để thực hiện hợp đồng: các vị trí vốn dùng `T` trong phương thức của giao diện sẽ được hiểu theo `String` ở API kiểu nguồn.

Kỹ thuật này giúp một giao diện có thể phục vụ nhiều kiểu dữ liệu nhưng mỗi lớp triển khai vẫn có chữ ký rõ ràng, được trình biên dịch kiểm tra. Bài học giải thích cú pháp `implements Interface<ConcreteType>`, cách viết `@Override` đúng, trường hợp nhiều tham số kiểu, khác biệt với lớp triển khai vẫn generic và những giới hạn liên quan đến kế thừa, bất biến kiểu và *type erasure*.

## 1. Đọc đúng cú pháp `implements Interface<ConcreteType>`

Giả sử ta muốn một hợp đồng chuyển dữ liệu thành chuỗi:

```java
interface Renderer<T> {
    String render(T value);
}
```

`T` là **tham số kiểu** được khai báo ở giao diện. Bản thân `Renderer<T>` chưa yêu cầu một kiểu dữ liệu cụ thể. Một lớp xử lý chuỗi có thể chọn `String`:

```java
final class UppercaseRenderer implements Renderer<String> {
    @Override
    public String render(String value) {
        return value.toUpperCase();
    }
}
```

Phân tích:

- `implements Renderer<String>`: lớp `UppercaseRenderer` cam kết triển khai giao diện `Renderer` với **đối số kiểu** `String`.
- `String render(String value)`: phương thức ở lớp triển khai khớp hợp đồng sau khi thay tham số kiểu `T` bằng `String`.
- `@Override`: yêu cầu trình biên dịch xác minh phương thức thực sự ghi đè hoặc triển khai phương thức có thể ghi đè của kiểu cha; rất hữu ích khi phát hiện sai chữ ký.
- `UppercaseRenderer` là lớp thông thường, **không phải lớp generic**, vì nó không khai báo `<T>` sau tên lớp. Nó luôn thực hiện `Renderer<String>`, không nhận đối số kiểu mới ở nơi tạo đối tượng.

Điều quan trọng: `implements Renderer<String>` không biến chữ `T` trong định nghĩa giao diện thành một biến toàn cục; nó **ràng buộc vai trò kiểu** trong hợp đồng mà lớp đang thực hiện. Một lớp khác có thể độc lập viết `implements Renderer<Integer>` và cung cấp `render(Integer value)`.

## 2. Chương trình Java 17 hoàn chỉnh: một giao diện, nhiều lớp chuyên biệt

Lưu thành tệp **`ConcreteInterfaceDemo.java`**:

```java
public class ConcreteInterfaceDemo {
    interface Renderer<T> {
        String render(T value);
    }

    static final class UppercaseRenderer implements Renderer<String> {
        @Override
        public String render(String value) {
            return value.toUpperCase();
        }
    }

    static final class IntegerRenderer implements Renderer<Integer> {
        @Override
        public String render(Integer value) {
            return "Số: " + value;
        }
    }

    public static void main(String[] args) {
        Renderer<String> text = new UppercaseRenderer();
        Renderer<Integer> number = new IntegerRenderer();

        System.out.println(text.render("java"));
        System.out.println(number.render(25));
    }
}
```

Biên dịch và chạy bằng JDK 17 trở lên:

```bash
javac -encoding UTF-8 ConcreteInterfaceDemo.java
java ConcreteInterfaceDemo
```

Kết quả chính xác (`stdout`):

```text
JAVA
Số: 25
```

Trong biến `text`, trình biên dịch hiểu `render(...)` nhận `String`; trong biến `number`, phương thức nhận `Integer`. Giá trị `25` kiểu `int` được Java đóng hộp (`autoboxing`) sang `Integer` khi gọi.

Nếu thêm lệnh sau vào `main`:

```java
// text.render(25); // Lỗi biên dịch: Renderer<String> yêu cầu String
```

trình biên dịch sẽ bác bỏ lời gọi. **Không cần dùng `instanceof` hay ép kiểu thủ công** ở nơi gọi vì kiểu của giá trị đầu vào đã được diễn đạt trong giao diện.

Cần phân biệt kiểu của **biến** và kiểu của **đối tượng**:

```java
Renderer<String> renderer = new UppercaseRenderer();
```

Biến có kiểu giao diện `Renderer<String>`; đối tượng thực tế có lớp `UppercaseRenderer`. Người gọi chỉ phụ thuộc vào hợp đồng của giao diện nên có thể thay bằng một lớp triển khai `Renderer<String>` khác mà không phải thay mọi nơi gọi.

## 3. Chữ ký phương thức phải khớp đối số kiểu đã chọn

Trong `Renderer<T>`, phương thức là:

```java
String render(T value);
```

Sau khi một lớp chọn `Renderer<String>`, **chữ ký triển khai phù hợp** là:

```java
@Override
public String render(String value) {
    return value.toUpperCase();
}
```

Đoạn trên là **một phương thức minh họa**, phải nằm trong một lớp `implements Renderer<String>`. Không thể chỉ thay tham số kiểu bằng `Object` rồi coi đó là triển khai tương đương:

```java
// Ví dụ cố ý sai; không dùng làm chương trình chạy:
final class WrongRenderer implements Renderer<String> {
    @Override
    public String render(Object value) {
        return value.toString();
    }
}
```

`render(Object)` không ghi đè phương thức `render(String)` được yêu cầu theo hợp đồng `Renderer<String>`. Vì vậy có lỗi biên dịch: `WrongRenderer` không triển khai phương thức abstract bắt buộc và `@Override` cũng không khớp. Ngay cả nếu bỏ `@Override`, phương thức nhận `Object` vẫn không tự biến thành phương thức đáp ứng `Renderer<String>`.

**Quy tắc thực hành:** mở khai báo interface, thay từng tham số kiểu bằng đối số kiểu được chọn trong `implements`, rồi viết chính xác phương thức cần triển khai. Đối với giao diện có nhiều phương thức, phải thực hiện tất cả các phương thức abstract còn lại hoặc khai báo lớp là `abstract`.

Về phạm vi truy cập, phương thức khai báo trong interface thông thường là `public` theo ngữ nghĩa Java. Do đó khi triển khai phương thức `render`, cần ghi `public`; không thể giảm quyền truy cập xuống `protected` hay package-private.

## 4. Triển khai giao diện có nhiều tham số kiểu

Một giao diện có thể biểu diễn cả kiểu đầu vào và kiểu đầu ra:

```java
interface Converter<I, O> {
    O convert(I input);
}
```

`I` là tham số kiểu đầu vào, `O` là tham số kiểu đầu ra. Lớp triển khai xác định cả hai đối số theo đúng thứ tự:

```java
final class TextLengthConverter implements Converter<String, Integer> {
    @Override
    public Integer convert(String input) {
        return input.length();
    }
}
```

Tham số kiểu `I` tương ứng `String`; `O` tương ứng `Integer`. Vì vậy phương thức nhận `String` và trả `Integer`.

### Ví dụ chạy được

Lưu tệp **`MultiTypeInterfaceDemo.java`**:

```java
public class MultiTypeInterfaceDemo {
    interface Converter<I, O> {
        O convert(I input);
    }

    static final class TextLengthConverter
            implements Converter<String, Integer> {
        @Override
        public Integer convert(String input) {
            return input.length();
        }
    }

    static final class NumberLabelConverter
            implements Converter<Integer, String> {
        @Override
        public String convert(Integer input) {
            return "ID-" + input;
        }
    }

    public static void main(String[] args) {
        Converter<String, Integer> length = new TextLengthConverter();
        Converter<Integer, String> label = new NumberLabelConverter();

        Integer size = length.convert("Generics");
        String code = label.convert(42);

        System.out.println(size);
        System.out.println(code);
    }
}
```

Lệnh chạy:

```bash
javac -encoding UTF-8 MultiTypeInterfaceDemo.java
java MultiTypeInterfaceDemo
```

`stdout`:

```text
8
ID-42
```

Hai lớp dùng **cùng một interface** nhưng chọn hai tập đối số kiểu khác nhau. Việc đảo vị trí `<String, Integer>` thành `<Integer, String>` làm thay đổi ý nghĩa và chữ ký bắt buộc, chứ không chỉ thay đổi cách trình bày mã.

| Kiểu giao diện | Kiểu nhận của `convert` | Kiểu trả về của `convert` |
|---|---|---|
| `Converter<String, Integer>` | `String` | `Integer` |
| `Converter<Integer, String>` | `Integer` | `String` |
| `Converter<String, String>` | `String` | `String` |

Lưu ý `int` là kiểu nguyên thủy nên không được dùng trực tiếp làm đối số generic; ta dùng lớp bao `Integer`. Biểu thức `input.length()` trả `int` và được đóng hộp thành `Integer` ở phương thức trả về.

## 5. So sánh lớp triển khai kiểu cụ thể và lớp triển khai vẫn generic

Hai dạng sau có mục đích khác nhau.

**Lớp triển khai cố định kiểu:**

```java
final class StringEcho implements Echo<String> {
    @Override
    public String echo(String value) {
        return value;
    }
}
```

**Lớp triển khai chuyển tiếp tham số kiểu:**

```java
final class GenericEcho<T> implements Echo<T> {
    @Override
    public T echo(T value) {
        return value;
    }
}
```

Cả hai snippet trên giả định có giao diện:

```java
interface Echo<T> {
    T echo(T value);
}
```

`StringEcho` đã chốt `String`; nơi sử dụng viết `new StringEcho()` và **không** thể viết `new StringEcho<Integer>()` vì lớp không khai báo tham số kiểu.

`GenericEcho<T>` vẫn mang tham số kiểu `T`; nơi sử dụng có thể viết `new GenericEcho<String>()` hoặc `new GenericEcho<Integer>()`. Cú pháp `implements Echo<T>` chuyển tiếp quan hệ kiểu từ lớp sang interface.

### Ví dụ so sánh hoàn chỉnh

Lưu tệp **`ConcreteVsGenericDemo.java`**:

```java
public class ConcreteVsGenericDemo {
    interface Echo<T> {
        T echo(T value);
    }

    static final class StringEcho implements Echo<String> {
        @Override
        public String echo(String value) {
            return value.toUpperCase();
        }
    }

    static final class GenericEcho<T> implements Echo<T> {
        @Override
        public T echo(T value) {
            return value;
        }
    }

    public static void main(String[] args) {
        Echo<String> stringOnly = new StringEcho();
        Echo<String> genericString = new GenericEcho<>();
        Echo<Integer> genericNumber = new GenericEcho<>();

        System.out.println(stringOnly.echo("java"));
        System.out.println(genericString.echo("Java"));
        System.out.println(genericNumber.echo(17));
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 ConcreteVsGenericDemo.java
java ConcreteVsGenericDemo
```

`stdout`:

```text
JAVA
Java
17
```

Nên chọn cách nào?

- Dùng `implements Echo<String>` nếu lớp thực hiện nghiệp vụ riêng cho **một kiểu đã xác định**, ví dụ xử lý chuỗi hoặc thực thể `Customer`.
- Dùng `class GenericEcho<T> implements Echo<T>` nếu thuật toán có thể hoạt động giống nhau với nhiều kiểu và bản thân lớp không cần biết `T` là kiểu nào.
- Có thể kết hợp: `class Child<U> implements Converter<String, U>` chốt kiểu đầu vào là `String` nhưng vẫn cho phép kiểu đầu ra `U` thay đổi. Chỉ dùng khi nghiệp vụ thật sự cần tính linh hoạt ấy.

Đây là quyết định về **mức độ tổng quát của lớp**, không phải về kiểu đối tượng runtime được nhân bản theo từng `T`.

## 6. Interface generic vẫn cho phép nhiều phương thức và phương thức mặc định

Một giao diện không chỉ có một phương thức. Khi khai báo `implements Repository<String>`, mọi vị trí `T` trong hợp đồng cần được hiểu đồng nhất theo `String`.

Ví dụ:

```java
interface Repository<T> {
    void save(T item);
    T latest();

    default String describe(T item) {
        return "Value: " + item;
    }
}
```

Một lớp chọn `String` sẽ phải cài đặt `save(String)` và `String latest()`; `describe(String)` là phương thức `default` đã có thân, nên lớp có thể kế thừa cách cài đặt mà không phải ghi đè. Nếu ghi đè, chữ ký vẫn phải khớp kiểu đã chọn.

Lưu tệp **`RepositoryDemo.java`**:

```java
public class RepositoryDemo {
    interface Repository<T> {
        void save(T item);
        T latest();

        default String describe(T item) {
            return "Value: " + item;
        }
    }

    static final class LastTextRepository implements Repository<String> {
        private String latest;

        @Override
        public void save(String item) {
            latest = item;
        }

        @Override
        public String latest() {
            return latest;
        }
    }

    public static void main(String[] args) {
        Repository<String> repository = new LastTextRepository();
        repository.save("Generics");

        System.out.println(repository.latest());
        System.out.println(repository.describe("Java"));
    }
}
```

Biên dịch/chạy:

```bash
javac -encoding UTF-8 RepositoryDemo.java
java RepositoryDemo
```

`stdout`:

```text
Generics
Value: Java
```

Trong ví dụ, `Repository<String>` không tự lưu dữ liệu hoặc tự thêm cơ chế truy cập database. `LastTextRepository` chỉ cất một giá trị trong bộ nhớ để minh họa quan hệ kiểu; tên `Repository` ở đây mô tả hợp đồng học tập, không phải kho lưu trữ bền vững.

Đặc biệt, phần cài đặt phải đảm bảo ý nghĩa phương thức; generics chỉ xác nhận **kiểu** của dữ liệu, không kiểm tra các yêu cầu nghiệp vụ như dữ liệu có tồn tại, có hợp lệ hoặc có cần đồng bộ giữa luồng hay không.

## 7. Ví dụ thực tế: `Comparable<Product>`

Một kiểu sử dụng phổ biến của triển khai interface generic với đối số kiểu cụ thể là `Comparable<T>`. Lớp có thể ghi `implements Comparable<Product>` để mô tả cách so sánh hai đối tượng `Product`.

Lưu tệp **`ProductSortDemo.java`**:

```java
import java.util.ArrayList;
import java.util.List;

public class ProductSortDemo {
    record Product(String code, int price) implements Comparable<Product> {
        @Override
        public int compareTo(Product other) {
            int byPrice = Integer.compare(price, other.price());
            return byPrice != 0 ? byPrice : code.compareTo(other.code());
        }
    }

    public static void main(String[] args) {
        List<Product> products = new ArrayList<>(List.of(
            new Product("B", 20),
            new Product("A", 10),
            new Product("C", 20)
        ));

        products.sort(null);

        for (Product product : products) {
            System.out.println(product.code() + ":" + product.price());
        }
    }
}
```

Chạy Java 17+:

```bash
javac -encoding UTF-8 ProductSortDemo.java
java ProductSortDemo
```

`stdout`:

```text
A:10
B:20
C:20
```

Với `Comparable<Product>`, phương thức bắt buộc là `int compareTo(Product other)`. Nếu giá thấp hơn, phương thức trả giá trị âm; cao hơn thì trả dương; bằng giá sẽ so thêm `code` để có thứ tự xác định. `List.sort(null)` dùng thứ tự tự nhiên do `Comparable` của phần tử cung cấp.

Ví dụ `record` ở trên là kiểu dữ liệu Java hỗ trợ từ trước Java 17; cú pháp `record Product(...) implements Comparable<Product>` hoàn toàn hợp lệ. Cần phân biệt `Comparable<T>`: đối tượng tự định nghĩa thứ tự tự nhiên của nó; còn `Comparator<T>` là một giao diện khác để mô tả quy tắc so sánh bên ngoài đối tượng.

Đối số `Product` trong `Comparable<Product>` giúp trình biên dịch ngăn việc truyền sai kiểu đối tượng cho `compareTo` thông qua API được kiểm tra kiểu.

## 8. Kế thừa và nhiều interface: giới hạn cần nhớ

### 8.1. Một lớp có thể triển khai nhiều giao diện generic khác nhau

Ví dụ:

```java
interface Named {
    String name();
}

interface Identifiable<T> {
    T id();
}

final class Account implements Named, Identifiable<Long> {
    @Override
    public String name() {
        return "Primary";
    }

    @Override
    public Long id() {
        return 100L;
    }
}
```

Đây là **nhóm khai báo minh họa**, có thể đặt trong một tệp cùng package. `Account` triển khai `Named` và `Identifiable<Long>`. Interface không có tham số kiểu và interface có tham số kiểu có thể được kết hợp bình thường, miễn các hợp đồng không xung đột.

### 8.2. Không được kế thừa cùng một giao diện generic với hai đối số kiểu khác nhau

Xét:

```java
interface Handler<T> {
    void handle(T value);
}

interface TextHandler extends Handler<String> { }
interface NumberHandler extends Handler<Integer> { }

// Sai:
// final class Both implements TextHandler, NumberHandler { ... }
```

Dù có thể nghĩ rằng chỉ cần viết `handle(String)` và `handle(Integer)`, Java không cho một kiểu cùng kế thừa hai cách tham số hóa khác nhau của **cùng một giao diện generic** như `Handler<String>` và `Handler<Integer>`. Sau *type erasure*, các quan hệ kiểu này còn có vấn đề xung đột; quy tắc ngôn ngữ loại bỏ cách kế thừa ấy ngay khi biên dịch.

Nếu ứng dụng thực sự cần xử lý hai kiểu, có thể khai báo hai phương thức có **tên khác nhau** trong các interface độc lập, hoặc dùng một giao diện nhận một kiểu bao quát hơn với logic phân loại rõ ràng. Không nên bỏ generics rồi dùng raw type để che lỗi thiết kế này.

### 8.3. Một lớp có thể kế thừa lớp cơ sở và triển khai interface

Trong Java, một lớp chỉ `extends` được một lớp cha nhưng có thể `implements` nhiều interface:

```java
abstract class Base {
    String prefix() {
        return "ID:";
    }
}

interface Formatter<T> {
    String format(T value);
}

final class IntegerFormatter extends Base implements Formatter<Integer> {
    @Override
    public String format(Integer value) {
        return prefix() + value;
    }
}
```

Các khai báo trên là **mã minh họa độc lập**. Thứ tự đúng là `extends Base implements Formatter<Integer>`. Giao diện generic vẫn được triển khai bằng một đối số kiểu cụ thể `Integer`.

## 9. Bất biến kiểu (`invariance`) vẫn áp dụng cho giao diện generic

Một lỗi phổ biến là nghĩ rằng vì `String extends Object` nên `Renderer<String>` có thể truyền vào nơi cần `Renderer<Object>`. Trong Java, quan hệ này **không tồn tại mặc định**:

```java
Renderer<String> strings = new UppercaseRenderer();

// Renderer<Object> allObjects = strings; // Sai: không tương thích kiểu
```

`Renderer<String>` và `Renderer<Object>` là hai cách tham số hóa khác nhau của `Renderer`; generic Java mặc định bất biến. Việc cho phép phép gán trên sẽ khiến người gọi có thể đưa vào những `Object` không phải `String`, phá hợp đồng lớp triển khai.

Đối với interface chỉ **tiêu thụ** giá trị kiểu `T` như `Renderer<T>`, wildcard `? super String` có thể hữu ích trong chữ ký API nhận những renderer có thể xử lý `String`; đối với interface chỉ **cung cấp** giá trị kiểu `T`, wildcard `? extends T` thường phù hợp hơn. Đây là hướng mở rộng khi thiết kế API tiêu thụ hoặc sản xuất dữ liệu; chưa cần áp dụng wildcard vào lớp `UppercaseRenderer` để nó triển khai đúng `Renderer<String>`.

## 10. Điều gì xảy ra khi chạy: type erasure và bridge method

Generics Java chủ yếu tạo ra các kiểm tra kiểu **khi biên dịch**. Sau quá trình *type erasure*, JVM không có một bản sao runtime riêng của giao diện cho mỗi đối số như `Renderer<String>` và `Renderer<Integer>`.

Xét ví dụ thu gọn:

```java
interface Source<T> {
    T get();
}

final class StringSource implements Source<String> {
    @Override
    public String get() {
        return "Java";
    }
}
```

Ở mức mã nguồn, `Source<String>.get()` trả về `String`. Sau erasure, chữ ký giao diện về cơ bản trở thành `Object get()`. Để giữ tính đa hình của phương thức đã ghi đè, trình biên dịch Java có thể tạo **bridge method** tổng hợp trong lớp triển khai, thường có chữ ký `Object get()` và chuyển tiếp tới `String get()`.

Bridge method là cơ chế tự động để giữ hành vi override đúng khi erasure làm thay đổi chữ ký JVM, **không phải** phương thức người lập trình phải tự viết. Có thể quan sát bằng cách lưu hai khai báo trên trong `StringSource.java` (không khai báo `public`), rồi chạy:

```bash
javac -encoding UTF-8 StringSource.java
javap -p -c StringSource
```

Kết quả `javap` gồm mã bytecode và các phương thức đã biên dịch; trong trường hợp này sẽ thấy một phương thức `get()` trả `java.lang.String` và phương thức cầu nối `get()` trả `java.lang.Object`. Tùy tùy chọn hiển thị, dùng `javap -v StringSource` để xem cờ `ACC_BRIDGE`, `ACC_SYNTHETIC`.

Từ góc độ sử dụng API, điểm quan trọng vẫn là: khai báo `implements Source<String>` giúp trình biên dịch kiểm tra `String` tại nơi gọi. Tuy nhiên, khi mã cũ dùng raw type hoặc thực hiện ép kiểu không được kiểm tra, vẫn có thể xảy ra `ClassCastException`. Vì thế nên tránh raw type và kiểm tra cảnh báo `unchecked`.

## 11. Các lỗi phổ biến khi `implements` kiểu cụ thể

| Lỗi | Nguyên nhân | Cách sửa |
|---|---|---|
| `implements Renderer<String>` nhưng viết `render(Object)` | Sai chữ ký cần triển khai: `render(String)`. | Ghi đè `public String render(String value)` và thêm `@Override`. |
| `implements Converter<String, Integer>` nhưng trả `String` | Kiểu trả về không đúng đối số thứ hai `Integer`. | Trả `Integer` hoặc điều chỉnh các đối số kiểu của hợp đồng. |
| Viết `class X implements Renderer<T>` khi lớp không khai báo `T` | `T` không tự tồn tại trong phạm vi lớp. | Dùng `class X<T> implements Renderer<T>` hoặc chốt `Renderer<String>`. |
| Viết `implements Renderer<int>` | Generic chỉ chấp nhận đối số là kiểu tham chiếu. | Dùng `Renderer<Integer>`. |
| Viết `new UppercaseRenderer<String>()` | `UppercaseRenderer` không phải lớp generic. | Dùng `new UppercaseRenderer()`. |
| Để phương thức triển khai có quyền package-private | Không được giảm quyền truy cập của phương thức interface public. | Khai báo phương thức `public`. |
| Nghĩ `Renderer<String>` gán được cho `Renderer<Object>` | Java generics bất biến. | Giữ đúng hợp đồng hoặc thiết kế wildcard ở nơi tiêu thụ nếu thật sự cần. |
| Cùng `implements Handler<String>` và `Handler<Integer>` qua các nhánh kế thừa | Không được kế thừa cùng generic interface với hai đối số kiểu khác nhau. | Thiết kế interface độc lập hoặc thay đổi mô hình xử lý. |
| Bỏ đối số kiểu: `implements Renderer` | Raw type làm mất lợi ích kiểm tra kiểu và có thể sinh cảnh báo. | Chỉ định `Renderer<String>` hay một tham số kiểu khai báo hợp lệ. |
| Cho rằng `implements Interface<String>` giúp runtime kiểm tra toàn bộ tham số generic | Type erasure làm phần lớn thông tin kiểu không còn cho phép kiểm tra `instanceof Interface<String>`. | Giữ an toàn ở compile time, xác thực dữ liệu ở ranh giới không tin cậy. |

Nếu một lớp không ghi đè đủ các phương thức abstract đúng hợp đồng, trình biên dịch yêu cầu lớp phải là `abstract` hoặc bổ sung các phương thức còn thiếu. Không nên vô hiệu hóa cảnh báo, dùng ép kiểu không kiểm tra hoặc bỏ `@Override` để che lỗi.

## 12. Quy trình triển khai một interface generic đúng cách

Khi gặp yêu cầu “viết lớp thực hiện `SomeInterface<ConcreteType>`”, thực hiện lần lượt:

1. **Đọc khai báo giao diện**: xác định số lượng tham số kiểu, thứ tự, các giới hạn như `T extends Number` và danh sách phương thức abstract/default.
2. **Chọn đối số kiểu có ý nghĩa nghiệp vụ**: ví dụ `String`, `Order`, `Integer`, `Converter<Request, Response>`. Phải dùng kiểu tham chiếu và thỏa mọi bound.
3. **Viết `implements` với đầy đủ đối số kiểu**: `class TextHandler implements Handler<String>`.
4. **Thay kiểu trong hợp đồng**: mọi vị trí của `T` ở phương thức được hiểu theo `String`; với `<I,O>` thay theo đúng thứ tự của hai đối số.
5. **Triển khai các phương thức bắt buộc**: ghi `public`, `@Override`, chữ ký đúng và xử lý nghiệp vụ bên trong.
6. **Sử dụng qua kiểu giao diện** ở bên gọi nếu phù hợp: `Handler<String> handler = new TextHandler();`.
7. **Biên dịch và kiểm tra cảnh báo**: `javac -Xlint:rawtypes,unchecked ...`; chú ý lỗi signature, raw type, bound và các đường ép kiểu không an toàn.
8. **Thử cả giá trị hợp lệ và sai kiểu ở biên dịch**: sai kiểu phải bị bác bỏ tại nơi gọi API được ràng buộc kiểu; runtime vẫn cần kiểm soát `null` và các quy tắc nghiệp vụ riêng.

### Bài tập tự kiểm tra

**Câu 1.** Với `interface Mapper<I, O> { O map(I input); }`, phương thức nào phải có trong một lớp `implements Mapper<String, Long>`?

**Câu 2.** Vì sao `class TextMapper implements Mapper<String, Long>` không thể chỉ khai báo `Object map(Object x)`?

**Câu 3.** `class CommonMapper<T> implements Mapper<T, String>` có phải lớp generic không? Nơi sử dụng có thể khai báo `CommonMapper<Integer>` không?

**Câu 4.** Vì sao Java không chấp nhận một lớp vừa là `Handler<String>` vừa là `Handler<Integer>` thông qua hai interface kế thừa?

**Câu 5.** `StringSource` ở phần *type erasure* có phải tự viết `Object get()` để được dùng qua `Source<String>` không?

**Đáp án định hướng:** (1) `public Long map(String input)`. (2) Chữ ký sau khi ràng buộc kiểu yêu cầu tham số `String` và kết quả `Long`; `Object` không đáp ứng hợp đồng đó. (3) Có; `T` vẫn là tham số kiểu của lớp, còn đầu ra đã chốt `String`. (4) Cùng giao diện generic không được kế thừa với hai đối số kiểu khác nhau. (5) Không; trình biên dịch xử lý bridge method cần thiết khi biên dịch.

**Ghi nhớ:** `implements Interface<ConcreteType>` là cách **chuyên biệt hóa hợp đồng của một giao diện generic tại lớp triển khai**. Hãy thay tham số kiểu bằng đối số kiểu đã chọn trong chữ ký phương thức, dùng `public` và `@Override`, sau đó sử dụng đối tượng qua kiểu giao diện được tham số hóa để compiler bảo vệ quan hệ kiểu ngay từ khi viết mã.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="generic-classes-interfaces-006">Triển khai giao diện bằng tham số kiểu của lớp</a>

<details>
<summary>Xem chi tiết</summary>

# Triển khai giao diện bằng tham số kiểu của lớp trong Java Generics

## 1. Mục tiêu

Java cho phép **generic class** truyền các tham số kiểu do chính lớp khai báo vào **generic interface** mà nó triển khai. Cơ chế này kết nối hợp đồng của interface với cách dùng lớp ở từng kiểu cụ thể: khi một class được dùng như Implementation<String>, compiler biết nó cũng thực hiện Interface<String> nếu lớp khai báo đúng mối quan hệ.

Ví dụ nền tảng:

~~~java
interface Store<T> {
    void save(T value);
    T load();
}

class MemoryStore<E> implements Store<E> {
    private E item;

    @Override
    public void save(E value) {
        item = value;
    }

    @Override
    public E load() {
        return item;
    }
}
~~~

Trong **MemoryStore<E>**, E là *type parameter* được lớp khai báo. Trong **implements Store<E>**, E là *type argument* lấy từ tham số kiểu của lớp để điền vào vị trí T của Store<T>. Hai vị trí này dùng cùng một tên E nhưng đảm nhận vai trò cú pháp khác nhau. **implements không tạo thêm tham số kiểu mới**.

Sau bài này, bạn cần giải thích và vận dụng được cách chuyển tiếp một hoặc nhiều tham số kiểu qua implements, viết @Override đúng chữ ký, triển khai interface bằng kiểu cố định hoặc kiểu hỗn hợp, giữ quan hệ generic qua extends, xử lý bounds và tránh raw type, wildcard không hợp lệ cùng xung đột do type erasure.

## 2. Mẫu cú pháp và phạm vi của tham số kiểu

Cú pháp thường gặp:

~~~java
interface Contract<T> {
    T process(T input);
}

class GenericImplementation<U> implements Contract<U> {
    @Override
    public U process(U input) {
        return input;
    }
}
~~~

Diễn giải theo thứ tự:

1. **interface Contract<T>** khai báo một type parameter T của interface, dùng nó cho tham số input và kết quả.
2. **class GenericImplementation<U>** khai báo một type parameter U mới, thuộc phạm vi lớp triển khai.
3. **implements Contract<U>** áp dụng U cho vị trí tham số kiểu T mà Contract khai báo. Mỗi trường hợp sử dụng GenericImplementation<String> đồng thời thỏa Contract<String>.
4. **public U process(U input)** triển khai hợp đồng phương thức sau phép thay thế type argument; cả đầu vào lẫn đầu ra vẫn giữ cùng U.
5. **@Override** buộc compiler kiểm tra rằng phương thức thật sự override một phương thức của supertype; nếu sai tên, tham số hoặc quan hệ kiểu, lỗi được phát hiện khi biên dịch.

Không có yêu cầu tên tham số kiểu ở class và interface phải giống nhau: **Store<T>** và **MemoryStore<E>** sử dụng hai tên khác nhau nhưng được kết nối ở **Store<E>**. Bạn cũng có thể dùng **class MemoryStore<T> implements Store<T>** nếu thấy dễ đọc hơn.

Một cách biểu đạt tổng quát:

~~~text
interface InterfaceName<P> { ... }

class ClassName<Q> implements InterfaceName<Q> {
    // Các method được triển khai theo Q.
}
~~~

Dấu **<Q>** sau ClassName là *nơi khai báo*; dấu **<Q>** sau InterfaceName là *nơi sử dụng*. Với nhiều kiểu:

~~~text
class ClassName<K, V> implements InterfaceName<K, V> { ... }
~~~

Dấu phẩy tách các type parameters độc lập. Từng type argument trong implements phải hợp lệ với vị trí và bounds tương ứng của interface.

## 3. Ví dụ chạy độc lập: truyền tham số kiểu của lớp vào interface

Lưu vào **GenericImplementationDemo.java**:

~~~java
public class GenericImplementationDemo {
    interface Store<T> {
        void save(T value);
        T load();
    }

    static final class MemoryStore<E> implements Store<E> {
        private E item;

        @Override
        public void save(E value) {
            item = value;
        }

        @Override
        public E load() {
            return item;
        }
    }

    interface Converter<I, O> {
        O convert(I input);
    }

    static final class LabelConverter<T>
            implements Converter<T, String> {
        @Override
        public String convert(T input) {
            return "value=" + input;
        }
    }

    public static void main(String[] args) {
        Store<String> words = new MemoryStore<>();
        words.save("Java");
        System.out.println(words.load());

        Store<Integer> score = new MemoryStore<>();
        score.save(42);
        System.out.println(score.load());

        Converter<Integer, String> converter = new LabelConverter<>();
        System.out.println(converter.convert(score.load()));
    }
}
~~~

Biên dịch và chạy với JDK 17+:

~~~bash
javac -encoding UTF-8 GenericImplementationDemo.java
java GenericImplementationDemo
~~~

Stdout:

~~~text
Java
42
value=42
~~~

Các kết luận rút ra:

- **MemoryStore<E> implements Store<E>** gắn E trong class với T trong interface; khi được dùng qua Store<String>, phương thức save nhận String, load trả String.
- **new MemoryStore<>()** dùng diamond operator để suy luận type argument từ ngữ cảnh. Diamond không tạo một type parameter mới.
- **Converter<I, O>** khai báo hai kiểu độc lập: kiểu đầu vào I và kiểu đầu ra O.
- **LabelConverter<T> implements Converter<T, String>** chỉ chuyển tiếp T vào tham số kiểu đầu tiên; type argument thứ hai được cố định là String.
- **Converter<Integer, String>** vì vậy nhận Integer và trả String. Phép nối chuỗi trong convert tạo chính xác "value=42" ở lần gọi cuối.

Các nested interface/class trong chương trình giúp một tệp duy nhất biên dịch độc lập. Khi xây dựng dự án lớn, chúng có thể được đưa ra các file riêng mà cú pháp implements vẫn như vậy.

## 4. Chuyển tiếp `T` giữ được quan hệ kiểu giữa class và interface

Khi viết `class MemorySource<E> implements Source<E>`, `E` được khai báo **một lần bởi class** rồi truyền vào hợp đồng interface. Chẳng hạn:

~~~java
interface Source<T> { T read(); }

class MemorySource<E> implements Source<E> {
    private final E value;
    MemorySource(E value) { this.value = value; }
    @Override public E read() { return value; }
}
~~~

Trong một method, `Source<String> source = new MemorySource<>("hello");` cho phép `String text = source.read();` mà không cần cast. Nếu chỉ khai báo `Object read()`, compiler không thể hứa kết quả là `String`.

Khác biệt quan trọng của **lớp triển khai generic** là cùng class có thể duy trì hợp đồng `Source<String>`, `Source<Integer>` và các type arguments khác tại từng nơi sử dụng. Việc chuyển tiếp `E` qua `implements` giúp chữ ký override được kiểm tra từ đầu vào tới đầu ra, nhưng không tạo các runtime classes riêng cho từng `E`.

## 5. Đối chiếu: cố định kiểu hay chuyển tiếp type parameter?

Không phải mọi implementation của generic interface đều phải generic. Hai cú pháp sau có phạm vi tái sử dụng khác nhau:

~~~java
interface Reader<T> { T read(); }

class TextReader implements Reader<String> {
    @Override public String read() { return "text"; }
}

class MemoryReader<E> implements Reader<E> {
    private final E item;
    MemoryReader(E item) { this.item = item; }
    @Override public E read() { return item; }
}
~~~

| Cách triển khai | Ý nghĩa |
|---|---|
| `TextReader implements Reader<String>` | Lớp chuyên xử lý `String`; không có type parameter riêng |
| `MemoryReader<E> implements Reader<E>` | Lớp mang `E` và chuyển `E` vào interface; cùng khai báo dùng được với nhiều kiểu |

Khi lớp có nghiệp vụ cố định vào một concrete type, chọn chuyên biệt. Khi logic giống nhau cho nhiều kiểu và cần giữ quan hệ `E` xuyên suốt, chọn lớp generic. Trọng tâm các mục kế tiếp là **chuyển tiếp một phần/nhiều type parameters qua các tầng hợp đồng**, không phải lặp lại việc triển khai kiểu cố định.

## 6. Triển khai interface nhiều tham số kiểu

Xét một hợp đồng nhận khóa K và trả dữ liệu V:

~~~java
interface Lookup<K, V> {
    V find(K key);
}
~~~

Một lớp triển khai có thể chuyển tiếp cả K và V:

~~~java
class MapLookup<K, V> implements Lookup<K, V> {
    private final java.util.Map<K, V> map;

    MapLookup(java.util.Map<K, V> map) {
        this.map = map;
    }

    @Override
    public V find(K key) {
        return map.get(key);
    }
}
~~~

Ở class MapLookup, K và V được khai báo riêng và dùng làm type arguments của Lookup. Khi dùng MapLookup<String, Integer>, hợp đồng được áp dụng thành **Integer find(String key)**.

Ví dụ sử dụng:

~~~java
// Minh họa trong một phương thức:
java.util.Map<String, Integer> data =
        java.util.Map.of("spring", 21);
Lookup<String, Integer> lookup = new MapLookup<>(data);
Integer value = lookup.find("spring");
~~~

Cần giữ **đúng thứ tự đối số kiểu**: Lookup<K, V> có K cho key và V cho value. Nếu viết **implements Lookup<V, K>**, chữ ký cần implement sẽ thành **K find(V key)**, tức thay đổi hợp đồng.

### Chuyển tiếp một phần và cố định một phần

~~~java
class StringKeyLookup<V> implements Lookup<String, V> {
    private final java.util.Map<String, V> map;

    StringKeyLookup(java.util.Map<String, V> map) {
        this.map = map;
    }

    @Override
    public V find(String key) {
        return map.get(key);
    }
}
~~~

Class này chỉ khai báo V, cố định kiểu khóa thành String. **StringKeyLookup<Integer>** triển khai Lookup<String, Integer>; **StringKeyLookup<Long>** triển khai Lookup<String, Long>.

Theo chiều ngược lại cũng được:

~~~java
class BooleanLookup<K> implements Lookup<K, Boolean> {
    @Override
    public Boolean find(K key) {
        return key != null;
    }
}
~~~

**BooleanLookup<K>** giữ khóa linh hoạt và kết quả cố định Boolean. Số type parameters khai báo trên class không bắt buộc bằng số type parameters của interface; chỉ cần các type arguments trong mệnh đề implements đầy đủ, tương thích và phù hợp với method contracts.

## 7. Triển khai nhiều interface cùng lúc

Java cho phép một class triển khai nhiều superinterfaces, mỗi interface có thể dùng type variables riêng của class.

~~~java
interface Identified<ID> {
    ID id();
}

interface Valued<V> {
    V value();
}

class Entry<K, V> implements Identified<K>, Valued<V> {
    private final K key;
    private final V data;

    Entry(K key, V data) {
        this.key = key;
        this.data = data;
    }

    @Override
    public K id() {
        return key;
    }

    @Override
    public V value() {
        return data;
    }
}
~~~

**Entry<K, V>** truyền K vào Identified và V vào Valued. Một thể hiện **Entry<Long, String>** có cả hợp đồng Identified<Long> và Valued<String>.

Một type variable cũng có thể được chia sẻ giữa nhiều interface:

~~~java
interface Input<T> {
    void accept(T value);
}

interface Output<T> {
    T produce();
}

class Channel<T> implements Input<T>, Output<T> {
    private T current;

    @Override
    public void accept(T value) {
        current = value;
    }

    @Override
    public T produce() {
        return current;
    }
}
~~~

Với Channel<String>, cả accept và produce dùng String. Việc dùng cùng T mô tả quan hệ kiểu đầu vào và đầu ra của một channel.

Cú pháp class nhiều interface là **implements I<T>, J<T>**, phân cách bằng dấu phẩy. Nếu class còn extends một superclass, thứ tự là **extends Base<...> implements I<...>, J<...>**.

## 8. Quy tắc override phương thức generic interface

Interface có thể có method thông thường sử dụng type variable của interface:

~~~java
interface Transformer<I, O> {
    O transform(I input);
}

class FunctionTransformer<I, O> implements Transformer<I, O> {
    private final java.util.function.Function<? super I, ? extends O> function;

    FunctionTransformer(
            java.util.function.Function<? super I, ? extends O> function) {
        this.function = function;
    }

    @Override
    public O transform(I input) {
        return function.apply(input);
    }
}
~~~

Method triển khai phải phù hợp với chữ ký **O transform(I input)** sau phép áp dụng các type arguments. Quan trọng:

- Phương thức interface là public, vì vậy implementation cần **public**; giảm visibility xuống package-private sẽ không hợp lệ.
- @Override giúp phát hiện method không thực sự override vì sai tên, sai tham số, sai chữ ký hoặc quan hệ kiểu.
- Kiểu trả về phải tương thích; Java có hỗ trợ covariant return với các reference types khi đáp ứng luật override.
- Không được mở rộng checked exceptions vượt quá hợp đồng phương thức của interface.
- Khi method cần dùng I, O được khai báo trên class, không cần tự khai báo **<I, O>** ngay trước kiểu trả về của method.

Ví dụ lỗi:

~~~java
// Cố ý sai, không biên dịch:
interface Holder<T> {
    T read();
}

// class Wrong<T> implements Holder<T> {
//     T read() { return null; }
// }
~~~

Trong Wrong, read() thiếu public. Cách sửa là **public T read()**. Nếu method khác hẳn về kiểu tham số hoặc trả về, compiler sẽ thông báo thiếu phương thức bắt buộc hay xung đột tên.

### Phân biệt type parameter của class với generic method

~~~java
interface Copier {
    <U> U copy(U input);
}

class IdentityCopier implements Copier {
    @Override
    public <U> U copy(U input) {
        return input;
    }
}
~~~

Trong ví dụ này **Copier** không generic; chính method **copy** khai báo type parameter U. Đó là lý do implementation phải giữ chữ ký generic method tương thích.

Ngược lại:

~~~java
interface Consumer<T> {
    void accept(T item);
}

class ConsumerImpl<T> implements Consumer<T> {
    @Override
    public void accept(T item) {
        System.out.println(item);
    }
}
~~~

Method accept sử dụng T **đã được class khai báo**. Viết **public <U> void accept(U item)** sẽ khai báo một generic method khác, không thực hiện hợp đồng accept(T) mà class đang có nghĩa vụ triển khai. Phân biệt nguồn gốc của từng type variable khi đọc chữ ký.

## 9. Kế thừa generic interface rồi triển khai

Một interface có thể extends một interface generic và tiếp tục chuyển type variable của chính nó:

~~~java
interface Identified<ID> {
    ID id();
}

interface Labeled<ID> extends Identified<ID> {
    String label();
}
~~~

Labeled<ID> kế thừa Identified<ID>. Class triển khai **Labeled<K>** cần thực hiện cả id() trả K và label() trả String, trừ khi một implementation hợp lệ đã được kế thừa từ superclass.

~~~java
class LabeledItem<K> implements Labeled<K> {
    private final K key;
    private final String text;

    LabeledItem(K key, String text) {
        this.key = key;
        this.text = text;
    }

    @Override
    public K id() {
        return key;
    }

    @Override
    public String label() {
        return text;
    }
}
~~~

Chỉ cần **implements Labeled<K>**; không bắt buộc lặp thêm **implements Identified<K>**, vì đó đã là superinterface của Labeled<K>.

Một interface con có thể khai báo nhiều tham số hơn interface cha:

~~~java
interface KeyValue<K, V> extends Identified<K> {
    V value();
}
~~~

K được truyền tiếp lên Identified; V dùng ở method mới. Class muốn triển khai KeyValue<K, V> phải có cả **K id()** và **V value()**. Đây là cách chuyển tiếp type variables xuyên qua nhiều tầng quan hệ kế thừa.

## 10. Kết hợp extends class với implements interface

Một generic subclass có thể chuyển tiếp cùng type variable tới superclass và interface:

~~~java
interface Printable<T> {
    String render(T input);
}

class Container<T> {
    private final T value;

    Container(T value) {
        this.value = value;
    }

    T get() {
        return value;
    }
}

class PrintableContainer<U> extends Container<U>
        implements Printable<U> {

    PrintableContainer(U value) {
        super(value);
    }

    @Override
    public String render(U input) {
        return "[" + input + "]";
    }
}
~~~

Class **PrintableContainer<U>** vừa extends Container<U> vừa implements Printable<U>. U là type parameter do subclass khai báo; hai supertype đều nhận U làm type argument. Việc kế thừa không làm mất quan hệ kiểu giữa các thành viên, nếu các type arguments được truyền đúng.

Subclass không generic có thể cố định type argument:

~~~java
class TextContainer extends PrintableContainer<String> {
    TextContainer(String text) {
        super(text);
    }
}
~~~

TextContainer kế thừa việc triển khai Printable<String> thông qua PrintableContainer<String>. Không cần viết lại implements nếu superclass đã thực hiện interface.

Ngoài ra, subclass có thể tiếp tục khai báo type parameter và chuyển tiếp:

~~~java
class CustomContainer<X> extends PrintableContainer<X> {
    CustomContainer(X value) {
        super(value);
    }
}
~~~

Trong một hệ thống kế thừa lớn, hãy lần theo type arguments qua từng extends/implements để biết chính xác method contract mà object cuối cùng đáp ứng.

## 11. Bounds khi interface yêu cầu một kiểu có ràng buộc

Nếu generic interface có type parameter với bound, class triển khai dùng type variable cũng phải bảo đảm bound đó.

~~~java
interface NumericView<N extends Number> {
    N value();
    double asDouble();
}

class NumericBox<T extends Number> implements NumericView<T> {
    private final T number;

    NumericBox(T number) {
        this.number = number;
    }

    @Override
    public T value() {
        return number;
    }

    @Override
    public double asDouble() {
        return number.doubleValue();
    }
}
~~~

**NumericBox<T extends Number>** bảo đảm T đáp ứng Number; do đó **implements NumericView<T>** hợp lệ. Nếu viết **class NumericBox<T> implements NumericView<T>**, compiler không thể chứng minh mọi T đều là Number.

Với interface cần Comparable:

~~~java
interface OrderedValue<T extends Comparable<T>> {
    T get();
}

class OrderedBox<T extends Comparable<T>>
        implements OrderedValue<T> {
    private final T value;

    OrderedBox(T value) {
        this.value = value;
    }

    @Override
    public T get() {
        return value;
    }
}
~~~

Nếu type parameter cần nhiều bounds, dùng **&**:

~~~java
class NumberOrder<T extends Number & Comparable<T>>
        implements Comparable<NumberOrder<T>> {

    private final T number;

    NumberOrder(T number) {
        this.number = number;
    }

    @Override
    public int compareTo(NumberOrder<T> other) {
        return number.compareTo(other.number);
    }
}
~~~

Ở đây **Comparable<NumberOrder<T>>** có type argument là **toàn bộ kiểu tham số hóa NumberOrder<T>**; vì vậy chữ ký override là **compareTo(NumberOrder<T> other)**, không phải compareTo(T). Bound trên T giúp phần thân có thể gọi number.compareTo(other.number).

Dù Number là class và Comparable là interface, khai báo type parameter vẫn dùng **extends**. Nếu có class bound, nó đứng trước interface bounds, phân tách bằng **&**.

## 12. Ví dụ chạy: kế thừa interface generic, nhiều kiểu và một subclass cố định

Lưu thành **GenericHierarchyDemo.java**:

~~~java
public class GenericHierarchyDemo {
    interface Identified<ID> {
        ID id();
    }

    interface Labeled<ID> extends Identified<ID> {
        String label();
    }

    static class Item<K, V> implements Labeled<K> {
        private final K key;
        private final V value;
        private final String label;

        Item(K key, V value, String label) {
            this.key = key;
            this.value = value;
            this.label = label;
        }

        @Override
        public K id() {
            return key;
        }

        @Override
        public String label() {
            return label;
        }

        V value() {
            return value;
        }
    }

    static class StringItem extends Item<String, Integer> {
        StringItem(String id, Integer value) {
            super(id, value, "fixed");
        }
    }

    public static void main(String[] args) {
        Labeled<String> a = new Item<>("A-1", 7, "item");
        System.out.println(a.id() + " / " + a.label());

        Item<Long, String> b = new Item<>(42L, "done", "task");
        System.out.println(b.id() + " -> " + b.value());

        StringItem c = new StringItem("S", 9);
        System.out.println(c.id() + ":" + c.value());
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac -encoding UTF-8 GenericHierarchyDemo.java
java GenericHierarchyDemo
~~~

Stdout:

~~~text
A-1 / item
42 -> done
S:9
~~~

Ở chương trình này:

1. **Labeled<ID> extends Identified<ID>** chuyển tiếp ID từ interface con lên interface cha.
2. **Item<K, V> implements Labeled<K>** chỉ truyền K cho interface; V là type parameter bổ sung dùng cho payload.
3. **Labeled<String> a** chỉ phơi bày các method do Labeled<String> và Identified<String> bảo đảm. Vì vậy a.id() trả String, a.label() trả String; còn method riêng value() của Item không thuộc API của biến a.
4. **Item<Long, String>** áp dụng K = Long và V = String; chỉ K tham gia hợp đồng Labeled<K>.
5. **StringItem extends Item<String, Integer>** cố định type arguments, đồng thời kế thừa implementation của Labeled<String>.

## 13. Tính bất biến khi sử dụng interface generic

Nếu class implements Service<T>, việc dùng Class<String> tạo ra quan hệ với Service<String>. Tuy nhiên, Java không xem **Service<String>** tự động là subtype của **Service<Object>**, dù String là subtype của Object.

~~~java
interface Service<T> {
    T result();
}

class ServiceImpl<T> implements Service<T> {
    private final T value;

    ServiceImpl(T value) {
        this.value = value;
    }

    @Override
    public T result() {
        return value;
    }
}
~~~

Các phép gán đúng:

~~~java
// Minh họa trong phương thức:
Service<String> text = new ServiceImpl<>("ok");
String a = text.result();

Service<Integer> count = new ServiceImpl<>(123);
Integer b = count.result();
~~~

Phép gán sau sai:

~~~java
// Cố ý sai, không biên dịch:
// Service<Object> everything = new ServiceImpl<String>("ok");
~~~

**Service<String>** không là subtype của Service<Object>. Đây là tính **invariance** của generic type arguments. Nếu chỉ cần đọc Number từ một service đang giữ một subtype của Number, dùng wildcard ở nơi khai báo tham chiếu:

~~~java
Service<? extends Number> numeric = new ServiceImpl<>(10);
Number n = numeric.result();
~~~

Đây là cách dùng wildcard **tại nơi sử dụng kiểu**. Nó khác với mệnh đề implements trong khai báo class.

## 14. Vì sao không viết implements Interface<?>?

Mã sau không hợp lệ:

~~~java
interface Receiver<T> {
    void accept(T input);
}

// Cố ý sai:
// class UnknownReceiver implements Receiver<?> {
//     public void accept(Object input) {}
// }
~~~

Dấu **?** là wildcard type argument phù hợp ở nhiều vị trí **sử dụng** parameterized type; không được dùng làm type argument trực tiếp trong phần khai báo superinterface **implements Receiver<?>**. Một class phải chỉ ra hợp đồng nó sẽ triển khai thông qua type arguments thích hợp.

Nếu muốn class linh hoạt:

~~~java
class GenericReceiver<T> implements Receiver<T> {
    @Override
    public void accept(T input) {
        System.out.println(input);
    }
}
~~~

Nếu muốn class chỉ nhận Object:

~~~java
class AnyObjectReceiver implements Receiver<Object> {
    @Override
    public void accept(Object input) {
        System.out.println(input);
    }
}
~~~

**Receiver<T>** và **Receiver<Object>** không thể tùy tiện thay thế nhau: T có thể là String, Integer hoặc một reference type khác. **Receiver<?>** có thể là kiểu biến ở nơi sử dụng khi bạn chưa biết type argument, nhưng không đồng nghĩa với Receiver<Object>.

## 15. Không thể triển khai hai chuyên biệt khác nhau của cùng generic interface

Ví dụ:

~~~java
interface Action<T> {
    void run(T value);
}

interface TextAction extends Action<String> {}
interface CountAction extends Action<Integer> {}

// Cố ý sai, không biên dịch:
// class Impossible implements TextAction, CountAction {}
~~~

Một class không thể đồng thời kế thừa Action<String> và Action<Integer> từ hai đường superinterfaces như trên. Generic interface đã bị chuyên biệt theo hai type arguments khác nhau; Java cấm xung đột hợp đồng kiểu này. Đây không chỉ là vấn đề thiếu method, mà là một giới hạn của hệ thống kiểu generic và erasure.

Có thể sử dụng **composition** để một class điều phối hai trách nhiệm khác nhau:

~~~java
class ActionCoordinator {
    private final Action<String> text;
    private final Action<Integer> count;

    ActionCoordinator(Action<String> text, Action<Integer> count) {
        this.text = text;
        this.count = count;
    }

    void text(String value) {
        text.run(value);
    }

    void count(Integer value) {
        count.run(value);
    }
}
~~~

ActionCoordinator giữ hai dependency có kiểu rõ ràng mà không phải cùng implements Action hai lần. Nếu hai vai trò thực sự tách biệt, thiết kế hai interface khác nhau cũng có thể phù hợp.

## 16. Raw type, erasure và bridge method

Một mệnh đề implements có thể dùng *raw type* của generic interface trong mã cũ:

~~~java
interface DataHolder<T> {
    void set(T value);
    T get();
}

// Raw type: chỉ minh họa tương thích mã cũ.
class RawHolder implements DataHolder {
    private Object data;

    @Override
    public void set(Object value) {
        data = value;
    }

    @Override
    public Object get() {
        return data;
    }
}
~~~

**implements DataHolder** bỏ type argument, dẫn đến việc sử dụng các chữ ký sau erasure như Object khi type variable không bound. Raw type làm mất nhiều kiểm tra an toàn kiểu, có thể tạo cảnh báo *unchecked* khi chuyển giữa raw và parameterized reference.

Trong code mới, nên viết rõ một trong hai ý định:

- **class Holder<T> implements DataHolder<T>**: class generic giữ cùng type variable.
- **class ObjectHolder implements DataHolder<Object>**: triển khai chuyên biệt cho Object.

Một hạn chế runtime liên quan là **type erasure**: generic type variables thường được erase thành cận trên (Object nếu không có cận). Nếu class triển khai interface bằng kiểu cụ thể và override một method, compiler có thể tạo **bridge method** để duy trì dispatch đúng sau erasure.

~~~java
interface Provider<T> {
    T supply();
}

class TextProvider implements Provider<String> {
    @Override
    public String supply() {
        return "Java";
    }
}
~~~

TextProvider viết method supply() trả String. Compiler có thể sinh bridge method có chữ ký erased trả Object để đáp ứng cơ chế JVM. Lập trình viên không tự viết bridge này; ở Java source, tham chiếu **Provider<String>** vẫn trả String với kiểm tra kiểu biên dịch.

Điều đó không có nghĩa cứ gọi **getClass()** sẽ biết type arguments của một object generic: thông tin generic có thể xuất hiện trong metadata của khai báo, nhưng đối số kiểu gắn với biến/tham chiếu runtime bất kỳ không thể luôn khôi phục được từ runtime class.

## 17. Default method và static method trong generic interface

Generic interface có thể định nghĩa default method sử dụng type parameter của interface:

~~~java
interface Mapper<T> {
    T map(T value);

    default java.util.List<T> mapAll(java.util.List<T> source) {
        java.util.List<T> output = new java.util.ArrayList<>();
        for (T value : source) {
            output.add(map(value));
        }
        return output;
    }

    static <U> U identity(U input) {
        return input;
    }
}

class EchoMapper<T> implements Mapper<T> {
    @Override
    public T map(T value) {
        return value;
    }
}
~~~

EchoMapper<T> cung cấp implementation của map(T), đồng thời kế thừa default method mapAll(List<T>). Với EchoMapper<String>, mapAll nhận List<String> và trả List<String>.

**Mapper.identity** là một static generic method tự khai báo U. Nó không dùng T của interface vì static method không gắn với một instance Mapper<String>. Có thể gọi **Mapper.identity("hello")** hoặc **Mapper.<String>identity("hello")**.

Nếu nhiều superinterfaces mang các default methods xung đột, class triển khai có thể phải tự override để giải quyết theo luật Java. Cơ chế type argument không loại bỏ những quy tắc xung đột của default method.

## 18. Ví dụ chạy độc lập: nhiều superinterfaces và bound

Lưu vào **GenericContractsDemo.java**:

~~~java
public class GenericContractsDemo {
    interface Named<T> {
        T value();
    }

    interface TextView {
        String render();
    }

    interface Formatter<T> {
        String format(T input);

        default String formatNonNull(T input) {
            return input == null ? "(missing)" : format(input);
        }
    }

    static final class Ranked<T extends Number & Comparable<T>>
            implements Named<T>, TextView, Comparable<Ranked<T>> {

        private final T rank;

        Ranked(T rank) {
            this.rank = rank;
        }

        @Override
        public T value() {
            return rank;
        }

        @Override
        public String render() {
            return "rank=" + rank.doubleValue();
        }

        @Override
        public int compareTo(Ranked<T> other) {
            return rank.compareTo(other.rank);
        }
    }

    static final class SimpleFormatter<T> implements Formatter<T> {
        @Override
        public String format(T input) {
            return "[" + input + "]";
        }
    }

    public static void main(String[] args) {
        Ranked<Integer> low = new Ranked<>(3);
        Ranked<Integer> high = new Ranked<>(8);

        System.out.println(low.compareTo(high) < 0);
        System.out.println(low.render());
        System.out.println(high.value());

        Formatter<String> format = new SimpleFormatter<>();
        System.out.println(format.formatNonNull("Java"));
        System.out.println(format.formatNonNull(null));
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac -encoding UTF-8 GenericContractsDemo.java
java GenericContractsDemo
~~~

Stdout:

~~~text
true
rank=3.0
8
[Java]
(missing)
~~~

Chương trình minh họa:

- **Ranked<T>** truyền T vào Named<T>, nhưng implements TextView là interface không generic.
- **Comparable<Ranked<T>>** nhận cả kiểu Ranked<T> làm type argument; compareTo nhận một Ranked<T> khác.
- Bound **T extends Number & Comparable<T>** cho phép vừa gọi doubleValue() vừa so sánh hai giá trị T.
- **SimpleFormatter<T> implements Formatter<T>** có method format(T), còn default method formatNonNull(T) đến từ interface.
- Trong nhánh null, formatNonNull trả "(missing)" mà không gọi format.

## 19. Các lỗi biên dịch phổ biến và cách sửa

**1. Không khai báo T của class**

~~~java
interface Source<T> {
    T get();
}

// Sai vì T chưa tồn tại trong scope:
// class BrokenSource implements Source<T> {}
~~~

Sửa thành **class FixedSource<T> implements Source<T>** và triển khai get(), hoặc dùng **Source<String>** với kiểu cố định.

**2. Đảo thứ tự type arguments**

~~~java
interface PairService<K, V> {
    V get(K key);
}

// Cố ý sai nếu method viết theo K -> V:
// class Wrong<K, V> implements PairService<V, K> {
//     public V get(K key) { return null; }
// }
~~~

Với **PairService<V, K>**, hợp đồng thật là **K get(V key)**; muốn giữ V get(K key) phải dùng **implements PairService<K, V>**.

**3. Sai visibility của method override**

~~~java
interface Writer<T> {
    void write(T value);
}

// Sai:
// class WriterImpl<T> implements Writer<T> {
//     void write(T value) {}
// }
~~~

Method trong interface là public. Phần triển khai phải là **public void write(T value)** và nên dùng @Override.

**4. Đổi T thành Object tùy tiện**

~~~java
interface Returner<T> {
    T run();
}

// Sai:
// class ReturnerImpl<T> implements Returner<T> {
//     public Object run() { return new Object(); }
// }
~~~

Object không bảo đảm là T. Cần triển khai **public T run()** hoặc cố định type argument thành Object nếu API thực sự muốn như vậy.

**5. Dùng interface có bound nhưng class không đáp ứng**

~~~java
interface NumberView<T extends Number> {
    T get();
}

// Sai:
// class AnyView<T> implements NumberView<T> {}
~~~

Sửa thành **class NumberViewImpl<T extends Number> implements NumberView<T>** và triển khai get().

**6. Dùng wildcard trong implements**

**implements Receiver<?>** không hợp lệ; hãy sử dụng **implements Receiver<T>** khi class có khai báo T hoặc **implements Receiver<Object>** cho implementation cố định.

**7. Triển khai cùng interface với hai type arguments khác nhau**

**implements Action<String>, Action<Integer>** không hợp lệ. Cần thay đổi thiết kế để mỗi class không kế thừa cùng generic interface theo hai chuyên biệt khác nhau.

**8. Dùng raw type để bỏ type arguments**

**implements Holder** với Holder generic làm mất quan hệ kiểu. Chọn **Holder<T>** hoặc một chuyên biệt cụ thể và rà soát cảnh báo unchecked.

**9. Giả định type parameters của class được dùng trong static method**

~~~java
class Util<T> {
    // static T echo(T value) { return value; } // Sai.
    static <U> U echo(U value) {
        return value;
    }
}
~~~

Static method tự khai báo U. T của class không thuộc phần static theo cách dùng trên.

**10. Tưởng T extends Object cho phép cast tùy ý**

Ngay cả khi T không bound có cận Object, **Service<String>** không phải Service<Object>. Không ép kiểu vòng qua Object để bỏ qua generic type checking; hãy xem lại hợp đồng và cân nhắc wildcard ở vị trí sử dụng.

## 20. Khi nào nên thiết kế class implements interface generic?

Các trường hợp điển hình:

| Tình huống | Thiết kế gợi ý | Lý do |
| --- | --- | --- |
| Một bộ nhớ chứa bất kỳ kiểu E | MemoryStore<E> implements Store<E> | Giữ hợp đồng E xuyên suốt |
| Lookup với khóa và giá trị độc lập | MapLookup<K, V> implements Lookup<K, V> | Bảo toàn hai quan hệ kiểu |
| Khóa luôn String, giá trị biến đổi | StringKeyLookup<V> implements Lookup<String, V> | Chỉ linh hoạt vị trí cần thiết |
| Chuyển bất kỳ T thành chuỗi | LabelConverter<T> implements Converter<T, String> | Đầu ra cố định String |
| Chỉ đọc chuỗi từ nguồn cố định | TextReader implements Reader<String> | Không cần generic class |
| Cần cùng T giữa hai interface | Channel<T> implements Input<T>, Output<T> | Kết nối hợp đồng đọc/ghi |
| Nhiều hành vi độc lập cho cùng đối tượng | Entity<K,V> implements Identified<K>, Valued<V> | Mỗi interface nhận type argument đúng vai trò |

Một quy trình thiết kế có ích:

1. Bắt đầu từ hợp đồng interface và xác định từng type parameter biểu thị kiểu gì.
2. Quyết định type arguments nào cần linh hoạt qua class và type arguments nào nên cố định.
3. Đặt đúng danh sách **<...>** ngay sau tên class và viết đủ đối số trong implements.
4. Từ interface, thay type parameters bằng type arguments tương ứng để suy ra chính xác chữ ký cần @Override.
5. Kiểm tra bounds, visibility, return types và các superinterfaces thừa kế.
6. Thử khai báo một biến có kiểu interface được tham số hóa và gán một implementation thích hợp. Compiler sẽ phát hiện khi mối quan hệ kiểu không khớp.
7. Xử lý null, exceptions và trạng thái mutable theo contract riêng; generics không bảo đảm các thuộc tính runtime đó.

Ví dụ **Lookup<K, V>** dùng Map.get có thể trả null nếu không thấy khóa. Việc triển khai đúng Lookup<K,V> bảo đảm kiểu trả về V về mặt tĩnh nhưng không tự cam kết giá trị khác null; API nên mô tả rõ điều đó.

## 21. Câu hỏi ôn tập

**Câu 1.** Trong **class DataSource<U> implements Source<U>**, vị trí nào khai báo U và vị trí nào sử dụng U làm type argument?

**Đáp án:** DataSource<U> khai báo type parameter U của class; Source<U> trong implements dùng U đã khai báo làm type argument cho Source.

**Câu 2.** Có cần cùng tên type parameter giữa interface và class không?

**Đáp án:** Không. Interface<A> và Implementation<T> có thể liên kết bằng implements Interface<T>.

**Câu 3.** Nếu interface **Mapper<I, O>** có method **O map(I value)** và class **MapperImpl<T> implements Mapper<T, String>**, chữ ký method triển khai là gì?

**Đáp án:** **public String map(T value)**.

**Câu 4.** Tại sao **class Bad implements Source<T>** sai khi class chưa khai báo T?

**Đáp án:** T không nằm trong phạm vi của Bad. Cần khai báo **class Bad<T>** hoặc chọn một type argument cụ thể.

**Câu 5.** Nếu interface **Bounded<N extends Number>**, có thể viết **class Holder<T> implements Bounded<T>** không?

**Đáp án:** Không, nếu T không có bound tương thích. Cần ví dụ **class Holder<T extends Number> implements Bounded<T>** và các method bắt buộc.

**Câu 6.** Với **class Entry<K,V> implements Identified<K>, Valued<V>**, interface nào dùng K và interface nào dùng V?

**Đáp án:** Identified nhận K, Valued nhận V. Entry<Long, String> thực hiện Identified<Long> và Valued<String>.

**Câu 7.** Một class implements **Labeled<T>** với **Labeled<T> extends Identified<T>** có phải implement id() không?

**Đáp án:** Có nếu id() còn abstract và chưa được cung cấp bởi superclass; vì Labeled<T> kế thừa hợp đồng của Identified<T>.

**Câu 8.** **Service<String>** có tự động gán cho **Service<Object>** được không?

**Đáp án:** Không. Generic types Java mặc định invariant; dùng wildcard ở nơi sử dụng nếu mô hình đọc/ghi cho phép.

**Câu 9.** Tại sao **implements Receiver<?>** không hợp lệ?

**Đáp án:** Wildcard không thể là type argument trực tiếp của superinterface được khai báo trong implements. Dùng Receiver<T> với T do class khai báo hoặc Receiver<ConcreteType>.

**Câu 10.** Có thể triển khai Action<String> và Action<Integer> đồng thời trong một class không?

**Đáp án:** Không, vì cùng generic interface Action bị kế thừa với hai type arguments khác nhau; có thể dùng composition cho hai trách nhiệm.

**Câu 11.** Với **class Ranked<T> implements Comparable<Ranked<T>>**, kiểu tham số compareTo là T hay Ranked<T>?

**Đáp án:** Ranked<T>. Type argument của Comparable là cả kiểu Ranked<T>, nên method nhận đối tượng Ranked<T> khác.

**Câu 12.** Vì sao một implementation cụ thể của Reader<String> có thể có bridge method trong bytecode?

**Đáp án:** Compiler có thể sinh bridge để duy trì dispatch/phù hợp chữ ký sau type erasure; developer chỉ cần viết @Override đúng trong Java source.

## 22. Tổng kết

**class Implementation<T> implements Interface<T>** là cú pháp chuẩn để class dùng chính tham số kiểu của mình nhằm thực hiện một generic interface. Nó tạo một ràng buộc kiểu rõ ràng giữa class và contract, giúp caller sử dụng Implementation<String> như Interface<String> và được kiểm tra kiểu khi biên dịch.

Khi interface có nhiều tham số kiểu, class có thể chuyển tiếp tất cả, chuyển tiếp một phần hoặc kết hợp type variables với type arguments cụ thể. Cần theo dõi các type arguments qua từng tầng extends/implements, triển khai method với @Override đúng chữ ký, đáp ứng bounds và tránh raw type, wildcard trong superinterface cùng xung đột kế thừa các chuyên biệt khác nhau.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="generic-classes-interfaces-007">Record generic và các thành phần được tham số hóa</a>

<details>
<summary>Xem chi tiết</summary>

# Record generic và các thành phần được tham số hóa trong Java

`record` trong Java là một cách khai báo lớp chuyên dùng để mang dữ liệu. **Generic record** kết hợp cấu trúc record với **type parameter**, giúp cùng một khuôn dữ liệu áp dụng cho nhiều loại giá trị mà compiler vẫn kiểm tra quan hệ kiểu. Ví dụ, `Pair<String, Integer>` giữ một khóa kiểu `String` và một giá trị kiểu `Integer`, còn `Pair<Long, String>` dùng chính khai báo record ấy với hai type arguments khác.

Bài học này tập trung vào **cách khai báo record generic, ý nghĩa của các record components được tham số hóa, các thành viên compiler tạo ra, quan hệ giữa type parameter và constructor/accessor, việc implement generic interface, kiểm tra dữ liệu và các giới hạn cần biết khi chạy**. Các ví dụ chạy được bằng Java 17 trở lên.

## 1. Record là gì và vì sao kết hợp với Generics?

Một record khai báo *state description* ở phần header, tức một dãy **record components** có tên và kiểu:

```java
record User(String name, int age) {
}
```

Đây là đoạn khai báo minh họa, chưa có `main`. Từ danh sách components, Java cung cấp những thành viên cần thiết để dùng record như một dữ liệu có cấu trúc: trường thành phần `private final`, accessor có tên đúng bằng component (`name()`, `age()`), canonical constructor, `equals`, `hashCode` và `toString`, trừ khi một số thành viên đó được khai báo tường minh theo quy tắc của record.

Nếu thay mỗi component bằng `Object` để tái sử dụng cho nhiều dữ liệu, caller phải ép kiểu và compiler không còn biết quan hệ giữa phần tử đầu vào/đầu ra. Generic record giải quyết việc đó:

```java
record Box<T>(T value) {
}
```

Ở đây:

- `Box<T>` là **khai báo generic record**; `T` là type parameter của record.
- `T value` là **record component được tham số hóa**: component `value` có kiểu phụ thuộc `T`.
- `Box<String>` và `Box<Integer>` là hai cách **sử dụng** record, cung cấp type arguments cho `T`.
- `value()` của `Box<String>` có kiểu biểu thức `String`; `value()` của `Box<Integer>` có kiểu biểu thức `Integer` ở điểm gọi.
- Hai instantiations không tạo hai lớp JVM khác nhau; Java dùng type erasure cho generics.

Một record thông thường cũng không tự làm dữ liệu bất biến sâu (*deeply immutable*): trường component là final nhưng đối tượng được tham chiếu bởi component có thể vẫn mutable. Đây là điểm rất quan trọng với component kiểu `List<T>`, `Map<K,V>` hoặc mảng.

## 2. Cú pháp cơ bản của generic record

Dạng tổng quát:

```java
record RecordName<T1, T2>(T1 first, T2 second) {
    // Constructor, validation, method hoặc static member nếu cần.
}
```

Đoạn trên là mẫu cú pháp, không phải class Java chạy độc lập. `T1` và `T2` là type parameters khai báo ngay sau tên record. Trong danh sách components, chúng được dùng như các kiểu thông thường.

### Ví dụ hoàn chỉnh: Pair với hai biến kiểu

Tạo tệp `GenericPairDemo.java`:

```java
public class GenericPairDemo {
    record Pair<K, V>(K key, V value) {
    }

    public static void main(String[] args) {
        Pair<String, Integer> score = new Pair<>("Lan", 9);
        Pair<Long, String> status = new Pair<>(100L, "OK");

        String student = score.key();
        Integer mark = score.value();

        System.out.println(student + ": " + mark);
        System.out.println(status.key() + " -> " + status.value());
        System.out.println(score);
    }
}
```

Biên dịch và chạy bằng JDK 17+:

```bash
javac GenericPairDemo.java
java GenericPairDemo
```

`stdout`:

```text
Lan: 9
100 -> OK
Pair[key=Lan, value=9]
```

Giải thích:

1. `Pair<K,V>` khai báo hai type parameters độc lập: `K` là kiểu của component `key`, `V` là kiểu của component `value`.
2. `Pair<String,Integer>` ràng buộc `K=String`, `V=Integer` cho biến `score`. Compiler cho phép gán `score.key()` cho `String`, `score.value()` cho `Integer`.
3. `Pair<Long,String>` là cùng một record được sử dụng với loại dữ liệu khác.
4. Cú pháp diamond `new Pair<>(...)` giúp compiler suy luận type arguments từ ngữ cảnh và đối số constructor.
5. Accessor của record là `key()` và `value()`, **không tự tạo** `getKey()` hoặc `getValue()`.
6. `toString()` do record sinh hiển thị tên record và từng component theo cấu trúc được khai báo; ở ví dụ này là `Pair[key=Lan, value=9]`.

Nếu thay `new Pair<>("Lan", 9)` bằng `new Pair<>(9, "Lan")` mà vẫn gán vào `Pair<String,Integer>` thì compiler từ chối vì hai type arguments không tương thích hợp đồng tại điểm gán.

## 3. Lần theo type parameter ở từng record component

Một generic record có thể dùng type parameter ở một hoặc nhiều components, và có thể kết hợp với component kiểu cụ thể:

```java
record Event<ID, P>(
        ID id,
        String topic,
        P payload,
        java.time.Instant createdAt) {
}
```

Đây là phần khai báo minh họa. Lập bảng đọc chữ ký:

| Thành phần | Kiểu khai báo | Phụ thuộc vào |
| --- | --- | --- |
| `id` | `ID` | Type argument thứ nhất của `Event<ID,P>` |
| `topic` | `String` | Kiểu cố định, không phụ thuộc type argument |
| `payload` | `P` | Type argument thứ hai của `Event<ID,P>` |
| `createdAt` | `Instant` | Kiểu cố định |
| `id()` | `ID` | Cùng `ID` với component `id` |
| `payload()` | `P` | Cùng `P` với component `payload` |

Với `Event<Long, String>`, compiler xem `id()` trả `Long` và `payload()` trả `String`. Với `Event<String, byte[]>`, `id()` trả `String` còn `payload()` trả `byte[]`. Dù type argument của `Event` khác nhau, `topic()` luôn trả `String` và `createdAt()` luôn trả `Instant`.

**Cách đọc:** mỗi lần gặp `T` hoặc tên biến kiểu khác trong header, hãy tìm khai báo `<T>` ngay sau tên record. Những vị trí dùng cùng một `T` trong record cùng chịu một lựa chọn kiểu. Một component không sử dụng biến kiểu vẫn có kiểu đã khai báo như bình thường.

### Thành phần generic lồng nhau

Có thể khai báo các record components như:

```java
record Batch<T>(
        String label,
        java.util.List<T> items,
        java.util.Map<String, T> index) {
}
```

`T` được sử dụng ở hai generic type arguments: `List<T>` và giá trị `V` của `Map<String,T>`. Với `Batch<Integer>`, phương thức `items()` có kiểu `List<Integer>` và `index()` có kiểu `Map<String,Integer>`. Không phải các generic trong component khai báo lại một `T` mới: chúng đang dùng `T` đã có ở header record.

Một component có thể là `List<List<T>>`, `Map<String, List<T>>` hay `Optional<T>`; đọc từ generic bên ngoài vào bên trong.

## 4. Các thành viên Java cung cấp từ generic record components

Xét:

```java
record Box<T>(T value) {
}
```

Ở mức mô hình đọc API, bạn có thể hình dung các thành viên tương ứng:

```java
// Đây chỉ là mô hình giải thích, không phải cách
// Java chuyển record thành source code.
final class Box<T> {
    private final T value;

    public Box(T value) {
        this.value = value;
    }

    public T value() {
        return value;
    }

    // equals(Object), hashCode(), toString() được cung cấp.
}
```

Mô hình trên **không thay thế toàn bộ ngữ nghĩa của record**, nhưng giúp thấy rõ generic type parameter đi từ component tới constructor và accessor.

### 4.1. Component field là final

Mỗi record component tương ứng với một trường instance `private final`. Sau khi canonical constructor hoàn tất, không được gán lại trường đó:

```java
record Count<T>(T value) {
    // void replace(T x) {
    //     this.value = x; // Lỗi: trường component là final.
    // }
}
```

Generic typing và final field là hai tính chất độc lập. `T` kiểm tra **loại giá trị**, còn `final` ngăn **đổi tham chiếu trong trường**. Nếu `T` là một kiểu đối tượng mutable, caller vẫn có thể thay đổi trạng thái bên trong đối tượng mà record đang giữ.

### 4.2. Accessor có kiểu phụ thuộc type argument

Với `record Box<T>(T value)`, accessor `value()` có kiểu trả về `T`. Do đó:

```java
Box<String> a = new Box<>("Java");
String text = a.value();

// Integer wrong = a.value(); // Lỗi biên dịch.
```

Đây là các dòng sử dụng minh họa; để chạy, đặt trong class/method có khai báo `Box<T>`. Không cần viết cast `(String) a.value()` ở code sử dụng có kiểu đúng.

### 4.3. Canonical constructor có danh sách tham số theo components

Record `Pair<K,V>(K key,V value)` có canonical constructor tương ứng hai component, theo đúng thứ tự. Có thể đọc quan hệ constructor như `Pair(K key, V value)`. Đây là quan hệ type ở nguồn, không phải hai constructor runtime khác nhau cho `String` và `Integer`.

Một record có thể khai báo constructor bổ sung, nhưng constructor không chuẩn (*non-canonical*) phải ủy quyền tới constructor khác của cùng record thông qua `this(...)` để tất cả components được khởi tạo đúng cách.

### 4.4. equals, hashCode và toString

Record cung cấp so sánh dựa trên kiểu record và giá trị components, cùng hàm băm và biểu diễn văn bản tương ứng. Với `record Pair<K,V>(K key,V value)`, `Pair<String,Integer>` và `Pair<String,Number>` vẫn là các tham số hóa khác nhau của **cùng lớp runtime Pair**. Khi components thực chứa các giá trị bằng nhau, `equals` có thể trả true dù khai báo generic ở hai biến khác nhau.

Ngược lại, nếu component là một mảng, phép so sánh component reference của mảng không tự biến thành so sánh từng phần tử mảng. Nếu cần value equality theo nội dung mảng, phải thiết kế xử lý phù hợp (chẳng hạn dùng một cấu trúc immutable với `equals` theo nội dung hoặc tự hiện thực các method record một cách nhất quán).

## 5. Canonical constructor và compact constructor trong generic record

Record có thể kiểm tra điều kiện của components trước khi gán vào trường. Cú pháp **compact constructor** đặc biệt hữu ích vì không cần lặp lại danh sách tham số.

### Ví dụ hoàn chỉnh: bản sao List bất biến

Tạo `GenericBatchDemo.java`:

```java
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class GenericBatchDemo {
    record Batch<T>(String name, List<T> items) {
        Batch {
            Objects.requireNonNull(name, "name");
            items = List.copyOf(items);
        }
    }

    public static void main(String[] args) {
        List<String> mutable = new ArrayList<>(
                List.of("Java", "Kotlin"));

        Batch<String> batch = new Batch<>("languages", mutable);
        mutable.add("Scala");

        System.out.println(batch.name());
        System.out.println(batch.items());
        System.out.println(mutable);
    }
}
```

Chạy:

```bash
javac GenericBatchDemo.java
java GenericBatchDemo
```

`stdout`:

```text
languages
[Java, Kotlin]
[Java, Kotlin, Scala]
```

Giải thích:

- `Batch<T>` khai báo một component kiểu cụ thể `String name` và một component generic lồng `List<T> items`.
- `Batch { ... }` là compact canonical constructor; nó được đặt trong thân record, **không** có danh sách tham số tường minh.
- Trong compact constructor, tên `name` và `items` tham chiếu tới các **tham số ngầm** tương ứng với record components. Gán `items = List.copyOf(items)` thay đổi giá trị tham số sẽ được lưu vào trường `items` sau phần thân constructor.
- `List.copyOf` tạo danh sách không sửa được và, khi cần, một bản sao chứa các phần tử đang có. Do đó sửa `mutable` sau khi tạo `Batch` không thay đổi số phần tử trong `batch.items()`.
- `List.copyOf` không chấp nhận phần tử null; nếu input null hoặc chứa null, thao tác có thể ném `NullPointerException`. Đây là hành vi của API, **không** phải do `T` tự ngăn null.
- Bản sao trên là **shallow copy**: nếu `T` là một đối tượng mutable (ví dụ `StringBuilder`), việc thay đổi nội dung object đó vẫn có thể quan sát qua danh sách trong record.

Trong compact constructor, **không** gán trực tiếp `this.items = ...`. Việc khởi tạo component fields được thực hiện sau khi compact constructor body hoàn tất, dùng các tham số tương ứng.

### Canonical constructor tường minh

Nếu muốn tự khai báo canonical constructor với đầy đủ tham số, phải khởi tạo các component fields chính xác:

```java
import java.util.Objects;

record NonNullValue<T>(T value) {
    NonNullValue(T value) {
        this.value = Objects.requireNonNull(value);
    }
}
```

Đây là đoạn khai báo Java hợp lệ; không có `main`. Canonical constructor chứa đúng danh sách component parameters. Không được vừa khai báo compact canonical constructor vừa khai báo một canonical constructor khác trong cùng record.

### Kiểm tra ràng buộc trong constructor

Một generic record `Range<T>` không tự biết mọi `T` có thể so sánh. Nếu constructor cần so sánh endpoints, khai báo bound ngay tại type parameter:

```java
record Range<T extends Comparable<? super T>>(T start, T end) {
    Range {
        java.util.Objects.requireNonNull(start);
        java.util.Objects.requireNonNull(end);
        if (start.compareTo(end) > 0) {
            throw new IllegalArgumentException("start > end");
        }
    }
}
```

Bound `Comparable<? super T>` cho compiler biết `T` có khả năng so sánh với một `T` khác, trong phạm vi quan hệ tương thích. Nếu `T` không có bound thích hợp, lời gọi `start.compareTo(end)` không được compiler bảo đảm.

## 6. Bounded type parameter trên generic record

Có thể khai báo các ràng buộc tại chính record header:

```java
record Measurement<N extends Number>(String unit, N value) {
    double asDouble() {
        return value.doubleValue();
    }
}
```

Ở đây:

1. `N` được khai báo tại `Measurement<N extends Number>`.
2. `Number` là cận trên: `N` phải phù hợp quan hệ subtype với `Number`.
3. Component `value` dùng `N`; accessor `value()` trả `N` tại compile time.
4. Instance method `asDouble()` trả primitive `double`, không phải `N`. Compiler cho phép gọi `doubleValue()` vì bound đã cam kết điều đó.

Ví dụ chạy được trong `BoundedRecordDemo.java`:

```java
public class BoundedRecordDemo {
    record Measurement<N extends Number>(String unit, N value) {
        double asDouble() {
            return value.doubleValue();
        }
    }

    record Range<T extends Comparable<? super T>>(T start, T end) {
        Range {
            if (start.compareTo(end) > 0) {
                throw new IllegalArgumentException("start > end");
            }
        }
    }

    public static void main(String[] args) {
        Measurement<Integer> count = new Measurement<>("items", 12);
        Measurement<Double> weight = new Measurement<>("kg", 2.5);
        Range<Integer> interval = new Range<>(1, 10);

        System.out.println(count.asDouble());
        System.out.println(weight.asDouble());
        System.out.println(interval.start() + ".." + interval.end());
    }
}
```

Chạy:

```bash
javac BoundedRecordDemo.java
java BoundedRecordDemo
```

`stdout`:

```text
12.0
2.5
1..10
```

`Measurement<String>` không hợp lệ vì `String` không kế thừa `Number`. Trường hợp `Range<Integer>` hợp lệ vì `Integer` có quan hệ `Comparable<Integer>` tương ứng.

### Multiple bounds

Bạn có thể dùng nhiều bound cho một type parameter:

```java
record Ranked<T extends Number & Comparable<T>>(T value) {
}
```

Nếu có cận class, nó phải đứng trước các cận interface; các cận interface nối bằng `&`. `extends` dùng cho cả class và interface bound trong khai báo type parameter. Khi chỉ mới cần giữ dữ liệu mà không gọi hành vi đặc thù, **không cần** áp đặt bound cho mọi `T`.

## 7. Generic record implements generic interface

Record không được `extends` một class tùy ý; nó trực tiếp kế thừa `java.lang.Record` và là final. Tuy vậy, record **có thể implements interface**, kể cả generic interface, và nhờ đó tham gia hợp đồng trừu tượng.

Tạo `GenericRecordInterfaceDemo.java`:

```java
public class GenericRecordInterfaceDemo {
    interface Identified<ID> {
        ID id();
    }

    record Entity<ID, T>(ID id, T payload)
            implements Identified<ID> {
    }

    static <ID> void printId(Identified<ID> value) {
        System.out.println(value.id());
    }

    public static void main(String[] args) {
        Entity<Long, String> message =
                new Entity<>(42L, "queued");

        printId(message);
        System.out.println(message.payload());
    }
}
```

Chạy:

```bash
javac GenericRecordInterfaceDemo.java
java GenericRecordInterfaceDemo
```

`stdout`:

```text
42
queued
```

Giải thích:

- `Identified<ID>` khai báo hợp đồng `ID id()`.
- `Entity<ID,T>` khai báo hai type parameters riêng của record.
- `implements Identified<ID>` **truyền** type parameter `ID` của record làm type argument của interface.
- Record component `ID id` tạo accessor `id()` tương ứng và đáp ứng method trong interface; không phải viết thêm `getId()`.
- Method `printId` khai báo type parameter `ID` riêng; khi nhận `Entity<Long,String>`, compiler có thể suy luận kiểu tương ứng của ID.
- Không phải interface nào cũng được record đáp ứng tự động chỉ bằng component. Tên, kiểu trả về và hợp đồng method phải thật sự tương thích; nếu interface yêu cầu `getId()`, component `id` không sinh ra method đó.

Một record cũng có thể implement nhiều interfaces. Vì bản thân record là final, không thể viết `class Derived extends Entity<...>` để kế thừa nó.

## 8. Generic instance method trong record

Ngoài dùng type parameter của record, một instance method còn có thể khai báo **type parameter mới** ở phạm vi phương thức. Điểm này cho phép record mang `T` nhưng một phép biến đổi tạo ra `R`.

Tạo `GenericRecordMethodDemo.java`:

```java
import java.util.function.Function;

public class GenericRecordMethodDemo {
    record Box<T>(T value) {
        <R> Box<R> map(Function<? super T, ? extends R> converter) {
            return new Box<>(converter.apply(value));
        }
    }

    public static void main(String[] args) {
        Box<String> text = new Box<>("Java");
        Box<Integer> size = text.map(String::length);
        Box<String> description = size.map(n -> "length=" + n);

        System.out.println(size.value());
        System.out.println(description.value());
    }
}
```

Chạy:

```bash
javac GenericRecordMethodDemo.java
java GenericRecordMethodDemo
```

`stdout`:

```text
4
length=4
```

Đọc từng ràng buộc:

| Ký hiệu | Được khai báo ở | Ý nghĩa tại lời gọi `text.map(String::length)` |
| --- | --- | --- |
| `T` | `record Box<T>` | Kiểu của giá trị đang giữ: `String` |
| `R` | `<R> Box<R> map(...)` | Kiểu kết quả phép biến đổi: `Integer` |
| `? super T` | Wildcard dùng trong `Function` | Hàm nhận được giá trị kiểu `T` |
| `? extends R` | Wildcard dùng trong `Function` | Hàm cho giá trị tương thích `R` |

`Box<String>` và `Box<Integer>` là cùng khai báo record, nhưng lời gọi `map` tạo một đối tượng mới mang một type argument khác ở compile time. Điều này không làm thay đổi đối tượng `text`.

### Static method và type parameter của record

Method static không thể trực tiếp dùng `T` của `record Box<T>` làm kiểu độc lập vì `T` gắn với instantiation của record, còn method static thuộc lớp. Nếu muốn một static factory generic, khai báo type parameter của **method**:

```java
record Box<T>(T value) {
    static <U> Box<U> of(U value) {
        return new Box<>(value);
    }
}
```

Đoạn trên hợp lệ nhưng không có `main`. Ví dụ `Box.of("hello")` có thể suy luận `U=String`. `U` tại method là một biến kiểu khác với `T` ở record; type argument của `Box<U>` được xác định từ `U` khi tạo kết quả.

## 9. Type inference khi tạo generic record

Giống generic class khác, generic record có thể dùng diamond `<>` để Java suy luận type arguments. Có hai nguồn suy luận quan trọng: **kiểu mục tiêu** và **đối số constructor**.

### Chọn kiểu theo target

```java
record Pair<K, V>(K key, V value) {}

Pair<String, Integer> p = new Pair<>("A", 1);
```

Compiler dùng kiểu mục tiêu `Pair<String,Integer>` và dữ liệu truyền vào để áp dụng `K=String`, `V=Integer`. Nếu thay tham số thành kiểu không tương thích, `javac` báo lỗi.

### Chọn kiểu theo initializer

```java
var p = new Pair<>("A", 1);
```

Với Java 17, `var` suy luận kiểu biến cục bộ từ initializer; ở đây có thể suy ra `Pair<String,Integer>`. `var` không làm kiểu biến trở thành dynamic và không khai báo thêm type parameter. Một khi suy luận xong, compiler vẫn kiểm tra các accessor của `p` theo kiểu tĩnh đã xác định.

### Explicit type arguments và raw type

```java
Pair<String, Integer> a = new Pair<String, Integer>("A", 1);
Pair<String, Integer> b = new Pair<>("A", 1);
```

Hai cách này khai báo hợp đồng kiểu như nhau cho `a` và `b`. Cách viết diamond ngắn hơn.

Tránh kiểu raw:

```java
Pair raw = new Pair("A", 1);
```

Raw type làm suy giảm kiểm tra kiểu của generics và có thể phát cảnh báo `rawtypes`/`unchecked`. Đừng xem record là ngoại lệ: generic record vẫn chịu quy tắc raw types như generic class thông thường.

### Không hỗ trợ primitive type argument

`Pair<int, double>` sai cú pháp về kiểu generic. Cần dùng wrapper `Pair<Integer, Double>`; boxing/unboxing giúp chuyển các primitive values tại nơi phù hợp.

## 10. Generic record chứa collection và kiểu lồng nhau

Generic record không chỉ có component trực tiếp `T`; nó có thể giữ generic collection với các type arguments được liên kết.

Tạo `NestedGenericRecordDemo.java`:

```java
import java.util.List;
import java.util.Map;

public class NestedGenericRecordDemo {
    record Catalog<K, V>(
            Map<K, List<V>> entries,
            String label) {
    }

    public static void main(String[] args) {
        Map<String, List<Integer>> scores = Map.of(
                "Lan", List.of(8, 9),
                "Mai", List.of(7));

        Catalog<String, Integer> catalog =
                new Catalog<>(scores, "scores");

        List<Integer> lan = catalog.entries().get("Lan");
        System.out.println(catalog.label());
        System.out.println(lan);
        System.out.println(lan.get(0));
    }
}
```

Chạy:

```bash
javac NestedGenericRecordDemo.java
java NestedGenericRecordDemo
```

`stdout`:

```text
scores
[8, 9]
8
```

Đọc kiểu theo từng tầng:

1. `Catalog<K,V>` khai báo `K` và `V`.
2. Component `entries` có kiểu `Map<K,List<V>>`.
3. Với `Catalog<String,Integer>`, accessor `entries()` có kiểu `Map<String,List<Integer>>`.
4. Phương thức `Map.get(Object)` trả `V` của map, lúc này là `List<Integer>`; lưu ý `Map.get` nhận `Object` theo API Java, dù key của map được khai báo `String`.
5. `List<Integer>.get(0)` có kiểu biểu thức `Integer`.

Tương tự, component `Optional<T>` giúp biểu diễn việc có/không có giá trị thông qua API `Optional`, nhưng generic type tự nó không cấm component null; muốn cấm null cần kiểm tra riêng hoặc chọn invariant.

## 11. Tính bất biến của generic type arguments

Dù `Integer extends Number`, không thể gán trực tiếp `Pair<String,Integer>` sang `Pair<String,Number>`:

```java
record Pair<K, V>(K key, V value) {}

Pair<String, Integer> integerPair = new Pair<>("A", 7);
// Pair<String, Number> numberPair = integerPair; // Sai.
```

Generic type arguments của Java về cơ bản **invariant**. Điều này vẫn đúng với record dù các component fields là final và record không có setter; compiler áp dụng quy tắc kiểu generic của `Pair<K,V>`, không tự chuyển thành covariance chỉ vì record immutable ở cấp trường.

### Dùng wildcard khi cần đọc linh hoạt

```java
Pair<String, ? extends Number> numberView = integerPair;
Number number = numberView.value();
```

Đây là các dòng minh họa đặt trong phạm vi có `Pair<K,V>` và `integerPair`. Wildcard `? extends Number` cho phép nhận record có value là một subtype của `Number`. Khi đọc `numberView.value()`, compiler cho phép coi kết quả là `Number`.

Không thể tùy ý thay đổi kiểu component của một record đã tạo vì không có setter; khi cần một record khác, hãy tạo đối tượng mới với kiểu đích phù hợp. Nếu muốn chấp nhận nhiều kiểu khi **thiết kế method**, wildcard trên tham số có thể hữu ích, ví dụ `Pair<String, ? extends Number>` hoặc `Pair<?, ?>`.

### Raw record không phải “mọi Pair hợp lệ”

`Pair` raw bỏ mất quan hệ kiểu compile time. `Pair<?,?>` giữ thông tin rằng record có hai type parameters không biết cụ thể và giúp tránh nhiều thao tác unchecked. Nếu chỉ cần in record hoặc đọc components dưới dạng `Object`, wildcard phù hợp hơn raw type.

## 12. equals và type arguments: phải xét dữ liệu runtime

Generic typing kiểm tra ở compile time, nhưng hai cách tham số hóa cùng một record không làm Java tạo lớp runtime riêng. Ví dụ:

Tạo `RecordEqualityDemo.java`:

```java
public class RecordEqualityDemo {
    record Pair<K, V>(K key, V value) {
    }

    public static void main(String[] args) {
        Pair<String, Integer> exact = new Pair<>("A", 1);
        Pair<String, Number> wide = new Pair<>("A", 1);

        System.out.println(exact.getClass().equals(wide.getClass()));
        System.out.println(exact.equals(wide));
        System.out.println(exact.hashCode() == wide.hashCode());
    }
}
```

Chạy `javac RecordEqualityDemo.java` và `java RecordEqualityDemo`:

```text
true
true
true
```

Hai đối tượng cùng runtime class `Pair`, với components chứa chuỗi `"A"` và số nguyên boxed `Integer(1)` bằng nhau. Việc một biến có type argument `V=Integer`, biến khác có `V=Number` không buộc `equals` trả false.

**Không suy rộng** điều này thành “hai loại generic record khác tên nhưng components giống nhau sẽ bằng nhau”: record equality chỉ so với đối tượng của cùng record class. Cũng không suy luận `Integer(1)` và `Long(1L)` bằng nhau theo `equals`; chúng là wrapper khác kiểu với `equals` không tương đương.

### Những trường hợp không bất biến sâu

- Component là `ArrayList<T>`: không thể gán lại component field, nhưng có thể `add` qua tham chiếu nếu list mutable.
- Component là `Map<K,V>`: key/value có thể được thay đổi qua mutable map, trừ khi đã sao chép/bọc với chính sách thích hợp.
- Component là `T[]`: mảng có thể bị thay đổi phần tử, nên muốn bảo vệ cần clone khi nhận và khi trả.
- Component là bản thân một object `T` mutable: việc giữ final reference không đóng băng trạng thái của object đó.

Khi record là DTO chia sẻ giữa nhiều phần của chương trình, hãy công bố rõ tính mutable của component và dùng **defensive copy** ở ranh giới phù hợp.

## 13. Defensive copy và giới hạn “immutable record”

Đây là lỗi phổ biến nhất khi record component là collection generic:

```java
record BadSnapshot<T>(java.util.List<T> items) {}
```

Tạo `BadSnapshot<String> snapshot = new BadSnapshot<>(mutableList)` không tự sao chép danh sách. `snapshot.items()` trả đúng tham chiếu đã lưu. Nếu caller còn giữ danh sách và thay đổi nó, snapshot sẽ quan sát thấy thay đổi đó.

### Thiết kế tốt hơn cho component `List<T>`

```java
record Snapshot<T>(java.util.List<T> items) {
    Snapshot {
        items = java.util.List.copyOf(items);
    }
}
```

`List.copyOf` tạo danh sách không sửa được; đồng thời đầu vào không được null và không được chứa phần tử null. Nếu đầu vào đã là danh sách immutable phù hợp, triển khai có thể tái sử dụng thay vì luôn cấp phát bản sao; hợp đồng đáng tin là snapshot kết quả không thay đổi khi sửa source list thông qua alias.

Tuy nhiên, nếu từng `T` là đối tượng mutable, `List.copyOf` chỉ bảo vệ cấu trúc danh sách, **không clone từng phần tử**. Cần clone sâu hoặc biểu diễn immutable của từng phần tử nếu yêu cầu bài toán thực sự là deep immutability.

### Với mảng, cần phòng vệ ở cả hai đầu

```java
record Bytes(byte[] data) {
    Bytes {
        data = data.clone();
    }

    @Override
    public byte[] data() {
        return data.clone();
    }
}
```

Đoạn này là khai báo hợp lệ nhưng không có `main`. Constructor clone dữ liệu lúc nhận; accessor clone lần nữa để caller không sửa trực tiếp mảng nội bộ. Trường hợp array component còn đặt ra vấn đề `equals`/`hashCode` của record: mặc định chúng không tự so sánh nội dung mảng; nếu muốn dùng record làm giá trị có equality theo byte, cần thiết kế overrides nhất quán hoặc chọn biểu diễn khác.

Bài học: **type parameter T**, **component final** và **immutability của object graph** là ba khái niệm khác nhau.

## 14. Type erasure và reflection đối với generic record

Record là class ở JVM; generic record chịu **type erasure** giống các generic class khác. `Box<String>` và `Box<Integer>` không có hai class runtime chuyên biệt. Tuy nhiên metadata generic của **khai báo record và components** có thể được reflection đọc.

Tạo `RecordReflectionDemo.java`:

```java
import java.lang.reflect.RecordComponent;
import java.util.List;

public class RecordReflectionDemo {
    record Container<T>(List<T> values) {
    }

    public static void main(String[] args) {
        Container<String> a = new Container<>(List.of("A"));
        Container<Integer> b = new Container<>(List.of(1));

        System.out.println(a.getClass().equals(b.getClass()));
        System.out.println(Container.class.isRecord());

        RecordComponent component =
                Container.class.getRecordComponents()[0];

        System.out.println(component.getName());
        System.out.println(component.getType().getTypeName());
        System.out.println(component.getGenericType().getTypeName());
    }
}
```

Chạy:

```bash
javac RecordReflectionDemo.java
java RecordReflectionDemo
```

`stdout`:

```text
true
true
values
java.util.List
java.util.List<T>
```

Giải thích:

- `getClass()` cho thấy hai objects cùng class `Container`.
- `Class.isRecord()` cho phép nhận biết class này là record.
- `Class.getRecordComponents()` trả các `RecordComponent` theo thứ tự components khai báo. Ví dụ chỉ có `values` nên lấy index `0`.
- `getName()` trả tên component.
- `getType()` trả class runtime của kiểu component đã xóa, ở đây là `java.util.List`.
- `getGenericType()` mô tả generic type đã khai báo `List<T>`, vì chữ ký generic của khai báo có thể được ghi trong class file.

Reflection đọc `java.util.List<T>` không có nghĩa instance `a` mang `T=String` hay instance `b` mang `T=Integer` để JVM tự cưỡng chế kiểu phần tử. Type erasure vẫn áp dụng tại runtime; metadata khai báo và type argument của từng tham chiếu là hai nguồn thông tin khác nhau.

Đừng dùng `getClass()` để phân biệt `Container<String>` và `Container<Integer>`. Nếu một cơ chế serialization cần hiểu `List<String>` hay kiểu lồng sâu, thường phải truyền metadata kiểu đích phù hợp bằng `Type`, type token hoặc API của thư viện đó.

## 15. Generic record so với generic class

Cả hai dùng type parameters và cùng tuân theo type erasure. Khác biệt quan trọng đến từ **cơ chế record**, không đến từ Generics:

| Khía cạnh | Generic record | Generic class thông thường |
| --- | --- | --- |
| Khai báo | `record Pair<K,V>(K key,V value)` | `class Pair<K,V> { ... }` |
| Components/state | Header là bản mô tả trạng thái chuẩn; component fields final | Tự chọn các trường, có thể mutable/final |
| Accessor | Java tạo `key()`, `value()` theo tên component | Tự viết accessor nếu muốn |
| Constructor chuẩn | Canonical constructor có tham số theo component | Tự thiết kế constructor |
| equals/hashCode/toString | Được cung cấp theo components (trừ khi override hợp lệ) | Thừa hưởng từ `Object` nếu không tự định nghĩa |
| Kế thừa lớp | Record final và không extends class tùy ý | Có thể kế thừa theo quy tắc Java |
| Interface | Có thể implements generic interface | Có thể implements generic interface |
| Type parameter/erasure | Áp dụng quy tắc Java Generics | Áp dụng quy tắc Java Generics |
| Bất biến sâu | **Không tự bảo đảm** khi component tham chiếu mutable | Phụ thuộc thiết kế |

**Chọn generic record** khi đối tượng chủ yếu biểu diễn một tập giá trị, muốn API ngắn gọn và semantics của components phù hợp. **Chọn class** khi cần kế thừa một superclass khác, trạng thái nội bộ khác danh sách components, vòng đời có nhiều thay đổi, hoặc muốn kiểm soát hoàn toàn mô hình trạng thái. Đừng chọn record chỉ vì code ngắn nếu API thực sự cần encapsulation khác.

## 16. Một ví dụ tổng hợp: record làm kết quả xử lý có kiểu

Một API thường phải trả nhiều giá trị có liên hệ. Generic record là cấu trúc phù hợp để gom kết quả, vẫn giữ type rõ ràng.

Tạo `GenericResultDemo.java`:

```java
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public class GenericResultDemo {
    record Result<T>(T value, List<String> warnings) {
        Result {
            value = Objects.requireNonNull(value);
            warnings = List.copyOf(warnings);
        }

        <R> Result<R> map(Function<? super T, ? extends R> mapper) {
            return new Result<>(mapper.apply(value), warnings);
        }
    }

    public static void main(String[] args) {
        Result<String> input =
                new Result<>("Java", List.of("normalized"));

        Result<Integer> length = input.map(String::length);

        System.out.println(input.value());
        System.out.println(length.value());
        System.out.println(length.warnings());
    }
}
```

Chạy:

```bash
javac GenericResultDemo.java
java GenericResultDemo
```

`stdout`:

```text
Java
4
[normalized]
```

Phân tích kiểu:

1. `Result<T>` giữ dữ liệu chính bằng component `T value` và metadata bằng `List<String> warnings`.
2. `Result<String>` áp dụng `T=String`, nên `input.value()` có kiểu `String`.
3. `map` khai báo type parameter **phương thức** `<R>`. Với `String::length`, `R` suy luận `Integer`, vì vậy `Result<R>` trở thành `Result<Integer>` tại điểm gọi.
4. `warnings` không phụ thuộc `T` hay `R`; cả input và result có thể chia sẻ một unmodifiable list phù hợp vì constructor thực hiện `List.copyOf`.
5. Không thể gán `Result<Integer>` trực tiếp cho `Result<String>`. Type parameter được compiler giữ nhất quán.
6. `Objects.requireNonNull(value)` và `List.copyOf(warnings)` thể hiện các invariant runtime bổ sung. Nếu mapper trả null, constructor của `Result<R>` sẽ ném `NullPointerException`; Generics tự nó không ngăn null.
7. `Result` là record nên việc tạo kết quả mới không thay đổi `input`.

Ví dụ này chỉ phục vụ bài học về generic record và các components được tham số hóa. Nếu ứng dụng cần mô hình kết quả có nhánh thành công/thất bại, có thể thiết kế các kiểu riêng với sealed interface và record ở một bài học nâng cao.

## 17. Các ràng buộc cú pháp cần nhớ

| Tình huống | Có hợp lệ? | Giải thích |
| --- | --- | --- |
| `record Box<T>(T value) {}` | Có | Record khai báo type parameter. |
| `record Pair<K,V>(K key,V value) {}` | Có | Nhiều biến kiểu. |
| `record Metrics<N extends Number>(N value) {}` | Có | Type parameter có bound. |
| `record Items<T>(List<T> data) {}` | Có, nếu import List | Component generic lồng sử dụng `T`. |
| `record ArrayBox<T>(T[] values) {}` | Có | Component dạng mảng của `T`; cần chú ý mutability. |
| `record Bad<T>(T value) { T extra; }` | Không | Record không được khai báo instance field mới bên ngoài components. |
| `record Bad<T>(T value) extends Parent {}` | Không | Record không extends superclass tùy ý. |
| `class Child extends Box<String> {}` | Không nếu Box là record | Record mặc nhiên final. |
| `record Bad<T>(T value) { static T shared; }` | Không | Static member không dùng được type parameter instance của record. |
| `record Bad(int x, ...)` với biến kiểu nguyên thủy `T=int` | Không | Generics đòi reference type làm type argument, không dùng `int` như argument. |
| `record Pair<K,V>(K key,V value) implements Serializable {}` | Có nếu import/ghi đầy đủ interface | Record có thể implement interface; không mặc định implements `Serializable`. |

Các ví dụ `Bad` được viết để **minh họa lỗi compiler**; không ghép chúng vào một file thực thi rồi mong biên dịch thành công.

### Generic varargs cần thận trọng

Record header có thể dùng component varargs ở vị trí cuối giống constructor varargs, nhưng nếu varargs phụ thuộc generic non-reifiable type (như `T...`), compiler có thể cảnh báo về khả năng heap pollution. Đừng xem việc record có final fields là lý do bỏ qua unchecked/varargs warning. Với API công khai, thường dễ thiết kế an toàn hơn bằng component `List<T>` kết hợp defensive copy.

### Null, equals và serialization

- `record Pair<K,V>(K key,V value)` mặc định có thể chứa null ở component reference; muốn cấm null phải kiểm tra trong canonical constructor.
- Generated `equals`/`hashCode` dựa vào component values, không kiểm tra type arguments từng instance.
- Record **không tự động** implements `Serializable` chỉ vì là record. Nếu cần serialization Java, có thể khai báo implement interface và hiểu cơ chế serialization dành riêng cho record; không suy diễn từ generic type.
- Annotation liên quan đến component có thể tác động tới record component, field, accessor hoặc constructor parameter tùy các annotation targets; cơ chế này độc lập với cú pháp type parameter cơ bản.

## 18. Lỗi thường gặp và cách khắc phục

### Lỗi 1: nghĩ accessor luôn là getX()

`record Pair<K,V>(K key,V value)` cung cấp `key()`/`value()`. Gọi `getKey()` sẽ không biên dịch nếu bạn không tự khai báo method đó.

### Lỗi 2: bỏ type arguments khi dùng record

`Pair raw = new Pair("A", 1)` đánh mất thông tin kiểu, tạo raw/unchecked warnings. Dùng `Pair<String,Integer>` hoặc `var pair = new Pair<>("A",1)` khi kiểu suy luận rõ ràng. Nếu thật sự không biết các kiểu, dùng `Pair<?,?>` thay vì raw type.

### Lỗi 3: tin rằng final component làm collection bất biến sâu

`record Box<T>(List<T> values)` chỉ có trường final, không tự clone list và không làm các phần tử bất biến. Cân nhắc `List.copyOf`, kiểm tra null và sao chép sâu khi nhu cầu nghiệp vụ yêu cầu.

### Lỗi 4: hiểu sai bất biến kiểu của record

`Pair<String,Integer>` không là subtype của `Pair<String,Number>`. Khi cần một view linh hoạt để đọc `Number`, cân nhắc `Pair<String,? extends Number>`. Đừng dùng cast unchecked chỉ để loại bỏ compile error.

### Lỗi 5: quên điều kiện của bound

`Measurement<N extends Number>` không nhận `String` làm type argument. Nếu algorithm cần `Comparable`, hãy khai báo bound phù hợp; không cast bừa sang `Comparable`.

### Lỗi 6: nhầm type parameter của record và của method

`record Box<T>(T value) { <R> Box<R> map(...) { ... } }` có `T` thuộc record và `R` thuộc method. Mỗi invocation của `map` có thể suy luận `R` khác, trong khi `T` của receiver đã xác định tại kiểu của `Box<T>`.

### Lỗi 7: mong runtime biết `Pair<String,Integer>`

`getClass()` trả cùng record class bất kể type arguments. Metadata declaration có thể đọc bằng reflection nhưng không bảo đảm JVM kiểm tra từng đối tượng theo `K=String`, `V=Integer`.

### Lỗi 8: tạo constructor mà không hoàn tất khởi tạo components

Canonical constructor tường minh phải gán component fields. Compact constructor được compiler hoàn tất phần gán ngầm sau body; không gán `this.component` bên trong compact constructor. Non-canonical constructors phải gọi constructor khác của record bằng `this(...)`.

### Lỗi 9: đặt instance state không nằm trong header

Record không cho khai báo thêm instance fields tùy ý. Nếu cần trạng thái mutable ngoài components, class thường phù hợp hơn. Static constants hoặc helper methods có thể được khai báo theo quy tắc Java.

## 19. Bài thực hành tự kiểm tra

### Bài 1 — Dự đoán kiểu

Với `record Pair<K,V>(K key,V value) {}`, hãy dự đoán:

```java
Pair<String, Long> p = new Pair<>("order", 10L);
String a = p.key();
Long b = p.value();
// Integer wrong = p.value();
```

Đáp án: `a` và `b` hợp lệ; dòng `wrong` lỗi biên dịch vì accessor trả `Long`, không phải `Integer`.

### Bài 2 — Viết record có validation

Viết `record NonBlank<T>(String name, T payload)` với compact constructor kiểm tra `name` không null/blank và `payload` không null. Thử `NonBlank<Integer>`; mô tả rõ những ràng buộc nào do compiler generic kiểm tra, những ràng buộc nào do constructor kiểm tra runtime.

Gợi ý: `Objects.requireNonNull(payload)` và `name.isBlank()`, sau khi xác thực `name` không null. Hãy ném `IllegalArgumentException` cho chuỗi rỗng theo hợp đồng bạn chọn.

### Bài 3 — Interface generic

Viết `interface Identified<ID> { ID id(); }` và `record Task<ID,T>(ID id,T data) implements Identified<ID> {}`. Giải thích vì sao `Task<Long,String>` tự đáp ứng method `id()`, nhưng không đáp ứng interface đòi `getId()` nếu không bổ sung method.

### Bài 4 — Defensive copy

Dùng `record Group<T>(List<T> members)` để chứa `List<String>`. Hãy sửa canonical constructor để biến nó thành snapshot, sau đó thay đổi danh sách đầu vào. Kiểm tra snapshot không đổi; tiếp tục thử với `StringBuilder` để giải thích tại sao phần tử mutable có thể vẫn đổi.

### Bài 5 — Bounded record

Viết `record Interval<T extends Comparable<? super T>>(T start,T end)` và bắt lỗi nếu start lớn hơn end. Thử với `Integer` và `String`. Vì sao `T` phải có bound khi gọi `compareTo`?

### Bài 6 — Reflection

Dùng `RecordComponent.getGenericType()` để tìm kiểu khai báo của component `List<T> values`. Sau đó tạo `Container<String>` và `Container<Integer>`, so sánh runtime classes. Giải thích sự khác nhau giữa **metadata khai báo** và **đối số kiểu của một tham chiếu**.

### Bài 7 — Phân tích equality

Tạo hai `Pair<String,Number>` và `Pair<String,Integer>` có component values thực sự bằng nhau. Dự đoán `equals`. Tiếp theo thử record có component `int[]` chứa cùng nội dung nhưng hai array instances khác nhau; giải thích vì sao không nên mặc định mong deep equality.

## 20. Checklist khi thiết kế generic record

Khi chuẩn bị tạo một generic record cho dự án, trả lời các câu sau:

1. Record có thực sự đại diện cho **tập components mang dữ liệu** không? Nếu cần lifecycle/state tùy biến sâu, cân nhắc class.
2. Type parameter `T`, `K`, `V` được khai báo ở đâu và ràng buộc các components nào? Có cần bound?
3. Accessor do record sinh trả kiểu gì tại mỗi concrete type argument?
4. Constructor cần bảo vệ invariant nào (null, range, empty list, quan hệ giữa components)?
5. Có component tham chiếu mutable không? Cần defensive copy, deep copy hay API immutable?
6. Thao tác nào nên là instance method, thao tác nào cần một generic method `<R>` khác với type parameter của record?
7. Record có cần implement generic interface để phù hợp một contract chung không?
8. Caller có cần wildcard `? extends`/`? super` để nhận nhiều type arguments, hay kiểu cụ thể là đủ?
9. Có cần custom equality/hashCode do component chứa mảng hoặc quan hệ bình đẳng nghiệp vụ khác mặc định không?
10. Có đang phụ thuộc vào runtime generic type argument mà type erasure không cung cấp không?

Generic record đem lại sự gọn gàng ở khai báo **và** sự an toàn kiểu ở compile time. Hãy luôn lần theo type parameter từ record header tới từng component, accessor và constructor; sau đó đánh giá các ràng buộc runtime độc lập như null, mutability và equality. Cách đọc đó giúp dùng record cho DTO, value object, cặp dữ liệu và kết quả xử lý mà vẫn giữ API chính xác, dễ hiểu và dễ kiểm chứng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="generic-classes-interfaces-008">Phân biệt kiểu khai báo generic với từng cách sử dụng kiểu đó</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt kiểu khai báo generic với từng cách sử dụng kiểu đó

Trong Java Generics, **khai báo một kiểu generic** và **sử dụng kiểu generic ấy với một đối số kiểu cụ thể** là hai việc khác nhau. Khi viết `class Box<T>`, ta định nghĩa một khuôn mẫu hợp đồng kiểu có tham số `T`. Khi viết `Box<String>` hoặc `Box<Integer>`, ta sử dụng cùng khuôn mẫu đó theo hai cách được kiểm tra kiểu khác nhau.

Nhận ra sự khác biệt này giúp đọc đúng cú pháp `<T>`, `<String>`, `<>`, biết nơi nào khai báo tham số kiểu, nơi nào truyền đối số kiểu và tránh nhầm rằng Java tạo ra một lớp JVM hoàn toàn mới mỗi lần sử dụng generic.

## 1. Một khai báo generic có thể được sử dụng theo nhiều cách

Định nghĩa lớp sau chỉ cần viết **một lần**:

```java
public final class Box<T> {
    private final T value;

    public Box(T value) {
        this.value = value;
    }

    public T get() {
        return value;
    }
}
```

Đây là toàn bộ nội dung của tệp `Box.java`. `T` là một **tham số kiểu** (*type parameter*) được khai báo trong phần đầu của lớp: `Box<T>`. Nó biểu diễn một kiểu tham chiếu chưa được chốt trong khai báo. Mọi vị trí dùng `T` ở trường, constructor hoặc getter cùng tham chiếu tới tham số kiểu của lớp đó.

Ở mã sử dụng, ta có thể viết:

```java
Box<String> title = new Box<>("Java");
Box<Integer> year = new Box<>(2026);
Box<Double> ratio = new Box<>(0.75);
```

Đây là ba **cách sử dụng** một khai báo `Box<T>`. Trong từng kiểu tham số hóa (*parameterized type*):

- `Box<String>`: `String` là **đối số kiểu** (*type argument*) ứng với `T`.
- `Box<Integer>`: `Integer` là đối số kiểu ứng với cùng vị trí `T`.
- `Box<Double>`: `Double` là đối số kiểu khác; Java không yêu cầu tạo một khai báo lớp mới chỉ để đổi kiểu dữ liệu được lưu.

Các dòng trên là đoạn mã **trong một phương thức** và cần tệp `Box.java` ở cùng nơi biên dịch. Kết quả của `title.get()` có kiểu tĩnh `String`, còn `year.get()` có kiểu tĩnh `Integer`.

| Thành phần | Khai báo generic | Một cách sử dụng generic |
|---|---|---|
| Ví dụ | `class Box<T>` | `Box<String>` |
| Vai trò trong chương trình | Định nghĩa lớp và hợp đồng kiểu dùng lại | Chỉ định cách nhìn một kiểu generic tại điểm dùng |
| Tên trong ngoặc nhọn | `T` là **tham số kiểu** | `String` là **đối số kiểu** |
| Xuất hiện ở đâu? | Phần khai báo `class`, `interface` hoặc `record` generic | Kiểu biến, tham số, kết quả, `new`, kiểu cha được tham số hóa… |
| Số lần viết | Một khai báo có thể phục vụ nhiều nơi | Có thể dùng cùng kiểu generic với nhiều đối số khác nhau |
| Kiểm tra kiểu | Mô tả quan hệ giữa những vị trí dùng `T` | Trình biên dịch áp quan hệ đó cho đối số kiểu đã chọn |

**Điểm cốt lõi:** `Box<T>` là **khai báo** khi viết `class Box<T>`. `Box<String>` là **một kiểu được tham số hóa** khi dùng lớp ấy. Không nên gọi mọi chuỗi ký tự nằm giữa `<...>` là “khai báo generic”.

## 2. Đọc đúng bốn hình thức rất dễ bị nhầm

### 2.1. `class Box<T>`: khai báo tham số kiểu

```java
class Box<T> {
    private T value;

    Box(T value) {
        this.value = value;
    }

    T get() {
        return value;
    }
}
```

`T` được **giới thiệu** lần đầu trong phần khai báo lớp. Bên trong lớp này, trình biên dịch hiểu `T` là một tham số kiểu hợp lệ và cho phép dùng nó trong những thành phần instance tương ứng.

Nếu thay `T` bằng tên `E` nhất quán trong toàn bộ khai báo, ý nghĩa về cơ bản không đổi. Các tên `T`, `E`, `K` và `V` chỉ là quy ước đặt tên dễ đọc, không phải từ khóa đặc biệt.

### 2.2. `Box<String>`: sử dụng một đối số kiểu cụ thể

```java
// Đoạn minh họa đặt trong thân một phương thức:
Box<String> message;
```

Dòng này **khai báo biến** `message` có kiểu `Box<String>`; nó **không khai báo thêm một lớp generic mới** và cũng chưa tạo đối tượng vì chưa có `new`.

`String` là kiểu đã có. Nó được truyền ở vị trí đối số kiểu, còn tên `T` không cần xuất hiện tại nơi dùng.

### 2.3. `new Box<String>("hello")`: khởi tạo có đối số kiểu tường minh

```java
// Đoạn minh họa đặt trong thân một phương thức:
Box<String> message = new Box<String>("hello");
```

Hai vị trí `<String>` có chức năng liên quan nhưng khác nhau: bên trái diễn đạt kiểu của biến tham chiếu, bên phải chỉ định kiểu cho biểu thức khởi tạo đối tượng.

`"hello"` nằm trong ngoặc tròn là **đối số giá trị cho constructor**, không phải đối số kiểu. Nếu constructor nhận `T` thì, khi `T` tương ứng `String`, đối số giá trị phải có kiểu phù hợp với `String`.

### 2.4. `new Box<>("hello")`: khởi tạo dùng diamond

```java
// Đoạn minh họa đặt trong thân một phương thức:
Box<String> message = new Box<>("hello");
```

Dấu `<>` rỗng là **diamond operator**. Nó báo cho trình biên dịch tự suy luận đối số kiểu của biểu thức `new` dựa trên ngữ cảnh và đối số constructor. Ở đây kiểu đích `Box<String>` giúp suy luận `String`.

**Diamond không có nghĩa lớp được khai báo không còn tham số kiểu.** `Box` vẫn là lớp generic có khai báo `Box<T>`; Java chỉ rút gọn cách chỉ định đối số kiểu ở nơi sử dụng.

## 3. Một ví dụ chạy được: một khai báo, nhiều kiểu sử dụng

Lưu thành **`GenericDeclarationDemo.java`**:

```java
import java.util.List;

public class GenericDeclarationDemo {
    static final class Box<T> {
        private final T value;

        Box(T value) {
            this.value = value;
        }

        T get() {
            return value;
        }
    }

    public static void main(String[] args) {
        Box<String> language = new Box<>("Java");
        Box<Integer> quantity = new Box<>(12);
        Box<List<String>> topics = new Box<>(List.of("type", "generic"));

        String text = language.get();
        Integer count = quantity.get();
        List<String> names = topics.get();

        System.out.println(text.toUpperCase());
        System.out.println(count + 3);
        System.out.println(String.join(" / ", names));
        System.out.println(language.getClass() == quantity.getClass());
    }
}
```

Chạy bằng JDK 17 trở lên:

```bash
javac -encoding UTF-8 GenericDeclarationDemo.java
java GenericDeclarationDemo
```

Kết quả chính xác của chương trình:

```text
JAVA
15
type / generic
true
```

Giải thích:

1. Chỉ có **một định nghĩa** lớp lồng `Box<T>`.
2. `Box<String>` yêu cầu getter được sử dụng như trả về `String`.
3. `Box<Integer>` yêu cầu getter được sử dụng như trả về `Integer`. Khi cộng `count + 3`, Java tự mở hộp (*unboxing*) giá trị `Integer` thành `int`.
4. `Box<List<String>>` dùng một **kiểu tham số hóa lồng nhau** làm đối số kiểu; `T` ứng với toàn bộ `List<String>`.
5. Biểu thức so sánh `getClass()` trả `true`: hai đối tượng vẫn có cùng lớp runtime `GenericDeclarationDemo.Box`. Điều đó phù hợp với cơ chế **type erasure** và không mâu thuẫn với việc compiler kiểm tra các cách sử dụng khác nhau.

Không có bước nào yêu cầu tạo các lớp `StringBox`, `IntegerBox` hoặc `ListBox` riêng biệt.

## 4. Một khai báo generic nhưng nhiều kiểu tĩnh khác nhau ở mã sử dụng

Hãy phân biệt **kiểu tĩnh của biến** với **lớp runtime của đối tượng**:

```java
// Đoạn minh họa trong một phương thức, đã có class Box<T>:
Box<String> a = new Box<>("A");
Box<Integer> b = new Box<>(5);
```

Ở thời điểm biên dịch, `a` có kiểu `Box<String>` còn `b` có kiểu `Box<Integer>`. Vì thế:

```java
String first = a.get();
Integer second = b.get();

// Integer wrong = a.get(); // Lỗi biên dịch: String không gán được cho Integer
// String wrong2 = b.get(); // Lỗi biên dịch: Integer không gán được cho String
```

Các dòng lỗi ở trên được chú thích để minh họa hành vi trình biên dịch. Việc thay đối số kiểu làm thay đổi **hợp đồng kiểu ở nguồn Java** mà người sử dụng được phép gọi.

Tuy nhiên, tại runtime, `a` và `b` đều là các thể hiện của lớp `Box`. Khác biệt giữa `Box<String>` và `Box<Integer>` không đồng nghĩa JVM nạp hai định nghĩa lớp độc lập.

Một điều quan trọng khác: **khai báo biến không phải tạo đối tượng**.

```java
// Chỉ khai báo biến cục bộ:
Box<String> emptyReference;

// Tạo đối tượng và gán tham chiếu:
Box<String> initialized = new Box<>("ready");
```

Biến cục bộ `emptyReference` chưa được gán nên không thể đọc nó trước khi gán, dù kiểu `Box<String>` hoàn toàn hợp lệ. Khi sử dụng, cần phân biệt cú pháp kiểu, cú pháp khai báo biến và việc thực sự gọi constructor.

## 5. Khai báo nhiều tham số kiểu và dùng chúng theo đúng vị trí

Một kiểu generic không nhất thiết chỉ có một tham số:

```java
final class Pair<K, V> {
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

Trong **khai báo** `Pair<K, V>`, `K` và `V` là hai tham số kiểu riêng biệt. Ở **nơi sử dụng**:

```java
// Đoạn minh họa trong một phương thức:
Pair<String, Integer> stock = new Pair<>("book", 10);
Pair<Integer, String> directory = new Pair<>(404, "Not Found");
```

Với `Pair<String, Integer>`, `K = String` và `V = Integer`. Với `Pair<Integer, String>`, vị trí được hoán đổi. Trình biên dịch không tự suy luận rằng hai kiểu sử dụng đó là tương đương.

| Cách sử dụng | `K` ứng với | `V` ứng với | Kiểu `key()` | Kiểu `value()` |
|---|---|---|---|---|
| `Pair<String, Integer>` | `String` | `Integer` | `String` | `Integer` |
| `Pair<Integer, String>` | `Integer` | `String` | `Integer` | `String` |
| `Pair<String, String>` | `String` | `String` | `String` | `String` |

**Số lượng và thứ tự** đối số kiểu phải phù hợp khai báo. Không thể dùng `Pair<String>` nếu `Pair` yêu cầu hai tham số kiểu, và cũng không thể tạo `Pair<String, Integer, Long>` từ khai báo này.

### Ví dụ hoàn chỉnh cho `Pair<K, V>`

Lưu tệp **`PairUsageDemo.java`**:

```java
public class PairUsageDemo {
    static final class Pair<K, V> {
        private final K key;
        private final V value;

        Pair(K key, V value) {
            this.key = key;
            this.value = value;
        }

        K key() { return key; }
        V value() { return value; }
    }

    public static void main(String[] args) {
        Pair<String, Integer> stock = new Pair<>("book", 10);
        Pair<Integer, String> code = new Pair<>(404, "Not Found");

        System.out.println(stock.key() + "=" + stock.value());
        System.out.println(code.key() + ": " + code.value());
    }
}
```

Biên dịch và chạy:

```bash
javac -encoding UTF-8 PairUsageDemo.java
java PairUsageDemo
```

`stdout`:

```text
book=10
404: Not Found
```

Đây tiếp tục là **một khai báo** `Pair<K, V>` với hai cách dùng có thứ tự đối số kiểu khác nhau.

## 6. Khai báo interface generic và các cách triển khai, sử dụng

Giao diện cũng có thể khai báo tham số kiểu:

```java
interface Reader<T> {
    T read();
}
```

`Reader<T>` là **khai báo interface generic**. Các kiểu sau là những **cách sử dụng**:

```java
// Đoạn minh họa trong phần khai báo lớp:
final class StringReader implements Reader<String> {
    @Override
    public String read() {
        return "Java";
    }
}

final class IntegerReader implements Reader<Integer> {
    @Override
    public Integer read() {
        return 21;
    }
}
```

Lớp `StringReader` cố định đối số kiểu của `Reader` thành `String`, còn `IntegerReader` cố định thành `Integer`. Cả hai thực hiện **cùng một khai báo** `Reader<T>` theo hai hợp đồng kiểu khác nhau.

Một lớp khác có thể tiếp tục là generic:

```java
final class FixedReader<T> implements Reader<T> {
    private final T value;

    FixedReader(T value) {
        this.value = value;
    }

    @Override
    public T read() {
        return value;
    }
}
```

Ở đây `FixedReader<T>` **khai báo tham số kiểu của chính lớp** và **chuyển tiếp tham số kiểu ấy** vào giao diện `Reader<T>`. Nơi sử dụng chọn:

```java
// Đoạn minh họa bên trong một phương thức:
Reader<String> title = new FixedReader<>("Java");
Reader<Integer> age = new FixedReader<>(20);
```

Vì thế, cần phân biệt ba trường hợp:

- `interface Reader<T>`: **khai báo** một interface generic.
- `class StringReader implements Reader<String>`: lớp thường **sử dụng** interface bằng đối số kiểu cụ thể.
- `class FixedReader<T> implements Reader<T>`: một **khai báo lớp generic mới**, đồng thời sử dụng interface generic qua tham số kiểu của lớp.

Việc giữ tên `T` ở lớp và interface không có nghĩa chúng tự động dùng chung một biến kiểu từ trước. Mỗi khai báo có phạm vi tham số kiểu riêng; biểu thức `implements Reader<T>` chỉ ra cách lớp mới liên kết tham số kiểu của mình với interface.

## 7. Khai báo phương thức generic cũng khác với lời gọi phương thức ấy

Generics không chỉ dùng ở lớp và interface. Một **phương thức generic** tự khai báo tham số kiểu ở phần đầu chữ ký:

```java
static <T> T echo(T value) {
    return value;
}
```

`<T>` trước kiểu trả về là **khai báo tham số kiểu của phương thức**. Các lời gọi sau là những **lần sử dụng** cùng phương thức:

```java
// Ví dụ bên trong một phương thức của lớp chứa echo:
String text = echo("Hi");
Integer number = echo(9);
```

Trình biên dịch suy luận `T = String` cho lời gọi đầu và `T = Integer` cho lời gọi sau. Không có một định nghĩa phương thức mới được tạo trong mã nguồn mỗi lần gọi.

Khi cần, có thể **chỉ định đối số kiểu tường minh** ngay trước tên phương thức:

```java
String text = GenericMethodDemo.<String>echo("Hi");
```

Cú pháp này khác với cú pháp tạo đối tượng `new Box<String>(...)`. Một phương thức `static` không được lấy trực tiếp tham số kiểu của lớp generic, nên thường cần tự khai báo `<U>` hoặc `<T>`.

### Chương trình chạy được

Lưu thành **`GenericMethodDemo.java`**:

```java
public class GenericMethodDemo {
    static <T> T echo(T value) {
        return value;
    }

    static final class Holder<T> {
        private final T value;

        Holder(T value) {
            this.value = value;
        }

        T get() {
            return value;
        }

        static <U> Holder<U> of(U value) {
            return new Holder<>(value);
        }
    }

    public static void main(String[] args) {
        String a = echo("Java");
        Integer b = GenericMethodDemo.<Integer>echo(17);
        Holder<String> c = Holder.of("Generics");

        System.out.println(a);
        System.out.println(b + 1);
        System.out.println(c.get());
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 GenericMethodDemo.java
java GenericMethodDemo
```

`stdout`:

```text
Java
18
Generics
```

Đọc các vị trí:

- `class Holder<T>`: khai báo tham số kiểu của **lớp**.
- `static <U> Holder<U> of(U value)`: khai báo tham số kiểu riêng của **phương thức static**.
- `Holder<String> c`: sử dụng **lớp** với đối số kiểu `String`.
- `Holder.of("Generics")`: sử dụng **phương thức**; compiler suy luận `U` từ đối số và ngữ cảnh.

## 8. Ranh giới kiểu: một cách sử dụng không thể tùy ý biến thành cách sử dụng khác

Một hiểu nhầm lớn là tưởng rằng `Box<String>` sẽ là kiểu con của `Box<Object>` vì `String` là kiểu con của `Object`. Java generic mặc định **bất biến** (*invariant*):

```java
// Ví dụ đặt trong một phương thức, đã khai báo Box<T>:
Box<String> strings = new Box<>("hello");

// Box<Object> objects = strings; // Không biên dịch
```

Dù hai biến tham chiếu tới các cách tham số hóa của cùng lớp `Box`, Java không cho gán như trên. Nếu cho phép, những API có thao tác ghi theo `Object` có thể đưa đối tượng không phải chuỗi vào hộp vốn được cam kết chứa `String`.

Đối với collection, hậu quả càng rõ:

```java
import java.util.ArrayList;
import java.util.List;

// Đoạn minh họa trong một phương thức:
List<String> names = new ArrayList<>();
names.add("An");

// List<Object> values = names; // Không biên dịch
```

Nếu `List<Object> values = names` được phép, `values.add(123)` sẽ phá hợp đồng `List<String>` mà `names` cần giữ. Chính vì vậy trình biên dịch chặn ngay phép gán.

### Wildcard dùng khi không biết một đối số kiểu cụ thể

Một phương thức **chỉ đọc** giá trị trong hộp có thể chấp nhận:

```java
// Đặt trong một lớp đã có Box<T>:
static void print(Box<?> box) {
    System.out.println(box.get());
}
```

`Box<?>` vẫn là **một cách sử dụng** của kiểu `Box`, nhưng đối số kiểu là wildcard biểu thị một kiểu chưa biết. Nó không khai báo một tham số kiểu tên `?`, và không khẳng định giá trị bên trong là `String` hoặc `Integer`.

Các cách gọi `print(new Box<>("A"))` và `print(new Box<>(5))` đều hợp lệ. Vì kiểu cụ thể chưa biết, kết quả đọc ra có thể sử dụng an toàn như `Object`. Nếu API cần giữ quan hệ kiểu giữa nhiều tham số và giá trị trả về, một phương thức generic `<T>` có tên tham số kiểu riêng có thể diễn đạt quan hệ đó rõ hơn.

**Phân biệt:** `Box<T>` có thể là một cách sử dụng hợp lệ **bên trong phạm vi đã khai báo `T`**, như `class Container<T> { Box<T> box; }`. Ngoài phạm vi ấy, `T` không có nghĩa tự động. Không nên dùng chữ `T` như ký hiệu cho “kiểu bất kỳ” nếu chưa có khai báo hợp lệ.

## 9. Một khai báo có thể đặt giới hạn trên tham số kiểu

Khai báo sau yêu cầu `T` phải là `Number` hoặc kiểu con của nó:

```java
final class NumericBox<T extends Number> {
    private final T value;

    NumericBox(T value) {
        this.value = value;
    }

    double doubleValue() {
        return value.doubleValue();
    }
}
```

Cách sử dụng hợp lệ:

```java
// Đoạn minh họa trong một phương thức:
NumericBox<Integer> whole = new NumericBox<>(4);
NumericBox<Double> decimal = new NumericBox<>(2.5);
```

Cách dùng không hợp lệ:

```java
// NumericBox<String> bad = new NumericBox<>("x"); // Sai giới hạn T extends Number
```

Ở đây **giới hạn được viết một lần trong khai báo** `NumericBox<T extends Number>`, sau đó áp dụng cho mọi đối số kiểu tại nơi dùng. `<T extends Number>` trong khai báo khác vai trò với `<Integer>` trong cách sử dụng.

Một điểm nữa: đối số kiểu phải là **kiểu tham chiếu**. Vì vậy `Box<int>` không hợp lệ; `Box<Integer>` mới hợp lệ. Java có thể tự đóng hộp số nguyên khi truyền giá trị vào đối tượng tham số hóa bởi `Integer`.

## 10. Diamond và `var`: cùng khai báo, nhưng kiểu được suy luận tại nơi dùng

Java thường suy luận đối số kiểu khi tạo đối tượng:

```java
import java.util.ArrayList;
import java.util.List;

// Trong một phương thức:
List<String> labels = new ArrayList<>();
```

Kiểu đích `List<String>` giúp biểu thức `new ArrayList<>()` được suy luận là `ArrayList<String>`. Đó vẫn là một lần **sử dụng lớp khai báo generic `ArrayList<E>`**, chứ không phải một khai báo kiểu mới.

Với `var`, cần chú ý ngữ cảnh:

```java
// Trong một phương thức:
var names = new ArrayList<String>();
var unknown = new ArrayList<>();
```

- `names` có kiểu tĩnh `ArrayList<String>` vì đối số kiểu của biểu thức khởi tạo đã nêu rõ.
- `unknown` có kiểu tĩnh `ArrayList<Object>` trong ngữ cảnh này, vì không có thông tin nào cụ thể hơn để suy luận tham số kiểu. Do đó `unknown.add("x")` và `unknown.add(123)` đều có thể biên dịch.

Cú pháp `var` **không tạo kiểu động**. Trình biên dịch vẫn chốt một kiểu tĩnh tại chỗ khai báo biến cục bộ. Nếu mục tiêu là danh sách chuỗi, hãy dùng `List<String> names = new ArrayList<>();` hoặc `var names = new ArrayList<String>();`.

## 11. Type erasure: các cách sử dụng không sinh các lớp JVM độc lập

Điểm dễ gây hiểu nhầm nhất nằm ở sự khác biệt giữa **hợp đồng kiểu ở compile time** và **lớp thực tế ở runtime**.

Giả sử:

```java
// Đoạn minh họa trong một phương thức:
Box<String> text = new Box<>("Java");
Box<Integer> number = new Box<>(42);

System.out.println(text.getClass() == number.getClass());
```

Kết quả là `true`. Java dùng cơ chế **type erasure** cho phần lớn thông tin tham số generic; cả hai đối tượng đều dùng cùng lớp runtime `Box`. Lúc biên dịch, compiler vẫn kiểm tra hai kiểu tham số hóa là khác nhau.

Điều này giải thích tại sao cú pháp sau **không hợp lệ**:

```java
// Object item = ...;
// if (item instanceof Box<String>) { } // Không biên dịch
```

JVM không giữ thông tin đối số kiểu theo cách có thể kiểm tra với `instanceof Box<String>`. Có thể dùng `instanceof Box<?>` để kiểm tra đối tượng có phải một `Box` hay không, nhưng kiểm tra ấy **không chứng minh** dữ liệu bên trong là `String`.

### Kiểu raw không đồng nghĩa một cách sử dụng được kiểm tra an toàn

Khi viết:

```java
// Trong một phương thức:
Box raw = new Box("Java");
```

`Box` không có đối số kiểu là **raw type**. Đây là dạng dành cho tương thích với mã Java cũ, làm mất một phần bảo vệ của generic và thường gây cảnh báo `rawtypes` hoặc `unchecked`.

Đừng nhầm:

| Biểu thức/kiểu | Ý nghĩa |
|---|---|
| `Box<T>` trong `class Box<T>` | Khai báo generic, giới thiệu tham số `T`. |
| `Box<String>` | Kiểu tham số hóa sử dụng đối số `String`. |
| `Box<?>` | Kiểu tham số hóa với đối số wildcard chưa biết. |
| `Box` | Raw type, bỏ thông tin đối số kiểu. |
| `new Box<>("value")` | Biểu thức tạo đối tượng với đối số kiểu được suy luận. |

Raw type có thể làm xuất hiện `ClassCastException` ở lúc đọc dữ liệu qua API được tham số hóa, đặc biệt nếu mã không an toàn đã đưa sai loại đối tượng vào cấu trúc dữ liệu. Generic giảm lỗi kiểu bằng kiểm tra biên dịch, nhưng không tự xác thực dữ liệu đến từ nguồn bên ngoài hoặc mã dùng cast không kiểm tra.

## 12. Các lỗi phổ biến khi phân biệt khai báo và sử dụng

| Đoạn mã hoặc suy nghĩ | Vấn đề | Cách hiểu đúng |
|---|---|---|
| “`Box<String>` là một lớp Java mới được định nghĩa.” | Chỉ là cách tham số hóa từ cùng khai báo `Box<T>`. | Định nghĩa lớp ở `class Box<T>`; sử dụng ở `Box<String>`. |
| “`T` trong `Box<T>` lúc nào cũng thay bằng `Object` trong mã nguồn.” | `T` là tham số kiểu có phạm vi và có thể có bound. | Ở mã sử dụng được kiểm tra kiểu, `Box<String>` mang hợp đồng `String`. |
| Viết `Box<T> b` ở phương thức không khai báo `T` | Tên `T` chưa có trong phạm vi. | Dùng kiểu cụ thể, wildcard hoặc khai báo tham số kiểu ở lớp/phương thức. |
| Viết `new Box<>()` rồi nghĩ không dùng generics | Diamond vẫn suy luận đối số kiểu. | Xác định kiểu đích hoặc kiểm tra kiểu được suy luận. |
| Nghĩ `Box<String>` là kiểu con của `Box<Object>` | Generic mặc định bất biến. | Không gán trực tiếp giữa hai kiểu tham số hóa đó. |
| Dùng `Box<int>` | Generics không nhận kiểu nguyên thủy làm đối số kiểu. | Dùng `Box<Integer>`. |
| Dùng `Box raw` như `Box<?>` | Raw type bỏ kiểm tra; wildcard vẫn bảo toàn nhiều quy tắc kiểu generic. | Dùng `Box<?>` khi đối số kiểu chưa biết. |
| Dùng `instanceof Box<String>` | Không thể kiểm tra đối số kiểu đó bằng `instanceof` do erasure. | Dùng `instanceof Box<?>` nếu chỉ cần biết đối tượng có phải Box. |
| Nghĩ mỗi đối số kiểu tạo lớp runtime riêng | Không đúng với cơ chế generics hiện tại của Java. | Một khai báo lớp được dùng với nhiều hợp đồng kiểu lúc biên dịch. |
| Dùng `var values = new ArrayList<>()` rồi mong `List<String>` | Không có kiểu đích buộc đối số kiểu là `String`. | Khai báo rõ đối số kiểu ở ít nhất một vị trí thích hợp. |

## 13. Checklist đọc và viết generic đúng

Khi thấy cú pháp có dấu `<...>`, hãy tự hỏi:

1. **Đang đứng tại khai báo hay nơi sử dụng?** Sau `class`/`interface` và trong `<T>` là khai báo tham số kiểu; trong `Box<String>` ở vị trí kiểu biến là đối số kiểu được cung cấp.
2. **Tên trong ngoặc nhọn có được khai báo chưa?** `T` là tên tham số kiểu, chỉ hợp lệ trong phạm vi khai báo thích hợp.
3. **Có bao nhiêu tham số kiểu, thứ tự ra sao?** Với `Pair<K,V>`, `Pair<String,Integer>` gắn theo đúng thứ tự.
4. **Giới hạn kiểu là gì?** Nếu khai báo `T extends Number` thì `String` không thể dùng làm đối số kiểu.
5. **Biểu thức có tạo đối tượng không?** `Box<String> ref;` chỉ khai báo biến; `new Box<>("x")` mới tạo đối tượng.
6. **Diamond có đủ ngữ cảnh suy luận chưa?** Kiểu đích rõ ràng làm mã dễ đọc, còn `var` có thể khiến suy luận ra `Object`.
7. **Có đang trộn các kiểu tham số hóa khác nhau không?** `Box<String>` không tự gán được cho `Box<Object>`.
8. **Có nhầm compile time với runtime không?** Một lớp runtime `Box` có thể được dùng với nhiều đối số kiểu ở mã nguồn.

### Tự kiểm tra

**Câu 1.** Chỉ ra đâu là khai báo tham số kiểu và đâu là sử dụng đối số kiểu:

```java
class Holder<T> {
    private final T value;

    Holder(T value) { this.value = value; }
    T get() { return value; }
}

// Trong một phương thức:
Holder<String> h = new Holder<>("Hello");
```

**Câu 2.** Tại sao cùng một lớp `Pair<K,V>` lại cho phép `Pair<String,Integer>` và `Pair<Integer,String>` nhưng không đồng nghĩa hai kiểu ấy hoán đổi được cho nhau?

**Câu 3.** `class StaticReader implements Reader<String>` có khai báo tham số kiểu trên chính lớp `StaticReader` không?

**Câu 4.** `Box<?>` và `Box` khác nhau thế nào?

**Câu 5.** Vì sao `new Box<>("x")` có thể suy luận thành `Box<String>`, còn `var list = new ArrayList<>()` thường suy luận đối số kiểu thành `Object`?

**Gợi ý đáp án:** (1) `<T>` sau `Holder` là khai báo, `<String>` trong `Holder<String>` là đối số kiểu, `<>` trong `new Holder<>()` là diamond để suy luận. (2) Các đối số kiểu gắn với những vị trí khác nhau; generic Java mặc định bất biến. (3) Không, `StaticReader` là lớp không generic và chỉ sử dụng giao diện `Reader<String>`. (4) `Box<?>` vẫn giữ một kiểu tham số hóa với đối số chưa biết; `Box` là raw type. (5) Trường hợp đầu có kiểu đích và đối số constructor, trường hợp sau thiếu thông tin để suy luận kiểu hẹp hơn.

**Ghi nhớ:** **Khai báo generic** xây dựng một hợp đồng kiểu sử dụng lại được, còn **mỗi cách sử dụng generic** chỉ định hoặc suy luận các đối số kiểu cho hợp đồng đó. Compiler phân biệt rõ `Box<String>`, `Box<Integer>` và `Box<?>` tại mã nguồn, dù các đối tượng của chúng thông thường vẫn có cùng một lớp runtime sau type erasure.


</details>

- [Quay lại đầu trang](#back-to-top)

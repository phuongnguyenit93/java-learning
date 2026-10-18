<a id="back-to-top"></a>

# Giới hạn kiểu

## Menu
- [Giới hạn trên &lt;T extends U&gt; và các thao tác được phép trên T](#generic-type-bounds-001)
- [Giới hạn bằng lớp hoặc giao diện](#generic-type-bounds-002)
- [Nhiều giới hạn với &lt;T extends A &amp; B&gt;](#generic-type-bounds-003)
- [Quy tắc lớp đứng trước và các giới hạn phải tương thích](#generic-type-bounds-004)
- [Giới hạn tham chiếu đến tham số kiểu khác](#generic-type-bounds-005)
- [Kiểu giao và ý nghĩa đối với truy cập thành viên](#generic-type-bounds-006)
- [&lt;T extends Number&gt; không cung cấp toán tử số học trên T](#generic-type-bounds-007)
- [Giới hạn Throwable trong throws của phương thức generic](#generic-type-bounds-008)

## <a id="generic-type-bounds-001">Giới hạn trên &lt;T extends U&gt; và các thao tác được phép trên T</a>

<details>
<summary>Xem chi tiết</summary>

# Giới hạn trên `<T extends U>` và các thao tác được phép trên `T` trong Java

**Giới hạn trên** (*upper bound*) là ràng buộc một tham số kiểu generic phải là một kiểu phù hợp với một kiểu cha, một interface hoặc một type variable đã biết. Cú pháp thường gặp là `<T extends U>`, trong đó `T` là tham số kiểu được khai báo và `U` là giới hạn trên: **đối số kiểu truyền cho `T` phải là `U` hoặc subtype của `U`**.

Upper bound giải quyết vấn đề quan trọng: khi một thuật toán nhận dữ liệu thuộc nhiều lớp khác nhau, trình biên dịch cần biết **những phương thức nào chắc chắn có thể gọi trên `T`**. Nếu chỉ khai báo `<T>`, compiler không giả định `T` có `doubleValue()`, `compareTo()` hay một method nghiệp vụ bất kỳ. Nếu khai báo `<T extends Number>`, compiler biết `T` hỗ trợ những thành viên truy cập được của `Number`, chẳng hạn `doubleValue()`.

Bài này trình bày cú pháp, cơ chế kiểm tra lúc biên dịch, các thao tác hợp lệ và không hợp lệ, nhiều giới hạn, sự khác biệt giữa upper bound cho type parameter và wildcard `? extends U`, kiểu kết quả, type erasure, lỗi thường gặp và ví dụ Java 17+ chạy được.

## 1. Đọc cú pháp `<T extends U>` chính xác

Ví dụ đơn giản:

```java
static <T extends Number> double toDouble(T value) {
    return value.doubleValue();
}
```

Trong chữ ký:

1. `<T extends Number>` **khai báo** một type parameter mới tên `T`.
2. `Number` là **upper bound**: đối số kiểu thay cho `T` phải là `Number` hoặc lớp con phù hợp, như `Integer`, `Long`, `Double`, `BigDecimal`.
3. Kiểu trả về của phương thức là `double` cố định; không phải `T`.
4. Tham số `T value` chấp nhận giá trị theo `T` đã được compiler suy luận/chọn.
5. `doubleValue()` hợp lệ bởi đây là phương thức được khai báo trên `Number`.

Lời gọi minh họa:

```java
double a = toDouble(42);                       // T suy luận là Integer.
double b = toDouble(3.5);                      // T suy luận là Double.
double c = toDouble(new java.math.BigDecimal("2.25"));

// toDouble("42"); // Không biên dịch: String không extends Number.
```

`Number` ở đây là lớp trừu tượng trong Java, có các phương thức như `intValue()`, `longValue()`, `floatValue()`, `doubleValue()`. Sự tồn tại của method **không bảo đảm kết quả chuyển đổi không mất độ chính xác**: `longValue()` có thể làm mất phần thông tin và `doubleValue()` có thể làm tròn khi kiểu số gốc giữ độ chính xác cao hơn.

Cú pháp `<T extends U>` không có nghĩa `T` **bằng hẳn** `U`; `T` có thể là subtype của `U`. Từ khóa `extends` dùng cho cả upper bound là class, interface hoặc một type variable đã được khai báo phù hợp.

### `U` không nhất thiết là tên class

Một ràng buộc có thể tham chiếu type variable khác:

```java
static <U, T extends U> U upcast(T value) {
    return value;
}
```

Ở đây `U` được khai báo trước, nên `T extends U` hợp lệ. Phương thức trả `U` vì mọi `T` phù hợp có thể được xem như `U`. Đoạn minh họa này **không cần dùng trong mọi bài toán**; trên thực tế, các chữ ký đơn giản hơn thường dễ hiểu hơn nếu chỉ cần phép upcast thông thường.

### Upper bound có thể là interface

```java
static <T extends CharSequence> int lengthOf(T value) {
    return value.length();
}
```

`CharSequence` là interface. Dù vậy vẫn viết `extends`, không viết `implements` trong danh sách giới hạn type parameter. `String`, `StringBuilder`, `StringBuffer` là các kiểu thích hợp để sử dụng ở đây; kiểu `Integer` không đáp ứng ràng buộc `CharSequence`.

`T` có thể là một class/interface subtype phù hợp, nhưng **không thể là primitive**: `<T extends Number>` có thể dùng `Integer`, không dùng trực tiếp `int`. Java có boxing để hỗ trợ nhiều lời gọi với literal primitive, nhưng type argument thực vẫn là kiểu tham chiếu.

## 2. Vì sao không có bound thì compiler chỉ biết rất ít về `T`?

Xét generic method không có bound:

```java
static <T> String describe(T value) {
    return String.valueOf(value);
}
```

`T` mặc định có giới hạn trên là `Object` nếu không bị ràng buộc khác. Compiler chỉ cho truy cập các thành viên hợp lệ đã được bảo đảm bởi kiểu đó, chẳng hạn các phương thức của `Object` được phép gọi trong ngữ cảnh tương ứng (`toString`, `equals`, `hashCode`, `getClass`, ...). `String.valueOf(value)` còn xử lý được trường hợp `null`.

**Không thể tự ý gọi method thuộc một subtype tùy ý:**

```java
// Không biên dịch:
// static <T> double broken(T value) {
//     return value.doubleValue();
// }
```

Compiler phải kiểm tra phương thức tổng quát ấy cho **mọi `T` hợp lệ**, từ `String` đến `LocalDate` hoặc một lớp nghiệp vụ. Không có hợp đồng nào yêu cầu mọi đối tượng đều có `doubleValue()`.

Nếu code viết `<T extends Number>`, các member của `Number` trở nên khả dụng:

```java
static <T extends Number> String describeNumber(T value) {
    return "double=" + value.doubleValue()
            + ", long=" + value.longValue();
}
```

Upper bound là **lời hứa về kiểu được compiler kiểm tra**, chứ không phải một phép chuyển runtime từ giá trị tùy ý thành `Number`. Caller truyền `String` sẽ bị từ chối theo đường gọi có kiểm tra generic type đầy đủ.

## 3. Những thao tác nào được phép trên `T extends U`?

Hãy hình dung bạn viết:

```java
static <T extends Number> double twice(T value) {
    return value.doubleValue() * 2.0;
}
```

Compiler biết `value` có ít nhất hợp đồng của `Number`; do đó có thể gọi `doubleValue()` và thực hiện phép toán số học trên **giá trị `double` trả về**.

| Thao tác | Với `T extends Number` | Vì sao |
| --- | --- | --- |
| `value.doubleValue()` | Hợp lệ | Method nằm trong `Number` |
| `value.intValue()` | Hợp lệ | Method nằm trong `Number` |
| `value.toString()` | Hợp lệ | Thừa hưởng/ghi đè method của `Object` |
| `Number n = value` | Hợp lệ | Mọi `T` phù hợp có thể được xem như `Number` |
| `Object o = value` | Hợp lệ | Mọi kiểu tham chiếu phù hợp là `Object` |
| `T copy = value` | Hợp lệ | Gán đúng kiểu `T` hiện có |
| `value.compareTo(other)` | Không được bảo đảm | `Number` không khai báo `compareTo` |
| `value.add(other)` | Không được bảo đảm | Không phải mọi `Number` có method `add` |
| `value + other` | Không hợp lệ cho `T` chỉ bound bởi `Number` | Java không định nghĩa toán tử số học trực tiếp cho type variable `T` như primitive |
| `new T()` | Không hợp lệ | Không thể khởi tạo type variable bằng constructor trực tiếp |
| `new T[10]` | Không hợp lệ | Java không cho tạo mảng component type không reifiable như type variable `T` |

Upper bound cũng **không** cho phép truy cập field/method `private` của `U` chỉ vì `T extends U`: kiểm soát truy cập Java vẫn áp dụng. Những method cụ thể của subclass `Integer` hoặc `BigDecimal` cũng không tự nhiên được gọi trên `T` nếu bound chỉ là `Number`.

### Không nhầm `T` với `U`

Với `<T extends Number>`, bạn có thể gán `T` cho `Number`, nhưng không thể tùy ý gán `Number` thành `T`:

```java
static <T extends Number> void example(T value) {
    Number number = value;       // Hợp lệ.
    // T wrong = number;         // Không biên dịch.
}
```

Lý do: `T` có thể là `Integer`, còn biến `number` có thể tham chiếu đến `Double`. Quan hệ subtype chỉ bảo đảm chiều **T → Number**, không bảo đảm chiều ngược.

Tương tự, không thể coi `<T extends Number>` là một phép `cast` cho mọi object. Nếu dữ liệu đến từ nguồn bên ngoài, vẫn phải xác thực kiểu và nội dung theo yêu cầu nghiệp vụ.

## 4. Upper bound ở cấp class và ở cấp method

### 4.1. Khai báo trên generic class

```java
final class NumericBox<T extends Number> {
    private final T value;

    NumericBox(T value) {
        this.value = value;
    }

    T get() {
        return value;
    }

    double asDouble() {
        return value.doubleValue();
    }
}
```

`T extends Number` được khai báo **trên lớp**, nên constructor, field và instance methods cùng biết `T` phải phù hợp với `Number`. `NumericBox<Integer>` và `NumericBox<Double>` hợp lệ; `NumericBox<String>` không hợp lệ.

`T get()` trả đúng `T`, trong khi `double asDouble()` trả primitive `double`: hai method cung cấp hai kiểu hợp đồng khác nhau mà vẫn tận dụng chung upper bound.

### 4.2. Khai báo trên generic method

```java
class NumericTools {
    static <T extends Number> double square(T value) {
        double x = value.doubleValue();
        return x * x;
    }
}
```

Lớp `NumericTools` **không generic**; chỉ method `square` tự khai báo `<T extends Number>`. Mỗi lời gọi có thể dùng một subtype khác của `Number`.

### 4.3. Bound của lớp và bound của method độc lập

```java
class Holder<T extends Number> {
    private final T number;

    Holder(T number) {
        this.number = number;
    }

    <U extends CharSequence> String append(U suffix) {
        return number + ":" + suffix;
    }
}
```

`T` thuộc class và bound bởi `Number`; `U` thuộc method và bound bởi `CharSequence`. Hai type parameters có **phạm vi riêng**. Không nên khai báo một `<T>` mới ở method nếu mục tiêu là sử dụng chính `T` của lớp; việc dùng lại tên có thể che khuất type parameter ở lớp.

## 5. Ràng buộc bằng một interface nghiệp vụ

Không nhất thiết phải dùng `Number` hoặc interface có sẵn. Upper bound đặc biệt hữu ích khi thuật toán cần một **hợp đồng nghiệp vụ cụ thể**:

```java
interface HasId<ID> {
    ID id();
}

static <T extends HasId<String>> String extractId(T entity) {
    return entity.id();
}
```

Khi `T extends HasId<String>`, method biết `entity.id()` trả `String`, dù entity thực tế là `Product`, `Customer` hay một kiểu triển khai khác. Điều này cho phép tái sử dụng logic theo **khả năng được interface bảo đảm**, thay vì ép mọi entity kế thừa cùng một lớp nghiệp vụ.

Ví dụ:

```java
record Product(String id, String name) implements HasId<String> {}

String id = extractId(new Product("P-7", "Pen"));
```

Nếu một lớp chỉ implements `HasId<Long>`, nó không đáp ứng bound `HasId<String>` nói trên. Type arguments của interface generic vẫn quan trọng; không thể chỉ nhìn tên `HasId` rồi cho rằng mọi cách tham số hóa đều tương thích.

### Bound có thể gắn nhiều type variables

```java
interface HasId<ID> {
    ID id();
}

static <ID, T extends HasId<ID>> ID idOf(T entity) {
    return entity.id();
}
```

`ID` được khai báo trước `T`, và bound `T extends HasId<ID>` mô tả quan hệ giữa hai type parameters. Khi truyền một entity triển khai `HasId<Long>`, compiler có thể dùng quan hệ đó để xác định kiểu kết quả phù hợp với `Long`.

Đây là ví dụ một bound **có ích cho hợp đồng kiểu**, không chỉ giúp gọi method: nó kết nối loại entity với kiểu ID của chính entity ấy.

## 6. Nhận diện nhiều giới hạn trước khi đi sâu

Một type parameter có thể cần **đồng thời** hai khả năng độc lập, chẳng hạn tính toán như `Number` và so sánh như `Comparable<T>`. Khi đó Java cho phép:

```java
class NumericHolder<T extends Number & Comparable<T>> {
    private final T value;
    NumericHolder(T value) { this.value = value; }
    double asDouble() { return value.doubleValue(); }
    boolean isGreaterThan(T other) {
        return value.compareTo(other) > 0;
    }
}
```

`Integer` phù hợp cả hai bounds, nhưng `Number` **không** tự triển khai `Comparable<Number>`. Dấu `&` yêu cầu thỏa **tất cả** điều kiện, khác dấu phẩy phân tách nhiều type parameters. Nếu có class bound, class đứng đầu và các bounds sau là interfaces; không thể đặt hai class bounds.

Ví dụ này chỉ giới thiệu lý do cần multiple bounds; trọng tâm của bài hiện tại vẫn là **upper bound cấp quyền gọi phương thức nào trên `T`**. Các chương trình có nhiều interfaces, điều kiện tương thích và ảnh hưởng tới erasure được khai triển riêng ở các tình huống phức tạp hơn.

## 7. `Comparable<? super T>` và thuật toán chọn phần tử lớn nhất

Một yêu cầu hay gặp là **chọn phần tử lớn nhất** từ danh sách mà không ép kiểu về `Object`. Để gọi `compareTo` trên `T`, cần bound thích hợp:

```java
static <T extends Comparable<? super T>> T max(
        java.util.List<T> values) {

    if (values.isEmpty()) {
        throw new IllegalArgumentException("empty");
    }

    T best = values.get(0);
    for (T value : values) {
        if (value.compareTo(best) > 0) {
            best = value;
        }
    }
    return best;
}
```

`Comparable<? super T>` có nghĩa `T` triển khai khả năng so sánh với `T` **hoặc với một supertype của `T`**. Khi gọi `value.compareTo(best)`, compiler biết `best` kiểu `T` có thể được truyền vào hợp đồng so sánh.

Tại sao không chỉ viết `T extends Comparable<T>`? Bản thân cách viết ấy vẫn hợp lệ cho nhiều kiểu, nhưng **hẹp hơn**. Có những lớp con kế thừa `Comparable<Parent>` từ kiểu cha: chúng có thể so sánh các đối tượng lớp con thông qua hợp đồng `Comparable<Parent>` dù không implements trực tiếp `Comparable<Child>`. Bound `Comparable<? super T>` chấp nhận các trường hợp như vậy.

Ví dụ:

```java
String longestLexicographically =
        max(java.util.List.of("Go", "Java", "C"));

Integer maxScore = max(java.util.List.of(4, 9, 6));
```

`String` và `Integer` đều đáp ứng bound, nhưng chúng có quy tắc so sánh riêng. **`max` dựa vào thứ tự `compareTo`**, không dựa vào độ dài chuỗi: `"Java"` lớn hơn `"Go"` trong so sánh từ điển ở ví dụ. Không nhầm chữ `max` với “chuỗi dài nhất”.

Với `T extends Number`, compiler **không** cho gọi `compareTo()` vì `Number` không có phương thức ấy. Hãy chọn bound dựa trên thao tác thuật toán thực sự cần.

## 8. Chương trình Java 17+ hoàn chỉnh: từng bound mở khóa thao tác gì?

Lưu toàn bộ mã dưới đây vào **`Main.java`**. Ví dụ sử dụng:

- `T extends Number` để gọi `doubleValue()`;
- `T extends Comparable<? super T>` để gọi `compareTo`;
- `T extends Identified<String> & Named` để gọi đồng thời `id()` và `name()`;
- `T extends Number & Comparable<T>` để dùng cả phép chuyển số và phép so sánh.

```java
import java.util.ArrayList;
import java.util.List;

public class Main {
    interface Identified<ID> {
        ID id();
    }

    interface Named {
        String name();
    }

    record Product(String id, String name)
            implements Identified<String>, Named {}

    static <T extends Number> double sum(List<T> values) {
        double total = 0;
        for (T value : values) {
            total += value.doubleValue();
        }
        return total;
    }

    static <T extends Comparable<? super T>> T max(List<T> values) {
        if (values.isEmpty()) {
            throw new IllegalArgumentException("empty");
        }
        T best = values.get(0);
        for (T value : values) {
            if (value.compareTo(best) > 0) {
                best = value;
            }
        }
        return best;
    }

    static <T extends Identified<String> & Named>
    String describe(T entity) {
        return entity.id() + "/" + entity.name();
    }

    static final class PositiveBox<T extends Number & Comparable<T>> {
        private final T value;

        PositiveBox(T value) {
            this.value = value;
        }

        boolean greaterThan(T other) {
            return value.compareTo(other) > 0;
        }

        double twice() {
            return value.doubleValue() * 2;
        }
    }

    static <T extends Number> List<T> aboveThreshold(
            List<T> values, double threshold) {

        List<T> result = new ArrayList<>();
        for (T value : values) {
            if (value.doubleValue() > threshold) {
                result.add(value);
            }
        }
        return result;
    }

    public static void main(String[] args) {
        System.out.println("sum integers: " + sum(List.of(1, 2, 3, 4)));
        System.out.println("sum decimals: " + sum(List.of(1.5, 2.25)));

        System.out.println("max string: " + max(List.of("Go", "Java", "C")));
        System.out.println("max integer: " + max(List.of(4, 9, 6)));

        System.out.println("product: "
                + describe(new Product("P-7", "Pen")));

        PositiveBox<Integer> box = new PositiveBox<>(7);
        System.out.println("larger: " + box.greaterThan(5));
        System.out.println("double: " + box.twice());

        System.out.println("above: "
                + aboveThreshold(List.of(1, 4, 6), 3.0));

        // sum(List.of("A", "B")); // Không biên dịch: String không extends Number.
        // PositiveBox<Number> wrong; // Không thỏa Comparable<Number>.
    }
}
```

Biên dịch và chạy bằng JDK 17 trở lên:

```bash
javac -encoding UTF-8 Main.java
java Main
```

**Stdout:**

```text
sum integers: 10.0
sum decimals: 3.75
max string: Java
max integer: 9
product: P-7/Pen
larger: true
double: 14.0
above: [4, 6]
```

### Giải thích kết quả và cơ chế type checking

1. `sum(List.of(1,2,3,4))`: compiler suy luận `T` phù hợp với `Integer`; method chỉ gọi `doubleValue()` mà `Number` bảo đảm. Vì dùng `double` làm biến cộng dồn, kết quả hiển thị `10.0`.
2. `sum(List.of(1.5,2.25))`: type argument phù hợp với `Double`; dùng chung implementation, kết quả `3.75`.
3. `max(List.of("Go","Java","C"))`: `String` đáp ứng `Comparable<String>`, nên gọi `compareTo` hợp lệ; `"Java"` đứng lớn nhất theo thứ tự từ điển.
4. `max(List.of(4,9,6))`: `Integer` cũng đáp ứng bound, kết quả `9`.
5. `describe(Product)`: record `Product` triển khai cả `Identified<String>` và `Named`, nên cả hai method được gọi an toàn.
6. `PositiveBox<Integer>`: `Integer` vừa kế thừa `Number`, vừa triển khai `Comparable<Integer>`. `greaterThan(5)` sử dụng `compareTo` và `twice()` dùng `doubleValue()`.
7. `aboveThreshold` trả `List<T>`, **giữ lại kiểu phần tử** của danh sách đầu vào. Với `List<Integer>`, kết quả vẫn có kiểu `List<Integer>`; method chỉ so sánh bằng giá trị `double` suy ra từ từng phần tử.

**Giới hạn của ví dụ:** `sum` tính tổng qua `doubleValue()`, nên không dành cho phép tính tài chính cần độ chính xác thập phân tuyệt đối. Với `BigDecimal`, hãy dùng phép tính theo chính kiểu `BigDecimal` hoặc một abstraction phù hợp. `max` yêu cầu danh sách không rỗng và các phần tử có thể so sánh được; chương trình ném `IllegalArgumentException` trên danh sách rỗng, còn các giá trị `null` có thể gây lỗi khi truy cập method. Upper bound **không** tự loại trừ `null`.

## 9. Bound không có nghĩa là mọi thao tác số học đều hợp lệ

Một chỗ dễ nhầm là chữ `Number` trong bound. Ví dụ:

```java
static <T extends Number> double add(T a, T b) {
    return a.doubleValue() + b.doubleValue();
}
```

Cách này hợp lệ vì phép cộng được thực hiện giữa **hai `double` primitive**, sau khi gọi method mà `Number` cung cấp.

Ngược lại:

```java
// Không biên dịch:
// static <T extends Number> T add(T a, T b) {
//     return a + b;
// }
```

Java không có toán tử `+` tổng quát cho mọi subtype `Number`, và không thể bảo đảm kết quả là cùng subtype `T`. Nếu `T = Integer`, biểu thức cộng có thể cần xử lý tràn số; nếu `T = BigDecimal`, việc cộng phải qua `add(...)`; nếu `T = AtomicInteger`, hành vi cũng khác. Một upper bound như `Number` không phát minh ra phép cộng chung cho mọi số.

**Chọn abstraction đúng:**

- Khi chỉ cần thống kê/hiển thị gần đúng, chuyển qua `doubleValue()` có thể phù hợp nếu chấp nhận giới hạn độ chính xác.
- Khi cần toán học chính xác trên `BigDecimal`, dùng API cụ thể `BigDecimal.add`, `multiply` và quy tắc rounding.
- Khi cần thuật toán tùy biến giữa nhiều kiểu số, cân nhắc truyền vào một hàm cộng/strategy có chữ ký rõ ràng thay vì ép kiểu không kiểm chứng.

## 10. Upper bound khác với wildcard `? extends U`

Hai cú pháp gần giống nhau nhưng được dùng cho **hai mục đích**:

```java
<T extends Number>           // Khai báo một type variable tên T có upper bound.
List<? extends Number>       // Dùng wildcard trong một parameterized type.
```

### 10.1. Type parameter được đặt tên và dùng lại

```java
static <T extends Number> T first(List<T> numbers) {
    return numbers.get(0);
}
```

`T` xuất hiện ở **cả tham số đầu vào và kiểu trả về**. Caller truyền `List<Integer>` thì kết quả được kiểm tra theo `Integer`, không chỉ theo `Number`.

### 10.2. Wildcard chỉ biểu diễn một kiểu chưa biết tại vị trí đó

```java
static double sumWildcard(List<? extends Number> numbers) {
    double total = 0;
    for (Number value : numbers) {
        total += value.doubleValue();
    }
    return total;
}
```

Method nhận `List<Integer>`, `List<Double>` và các danh sách subtype `Number` phù hợp. Nó chỉ cần đọc từng `Number`, **không cần đặt tên cho kiểu phần tử** hoặc trả lại một `T` cụ thể.

### Bảng so sánh

| Điểm so sánh | `<T extends Number>` | `? extends Number` |
| --- | --- | --- |
| Vai trò | Khai báo type parameter có tên | Chỉ định wildcard khi sử dụng generic type |
| Có tên `T` để dùng lại trong nhiều vị trí? | Có | Không |
| Khi hợp lý | Liên kết kiểu input và output, nhiều tham số cùng kiểu, thuật toán phải giữ `T` | Chỉ đọc từ cấu trúc gồm số thuộc một subtype bất kỳ |
| Dùng ở khai báo phương thức | `static <T extends Number> ...` | Không dùng wildcard làm type parameter khai báo |
| Dùng trong `List` | `List<T>` sau khi đã khai báo `T` | `List<? extends Number>` |
| Tự cho phép thêm `Number` tùy ý vào danh sách? | Phải xét đúng `T` và loại danh sách | Không, vì kiểu phần tử cụ thể chưa biết |

Ví dụ:

```java
List<Integer> integers = new ArrayList<>();
List<? extends Number> numbers = integers;

// numbers.add(3.14); // Không biên dịch.
// numbers.add(10);   // Cũng không biên dịch.
```

Vì `numbers` thực ra có thể tham chiếu `List<Integer>`, compiler phải ngăn việc thêm một `Double` hay một `Number` tùy ý. Có thể thêm `null` qua những thao tác phù hợp, nhưng điều đó không có ích khi mục đích là thêm số hợp lệ.

### Upper bound không làm generic container hiệp biến

```java
List<Integer> integers = new ArrayList<>();

// Không biên dịch:
// List<Number> allNumbers = integers;

List<? extends Number> readNumbers = integers; // Hợp lệ.
```

`Integer extends Number` không biến `List<Integer>` thành subtype của `List<Number>`: generic types Java mặc định **bất biến**. Upper bound trên `T` và wildcard `? extends Number` giúp viết API linh hoạt theo những cách khác nhau, nhưng không thay đổi quy tắc ấy.

## 11. Bounds và quan hệ kế thừa giữa các generic classes

Một class con có thể **giữ hoặc thu hẹp** giới hạn của type parameter khi truyền nó cho superclass:

```java
class Box<T> {
    private final T value;

    Box(T value) {
        this.value = value;
    }

    T get() {
        return value;
    }
}

class NumberBox<N extends Number> extends Box<N> {
    NumberBox(N value) {
        super(value);
    }

    double asDouble() {
        return get().doubleValue();
    }
}
```

`Box<T>` chấp nhận mọi reference type, còn `NumberBox<N>` chỉ chấp nhận subtype của `Number`. `NumberBox<Integer>` hợp lệ, `NumberBox<String>` không hợp lệ.

Ngược lại, nếu superclass đã có ràng buộc:

```java
class NumericStore<T extends Number> {}

// Không biên dịch:
// class BadStore<T> extends NumericStore<T> {}
```

Compiler chưa thể bảo đảm một `T` hoàn toàn tự do thỏa `T extends Number` mà `NumericStore` yêu cầu. Sửa bằng cách giữ bound:

```java
class GoodStore<T extends Number> extends NumericStore<T> {}
```

Hoặc truyền trực tiếp một subtype cụ thể hợp lệ:

```java
class IntegerStore extends NumericStore<Integer> {}
```

Bounds của superclass/interface là một phần của hợp đồng: subclass muốn truyền type argument phải đáp ứng ràng buộc mà kiểu cha yêu cầu.

## 12. Bounds và type erasure ở runtime

Java generics chủ yếu được triển khai bằng **type erasure**: compiler kiểm tra kiểu ở compile time, sau đó xóa phần lớn type arguments và type variables trong bytecode. Với `T extends Number`, kiểu bị xóa của `T` thường là **bound ngoài cùng bên trái** `Number`, thay vì `Object` như type parameter không bị bound.

Trong:

```java
static <T extends Number> double asDouble(T value) {
    return value.doubleValue();
}
```

code biên dịch có thể gọi `Number.doubleValue()` vì erasure của type variable phù hợp với `Number`. Khi có nhiều bounds, compiler có thể chèn cast và bridge methods nếu cần để duy trì hành vi đa hình.

Điều này **không** tạo một runtime class riêng cho mỗi `T`, và cũng không khiến kiểm tra sau hợp lệ:

```java
// Không biên dịch:
// if (object instanceof List<Integer>) { ... }

// Không biên dịch:
// T item = new T();

// Không biên dịch:
// T[] items = new T[10];
```

Nếu cần tạo một đối tượng kiểu `T`, một cách an toàn hơn là nhận **factory** có kiểu `Supplier<T>` hoặc constructor/factory được truyền vào:

```java
static <T extends Number> T fromFactory(
        java.util.function.Supplier<T> supplier) {
    return supplier.get();
}
```

Caller có thể truyền `() -> Integer.valueOf(5)`; đó là đối tượng thực được tạo bởi factory, không phải `new T()` do compiler suy đoán constructor của mọi subclass.

Nếu cần kiểm tra kiểu runtime cho một giá trị riêng lẻ, có thể truyền `Class<T>` vào phương thức và dùng `Class.cast` hoặc phương thức kiểm tra tương ứng. Tuy nhiên, `Class<List<Integer>>` không biểu diễn đầy đủ kiểu generic lồng ở runtime theo cách `List<Integer>.class`; việc validate cấu trúc phức tạp có thể cần type reference/schema hoặc kiểm tra từng phần tử.

## 13. Những hiểu nhầm và lỗi phổ biến

**Lỗi 1 — dùng `implements` trong bound:** không viết `<T implements Runnable>`. Type parameter bound luôn dùng `extends`, dù `Runnable` là interface.

**Lỗi 2 — tưởng `T extends Number` chỉ nhận đúng `Number`:** bound cho phép cả `Number` và subtype phù hợp. `Integer`, `Long`, `Double`, `BigDecimal` đều là các ví dụ hợp lệ.

**Lỗi 3 — gọi method không tồn tại trong bound:** `value.compareTo(other)` không được bảo đảm với `T extends Number`. Cần giới hạn có `Comparable` phù hợp hoặc sử dụng strategy.

**Lỗi 4 — dùng toán tử số học trực tiếp trên `T`:** phép `a + b` không hợp lệ cho hai biến chỉ có kiểu `T extends Number`. Chuyển sang thao tác mà bound bảo đảm hoặc dùng abstraction cụ thể.

**Lỗi 5 — đặt nhiều class bounds hoặc sai thứ tự:**

```java
// Sai:
// <T extends Runnable & Number>
// <T extends Number & java.util.AbstractList<T>>
```

Nếu có class, nó phải đầu tiên; các bound sau là interface. Không có hai superclass riêng cho một type parameter.

**Lỗi 6 — quên rằng bound phải thỏa ở lớp con:** nếu `Parent<T extends Number>` thì `Child<T> extends Parent<T>` không hợp lệ khi `T` chưa có bound phù hợp.

**Lỗi 7 — cho rằng upper bound loại bỏ `null`:** các reference type phù hợp vẫn có thể có giá trị `null`. Gọi `doubleValue()` trên `null` vẫn ném `NullPointerException`; validation và kiểu generic là hai vấn đề riêng.

**Lỗi 8 — nhầm type parameter với wildcard:** `<T extends Number>` tạo một tên kiểu có thể dùng lại; `? extends Number` là kiểu không biết chính xác tại một vị trí. Chọn dựa trên nhu cầu liên kết input/output.

**Lỗi 9 — hiểu `T extends Number` là mọi số đều so sánh được cùng cách:** `Number` không khai báo `compareTo`; `BigDecimal` và `Double` còn có ngữ nghĩa số học khác nhau. Không suy luận tính chất không có trong bound.

**Lỗi 10 — dùng cast không kiểm chứng để vượt bound:** `(T) arbitraryObject` không biến object thật thành `T`, có thể sinh unchecked warning và lỗi runtime. Hãy thiết kế hợp đồng cụ thể hoặc validate ở ranh giới.

**Lỗi 11 — hy vọng `T` tự có constructor mặc định:** ngay cả khi có upper bound, `new T()` vẫn không hợp lệ. Dùng factory/supplier hoặc truyền object đã tạo.

**Lỗi 12 — dùng `T` làm primitive type argument:** `List<int>` và `<T extends int>` không hợp lệ. Dùng wrapper/reference type phù hợp hoặc cấu trúc primitive nếu cần hiệu năng.

**Lỗi 13 — nhầm thứ tự trong nhiều bounds:** khi class bound xuất hiện, nó phải đứng đầu; `T extends Comparable<T> & Number` không hợp lệ vì `Number` là class nằm sau interface.

**Lỗi 14 — quên giới hạn độ chính xác của phép chuyển số:** `doubleValue()` hợp lệ về kiểu nhưng không bảo đảm bảo toàn toàn bộ chữ số của `BigDecimal` hay mọi `long`. Đối chiếu yêu cầu nghiệp vụ trước khi dùng thuật toán tổng quát qua `double`.

## 14. Quy trình chọn upper bound phù hợp

Trước khi khai báo `<T extends U>`, hãy đi từ **thao tác thực sự cần**:

1. **Liệt kê method cần gọi**: `doubleValue()`, `length()`, `compareTo()`, `id()` hay `name()`?
2. **Tìm hợp đồng chung nhỏ nhất phù hợp**: `Number`, `CharSequence`, `Comparable<? super T>` hay một interface nghiệp vụ tự định nghĩa?
3. **Kiểm tra mọi type argument dự kiến** có đáp ứng bound không; một loại class cụ thể không nhất thiết đại diện cho tất cả các subtype.
4. **Xác định đầu ra có cần giữ `T` không**: trả `T`, `List<T>` hay chỉ cần kiểu cố định như `double` hoặc `String`?
5. **Cân nhắc wildcard** nếu method chỉ đọc từ một cấu trúc generic và không cần đặt tên kiểu để liên kết nhiều vị trí.
6. **Xem xét null, dữ liệu rỗng và độ chính xác**: bound chỉ nói về khả năng của type, không bảo đảm các ràng buộc giá trị.
7. **Giữ signature đơn giản nhất đáp ứng hợp đồng**: mỗi bound nên có lý do rõ ràng, tránh thêm ràng buộc không dùng đến.

| Nhu cầu | Upper bound thường phù hợp |
| --- | --- |
| Truy cập `doubleValue()` hoặc `longValue()` | `<T extends Number>` |
| Đọc chiều dài chuỗi/ký tự | `<T extends CharSequence>` |
| So sánh phần tử cùng kiểu hoặc kiểu cha tương thích | `<T extends Comparable<? super T>>` |
| Cần vừa đặc điểm `Number`, vừa `compareTo` | `<T extends Number & Comparable<T>>` khi phù hợp với các type arguments thực tế |
| Cần ID theo kiểu cụ thể | `<T extends HasId<String>>` |
| Cần vừa có ID, vừa có tên | `<T extends Identified<String> & Named>` |
| Chỉ duyệt danh sách số để đọc | `List<? extends Number>` có thể đủ, không cần một `T` có tên |

## 15. Bài tập tự kiểm tra

**Câu 1.** Vì sao `static <T> double doubleValue(T value)` không thể gọi trực tiếp `value.doubleValue()`?

**Đáp án:** `T` không có upper bound diễn đạt khả năng ấy. Nếu compiler cho phép, method có thể được gọi với `String` hoặc bất kỳ reference type không có `doubleValue()`. Thêm `T extends Number` khi mục đích thật sự là làm việc với `Number`.

**Câu 2.** Với `static <T extends Number> double twice(T value)`, `twice(4)` có hợp lệ không?

**Đáp án:** Có. Literal `int` được boxing tương thích với `Integer` trong quá trình suy luận/gọi method generic. `Integer` là subtype của `Number`.

**Câu 3.** Một method có `T extends Number` được gọi `compareTo()` trên `T` không?

**Đáp án:** Không. `Number` không khai báo `compareTo`. Nếu cần so sánh, ràng buộc `Comparable<? super T>` hoặc các multiple bounds thích hợp.

**Câu 4.** `<T extends Identified<String> & Named>` yêu cầu `T` thỏa mấy hợp đồng?

**Đáp án:** Cả hai: `Identified<String>` và `Named`. Dấu `&` thể hiện giao của các giới hạn; class được chọn phải đáp ứng tất cả.

**Câu 5.** `List<Integer>` có gán trực tiếp cho `List<Number>` không? Có thể gán cho `List<? extends Number>` không?

**Đáp án:** Không gán trực tiếp cho `List<Number>` vì generic type bất biến. Có thể gán cho `List<? extends Number>` để đọc giá trị theo một subtype chưa biết; không được thêm một `Number` tùy ý qua tham chiếu wildcard.

**Câu 6.** Tại sao không thể viết `new T()` trong phương thức `<T extends Number>`?

**Đáp án:** Upper bound không bảo đảm mọi subtype của `Number` đều có một constructor không tham số hợp lệ, và erasure không giữ đối số kiểu để khởi tạo trực tiếp. Dùng factory hoặc đối tượng được truyền vào.

**Câu 7.** Nếu lớp cha là `class NumericStore<T extends Number>`, dòng `class Child<T> extends NumericStore<T>` hợp lệ không?

**Đáp án:** Không, vì `T` của `Child` chưa bị ràng buộc. Cần `class Child<T extends Number> extends NumericStore<T>` hoặc chọn type argument cụ thể đáp ứng `Number`.

**Câu 8.** `max(List<String>)` trong ví dụ dựa vào độ dài chuỗi hay `compareTo`?

**Đáp án:** Dựa vào `compareTo`, tức thứ tự so sánh tự nhiên của `String` theo hợp đồng Java, không phải độ dài. Tên hàm không tự suy ra tiêu chí nghiệp vụ.

## Tổng kết

Cú pháp `<T extends U>` đặt một **upper bound** lên type parameter `T`, qua đó cho phép trình biên dịch kiểm tra và cho sử dụng những thao tác được bảo đảm bởi `U`. Bound có thể là class, interface, type variable hoặc nhiều giới hạn kết hợp bằng `&`. Một method với `T extends Number` được gọi `doubleValue()` nhưng **không** tự được gọi `compareTo()` hay sử dụng toán tử số học trực tiếp trên `T`; muốn thêm khả năng cần thêm bound hoặc thiết kế abstraction phù hợp. Khi cần liên hệ chính xác kiểu đầu vào và đầu ra, type parameter có tên hữu ích hơn wildcard; khi chỉ đọc dữ liệu, `? extends U` có thể đơn giản hơn. Upper bound chủ yếu bảo đảm **kiểu lúc biên dịch**, không thay thế kiểm tra `null`, độ chính xác số học, dữ liệu runtime hoặc quy tắc nghiệp vụ.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="generic-type-bounds-002">Giới hạn bằng lớp hoặc giao diện</a>

<details>
<summary>Xem chi tiết</summary>

# Giới hạn bằng lớp hoặc giao diện trong Java Generics

Trong Java Generics, một type parameter không có giới hạn tường minh như `<T>` có thể đại diện cho nhiều reference types khác nhau. Điều đó giúp tái sử dụng mã, nhưng compiler chỉ bảo đảm những thao tác có trên kiểu `Object`. Nếu thuật toán phải gọi `doubleValue()`, `compareTo()`, `run()` hoặc một phương thức thuộc interface cụ thể, bạn cần **ràng buộc kiểu (type bound)** ngay tại nơi khai báo type parameter.

Cú pháp cơ bản là `<T extends Bound>`. `Bound` có thể là **class** hoặc **interface**; trong cả hai trường hợp Java đều dùng từ khóa `extends`. Với nhiều yêu cầu đồng thời, có thể dùng các cận nối bằng `&`, chẳng hạn `<T extends Number & Comparable<T>>`. Những giới hạn này được kiểm tra chủ yếu **lúc biên dịch** và cùng tham gia quá trình suy luận kiểu ở mỗi lời gọi.

Bài này giải thích cơ chế, cú pháp, cách áp dụng trên class, interface, record và generic method, multiple bounds, generic inheritance, ví dụ chạy Java 17+, điểm liên hệ với wildcard và type erasure, cùng các lỗi dễ gặp. Mục tiêu là biết **khi nào cần bound, compiler chứng minh được gì nhờ bound, và khi nào không nên làm API phức tạp hơn mức cần thiết**.

## 1. Lựa chọn giữa class bound và interface bound

Nếu không có bound, type variable `T` thông thường chỉ được bảo đảm các khả năng của `Object`; vì thế không thể gọi `value.doubleValue()` trên mọi `T`. Với `<T extends Number>`, compiler biết phương thức `doubleValue()` tồn tại, nhưng điều này cũng **thu hẹp tập type arguments** xuống những kiểu phù hợp với `Number`.

Bài này tập trung trả lời câu hỏi thiết kế: **khi nào nên chọn một lớp làm bound, khi nào chọn interface và việc chọn đó thay đổi API ra sao?**

- **Class bound** như `Number`: tái sử dụng hợp đồng đã có, đồng thời yêu cầu quan hệ kế thừa với lớp.
- **Interface bound** như `Runnable`: yêu cầu một năng lực mà các lớp ở nhiều họ kế thừa khác nhau có thể triển khai.
- **Parameterized interface bound** như `Identified<ID>`: bảo toàn quan hệ kiểu của kết quả hoặc định danh qua một hợp đồng generic.

Nếu chỉ cần `double twice(Number value)` không có mối quan hệ `T` nào phải bảo toàn, chữ ký nhận trực tiếp `Number` có thể đơn giản hơn. Trong các mục tiếp theo, so sánh class/interface được kiểm chứng bằng những chương trình Java 17 độc lập.

## 2. Bound bằng class: `<T extends Number>`

Chương trình đầu tiên cho thấy cùng một generic method làm việc với hai subtype của `Number`.

Tạo `ClassBoundDemo.java`:

```java
public class ClassBoundDemo {
    static <T extends Number> double twice(T value) {
        return value.doubleValue() * 2;
    }

    static <T extends Number> T chooseFirst(T first, T second) {
        return first;
    }

    public static void main(String[] args) {
        System.out.println(twice(3));
        System.out.println(twice(2.5));

        Integer selected = chooseFirst(10, 20);
        System.out.println(selected);
    }
}
```

Biên dịch và chạy:

```bash
javac ClassBoundDemo.java
java ClassBoundDemo
```

Kết quả `stdout`:

```text
6.0
5.0
10
```

### Đọc từng ràng buộc

**`twice(T value)`:** compiler cho phép gọi `doubleValue()` trên `value` vì `T` bị giới hạn bởi `Number`. Lời gọi `twice(3)` sử dụng boxing khi cần để có reference type; generic type arguments không nhận primitive `int` trực tiếp. Kết quả của hàm là primitive `double`.

**`chooseFirst(T first, T second)`:** hai tham số và kiểu trả về đều dùng **cùng một type variable T**. Với hai đối số `Integer`, một lựa chọn kiểu hợp lệ là `T=Integer`; caller có thể nhận `Integer` mà không phải cast.

Bound `Number` chỉ đòi các giá trị phù hợp với `Number`; nó **không** bắt hai đối số phải có exact runtime class giống nhau. Nếu hai kiểu khác nhau, compiler có thể giải các ràng buộc để tìm kiểu thích hợp (ví dụ một supertype chung) tùy target context. Không dùng `<T extends Number>` làm cơ chế kiểm tra “hai lớp runtime phải bằng nhau”.

### Class bound cho phép những thao tác nào?

Với `<T extends Number>`, compiler có thể cho phép các instance methods công khai thích hợp từ `Number` như `intValue()`, `longValue()`, `floatValue()` và `doubleValue()`. Nhưng nó **không** tự cho phép gọi phương thức chỉ có ở `Integer` hoặc `BigDecimal` mà không nằm trong hợp đồng của `Number`.

Chẳng hạn, `value.compareTo(other)` không được bảo đảm chỉ từ bound `Number`. `Number` không khai báo `compareTo`. Nếu thuật toán còn cần so sánh, phải bổ sung một interface bound hoặc nhận riêng một `Comparator`.

### Những kiểu nào thỏa cận Number?

`Integer`, `Long`, `Double`, `Float`, `Short`, `Byte`, `java.math.BigDecimal` và `java.math.BigInteger` đều là các ví dụ subtype của `Number`. `String` không phải subtype của `Number`, dù bạn có thể chuyển chuỗi số thành số bằng phương thức parse.

`Number` là abstract class nhưng vẫn hoàn toàn hợp lệ làm bound; một type argument có thể chính là `Number` hoặc một subtype phù hợp. **Bound không yêu cầu phải gọi `new Number()`**, và abstract class không phải lý do cản trở generic constraint.

## 3. Bound bằng interface: vẫn dùng `extends`

Khai báo generic:

```java
static <T extends Runnable> void execute(T task) {
    task.run();
}
```

Từ khóa `extends` ở đây áp dụng cho **interface `Runnable`**, mặc dù class cụ thể thực thi interface sẽ dùng `implements Runnable`. Hai vị trí cú pháp khác nhau:

- `class Job implements Runnable`: một **class** triển khai interface.
- `<T extends Runnable>`: một **type parameter** có cận trên là interface.
- Không viết `<T implements Runnable>`; đó là cú pháp sai.

### Ví dụ với Runnable

Tạo `InterfaceBoundDemo.java`:

```java
public class InterfaceBoundDemo {
    static final class Printer implements Runnable {
        private final String message;

        Printer(String message) {
            this.message = message;
        }

        @Override
        public void run() {
            System.out.println(message);
        }
    }

    static <T extends Runnable> void execute(T task) {
        task.run();
    }

    public static void main(String[] args) {
        execute(new Printer("running"));
        execute(() -> System.out.println("lambda"));
    }
}
```

Biên dịch và chạy:

```bash
javac InterfaceBoundDemo.java
java InterfaceBoundDemo
```

`stdout`:

```text
running
lambda
```

`Printer` implements `Runnable` nên hợp lệ làm đối số của `execute`. Java cũng suy luận được cách gọi `execute` bằng lambda, vì ngữ cảnh tham số generic mang cận interface chức năng `Runnable`.

Nếu truyền một đối tượng không implement `Runnable`, compiler không chứng minh được bound và sẽ từ chối lời gọi. Generic method không tự biến đối tượng tùy ý thành `Runnable`; phải có quan hệ kiểu thích hợp.

### Một interface tự khai báo generic

Giới hạn không nhất thiết phải là interface không generic. Ví dụ:

```java
interface Identified<ID> {
    ID id();
}

static <ID, T extends Identified<ID>> ID extractId(T value) {
    return value.id();
}
```

Đây là phần khai báo minh họa (method phải đặt trong class thích hợp). Trong `<ID, T extends Identified<ID>>`, cả `ID` và `T` đều là type parameters của method; `T` bị giới hạn bởi **một interface đã áp dụng type argument `ID`**. Vì thế `extractId` trả chính kiểu `ID` tương ứng với giá trị đầu vào.

Điểm mấu chốt không phải tên biến `ID` hay `T` mà là **quan hệ được viết trong bound**: `T` phải thực thi `Identified<ID>` theo một cách tham số hóa tương thích.

## 4. Một chương trình đầy đủ: bound bằng generic interface

Tạo `GenericInterfaceBoundDemo.java`:

```java
public class GenericInterfaceBoundDemo {
    interface Identified<ID> {
        ID id();
    }

    record User(Long id, String name) implements Identified<Long> {
    }

    record Product(String id, String title)
            implements Identified<String> {
    }

    static <ID, T extends Identified<ID>> ID extractId(T item) {
        return item.id();
    }

    public static void main(String[] args) {
        User user = new User(42L, "Lan");
        Product product = new Product("P-5", "Book");

        Long userId = extractId(user);
        String productId = extractId(product);

        System.out.println(userId);
        System.out.println(productId);
    }
}
```

Chạy:

```bash
javac GenericInterfaceBoundDemo.java
java GenericInterfaceBoundDemo
```

`stdout`:

```text
42
P-5
```

Phân tích:

1. `Identified<ID>` là generic interface; `ID` biểu diễn kiểu định danh.
2. `User` cố định interface thành `Identified<Long>`; `Product` cố định thành `Identified<String>`.
3. `<ID, T extends Identified<ID>>` khai báo hai type parameters và liên kết chúng.
4. `extractId(user)` có thể suy luận kết quả phù hợp với `Long`; `extractId(product)` phù hợp với `String`.
5. Generic bound cho phép gọi `item.id()` và giữ quan hệ return type với interface. Nó không xác minh giá trị ID có tồn tại trong database hay không; đó là yêu cầu nghiệp vụ khác.

Một method chỉ nhận `Identified<ID>` cũng có thể đủ cho bài toán này:

```java
static <ID> ID extractId(Identified<ID> item) {
    return item.id();
}
```

Chữ ký ngắn hơn vẫn giữ quan hệ `ID`. Chỉ cần thêm `T extends Identified<ID>` khi chính xác kiểu con `T` cần xuất hiện ở nơi khác trong method hoặc trong hợp đồng trả về.

## 5. Multiple bounds: một type parameter phải thỏa nhiều yêu cầu

Nếu thuật toán vừa cần một base class vừa cần một interface, có thể viết **multiple bounds**:

```java
<T extends SomeClass & SomeInterface>
```

Dấu `&` tại đây biểu diễn **giao các giới hạn**: `T` phải tương thích với cả hai cận. Quy tắc cú pháp:

1. Chỉ có **tối đa một class bound tường minh**, và nếu có thì **phải đứng đầu**.
2. Sau class bound có thể là một hoặc nhiều interface bounds.
3. Nếu toàn interface, bạn có thể bắt đầu bằng interface bất kỳ rồi nối các interface khác với `&`.
4. Dùng `extends` **một lần** trước danh sách bounds; các bounds còn lại nối bằng `&`.
5. Bound không phải phép hợp `|`; `T` phải đáp ứng **tất cả** điều kiện, không phải chỉ một.

Ví dụ hợp lệ:

```java
<T extends Number & Comparable<T>>
<T extends Runnable & AutoCloseable>
<T extends java.io.Serializable & Comparable<T>>
```

Các dòng trên là mẫu khai báo type-parameter list (không phải chương trình chạy độc lập). Ngược lại, `<T extends Runnable & Number>` là không hợp lệ vì nếu `Number` làm class bound thì nó phải đứng đầu.

### Tại sao `Number` chưa đủ để so sánh?

`Number` cung cấp các phép chuyển đổi sang primitive numeric types nhưng không khai báo `compareTo`. Khi cần trả về giá trị lớn hơn theo một quy tắc tự nhiên, phải bảo đảm cả khả năng nhận giá trị kiểu `T` và khả năng so sánh.

```java
static <T extends Number & Comparable<T>> T larger(T a, T b) {
    return a.compareTo(b) >= 0 ? a : b;
}
```

`Integer`, `Long`, `Double` đều phù hợp với chữ ký này trong các lời gọi riêng vì mỗi wrapper triển khai `Comparable` với kiểu tương ứng. `java.math.BigDecimal` cũng thỏa. Nhưng một class con `Number` tự tạo mà không implement `Comparable<T>` sẽ không phù hợp, dù có đủ `doubleValue()`.

## 6. Ví dụ class bound kết hợp interface bound

Tạo `MultipleBoundDemo.java`:

```java
public class MultipleBoundDemo {
    static <T extends Number & Comparable<T>> T larger(T a, T b) {
        return a.compareTo(b) >= 0 ? a : b;
    }

    static <T extends Number & Comparable<T>> double doubledMax(
            T a, T b) {
        return larger(a, b).doubleValue() * 2;
    }

    public static void main(String[] args) {
        Integer topScore = larger(8, 12);
        Double topPrice = larger(2.5, 1.5);

        System.out.println(topScore);
        System.out.println(topPrice);
        System.out.println(doubledMax(3, 7));
    }
}
```

Chạy:

```bash
javac MultipleBoundDemo.java
java MultipleBoundDemo
```

`stdout`:

```text
12
2.5
14.0
```

Trong `larger`:

- `Number` cho phép xử lý như một đối tượng Number, nhưng riêng method này chỉ dùng `compareTo`.
- `Comparable<T>` cho phép gọi `a.compareTo(b)` với `b` kiểu `T`.
- Return type `T` bảo toàn quan hệ kiểu với hai tham số.
- `doubledMax` cần cả `compareTo` để tìm phần tử lớn hơn và `doubleValue()` để trả `double`.

Các ví dụ riêng lẻ với `Integer` và `Double` không có nghĩa generic method có thể tùy ý so sánh `Integer` với `Double` bằng cách gọi `compareTo`. Hai wrapper implement `Comparable` với các type arguments khác nhau; một lời gọi lẫn hai loại không tự đáp ứng bound `Comparable<T>` một cách tùy tiện. Nếu mục tiêu là so sánh nhiều loại Number hỗn hợp, hãy định nghĩa một chiến lược so sánh cụ thể (chẳng hạn chuyển sang `double` với lưu ý về mất độ chính xác) thay vì giả định một natural order chung.

### Khi nào chọn `Comparable<? super T>`?

Một chữ ký thường linh hoạt hơn là:

```java
static <T extends Comparable<? super T>> T max(T a, T b) {
    return a.compareTo(b) >= 0 ? a : b;
}
```

`Comparable<? super T>` chấp nhận trường hợp một kiểu `T` có khả năng so sánh với **một supertype của T**, chẳng hạn thông qua interface `Comparable<Base>` được một subtype kế thừa. Cách viết này phổ biến trong Java Collections API. Nó không yêu cầu `T` là `Number`; `String` cũng có thể được so sánh.

Chữ ký `<T extends Number & Comparable<? super T>>` dùng khi **đồng thời** cần hành vi `Number` và so sánh tự nhiên. Chọn bound tối thiểu nhưng đúng để không loại bỏ những kiểu hợp lệ khác.

## 7. Multiple interface bounds: không cần class bound

Nếu thuật toán cần hai hợp đồng độc lập từ interface, có thể ràng buộc `T` bằng cả hai:

```java
static <T extends Runnable & AutoCloseable> void executeAndClose(T task)
        throws Exception {
    try (task) {
        task.run();
    }
}
```

Đây là method Java 17 hợp lệ khi đặt trong class. `Runnable` bảo đảm `run()`; `AutoCloseable` cho phép dùng task trong try-with-resources để đóng sau khi thực thi, kể cả lúc `run` ném lỗi.

### Chương trình hoàn chỉnh

Tạo `InterfaceIntersectionDemo.java`:

```java
public class InterfaceIntersectionDemo {
    static final class Job implements Runnable, AutoCloseable {
        @Override
        public void run() {
            System.out.println("running");
        }

        @Override
        public void close() {
            System.out.println("closed");
        }
    }

    static <T extends Runnable & AutoCloseable> void executeAndClose(T task)
            throws Exception {
        try (task) {
            task.run();
        }
    }

    public static void main(String[] args) throws Exception {
        executeAndClose(new Job());
    }
}
```

Biên dịch và chạy:

```bash
javac InterfaceIntersectionDemo.java
java InterfaceIntersectionDemo
```

`stdout`:

```text
running
closed
```

`Job` phải implement **cả** `Runnable` và `AutoCloseable`. Một đối tượng chỉ implements `Runnable` không thỏa bound còn lại. Thứ tự cận interface có thể thay đổi ở mức khả năng của compiler chứng minh các thành viên, nhưng thứ tự cận có ý nghĩa trong **type erasure**, vì cận đầu tiên được dùng khi xóa biến kiểu.

Lưu ý: `AutoCloseable.close()` có thể khai báo ném `Exception`, nên ví dụ cho phép ngoại lệ bằng `throws Exception`. Đây là hợp đồng exception thông thường, không phải đặc quyền của Generic.

## 8. Generic class với bound bằng class

Bound không chỉ áp dụng cho generic method. Bạn có thể khai báo trực tiếp ở class:

```java
class NumericBox<T extends Number> {
    private final T value;

    NumericBox(T value) {
        this.value = value;
    }

    T get() {
        return value;
    }

    double asDouble() {
        return value.doubleValue();
    }
}
```

Đây là class minh họa, không có `main`. `T extends Number` có phạm vi trong instance members của `NumericBox`: component/field `value` có kiểu `T`, `get()` trả `T`, và `asDouble()` gọi method có trên `Number`.

Với `NumericBox<Integer>`, `get()` có kiểu biểu thức `Integer`; với `NumericBox<java.math.BigDecimal>`, nó có kiểu `BigDecimal`. Cả hai có thể gọi `asDouble()`.

### Ví dụ đầy đủ

Tạo `BoundedClassDemo.java`:

```java
public class BoundedClassDemo {
    static final class NumericBox<T extends Number> {
        private final T value;

        NumericBox(T value) {
            this.value = value;
        }

        T get() {
            return value;
        }

        double twice() {
            return value.doubleValue() * 2;
        }
    }

    public static void main(String[] args) {
        NumericBox<Integer> count = new NumericBox<>(6);
        NumericBox<Double> weight = new NumericBox<>(1.25);

        Integer countValue = count.get();

        System.out.println(countValue);
        System.out.println(count.twice());
        System.out.println(weight.twice());
    }
}
```

Chạy `javac BoundedClassDemo.java` rồi `java BoundedClassDemo`:

```text
6
12.0
2.5
```

`NumericBox<String>` là lỗi biên dịch vì `String` không thỏa `Number`. Java không tự parse một chuỗi như `"6"` thành số để thỏa generic bound.

Nếu class có nhiều type parameters, chỉ ràng buộc những biến cần thiết:

```java
class Measurement<ID, N extends Number> {
    private final ID id;
    private final N value;

    Measurement(ID id, N value) {
        this.id = id;
        this.value = value;
    }

    ID id() { return id; }
    N value() { return value; }
}
```

Ở đây `ID` không có bound tường minh; `N` bị giới hạn bởi `Number`. Có thể dùng `Measurement<String,Integer>` hay `Measurement<Long,Double>`. Bound của `N` không áp dụng cho `ID`.

## 9. Generic interface và record với bound

Một generic interface có thể ràng buộc type parameter của chính nó:

```java
interface NumericSource<T extends Number> {
    T read();
}
```

Mọi class triển khai `NumericSource<T>` phải truyền type argument phù hợp bound. Nếu class con còn generic, cần chuyển tiếp điều kiện đó.

Một record cũng hỗ trợ bound:

```java
record Range<T extends Comparable<? super T>>(T from, T to) {
    Range {
        if (from.compareTo(to) > 0) {
            throw new IllegalArgumentException("from > to");
        }
    }
}
```

Record components `from` và `to` cùng kiểu `T`. Bound `Comparable<? super T>` cho phép compiler gọi `compareTo`; compact constructor bảo vệ **bất biến nghiệp vụ** “from không lớn hơn to”. Generic type bound tự nó không bảo đảm thứ tự hai giá trị — chính câu lệnh kiểm tra trong constructor mới làm việc đó.

Tạo `BoundedRecordDemo.java`:

```java
public class BoundedRecordDemo {
    record Range<T extends Comparable<? super T>>(T from, T to) {
        Range {
            if (from.compareTo(to) > 0) {
                throw new IllegalArgumentException("from > to");
            }
        }
    }

    public static void main(String[] args) {
        Range<Integer> pageRange = new Range<>(1, 5);
        Range<String> nameRange = new Range<>("A", "Z");

        System.out.println(pageRange.from() + ".." + pageRange.to());
        System.out.println(nameRange.from() + ".." + nameRange.to());
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
1..5
A..Z
```

Nếu `from` hoặc `to` null, ví dụ sẽ ném `NullPointerException` khi gọi `compareTo`. Bạn có thể bổ sung `Objects.requireNonNull` nếu muốn lỗi và thông báo rõ hơn; generic bound không tự cấm null.

## 10. Bound và kế thừa: class con phải chuyển tiếp đúng ràng buộc

Giả sử superclass yêu cầu `T extends Number`:

```java
class NumericStore<T extends Number> {
    final T value;

    NumericStore(T value) {
        this.value = value;
    }
}
```

Một subclass vẫn generic phải cung cấp type argument luôn thỏa bound của superclass:

```java
class AuditedStore<N extends Number> extends NumericStore<N> {
    AuditedStore(N value) {
        super(value);
    }
}
```

Đây là khai báo hợp lệ; `AuditedStore<Integer>` trở thành một `NumericStore<Integer>`, còn `AuditedStore<Double>` thành `NumericStore<Double>`. `N` thuộc subclass và được **chuyển tiếp** làm type argument của cha.

Ngược lại:

```java
// Không biên dịch:
// class BadStore<U> extends NumericStore<U> {
//     BadStore(U value) { super(value); }
// }
```

Vì `U` chưa có bound, compiler không thể chứng minh `U` luôn tương thích với `Number`. Bạn có thể sửa bằng `U extends Number` hoặc cố định subclass thành `extends NumericStore<Integer>` khi ý nghĩa nghiệp vụ chỉ cần `Integer`.

### Ví dụ chạy hoàn chỉnh

Tạo `BoundedInheritanceDemo.java`:

```java
public class BoundedInheritanceDemo {
    static class NumericStore<T extends Number> {
        private final T value;

        NumericStore(T value) {
            this.value = value;
        }

        double doubleValue() {
            return value.doubleValue();
        }
    }

    static final class AuditedStore<N extends Number>
            extends NumericStore<N> {
        private final String label;

        AuditedStore(String label, N value) {
            super(value);
            this.label = label;
        }

        void print() {
            System.out.println(label + "=" + doubleValue());
        }
    }

    public static void main(String[] args) {
        AuditedStore<Integer> items = new AuditedStore<>("items", 3);
        AuditedStore<Double> weight = new AuditedStore<>("kg", 2.5);

        items.print();
        weight.print();
    }
}
```

Chạy:

```bash
javac BoundedInheritanceDemo.java
java BoundedInheritanceDemo
```

`stdout`:

```text
items=3.0
kg=2.5
```

Bound không cần lặp lại bằng **đúng cùng chữ tên** như ở cha: `NumericStore<T>` và `AuditedStore<N>` khai báo hai type parameters thuộc hai class khác nhau. Mệnh đề `extends NumericStore<N>` cùng bound của `N` bảo đảm ánh xạ hợp lệ.

## 11. Bound không tạo quan hệ subtype giữa các cách tham số hóa

Ngay cả khi `Integer extends Number`, thì:

```java
NumericBox<Integer> integers = new NumericBox<>(5);
// NumericBox<Number> numbers = integers; // Không hợp lệ.
```

Dòng minh họa trên giả sử `NumericBox<T extends Number>` đã được khai báo.

Generic types thường **invariant** theo type arguments. Bound `T extends Number` chỉ giới hạn **những kiểu nào có thể dùng làm T**, chứ không biến `NumericBox<Integer>` thành subtype của `NumericBox<Number>`.

Muốn một method đọc giá trị từ nhiều loại `NumericBox` khác nhau, có thể nhận wildcard:

```java
static void printValue(NumericBox<? extends Number> box) {
    Number n = box.get();
    System.out.println(n);
}
```

`? extends Number` ở đây là **upper-bounded wildcard** dùng làm type argument, khác với `<T extends Number>` là **khai báo named type parameter**. Hai cú pháp đều diễn đạt giới hạn phía trên nhưng đảm nhiệm hai vai trò khác nhau.

### Khi nào dùng type parameter, khi nào dùng wildcard?

- Dùng `<T extends Number>` nếu cần **đặt tên kiểu T và liên kết T giữa nhiều vị trí**, ví dụ `T min(T a,T b)`.
- Dùng `List<? extends Number>` khi một tham số chủ yếu **cung cấp** các giá trị đọc như `Number` mà không cần đưa kiểu cụ thể của phần tử vào kết quả.
- Dùng `List<? super Integer>` khi cần **thêm Integer** vào một danh sách có thể chứa Integer hoặc supertype của Integer; đây là lower-bounded wildcard, không phải một multiple bound của type parameter.
- Tránh dùng raw type chỉ để lách giới hạn compile time.

Đặc biệt, Java **không khai báo lower-bound cho named type parameter** dưới dạng `<T super Number>`. Cú pháp đó không hợp lệ. `super` xuất hiện ở **wildcard type argument**, ví dụ `List<? super Number>`.

## 12. Recursive bounds và Comparable

Một số interface generic mô tả kiểu đối tượng có thể thao tác với chính kiểu đó. Java cho phép type parameter xuất hiện trong bound của nó:

```java
<T extends Comparable<T>>
```

Đây là **recursive bound**, không phải vòng lặp runtime. Nó nói rằng kiểu `T` phải phù hợp với `Comparable<T>`. Khi viết generic method cần tính thứ tự, `Comparable` là lựa chọn quen thuộc.

### Tại sao `Comparable<? super T>` linh hoạt hơn?

Giả sử class `Animal` implements `Comparable<Animal>`; một subtype `Dog extends Animal` kế thừa khả năng so sánh với `Animal`. `Dog` không nhất thiết implements riêng `Comparable<Dog>`. Vì vậy:

```java
<T extends Comparable<? super T>>
```

có thể chấp nhận `Dog` thông qua `Comparable<Animal>` — loại so sánh cho một **supertype** của `Dog`. Đây là lý do nhiều chữ ký trong JDK dùng mẫu `Comparable<? super T>` thay vì `Comparable<T>`.

### Ví dụ chứng minh

Tạo `ComparableBoundDemo.java`:

```java
public class ComparableBoundDemo {
    static class Animal implements Comparable<Animal> {
        private final int weight;

        Animal(int weight) {
            this.weight = weight;
        }

        @Override
        public int compareTo(Animal other) {
            return Integer.compare(weight, other.weight);
        }

        @Override
        public String toString() {
            return "Animal(" + weight + ")";
        }
    }

    static final class Dog extends Animal {
        Dog(int weight) {
            super(weight);
        }
    }

    static <T extends Comparable<? super T>> T greater(T a, T b) {
        return a.compareTo(b) >= 0 ? a : b;
    }

    public static void main(String[] args) {
        Dog light = new Dog(5);
        Dog heavy = new Dog(9);

        Dog winner = greater(light, heavy);
        System.out.println(winner);
    }
}
```

Chạy:

```bash
javac ComparableBoundDemo.java
java ComparableBoundDemo
```

`stdout`:

```text
Animal(9)
```

`Dog` thỏa `Comparable<? super Dog>` vì nó kế thừa `Comparable<Animal>`, và `Animal` là supertype của `Dog`. Kết quả vẫn có thể được sử dụng dưới kiểu tĩnh `Dog` khi inference chọn `T=Dog`.

Không suy luận rằng mọi subtype của một kiểu implements Comparable đều có total order phù hợp nghiệp vụ. Nếu định nghĩa `compareTo` mâu thuẫn với `equals` hoặc không bắc cầu, thuật toán sắp xếp và tập có thứ tự có thể hoạt động khó đoán. Bound chỉ chứng minh **khả năng gọi method**, không chứng minh tính đúng đắn của thuật toán so sánh.

## 13. Multiple bounds và các thành viên trùng chữ ký

Nếu các bound có các method trùng tên, compiler phải xác định có một hợp đồng method được kế thừa **tương thích**. Multiple bounds không phải cơ chế hợp nhất các API tùy ý có return types xung đột.

Ví dụ hai interface có method cùng chữ ký tương thích:

```java
interface Named {
    String name();
}

interface Labeled {
    String name();
}

static <T extends Named & Labeled> String display(T value) {
    return value.name();
}
```

Đây là trích khai báo minh họa: hai interface khai báo cùng method `String name()` nên một implementation có thể đáp ứng cả hai bằng một method.

Nếu hai interface định nghĩa các method có cùng parameter signature nhưng kiểu trả về không tương thích, không thể giả định intersection bound hợp lệ. Java áp dụng các quy tắc xung đột phương thức kế thừa và kiểu trả về.

Một lưu ý nữa: chỉ một class bound tường minh và cận class phải ở đầu. Cú pháp kiểu `<T extends ClassA & ClassB>` với hai class bounds không hợp lệ, kể cả khi ClassB có quan hệ kế thừa với ClassA. Nếu cần hợp đồng kết hợp, thường đặt một class ở đầu và interface theo sau.

## 14. Type erasure: bound ảnh hưởng đến kiểu sau xóa

Java Generics dùng **type erasure** khi biên dịch. Kiểm tra bound thực hiện ở compile time; type variable không giữ nguyên làm một kiểu runtime chuyên biệt cho mỗi type argument.

Các quy tắc cần nhớ:

1. Type parameter không có bound tường minh thường bị xóa thành `Object`.
2. `<T extends Number>` bị xóa thành `Number`.
3. `<T extends Runnable & AutoCloseable>` bị xóa thành **cận đầu tiên là `Runnable`**; compiler vẫn bảo đảm các bound khác trong mã nguồn và có thể chèn cast cần thiết.
4. Generic type `List<Integer>` và `List<String>` không biến thành hai runtime `ArrayList` classes khác nhau chỉ bởi type arguments.
5. Compiler có thể tạo bridge methods hoặc casts để duy trì hợp đồng generic và đa hình sau erasure.

### Quan sát bound bằng reflection

Tạo `ErasureBoundDemo.java`:

```java
import java.lang.reflect.Method;
import java.lang.reflect.TypeVariable;

public class ErasureBoundDemo {
    static <T extends Number> double doubleValue(T number) {
        return number.doubleValue();
    }

    public static void main(String[] args) throws Exception {
        Method method = ErasureBoundDemo.class
                .getDeclaredMethod("doubleValue", Number.class);
        TypeVariable<?> type = method.getTypeParameters()[0];

        System.out.println(type.getName());
        System.out.println(type.getBounds()[0].getTypeName());
        System.out.println(method.getParameterTypes()[0].getTypeName());
    }
}
```

Biên dịch/chạy:

```bash
javac ErasureBoundDemo.java
java ErasureBoundDemo
```

`stdout`:

```text
T
java.lang.Number
java.lang.Number
```

`getTypeParameters()` đọc metadata type parameter `T` của **khai báo method**. `getBounds()` cho biết upper bound `Number`. Tham số runtime của method đã được erasure về `Number`, nên reflection tìm method bằng `Number.class`, không phải bằng một `T.class` không tồn tại.

Điều này **không** có nghĩa reflection có thể khôi phục chính xác type argument được suy luận tại từng invocation của method. Metadata khai báo generic khác với việc mỗi lời gọi tạo một method runtime chuyên biệt.

### Không overload chỉ dựa trên bound/type arguments đã xóa

Ví dụ:

```java
// Không được đặt cả hai trong một class:
// void add(java.util.List<String> list) {}
// void add(java.util.List<Integer> list) {}
```

Hai tham số đều erasure thành `List`, gây name clash. Tương tự, hai generic methods có parameter signatures trùng sau erasure không thể phân biệt chỉ bằng tên type variables `T` và `U`.

## 15. Class token và factory: bound không cho phép `new T()`

Dù `T extends Number` hoặc `T extends Runnable`, Java vẫn không cho phép viết:

```java
// Không hợp lệ trong generic method/class:
// T value = new T();
// Class<T> runtimeType = T.class;
```

Bound bảo đảm **quan hệ kiểu và tập phương thức**, không bảo đảm có một constructor được gọi trực tiếp qua `T`, cũng không tạo class token runtime mang tên `T`.

Nếu cần tạo giá trị của kiểu `T`, nên **nhận một factory hoặc Supplier** từ caller:

Tạo `BoundedFactoryDemo.java`:

```java
import java.util.function.Supplier;

public class BoundedFactoryDemo {
    static final class Task implements Runnable {
        @Override
        public void run() {
            System.out.println("task");
        }
    }

    static <T extends Runnable> T createAndRun(
            Supplier<? extends T> factory) {
        T task = factory.get();
        task.run();
        return task;
    }

    public static void main(String[] args) {
        Task task = createAndRun(Task::new);
        System.out.println(task.getClass().getSimpleName());
    }
}
```

Chạy:

```bash
javac BoundedFactoryDemo.java
java BoundedFactoryDemo
```

`stdout`:

```text
task
Task
```

`Supplier<? extends T>` tạo ra một giá trị có kiểu tương thích `T`, và bound `T extends Runnable` cho phép gọi `run()`. Đây là cách kết hợp factory với generic bound mà không vi phạm `new T()`.

Nếu factory trả `null`, `task.run()` sẽ ném `NullPointerException`; nếu cần thông báo rõ hơn, bổ sung `Objects.requireNonNull(factory.get())`. Generic bound không bảo đảm non-null.

## 16. Bounds có thể là kiểu generic được tham số hóa

Cận trên có thể là một generic interface đã áp dụng type argument:

```java
<T extends Comparable<T>>
<T extends Comparable<? super T>>
<T extends java.lang.Iterable<String>>
```

Ví dụ `<T extends Iterable<String>>` cho compiler biết mỗi `T` có thể được duyệt và từng phần tử trả ra thuộc kiểu `String` ở mức mã nguồn. Một implementation của `Iterable<String>`, chẳng hạn `List<String>`, phù hợp.

Tạo `ParameterizedInterfaceBoundDemo.java`:

```java
import java.util.List;

public class ParameterizedInterfaceBoundDemo {
    static <T extends Iterable<String>> int totalLength(T values) {
        int total = 0;
        for (String value : values) {
            total += value.length();
        }
        return total;
    }

    public static void main(String[] args) {
        System.out.println(totalLength(List.of("Java", "Core")));
    }
}
```

Chạy:

```bash
javac ParameterizedInterfaceBoundDemo.java
java ParameterizedInterfaceBoundDemo
```

`stdout`:

```text
8
```

`List<String>` implements `Iterable<String>` và thỏa bound. `List<Integer>` không thỏa `Iterable<String>`. Đây là giới hạn về **cách interface generic được tham số hóa**, không chỉ về tên raw interface.

Hãy phân biệt:

- `T extends Iterable<String>`: named type parameter `T` phải triển khai một interface có element type `String`.
- `Iterable<? extends CharSequence>`: wildcard cho phép producer có kiểu phần tử là một subtype của `CharSequence`.
- `Iterable<T>`: khi `T` đã được khai báo ở một nơi khác, đây là **type argument usage**, không tự tạo một biến kiểu mới.

## 17. Quy tắc thực tế để chọn cận phù hợp

| Nhu cầu thuật toán | Bound/cách khai báo phù hợp | Lý do |
| --- | --- | --- |
| Chỉ nhận đối tượng số và gọi `doubleValue` | `<T extends Number>` hoặc trực tiếp `Number` | Compiler biết phương thức của Number |
| Nhận task và gọi `run` | `<T extends Runnable>` hoặc `Runnable` | Yêu cầu hành vi của interface |
| Vừa `run` vừa `close` | `<T extends Runnable & AutoCloseable>` | Phải có đồng thời hai khả năng |
| Giữ đúng kiểu số trả về | `<T extends Number> T choose(T a,T b)` | Return gắn cùng named T |
| So sánh tự nhiên nhiều kiểu | `<T extends Comparable<? super T>>` | So sánh T với T qua Comparable |
| Vừa Number vừa Comparable | `<T extends Number & Comparable<? super T>>` | Hợp đồng gồm numeric conversion và ordering |
| Đọc danh sách nhiều loại số | `List<? extends Number>` | Wildcard đủ nếu không cần đặt tên kiểu phần tử |
| Cho phép thêm Integer vào danh sách đích | `List<? super Integer>` | Lower-bounded wildcard ở vị trí consumer |
| Chỉ tính trung bình hai Number | `double average(Number a,Number b)` | Không nhất thiết phải generic |
| Trả kết quả mapping có kiểu khác nguồn | `<T,R> R convert(T input,Function<? super T,? extends R> f)` | T nối đầu vào, R nối kiểu kết quả |

Không nên thêm bound chỉ vì “mọi generic method trông chuyên nghiệp hơn khi có extends”. Mỗi cận làm **thu hẹp tập type arguments có thể dùng**, vì vậy nên có lý do liên quan đến thao tác hay quan hệ kiểu thật sự.

### Cận quá rộng và quá hẹp

- Quá rộng: dùng `<T>` nhưng thân cần `doubleValue()` → compiler không cho gọi.
- Vừa đủ: dùng `<T extends Number>` khi thật sự cần `doubleValue()`.
- Quá hẹp: dùng `<T extends Integer>` khi chỉ cần `Number` → về cú pháp là **hợp lệ**, nhưng `Integer` là final nên bound này không cho thêm subtype ngoài `Integer`; thường dùng trực tiếp `Integer` rõ hơn.
- Quá phức tạp: dùng multiple bounds khi thuật toán chỉ sử dụng phương thức từ một cận → API khó tái sử dụng hơn cần thiết.

Dù một final class vẫn có thể xuất hiện làm bound, nó thường không mang lợi ích tái sử dụng kiểu; “final class” **không làm cú pháp `<T extends FinalClass>` tự động sai**.

## 18. Lỗi thường gặp khi giới hạn type parameter

### 18.1. Dùng `implements` thay `extends` ở type parameter

Sai:

```java
// <T implements Runnable> // Không hợp lệ.
```

Đúng:

```java
<T extends Runnable>
```

`implements` dùng trong khai báo class implement interface; type parameter dùng `extends` bất kể upper bound là class hay interface.

### 18.2. Đặt class bound sau interface

Sai:

```java
// <T extends Runnable & Number> // Sai thứ tự class/interface.
```

Đúng nếu bạn thực sự cần hai cận và có type phù hợp:

```java
<T extends Number & Runnable>
```

`Number` là class bound nên đứng đầu. Ví dụ cú pháp không khẳng định các wrapper số chuẩn của JDK đều implement `Runnable`; một class Number tùy biến có thể triển khai Runnable để thỏa yêu cầu.

### 18.3. Lặp từ khóa extends cho mỗi bound

Sai:

```java
// <T extends Number extends Comparable<T>>
```

Đúng:

```java
<T extends Number & Comparable<T>>
```

Giữa các bounds dùng `&`, không lặp `extends`.

### 18.4. Giả định Number hỗ trợ compareTo

`Number` không có `compareTo`. Nếu thuật toán gọi method đó, cần `Comparable` bound hoặc nhận `Comparator` phù hợp. Đừng cast toàn bộ Number sang `Comparable` để né compiler; không phải mọi Number subtype đều có natural ordering.

### 18.5. Nhầm bound với wildcard

`<T extends Number>` **khai báo T**. `List<? extends Number>` **dùng wildcard**. Hai cú pháp ở hai vị trí khác nhau, không hoán đổi tùy ý. Không có dạng `<T super Number>` hợp lệ.

### 18.6. Không chuyển tiếp bound khi extends generic class

Nếu cha đòi `T extends Number` thì con `Child<U> extends Parent<U>` phải bảo đảm `U` thỏa cận tương ứng. Thêm `U extends Number` hoặc dùng type argument cụ thể phù hợp.

### 18.7. Cho rằng bound khiến mọi T đều có thể khởi tạo

`new T()` không hợp lệ chỉ dựa trên bound; cần factory, supplier hoặc thông tin kiểu runtime. Bound không bảo đảm no-arg constructor.

### 18.8. Nghĩ T không thể nhận null vì đã có bound

`T extends Number` vẫn là reference type; null có thể thỏa kiểm tra kiểu tĩnh. Một lời gọi method trên giá trị null ném `NullPointerException`. Nếu cần non-null, kiểm tra runtime hoặc dùng chính sách API riêng.

### 18.9. Trộn loại Number không tương thích khi so sánh

`Integer` và `Double` cùng extends `Number`, nhưng không có một `Comparable<Number>` chung được mỗi wrapper hiện thực. Một bound `<T extends Number & Comparable<T>>` không tự biến việc so sánh hai wrapper khác loại thành hợp lệ. Chọn kiểu so sánh hoặc comparator rõ ràng.

### 18.10. Che lỗi bằng raw type/unchecked cast

Bound giúp compiler phát hiện vi phạm hợp đồng; raw types và unchecked casts có thể đánh mất những bảo đảm đó. Nếu bắt gặp warning, lần theo API rồi sửa generic declaration/type argument thay vì tắt warning vô điều kiện.

## 19. Bài tập có đáp án

### Bài 1 — Class bound

Cho:

```java
static <T extends Number> double triple(T value) {
    return value.doubleValue() * 3;
}
```

`triple(4)`, `triple(1.5)` có hợp lệ không? `triple("4")` thì sao?

**Đáp án:** Hai lời gọi số hợp lệ và trả `12.0`, `4.5`. `String` không kế thừa `Number` nên lời gọi chuỗi bị lỗi biên dịch. Đây là method trích để học, chưa có `main`.

### Bài 2 — Interface bound

Cho:

```java
static <T extends CharSequence> int length(T value) {
    return value.length();
}
```

Vì sao `String` và `StringBuilder` hợp lệ? Vì cả hai implement `CharSequence`. Một `Integer` không có quan hệ interface đó nên không hợp lệ.

### Bài 3 — Class và interface đồng thời

```java
static <T extends Number & Comparable<T>> T smaller(T a, T b) {
    return a.compareTo(b) <= 0 ? a : b;
}
```

Giải thích: `Number` đảm bảo tập numeric methods, `Comparable<T>` bảo đảm `compareTo`, `T` giữ kiểu trả về. `smaller(3,7)` trả giá trị `3`; `smaller(2.0,1.5)` trả `1.5`.

### Bài 4 — Hai interface

Viết một class `Task` implements `Runnable` và `AutoCloseable`. Truyền object vào `<T extends Runnable & AutoCloseable> void executeAndClose(T)`. Nếu class chỉ implements `Runnable`, điều gì xảy ra? Compiler không chấp nhận vì thiếu `AutoCloseable`. Nếu có cả hai, try-with-resources gọi `close` sau `run`.

### Bài 5 — Bound trong class

Cho `class Holder<T extends Number>`. Tại sao `Holder<Integer>` hợp lệ còn `Holder<String>` không? Vì type argument phải thỏa upper bound `Number`. Đưa một giá trị chuỗi có chữ số vào constructor không thể sửa lỗi kiểu bằng boxing hay tự động parse.

### Bài 6 — Kế thừa bound

```java
class Parent<T extends Number> {}
class Good<U extends Number> extends Parent<U> {}
// class Bad<U> extends Parent<U> {}
```

Vì sao Good hợp lệ còn Bad không? Good bảo đảm mọi `U` của nó là subtype Number; Bad không thể đưa ra bảo đảm ấy cho superclass.

### Bài 7 — Named bound hay wildcard?

Một method chỉ cộng tất cả phần tử trong danh sách số. Bạn sẽ nhận `List<Number>` hay `List<? extends Number>`? Nên dùng wildcard upper bound nếu muốn chấp nhận cả `List<Integer>`, `List<Double>`... để **đọc** như Number. Không cần tự khai báo `<T extends Number>` nếu return type không nối với một T cụ thể và wildcard đã đủ.

### Bài 8 — Recursive bound

Một class `Base` implements `Comparable<Base>`; class `Derived extends Base`. Vì sao `<T extends Comparable<? super T>>` có thể phù hợp với Derived? Vì Derived có thể so sánh với Base là supertype của Derived thông qua interface kế thừa.

### Bài 9 — Thứ tự cận

Phân loại:

```java
<T extends Number & Comparable<T>>     // Hợp lệ.
<T extends Runnable & AutoCloseable>   // Hợp lệ.
<T extends Runnable & Number>          // Không hợp lệ: class đứng sau.
<T extends Number & Runnable>          // Hợp lệ về thứ tự.
```

Một khai báo hợp lệ về **cú pháp bounds** chưa chắc mọi type có sẵn đều thỏa; phải xét class/interface cụ thể làm type argument.

### Bài 10 — Sửa API quá generic

Bạn thấy:

```java
static <T extends Number> double plus(T a, T b) {
    return a.doubleValue() + b.doubleValue();
}
```

Nếu API chỉ cần cộng hai Number và trả double, viết `static double plus(Number a, Number b)` có được không? Có. Chữ ký cụ thể này cũng chấp nhận hai đối số Number khác subtype, rõ ràng hơn khi không cần bảo toàn một named T giữa input và output.

## 20. Checklist khi thiết kế một giới hạn kiểu

Trước khi thêm `extends` vào type parameter, hãy kiểm tra:

1. Phương thức hoặc class cần gọi **method nào** trên T? Method đó có thuộc Object, một class, hay interface?
2. Một cận class đã đủ chưa, hay thật sự cần thêm interface bounds?
3. Nếu có nhiều bounds, **class đứng đầu**, sau đó mới tới các interfaces?
4. Bound có yêu cầu **tất cả** contract đúng như nghiệp vụ, hay đang vô tình hạn chế các type arguments hữu ích?
5. Type parameter có xuất hiện ở **nhiều vị trí cần liên kết kiểu** không? Nếu không, dùng một kiểu cụ thể hoặc wildcard có thể đơn giản hơn.
6. Nếu method nằm trong generic class, đang dùng T của class hay vô tình khai báo một T mới ở method?
7. Nếu subclass chuyển tiếp type parameter cho superclass, các bounds của cha đã được đáp ứng chưa?
8. Caller có định truyền các generic containers với type arguments khác nhau? Nhớ quy tắc invariance, cân nhắc wildcard cho producer/consumer.
9. Có đang nhầm compile-time bound với runtime validation, non-null, factory hay constructor không?
10. Khi có raw type/unchecked warning, đã sửa quan hệ kiểu thay vì ép cast thiếu cơ sở chưa?

## 21. Tổng kết

**Giới hạn bằng lớp hoặc giao diện** là cách Java compiler biết một biến kiểu generic có những khả năng nào. `<T extends Number>` cấp quyền sử dụng các thao tác do `Number` bảo đảm; `<T extends Runnable>` yêu cầu hợp đồng interface `Runnable`; `<T extends Number & Comparable<T>>` kết hợp class bound với interface bound; `<T extends Runnable & AutoCloseable>` yêu cầu đồng thời nhiều interfaces.

Bound có hiệu lực ở **khai báo generic**, được kiểm tra khi áp dụng type arguments và tham gia inference. Nó không cho tạo `new T()`, không tự cấm null, không làm generic type trở thành covariant, và không chứng minh thuật toán nghiệp vụ đúng. Hãy chọn cận **nhỏ nhất đủ bảo đảm những thao tác thật sự cần**, giữ type parameter có tên khi phải liên kết nhiều vị trí, và dùng wildcard hoặc kiểu cụ thể khi điều đó đơn giản hơn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="generic-type-bounds-003">Nhiều giới hạn với &lt;T extends A &amp; B&gt;</a>

<details>
<summary>Xem chi tiết</summary>

# Nhiều giới hạn với `<T extends A & B>` trong Java Generics

Một tham số kiểu generic thường được khai báo đơn giản là `<T>`. Nhưng trong nhiều API, việc “`T` có thể là bất kỳ kiểu tham chiếu nào” là quá rộng: thuật toán có thể cần một đối tượng vừa kế thừa một lớp nền, vừa triển khai interface cung cấp tên và interface hỗ trợ kiểm toán. **Multiple bounds** (nhiều giới hạn kiểu) giải quyết trường hợp này bằng cú pháp `<T extends A & B>`: đối số kiểu hợp lệ phải thỏa mãn **đồng thời tất cả giới hạn**.

Ví dụ `<T extends Identified & Named & Auditable>` cho phép mã dùng các thành viên được hợp đồng của `Identified`, `Named` và `Auditable` công bố, mà không ép kiểu thủ công. Đây là một dạng **intersection type** ở mức tham số kiểu: `T` phải thuộc giao của các tập kiểu mà giới hạn mô tả.

Bài học tập trung vào cú pháp, điều kiện hợp lệ của giới hạn, thứ tự giới hạn lớp/interface, cách compiler kiểm tra và suy luận kiểu, thiết kế lớp/phương thức dùng nhiều bounds, những lỗi dễ gặp và ảnh hưởng của *type erasure*.

## 1. Từ một giới hạn đến nhiều giới hạn

Khai báo không có giới hạn tường minh:

```java
class Box<T> {
    private final T value;

    Box(T value) {
        this.value = value;
    }
}
```

Ở đây `T` không có yêu cầu đặc biệt nào ngoài những điều kiện của Java Generics. Bạn không thể gọi `value.name()` hoặc `value.audit()` từ khai báo này, vì compiler không biết mọi kiểu `T` đều có các phương thức đó.

Một giới hạn:

```java
interface Named {
    String name();
}

class NamedBox<T extends Named> {
    private final T value;

    NamedBox(T value) {
        this.value = value;
    }

    String name() {
        return value.name();
    }
}
```

`T extends Named` yêu cầu đối số kiểu triển khai `Named`. Dù `Named` là **interface**, cú pháp giới hạn tham số kiểu **vẫn dùng từ khóa `extends`**, không dùng `implements`.

Nếu thuật toán cần thêm khả năng kiểm toán:

```java
interface Auditable {
    void audit();
}

class NamedAuditedBox<T extends Named & Auditable> {
    private final T value;

    NamedAuditedBox(T value) {
        this.value = value;
    }

    void showAndAudit() {
        System.out.println(value.name());
        value.audit();
    }
}
```

Ở đây `T` phải đồng thời thỏa `Named` **và** `Auditable`. Lời gọi `name()` và `audit()` hợp lệ vì compiler biết cả hai hợp đồng áp dụng cho `T`.

Ba nhóm khai báo ở phần này là **đoạn mã minh họa** có thể đặt vào một tệp Java với quy tắc tên lớp thích hợp; các mục tiếp theo cung cấp chương trình `main` độc lập để chạy.

## 2. Cú pháp chuẩn của nhiều giới hạn

Mẫu tổng quát của một tham số kiểu:

```java
<T extends FirstBound & SecondBound & ThirdBound>
```

Đây là **mẫu cú pháp**, không phải câu lệnh Java độc lập.

Ý nghĩa:

- `T` là tên **tham số kiểu** được khai báo.
- `extends` giới thiệu danh sách giới hạn trên (*upper bounds*).
- `&` nối các giới hạn. Ở đây `&` **không phải phép AND bitwise/boolean đang được tính toán**, mà là dấu phân cách trong một *intersection bound* của kiểu.
- Mọi giới hạn đều phải đúng **cùng lúc**, không phải “thỏa A **hoặc** B”.
- Danh sách giới hạn áp dụng cho **một** tham số kiểu `T`; nó khác việc khai báo nhiều tham số độc lập `<T, U>`.

Các hình thức hợp lệ:

```java
<T extends Named & Auditable>
<T extends Identified & Named>
<T extends Identified & Named & Auditable>
<T extends Number & Comparable<T>>
```

Chúng là **mẫu khai báo**, cần được đặt trong ngữ cảnh khai báo lớp, interface, constructor hoặc phương thức generic phù hợp.

Một số quy tắc bắt buộc:

1. **Tối đa một lớp cụ thể hoặc abstract làm class bound**. Nếu có class bound cùng các interface, **class bound phải đứng đầu tiên**.
2. Những giới hạn còn lại có thể là các interface hợp lệ; Java không cho một lớp kế thừa đồng thời hai lớp không liên quan như một cách “đa kế thừa lớp”.
3. Các giới hạn có thể chứa đối số generic, chẳng hạn `Comparable<T>`.
4. Cần tránh lặp cùng interface theo những kiểu tham số hóa xung đột, vì hệ phân cấp Java không cho một kiểu cùng kế thừa hai cách tham số hóa khác nhau của cùng generic interface.
5. Compiler chỉ chấp nhận tập giới hạn có thể tồn tại cùng nhau theo các quy tắc kế thừa/chữ ký của Java.

**Đặc biệt:** nếu chỉ dùng interface, interface có thể đứng đầu tiên mà không cần thêm `Object`. Ví dụ `<T extends Named & Auditable>` hoàn toàn hợp lệ.

## 3. Một class bound và các interface bổ sung

Với `<T extends BaseEntity & Named & Auditable>`, `BaseEntity` là **class bound** nên phải đứng đầu; `Named` và `Auditable` là các **interface bounds**. Compiler cho phép gọi những member truy cập được do cả ba hợp đồng cung cấp:

```java
static <T extends BaseEntity & Named & Auditable>
void process(T entity) {
    System.out.println(entity.id());
    System.out.println(entity.name());
    entity.audit();
}
```

Đoạn này là **method minh họa**: `BaseEntity`, `Named`, `Auditable` phải được khai báo với các method tương ứng và method phải nằm trong class hợp lệ. Nếu chỉ cần các khả năng `name()` và `audit()`, có thể viết `<T extends Named & Auditable>` mà không bắt buộc một class bound.

Bài học ở đây là **kết hợp nhiều hợp đồng để giải quyết một thuật toán**. Các luật cấu trúc nâng cao—thứ tự class, bound final, methods xung đột, trùng generic interface sau erasure—phải được xem xét trước khi công bố public API, không suy ra rằng mọi danh sách bounds đều hợp lệ.

## 4. Ví dụ Java 17 hoàn chỉnh: ràng buộc lớp và hai interface

Lưu toàn bộ chương trình thành **`MultipleBoundsDemo.java`**:

```java
public class MultipleBoundsDemo {
    static abstract class Identified {
        private final String id;

        Identified(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }
    }

    interface Named {
        String name();
    }

    interface Auditable {
        void audit();
    }

    static final class Customer extends Identified
            implements Named, Auditable {
        private final String name;

        Customer(String id, String name) {
            super(id);
            this.name = name;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public void audit() {
            System.out.println("audit:" + id());
        }
    }

    static <T extends Identified & Named & Auditable>
    String process(T value) {
        value.audit();
        return value.id() + "|" + value.name();
    }

    public static void main(String[] args) {
        Customer customer = new Customer("C-17", "An");
        System.out.println(process(customer));
    }
}
```

Biên dịch và chạy:

```bash
javac -encoding UTF-8 MultipleBoundsDemo.java
java MultipleBoundsDemo
```

Kết quả chính xác (`stdout`):

```text
audit:C-17
C-17|An
```

Đọc ví dụ theo thứ tự:

- `Identified` là lớp nền có phương thức `id()`.
- `Named` và `Auditable` là các interface.
- `Customer extends Identified implements Named, Auditable` **thỏa cả ba điều kiện**.
- `<T extends Identified & Named & Auditable>` ràng buộc một tham số kiểu của **phương thức `process`**, không yêu cầu lớp `MultipleBoundsDemo` trở thành generic.
- Từ một biến `T value`, compiler cho phép gọi `value.id()`, `value.name()` và `value.audit()`.
- Khi gọi `process(customer)`, đối số `Customer` là một ứng viên hợp lệ cho `T`.

Đây là lợi ích chính của multiple bounds: **tránh dùng `Object` rồi tự ép kiểu thành từng interface** mỗi lần cần một hành vi.

## 5. Điều kiện “thỏa đồng thời” khác với một hợp đồng rộng

Giả sử có lớp chỉ triển khai `Named`:

```java
// Đoạn minh họa trong cùng phạm vi có interface Named:
final class ShortLabel implements Named {
    @Override
    public String name() {
        return "Short";
    }
}
```

`ShortLabel` không tự động thỏa `Auditable`. Nếu gọi phương thức:

```java
// process(new ShortLabel()); // Lỗi biên dịch: không thỏa tất cả giới hạn
```

thì compiler từ chối, ngay cả khi đối tượng có phương thức `name()` hoàn toàn hợp lệ. Đối tượng còn phải kế thừa `Identified` và triển khai `Auditable` nếu dùng bản `process` ở ví dụ trước.

Tương tự, nếu một lớp kế thừa `Identified` và triển khai `Auditable` nhưng không triển khai `Named`, nó vẫn không phù hợp.

Multiple bounds không kiểm tra tên phương thức theo kiểu *duck typing*: **thực sự phải có quan hệ kiểu Java** với các giới hạn đã khai báo. Một lớp vô tình có phương thức `name()` nhưng **không `implements Named`** vẫn không thỏa bound `Named`.

Mặt khác, nếu class bound là một lớp abstract, đối số kiểu `T` có thể là lớp con concrete phù hợp. Không cần `T` trùng chính xác với tên lớp trong phần giới hạn.

## 6. Nhiều interface mà không cần class bound

Một trường hợp phổ biến là cần hai năng lực được biểu diễn bằng hai interface:

```java
interface Named {
    String name();
}

interface Coded {
    String code();
}

static <T extends Named & Coded> String format(T item) {
    return item.name() + "#" + item.code();
}
```

Đoạn trên gồm khai báo interface và **một phương thức minh họa đặt trong một lớp**. `T` có thể là bất kỳ lớp hoặc kiểu đáp ứng đồng thời hai interface, không nhất thiết kế thừa lớp nền chung do ứng dụng định nghĩa.

### Chương trình hoàn chỉnh

Tạo tệp **`InterfaceBoundsDemo.java`**:

```java
public class InterfaceBoundsDemo {
    interface Named {
        String name();
    }

    interface Coded {
        String code();
    }

    record Product(String name, String code)
            implements Named, Coded { }

    static <T extends Named & Coded> String format(T item) {
        return item.name() + "#" + item.code();
    }

    public static void main(String[] args) {
        Product product = new Product("Pen", "P01");
        System.out.println(format(product));
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 InterfaceBoundsDemo.java
java InterfaceBoundsDemo
```

`stdout`:

```text
Pen#P01
```

Java `record` tự sinh các accessor `name()` và `code()` ở mức `public`, đáp ứng chữ ký của hai interface. Lớp `Product` không cần kế thừa một class bound cụ thể. Đây là cách diễn đạt “có cả hai khả năng” một cách gọn gàng.

Với một thuật toán cần thêm khả năng mới, thường có thể bổ sung interface vào bounds thay vì ép mọi lớp nghiệp vụ vào một cây kế thừa lớp sâu.

## 7. Ràng buộc còn dùng được trong lớp generic và phương thức instance

Multiple bounds không chỉ dành cho phương thức `static`. Có thể ràng buộc tham số kiểu của cả lớp:

```java
interface Named {
    String name();
}

interface Auditable {
    void audit();
}

final class AuditedStore<T extends Named & Auditable> {
    private final T value;

    AuditedStore(T value) {
        this.value = value;
    }

    String describe() {
        value.audit();
        return value.name();
    }

    T get() {
        return value;
    }
}
```

Đây là nhóm khai báo Java minh họa. Khi viết `AuditedStore<Customer>`, kiểu `Customer` phải thỏa cả `Named` và `Auditable`. Constructor nhận `T`, getter trả `T` và phương thức instance được phép gọi cả hai hợp đồng.

Phân biệt hai vị trí giới hạn:

| Khai báo | Phạm vi ràng buộc |
|---|---|
| `class AuditedStore<T extends Named & Auditable>` | Mọi cách dùng `T` hợp lệ trong lớp `AuditedStore`, gồm trường và phương thức instance. |
| `static <T extends Named & Auditable> void audit(T v)` | Chỉ tham số kiểu `T` được khai báo riêng cho phương thức `audit`. |
| `<U extends Named & Auditable> U pick(U a, U b)` | Chỉ tham số kiểu `U` của phương thức instance `pick`. |
| `interface Combined<T extends Named & Auditable>` | Hợp đồng tham số kiểu của giao diện `Combined`. |

Một phương thức `static` trong lớp generic **không được dùng trực tiếp tham số kiểu của lớp**; nếu cần giới hạn kiểu ở static method, hãy khai báo `<U extends ...>` cho phương thức đó.

## 8. Ví dụ kết hợp: generic class và generic static method

Tạo tệp **`StoreBoundsDemo.java`**:

```java
public class StoreBoundsDemo {
    interface Named {
        String name();
    }

    interface Auditable {
        void audit();
    }

    record Entry(String name) implements Named, Auditable {
        @Override
        public void audit() {
            System.out.println("checked:" + name);
        }
    }

    static final class Store<T extends Named & Auditable> {
        private final T value;

        Store(T value) {
            this.value = value;
        }

        T get() {
            return value;
        }

        String verify() {
            value.audit();
            return value.name();
        }

        static <U extends Named & Auditable> Store<U> of(U value) {
            return new Store<>(value);
        }
    }

    public static void main(String[] args) {
        Store<Entry> store = Store.of(new Entry("invoice"));
        System.out.println(store.verify());
        System.out.println(store.get().name());
    }
}
```

Lệnh:

```bash
javac -encoding UTF-8 StoreBoundsDemo.java
java StoreBoundsDemo
```

`stdout`:

```text
checked:invoice
invoice
invoice
```

`T` của `Store<T>` và `U` của `Store.of(...)` là **hai tham số kiểu độc lập**, dù chúng cùng có giới hạn `Named & Auditable`. Factory `of` có thể suy luận `U = Entry` rồi tạo `Store<Entry>`.

Ví dụ cũng cho thấy việc đưa nhiều giới hạn vào lớp có ý nghĩa khi **hầu hết hành vi của cả lớp** đều đòi các năng lực đó; còn đặt chúng vào một phương thức có thể phù hợp hơn khi **chỉ một thuật toán** cần các năng lực kết hợp.

## 9. Giới hạn kiểu dùng interface generic

Một bound bản thân có thể là **interface generic đã được tham số hóa**. Ví dụ `Comparable<T>` diễn đạt khả năng so sánh các đối tượng:

```java
static <T extends Number & Comparable<T>>
T max(T first, T second) {
    return first.compareTo(second) >= 0 ? first : second;
}
```

Đây là **phương thức minh họa**, cần đặt trong lớp hợp lệ. `Number` là class bound và **đứng trước** `Comparable<T>` là interface bound.

Phương thức có thể nhận `Integer` hoặc `Double` vì cả hai kế thừa `Number` và triển khai `Comparable` với kiểu phù hợp. Nhưng kiểu tham số không phải `Number` bất kỳ: `Number` trừu tượng không tự triển khai `Comparable<Number>`.

### Chương trình đầy đủ

Tạo tệp **`NumericBoundsDemo.java`**:

```java
public class NumericBoundsDemo {
    static <T extends Number & Comparable<T>> T max(T a, T b) {
        return a.compareTo(b) >= 0 ? a : b;
    }

    public static void main(String[] args) {
        Integer greatestInt = max(3, 7);
        Double greatestDouble = max(2.5, 1.25);

        System.out.println(greatestInt);
        System.out.println(greatestDouble);
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 NumericBoundsDemo.java
java NumericBoundsDemo
```

`stdout`:

```text
7
2.5
```

Không được gọi `max("A", "B")` bằng chữ ký này vì `String` không phải `Number`. Đồng thời, những lời gọi với hai loại số không tương thích có thể khiến suy luận kiểu thất bại; nên biểu diễn hợp đồng rõ thay vì dùng cast hoặc raw type.

### Biến thể linh hoạt hơn với `? super T`

Trong API thực tế, bound `Comparable<? super T>` thường linh hoạt hơn:

```java
static <T extends Comparable<? super T>>
T maxComparable(T a, T b) {
    return a.compareTo(b) >= 0 ? a : b;
}
```

`? super T` cho phép một đối tượng kiểu `T` dùng hợp đồng so sánh với kiểu cha phù hợp. Đây vẫn là **một giới hạn trên tham số kiểu `T`**, có chứa wildcard trong đối số của `Comparable`. Không nhầm wildcard **bên trong** bound với cú pháp nhiều giới hạn `&`.

## 10. Suy luận kiểu và kiểu tĩnh của biểu thức

Compiler kiểm tra multiple bounds dựa trên **kiểu tĩnh**, không chỉ dựa vào kiểu runtime của đối tượng:

```java
interface Named {
    String name();
}

interface Auditable {
    void audit();
}

static <T extends Named & Auditable> void use(T value) {
    System.out.println(value.name());
    value.audit();
}
```

Giả sử `Customer` triển khai cả `Named` và `Auditable`. Khi biến được khai báo là `Customer`:

```java
Customer customer = /* một Customer hợp lệ */;
use(customer);
```

Đây là đoạn minh họa, phần khởi tạo phải được thay bằng constructor thực tế của `Customer`.

Nhưng nếu **chính đối tượng ấy** đang nằm trong biến chỉ có kiểu giao diện `Named`:

```java
Named named = customer;
// use(named); // Lỗi biên dịch: kiểu tĩnh Named chưa bảo đảm Auditable
```

Lỗi là hợp lý: compiler chỉ biết `named` là `Named`; không thể tự suy ra mọi `Named` đều là `Auditable`.

Cách thiết kế tốt hơn là giữ kiểu cụ thể đến điểm cần gọi, hoặc giới thiệu một interface kết hợp có ý nghĩa nghiệp vụ. Nếu dữ liệu đến từ nguồn không tin cậy, cần xác thực trước khi ép kiểu; không nên dùng unchecked cast để “thuyết phục” compiler rằng một đối tượng có những năng lực chưa được chứng minh.

### Một interface kết hợp có thể giúp đơn giản API

```java
interface Named {
    String name();
}

interface Auditable {
    void audit();
}

interface AuditedNamed extends Named, Auditable { }

static <T extends AuditedNamed> void useCombined(T value) {
    System.out.println(value.name());
    value.audit();
}
```

Nhóm mã trên là **ví dụ minh họa**, trong đó phương thức `useCombined` cần nằm trong lớp. Nếu hệ thống có khái niệm nghiệp vụ ổn định “đối tượng vừa có tên vừa kiểm toán được”, việc định nghĩa `AuditedNamed` có thể làm API dễ đọc. Tuy nhiên, Java có phân biệt quan hệ kiểu **danh nghĩa** (*nominal typing*): một lớp chỉ `implements Named, Auditable` không tự động trở thành `AuditedNamed`; muốn dùng với `<T extends AuditedNamed>`, lớp phải triển khai interface kết hợp đó (trực tiếp hoặc qua kế thừa).

Đây là sự khác biệt giữa nhiều bounds (đòi đồng thời các supertype mà không cần khai báo interface kết hợp) và một interface mới được đặt tên.

## 11. Nhiều bounds và intersection type

Một tham số có `<T extends Named & Auditable>` được ràng buộc bởi một **intersection type**. Nghĩa là compiler cho phép sử dụng những thao tác từ tất cả giới hạn theo quy tắc của Java.

Tuy nhiên, **không phải mọi vị trí có kiểu Java đều cho phép viết trực tiếp `A & B`**:

```java
// Đúng: danh sách bounds trong khai báo tham số kiểu.
static <T extends Named & Auditable> void inspect(T value) { }

// Sai: wildcard không hỗ trợ cú pháp nhiều bounds này.
// List<? extends Named & Auditable> items;
```

Đây là những **đoạn khai báo minh họa**, cần đặt phương thức trong lớp và có các interface tương ứng. Nếu muốn nhận danh sách chỉ gồm các giá trị thỏa hai interface, có thể khai báo một phương thức generic:

```java
static <T extends Named & Auditable>
void inspectAll(java.util.List<T> values) {
    for (T value : values) {
        System.out.println(value.name());
        value.audit();
    }
}
```

Phương thức này dùng `T` để giữ đồng thời hai ràng buộc cho phần tử danh sách. Nó không đòi Java hỗ trợ cú pháp `List<? extends Named & Auditable>` vốn không hợp lệ.

Java cũng có cú pháp **intersection cast** như `(Named & Auditable) object` trong những tình huống thích hợp. Cast là kiểm tra/chuyển đổi trong một biểu thức và có thể thất bại lúc chạy nếu đối tượng không thỏa các kiểu được yêu cầu; đó là khái niệm khác với việc ràng buộc tham số kiểu để compiler bảo đảm lời gọi API.

## 12. Tác động của thứ tự bounds tới type erasure

Java Generics chủ yếu được kiểm tra ở **compile time** và sử dụng **type erasure**. Khi một tham số kiểu `T` có nhiều giới hạn, **erasure của `T` là erasure của giới hạn đầu tiên**.

Ví dụ:

```java
static <T extends Number & Comparable<T>> double asDouble(T value) {
    return value.doubleValue();
}
```

Bound đầu là `Number`, nên erasure của `T` là `Number`. Chữ ký ở mức JVM của tham số tương ứng về cơ bản là `Number`, dù compiler ở mã nguồn còn biết thêm yêu cầu `Comparable<T>`.

Nếu chỉ có các interface:

```java
static <T extends Named & Auditable> String label(T value) {
    return value.name();
}
```

erasure của `T` là `Named` vì đây là giới hạn đầu tiên. Nếu viết lại một API thành `<T extends Auditable & Named>`, erasure có thể đổi sang `Auditable`. Điều này ảnh hưởng đến descriptor/khả năng tương thích nhị phân và có thể liên quan đến bridge method khi biên dịch các phân cấp lớp.

### Thứ tự interface bounds không nên đổi tùy tiện trong API công khai

Xét hai khai báo:

```java
<T extends Named & Auditable>
<T extends Auditable & Named>
```

Cả hai có cùng ý định logic “`T` phải thỏa hai interface”, và đều có thể hợp lệ nếu hai interface tương thích. Nhưng **giới hạn đầu tiên khác nhau** nên erasure khác. Trong thư viện đã phát hành, đổi thứ tự có thể tạo khác biệt trong chữ ký JVM của các phương thức dùng `T`, từ đó gây vấn đề khi mã client cũ không được biên dịch lại.

Khi thiết kế API công khai, chọn thứ tự có ý nghĩa, giữ ổn định và xem thay đổi bounds là việc có thể ảnh hưởng tương thích, không chỉ là điều chỉnh thẩm mỹ.

Lưu ý Java vẫn lưu thông tin chữ ký generic trong metadata `Signature` của class file để trình biên dịch và công cụ khác khai thác; “erasure” **không có nghĩa mọi thông tin generic bị xóa hoàn toàn khỏi mọi phần của `.class`**.

## 13. Những giới hạn không thể kết hợp

Multiple bounds không cho phép bất kỳ tổ hợp kiểu nào một cách vô điều kiện.

### 13.1. Hai giới hạn lớp

```java
class ParentA { }
class ParentB { }

// Sai: Java không hỗ trợ hai class bounds.
// class Invalid<T extends ParentA & ParentB> { }
```

Nếu cần năng lực từ nhiều nguồn, sử dụng interface phù hợp hoặc composition thay vì cố mô hình hóa đa kế thừa class.

### 13.2. Class bound không đứng đầu

```java
interface Named {
    String name();
}

class Base { }

// Sai: class bound Base phải đứng đầu.
// class Invalid<T extends Named & Base> { }
```

Đúng là `<T extends Base & Named>`, với điều kiện các giới hạn có thể cùng được đáp ứng.

### 13.3. Hai cách tham số hóa xung đột của cùng generic interface

```java
interface Handler<T> {
    void handle(T value);
}

// Sai: hai cách tham số hóa khác nhau của cùng Handler.
// class Invalid<T extends Handler<String> & Handler<Integer>> { }
```

Một kiểu Java không được đồng thời kế thừa `Handler<String>` và `Handler<Integer>`. Đổi thứ tự hai giới hạn không sửa được lỗi: bản thân quan hệ kế thừa đã mâu thuẫn.

### 13.4. Các hợp đồng phương thức bất tương thích

```java
interface TextResult {
    String result();
}

interface NumberResult {
    Integer result();
}

// Sai: yêu cầu hai phương thức cùng tên/tham số nhưng
// có kiểu kết quả không tương thích.
// class Invalid<T extends TextResult & NumberResult> { }
```

Hai phương thức `result()` có cùng tên và danh sách tham số, nhưng kiểu trả về `String` và `Integer` không có quan hệ trả về covariant phù hợp. Compiler không thể định nghĩa một `T` đáp ứng hợp đồng phương thức của cả hai theo cách hợp lệ.

Các ví dụ ở phần này là **mã minh họa lỗi**, không phải chương trình để chạy. Chỉ bỏ comment khi muốn tự xem thông báo biên dịch.

## 14. Những lỗi cú pháp và cách sửa

| Viết sai hoặc hiểu nhầm | Vấn đề | Cách sửa |
|---|---|---|
| `<T implements A & B>` | Khai báo giới hạn tham số kiểu không dùng `implements`. | `<T extends A & B>`. |
| `<T extends A, B>` khi muốn `T` thỏa cả A và B | Dấu phẩy dùng phân cách **nhiều tham số kiểu**, không dùng nối multiple bounds. | `<T extends A & B>`. |
| `<T extends InterfaceA & ClassB>` | Class bound phải đứng đầu. | `<T extends ClassB & InterfaceA>` nếu các kiểu tương thích. |
| `<T extends ClassA & ClassB>` | Tối đa một class bound. | Dùng một lớp nền + các interface. |
| Nghĩ `A & B` cho phép A **hoặc** B | Đây là giao của hai hợp đồng. | Đối số kiểu phải thỏa **cả hai**. |
| Lớp có đủ phương thức nhưng không `implements¤ interface | Java dùng quan hệ kiểu danh nghĩa, không phải duck typing. | Khai báo đúng các quan hệ `implements`/`extends`. |
| `List<? extends A & B>` | Wildcard bound không cho viết trực tiếp nhiều giới hạn. | Dùng phương thức `<T extends A & B> void f(List<T>)`. |
| Gọi generic method qua biến khai báo chỉ là `A` khi yêu cầu `A & B` | Kiểu tĩnh chưa chứng minh bound `B`. | Giữ kiểu cụ thể, hoặc dùng API hợp đồng phù hợp. |
| Thay đổi thứ tự interface bounds tùy ý trong thư viện | Bound đầu chi phối erasure, có thể ảnh hưởng tương thích nhị phân. | Ổn định thứ tự bounds trong API công khai. |
| Dùng raw type/unchecked cast để né lỗi | Mất bảo vệ kiểu và có thể phát sinh lỗi runtime. | Thiết kế đúng bounds và chọn kiểu hợp lệ. |

## 15. Tình huống thiết kế thực tế: kiểm toán các thực thể có tên

Giả sử ứng dụng có nhiều đối tượng như `Customer`, `Invoice` hoặc `Job`. Không phải loại nào cũng cần cùng một lớp cha, nhưng những thao tác chung như “hiển thị tên” và “ghi nhật ký kiểm toán” là các năng lực có thể định nghĩa bằng interface.

Có ba cách thiết kế hợp lý:

1. **Giới hạn theo hai interface**: `<T extends Named & Auditable> void run(T value)`. Phù hợp khi thuật toán cần cả hai năng lực nhưng không quan tâm một class base cụ thể.
2. **Giới hạn theo lớp + interface**: `<T extends Identified & Named & Auditable> void run(T value)`. Phù hợp khi thuật toán thực sự cần truy cập API của lớp nền `Identified` bên cạnh các interface.
3. **Định nghĩa interface nghiệp vụ kết hợp**: `interface AuditedNamed extends Named, Auditable {}` rồi dùng `<T extends AuditedNamed>`. Phù hợp nếu “đối tượng có tên và có thể kiểm toán” là khái niệm ổn định trong domain và những lớp liên quan có thể công khai triển khai giao diện chung này.

Không nên dùng nhiều bounds chỉ để chứng minh khả năng viết syntax phức tạp. Nếu thuật toán chỉ cần `Named.name()`, hãy dùng một giới hạn `<T extends Named>` hoặc nhận trực tiếp `Named`. Mỗi giới hạn thêm vào **thu hẹp tập kiểu hợp lệ** mà người gọi có thể truyền cho API.

Khi cần thao tác với nhiều phần tử:

```java
static <T extends Named & Auditable> void auditAll(
        java.util.List<T> items) {
    for (T item : items) {
        System.out.println(item.name());
        item.audit();
    }
}
```

Đây là **phương thức minh họa nằm trong một lớp**. Bằng một bound duy nhất trên `T`, compiler kiểm tra mọi phần tử trong danh sách mà không cần cast thành `Named` hoặc `Auditable` riêng lẻ.

## 16. Checklist áp dụng multiple bounds

Khi gặp đề bài “thiết kế `<T extends A & B>`”, thực hiện lần lượt:

1. **Xác định năng lực thật sự cần**: phương thức sẽ gọi API nào từ `A`, từ `B`? Nếu không dùng `B`, có thể bound ấy chưa cần thiết.
2. **Xác định A, B là class hay interface**: nếu có một class bound, đưa nó lên đầu; không cố đưa hai class vào danh sách.
3. **Kiểm tra tính tương thích của hợp đồng**: các giới hạn có thể cùng được một kiểu triển khai hợp pháp hay không? Có xung đột phương thức hoặc generic superinterface không?
4. **Đặt `<T extends ...>` đúng vị trí**: sau tên lớp khi khai báo generic class, hoặc trước kiểu trả về khi khai báo generic method.
5. **Giữ nguyên quan hệ kiểu tại lời gọi**: dùng đối số cụ thể thỏa tất cả giới hạn; tránh đánh mất kiểu thành một interface quá rộng trước khi gọi.
6. **Phân biệt bound với wildcard**: `<T extends A & B>` hợp lệ; `? extends A & B` không phải cú pháp wildcard hợp lệ.
7. **Chú ý erasure ở API công khai**: giới hạn đầu ảnh hưởng kiểu erasure; không tự ý đảo thứ tự ở API đã phát hành.
8. **Biên dịch bằng Java 17+**, kiểm tra cảnh báo `rawtypes`/`unchecked` nếu tích hợp mã cũ, và thử cả trường hợp hợp lệ lẫn lỗi compile-time như một bài tập học tập.

## 17. Tự kiểm tra

**Câu 1.** Trong `<T extends Named & Auditable>`, một kiểu chỉ triển khai `Named` có dùng làm đối số kiểu `T` được không? Vì sao?

**Câu 2.** Đâu là khai báo hợp lệ nếu `Base` là lớp và `Named`, `Auditable` là interface: `<T extends Named & Base & Auditable>` hay `<T extends Base & Named & Auditable>`?

**Câu 3.** Vì sao `<T extends Number & Comparable<T>>` hỗ trợ thuật toán so sánh số, trong khi `<T extends Number>` chưa đủ để gọi `compareTo(T)`?

**Câu 4.** Java có chấp nhận `List<? extends Named & Auditable>` không? Muốn xử lý một danh sách các đối tượng có cả hai năng lực, có thể khai báo phương thức generic thế nào?

**Câu 5.** Trong phương thức `static <T extends Named & Auditable> void f(T value)`, `T` thuộc lớp hay thuộc phương thức?

**Câu 6.** Tại sao đảo thứ tự `Named & Auditable` sang `Auditable & Named` có thể quan trọng ở cấp class file mặc dù cả hai đều yêu cầu cùng hai interface?

**Gợi ý đáp án:** (1) Không, đối số kiểu phải thỏa tất cả giới hạn. (2) Dạng thứ hai, vì bound là lớp phải đứng đầu. (3) `Number` tự nó không khai báo `compareTo`; bound `Comparable<T>` cung cấp hợp đồng so sánh cần thiết. (4) Không; dùng chẳng hạn `static <T extends Named & Auditable> void f(List<T> items)`. (5) `T` thuộc riêng phương thức generic. (6) Erasure của `T` lấy từ giới hạn đầu tiên nên descriptor phương thức có thể thay đổi.

**Ghi nhớ:** Multiple bounds trong Java dùng cú pháp **`<T extends A & B>`**, biểu thị **T phải thỏa đồng thời A và B**. Nếu có giới hạn lớp, nó phải **đứng đầu** và chỉ được có một giới hạn lớp; các giới hạn tiếp theo là interface. Compiler cho phép gọi API của mọi giới hạn trên `T`, đồng thời ngăn kiểu không đáp ứng hợp đồng ngay khi biên dịch. Hãy nhớ thứ tự giới hạn đầu tiên có ý nghĩa đối với type erasure và phân biệt ràng buộc tham số kiểu với wildcard.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="generic-type-bounds-004">Quy tắc lớp đứng trước và các giới hạn phải tương thích</a>

<details>
<summary>Xem chi tiết</summary>

# Quy tắc lớp đứng trước và các giới hạn phải tương thích trong Java Generics

## 1. Mục tiêu bài học

Trong Java Generics, **giới hạn kiểu** (*type bound*) cho biết một tham số kiểu được phép đại diện cho những kiểu nào và có thể sử dụng những phương thức nào. Một tham số kiểu T có thể có **nhiều cận trên** (*multiple upper bounds*), nhưng danh sách này phải tuân thủ những quy tắc bắt buộc:

1. Nếu có **cận là class**, class đó **phải đứng đầu** danh sách.
2. Một type parameter không thể khai báo **hai class bounds độc lập**.
3. Những cận còn lại sau class bound phải là **interface types**; chúng được nối bằng dấu **&**, không phải dấu phẩy.
4. Các cận phải **tương thích với nhau**: phải tuân thủ luật kế thừa của Java, không được gây xung đột giữa những specialization không thể cùng tồn tại hay những phương thức không có chữ ký tương thích.
5. Mỗi đối số kiểu thực tế phải đáp ứng **tất cả** các cận đã khai báo.
6. **Cận đầu tiên** có vai trò đặc biệt trong **type erasure**; thay đổi thứ tự bounds có thể có hệ quả về binary compatibility.

Ví dụ đúng:

~~~java
static <T extends Number & Comparable<T>> T larger(T a, T b) {
    return a.compareTo(b) >= 0 ? a : b;
}
~~~

T phải vừa là subtype của Number, vừa thực thi Comparable<T>. Một Integer thỏa cả hai điều kiện; một Number tổng quát không tự bảo đảm thực thi Comparable<Number>.

Bài tập trung vào **quy tắc khai báo, tính tương thích và tác động kỹ thuật của nhiều bounds**, không thay thế cho toàn bộ chương về wildcard, PECS hoặc variance.

## 2. Cú pháp giới hạn kiểu

Cú pháp chung:

~~~text
<T extends FirstBound & Interface2 & Interface3>
~~~

Trong đó:

- **T** là type parameter được khai báo.
- **extends** là từ khóa dùng cho **mọi upper bound**, dù bound đó là class hay interface.
- **FirstBound** có thể là một class hoặc một interface.
- Nếu có một class bound, nó **bắt buộc là bound đầu tiên**.
- Sau bound đầu tiên, những bound thêm vào phải là interface types.
- **&** nối các cận thuộc **cùng một** type parameter.

Ví dụ:

~~~java
class NumericValue<T extends Number> {
    T value;
}

class SortedNumber<T extends Number & Comparable<T>> {
    T value;
}

class Exportable<T extends Number & Comparable<T> & java.io.Serializable> {
    T value;
}
~~~

Các khai báo trên đều hợp lệ. Trong SortedNumber, T vừa có các phương thức của Number như doubleValue(), vừa có compareTo(T) của Comparable<T>.

Một dấu phẩy có ý nghĩa **khác** dấu &: 

~~~java
class Pair<A extends Number, B extends CharSequence> {
    A first;
    B second;
}
~~~

**<A extends Number, B extends CharSequence>** khai báo **hai type parameters** có giới hạn riêng. Nó không tương đương **<T extends Number & CharSequence>**, vốn khai báo **một T** phải đáp ứng cả Number lẫn CharSequence.

| Cú pháp | Ý nghĩa |
| --- | --- |
| **<T>** | T có cận trên ngầm định Object |
| **<T extends Number>** | Một T, cận class Number |
| **<T extends Runnable>** | Một T, cận interface Runnable |
| **<T extends Number & Comparable<T>>** | Một T, vừa là Number vừa Comparable<T> |
| **<T extends Runnable & AutoCloseable>** | Một T, đáp ứng hai interface |
| **<A extends Number, B extends CharSequence>** | Hai tham số kiểu độc lập A và B |

## 3. Nếu có class bound, class phải đứng đầu

Java quy định rằng trong một intersection của các bounds khai báo cho type parameter, một class chỉ có thể xuất hiện ở **vị trí đầu tiên**. Ví dụ:

~~~java
// Hợp lệ:
class Good<T extends Number & Comparable<T>> {}
~~~

Sai khi đảo hai cận để đưa class về phía sau:

~~~java
// Minh họa lỗi biên dịch:
class Wrong<T extends Comparable<T> & Number> {}
~~~

**Comparable<T>** là interface; **Number** là class và lại xuất hiện sau dấu &. Đây là lỗi cú pháp/cấu trúc bounds, không phải lỗi chỉ xuất hiện lúc sử dụng type argument.

Giải pháp là đổi thứ tự:

~~~java
class Correct<T extends Number & Comparable<T>> {}
~~~

Cũng không thể viết:

~~~java
// Minh họa lỗi biên dịch:
class TwoClasses<T extends Number & Thread> {}
~~~

Number và Thread đều là class. Việc Java không hỗ trợ một class kế thừa đồng thời hai superclass độc lập cũng thể hiện ở cách quy định type bounds: **không có danh sách hai class bounds**.

**Lưu ý quan trọng:** nếu **không có class bound**, interface có thể đứng đầu. Dạng sau hợp lệ:

~~~java
class WorkItem<T extends Runnable & AutoCloseable> {
    T task;
}
~~~

Cả Runnable và AutoCloseable đều là interface. Thứ tự của các interfaces có thể khác nhau về mặt luật “class đứng trước”, nhưng bound đầu tiên vẫn có thể tác động tới erasure và bytecode; do đó đừng tùy tiện thay đổi thứ tự trong public API đã phát hành.

## 4. `extends` vẫn dùng khi bound là interface

Trong khai báo type parameter, Java chỉ dùng `extends`, kể cả khi bound là interface:

~~~java
class Runner<T extends Runnable> {
    private final T task;
    Runner(T task) { this.task = task; }
    void execute() { task.run(); }
}
~~~

Không viết `<T implements Runnable>`: `implements` chỉ dùng khi khai báo một class thực hiện interface, không dùng trong type-parameter bound. Nếu kết hợp class và interface, viết `<T extends Thread & Runnable>` với `Thread` đứng đầu; đây là bound có `Runnable` phần nào dư vì `Thread` đã thực hiện giao diện đó.

Sau khi nhận diện đúng cú pháp, phần đáng đánh giá kỹ hơn là **bộ bounds có thể thỏa đồng thời hay không**. Chẳng hạn `String & Runnable` không có type argument hợp lệ vì `String` là final và không triển khai `Runnable`.

## 5. Các cận phải có kiểu hiện thực đồng thời

Đọc **<T extends A & B>** như một yêu cầu kết hợp:

~~~text
T phải là subtype của A
VÀ
T phải là subtype của B
~~~

Nó **không** có nghĩa T được chọn một trong hai A hoặc B.

Ví dụ:

~~~java
interface Printable {
    String print();
}

interface Identified {
    long id();
}

class Ticket implements Printable, Identified {
    private final long number;

    Ticket(long number) {
        this.number = number;
    }

    @Override
    public String print() {
        return "ticket-" + number;
    }

    @Override
    public long id() {
        return number;
    }
}
~~~

Một generic method có thể yêu cầu cả hai giao diện:

~~~java
static <T extends Printable & Identified> String describe(T item) {
    return item.id() + ":" + item.print();
}
~~~

**Ticket** là type argument hợp lệ vì implement cả Printable và Identified. Một kiểu chỉ implement Printable nhưng không implement Identified không hợp lệ. Mặc dù trong ví dụ không có class bound, compiler vẫn cho phép gọi cả print() lẫn id() trên biến T.

**Khả năng tương thích có hai lớp cần tách biệt:**

- **Tại khai báo bounds:** Các giới hạn không được mâu thuẫn với quy tắc kiểu của Java (ví dụ lặp cùng generic interface bằng hai type arguments khác nhau).
- **Tại nơi áp dụng type argument:** Kiểu cụ thể truyền vào phải thỏa tất cả bounds. Việc một interface tùy ý chưa có class hiện thực ngay ở chỗ khai báo **không tự khiến intersection sai**; về sau một class có thể implement nhiều interface tương thích cùng lúc.

Đừng lẫn “chưa tìm thấy class đáp ứng” với “các bounds tự mâu thuẫn”.

## 6. Ví dụ chạy hoàn chỉnh: kết hợp nhiều interface bounds

Lưu thành **InterfaceBoundsDemo.java**:

~~~java
public class InterfaceBoundsDemo {
    interface Printable {
        String print();
    }

    interface Identified {
        long id();
    }

    static final class Ticket implements Printable, Identified {
        private final long number;

        Ticket(long number) {
            this.number = number;
        }

        @Override
        public String print() {
            return "ticket-" + number;
        }

        @Override
        public long id() {
            return number;
        }
    }

    static <T extends Printable & Identified> String describe(T item) {
        return item.id() + ":" + item.print();
    }

    public static void main(String[] args) {
        Ticket ticket = new Ticket(17);
        System.out.println(describe(ticket));

        Printable asPrintable = ticket;
        System.out.println(asPrintable.print());
    }
}
~~~

Biên dịch và chạy trên JDK 17+:

~~~bash
javac -encoding UTF-8 InterfaceBoundsDemo.java
java InterfaceBoundsDemo
~~~

Stdout chính xác:

~~~text
17:ticket-17
ticket-17
~~~

Trong describe(T), compiler cho phép gọi **item.id()** và **item.print()** vì T có cả hai interface bounds. Khi biến được khai báo đơn thuần là Printable, phương thức id() không xuất hiện trong hợp đồng của kiểu biến đó; quyền truy cập thành viên tại source code phụ thuộc vào **kiểu tĩnh**, không chỉ vào runtime object.

## 7. Kết hợp class bound với nhiều interface bounds

Một tình huống thực tiễn là muốn thao tác với số, đồng thời so sánh và serialize kiểu số đó:

~~~java
import java.io.Serializable;

class NumericEntry<T extends Number & Comparable<T> & Serializable> {
    private final T number;

    NumericEntry(T number) {
        this.number = number;
    }

    double asDouble() {
        return number.doubleValue();
    }

    boolean isGreaterThan(T other) {
        return number.compareTo(other) > 0;
    }
}
~~~

Giải nghĩa:

- **Number** là class bound, nên đứng đầu.
- **Comparable<T>** là interface bound, cho phép compareTo với T.
- **Serializable** là interface bound bổ sung, không trực tiếp tạo method public mới để gọi trên T.
- **Integer**, **Long**, **Double** là những ví dụ wrapper classes có thể đáp ứng các bound này với type arguments phù hợp.
- **String** không đáp ứng Number dù String có Comparable<String> và Serializable.

Lưu ý **Number bản thân đã implement Serializable**; trong trường hợp cụ thể trên, ghi thêm Serializable là dư về điều kiện cần. Đây là ví dụ có chủ ý để minh họa cú pháp ba bounds; ở API thực tế, cân nhắc bỏ bound dư thừa.

Không nên hiểu một class bound là “chuyển T thành primitive”. **T vẫn là reference type variable**, vì vậy cú pháp **a + b** cho hai biến T không được cho phép như phép cộng số generic. Muốn cộng dưới kiểu double, có thể lấy **a.doubleValue() + b.doubleValue()** rồi chấp nhận hệ quả về độ chính xác số.

Một class abstract cũng có thể làm class bound, và type argument thực tế có thể là concrete subclass tương ứng. Không bắt buộc class bound là một concrete class có constructor không tham số.

## 8. Ví dụ chạy hoàn chỉnh: Number, Comparable và Serializable

Lưu thành **ClassFirstBoundsDemo.java**:

~~~java
import java.io.Serializable;

public class ClassFirstBoundsDemo {
    static final class NumericEntry<T extends Number & Comparable<T> & Serializable> {
        private final T value;

        NumericEntry(T value) {
            this.value = value;
        }

        double twice() {
            return value.doubleValue() * 2.0;
        }

        boolean greaterThan(T other) {
            return value.compareTo(other) > 0;
        }
    }

    static <T extends Number & Comparable<T>> T maximum(T left, T right) {
        return left.compareTo(right) >= 0 ? left : right;
    }

    public static void main(String[] args) {
        NumericEntry<Integer> entry = new NumericEntry<>(8);
        System.out.println(entry.twice());
        System.out.println(entry.greaterThan(3));
        System.out.println(maximum(4, 9));
        System.out.println(maximum(2.5, 1.5));
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac -encoding UTF-8 ClassFirstBoundsDemo.java
java ClassFirstBoundsDemo
~~~

Stdout:

~~~text
16.0
true
9
2.5
~~~

Phân tích:

1. **NumericEntry<Integer>** hợp lệ vì Integer extends Number, implements Comparable<Integer> và Serializable.
2. **twice()** dùng Number.doubleValue(), trả double.
3. **greaterThan(3)** gọi Comparable<Integer>.compareTo với giá trị Integer sau autoboxing.
4. Trong **maximum(4, 9)**, kiểu phù hợp được suy luận là Integer.
5. Trong **maximum(2.5, 1.5)**, kiểu phù hợp là Double; kết quả được in theo định dạng Double thông thường.

Phương thức maximum dùng T ở cả tham số lẫn kết quả để compiler giữ quan hệ kiểu. Các bounds cho phép gọi compareTo; không cần ép kiểu từ Object.

## 9. Thế nào là các bounds không tương thích?

Có một số loại xung đột thật sự cần nhận diện.

### 9.1. Hai class bounds không được phép

~~~java
// Cố ý sai:
class Invalid<T extends Number & Thread> {}
~~~

Đây là vi phạm luật class bound, dù ta có tưởng tượng một kiểu đáp ứng cả hai. Một class bound nếu có phải đứng đầu, và phần còn lại là interface types.

### 9.2. Một class final và một interface không thể cùng thỏa

Ví dụ minh họa:

~~~java
// Cố ý sai:
class Impossible<T extends String & Runnable> {}
~~~

String là **final class** và không implement Runnable. Không có subtype của String nào có thể được tạo để bổ sung Runnable; các bounds này không thể có type argument đáp ứng. Compiler phát hiện mâu thuẫn. Ngược lại, nếu class bound không final, việc bản thân class đó chưa implement một interface chưa nhất thiết khiến khai báo không hợp lệ; một subclass có thể bổ sung interface.

### 9.3. Cùng một generic interface bị yêu cầu với hai specialization khác nhau

Xét:

~~~java
interface TextComparable extends Comparable<String> {}

interface NumberComparable extends Comparable<Integer> {}

// Không hợp lệ:
// class Conflict<T extends TextComparable & NumberComparable> {}
~~~

Một kiểu T vừa thỏa TextComparable vừa thỏa NumberComparable sẽ đồng thời là **Comparable<String>** và **Comparable<Integer>**. Java không cho một subtype kế thừa cùng generic interface với hai type arguments khác nhau. Đây là xung đột về generic supertypes, không thể sửa bằng cast.

Một dạng trực tiếp hơn:

~~~java
// Minh họa lỗi:
class ConflictDirect<T extends Comparable<String> & Comparable<Integer>> {}
~~~

Dấu & không thể dùng để “ghép” hai phiên bản Comparable với type arguments mâu thuẫn.

### 9.4. Những method signatures trùng nhưng return types không tương thích

Hai interface riêng biệt có thể khai báo cùng một chữ ký phương thức nhưng với return types không thể cùng đáp ứng:

~~~java
interface StringReader {
    String read();
}

interface IntegerReader {
    Integer read();
}

// Minh họa bounds xung đột:
// class ImpossibleReader<T extends StringReader & IntegerReader> {}
~~~

Không có một method read() không tham số nào có thể đồng thời trả String và Integer theo hợp đồng override của cả hai, vì String và Integer là những final classes không có quan hệ subtype cần thiết.

Nếu hai interface có phương thức cùng signature và **return types tương thích theo covariance**, chúng có thể cùng được implement bởi một class thích hợp. Chẳng hạn một interface quy định Object read() và interface khác quy định String read(); class triển khai có thể trả String, vì String là subtype của Object.

**Quy tắc thực hành:** khi nhiều bounds đều có methods cùng tên, phải xét **chữ ký sau erasure, return type substitutability, default methods và luật override**. Đừng chỉ nhìn thấy hai interface khác tên rồi kết luận intersection hợp lệ.

### 9.5. Method name clash sau type erasure

Ví dụ về hợp đồng có cùng erasure nhưng type arguments khác:

~~~java
interface Strings {
    void accept(java.util.List<String> values);
}

interface Integers {
    void accept(java.util.List<Integer> values);
}

// Một type parameter cố kết hợp các bounds này không thể
// giả định sẽ có hai overload độc lập tên accept(List<...>).
~~~

Hai phương thức **accept(List<String>)** và **accept(List<Integer>)** có cùng erasure đối với tham số List. Một class hiện thực thường không thể viết hai overload riêng chỉ khác type argument. Tùy quan hệ giữa các declarations, compiler có thể báo name clash hoặc xung đột kế thừa. Khi thiết kế intersection bounds, cần tránh đưa các hợp đồng không thể hiện thực được vào cùng một type parameter.

## 10. “Tương thích” không có nghĩa các interface phải có quan hệ cha–con

Một kiểu có thể implement nhiều interface độc lập:

~~~java
interface Auditable {
    String audit();
}

interface Validatable {
    boolean valid();
}

class Event implements Auditable, Validatable {
    @Override
    public String audit() {
        return "event";
    }

    @Override
    public boolean valid() {
        return true;
    }
}
~~~

Hai interface không bắt buộc cái này extends cái kia. Miễn có type argument đáp ứng đồng thời và không phát sinh xung đột thành viên, khai báo:

~~~java
static <T extends Auditable & Validatable> boolean process(T item) {
    return item.valid() && !item.audit().isEmpty();
}
~~~

là có ý nghĩa và compiler cho phép cả hai thao tác.

Nếu interface A extends B, bound **T extends A & B** có thể dư thừa vì thỏa A đã suy ra thỏa B. Dư thừa không tự làm API sai về mặt cú pháp, nhưng khiến người đọc khó nhận biết đâu là ràng buộc thực sự quan trọng. Hãy tối giản danh sách khi có thể.

## 11. Nếu chỉ có các interface bounds, thứ tự vẫn có ý nghĩa kỹ thuật

Hai khai báo sau cùng yêu cầu kiểu T thỏa cả Serializable và Comparable<T>:

~~~java
class First<T extends java.io.Serializable & Comparable<T>> {}

class Second<T extends Comparable<T> & java.io.Serializable> {}
~~~

Ở cấp độ quan hệ “T phải đáp ứng cả hai”, điều kiện của chúng tương tự. Tuy nhiên, **cận đầu tiên** quyết định type erasure của type variable T (khi bound đầu tiên là interface, erasure là interface đó):

- Trong First, erasure của T là **Serializable**.
- Trong Second, erasure của T là **Comparable**.

Bởi vậy việc đổi thứ tự các interface bounds trong một API đã được biên dịch và phát hành **có thể thay đổi chữ ký bytecode** của các field/method dùng T và ảnh hưởng binary compatibility. Khi thiết kế thư viện công khai, đừng coi việc đảo thứ tự là thay đổi thuần thẩm mỹ.

Ví dụ với class bound:

~~~java
class ClassFirst<T extends Number & Comparable<T>> {
    T item;
}
~~~

Erasure của T là Number, vì bound đầu tiên là Number. Với unbounded T, erasure thường là Object. Với interface-only bound **<T extends Runnable & AutoCloseable>**, bound đầu tiên Runnable quyết định erasure của T là Runnable.

**Quan trọng:** “erasure dùng bound đầu tiên” là quy tắc mô tả ở cấp JVM representation. Tại Java source, compiler vẫn cho phép dùng phương thức được bảo đảm bởi **mọi bounds**, không chỉ bound đầu tiên.

## 12. Ví dụ chạy hoàn chỉnh: thứ tự interface bounds và quyền gọi method

Lưu thành **InterfaceOrderDemo.java**:

~~~java
public class InterfaceOrderDemo {
    interface Named {
        String name();
    }

    interface Scored {
        int score();
    }

    static final class Student implements Named, Scored {
        private final String name;
        private final int score;

        Student(String name, int score) {
            this.name = name;
            this.score = score;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public int score() {
            return score;
        }
    }

    static <T extends Named & Scored> String first(T value) {
        return value.name() + ":" + value.score();
    }

    static <T extends Scored & Named> String second(T value) {
        return value.name() + ":" + value.score();
    }

    public static void main(String[] args) {
        Student student = new Student("Lan", 95);
        System.out.println(first(student));
        System.out.println(second(student));
    }
}
~~~

Chạy:

~~~bash
javac -encoding UTF-8 InterfaceOrderDemo.java
java InterfaceOrderDemo
~~~

Stdout:

~~~text
Lan:95
Lan:95
~~~

Cả hai generic methods đều được gọi với Student vì Student implement Named lẫn Scored. **first** có cận đầu tiên Named; **second** có cận đầu tiên Scored. Trình biên dịch vẫn cho cả hai methods gọi name() và score() do các interface bounds hợp lệ. Kết quả runtime giống nhau, nhưng erasure của T ở hai method signatures có thể khác nhau.

## 13. Giới hạn kiểu là giao (intersection), không phải hợp (union)

Hãy hiểu rõ các biểu thức:

~~~java
<T extends Runnable & AutoCloseable>
~~~

Có nghĩa T phải **đồng thời** là Runnable và AutoCloseable. Nó không chấp nhận bất kỳ Runnable *hoặc* bất kỳ AutoCloseable nào.

Dấu & tại đây biểu diễn giao điều kiện. Ví dụ generic method:

~~~java
static <T extends Runnable & AutoCloseable> void executeAndClose(T resource)
        throws Exception {
    try (resource) {
        resource.run();
    }
}
~~~

Ví dụ trên dành cho Java 17+, nơi biến resource là một parameter effectively final trong try-with-resources; parameter là effectively final khi không bị gán lại. AutoCloseable.close có thể ném Exception, nên method khai báo throws Exception. Runnable.run không có checked exception trong chữ ký.

Một object muốn dùng với executeAndClose phải implement cả Runnable và AutoCloseable. Không nên gán nhầm một class chỉ implement Runnable vào method này.

### Không nhầm với intersection cast

Java còn cho phép intersection type trong cast, ví dụ:

~~~java
// Chỉ minh họa một biểu thức cast:
Object source = /* đối tượng phù hợp */ null;
// Runnable task = (Runnable & AutoCloseable) source;
~~~

Cast ở runtime có các kiểm tra tương ứng, có thể ném ClassCastException khi object không thực thi những kiểu yêu cầu. Đây **không phải** cách thiết kế thay thế cho generic bounds khi muốn compiler kiểm tra quan hệ kiểu tại chữ ký method. Nếu không chắc về runtime object, không nên cast chỉ để “đưa nó qua” một API có bounds chặt.

## 14. Một class bound là final: xem khả năng tương thích với interfaces

Nếu class bound là final, không thể có một subtype mới kế thừa nó. Do đó nó phải **tự đáp ứng** các interface bounds bổ sung, nếu không sẽ không có type argument hợp lệ.

Ví dụ hợp lệ:

~~~java
class TextComparable<T extends String & Comparable<String>> {
    T value;
}
~~~

String final nhưng **đã implement Comparable<String>**, nên **String** có thể là type argument. Đây chỉ là ví dụ về bounds hợp lệ, chứ không phải khuyến nghị tạo type parameter T khi kiểu thực tế chỉ có thể là String. Trong trường hợp đó, dùng trực tiếp String thường đơn giản hơn.

Ví dụ không hợp lệ:

~~~java
// String là final và không implement Runnable:
// class TextRunnable<T extends String & Runnable> {}
~~~

Ngược lại, với class bound không final, một subclass có thể thực thi thêm các interfaces tương thích:

~~~java
abstract class BaseJob {
    abstract String label();
}

interface Schedulable {
    void schedule();
}

class ScheduledJob extends BaseJob implements Schedulable {
    @Override
    String label() {
        return "scheduled";
    }

    @Override
    public void schedule() {}
}

class JobHolder<T extends BaseJob & Schedulable> {
    T job;
}
~~~

ScheduledJob thỏa đồng thời class bound BaseJob và interface bound Schedulable. BaseJob không nhất thiết phải implements Schedulable từ trước.

## 15. Generic method áp dụng nhiều bounds cho phương thức riêng

Bounds không chỉ dùng trên generic classes:

~~~java
static <T extends Number & Comparable<T>> T min(T left, T right) {
    return left.compareTo(right) <= 0 ? left : right;
}
~~~

Type parameter T ở đây thuộc **generic method**, không phải một class chứa nó. Bất kỳ nơi gọi nào cũng phải đưa type arguments đáp ứng bounds. Phương thức có thể nằm trong class không generic hoặc class generic.

Nếu một class generic có T không bound, một static method bên trong không thể “mượn T của class” để thêm bounds. Static method cần tự khai báo type parameter của mình:

~~~java
class Holder<T> {
    static <U extends Number & Comparable<U>> U max(U a, U b) {
        return a.compareTo(b) >= 0 ? a : b;
    }
}
~~~

U độc lập với T của Holder. Bất kỳ cách dùng Holder<String> hay Holder<Integer> đều không làm thay đổi contract của static method max.

### Một phương thức dùng nhiều type parameters có bounds khác nhau

~~~java
static <A extends Number, B extends CharSequence> String summarize(A a, B b) {
    return b.length() + ":" + a.doubleValue();
}
~~~

Dấu phẩy tách **A** và **B**, không phải hai cận của cùng tham số. Một lời gọi summarize(3, "abcd") có thể suy luận A = Integer, B = String. Trong thân, compiler cho phép doubleValue() trên A và length() trên B theo đúng bound riêng.

## 16. Giới hạn tương thích còn phụ thuộc vào generic type arguments

Tính tương thích không chỉ xét tên interface. Những type arguments được dùng trong bounds có thể gây mâu thuẫn.

Một ví dụ F-bounded polymorphism hợp lệ:

~~~java
interface HasOrder<T> {
    int compareTo(T other);
}

class OrderedValue<T extends HasOrder<T>> {
    private final T value;

    OrderedValue(T value) {
        this.value = value;
    }

    boolean greaterThan(T other) {
        return value.compareTo(other) > 0;
    }
}
~~~

Cận **T extends HasOrder<T>** yêu cầu T implement chính giao diện dùng T làm type argument. Tham chiếu đến T bên trong bound **không phải lỗi vòng lặp tự động**. Dạng này phổ biến trong Comparable<T>, Enum<E extends Enum<E>>, hoặc API kiểu “self type”.

Một bound phức tạp hơn:

~~~java
class OrderedNumber<T extends Number & Comparable<? super T>> {
    private final T value;

    OrderedNumber(T value) {
        this.value = value;
    }

    int compare(T another) {
        return value.compareTo(another);
    }
}
~~~

**Comparable<? super T>** cho phép T có thể so sánh với T hoặc một supertype phù hợp của T. Đây là cách diễn đạt rộng hơn **Comparable<T>**, nhưng cần hiểu variance và contract thật của Comparable.

Dạng tự tham chiếu hợp lệ không đồng nghĩa với mọi giới hạn vòng tròn đều hợp lệ. Ví dụ **<T extends T>** là một cyclic bound không cung cấp cơ sở hợp lệ, khác với **<T extends Comparable<T>>** nơi bound được xây trên một interface cụ thể.

## 17. Luật trùng erasure của generic interface cần chú ý

Type erasure xóa thông tin type arguments trong nhiều vị trí của bytecode. Vì vậy những interface instantiations như **Comparable<String>** và **Comparable<Integer>** có cùng raw interface sau erasure là Comparable.

Một class không thể thừa kế hai specialization khác nhau của cùng generic interface:

~~~java
interface One extends Comparable<String> {}
interface Two extends Comparable<Integer> {}

// Không hợp lệ:
// class Both implements One, Two {}
~~~

Dạng bounds **<T extends One & Two>** cũng không tạo ra một type argument T có thể thực hiện cả hai đường kế thừa đó.

Tương tự, với lớp hoặc interface đã có hợp đồng generic, hãy tránh những phương thức chỉ khác nhau ở type arguments:

~~~java
// Cố ý sai:
class InvalidOverload {
    // void handle(java.util.List<String> a) {}
    // void handle(java.util.List<Integer> b) {}
}
~~~

Sau erasure, hai overload cùng có tham số List; compiler không thể phân biệt chúng theo chữ ký runtime cần thiết.

Đừng giải quyết bằng raw types: raw type chỉ làm giảm kiểm tra kiểu và có thể tạo unchecked warnings, không làm hai specialization trở thành hợp đồng tương thích.

## 18. Bound đầu tiên, erasure và khả năng tương thích nhị phân

Quy tắc quan trọng của Java: erasure của một type variable là erasure của **bound đầu tiên**; nếu không có bound, erasure thường là Object.

Ví dụ:

~~~java
class A<T> {
    T get() { return null; }
}

class B<T extends Number> {
    T get() { return null; }
}

class C<T extends Runnable & AutoCloseable> {
    T get() { return null; }
}
~~~

Tại mức chữ ký erasure:

| Khai báo | Erasure của T |
| --- | --- |
| A<T> | Object |
| B<T extends Number> | Number |
| C<T extends Runnable & AutoCloseable> | Runnable |

Với **C**, compiler vẫn kiểm tra cả Runnable và AutoCloseable khi dùng một giá trị T. Tuy nhiên chữ ký trong bytecode liên quan đến T dùng erasure Runnable, tức bound đầu tiên.

Điều này có thể tác động tới việc liên kết mã đã biên dịch trước đó nếu public API của một thư viện đổi thứ tự interface bounds và làm thay đổi erased method descriptor. Vì vậy khi bảo trì thư viện, **thay đổi thứ tự bounds phải được xem như một thay đổi tiềm ẩn ảnh hưởng ABI**, không nên chỉ xem như sắp xếp lại câu chữ.

Có thể dùng **javap -p -s** để khảo sát descriptors sau khi biên dịch. Cú pháp generic nhìn trong source và descriptor runtime không hoàn toàn giống nhau. Compiler có thể thêm bridge methods trong các trường hợp override generic để bảo toàn hành vi dispatch; đó là chi tiết liên quan, nhưng không thay đổi luật khai báo “class đứng trước” mà bài này tập trung.

## 19. Ví dụ chạy: AutoCloseable và Runnable đều bắt buộc

Lưu thành **CombinedResourceDemo.java**:

~~~java
public class CombinedResourceDemo {
    static final class Task implements Runnable, AutoCloseable {
        private final String name;

        Task(String name) {
            this.name = name;
        }

        @Override
        public void run() {
            System.out.println("run:" + name);
        }

        @Override
        public void close() {
            System.out.println("close:" + name);
        }
    }

    static <T extends Runnable & AutoCloseable> void execute(T task)
            throws Exception {
        try (task) {
            task.run();
        }
    }

    public static void main(String[] args) throws Exception {
        execute(new Task("compile"));
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac -encoding UTF-8 CombinedResourceDemo.java
java CombinedResourceDemo
~~~

Stdout:

~~~text
run:compile
close:compile
~~~

Ở đây **Task** implement cả Runnable và AutoCloseable. Generic method execute biết task có run() và close() nhờ hai bounds. Cấu trúc try-with-resources gọi close() sau khi thân try chạy xong. Vì AutoCloseable.close được phép ném Exception, execute và main khai báo throws Exception.

Chú ý runtime có thể có exception trong run()/close() với những implementations khác, nhưng chương trình demo này in hai dòng xác định và không ném lỗi.

## 20. Các lỗi biên dịch và ngộ nhận phổ biến

### Lỗi 1: Đặt interface trước class bound

~~~java
// Sai:
class Bad<T extends Comparable<T> & Number> {}
~~~

**Sửa:** **class Good<T extends Number & Comparable<T>> {}**.

### Lỗi 2: Khai báo nhiều class bounds

~~~java
// Sai:
class Bad<T extends Number & Thread> {}
~~~

Một type parameter chỉ có thể có tối đa một class bound, và class bound phải đứng đầu.

### Lỗi 3: Dùng dấu phẩy thay cho & để nối nhiều cận của T

~~~java
// Sai về ý nghĩa mong muốn:
// <T extends Number, Comparable<T>>
~~~

Dấu phẩy phân tách type parameters. Muốn một T có hai bounds, viết **<T extends Number & Comparable<T>>**.

### Lỗi 4: Dùng implements trong giới hạn kiểu

~~~java
// Sai:
class Bad<T implements Runnable> {}
~~~

**Sửa:** **class Good<T extends Runnable> {}**.

### Lỗi 5: Giả định thỏa một bound là đủ

~~~java
class Pair<T extends Number & Comparable<T>> {
    T value;
}
~~~

Một subclass của Number **không** implement Comparable phù hợp sẽ không thể dùng làm T. Mọi type argument đều phải thỏa **tất cả** cận.

### Lỗi 6: Dùng class final không đáp ứng interface bổ sung

~~~java
// Sai:
class Bad<T extends String & Runnable> {}
~~~

String final và không implement Runnable. Không có cách tạo subclass String để bổ sung Runnable.

### Lỗi 7: Kết hợp hai đường Comparable khác type arguments

~~~java
// Sai:
// <T extends Comparable<String> & Comparable<Integer>>
~~~

Đây là xung đột cùng generic interface với hai specializations khác nhau.

### Lỗi 8: Cho rằng interfaces khác tên luôn tương thích

Hai interfaces có thể khai báo cùng method nhưng return types mâu thuẫn, hoặc thừa kế cùng generic interface qua type arguments không tương thích. Phải xem **cả hợp đồng kế thừa, signatures và erasure**, không chỉ tên interface.

### Lỗi 9: Tưởng giới hạn Number cho phép phép cộng T

~~~java
// Không hợp lệ:
static <T extends Number> T add(T a, T b) {
    // return a + b;
    return a;
}
~~~

Đoạn được comment vì toán tử **+** số học không được định nghĩa trên type variable T. Thân mẫu chỉ trả a để code hợp lệ nếu đem biên dịch; nó không thực hiện phép cộng. Nếu muốn tính tổng bằng double, dùng **a.doubleValue() + b.doubleValue()** với kiểu trả double và chấp nhận giới hạn độ chính xác.

### Lỗi 10: Coi một bound là bảo đảm non-null

Một T extends Number vẫn có thể là null. Gọi doubleValue() khi receiver null sẽ ném NullPointerException. Bounds kiểm tra **kiểu tĩnh**, không tự ép **giá trị runtime** phải tồn tại.

### Lỗi 11: Nghĩ thứ tự interfaces luôn có thể đổi không hệ quả

Khi không có class bound, interface đầu tiên quyết định erasure của T. Đổi thứ tự có thể đổi erased descriptors, đặc biệt đáng lưu ý với thư viện đã phát hành.

### Lỗi 12: Hiểu & trong generic bound như toán tử bitwise

Ở đây **&** là dấu nối intersection bounds trong khai báo kiểu. Nó khác toán tử bitwise AND trên những giá trị số nguyên và boolean expressions.

### Lỗi 13: Cho rằng một interface bound buộc viết new T()

Bound cho phép truy cập members do bound khai báo; nó **không** bảo đảm một constructor cụ thể hay làm **new T()** hợp lệ. Khi cần tạo object, truyền Supplier<? extends T>, factory hoặc type token với hợp đồng thích hợp.

### Lỗi 14: Dùng lower bound bằng super trong type parameter declaration

~~~java
// Sai:
// class Bad<T super Number> {}
~~~

Từ khóa **super** phù hợp với **lower-bounded wildcard** như **List<? super Integer>**, không phải khai báo type parameter. Với type parameter, sử dụng **extends** cho upper bounds.

## 21. Hướng dẫn thiết kế API với nhiều bounds

Không phải lúc nào khai báo nhiều bounds cũng tốt. Hãy bắt đầu từ những thao tác thực sự cần:

- Nếu chỉ gọi **doubleValue()**, **<T extends Number>** là đủ.
- Nếu cần gọi **compareTo(T)**, cân nhắc **<T extends Comparable<T>>** hoặc biến thể **Comparable<? super T>** phù hợp.
- Nếu cần cả doubleValue và compareTo, **<T extends Number & Comparable<T>>** diễn đạt hợp đồng.
- Nếu cần hai hành vi độc lập, có thể dùng **<T extends InterfaceA & InterfaceB>**.
- Nếu thứ tự/chiến lược hành vi thay đổi theo nơi gọi, đôi khi **truyền Comparator, Function hoặc strategy object** rõ ràng và linh hoạt hơn việc bắt T implement thêm interface.
- Nếu class bound quá chặt làm API không nhận được kiểu khác hữu ích, cân nhắc thiết kế lại quanh một interface chung hoặc một adapter.

Ví dụ thay một yêu cầu Comparable bằng Comparator:

~~~java
static <T extends Number> T greater(
        T first, T second,
        java.util.Comparator<? super T> comparator) {
    return comparator.compare(first, second) >= 0 ? first : second;
}
~~~

Phương thức giữ T extends Number, nhưng hành vi so sánh được truyền vào. Caller có thể chọn comparator mà không buộc chính T phải implement Comparable<T>. Đó là lựa chọn thiết kế, không phải cách “lách” compiler: chữ ký method vẫn thể hiện đầy đủ dữ liệu và hành vi cần thiết.

### Checklist trước khi khai báo multiple bounds

1. T là một type parameter hay bạn thật ra cần nhiều type parameters riêng?
2. Các cận được nối bằng **&** và chỉ có một **extends** sau tên T?
3. Nếu có class bound, nó đã đứng đầu chưa?
4. Có vô tình đưa hai class types vào danh sách không?
5. Những interfaces có trùng các generic supertypes nhưng truyền type arguments khác nhau không?
6. Có method signatures/return types không thể cùng được hiện thực không?
7. Có một class final buộc một giao diện mà nó không implement không?
8. Type argument dự kiến như Integer, Double hay custom class có đáp ứng toàn bộ bounds không?
9. Các cận có dư thừa hoặc làm API khó dùng hơn cần thiết không?
10. Việc thay thứ tự interface bounds có làm đổi erasure của public API hiện tại không?

## 22. Bài thực hành tự kiểm tra

**Câu 1.** Vì sao **<T extends Number & Comparable<T>>** hợp lệ còn **<T extends Comparable<T> & Number>** không?

**Đáp án:** Number là class bound nên phải đứng đầu danh sách. Comparable<T> là interface và có thể đứng sau.

**Câu 2.** Có thể viết **<T extends Runnable & AutoCloseable>** không, khi không có class bound?

**Đáp án:** Có. Đây là hai interface bounds; T phải thực thi cả hai.

**Câu 3.** **<A extends Number, B extends CharSequence>** có bao nhiêu type parameters?

**Đáp án:** Hai: A và B. Dấu phẩy tách hai khai báo; nó không nối multiple bounds của một tham số.

**Câu 4.** Có thể dùng String với **<T extends Number & Comparable<T>>** không?

**Đáp án:** Không. String có Comparable<String>, nhưng không extends Number.

**Câu 5.** Một class extends Number nhưng không implement Comparable<?> có dùng được với T ở câu 4 không?

**Đáp án:** Không. Nó thiếu cận Comparable<T> tương ứng.

**Câu 6.** Nếu **<T extends Runnable & AutoCloseable>**, compiler có cho gọi run() và close() trên T không?

**Đáp án:** Có. Tuy nhiên close() theo AutoCloseable có thể ném Exception và cần được xử lý/khai báo đúng.

**Câu 7.** Vì sao **<T extends String & Runnable>** mâu thuẫn?

**Đáp án:** String final và không implement Runnable; không thể có subtype khác của String để thỏa interface.

**Câu 8.** Một type variable có thể có hai class bounds như Number & Thread không?

**Đáp án:** Không. Java chỉ cho tối đa một class bound, ở vị trí đầu tiên.

**Câu 9.** Hai interface riêng cùng kế thừa Comparable<String> và Comparable<Integer> có thể dùng đồng thời làm bounds không?

**Đáp án:** Không. Một type argument không thể có cùng generic superinterface Comparable với hai specialization khác nhau.

**Câu 10.** Với **<T extends Serializable & Comparable<T>>**, erasure của T là gì?

**Đáp án:** Serializable, vì đó là cận đầu tiên. Nếu đổi Comparable<T> lên trước, erasure thường là Comparable.

**Câu 11.** Với **<T extends Number & Comparable<T>>**, T có phải luôn khác null không?

**Đáp án:** Không. Giới hạn kiểu không bảo đảm non-null; cần kiểm tra hoặc hợp đồng runtime khác.

**Câu 12.** Bound **<T extends Comparable<T>>** có phải lỗi vòng tham chiếu vì T xuất hiện hai lần?

**Đáp án:** Không. Đây là dạng self-referential/F-bound hợp lệ, yêu cầu T có hợp đồng so sánh với T. Không đồng nghĩa với cyclic bound sai như T extends T.

**Câu 13.** Tại sao không nên thay đổi thứ tự interfaces trong một public generic API chỉ vì muốn sắp xếp lại?

**Đáp án:** Bound đầu tiên quyết định erasure, có thể ảnh hưởng chữ ký JVM và khả năng liên kết binary với mã đã biên dịch.

**Câu 14.** Nếu chỉ cần một chiến lược so sánh tùy chọn cho các Number, có nhất thiết phải dùng **<T extends Number & Comparable<T>>** không?

**Đáp án:** Không. Có thể dùng **<T extends Number>** cùng Comparator<? super T> được truyền vào, nếu đó là contract phù hợp hơn.

## 23. Tổng kết

Với nhiều giới hạn kiểu, Java dùng dạng **<T extends ClassBound & InterfaceA & InterfaceB>**. **Class bound, nếu có, bắt buộc đứng đầu; những bound tiếp theo là interface.** Điều kiện giữa các bounds là **AND**, nghĩa type argument phải đáp ứng tất cả.

Các bounds cũng phải **tương thích về hệ thống kiểu**: không được yêu cầu một final class thực hiện interface nó không hỗ trợ, không được kế thừa cùng generic interface với các type arguments mâu thuẫn và không được tạo ra những method contracts không thể cùng hiện thực. Trong source code, compiler cho phép gọi thành viên được bảo đảm bởi mọi bounds; trong bytecode, **bound đầu tiên** quyết định erasure của type variable. Hiểu cả hai tầng này giúp viết generics đúng cú pháp, an toàn kiểu và ổn định khi thiết kế API có kế thừa phức tạp.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="generic-type-bounds-005">Giới hạn tham chiếu đến tham số kiểu khác</a>

<details>
<summary>Xem chi tiết</summary>

# Giới hạn tham chiếu đến tham số kiểu khác trong Java Generics

## 1. Bài toán và mục tiêu

Trong Java, một tham số kiểu generic có thể bị giới hạn (*bounded type parameter*) bởi một lớp hoặc interface cụ thể, ví dụ `<T extends Number>`. Nhưng nhiều thuật toán cần diễn tả **quan hệ giữa hai hoặc nhiều tham số kiểu**, chứ không chỉ giới hạn từng tham số riêng rẽ:

```java
// Mảnh minh họa đặt trong một lớp Java.
static <Super, Sub extends Super> Super choose(
        boolean useSub, Super first, Sub second) {
    return useSub ? second : first;
}
```

Ở đây `Sub extends Super` nói rằng **kiểu đối số `Sub` phải là subtype của kiểu đối số `Super`**. Hai tên `Super` và `Sub` đều là tham số kiểu của cùng phương thức. Với `Super = Number`, `Sub = Integer`, compiler chứng minh rằng một `Integer` có thể dùng ở nơi yêu cầu `Number`. Nếu cố chọn `Super = String` và `Sub = Integer`, ràng buộc sẽ không hợp lệ.

Một ví dụ khác:

```java
static <T extends Comparable<? super T>> T maximum(
        java.util.Collection<? extends T> source) {
    // Triển khai đầy đủ ở phần sau.
    throw new UnsupportedOperationException();
}
```

`T` trong `Comparable<? super T>` được dùng **ngay trong giới hạn của chính `T`**. Đây là một ví dụ của giới hạn tự tham chiếu (*recursive/F-bound*), đồng thời kết hợp wildcard để cho phép một kiểu được so sánh theo giao diện do chính nó hoặc kiểu cha hỗ trợ.

Sau bài học, bạn cần có thể:

- Đọc và viết `<A, B extends A>`, `<T extends Comparable<T>>` và `<T extends Comparable<? super T>>`.
- Xác định chính xác **tham số nào là nguồn ràng buộc**, tham số nào bị giới hạn và quan hệ subtype cần chứng minh.
- Hiểu tham chiếu tới một tham số kiểu khác trong **lớp generic, phương thức generic, constructor generic và bound tự tham chiếu**.
- Phân biệt `extends` trong **khai báo type parameter** với wildcard `? extends ...`/`? super ...`.
- Biết khi nào type inference đủ thông tin, khi nào nên dùng type witness hoặc khai báo kiểu đích rõ ràng.
- Tránh nhầm quan hệ subtype với covariance của `List<T>` và tránh raw type/unchecked cast để lách lỗi giới hạn.

Bài viết dành cho Java **17+**, bao gồm các mảnh minh họa và một tệp `DependentBoundsDemo.java` hoàn chỉnh đã được biên dịch, chạy thử.

## 2. Cú pháp nền tảng: bound của một tham số kiểu

Giới hạn đơn giản thường viết:

```java
class NumericBox<T extends Number> {
    private final T value;

    NumericBox(T value) {
        this.value = value;
    }

    double asDouble() {
        return value.doubleValue();
    }
}
```

`T extends Number` nghĩa là đối số kiểu thực tế của `T` phải là `Number` hoặc subtype của `Number`. Vì vậy `NumericBox<Integer>` và `NumericBox<Double>` hợp lệ, còn `NumericBox<String>` không hợp lệ.

Dù `Number` là lớp hay giới hạn là một interface như `Comparable<T>`, Java đều dùng **từ khóa `extends`** trong danh sách khai báo type parameter. Không viết `<T implements Interface>`.

Một tham số kiểu có thể có **nhiều bound**:

```java
// Mảnh khai báo trong lớp hoặc phương thức Java.
static <T extends Number & Comparable<T>> boolean greater(T a, T b) {
    return a.compareTo(b) > 0;
}
```

Bound lớp (nếu có) phải đứng đầu, các interface đứng sau dấu `&`. Ví dụ `Integer` vừa là `Number` vừa thực hiện `Comparable<Integer>`, nên đáp ứng bound này. Không được ghép nhiều class bound: Java không hỗ trợ kế thừa nhiều lớp.

Khi bound tham chiếu đến **tham số kiểu khác** thay vì tên một class/interface cụ thể, nguyên tắc vẫn là kiểm tra quan hệ subtype ngay từ lúc biên dịch.

## 3. Hai tham số kiểu phụ thuộc nhau: `<Super, Sub extends Super>`

Ví dụ lớp generic:

```java
class Pair<Super, Sub extends Super> {
    private final Super first;
    private final Sub second;

    Pair(Super first, Sub second) {
        this.first = first;
        this.second = second;
    }

    Super first() {
        return first;
    }

    Sub second() {
        return second;
    }
}
```

`Pair<Number, Integer>` hợp lệ vì `Integer` là subtype của `Number`. `Pair<CharSequence, String>` hợp lệ vì `String` thực hiện `CharSequence`. Nhưng `Pair<String, Integer>` không hợp lệ vì `Integer` không phải subtype của `String`.

```java
// Các câu lệnh có thể đặt trong một phương thức Java.
Pair<Number, Integer> numbers = new Pair<>(3.5, 7);
Number a = numbers.first();
Integer b = numbers.second();

Pair<CharSequence, String> names = new Pair<>("base", "child");
// Pair<String, Integer> wrong = new Pair<>("text", 1); // Lỗi bound.
```

**Phân tích từng vị trí:**

| Thành phần | Ý nghĩa |
| --- | --- |
| `Super` | Tham số kiểu độc lập, chưa có bound riêng ngoài `Object` mặc định. |
| `Sub extends Super` | `Sub` phải là subtype của `Super` được chọn ở cùng lần tham số hóa. |
| `Super first` | Trường chấp nhận giá trị theo hợp đồng của `Super`. |
| `Sub second` | Trường giữ kiểu cụ thể hơn theo hợp đồng `Sub`. |
| `Super first()` | Kết quả người gọi nhận là `Super`. |
| `Sub second()` | Kết quả người gọi nhận là `Sub`; không phải ép từ `Object`. |

Quan hệ `Sub extends Super` chỉ ràng buộc **kiểu**, không khẳng định hai đối tượng trong các trường `first` và `second` có cùng giá trị. Nó cũng không yêu cầu `Sub` là **subclass trực tiếp**: một subtype qua nhiều cấp, interface implementation, hoặc cùng kiểu được chọn cho cả hai tham số cũng hợp lệ nếu các bound đáp ứng.

### Tham số kiểu bằng chính kiểu kia vẫn hợp lệ

```java
Pair<String, String> same = new Pair<>("A", "B");
```

`String` là subtype của chính nó theo quan hệ subtype phản xạ; `extends` trong bound không bắt buộc phải là một lớp con *khác* lớp cha.

## 4. Bound phụ thuộc trong phương thức generic

Không cần tạo lớp generic mới để sử dụng quan hệ `Sub extends Super`. Một phương thức thường được khai báo bên trong lớp tiện ích có thể tự sở hữu hai tham số kiểu:

```java
final class Selection {
    private Selection() {}

    static <Super, Sub extends Super> Super choose(
            boolean chooseSub, Super normal, Sub specialized) {
        return chooseSub ? specialized : normal;
    }
}
```

Phương thức chỉ cần biết giá trị `specialized` có thể dùng an toàn như một `Super`. Ví dụ:

```java
Number result =
        Selection.<Number, Integer>choose(true, 1.5, 9); // 9
```

Hai type arguments `Number` và `Integer` nằm giữa `<...>` của lời gọi phương thức: `Selection.<Number, Integer>choose(...)`. Đây gọi là **explicit type witness**, hữu ích khi muốn nhìn rõ quan hệ. Trong nhiều lời gọi, compiler có thể suy luận kiểu và người lập trình chỉ viết `Selection.choose(...)`.

Lưu ý quan trọng: Java thường có nhiều cách chọn kiểu chung hợp lệ. Nếu không chỉ định type arguments, compiler có thể suy luận một kiểu cha/giao diện rộng hơn ý muốn dựa trên các đối số và ngữ cảnh. **Đừng tự mặc định compiler luôn chọn `Number`** chỉ vì người viết đang làm việc với số. Khi chữ ký phụ thuộc nhau hoặc lỗi suy luận khó hiểu, hãy đặt kiểu đích rõ ràng hay dùng type witness như trên.

### Bound giúp gì cho thân phương thức?

Với `<Super, Sub extends Super>`, compiler cho phép:

```java
static <Super, Sub extends Super> Super promote(Sub value) {
    Super upcast = value; // Hợp lệ: Sub extends Super.
    return upcast;
}
```

Ngược lại, chiều từ `Super` sang `Sub` không được bảo đảm:

```java
// Không hợp lệ:
// static <Super, Sub extends Super> Sub downcast(Super value) {
//     return value; // Super không nhất thiết là Sub.
// }
```

Đây chính là giá trị của bound: trình biên dịch cho phép chiều chuyển kiểu được chứng minh, từ chối chiều không có bảo đảm, không cần chờ `ClassCastException` lúc chạy.

## 5. Tham chiếu đến type parameter khai báo phía sau

Java cho phép một type parameter tham chiếu đến type parameter khác nằm **phía sau** trong cùng danh sách khai báo:

```java
class Forward<Sub extends Super, Super> {
    private final Sub value;

    Forward(Sub value) {
        this.value = value;
    }

    Super readAsSuper() {
        return value;
    }
}

// Forward<Integer, Number> f = new Forward<>(7);
// Number n = f.readAsSuper();
```

`Super` vẫn thuộc cùng danh sách type parameter của `Forward`, nên bound `Sub extends Super` hợp lệ. Tương tự, phương thức generic cũng có thể viết:

```java
static <Sub extends Super, Super> Super up(Sub value) {
    return value;
}
```

Cú pháp này **được Java hỗ trợ**, nhưng cách viết `<Super, Sub extends Super>` thường **dễ đọc hơn** vì khai báo kiểu cơ sở trước rồi mới khai báo kiểu phụ thuộc. Cách bố trí thuận mắt giúp người mới nhanh nhận ra chiều của ràng buộc.

Việc cho phép forward reference không đồng nghĩa Java cho phép mọi kiểu bound vòng tròn. Một số tổ hợp bounds bất hợp lệ do luật vòng lặp hoặc do không thỏa điều kiện generic, và một số dạng F-bound hợp lệ được diễn giải ở mục 9. Hãy để compiler kiểm tra từng khai báo thay vì giả định mọi chu trình tham chiếu đều được cho phép.

## 6. Bound qua interface: một tham số kiểu dùng API do tham số khác mô tả

Một bound có thể sử dụng một interface generic mang tham số kiểu khác. Ví dụ:

```java
import java.util.function.Function;

final class Converter<Source, Target> {
    private final Function<? super Source, ? extends Target> function;

    Converter(Function<? super Source, ? extends Target> function) {
        this.function = function;
    }

    Target convert(Source value) {
        return function.apply(value);
    }
}
```

`Converter<Source, Target>` liên kết nguồn và đích bằng một generic functional interface, nhưng **chưa đặt bound subtype giữa hai tham số**. Đây là hợp đồng chuyển đổi, không phải `Target extends Source`. Hai loại quan hệ phải được phân biệt:

1. **Quan hệ subtype**: `<Parent, Child extends Parent>` khẳng định mọi giá trị `Child` có thể dùng như `Parent`.
2. **Quan hệ hàm biến đổi**: `Function<? super Source, ? extends Target>` khẳng định có một thao tác chuyển nguồn thành kết quả, dù `Source` và `Target` không có quan hệ kế thừa nào.

Nếu một kiểu cần hỗ trợ so sánh với một kiểu khác, bound có thể tham chiếu đến generic interface:

```java
// Mảnh chữ ký minh họa trong một lớp Java:
static <Other, T extends Comparable<Other>> int compare(
        T left, Other right) {
    return left.compareTo(right);
}
```

`T extends Comparable<Other>` bảo đảm `left.compareTo(right)` hợp lệ, do `left` có khả năng so sánh với `Other`. Không nhất thiết `T` phải cùng kiểu với `Other`. Khi chọn tham số, phải có implementation thật sự đáp ứng `Comparable<Other>`.

### Khác với sử dụng nhiều kiểu trong một phương thức

```java
static <A, B> String describe(A a, B b) {
    return String.valueOf(a) + "/" + String.valueOf(b);
}
```

Phương thức này chỉ khai báo hai tham số kiểu độc lập; **không có bound tham chiếu chéo**. Không phải mọi method dùng nhiều type parameters đều là phương thức có dependent bounds.

## 7. Recursive bound — tham số kiểu tự tham chiếu

Một kiểu giới hạn có thể đề cập đến chính tham số đang được khai báo:

```java
class OrderedBox<T extends Comparable<T>> {
    private final T value;

    OrderedBox(T value) {
        this.value = value;
    }

    boolean isLessThan(T other) {
        return value.compareTo(other) < 0;
    }
}
```

Ở đây `T` phải là một kiểu thực hiện `Comparable<T>`. Ví dụ `String implements Comparable<String>` và `Integer implements Comparable<Integer>`, nên `OrderedBox<String>` và `OrderedBox<Integer>` đáp ứng bound.

Mẫu `<T extends Comparable<T>>` còn được gọi là **F-bounded polymorphism** trong ngữ cảnh một kiểu được ràng buộc bởi một cấu trúc generic chứa chính nó. Một mục đích thường gặp là mô hình hóa khả năng "so sánh với cùng kiểu".

```java
OrderedBox<String> text = new OrderedBox<>("A");
boolean result = text.isLessThan("B"); // true.
```

Không nhầm bound này với cú pháp tự kế thừa trực tiếp:

```java
// Sai: một lớp không thể extends chính nó.
// class Loop extends Loop {}
```

Trong `T extends Comparable<T>`, `T` không tự extends chính mình theo nghĩa khai báo superclass; nó phải thực thi **một interface generic** mà đối số kiểu trỏ về chính `T`.

## 8. Tại sao `Comparable<? super T>` linh hoạt hơn `Comparable<T>`?

Xét quan hệ:

```java
class Animal implements Comparable<Animal> {
    private final String name;

    Animal(String name) {
        this.name = name;
    }

    @Override
    public int compareTo(Animal other) {
        return name.compareTo(other.name);
    }
}

class Dog extends Animal {
    Dog(String name) {
        super(name);
    }
}
```

`Dog` kế thừa việc so sánh của `Animal`. Vì `Animal implements Comparable<Animal>`, một `Dog` có thể được so sánh với các `Animal` khác — nhưng `Dog` **không tự nhiên trở thành `Comparable<Dog>`**. Generics Java không đồng biến: `Comparable<Animal>` và `Comparable<Dog>` là hai kiểu tham số hóa khác nhau.

Do đó, bound hẹp:

```java
// Có thể loại Dog trong ví dụ trên:
// static <T extends Comparable<T>> T max(Collection<T> items) { ... }
```

không linh hoạt bằng:

```java
static <T extends Comparable<? super T>> T max(
        java.util.Collection<? extends T> items) {
    java.util.Iterator<? extends T> iterator = items.iterator();
    if (!iterator.hasNext()) {
        throw new java.util.NoSuchElementException("Empty collection");
    }

    T best = iterator.next();
    while (iterator.hasNext()) {
        T candidate = iterator.next();
        if (candidate.compareTo(best) > 0) {
            best = candidate;
        }
    }
    return best;
}
```

Phân tích:

- `T extends Comparable<? super T>`: kiểu `T` phải có khả năng so sánh với `T` **hoặc một kiểu cha của `T`**.
- `Collection<? extends T>`: danh sách nguồn chứa `T` hoặc subtype phù hợp của `T`, nên duyệt và đọc như `T` an toàn.
- `candidate.compareTo(best)`: được compiler kiểm tra bởi bound `Comparable<? super T>`.
- Giá trị trả về là `T`: giữ lại kiểu phần tử theo hợp đồng, không ép về `Object`.

```java
// Với List<Dog> và Dog extends Animal implements Comparable<Animal>:
List<Dog> dogs = List.of(new Dog("Bim"), new Dog("An"));
Dog selected = max(dogs); // Bim vì "Bim" > "An" theo String.compareTo.
```

Đây là ví dụ **giới hạn tham chiếu đến tham số kiểu trong wildcard của một bound**. Phần `? super T` nằm **bên trong đối số kiểu của interface `Comparable`**, không phải khai báo bound "lower bound" trực tiếp cho `T`.

Nếu không cần tự viết thuật toán, `Collections.max(...)` đã cung cấp phương thức chuẩn có hợp đồng tương tự và xử lý trường hợp empty collection bằng ngoại lệ thích hợp.

## 9. F-bound ở lớp cha: `N extends Node<N>`

Một mẫu nâng cao khác là **self-referential type parameter** dành cho API fluent hoặc cây đối tượng:

```java
abstract class Node<N extends Node<N>> {
    abstract N self();

    N touch() {
        return self();
    }
}

final class TaskNode extends Node<TaskNode> {
    @Override
    TaskNode self() {
        return this;
    }

    String finish() {
        return "done";
    }
}
```

`Node<N extends Node<N>>` nghĩa là kiểu `N` phải thuộc gia đình `Node<N>`. Lớp con chọn `N = TaskNode` vì `TaskNode extends Node<TaskNode>`:

```java
TaskNode node = new TaskNode();
String result = node.touch().finish(); // "done".
```

Nhờ bound, `touch()` trả `N` thay vì `Node<?>`, cho phép code sử dụng tiếp các phương thức của lớp cụ thể mà không cast.

**Giới hạn cần hiểu:** Mẫu này không tự chứng minh tại runtime rằng mọi subtype sẽ chọn chính nó làm type argument. Java cho phép một số cách kế thừa không đúng ý đồ của nhà thiết kế nếu không kiểm soát hierarchy. Khi cần nghiêm ngặt, dùng `final` cho concrete subclasses hoặc thiết kế interface/factory phù hợp, đồng thời cân nhắc tính phức tạp của self-bound.

### Bound tự tham chiếu của enum

Java khai báo khái niệm enum theo kiểu cơ sở `Enum<E extends Enum<E>>`. Một generic method làm việc với enum có thể dùng bound:

```java
static <E extends Enum<E>> String enumName(E value) {
    return value.name();
}
```

`E` vừa là kiểu của enum cụ thể vừa được tham chiếu lại trong `Enum<E>`. Ví dụ với `enum Status { NEW, DONE }`:

```java
// enumName(Status.DONE) trả "DONE".
```

Đây là ứng dụng phổ biến của F-bound trong thư viện chuẩn, không phải thủ thuật riêng của một dự án.

## 10. Bound của lớp, phương thức và constructor

Dependent bounds được khai báo trong **danh sách tham số kiểu phù hợp với từng phạm vi**.

### 10.1. Ở lớp generic

```java
class ParentChild<Parent, Child extends Parent> {
    private final Child child;

    ParentChild(Child child) {
        this.child = child;
    }

    Parent asParent() {
        return child;
    }

    Child child() {
        return child;
    }
}
```

`Parent` và `Child` thuộc phạm vi của lớp; các phương thức instance sử dụng chúng mà không cần khai báo lại `<Parent, Child>`.

### 10.2. Ở phương thức generic trong lớp thông thường

```java
class RelationalTools {
    static <Base, Derived extends Base> Base viewAsBase(Derived item) {
        return item;
    }
}
```

Hai tham số chỉ thuộc phương thức; một lớp không generic vẫn hoàn toàn được phép khai báo method này. Vì method `static` có tham số kiểu riêng, nó không phụ thuộc type argument của lớp bao quanh.

### 10.3. Ở constructor generic

```java
class Description {
    private final String text;

    <Base, Derived extends Base> Description(
            Base general, Derived specialized) {
        this.text = general + " -> " + specialized;
    }

    String text() {
        return text;
    }
}
```

`Description` không phải generic class, nhưng constructor **tự khai báo** `<Base, Derived extends Base>`. Quan hệ subtype được kiểm tra ở mỗi lời gọi constructor. Một constructor trong `class Holder<T>` có thể kết hợp `T` của lớp với tham số kiểu mới:

```java
class Holder<T> {
    private final T value;

    <S extends T> Holder(S source) {
        this.value = source;
    }

    T get() {
        return value;
    }
}

// Holder<Number> h = new Holder<>(7); // S có thể được suy luận là Integer.
```

Ở đây `T` thuộc **lớp** `Holder<T>`; `S` thuộc **constructor**, bị bound bởi `T`. Không cần khai báo lại `<T>` cho constructor: điều đó sẽ tạo một tên trùng che khuất `T` lớp.

Hãy chú ý constructor generic cần type parameter list đặt trước tên constructor, không có kiểu trả về.

## 11. Suy luận kiểu và quan hệ bất biến của generic containers

Khi nhiều tham số phụ thuộc nhau, compiler giải một tập ràng buộc tại mỗi lời gọi. Có thể viết:

```java
// Lớp có static <Base, Item extends Base> List<Base> widen(...):
List<Number> values =
        DependentBoundsDemo.<Number, Integer>widen(List.of(1, 2));
```

Ở đây caller chỉ rõ `Base = Number` và `Item = Integer`; compiler xác minh `Integer extends Number` rồi trả `List<Number>`.

Trong khi đó, phép gán sau là **sai**:

```java
List<Integer> ints = List.of(1, 2);
// List<Number> nums = ints; // Sai: List<Integer> không phải List<Number>.
```

Quan hệ `Integer extends Number` không làm `List<Integer> extends List<Number>`. Nếu Java cho phép, người dùng `List<Number>` có thể đưa `Double` vào một đối tượng vốn hứa chỉ chứa `Integer`.

### Dùng wildcard khi nguồn chỉ sản xuất dữ liệu

```java
static <Base, Item extends Base> List<Base> widen(
        java.util.Collection<? extends Item> items) {
    java.util.List<Base> result = new java.util.ArrayList<>();
    result.addAll(items);
    return result;
}
```

`Collection<? extends Item>` bảo đảm các phần tử có thể đọc như `Item`; `Item extends Base` tiếp tục cho phép đưa từng phần tử vào `List<Base>`. Đây là cách phối hợp **dependent type bound** và **upper-bounded wildcard** mà không ép kiểu không kiểm tra được.

Bound ở `<Base, Item extends Base>` tạo quan hệ giữa **các type parameters**; wildcard `? extends Item` ở tham số phương thức tạo điều kiện cho **một giá trị collection cụ thể** được truyền vào. Cả hai cùng dùng từ khóa `extends` nhưng phạm vi và tác dụng khác nhau.

### Type witness và khả năng suy luận

Một lời gọi như `widen(List.of(1, 2))` có thể được compiler suy luận tùy ngữ cảnh; đôi khi kiểu `Base` được chọn thành kiểu rộng hoặc khác dự đoán. Để bảo vệ hợp đồng dễ đọc, đặt kiểu vế trái rõ hoặc dùng type witness `ClassName.<Number,Integer>method(...)`. Không nên dựa vào raw `List` hay cast unchecked để cố ép compiler chọn kết quả.

## 12. Thứ tự bound và những cú pháp không hợp lệ

Bảng so sánh các cách viết:

| Khai báo | Trạng thái | Ý nghĩa |
| --- | --- | --- |
| `<A, B extends A>` | Hợp lệ | `B` là subtype của `A`. |
| `<B extends A, A>` | Hợp lệ | Tham chiếu forward đến `A` được khai báo trong cùng danh sách. |
| `<T extends Comparable<T>>` | Hợp lệ | `T` hỗ trợ so sánh với chính `T`. |
| `<T extends Comparable<? super T>>` | Hợp lệ | `T` hỗ trợ so sánh với `T` hoặc một supertype. |
| `<E extends Enum<E>>` | Hợp lệ | Recursive bound thông dụng của enum. |
| `<A, B super A>` | Không hợp lệ | Khai báo type parameter không dùng `super` như wildcard. |
| `<A, B implements A>` | Không hợp lệ | Bound dùng `extends`, không dùng `implements`. |
| `<T extends Number & Comparable<T>>` | Hợp lệ | Bound lớp rồi đến bound interface. |
| `<T extends Comparable<T> & Number>` | Không hợp lệ | Nếu bound lớp là `Number`, nó phải đứng trước interface. |
| `<T extends String>` | Cú pháp hợp lệ nhưng không có type argument reference hợp lệ | `String` là final; không thể tạo subtype mới ngoài chính `String`. Dùng `T = String` vẫn hợp lệ. |

**Lưu ý về hàng cuối:** `<T extends String>` là một khai báo hợp lệ về mặt generic bounds (dù rất ít hữu ích), vì `T` vẫn có thể được gán là chính `String`. Không được diễn giải `extends` bắt buộc strict subtype; `String` thỏa bound `String` của chính nó.

Ví dụ về nhiều bound phụ thuộc:

```java
// Đây là chữ ký minh họa.
static <T extends Number & Comparable<T>, U extends T>
        T chooseNumber(T general, U specific) {
    return specific;
}
```

`T` trước hết phải là một subtype của `Number` và thực hiện `Comparable<T>`; tiếp đó `U` phải là subtype của `T`. Mỗi bound thêm một điều kiện, nhưng không thay đổi ý nghĩa `U extends T`.

Nếu compiler báo lỗi `type argument is not within bounds`, hãy kiểm tra đối số kiểu của từng tham số theo thứ tự. Ví dụ `Pair<String,Integer>` thất bại vì `Integer` không nằm trong miền mà bound `Sub extends Super` cho phép khi `Super = String`.

## 13. Hạn chế: raw type, type erasure, static context

Giới hạn kiểu được kiểm tra lúc biên dịch. Java generics chủ yếu dùng **type erasure**, nên runtime thường không giữ một lớp riêng cho mỗi cách tham số hóa `Pair<Number,Integer>` hoặc `Pair<CharSequence,String>`.

```java
// Những kiểu chung không thể trực tiếp được tạo hay kiểm tra ở runtime:
// T value = new T();            // Sai với T là type parameter.
// Class<T> clazz = T.class;     // Sai.
// if (object instanceof Pair<Number, Integer>) { } // Sai: tham số không reifiable.
```

Có thể kiểm tra `object instanceof Pair<?, ?>` nếu cần xác nhận dạng lớp, nhưng phép kiểm tra ấy không chứng minh các đối số kiểu cụ thể hay kiểu dữ liệu thực trong từng trường. Khi tiếp nhận dữ liệu từ nguồn ngoài, vẫn phải xác thực các giá trị nếu rủi ro dữ liệu sai kiểu là có thật.

### Raw type có thể phá lời hứa của bound

```java
Pair<Number, Integer> correct = new Pair<>(1.5, 4);
// Pair raw = correct; // Dạng raw làm mất thông tin về hai type arguments.
```

Đừng dùng `Pair` raw rồi cast về `Pair<String,Integer>` để vượt qua giới hạn compiler. Raw type và unchecked conversion có thể tạo **heap pollution**, dẫn đến `ClassCastException` tại nơi đọc giá trị sau này. Bound không phải cơ chế kiểm tra dữ liệu runtime thay thế cho validation.

### Static member không mượn được type parameter của lớp

```java
class Container<Base, Item extends Base> {
    // static Item last; // Không hợp lệ: Item thuộc lớp, không thuộc static member.

    static <X, Y extends X> X promote(Y value) {
        return value;
    }
}
```

`Base`/`Item` là tham số kiểu của `Container`, nên dùng trong instance fields/methods. Một static generic method phải **tự khai báo `<X,Y extends X>`**; hai tham số mới độc lập với tham số kiểu của lớp.

## 14. Chương trình Java 17+ hoàn chỉnh, đã kiểm thử

Tạo tệp **`DependentBoundsDemo.java`** đúng nội dung dưới đây. Mã minh họa:

- Bound giữa hai type parameters trên lớp `Pair<Super, Sub extends Super>`.
- Lớp con cố định type arguments `NumberPair extends Pair<Number,Integer>`.
- Phương thức generic `choose` và `widen` dùng `Sub extends Super`.
- Giới hạn tự tham chiếu `Comparable<? super T>` qua lớp `Dog extends Animal implements Comparable<Animal>`.
- F-bound `E extends Enum<E>` và `N extends Self<N>`.

```java
import java.util.*;

public class DependentBoundsDemo {
    static class Animal implements Comparable<Animal> {
        private final String name;

        Animal(String name) { this.name = name; }
        String name() { return name; }

        @Override
        public int compareTo(Animal other) {
            return name.compareTo(other.name);
        }
    }

    static final class Dog extends Animal {
        Dog(String name) { super(name); }
    }

    static class Pair<Super, Sub extends Super> {
        private final Super first;
        private final Sub second;

        Pair(Super first, Sub second) {
            this.first = first;
            this.second = second;
        }

        Super first() { return first; }
        Sub second() { return second; }
    }

    static final class NumberPair extends Pair<Number, Integer> {
        NumberPair(Number first, Integer second) {
            super(first, second);
        }
    }

    static <Super, Sub extends Super> Super choose(
            boolean useSub, Super first, Sub second) {
        return useSub ? second : first;
    }

    static <T extends Comparable<? super T>> T max(
            Collection<? extends T> source) {
        Iterator<? extends T> iterator = source.iterator();
        if (!iterator.hasNext()) {
            throw new NoSuchElementException("empty");
        }

        T best = iterator.next();
        while (iterator.hasNext()) {
            T value = iterator.next();
            if (value.compareTo(best) > 0) {
                best = value;
            }
        }
        return best;
    }

    static <E extends Enum<E>> String enumName(E value) {
        return value.name();
    }

    enum Status { NEW, DONE }

    static abstract class Self<N extends Self<N>> {
        abstract N self();

        N fluent() {
            return self();
        }
    }

    static final class Task extends Self<Task> {
        @Override
        Task self() { return this; }

        String finish() { return "done"; }
    }

    static <Base, Item extends Base> List<Base> widen(
            Collection<? extends Item> items) {
        List<Base> result = new ArrayList<>();
        result.addAll(items);
        return result;
    }

    public static void main(String[] args) {
        Pair<Number, Integer> pair = new Pair<>(3.5, 7);
        System.out.println("pair: " + pair.first() + " / " + pair.second());

        System.out.println("choice: " +
                DependentBoundsDemo.<Number, Integer>choose(true, 1.5, 9));

        List<Dog> dogs = List.of(new Dog("Bim"), new Dog("An"));
        System.out.println("max dog: " + max(dogs).name());

        System.out.println("enum: " + enumName(Status.DONE));
        System.out.println("self: " + new Task().fluent().finish());

        List<Number> numbers =
                DependentBoundsDemo.<Number, Integer>widen(List.of(2, 4));
        System.out.println("widen: " + numbers);

        NumberPair np = new NumberPair(1.2, 3);
        System.out.println("fixed: " + np.second());
    }
}
```

Biên dịch và chạy trên JDK 17+:

```bash
javac --release 17 -Xlint:all DependentBoundsDemo.java
java DependentBoundsDemo
```

**Stdout chính xác** (đã chạy với javac tương thích `--release 17`, không có cảnh báo từ `-Xlint:all`):

```text
pair: 3.5 / 7
choice: 9
max dog: Bim
enum: DONE
self: done
widen: [2, 4]
fixed: 3
```

Diễn giải kết quả:

1. `Pair<Number,Integer>` lưu `3.5` như `Number` và `7` như `Integer`, do bound `Integer extends Number`.
2. Type witness `<Number,Integer>` cho `choose` thể hiện rõ quan hệ, hàm trả nhánh thứ hai là `9` dưới kiểu kết quả `Number`.
3. `Dog` kế thừa `Comparable<Animal>` qua `Animal`; bound `Comparable<? super Dog>` phù hợp, nên `max(dogs)` trả một `Dog`. `"Bim"` lớn hơn `"An"` theo `String.compareTo`.
4. `Status.DONE` là giá trị enum thỏa `E extends Enum<E>`; `enumName` trả tên hằng `DONE`.
5. `Task extends Self<Task>` nên `fluent()` có kiểu `Task`; lời gọi nối tiếp `finish()` thành công.
6. `widen` sao chép nguồn `Integer` thành `List<Number>` mới. Nó không cố ép một `List<Integer>` hiện có thành `List<Number>`.
7. `NumberPair` kế thừa lớp generic bằng đối số kiểu cố định và getter `second()` trả `Integer`.

## 15. Các lỗi thường gặp và cách sửa

| Biểu hiện | Giải thích | Cách xử lý |
| --- | --- | --- |
| `Pair<String,Integer>` báo vi phạm bound | `Integer` không phải subtype của `String` | Chọn type arguments thực sự có quan hệ subtype. |
| `<Super, Sub super Super>` không biên dịch | Bound khai báo type parameter không dùng `super` | Viết `<Super, Sub extends Super>`. |
| `<T implements Comparable<T>>` không biên dịch | Bound của type parameter dùng `extends` ngay cả cho interface | Viết `<T extends Comparable<T>>`. |
| `List<Integer>` không gán cho `List<Number>` | Generic type arguments là invariant | Dùng `List<? extends Number>` để đọc, hoặc copy sang list đích phù hợp. |
| `<T extends Comparable<T>>` từ chối một số subtype | Kiểu con có thể chỉ thực hiện `Comparable<Supertype>` | Cân nhắc `<T extends Comparable<? super T>>`. |
| `static Sub method(...)` bên trong `class Pair<Super,Sub>` lỗi | Static member không tham chiếu type parameter lớp | Khai báo type parameters mới trên static method. |
| Cố `new T()` từ generic method | Erasure không cung cấp constructor của `T` | Nhận `Supplier<? extends T>`/factory. |
| Dùng raw `Pair` để bỏ cảnh báo | Xóa kiểm tra generic thay vì giải đúng ràng buộc | Giữ kiểu tham số hóa; kiểm tra dữ liệu legacy ở boundary. |
| Giả định `Sub extends Super` bắt buộc là subclass trực tiếp | Subtype có thể theo nhiều cấp hoặc cùng kiểu | Dựa vào quan hệ subtype mà compiler kiểm tra. |
| Đọc `Comparable<? super T>` thành “T có super bound” | `super` thuộc wildcard bên trong `Comparable` | Phân tách từng lớp cú pháp: bound `extends Comparable<...>` và wildcard `? super T`. |
| Bound vòng tròn tùy ý dẫn đến lỗi | Không phải mọi chu trình phụ thuộc đều hợp lệ | Dùng mẫu F-bound đã hiểu rõ, tránh thiết kế type hierarchy quá phức tạp. |
| Compiler suy luận kiểu đích khác dự định | Nhiều cách gán type arguments thỏa bounds | Đặt type đích rõ hoặc explicit type witness. |

### Quy trình debug lời gọi có dependent bounds

1. Xác định danh sách khai báo type parameters: nằm sau tên lớp, trước tên phương thức/constructor, hay bên trong một interface generic?
2. Vẽ quan hệ `Sub <: Super` cho `Sub extends Super`, và xác định kiểu cụ thể mà caller muốn truyền.
3. Với `T extends Comparable<? super T>`, kiểm tra kiểu `T` thực hiện `Comparable` của chính nó hay của một supertype.
4. Nếu có collection, tách **quan hệ type parameter** khỏi **quan hệ tham số hóa collection**: `List<Integer>` khác `List<Number>`.
5. Nếu inference khó hiểu, thêm type witness tại lời gọi hoặc khai báo kiểu kết quả tường minh; đọc compiler diagnostic thay vì che lỗi bằng cast.
6. Với code legacy/raw, xác thực dữ liệu trước khi chuyển vào API generic để không phát tán heap pollution.

## 16. Bài tập thực hành

**Bài 1 — Bound hai tham số kiểu.** Viết `class Holder<Base, Child extends Base>` với `Child get()` và `Base asBase()`. Tạo `Holder<Number,Integer>`, thử `Holder<CharSequence,String>` và phân tích vì sao `Holder<String,Integer>` thất bại.

**Bài 2 — Forward reference.** Viết một class `Forward<A extends B,B>` và phương thức `static <A extends B,B> B promote(A input)`. Biên dịch Java 17 và giải thích tại sao việc `B` xuất hiện phía sau vẫn được chấp nhận. Sau đó đổi thứ tự thành `<B,A extends B>` để tăng khả năng đọc.

**Bài 3 — Recursive bound.** Viết `static <T extends Comparable<? super T>> T min(Collection<? extends T> values)`, xử lý collection rỗng rõ ràng. Kiểm tra với `List<Integer>`, `List<String>` và subclass của một lớp cha thực hiện `Comparable<Cha>`.

**Bài 4 — Enum F-bound.** Viết `static <E extends Enum<E>> boolean sameName(E a, E b)` so sánh `a.name()` và `b.name()`. Giải thích rằng `E` buộc hai tham số có cùng kiểu enum tại chữ ký (caller không truyền hai enum khác nhau theo cách thông thường với một `E` duy nhất).

**Bài 5 — Self-bound.** Viết `abstract class Fluent<S extends Fluent<S>>` có phương thức `S configure()` dựa vào `S self()`; tạo concrete class `Query extends Fluent<Query>`. Kiểm tra `new Query().configure().execute()` khi `execute()` thuộc `Query`.

**Bài 6 — Invariance và copy.** Tạo `List<Integer> input = List.of(1,2,3)`. Chứng minh không gán trực tiếp vào `List<Number>`; dùng phương thức `widen` với `<Number,Integer>` để nhận một list số kiểu rộng hơn. Hãy thử thêm `Double` vào kết quả và giải thích vì sao điều này không thay đổi nguồn ban đầu.

**Bài 7 — Thiết kế API.** Một hệ thống có `class Animal` và `class Dog extends Animal`. Bạn cần hàm nhận nguồn `Collection<Dog>` và xuất `List<Animal>`. So sánh chữ ký dùng hai type parameters `<Base,Derived extends Base>` với chữ ký chỉ dùng `Collection<? extends Animal>`. Khi nào bound phụ thuộc thực sự đem lại lợi ích, khi nào chỉ thêm phức tạp?

## 17. Tổng kết

**Giới hạn tham chiếu đến tham số kiểu khác** cho phép diễn tả quan hệ mà Java có thể chứng minh ở thời điểm biên dịch:

- `<A, B extends A>`: `B` là subtype hợp lệ của `A`; dùng trong class, generic method hoặc constructor.
- `<T extends Comparable<T>>`: `T` được so sánh với chính kiểu `T` theo giao diện.
- `<T extends Comparable<? super T>>`: phiên bản linh hoạt hơn khi một kiểu thừa hưởng khả năng so sánh với supertype.
- `<E extends Enum<E>>`, `<N extends Node<N>>`: các **F-bounds** tự tham chiếu, hữu ích cho enum và self-typed APIs.
- Dependent bound và wildcard liên quan nhưng không giống nhau; Java generics mặc định **invariant**, nên `List<Derived>` không tự là `List<Base>`.
- Type inference, erasure và static context không xóa bỏ nghĩa của bound: hãy dùng kiểu khai báo chính xác và để compiler bảo vệ quan hệ subtype.

Khi gặp chữ ký generic khó đọc, xác định từng tham số, từng bound và hướng dữ liệu đi qua API. Câu hỏi quan trọng nhất là **“Giá trị thuộc type parameter này có thể được dùng một cách an toàn ở vị trí yêu cầu type parameter kia hay không?”**. Nếu biểu diễn được câu trả lời bằng bound như `Sub extends Super` hoặc `T extends Comparable<? super T>`, bạn đang tận dụng generics đúng mục đích: **tái sử dụng thuật toán mà vẫn giữ an toàn kiểu**.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="generic-type-bounds-006">Kiểu giao và ý nghĩa đối với truy cập thành viên</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểu giao (intersection type) và quyền truy cập thành viên trong Java Generics

Trong Java Generics, một tham số kiểu không nhất thiết chỉ bị giới hạn bởi một lớp hoặc interface. Ta có thể yêu cầu kiểu thực tế **đồng thời thỏa nhiều giới hạn** (*multiple bounds*) bằng toán tử `&`. Các ràng buộc này tạo nên một **kiểu giao (intersection type)**: một giá trị phải phù hợp với tất cả các thành phần của giao.

Ví dụ:

```java
interface Named {
    String name();
}

interface Versioned {
    int version();
}

static <T extends Named & Versioned> String describe(T item) {
    return item.name() + "@" + item.version();
}
```

Hai interface và phương thức trên là **mã minh họa**, cần đặt trong class phù hợp trước khi biên dịch. Chữ ký `<T extends Named & Versioned>` bảo đảm rằng **bất kỳ kiểu thực tế `T` nào được chấp nhận** đều có thể dùng qua cả hợp đồng `Named` và `Versioned`. Vì vậy, trong thân phương thức, compiler cho gọi cả `item.name()` lẫn `item.version()` mà không cần cast.

**Cốt lõi:** với một biến kiểu `T extends A & B`, **tập thành viên có thể truy cập lúc biên dịch được quyết định bởi những thành viên hợp lệ của các giới hạn `A` và `B`**, cùng các quy tắc truy cập, kế thừa và giải quyết phương thức của Java. Không phải mọi phương thức của lớp runtime đều tự nhiên được nhìn thấy.

## 1. Intersection type nghĩa là gì?

Trong lý thuyết kiểu của Java, giao `A & B` biểu thị yêu cầu một giá trị phải đồng thời phù hợp với `A` **và** `B`. Nó không tạo ra một interface Java mới mang tên `A&B`; đó là **mô tả kiểu kết hợp** trong những ngữ cảnh cú pháp cho phép.

Hãy tưởng tượng:

- `Named` là giao diện cấp khả năng `name()`.
- `Versioned` là giao diện cấp khả năng `version()`.
- `Document` là lớp triển khai cả `Named` và `Versioned`.

Khi `Document implements Named, Versioned`, một đối tượng `Document` có thể được dùng tại vị trí yêu cầu `Named`, tại vị trí yêu cầu `Versioned` hoặc tại vị trí một type parameter phải thỏa **đồng thời** hai interface.

```java
class Document implements Named, Versioned {
    @Override
    public String name() {
        return "Java";
    }

    @Override
    public int version() {
        return 17;
    }
}
```

Đây là **định nghĩa lớp minh họa** dựa trên hai interface đã khai báo. `Document` thỏa ràng buộc `<T extends Named & Versioned>`; còn một lớp chỉ `implements Named` thì không thỏa, dù nó có phương thức `name()`.

Phân biệt với kế thừa thông thường:

| Khai báo | Ràng buộc compile-time | Có thể gọi trên biến kiểu `T` |
|---|---|---|
| `<T>` | Không có bound tường minh; upper bound ngầm là `Object` | Những thành viên `Object` truy cập được |
| `<T extends Named>` | `T` phù hợp với `Named` | `name()` và các thành viên hợp lệ từ bound |
| `<T extends Named & Versioned>` | `T` phù hợp với cả hai | `name()` **và** `version()` |
| `<T extends Asset & Named & Versioned>` | `T` là subtype của `Asset` và phù hợp hai interface | Thành viên truy cập được của cả `Asset`, `Named`, `Versioned` |

**Chú ý:** `extends` trong khai báo generic bound dùng chung cho cả **class bound** và **interface bounds**, kể cả khi Java dùng từ khóa `implements` ở khai báo `class Document implements Named`.

## 2. Nhận diện cú pháp intersection để phân tích kiểu

Một type parameter dùng `&` để kết hợp nhiều upper bounds:

```java
<T extends Named & Versioned>
<T extends Asset & Named & Versioned>
```

Đây là các **mẫu khai báo** đặt vào class hoặc generic method phù hợp, không phải statements Java độc lập. Nếu `Asset` là class, nó phải đứng đầu; các bound còn lại là interfaces. `T` cần thỏa **tất cả** bounds. Dấu phẩy `,` dùng để tách **các type parameters khác nhau**, còn `&` biểu diễn intersection của những yêu cầu trên **một** parameter.

Trong bài này, điều quan trọng hơn cú pháp là: tại mỗi vị trí sử dụng `T`, compiler cho phép gọi những member nào từ `Named`, `Versioned` và `Asset`? Ví dụ, chỉ `<T extends Named & Versioned>` **không cho gọi** một `id()` riêng của `Asset`, dù object thực có thể là subclass của `Asset`. Phải ghi rõ bound cung cấp method đó và tuân thủ access modifiers.

Giới hạn phải tương thích; các interface có signature/return type xung đột hoặc các instantiations không tương thích của cùng generic interface không tạo ra intersection hợp lệ. Khi xét runtime, phân biệt **intersection bound ở khai báo** với **intersection cast** có kiểm tra kiểu tương ứng.

## 3. Tại sao intersection bound thay đổi quyền truy cập thành viên?

Xét hai method nhận cùng loại object thực tế nhưng có hợp đồng kiểu khác nhau:

```java
static <T extends Named> String oneCapability(T item) {
    return item.name();
}

static <T extends Named & Versioned> String twoCapabilities(T item) {
    return item.name() + "@" + item.version();
}
```

Hai phương thức là **đoạn minh họa** cần đặt trong class nhìn thấy `Named` và `Versioned`.

Trong `oneCapability`, compiler chỉ có bảo đảm `T extends Named`. Nó cho gọi `name()`, nhưng không có cơ sở cho phép `item.version()`, **ngay cả khi người gọi sau này truyền một `Document` có cả hai method**.

Trong `twoCapabilities`, compiler biết đồng thời hai giới hạn nên chấp nhận cả hai lời gọi. Đây là khác biệt giữa **kiểu tĩnh** và **lớp runtime**:

- Kiểu runtime của object có thể là `Document`, `Deployment` hoặc bất kỳ lớp triển khai đủ interface.
- **Kiểu tĩnh `T` cùng các bounds trong chữ ký** quyết định method nào được gọi hợp lệ trong thân generic method.
- Runtime dynamic dispatch vẫn chọn đúng phần triển khai của object khi method được gọi.

Mã sau **không biên dịch**, dù lời gọi thực tế có thể chỉ truyền `Document`:

```java
// static <T extends Named> int invalid(T item) {
//     return item.version(); // Không có version() trong bound Named.
// }
```

Giải pháp tốt là bổ sung `& Versioned` khi API **thực sự cần** cả hai khả năng, hoặc nhận một interface duy nhất đã mô tả đầy đủ hợp đồng. Không dùng cast tùy tiện bên trong generic method để “sửa” một bound chưa đủ mạnh: cast sẽ chuyển lỗi từ compile-time sang runtime nếu đối tượng không thỏa điều kiện.

## 4. Class bound đứng đầu, interface bounds theo sau

Khi cần sử dụng đồng thời hành vi của một lớp cơ sở và nhiều interface:

```java
class Asset {
    private final String id;

    Asset(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }
}

interface Named {
    String name();
}

interface Versioned {
    int version();
}

static <T extends Asset & Named & Versioned> String describe(T item) {
    return item.id() + ":" + item.name() + "@" + item.version();
}
```

Đây là **một cụm khai báo minh họa**: các interface, class và method cần nằm ở các vị trí hợp lệ (method trong class) trước khi biên dịch thành một chương trình. Phần giới hạn có ba khả năng:

- `Asset` cho phép truy cập `id()`.
- `Named` cho phép truy cập `name()`.
- `Versioned` cho phép truy cập `version()`.

Một kiểu thực tế `Document extends Asset implements Named, Versioned` thỏa tất cả. Nhưng `Document` không nhất thiết là lớp duy nhất thỏa ràng buộc đó.

**Không thể đảo thành `<T extends Named & Asset & Versioned>`** nếu `Asset` là class; thứ tự class-first là quy tắc cú pháp/kiểu của Java, không chỉ quy tắc trình bày. Nếu các bound đều là interface, Java cho phép đặt một interface ở vị trí đầu, song **thứ tự vẫn có thể quan trọng với erasure và binary compatibility** (xem phần 9).

Các quyền truy cập trong thân generic method còn bị chi phối bởi Java access control: `private`, package-private, `protected`, `public` không bị vô hiệu hóa bởi kiểu giao. Ví dụ một trường `private` trong `Asset` không tự truy cập được từ một class khác chỉ vì `T extends Asset`. Dùng API truy cập hợp lệ như `id()`.

## 5. Ví dụ Java 17 chạy độc lập

Lưu toàn bộ chương trình sau vào tệp **`IntersectionBoundsDemo.java`**:

```java
import java.util.ArrayList;
import java.util.List;

public class IntersectionBoundsDemo {

    interface Named {
        String name();
    }

    interface Versioned {
        int version();
    }

    static class Asset {
        private final String id;

        Asset(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }
    }

    static final class Document extends Asset
            implements Named, Versioned {
        private final String title;
        private final int revision;

        Document(String id, String title, int revision) {
            super(id);
            this.title = title;
            this.revision = revision;
        }

        @Override
        public String name() {
            return title;
        }

        @Override
        public int version() {
            return revision;
        }
    }

    static final class Deployment implements Named, Versioned {
        private final String environment;
        private final int release;

        Deployment(String environment, int release) {
            this.environment = environment;
            this.release = release;
        }

        @Override
        public String name() {
            return environment;
        }

        @Override
        public int version() {
            return release;
        }
    }

    static final class NamedOnly implements Named {
        private final String text;

        NamedOnly(String text) {
            this.text = text;
        }

        @Override
        public String name() {
            return text;
        }
    }

    static <T extends Named & Versioned> String label(T item) {
        return item.name() + "#" + item.version();
    }

    static <T extends Asset & Named & Versioned>
            String describe(T item) {
        return item.id() + ":" + item.name() + "@"
                + item.version();
    }

    static <T extends Named & Versioned>
            List<String> labels(List<T> items) {
        List<String> result = new ArrayList<>();
        for (T item : items) {
            result.add(label(item));
        }
        return List.copyOf(result);
    }

    static <T extends Number & Comparable<T>> T larger(T a, T b) {
        return a.compareTo(b) >= 0 ? a : b;
    }

    public static void main(String[] args) {
        Document first = new Document("doc-7", "Generics", 3);
        Document second = new Document("doc-8", "Streams", 2);
        Deployment release = new Deployment("prod", 5);

        System.out.println("full: " + describe(first));
        System.out.println("interfaces: " + label(release));

        Named namedView = first;
        System.out.println("named view: " + namedView.name());
        System.out.println("cast version: "
                + ((Named & Versioned) namedView).version());

        System.out.println("bulk: " + labels(List.of(first, second)));
        System.out.println("larger: " + larger(3, 7));

        Object oneCapability = new NamedOnly("solo");
        System.out.println("has both: "
                + (oneCapability instanceof Named
                && oneCapability instanceof Versioned));

        try {
            ((Named & Versioned) oneCapability).name();
            System.out.println("unexpected");
        } catch (ClassCastException exception) {
            System.out.println("cast failed: ClassCastException");
        }
    }
}
```

Chạy trong thư mục chứa tệp, với JDK 17 hoặc mới hơn:

```bash
javac IntersectionBoundsDemo.java
java IntersectionBoundsDemo
```

**Kết quả stdout:**

```text
full: doc-7:Generics@3
interfaces: prod#5
named view: Generics
cast version: 3
bulk: [Generics#3, Streams#2]
larger: 7
has both: false
cast failed: ClassCastException
```

### Phân tích cơ chế theo output

1. **`describe(first)` yêu cầu ba bounds:** `Document` là subtype của `Asset` đồng thời triển khai `Named` và `Versioned`. Thân `describe` gọi `id()`, `name()`, `version()` hợp lệ; kết quả `doc-7:Generics@3`.
2. **`label(release)` chỉ yêu cầu hai interface:** `Deployment` không kế thừa `Asset` nhưng vẫn hợp lệ với `<T extends Named & Versioned>`. Nó trả `prod#5`. Nếu gọi `describe(release)`, compiler sẽ từ chối vì `Deployment` không thỏa class bound `Asset`.
3. **`Named namedView = first;` làm mất quyền truy cập tĩnh vào `version()`:** qua biến này chỉ nhìn thấy hợp đồng `Named`. Lời gọi `namedView.name()` hợp lệ; `namedView.version()` sẽ không biên dịch dù object thực tế là `Document`.
4. **`((Named & Versioned) namedView)` là intersection cast:** nó yêu cầu runtime object phù hợp **cả hai** interface; `first` thỏa điều kiện và cho gọi `version()`, in `3`.
5. **`labels(List.of(first, second))` giữ `T = Document`:** danh sách các `Document` thỏa hai bounds; vòng lặp trong method generic gọi được cả `name()` và `version()`, trả danh sách chuỗi `[Generics#3, Streams#2]`.
6. **`larger(3, 7)` thể hiện giao giữa class và generic interface:** `T` được suy luận thành `Integer` qua boxing; `Integer` là subtype của `Number` và triển khai `Comparable<Integer>`. `compareTo` chọn `7`.
7. **`NamedOnly` chỉ thỏa một bound:** biểu thức `instanceof Named && instanceof Versioned` trả `false`. Intersection cast ở ví dụ cuối không đạt đủ hai kiểm tra runtime và ném `ClassCastException`, được bắt để in một dòng ổn định.

**Phân biệt rõ:** các type bounds trong generic method giúp kiểm tra **trước khi chạy**; intersection cast `(A & B) object` thực hiện **chuyển kiểu tường minh có kiểm tra runtime**. Hai cơ chế liên quan đến cùng ý tưởng giao các hợp đồng nhưng không thay thế nhau.

## 6. Intersection bounds trong generic class và interface

Không chỉ phương thức, generic class và generic interface cũng có thể khai báo nhiều bounds:

```java
interface Named {
    String name();
}

interface Versioned {
    int version();
}

final class Registry<T extends Named & Versioned> {
    private final T item;

    Registry(T item) {
        this.item = item;
    }

    String describe() {
        return item.name() + "@" + item.version();
    }

    T get() {
        return item;
    }
}
```

Đây là **định nghĩa minh họa riêng**, không sao chép các interface trùng tên vào cùng scope với những khai báo trước đó. Với `Registry<Document>` nếu `Document` triển khai đủ interface:

- Khi xây `Registry<Document>`, type argument `Document` phải thỏa cả hai bounds.
- Trong lớp `Registry`, trường `item` có kiểu tĩnh `T`, nên có thể gọi cả `name()` và `version()`.
- Phương thức `get()` trả **đúng `T`**, giữ lại kiểu cụ thể của object cho người gọi.
- Với lớp chỉ triển khai `Named`, `Registry<NamedOnly>` sẽ không biên dịch.

Một generic interface cũng có thể tự khai báo bound:

```java
interface Inspector<T extends Named & Versioned> {
    String inspect(T item);
}
```

Đây là một interface minh họa; class triển khai `Inspector<Document>` sẽ dùng đúng `T = Document` trong chữ ký. Nếu một generic subclass kế thừa hoặc thực thi type đã có bound, lớp con phải truyền đối số kiểu thỏa mọi ràng buộc của supertype; ví dụ `class AdvancedRegistry<U extends Named & Versioned> extends Registry<U>` chỉ hợp lệ về mặt bound nếu `Registry` được thiết kế cho kế thừa (không `final`) và có constructor/phương thức cần thiết.

## 7. Intersection cast và cách kiểm tra runtime

Java cho phép một số biểu thức cast dùng nhiều reference bounds:

```java
Object object = new Document("doc-1", "Intro", 1);
Named named = (Named & Versioned) object;
String title = named.name();
```

Đây là **các dòng minh họa**, giả sử đã có `Document`, `Named` và `Versioned` như trong ví dụ chạy độc lập và đặt vào method. Cast bảo đảm runtime object đáp ứng cả `Named` và `Versioned`, nhưng biến `named` **được khai báo là `Named`**, nên về sau chỉ có các method của `Named` ở kiểu tĩnh của biến.

Nếu muốn gọi cả hai ngay trong một biểu thức, dùng cast làm kiểu của receiver:

```java
int version = ((Named & Versioned) object).version();
```

Lưu ý dấu ngoặc: `(Named & Versioned)` là phần kiểu trong cast; `((Named & Versioned) object)` là biểu thức đối tượng đã cast. Nếu object không triển khai đủ hai interface, lệnh cast có thể ném `ClassCastException` ở runtime.

Trước khi cast, có thể kiểm tra riêng:

```java
if (object instanceof Named named
        && object instanceof Versioned versioned) {
    System.out.println(named.name() + "@" + versioned.version());
}
```

`instanceof` pattern trong ví dụ trên có sẵn ở Java 17. Nó thiết lập hai biến pattern với hai kiểu tĩnh riêng; không tạo một biến được khai báo bằng cú pháp `Named & Versioned` như tên kiểu thông thường.

**Phân biệt các vị trí cú pháp:**

| Vị trí | Viết được? | Ghi chú |
|---|---|---|
| Khai báo type parameter `<T extends A & B>` | Có | Cách chủ đạo để dùng intersection bounds trong generic API |
| Cast `(A & B) object` | Có nếu các bound/cast tương thích | Kiểm tra runtime; có thể ném `ClassCastException` |
| Khai báo biến `A & B value` | Không | Không phải cú pháp khai báo biến trực tiếp thông thường |
| Type argument `List<A & B>` | Không | Type argument không viết trực tiếp dưới dạng giao như vậy |
| Wildcard `List<? extends A & B>` | Không | Wildcard bound trong Java không dùng cú pháp giao nhiều thành phần này |
| Type check `object instanceof A & B` | Không | Kiểm tra riêng hai loại bằng `&&` hoặc pattern phù hợp |

Dù không viết được `List<A & B>`, bạn có thể viết generic method nhận `List<T>` với `<T extends A & B>`, như `labels(List<T>)` trong ví dụ. Tên `T` là cách biểu đạt mối liên hệ kiểu an toàn giữa danh sách đầu vào và các thành viên được phép truy cập.

## 8. Interface xung đột: không phải giao nào cũng hợp lệ

Hai bound phải tương thích về các method được kế thừa. Xét hai interface không tương thích:

```java
interface A {
    String read();
}

interface B {
    Integer read();
}

// static <T extends A & B> T invalid(T value) { return value; }
```

Đây là **ví dụ lỗi**, trong đó hai interface có method cùng tên và cùng danh sách tham số nhưng kiểu trả về không thể cùng được một triển khai hợp lệ đáp ứng. Không thể chỉ thêm `&` rồi tự động “kết hợp” hai kiểu trả về. Java sẽ áp dụng quy tắc xung đột method/return-type trong quá trình kiểm tra bound hoặc khi khai báo class thực thi; thiết kế nên tránh các hợp đồng loại trừ nhau.

Một kiểu xung đột khác liên quan tới **default methods**:

```java
interface Left {
    default String tag() {
        return "L";
    }
}

interface Right {
    default String tag() {
        return "R";
    }
}

// class Ambiguous implements Left, Right { }
// Sai nếu không giải quyết hai default tag() xung đột.

final class Resolved implements Left, Right {
    @Override
    public String tag() {
        return Left.super.tag() + Right.super.tag();
    }
}
```

Đây là **cụm khai báo minh họa**, không nằm trong chương trình `IntersectionBoundsDemo.java`. `Resolved.tag()` trả `"LR"` nhờ override tường minh. Trong thiết kế bound, không được xem `<T extends Left & Right>` là cách tự động giải quyết các default method mâu thuẫn; phải xem tính hợp lệ của giao diện kết hợp và cung cấp một hợp đồng không xung đột.

Một trường hợp cần phân biệt: hai interface cùng khai báo **một abstract method tương thích** có thể được thực thi bằng **một** phương thức trên class. Vì vậy, “hai interface có method trùng tên” **không tự động là lỗi**; lỗi phát sinh khi hợp đồng xung đột theo các quy tắc của Java, chẳng hạn kiểu trả về không tương thích hoặc default implementations mơ hồ.

Các marker interface như `java.io.Serializable` có thể tham gia bound nhưng không thêm method nghiệp vụ nào để gọi. Chẳng hạn `<T extends Named & java.io.Serializable>` vừa yêu cầu `name()` vừa yêu cầu marker serialization, nhưng không tạo thêm method `serialize()` trên `T`.

## 9. Type erasure: bound đầu tiên ảnh hưởng biểu diễn biên dịch

Java generics thông thường dùng **type erasure**. Với type parameter không có bound, erasure thường là `Object`; với type parameter có nhiều bounds, **erasure của type variable được xác định từ bound đầu tiên**.

Ví dụ:

```java
static <T extends Asset & Named & Versioned> String describe(T item) {
    return item.id() + item.name() + item.version();
}
```

`T` ở đây có erasure là `Asset`, vì `Asset` là bound đầu tiên. Điều này **không xóa quyền gọi** `name()` và `version()` khi biên dịch: compiler đã biết intersection bounds, kiểm tra các lời gọi hợp lệ và tạo mã bytecode phù hợp, có thể bao gồm chuyển kiểu sang interface để gọi method.

Nếu chỉ có interface bounds:

```java
static <T extends Named & Versioned> String label(T item) {
    return item.name() + item.version();
}
```

erasure của `T` là `Named` (bound đầu tiên). Compiler vẫn bảo đảm `T` thỏa `Versioned` ở compile time.

**Hệ quả thiết kế API:** thứ tự các **interface bounds** dù có thể giữ nguyên ý nghĩa ràng buộc ở source level vẫn có thể làm thay đổi erasure và phần chữ ký được biểu diễn trong bytecode. Thay đổi thứ tự bound trong API public của một thư viện đã biên dịch có thể ảnh hưởng binary compatibility. Đừng đổi thứ tự bừa bãi chỉ để làm đẹp code; xem xét các tác động tương thích khi phát hành thư viện.

Erasure cũng giải thích vì sao:

- Không thể kiểm tra trực tiếp `obj instanceof T` với type variable `T` thông thường.
- Không thể tạo `new T()` chỉ dựa vào bound.
- Không thể dùng `T.class` để truy vấn đầy đủ kiểu thực tế đã truyền.
- Không thể overload chỉ bằng thay đổi type parameters/bounds nếu **erased signatures bị trùng**.
- Intersection bound **không** tạo một runtime class đặc biệt kết hợp `Named` và `Versioned`.

Nếu cần thông tin kiểu runtime, một API có thể yêu cầu `Class<T>` hoặc một cơ chế type token phù hợp, nhưng việc thêm runtime token **không thay thế** toàn bộ kiểm tra quan hệ kiểu trong chữ ký generic.

## 10. Intersection type và suy luận kiểu

Ngoài cú pháp khai báo bound và intersection cast, Java type system còn sử dụng intersection types khi suy luận kiểu trong một số ngữ cảnh (ví dụ tính **least upper bound** cho nhiều kiểu đầu vào). Điều này giúp biểu diễn những khả năng chung giữa các nhánh hoặc đối số.

Ví dụ về ý tưởng: nếu hai lớp độc lập đều triển khai `Named` và `Versioned`, trình biên dịch có thể tìm một kiểu chung kết hợp các interface khi phân tích một biểu thức phù hợp. Tuy nhiên, **kiểu suy luận cụ thể** phụ thuộc vào ngữ cảnh, quy tắc của Java và các supertype khác; không nên khẳng định mọi `var x = condition ? a : b` đều có đúng kiểu tĩnh `Named & Versioned`.

Để làm rõ hợp đồng của một phương thức công khai, khai báo bound tường minh thường dễ hiểu hơn:

```java
static <T extends Named & Versioned> String label(T item) {
    return item.name() + "@" + item.version();
}
```

Java có thể suy luận `T` khi gọi `label(document)` hoặc `label(deployment)`, miễn kiểu đối số thỏa bound. Cách viết này cũng cho phép compiler báo lỗi sớm nếu truyền `NamedOnly` chỉ có `Named`.

Đặc biệt, các type parameters trong một generic method có thể có quan hệ với nhau:

```java
static <T extends Named & Versioned, R> R map(
        T source,
        java.util.function.Function<? super T, ? extends R> mapper) {
    return mapper.apply(source);
}
```

Đây là **method minh họa** cần đặt trong class nhìn thấy hai interface. `T` buộc source thỏa cả hai interface, còn `R` mô tả kiểu đầu ra của mapper và không phải tự thỏa các bound của `T`. Dấu phẩy giữa `T`, `R` **khác** với dấu `&` nối nhiều bound của cùng `T`.

Nếu có nhu cầu tính toán trên một danh sách giá trị đa loại, phải lựa chọn giữa **kiểu phần tử đồng nhất có bound rõ ràng**, **wildcard đọc dữ liệu**, và **đối tượng bao/interface chung**. Một phép suy luận rộng ở một lời gọi không làm cho mọi thao tác ghi vào collection trở nên hợp lệ.

## 11. Truy cập thành viên: những giới hạn quan trọng

### 11.1. Chỉ truy cập các thành viên được giới hạn bảo đảm

`<T extends Named & Versioned>` không cho phép trực tiếp gọi `item.id()`, dù đối tượng thực tế có thể là `Document extends Asset`. Nếu method cần `id()`, hãy thêm bound `Asset` ở vị trí đầu hoặc bổ sung `id()` vào một interface phù hợp.

### 11.2. Bound không loại trừ `null`

`T extends Named & Versioned` bảo đảm về **kiểu tĩnh**, không có nghĩa `item` chắc chắn khác null. Truyền `null` có thể được chấp nhận nếu không có ràng buộc null riêng; sau đó `item.name()` có thể ném `NullPointerException`. Khi muốn cấm null, dùng `Objects.requireNonNull(item)` hoặc một hợp đồng/kiểm tra null thích hợp.

### 11.3. Java access control vẫn tồn tại

Một class bound có field `private` không đồng nghĩa mọi generic method ở class khác đều truy cập được field đó. Type bounds kiểm tra **khả năng kiểu**, còn access modifier kiểm tra **quyền truy cập theo vị trí mã**.

### 11.4. Multiple bounds không tạo multiple class inheritance

`<T extends Base & I1 & I2>` cho phép tối đa **một class bound** theo vị trí đầu, sau đó là interface bounds. Nó không cho phép `T` kế thừa đồng thời hai class độc lập. Nếu cần tái sử dụng hai dạng trạng thái, cân nhắc composition và interface.

### 11.5. Không biến phần tử của `List` thành một intersection type bằng cast danh sách

`List<Named>` và `List<Versioned>` vẫn invariant; không thể cast `List<Named>` thành `List<Named & Versioned>` vì cú pháp type argument giao không hợp lệ. Nếu cần một API đọc tập các phần tử thỏa cả hai giới hạn, dùng `<T extends Named & Versioned> void work(List<T> items)` với danh sách đã có type element phù hợp.

### 11.6. Thêm interface bound có thể làm API chặt hơn

Đổi `<T extends Named>` thành `<T extends Named & Versioned>` làm **một số lời gọi trước đây không còn hợp lệ** (những kiểu chỉ implements Named). Đây là thay đổi hợp đồng API, không phải cải tiến miễn phí. Cân nhắc hiệu ứng lên code client và binary compatibility.

## 12. Lỗi thường gặp và cách sửa

**Lỗi 1 — Dùng dấu phẩy thay vì `&`.** `<T extends Named, Versioned>` không biểu diễn hai bound cho `T`; nó không khai báo đúng danh sách type parameters. Viết `<T extends Named & Versioned>`.

**Lỗi 2 — Lặp từ khóa `extends`.** `<T extends Named extends Versioned>` sai cú pháp. Chỉ một `extends`, nối bằng `&`.

**Lỗi 3 — Đặt class bound sau interface bound.** `<T extends Named & Asset>` sai nếu `Asset` là class. Viết `<T extends Asset & Named>`.

**Lỗi 4 — Cho rằng bound đầu tiên là bound duy nhất có thể gọi method.** Với `<T extends Asset & Named & Versioned>`, method có thể dùng các member truy cập được từ **cả ba** bound; erasure về `Asset` không ngăn compiler kiểm tra `name()` và `version()`.

**Lỗi 5 — Cho rằng object runtime đủ khả năng thì generic method mặc nhiên gọi được.** Trong `<T extends Named>`, `item.version()` vẫn không biên dịch nếu bound không bảo đảm `version()`. Nâng hợp đồng lên `Named & Versioned` thay vì dựa vào các đối tượng tình cờ được truyền khi chạy.

**Lỗi 6 — Nghĩ `<T extends A & B>` bắt buộc `A`, `B` là hai class.** Java chỉ cho nhiều interface bounds; nếu có class, nó đứng đầu và duy nhất.

**Lỗi 7 — Dùng `List<A & B>`.** Đây không phải cú pháp type argument hợp lệ. Thiết kế generic method nhận `List<T>` với `T extends A & B`.

**Lỗi 8 — Nghĩ `? extends A & B` dùng như bound của wildcard.** Java không cho wildcard intersection bound trực tiếp như thế. Hãy đặt type parameter có tên để diễn đạt quan hệ cần giữ.

**Lỗi 9 — Dùng cast `(A & B)` thay cho giới hạn kiểu tĩnh mà không kiểm tra.** Cast có thể ném `ClassCastException`; generic bound buộc kiểu đối số phù hợp ngay lúc biên dịch và thường nên ưu tiên khi thiết kế API.

**Lỗi 10 — Hiểu nhầm `extends Serializable` tự thêm method.** `Serializable` là marker interface; nó giới hạn các kiểu được chấp nhận nhưng không thêm một `serialize()` member để gọi.

**Lỗi 11 — Quên kiểm tra xung đột giữa các interface.** Hai bound có method cùng signature nhưng return type không tương thích, hoặc nhiều default methods không thể giải quyết hợp lệ, có thể làm giao không hợp lệ. Sửa các hợp đồng hoặc tách riêng nhu cầu.

**Lỗi 12 — Nhầm type erasure với mất type checking.** Erasure là chiến lược runtime/bytecode; compiler vẫn xác minh mọi bound và member access ở source level trước khi sinh bytecode.

**Lỗi 13 — Đổi thứ tự interface bounds tùy tiện trong thư viện đã phát hành.** Bound đầu tiên quyết định erasure của type variable, có thể ảnh hưởng API binary. Đọc kỹ hợp đồng trước khi sửa.

**Lỗi 14 — Quên null và accessibility.** Multiple bounds không ngăn `NullPointerException` và không bỏ qua `private`/`protected`/package visibility. Cần tự thiết kế validation và mức truy cập phù hợp.

**Lỗi 15 — Viết `instanceof A & B`.** Java không có cú pháp kiểm tra kiểu như vậy trong biểu thức `instanceof`. Dùng `obj instanceof A && obj instanceof B` hoặc pattern `instanceof` riêng từng giao diện.

## 13. Quy tắc thiết kế API sử dụng intersection bounds

Cân nhắc nhiều bounds khi **một tham số thật sự cần đồng thời nhiều khả năng độc lập**. Ví dụ một bộ xử lý cần đối tượng vừa có tên vừa có phiên bản, nhưng không muốn tạo interface mới chỉ để gom hai khả năng đó:

```java
static <T extends Named & Versioned> String keyOf(T value) {
    return value.name() + ":" + value.version();
}
```

Thiết kế này cho phép các class không cùng superclass nghiệp vụ — như `Document` và `Deployment` — tham gia nếu cùng đáp ứng hợp đồng.

Tuy vậy, nếu cặp khả năng `Named + Versioned` xuất hiện nhiều lần trong domain, có thể cân nhắc một interface kết hợp:

```java
interface NamedVersioned extends Named, Versioned { }

static String keyOf(NamedVersioned value) {
    return value.name() + ":" + value.version();
}
```

Đây là **hai khai báo minh họa**. Một interface kết hợp tạo tên miền rõ ràng, nhưng những lớp chỉ `implements Named, Versioned` **không tự động được xem là triển khai `NamedVersioned`** nếu chúng không khai báo hoặc kế thừa interface ấy. Đây là sự khác biệt thực tế:

- **Intersection bound `T extends Named & Versioned`** chấp nhận mọi kiểu thỏa cả hai giao diện, không đòi một interface con mới được khai báo trên lớp.
- **Named interface `NamedVersioned`** đòi lớp thực hiện quan hệ `implements NamedVersioned` thích hợp; đổi lại API có một tên hợp đồng dễ tham chiếu.

Nên dùng type parameter có tên khi cần giữ quan hệ kiểu ở kết quả, ví dụ `static <T extends Named & Versioned> T verified(T item)` trả lại đúng `T`. Nếu chỉ cần hành vi và không cần giữ kiểu cụ thể, một interface miền nghiệp vụ có thể đơn giản hơn.

## 14. Câu hỏi tự kiểm tra có đáp án

**1.** `<T extends Named & Versioned>` yêu cầu điều gì?  
**Đáp án:** Type argument `T` phải phù hợp cả hai interface; trong phương thức có thể gọi các member hợp lệ từ cả `Named` và `Versioned`.

**2.** Vì sao không viết `<T extends Named extends Versioned>`?  
**Đáp án:** Java chỉ dùng một `extends` trong khai báo bound và nối các bound tiếp theo bằng `&`.

**3.** Nếu `Asset` là class, thứ tự đúng giữa `Asset`, `Named`, `Versioned` là gì?  
**Đáp án:** `<T extends Asset & Named & Versioned>`; class bound phải đứng đầu.

**4.** Với `<T extends Named>`, có gọi `item.version()` được không nếu người gọi truyền một `Document` implements cả hai interface?  
**Đáp án:** Không. Chữ ký chỉ bảo đảm `Named`; compiler không dựa vào object runtime trong từng lời gọi để mở thêm thành viên.

**5.** Với `<T extends Asset & Named & Versioned>`, những method nào có thể gọi được từ ba hợp đồng minh họa?  
**Đáp án:** `id()`, `name()` và `version()`, nếu tất cả các method đó truy cập được tại vị trí mã.

**6.** `NamedOnly` có thể truyền vào method `<T extends Named & Versioned>` không?  
**Đáp án:** Không, vì thiếu `Versioned`.

**7.** Intersection cast `((Named & Versioned) obj)` có đảm bảo thành công với mọi object `Named` không?  
**Đáp án:** Không; object còn phải thỏa `Versioned`. Nếu không, cast có thể ném `ClassCastException`.

**8.** Một biến `Named named = (Named & Versioned) obj;` có trực tiếp gọi `named.version()` được không?  
**Đáp án:** Không, vì kiểu tĩnh của biến là `Named`; muốn truy cập `version()`, dùng một API bảo toàn cả hai khả năng hoặc cast trong biểu thức khi an toàn.

**9.** `List<Named & Versioned>` hợp lệ không?  
**Đáp án:** Không. Hãy dùng named type parameter `<T extends Named & Versioned>` với `List<T>`.

**10.** `<T extends Number & Comparable<T>>` có chấp nhận `Integer` không?  
**Đáp án:** Có, vì `Integer` là `Number` và thực thi `Comparable<Integer>`.

**11.** Bound đầu tiên ảnh hưởng đến điều gì khi biên dịch generics?  
**Đáp án:** Erasure của type variable; ví dụ trong `T extends Asset & Named`, erasure là `Asset`.

**12.** Có thể dùng hai class bounds như `<T extends Asset & AnotherClass>` không?  
**Đáp án:** Không. Java không hỗ trợ multiple class inheritance; sau class bound, các bound bổ sung phải là interface.

**13.** `Serializable` trong một bound có tự cấp method nghiệp vụ cho biến `T` không?  
**Đáp án:** Không; nó là marker interface không có method nghiệp vụ riêng.

**14.** `T extends Named & Versioned` có loại trừ `null` không?  
**Đáp án:** Không; cần quy tắc validation/nullness riêng nếu API không chấp nhận `null`.

**15.** Type variable có intersection bounds có làm Java giữ tất cả type arguments nguyên vẹn tại runtime không?  
**Đáp án:** Không. Java generics thông thường vẫn dùng type erasure, dù compiler kiểm tra mọi bound khi biên dịch.

**16.** Nên dùng interface mới `NamedVersioned` hay intersection bound?  
**Đáp án:** Intersection bound thuận lợi khi cần chấp nhận mọi kiểu đã thực hiện độc lập hai interface; interface mới phù hợp nếu cần một tên hợp đồng chung rõ ràng và các class được chủ động khai báo theo nó.

**17.** `instanceof Named & Versioned` có hợp lệ không?  
**Đáp án:** Không. Dùng `obj instanceof Named && obj instanceof Versioned` hoặc hai `instanceof` pattern phù hợp.

**18.** Vì sao đổi thứ tự các interface bound trong thư viện có thể rủi ro dù code source vẫn trông tương đương?  
**Đáp án:** Bound đầu tiên quyết định erasure và có thể làm thay đổi chữ ký bytecode, ảnh hưởng tương thích nhị phân.

**Tổng kết:** intersection type diễn đạt một **hợp đồng kết hợp**: `T` phải đồng thời thỏa nhiều bound. Từ đó compiler cho phép đọc/gọi những thành viên mà **tất cả các yêu cầu về kiểu và truy cập** cho phép. Cú pháp đúng thường là `<T extends ClassBound & Interface1 & Interface2>` hoặc `<T extends Interface1 & Interface2>`. Hãy phân biệt khai báo bound (an toàn kiểu compile-time) với intersection cast (kiểm tra runtime), lưu ý bound đầu tiên ảnh hưởng erasure, và dùng kiểu giao khi nhiều khả năng thực sự cần xuất hiện trong một chữ ký API.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="generic-type-bounds-007">&lt;T extends Number&gt; không cung cấp toán tử số học trên T</a>

<details>
<summary>Xem chi tiết</summary>

# Vì sao `<T extends Number>` không cung cấp toán tử số học trên `T`?

Một cách hiểu nhầm phổ biến khi học Java Generics là: nếu khai báo `<T extends Number>`, các biến kiểu `T` hẳn phải dùng được các toán tử số học `+`, `-`, `*` và `/` như số nguyên hoặc số thực. **Điều đó không đúng trong Java.**

`Number` là một **lớp trừu tượng** đại diện cho các kiểu số dưới dạng đối tượng. Giới hạn `T extends Number` cho phép compiler biết rằng một giá trị kiểu `T` có các phương thức của `Number`, chẳng hạn `intValue()`, `longValue()`, `floatValue()` và `doubleValue()`. Nhưng giới hạn này **không biến `T` thành kiểu nguyên thủy**, không định nghĩa phép cộng cho mọi lớp con và không tạo toán tử tùy biến. Vì vậy biểu thức `a + b` với hai biến có kiểu `T extends Number` **không biên dịch**.

Bài học phân biệt kiểm tra kiểu generic với cơ chế toán tử số học của Java, giải thích vì sao unboxing không tự áp dụng cho `T`, cách chuyển đổi giá trị có chủ đích, và các phương án thiết kế API khi thật sự cần cộng/trừ/so sánh những số thuộc nhiều kiểu.

## 1. Đọc đúng ý nghĩa của `<T extends Number>`

Xét phương thức generic:

```java
static <T extends Number> double asDouble(T value) {
    return value.doubleValue();
}
```

Đây là **phương thức minh họa** đặt bên trong một lớp Java. Ta có thể gọi phương thức với `Integer`, `Long`, `Double`, `BigDecimal`, `BigInteger` hoặc lớp con hợp lệ khác của `Number`. Lý do compiler chấp nhận `value.doubleValue()` là `Number` công bố phương thức đó.

Nhưng chữ ký:

```java
static <T extends Number> T add(T a, T b) {
    // return a + b; // LỖI BIÊN DỊCH
    throw new UnsupportedOperationException();
}
```

lại khác. Dòng `return a + b` bị chú thích vì toán tử cộng số học của Java không có cách áp dụng tổng quát lên hai **tham chiếu kiểu `T`**. Phần `throw` chỉ nhằm tạo thân phương thức hợp lệ để minh họa chữ ký; **không phải** một cách cài đặt phép cộng.

Cần phân biệt:

| Khái niệm | `<T extends Number>` có bảo đảm không? | Vì sao? |
|---|---|---|
| `T` là một kiểu tham chiếu thỏa `Number` | Có | Đây là **upper bound** của tham số kiểu. |
| `value.doubleValue()` gọi được | Có | Phương thức được khai báo trên `Number`. |
| `value.intValue()` gọi được | Có | Đây là phương thức của `Number`. |
| `a + b` với `T a, T b` | **Không** | Không có toán tử số học tổng quát trên các đối tượng `Number`. |
| `a.compareTo(b)` gọi được | **Không** chỉ từ bound này | `Number` không thực hiện `Comparable<Number>`. |
| Kết quả phép cộng tự có kiểu `T` | **Không** | Không có hợp đồng nào sinh một giá trị `T` mới. |
| Mọi phép chuyển sang `double` đều chính xác | **Không** | Chuyển đổi có thể làm tròn hoặc mất thông tin. |

**Upper bound là ràng buộc về các thành viên có thể sử dụng và quan hệ kiểu**, không phải lời hứa rằng mọi lớp con đều hỗ trợ cùng một toán tử với cùng quy tắc.

## 2. Java định nghĩa các toán tử số học cho những kiểu nào?

Các phép số học số nguyên/số thực như `+`, `-`, `*`, `/` chủ yếu hoạt động trên **kiểu số nguyên thủy** (`byte`, `short`, `char`, `int`, `long`, `float`, `double`) sau quy tắc chuyển đổi số nhị phân (*binary numeric promotion*). Java cũng hỗ trợ **unboxing** từ những lớp bao chuẩn tương ứng khi ngữ cảnh phép toán yêu cầu.

Ví dụ nằm trong một phương thức:

```java
int a = 4;
long b = 5L;
System.out.println(a + b); // 9, kết quả kiểu long

Integer x = 4;
Integer y = 5;
System.out.println(x + y); // 9: Integer tự unbox thành int
```

Ở ví dụ thứ hai, compiler **biết chính xác** `x` và `y` có kiểu `Integer`, lớp bao chuẩn của `int`. Vì thế nó áp dụng unboxing và phép cộng số nguyên.

Ngược lại:

```java
Number first = Integer.valueOf(4);
Number second = Integer.valueOf(5);

// System.out.println(first + second); // SAI: Number + Number
```

Dù hai đối tượng runtime đều là `Integer`, **kiểu tĩnh** của hai biến là `Number`. Java không định nghĩa unboxing từ kiểu `Number` nói chung. Compiler không tìm class runtime của từng toán hạng rồi quyết định toán tử ở lúc chạy.

Điều tương tự xảy ra với tham số kiểu `T` bị giới hạn bởi `Number`: tuy `T` hẹp hơn hoặc bằng `Number` theo bound, compiler vẫn không biết một phép cộng thống nhất cho mọi `T` hợp lệ.

### Những trường hợp dễ nhầm

- `Integer + Integer`: hợp lệ nhờ unboxing.
- `Double + Integer`: hợp lệ nhờ unboxing và numeric promotion.
- `Number + Number`: **không hợp lệ**.
- `T + T` trong `<T extends Number>`: **không hợp lệ**.
- `BigDecimal + BigDecimal`: **không hợp lệ** vì `BigDecimal` là lớp đối tượng không được Java nạp chồng toán tử `+`; phải gọi `add`.
- `String + object`: hợp lệ với ý nghĩa **nối chuỗi**, không phải cộng số; không được dùng hiện tượng đó làm bằng chứng `Number` hỗ trợ toán tử.

Không có cơ chế **operator overloading do người dùng định nghĩa** cho `+` trên một class Java tùy ý.

## 3. Ví dụ Java 17: cùng một giá trị, khác kiểu tĩnh, khác phép toán hợp lệ

Lưu chương trình sau thành **`NumberBoundaryDemo.java`**:

```java
public class NumberBoundaryDemo {
    public static void main(String[] args) {
        Integer wrappedA = 4;
        Integer wrappedB = 5;
        System.out.println(wrappedA + wrappedB);

        Number first = wrappedA;
        Number second = wrappedB;

        // Không được viết: first + second
        System.out.println(first.intValue() + second.intValue());
        System.out.println(first.doubleValue() + second.doubleValue());

        Number decimal = Double.valueOf(2.9);
        System.out.println(decimal.intValue());
        System.out.println(decimal.doubleValue());
    }
}
```

Biên dịch và chạy:

```bash
javac -encoding UTF-8 NumberBoundaryDemo.java
java NumberBoundaryDemo
```

Kết quả `stdout`:

```text
9
9
9.0
2
2.9
```

Ở đây ba điểm rất đáng chú ý:

1. `wrappedA + wrappedB` biên dịch vì cả hai có kiểu tĩnh `Integer`, cho phép unboxing.
2. `first + second` sẽ không biên dịch vì cả hai có kiểu tĩnh `Number`. Gọi `intValue()` hoặc `doubleValue()` trước phép toán là **một chuyển đổi do lập trình viên chọn**, khác hoàn toàn với cộng trực tiếp trên `Number`.
3. `decimal.intValue()` trả `2`, không phải `2.9`. Chuyển đổi sang `int` bỏ phần thập phân (với giá trị hữu hạn trong khoảng hợp lệ); vì thế **chuyển kiểu có thể mất dữ liệu**.

Chương trình chứng minh rằng bản thân đối tượng runtime chứa số chưa đủ để compiler cho phép toán tử. **Kiểu tĩnh và hợp đồng của phép toán mới là yếu tố quyết định.**

## 4. Vì sao Java không thể tự định nghĩa phép cộng cho mọi `Number`?

`Number` là một abstraction cho nhiều loại số với đặc tính khác nhau. Các lớp con phổ biến gồm:

- `Byte`, `Short`, `Integer`, `Long`: lớp bao các số nguyên thủy tương ứng.
- `Float`, `Double`: biểu diễn số thực dấu phẩy động nhị phân.
- `BigInteger`: số nguyên có độ lớn tùy ý, với phép cộng `add(BigInteger)`.
- `BigDecimal`: số thập phân có độ chính xác thập phân được kiểm soát, với `add(BigDecimal)`.
- Các lớp con khác có thể do thư viện hoặc ứng dụng định nghĩa, với ý nghĩa và giới hạn riêng.

Nếu một phương thức nhận `Number a` và `Number b`, cần trả lời:

- Nếu `a` là `Integer` nhưng `b` là `Double` thì kết quả phải là `Integer`, `Double`, `BigDecimal` hay kiểu khác?
- Nếu cả hai là `Long` và tổng vượt giới hạn `long`, API phải tràn số, báo lỗi, hay trả `BigInteger`?
- Nếu `a` là `BigDecimal("0.1")`, phép cộng có cần giữ **độ chính xác thập phân** không?
- Nếu `a` là một lớp con `Number` tùy chỉnh, làm thế nào tạo một thể hiện cùng lớp để giữ kiểu `T`?
- Nếu có giá trị `null`, API nên từ chối, bỏ qua hay thay bằng zero?

Java không tự chọn một câu trả lời cho tất cả các trường hợp đó. `Number` **không khai báo `add(Number)`**, cũng không có phương thức abstract hoặc toán tử đảm bảo “trả về một `T` tương ứng”.

Vì vậy, nếu chỉ biết `T extends Number`, bạn biết **cách chuyển giá trị thành một vài kiểu primitive**, chứ chưa biết **cách thực hiện số học đóng trên `T`**.

## 5. Cách 1: chuyển sang một kiểu primitive đích rõ ràng

Nếu nghiệp vụ chấp nhận xử lý trên `double`, có thể viết utility:

```java
static <T extends Number> double sumAsDouble(
        java.util.List<T> numbers) {
    double total = 0.0;
    for (T number : numbers) {
        total += number.doubleValue();
    }
    return total;
}
```

Đây là **phương thức minh họa trong một lớp**. Nó nhận một danh sách các đối tượng kiểu `T` và cộng giá trị đã chuyển thành `double`. Hàm **trả `double` chứ không trả `T`**. Nếu cố trả `T` từ kết quả `double`, compiler không có cách chuyển ngược an toàn, chung cho mọi lớp con của `Number`.

### Chương trình hoàn chỉnh

Lưu **`SumAsDoubleDemo.java`**:

```java
import java.util.List;

public class SumAsDoubleDemo {
    static <T extends Number> double sumAsDouble(List<T> values) {
        double total = 0.0;
        for (T value : values) {
            total += value.doubleValue();
        }
        return total;
    }

    static double sumNumbers(List<? extends Number> values) {
        double total = 0.0;
        for (Number value : values) {
            total += value.doubleValue();
        }
        return total;
    }

    public static void main(String[] args) {
        List<Integer> integers = List.of(1, 2, 3);
        List<Double> decimals = List.of(1.5, 2.25);

        System.out.println(sumAsDouble(integers));
        System.out.println(sumAsDouble(decimals));
        System.out.println(sumNumbers(List.of(1, 2.5, 3L)));
    }
}
```

Chạy bằng Java 17:

```bash
javac -encoding UTF-8 SumAsDoubleDemo.java
java SumAsDoubleDemo
```

`stdout`:

```text
6.0
3.75
6.5
```

Phân tích:

- Trong `sumAsDouble`, `<T extends Number>` giúp phần tử danh sách có kiểu `T` và cho phép gọi `doubleValue()`.
- Trong `sumNumbers`, không cần một tham số kiểu được **đặt tên**, vì thuật toán chỉ đọc phần tử dưới dạng `Number` rồi trả `double`. `List<? extends Number>` đủ để nhận `List<Integer>`, `List<Double>` và những danh sách số khác.
- Cả hai phiên bản đều thực hiện **tính toán trên `double`**, không thực hiện phép cộng trực tiếp trên `T`.
- `List.of(1, 2.5, 3L)` là một danh sách không sửa đổi được chứa các đối tượng số, được chấp nhận bởi tham số `List<? extends Number>`.

Nếu phương thức không cần giữ mối quan hệ kiểu giữa các tham số hoặc giá trị trả về, **wildcard đơn giản có thể dễ đọc hơn việc khai báo `<T>`**. Chỉ dùng một tên tham số kiểu khi nó thật sự diễn đạt một quan hệ cần được bảo toàn.

## 6. Cảnh báo: `doubleValue()` không phải chuyển đổi chính xác cho mọi kiểu

Mặc dù `doubleValue()` có sẵn trên `Number`, đây **không phải phép chuyển đổi bảo toàn dữ liệu cho mọi số**. `double` là kiểu dấu phẩy động nhị phân IEEE 754 với khoảng 53 bit chính xác cho phần trị, không thể biểu diễn chính xác mọi số nguyên lớn hoặc mọi phân số thập phân.

Ví dụ:

```java
long exact = 9_007_199_254_740_993L;
double converted = Long.valueOf(exact).doubleValue();

System.out.println(converted == 9_007_199_254_740_992.0);
```

Đây là **đoạn mã trong một phương thức**; kết quả là `true` vì hai số nguyên liên tiếp ở vùng giá trị đó có thể bị làm tròn về cùng một giá trị `double`. Như vậy chuyển từ `long` lớn sang `double` có thể làm mất đơn vị cuối.

Một chương trình độc lập để minh họa:

Lưu tệp **`PrecisionDemo.java`**:

```java
import java.math.BigDecimal;

public class PrecisionDemo {
    public static void main(String[] args) {
        Number large = 9_007_199_254_740_993L;
        double approximate = large.doubleValue();

        System.out.println(
                approximate == 9_007_199_254_740_992.0);
        System.out.println(new BigDecimal("0.1")
                .add(new BigDecimal("0.2")));
        System.out.println(0.1 + 0.2);
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 PrecisionDemo.java
java PrecisionDemo
```

`stdout`:

```text
true
0.3
0.30000000000000004
```

**Cách chọn kiểu tính toán** phụ thuộc nghiệp vụ:

- Dữ liệu đo đạc hoặc mô phỏng chấp nhận sai số: `double` có thể phù hợp.
- Tiền tệ hoặc giá trị đòi độ chính xác thập phân: thường dùng `BigDecimal` với chính sách scale/rounding minh bạch.
- Số nguyên rất lớn cần chính xác: cân nhắc `BigInteger`.
- Số nguyên cố định: `int`/`long` có giới hạn; cân nhắc kiểm tra tràn bằng `Math.addExact` và các hàm liên quan.

Đừng đưa `doubleValue()` vào mọi generic utility chỉ để khiến phép `+` biên dịch; bạn đang **quyết định một mô hình số học mới**, không chỉ làm “cú pháp tương đương”.

## 7. Cách 2: gọi phép toán cụ thể của lớp số

Khi biết kiểu đầu vào, hãy ưu tiên API số học chuyên biệt của kiểu ấy thay vì cố viết `T + T`. Ví dụ:

```java
import java.math.BigDecimal;
import java.math.BigInteger;

// Đoạn minh họa trong thân phương thức:
BigDecimal money = new BigDecimal("0.1")
        .add(new BigDecimal("0.2"));

BigInteger big = new BigInteger("99999999999999999999")
        .add(BigInteger.ONE);
```

`BigDecimal.add(...)` và `BigInteger.add(...)` là các phương thức do các lớp cụ thể định nghĩa. Chúng **không** được khai báo bởi `Number`, nên một biểu thức kiểu `T extends Number` không thể gọi `a.add(b)` nếu không có bound khác thực sự cung cấp hợp đồng phương thức phù hợp.

Một ví dụ Java 17 hoàn chỉnh:

Lưu **`ConcreteArithmeticDemo.java`**:

```java
import java.math.BigDecimal;
import java.math.BigInteger;

public class ConcreteArithmeticDemo {
    public static void main(String[] args) {
        BigDecimal money = new BigDecimal("0.1")
                .add(new BigDecimal("0.2"));
        BigInteger huge = new BigInteger("99999999999999999999")
                .add(BigInteger.ONE);

        int safe = Math.addExact(20, 22);
        System.out.println(money);
        System.out.println(huge);
        System.out.println(safe);
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 ConcreteArithmeticDemo.java
java ConcreteArithmeticDemo
```

`stdout`:

```text
0.3
100000000000000000000
42
```

Lưu ý tạo `BigDecimal` từ **chuỗi thập phân** như `"0.1"` để chỉ rõ giá trị mong muốn; viết `new BigDecimal(0.1)` thường mang theo xấp xỉ nhị phân của literal `double`. `BigDecimal.valueOf(0.1)` có hành vi phù hợp hơn cho nhiều tình huống, nhưng khi giá trị tài chính xuất phát từ văn bản thập phân thì constructor nhận chuỗi thường biểu đạt ý định rõ nhất.

## 8. Cách 3: truyền phép cộng từ bên ngoài bằng `BinaryOperator<T>`

Nếu thật sự muốn một thuật toán tổng quát **trả về đúng kiểu `T`**, thuật toán phải được cung cấp một cách cộng hai giá trị `T` thành một giá trị `T`. Trong Java, có thể biểu diễn khả năng này bằng một **strategy** hoặc hàm `BinaryOperator<T>`.

Chữ ký:

```java
static <T extends Number> T sum(
        java.util.List<T> values,
        T zero,
        java.util.function.BinaryOperator<T> add) {
    T result = zero;
    for (T value : values) {
        result = add.apply(result, value);
    }
    return result;
}
```

Đây là **phương thức minh họa**, cần đặt trong một lớp. Thuật toán không viết `result + value`. Nó gọi `add.apply(result, value)` và để người gọi quyết định số học ứng với loại `T`.

### Chương trình hoàn chỉnh: cùng thuật toán cho `Integer` và `BigDecimal`

Lưu **`NumericStrategyDemo.java`**:

```java
import java.math.BigDecimal;
import java.util.List;
import java.util.function.BinaryOperator;

public class NumericStrategyDemo {
    static <T extends Number> T sum(
            List<T> values,
            T zero,
            BinaryOperator<T> addition) {
        T result = zero;
        for (T value : values) {
            result = addition.apply(result, value);
        }
        return result;
    }

    public static void main(String[] args) {
        Integer integerTotal = sum(
                List.of(1, 2, 3),
                0,
                Integer::sum);

        BigDecimal decimalTotal = sum(
                List.of(
                    new BigDecimal("0.1"),
                    new BigDecimal("0.2")
                ),
                BigDecimal.ZERO,
                BigDecimal::add);

        System.out.println(integerTotal);
        System.out.println(decimalTotal);
    }
}
```

Biên dịch và chạy:

```bash
javac -encoding UTF-8 NumericStrategyDemo.java
java NumericStrategyDemo
```

`stdout`:

```text
6
0.3
```

Ở lời gọi thứ nhất, `T` được suy luận là `Integer` và người gọi truyền `Integer::sum`. Ở lời gọi thứ hai, `T` được suy luận là `BigDecimal` và phép cộng được truyền bằng `BigDecimal::add`.

**Vì sao cách này khả thi?** Java không cần biết cách cộng một `T` bất kỳ; nó chỉ cần đảm bảo hàm `BinaryOperator<T>` nhận hai giá trị `T` và trả về một giá trị `T`. Người gọi tự cung cấp định nghĩa phép cộng của kiểu cụ thể.

Một số lưu ý thiết kế:

- `zero` phải là giá trị khởi tạo phù hợp với phép cộng đã chọn; compiler kiểm tra **kiểu**, chứ không chứng minh đây là phần tử trung hòa toán học.
- `Integer::sum` dùng phép cộng kiểu `int`, nên **vẫn có thể tràn số**. Nếu yêu cầu phát hiện tràn, có thể dùng `Math::addExact` trong ngữ cảnh `Integer`.
- `addition` có thể do người dùng viết sai, ném ngoại lệ hoặc có tác dụng phụ. Kiểm tra nghiệp vụ vẫn cần thiết.
- Nếu danh sách chứa `null`, toán tử như `Integer::sum` có thể ném `NullPointerException` khi unboxing.
- Ràng buộc `T extends Number` không cần thiết về mặt cú pháp nếu utility chỉ thực hiện `addition.apply` mà không dùng thành viên `Number`. Có thể chọn `<T>` thuần túy nếu muốn thuật toán áp dụng cho các kiểu có phép kết hợp khác, chẳng hạn nối chuỗi.

**Điểm quan trọng:** `BinaryOperator<T>` là một giải pháp cung cấp **hợp đồng phép toán** từ bên ngoài. Nó không làm Java “tự hỗ trợ toán tử `+` trên `T`”.

## 9. Cách 4: thiết kế interface cung cấp phép cộng

Nếu ứng dụng sở hữu các lớp số custom hoặc domain riêng, có thể định nghĩa interface biểu thị khả năng cộng. Giới hạn generic sẽ dựa trên **hợp đồng thực sự có phương thức phép cộng**, không dựa vào `Number`.

Ví dụ một kiểu lượng nguyên:

```java
interface Addable<T> {
    T add(T other);
}
```

Đây là một interface generic; có thể có một lớp domain `Quantity` triển khai nó và định nghĩa `add`. Nếu muốn dùng tất cả phương thức của `Number` **và** khả năng cộng, khai báo kiểu ràng buộc kép:

```java
static <T extends Number & Addable<T>> T add(T a, T b) {
    return a.add(b);
}
```

Đây là **phương thức minh họa**, cần đặt trong lớp. `Number` là bound dạng lớp nên đứng đầu; `Addable<T>` là interface bound theo sau dấu `&`. Lúc này compiler chấp nhận `a.add(b)` **do interface `Addable<T>` khai báo phương thức `add`**, không phải vì `Number` cung cấp phép cộng.

### Ví dụ chạy được với `Quantity extends Number`

Lưu **`AddableNumberDemo.java`**:

```java
public class AddableNumberDemo {
    interface Addable<T> {
        T add(T other);
    }

    static final class Quantity extends Number
            implements Addable<Quantity> {
        private final int value;

        Quantity(int value) {
            this.value = value;
        }

        @Override
        public Quantity add(Quantity other) {
            return new Quantity(Math.addExact(value, other.value));
        }

        @Override
        public int intValue() {
            return value;
        }

        @Override
        public long longValue() {
            return value;
        }

        @Override
        public float floatValue() {
            return value;
        }

        @Override
        public double doubleValue() {
            return value;
        }

        @Override
        public String toString() {
            return Integer.toString(value);
        }
    }

    static <T extends Number & Addable<T>> T plus(T a, T b) {
        return a.add(b);
    }

    public static void main(String[] args) {
        Quantity a = new Quantity(20);
        Quantity b = new Quantity(22);
        System.out.println(plus(a, b));
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 AddableNumberDemo.java
java AddableNumberDemo
```

`stdout`:

```text
42
```

Tại sao `plus(a,b)` biên dịch? `Quantity` **kế thừa `Number` và triển khai `Addable<Quantity>`**, nên thỏa đồng thời hai giới hạn. `Math.addExact` giúp phương thức `Quantity.add` ném `ArithmeticException` thay vì âm thầm tràn số khi cộng hai `int` vượt phạm vi.

Không nên ép những lớp JDK như `Integer` hoặc `BigDecimal` vào interface `Addable<T>` do mình định nghĩa: chúng không tự nhiên triển khai interface này, và ta cũng không thể sửa mã nguồn của chúng. Với lớp số sẵn có, cách **truyền `BinaryOperator<T>`** ở phần trước thường thuận tiện hơn.

## 10. Toán tử so sánh cũng cần xem xét riêng

`T extends Number` không đủ để gọi `compareTo`:

```java
static <T extends Number> boolean greater(T a, T b) {
    // return a.compareTo(b) > 0; // Sai: Number không khai báo compareTo
    return a.doubleValue() > b.doubleValue();
}
```

Phương thức trên là **mã minh họa có thân hợp lệ** nhưng so sánh sau chuyển đổi `double`, nên có thể bị mất chính xác. Nó không đồng nghĩa so sánh chính xác cho mọi loại `Number`.

Nếu cần thứ tự tự nhiên do đối tượng định nghĩa, thêm bound `Comparable`:

```java
static <T extends Number & Comparable<? super T>> T maximum(
        T a, T b) {
    return a.compareTo(b) >= 0 ? a : b;
}
```

Đoạn này là phương thức đặt trong một lớp. Compiler cho phép `compareTo` vì `Comparable<? super T>` cung cấp hợp đồng so sánh; `Number` vẫn chỉ cung cấp khả năng chuyển đổi số.

Một chương trình Java 17 đầy đủ:

Lưu **`NumberComparableDemo.java`**:

```java
public class NumberComparableDemo {
    static <T extends Number & Comparable<? super T>>
    T maximum(T a, T b) {
        return a.compareTo(b) >= 0 ? a : b;
    }

    public static void main(String[] args) {
        System.out.println(maximum(5, 9));
        System.out.println(maximum(2.5, 1.25));
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 NumberComparableDemo.java
java NumberComparableDemo
```

`stdout`:

```text
9
2.5
```

Cú pháp `<T extends Number & Comparable<? super T>>` thuộc nhóm **multiple bounds**. Nếu có class bound, nó phải đứng đầu; tiếp theo là các interface.

Một điểm khác: các wrapper như `Integer` và `Double` có `compareTo` nhưng không tự cho phép so sánh chéo bằng một `Comparable<Number>` chung. Đừng giả định `maximum(5, 2.5)` có một kiểu `T` phù hợp chỉ vì cả hai đều là số.

## 11. Số nguyên bị tràn: một phép cộng “hợp lệ” vẫn có thể sai nghiệp vụ

Ngay cả khi kiểu đã được chốt là `Integer`, toán tử cộng hoặc `Integer.sum` sử dụng phép cộng `int` thông thường. Java không tự động báo lỗi khi `int` tràn trong phép cộng thường.

Tạo **`OverflowDemo.java`**:

```java
public class OverflowDemo {
    public static void main(String[] args) {
        int ordinary = Integer.MAX_VALUE + 1;
        System.out.println(ordinary);

        try {
            int checked = Math.addExact(Integer.MAX_VALUE, 1);
            System.out.println(checked);
        } catch (ArithmeticException ex) {
            System.out.println("overflow detected");
        }
    }
}
```

Biên dịch và chạy:

```bash
javac -encoding UTF-8 OverflowDemo.java
java OverflowDemo
```

`stdout`:

```text
-2147483648
overflow detected
```

Bài toán tổng quát không chỉ là làm phép cộng biên dịch, mà còn phải **định nghĩa chính sách số học**: chấp nhận tràn, từ chối tràn hay sử dụng kiểu có miền giá trị lớn hơn. `T extends Number` không giải quyết chính sách này.

Với số thập phân, cũng phải phân biệt lỗi làm tròn nhị phân của `double` với quy tắc scale/rounding của `BigDecimal`. Chẳng hạn `BigDecimal.divide` có thể ném `ArithmeticException` khi thương thập phân vô hạn nếu không chỉ định cách làm tròn phù hợp.

## 12. Khi nào không cần `<T extends Number>`?

Một generic bound chỉ có ích nếu nó thể hiện quan hệ kiểu mà API cần kiểm tra. Ví dụ:

```java
static double sum(List<? extends Number> values) {
    double total = 0.0;
    for (Number value : values) {
        total += value.doubleValue();
    }
    return total;
}
```

Đây là **phương thức minh họa** cần import `java.util.List` và đặt trong lớp. Nếu không có mối liên hệ kiểu cần giữ giữa đầu vào, đầu ra hoặc nhiều tham số, `List<? extends Number>` đủ rõ: danh sách là nguồn cung cấp các giá trị `Number`, còn thuật toán chuyển sang `double`.

Ngược lại, muốn **giữ kiểu `T` từ đầu vào đến kết quả**:

```java
static <T extends Number> T reduce(
        List<T> values,
        T zero,
        java.util.function.BinaryOperator<T> op) {
    T result = zero;
    for (T value : values) {
        result = op.apply(result, value);
    }
    return result;
}
```

Ở đây tham số kiểu `T` quan trọng vì liên kết kiểu phần tử, giá trị khởi tạo, toán tử và kết quả. Tuy nhiên, bound `extends Number` vẫn có thể không cần nếu phép toán chỉ dùng `op.apply`. Nếu muốn hàm xử lý tổng quát mọi giá trị có phép kết hợp, chữ ký `<T> T reduce(...)` còn linh hoạt hơn.

Nguyên tắc lựa chọn:

- **Chỉ cần đọc số để chuyển thành primitive:** nhận `Number` hoặc `? extends Number` là đủ.
- **Cần kết quả cụ thể `double` hoặc `long`:** khai báo rõ kiểu kết quả; ghi chú độ chính xác và overflow.
- **Cần giữ `T`:** truyền phép toán như `BinaryOperator<T>`, hoặc ràng buộc bằng interface mang phép toán thực sự.
- **Cần số thập phân chính xác:** chọn `BigDecimal` làm hợp đồng đầu vào/đầu ra và sử dụng API của nó, thay vì generic hóa quá mức.
- **Cần so sánh:** yêu cầu `Comparable<? super T>` hoặc nhận một `Comparator<? super T>` thích hợp.

## 13. Các lỗi biên dịch hoặc thiết kế thường gặp

| Viết sai hoặc suy luận sai | Vấn đề thực sự | Cách xử lý |
|---|---|---|
| `static <T extends Number> T add(T a,T b) { return a+b; }` | Toán tử `+` không hoạt động với `T`. | Chọn một kiểu số cụ thể, hoặc truyền phép cộng. |
| `Number a=1, b=2; a+b` | `Number` không được Java tự unbox. | Chuyển đổi tường minh và chấp nhận quy tắc số học đã chọn. |
| `T total = 0;` | Literal nguyên thủy `0` không tự có kiểu `T` bất kỳ. | Nhận `T zero` từ caller hoặc factory phù hợp. |
| `return (T) Double.valueOf(a.doubleValue()+b.doubleValue());` | Ép kiểu không an toàn; kết quả không nhất thiết thuộc kiểu `T` yêu cầu. | Trả `double` hoặc dùng toán tử được truyền vào trả `T`. |
| `a.add(b)` với `T extends Number` | `Number` không khai báo `add`. | Dùng `BigDecimal` cụ thể hoặc bound có `Addable<T>`. |
| `a.compareTo(b)` với `T extends Number` | `Number` không khai báo `compareTo`. | Thêm bound `Comparable<? super T>` hoặc nhận `Comparator`. |
| Lấy `doubleValue()` rồi cho rằng không đổi dữ liệu | Mất chính xác với số lớn hoặc phần thập phân. | Kiểm tra yêu cầu, dùng `BigDecimal`/`BigInteger` khi thích hợp. |
| Dùng `Integer::sum` và nghĩ không tràn | Phép cộng `int` có thể overflow. | `Math::addExact`, kiểu lớn hơn hoặc chính sách tràn phù hợp. |
| Viết generic cho phép cộng tiền rồi tự chuyển qua `double` | Có thể làm sai giá trị tiền tệ. | Thiết kế API nhận/trả `BigDecimal` với thang đo và quy tắc làm tròn rõ. |
| Dùng toán tử `+` trên lớp `BigDecimal` | Java không nạp chồng toán tử số học cho class. | `a.add(b)`. |
| Cho rằng compiler quyết định phép `+` theo class runtime | Java kiểm tra toán tử qua kiểu tĩnh và quy tắc unboxing/numeric promotion. | Thiết kế chữ ký số học rõ ở compile time. |

Các ví dụ lỗi được dùng để **nhận diện lỗi**, không phải mã Java để chạy nguyên trạng.

## 14. Quy trình thiết kế utility số học generic

Khi cần viết một hàm xử lý nhiều loại số, hãy xác định lần lượt:

1. **Đầu vào là những kiểu số nào?** Chỉ `Integer` và `Long`, hay cả `BigDecimal`, `BigInteger`, `Double` và các lớp con `Number` khác?
2. **Kết quả phải có kiểu gì?** `double`, `BigDecimal`, hay giữ nguyên kiểu `T`? Đây là quyết định API quan trọng nhất.
3. **Độ chính xác được phép mất bao nhiêu?** Tính tiền, số đếm rất lớn và phép đo gần đúng không nên dùng cùng chính sách.
4. **Số học có bị tràn không?** Nếu dùng `int`/`long`, xem xét `Math.addExact` hoặc chuyển kiểu theo nghiệp vụ.
5. **Phép toán lấy từ đâu?** Nếu dùng `T extends Number`, chỉ có các phương thức chuyển đổi của `Number`. Nếu muốn cộng trả `T`, phải có toán tử/strategy bổ sung.
6. **Có cần giữ quan hệ kiểu bằng tham số `T` không?** Nếu chỉ đọc và đổi thành `double`, wildcard `? extends Number` có thể đơn giản hơn.
7. **Giá trị `null`, danh sách rỗng và ngoại lệ xử lý thế nào?** Các điều kiện runtime cần được mô tả riêng.
8. **Có cần kiểm tra cảnh báo compiler không?** Tránh raw type, unchecked cast hoặc ép kiểu vòng vo nhằm che lỗi `T + T`.
9. **Thử dữ liệu biên**: số âm, số rất lớn, số thập phân, danh sách rỗng, tràn số và các giá trị không được phép.

## 15. Tự kiểm tra

**Câu 1.** Vì sao `Integer a=1, b=2; a+b` biên dịch nhưng `Number a=1, b=2; a+b` không biên dịch, dù đối tượng runtime có thể cùng là `Integer`?

**Câu 2.** Với `<T extends Number>`, compiler bảo đảm có những phương thức chuyển đổi nào? Nó có bảo đảm `add(T)` hay `compareTo(T)` không?

**Câu 3.** Nếu viết `double total = a.doubleValue() + b.doubleValue()`, điều gì đã thay đổi về hợp đồng kết quả? Tại sao điều đó có thể gây lỗi khi xử lý tiền hoặc số nguyên lớn?

**Câu 4.** Làm thế nào viết một hàm cộng hai `T` và trả đúng `T` mà không ép kiểu không kiểm tra, khi các lớp như `Integer` và `BigDecimal` không chia sẻ `add` chung?

**Câu 5.** Phân biệt `<T extends Number>` và `<T extends Number & Addable<T>>` khi viết `a.add(b)`.

**Câu 6.** `<T extends Number & Comparable<? super T>>` giúp làm gì mà `<T extends Number>` không giúp được?

**Gợi ý đáp án:** (1) `Integer` là lớp bao được Java unbox, còn `Number` không nằm trong nhóm kiểu có chuyển đổi unboxing trực tiếp. (2) Những phương thức như `intValue()` và `doubleValue()`; `Number` không khai báo `add` hoặc `compareTo`. (3) Phép toán chuyển thành `double`, không trả `T` và có thể mất chính xác. (4) Truyền `BinaryOperator<T>`, hoặc dùng một interface số học do ứng dụng định nghĩa. (5) Bound thứ hai bổ sung hợp đồng `Addable<T>` có `add`. (6) Bound `Comparable` bảo đảm lời gọi `compareTo` hợp lệ để so sánh thứ tự tự nhiên.

**Ghi nhớ:** **`<T extends Number>` không có nghĩa `T` có toán tử số học.** Java chỉ cho gọi những thành viên được các bounds bảo đảm, trong khi toán tử `+`, `-`, `*`, `/` phụ thuộc các kiểu số nguyên thủy và quy tắc unboxing cụ thể. Muốn xử lý nhiều kiểu số, hãy **chọn rõ kiểu kết quả và chính sách chính xác**, hoặc **cung cấp một hợp đồng phép toán** thông qua interface hay strategy như `BinaryOperator<T>`. Tránh cast không kiểm tra để giả lập một phép cộng tổng quát mà Java Generics không thực sự bảo đảm.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="generic-type-bounds-008">Giới hạn Throwable trong throws của phương thức generic</a>

<details>
<summary>Xem chi tiết</summary>

# Giới hạn `Throwable` trong `throws` của phương thức generic (Java)

## 1. Mục tiêu và ý nghĩa

Trong Java, phương thức generic có thể khai báo một **tham số kiểu đại diện cho loại ngoại lệ** mà phương thức có khả năng ném. Thay vì công bố một ngoại lệ quá rộng như `throws Exception`, ta có thể viết `throws E` để bảo toàn thông tin loại ngoại lệ từ thao tác bên trong đến nơi gọi.

Sau bài này, bạn cần hiểu:

- Điều kiện để một type variable hợp lệ trong `throws` và sự khác nhau giữa `Throwable`, `Exception`, `RuntimeException`.
- Compiler kiểm tra checked exception như thế nào khi `E` là một type variable.
- Cách truyền ngoại lệ qua callback/lambda bằng generic method mà không buộc caller xử lý `Exception` quá rộng.
- Vì sao không thể `catch (E e)` hoặc tạo `Exception<T>` kế thừa `Throwable`; những lỗi thường gặp về suy luận kiểu.

## 2. Quy tắc nền tảng của Java

Theo Java Language Specification (JLS) §8.4.6, mỗi loại nằm trong mệnh đề `throws` phải là **lớp ngoại lệ** hoặc **type variable có kiểu con của `Throwable`**. Vì vậy, cú pháp cơ bản là:

~~~java
static <E extends Throwable> void raise(E error) throws E {
    throw error;
}
~~~

Trong khai báo này:

1. `<E extends Throwable>` khai báo tham số kiểu của **phương thức**, giới hạn `E` ở các kiểu dẫn xuất của `Throwable`.
2. Tham số `E error` buộc đối tượng truyền vào có kiểu phù hợp với `E`.
3. `throw error` ném **đối tượng thực tế**; `throws E` công bố khả năng đó với compiler và caller.
4. `E` được xác định qua đối số, type argument tường minh hoặc ngữ cảnh suy luận kiểu. `E` không phải một lớp ngoại lệ mới.

Có thể dùng giới hạn cụ thể hơn:

~~~java
static <E extends Exception> void perform(E error) throws E {
    throw error;
}

static <E extends java.io.IOException> void ioOnly(E error) throws E {
    throw error;
}
~~~

| Khai báo giới hạn | `E` có thể biểu diễn | Ý nghĩa thực tế |
| --- | --- | --- |
| `E extends Throwable` | `Exception`, `Error` và các lớp con | Rộng nhất; chỉ chọn khi API thực sự cần truyền cả `Error`. |
| `E extends Exception` | Checked exception thuộc nhánh `Exception` và `RuntimeException` | Thường phù hợp cho callback có thể thất bại. |
| `E extends IOException` | `IOException` và lớp con | Giới hạn vào họ lỗi I/O. |
| `E extends RuntimeException` | Các ngoại lệ runtime | Chỉ thuộc nhóm unchecked exception; caller không bị buộc khai báo/bắt. |

**Checked và unchecked:** `RuntimeException` cùng các lớp con, và `Error` cùng các lớp con là unchecked. Những kiểu con khác của `Throwable` thuộc nhóm checked; compiler bắt buộc phải chứng minh chúng được bắt hoặc khai báo. Không phải mọi `E extends Exception` đều checked, vì `RuntimeException extends Exception`.

Một tham số kiểu không giới hạn được ngầm hiểu là `Object`, nên `<E> void f() throws E` **không hợp lệ**: compiler không thể bảo đảm `E` là kiểu con của `Throwable`. Tương tự, `String`, `Object` hoặc kiểu parameterized không phải ngoại lệ đều không thể đứng trong `throws`.

## 3. `throws E` lan truyền checked exception ra caller như thế nào?

Giả sử ta gọi:

~~~java
try {
    raise(new java.io.IOException("network unavailable"));
} catch (java.io.IOException ex) {
    System.out.println(ex.getMessage());
}
~~~

Compiler suy luận `E` từ đối tượng `IOException`. Do `IOException` là checked exception, nơi gọi phải bắt hoặc khai báo tương ứng. Mệnh đề `throws E` không làm ngoại lệ thành unchecked và không che được quy tắc kiểm tra checked exception.

Khi caller có thể chọn chính xác `E`, nó cũng có thể truyền type argument tường minh, chẳng hạn `GenericThrowsDemo.<IOException>raise(...)` nếu `raise` được khai báo trong lớp `GenericThrowsDemo`. Type argument phải thỏa ràng buộc kiểu và tương thích với các đối số thực tế.

**Chi tiết quan trọng:** `throws E` chỉ *khai báo loại ngoại lệ có thể phát sinh*. Nó không tự chứng minh mọi `throw new IOException(...)` trong thân phương thức đều hợp lệ khi `E extends Exception`, vì người gọi có thể chọn `E = java.sql.SQLException`. Thân phương thức phải ném đối tượng có kiểu tương thích với **mọi** cách gán `E` hợp lệ, hoặc khai báo/bắt ngoại lệ cụ thể riêng.

## 4. Ví dụ chạy được: giữ đúng loại checked exception qua callback

Trong ví dụ dưới đây, `ThrowingSupplier<T, E>` có thể trả về `T` hoặc ném `E`. Phương thức generic `execute` giữ nguyên hợp đồng này; lambda gọi một thao tác có `IOException`, nên caller bắt được `IOException` cụ thể.

Lưu tệp **GenericThrowsDemo.java**:

~~~java
import java.io.IOException;

public class GenericThrowsDemo {
    @FunctionalInterface
    interface ThrowingSupplier<T, E extends Exception> {
        T get() throws E;
    }

    static <T, E extends Exception> T execute(ThrowingSupplier<T, E> work) throws E {
        return work.get();
    }

    static String read(boolean fail) throws IOException {
        if (fail) {
            throw new IOException("read failed");
        }
        return "OK";
    }

    public static void main(String[] args) {
        try {
            System.out.println("Result: " + execute(() -> read(false)));
            execute(() -> read(true));
        } catch (IOException ex) {
            System.out.println("Caught IOException: " + ex.getMessage());
        }
    }
}
~~~

Biên dịch và chạy với Java 17 trở lên:

~~~bash
javac -encoding UTF-8 GenericThrowsDemo.java
java GenericThrowsDemo
~~~

Kết quả chuẩn:

~~~text
Result: OK
Caught IOException: read failed
~~~

Diễn tiến:

1. `read(false)` trả về chuỗi `OK`, nên `execute` trả về bình thường.
2. `read(true)` ném một `IOException`. `get() throws E` cho phép `execute` truyền lỗi này ra ngoài.
3. Compiler dùng ràng buộc của lambda để suy luận `E` thích hợp; `catch (IOException ex)` xử lý đúng lỗi trong ví dụ.
4. Nếu bỏ xử lý checked exception này, caller phải khai báo `throws IOException` (hoặc kiểu cha phù hợp), hoặc biên dịch sẽ thất bại.

So với API chỉ viết `static <T> T execute(Callable<T> work) throws Exception`, giải pháp giữ type variable `E` giúp caller sử dụng kiểu checked exception cụ thể hơn thay vì phải xử lý `Exception` bao quát. Lưu ý `java.util.concurrent.Callable.call()` được khai báo `throws Exception`; viết một interface có `E` riêng là cách giữ chính xác hợp đồng ngoại lệ của callback.

## 5. Các giới hạn cần nhớ

### 5.1. `E` phải có bound hợp lệ

Các khai báo sau không hợp lệ:

~~~java
// SAI: E mặc định extends Object, không bảo đảm là Throwable.
static <E> void bad1() throws E {}

// SAI: String không kế thừa Throwable.
static void bad2() throws String {}

// SAI: T chỉ là kiểu dữ liệu tùy ý, không thể được sử dụng làm loại ngoại lệ.
static <T> void bad3(T value) throws T {}
~~~

Cách đúng là khai báo giới hạn, ví dụ `<E extends Exception>` hoặc `<E extends Throwable>`, và bảo đảm thân phương thức tuân thủ hợp đồng.

### 5.2. Không thể bắt bằng `catch (E e)`

Đoạn sau **không biên dịch**:

~~~java
static <E extends Exception> void broken(E error) {
    try {
        throw error;
    } catch (E ex) { // SAI: không được dùng type variable ở kiểu của catch
        System.out.println(ex.getMessage());
    }
}
~~~

JLS §14.20 yêu cầu kiểu ngoại lệ trong `catch` phải là một kiểu lớp thích hợp, không phải type variable. Có thể bắt lớp xác định `catch (Exception ex)` hoặc để ngoại lệ lan ra bằng `throws E`. Khi cần bắt chung, cân nhắc rằng bắt `Exception` có phạm vi rộng và không bao gồm `Error`.

### 5.3. Không thể tạo lớp ngoại lệ generic

Khai báo sau **không hợp lệ**:

~~~java
// SAI: lớp generic không được trực tiếp hoặc gián tiếp kế thừa Throwable.
class AppFailure<T> extends Exception {}
~~~

Có thể định nghĩa một lớp ngoại lệ **không generic** và lưu thông tin bổ sung trong các trường có kiểu xác định; hoặc dùng đối tượng kết quả generic, ví dụ `Result<T>`, nếu nghiệp vụ cần mang dữ liệu có kiểu.

### 5.4. Không thể tự ý tạo `new E()` hoặc ném một subtype bất kỳ

~~~java
// SAI: IOException không nhất thiết là một E cụ thể.
static <E extends Exception> void brokenThrow() throws E {
    throw new java.io.IOException("read failed");
}

// SAI: Java không cho gọi constructor của type variable.
static <E extends Exception> void brokenNew() throws E {
    throw new E();
}
~~~

Dùng một đối tượng `E` do caller truyền vào, gọi callback khai báo `throws E`, hoặc nếu thực sự luôn ném `IOException` thì khai báo `throws IOException` trực tiếp. Đừng thêm generic vào API khi không có thông tin ngoại lệ cần truyền xuyên qua.

### 5.5. Checked exception khi override vẫn bị giới hạn

Generic không cho phép phương thức override mở rộng tập checked exception vượt quá hợp đồng của phương thức cha/interface. Khi override, compiler vẫn áp dụng quy tắc tương thích `throws` của JLS §8.4.8.3. Nếu một interface không cho ném checked exception, không thể biến việc override thành hợp lệ chỉ bằng cách khai báo thêm `<E extends Exception> ... throws E`.

## 6. Cẩn thận với suy luận kiểu và cách “sneaky throw”

Suy luận kiểu generic có quy tắc đặc biệt với type variable xuất hiện trong `throws`. Theo JLS §18.1.3 và §18.4, khi không có ràng buộc khác chặt hơn, compiler có thể chọn `RuntimeException` cho biến suy luận xuất hiện trong `throws` và có các upper bound phù hợp. Đây **không phải** phép chuyển một `IOException` thành `RuntimeException` tại runtime.

Một mẫu nguy hiểm là:

~~~java
@SuppressWarnings("unchecked")
static <E extends Throwable> void sneakyThrow(Throwable error) throws E {
    throw (E) error;
}
~~~

Ở đây kiểu `E` **không xuất hiện trong tham số truyền vào**; phép ép kiểu `(E) error` là unchecked. Lời gọi không nêu type argument có thể được suy luận với `E = RuntimeException`, trong khi đối tượng thật vẫn là một checked exception (chẳng hạn `IOException`) khi chạy. Điều này phá kỳ vọng xử lý checked exception ở call site; **không nên áp dụng như một cách “lách” checked exception trong API thông thường**.

Hãy so sánh với phiên bản an toàn hơn:

~~~java
static <E extends Throwable> void throwExact(E error) throws E {
    throw error;
}
~~~

Với `throwExact(new IOException("failed"))`, kiểu của đối số buộc suy luận phản ánh `IOException`. Không nên suy đoán kiểu suy luận bằng trực giác khi dùng lambda hoặc nhiều nguồn lỗi: hãy kiểm tra khai báo phương thức, các ràng buộc và lỗi biên dịch tại call site.

## 7. Khi nào nên dùng `throws E`?

**Nên dùng** khi thiết kế helper điều phối thao tác có thể ném những loại checked exception khác nhau: callback, template method, xử lý tài nguyên với contract riêng, hoặc phương thức chuyển tiếp lỗi đã được định kiểu. Ví dụ điển hình là cặp `ThrowingSupplier<T, E>` và `execute(...) throws E`.

**Nên dùng `throws IOException`, `SQLException` hoặc một lớp lỗi cụ thể** khi phương thức có một tập lỗi xác định và không cần bảo toàn kiểu ngoại lệ từ một callback hay đối số generic. API càng chính xác và đơn giản càng dễ sử dụng.

**Tránh** giới hạn `E extends Throwable` khi chỉ định xử lý các lỗi nghiệp vụ: `Throwable` bao gồm cả `Error` vốn thường biểu thị lỗi nghiêm trọng mà ứng dụng không nên bắt để phục hồi. `E extends Exception` thường truyền đạt ý định tốt hơn.

## 8. Kiểm tra hiểu bài

1. Vì sao `<E> void run() throws E` không biên dịch? **Vì `E` mặc định có bound `Object`, không được bảo đảm là subtype của `Throwable`.**
2. Vì sao `<E extends Exception> void run(E e) throws E` hợp lệ? **Vì `E` chắc chắn thuộc nhánh `Throwable`, và thân phương thức có thể ném chính đối tượng `e`.**
3. `throws E` có cho phép `catch (E e)` không? **Không.** Hai vị trí tuân theo quy tắc cú pháp/kiểu khác nhau.
4. Có thể khai báo `class Failure<T> extends Exception` không? **Không.** Generic class không được kế thừa `Throwable` trực tiếp hoặc gián tiếp.
5. Generic throws có loại bỏ nghĩa vụ xử lý checked exception không? **Không.** Trong cách dùng đúng, compiler vẫn kiểm tra checked exception sau khi xác định kiểu thích hợp tại call site.

## Tài liệu tham khảo

- [JLS Java SE 17 §8.4.6 — Method Throws](https://docs.oracle.com/javase/specs/jls/se17/html/jls-8.html#jls-8.4.6): cho phép type variable trong `throws` nếu là subtype của `Throwable`.
- [JLS Java SE 17 §14.20 — The try Statement](https://docs.oracle.com/javase/specs/jls/se17/html/jls-14.html#jls-14.20): cấm type variable làm kiểu của `catch`.
- [JLS Java SE 17 §18 — Type Inference](https://docs.oracle.com/javase/specs/jls/se17/html/jls-18.html): quy tắc suy luận, gồm `throws` bound và việc chọn `RuntimeException` khi thích hợp.
- [Oracle Java Tutorials — Restrictions on Generics](https://docs.oracle.com/javase/tutorial/java/generics/restrictions.html): không cho generic class kế thừa `Throwable`; minh họa `throws T` hợp lệ.


</details>

- [Quay lại đầu trang](#back-to-top)

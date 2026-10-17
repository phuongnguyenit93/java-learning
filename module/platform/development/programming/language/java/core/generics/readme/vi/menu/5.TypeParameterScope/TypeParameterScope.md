<a id="back-to-top"></a>

# Phạm vi biến kiểu

## Menu
- [Phạm vi tham số kiểu của lớp, giao diện và phương thức](#type-parameter-scope-001)
- [Che khuất tên kiểu và vì sao nên tránh dùng lại tên T](#type-parameter-scope-002)
- [Giới hạn dùng T của lớp trong trường và phương thức static](#type-parameter-scope-003)
- [Phương thức static khai báo tham số kiểu riêng](#type-parameter-scope-004)
- [Lớp thành viên bên trong và tham số kiểu của lớp bao](#type-parameter-scope-005)
- [Lớp lồng static và tham số kiểu độc lập](#type-parameter-scope-006)
- [Thành viên static dùng chung giữa các kiểu tham số hóa](#type-parameter-scope-007)

## <a id="type-parameter-scope-001">Phạm vi tham số kiểu của lớp, giao diện và phương thức</a>

<details>
<summary>Xem chi tiết</summary>

# Phạm vi tham số kiểu của lớp, giao diện và phương thức trong Java Generics

Khi nhìn thấy `T` trong mã Java, ta cần xác định **`T` được khai báo ở đâu và dùng được trong phạm vi nào**. Ví dụ `class Box<T>` khai báo một tham số kiểu của lớp; `interface Source<T>` khai báo tham số kiểu của giao diện; còn `static <T> T identity(T value)` khai báo tham số kiểu **riêng của phương thức**. Những tên này có thể trông giống nhau nhưng không đương nhiên là cùng một tham số kiểu.

Phạm vi (*scope*) giúp trình biên dịch giải quyết một tên kiểu thành đúng khai báo, từ đó kiểm tra chữ ký phương thức, constructor, kiểu trường, kế thừa và lời gọi. Hiểu đúng phạm vi đặc biệt quan trọng khi dùng `static`, viết phương thức generic bên trong lớp generic, khai báo lớp lồng nhau hoặc đặt tên tham số kiểu trùng nhau.

## 1. Phạm vi tham số kiểu nghĩa là gì?

**Tham số kiểu** (*type parameter*) là một tên kiểu được giới thiệu bởi một khai báo generic, thường viết trong dấu `<...>`:

```java
class Box<T> { ... }

interface Reader<T> { ... }

static <T> T identity(T value) { ... }
```

Ba dòng trên là **phác thảo cú pháp độc lập**, trong đó `...` không phải Java hoàn chỉnh để biên dịch. Ở cả ba nơi, `<T>` khai báo một tên mới. Trình biên dịch tìm đúng khai báo tương ứng mỗi khi nó gặp một lần sử dụng `T`.

Trong nội dung này, cần tách ba câu hỏi:

1. **Phạm vi tên**: vị trí mã nguồn nào có thể tham chiếu tới tham số kiểu đã khai báo?
2. **Ngữ cảnh hợp lệ**: kể cả khi ở trong cùng lớp, có được dùng tham số kiểu của lớp ở thành phần `static` hay không?
3. **Kiểu tại nơi sử dụng**: khi tạo `Box<String>`, trình biên dịch hiểu `T` theo `String` trong hợp đồng của lần sử dụng đó như thế nào?

Ba câu hỏi liên quan nhưng không trùng nhau. Một khai báo lớp có thể bao gồm phương thức `static` về mặt cấu trúc, nhưng phương thức `static` **không thể dùng trực tiếp tham số kiểu của lớp**. Đồng thời, khai báo `T` chỉ tồn tại theo quy tắc kiểm tra kiểu ở mã nguồn; không đồng nghĩa JVM lưu đối số kiểu của từng đối tượng như một kiểu runtime tách biệt.

| Khai báo | Ai sở hữu tham số kiểu? | Những vị trí thường dùng được | Giới hạn đáng chú ý |
|---|---|---|---|
| `class Box<T>` | Khai báo lớp `Box` | Trường instance, constructor, phương thức instance, thành phần trong instance context | Thành phần `static` không được dùng trực tiếp `T` của lớp. |
| `interface Source<T>` | Khai báo interface `Source` | Chữ ký phương thức instance, phương thức `default` và phương thức instance `private` | Trường trong interface là `static final`; phương thức `static` không được dùng trực tiếp `T` của interface. |
| `<T> T method(T value)` | Riêng khai báo phương thức | Kiểu trả về, tham số, bound và thân phương thức đó | `T` không tự mở rộng sang các phương thức khác. |
| `<U> Constructor(U x)` | Riêng constructor generic | Chữ ký và thân constructor đó | `U` của constructor độc lập với tham số kiểu của lớp. |

Các phần sau sẽ đi lần lượt từ lớp, giao diện đến phương thức và đối chiếu trong một chương trình chạy được.

## 2. Tham số kiểu của lớp: `class Box<T>`

Xét khai báo:

```java
final class Box<T> {
    private final T value;

    Box(T value) {
        this.value = value;
    }

    T get() {
        return value;
    }

    void printWith(T other) {
        System.out.println(value + " / " + other);
    }
}
```

Ở đây `T` được khai báo tại `class Box<T>`. Những lần dùng `T` ở trường `value`, tham số constructor, kết quả `get()` và tham số `printWith(T other)` đều tham chiếu tới **cùng tham số kiểu của lớp**.

Nếu sử dụng:

```java
Box<String> words = new Box<>("Java");
String value = words.get();
words.printWith("Generics");
```

thì đối số kiểu `String` được áp vào các vị trí vốn dùng `T` trong hợp đồng của `Box<String>`. `words.printWith(123)` không được chấp nhận vì `123` không phù hợp `String`.

Trong một phương thức **instance** của `Box<T>`, ta có thể khai báo biến cục bộ mang kiểu `T`:

```java
T copy() {
    T result = value;
    return result;
}
```

Đây là **phương thức minh họa đặt bên trong `Box<T>`**, không phải chương trình chạy độc lập. `T` vẫn có ý nghĩa dù phương thức `copy` không tự khai báo `<T>`, vì phương thức instance đang dùng tham số kiểu của lớp bao quanh.

### Vì sao `static` là ngoại lệ quan trọng?

Một thành phần `static` thuộc về chính lớp `Box` chứ không gắn với một instance `Box<String>` hoặc `Box<Integer>` cụ thể. Vì vậy mã sau **sai**:

```java
class WrongBox<T> {
    // static T shared;                  // Sai: tham chiếu T của lớp từ static field
    // static T getShared() { ... }      // Sai: T của lớp trong static method
}
```

Ngay cả khi các dòng bị chú thích nằm trong phạm vi khai báo của lớp, chúng không hợp lệ trong ngữ cảnh `static`. Điều này thường được gọi là lỗi **“non-static type variable T cannot be referenced from a static context”**.

Cách sửa là sử dụng một kiểu cụ thể hoặc để phương thức `static` khai báo **tham số kiểu riêng**:

```java
static <U> Box<U> of(U value) {
    return new Box<>(value);
}
```

Đoạn trên là một **phương thức đặt trong lớp `Box<T>`**. `U` thuộc riêng phương thức `of`; nó không phải `T` của lớp. Khi gọi `Box.of("Java")`, Java có thể suy luận `U = String` và trả `Box<String>`.

## 3. Ví dụ chạy được: lớp generic, phương thức instance và phương thức static

Tạo tệp **`ClassScopeDemo.java`**:

```java
public class ClassScopeDemo {
    static final class Holder<T> {
        private final T value;

        Holder(T value) {
            this.value = value;
        }

        T get() {
            return value;
        }

        <U> String describe(U label) {
            return label + ":" + value;
        }

        static <U> Holder<U> of(U value) {
            return new Holder<>(value);
        }

        final class Reader {
            T read() {
                return value;
            }
        }

        static final class Info {
            static String name() {
                return "Holder";
            }
        }
    }

    public static void main(String[] args) {
        Holder<String> text = Holder.of("Java");
        Holder<Integer> number = Holder.of(21);

        System.out.println(text.get());
        System.out.println(text.describe(17));
        System.out.println(text.new Reader().read());
        System.out.println(number.get() + 1);
        System.out.println(Holder.Info.name());
    }
}
```

Biên dịch và chạy trên Java 17 trở lên:

```bash
javac -encoding UTF-8 ClassScopeDemo.java
java ClassScopeDemo
```

Kết quả `stdout`:

```text
Java
17:Java
Java
22
Holder
```

Phân tích từng phạm vi:

- `Holder<T>` giới thiệu tham số kiểu của **lớp**.
- `T get()` sử dụng `T` của lớp, không cần khai báo tham số kiểu mới ở phương thức.
- `<U> String describe(U label)` khai báo `U` của **phương thức instance**, đồng thời vẫn dùng trường `value` có kiểu `T` của lớp. Khi gọi `text.describe(17)`, `U` có thể là `Integer` trong khi `T` của `text` vẫn là `String`.
- `static <U> Holder<U> of(U value)` không phụ thuộc `T` của một instance; nó có tham số kiểu `U` riêng.
- Lớp **inner không static** `Reader` có thể dùng `T` của `Holder<T>` bao quanh. Vì cần một đối tượng bao ngoài, ta tạo bằng `text.new Reader()`.
- Lớp lồng **static** `Info` không dùng `T` của lớp bao ngoài; nó hoạt động độc lập và có thể được gọi qua `Holder.Info.name()`.

Ví dụ cho thấy một lớp generic có thể chứa nhiều tên tham số kiểu thuộc những khai báo độc lập. Không cần để tất cả phương thức đều dùng cùng tên `T`.

## 4. Tham số kiểu của giao diện: `interface Source<T>`

Một interface generic giới thiệu tham số kiểu để các phương thức instance cùng tuân theo một hợp đồng:

```java
interface Source<T> {
    T read();

    void accept(T value);

    default boolean sameReference(T value) {
        return read() == value;
    }
}
```

`T` ở `read()`, `accept(T value)` và `sameReference(T value)` thuộc **khai báo interface `Source<T>`**. Phương thức `default` vẫn là phương thức instance, vì vậy có thể dùng `T` của interface và gọi phương thức instance khác như `read()`.

Một lớp triển khai có thể **chốt đối số kiểu**:

```java
final class StringSource implements Source<String> {
    private String value = "";

    @Override
    public String read() {
        return value;
    }

    @Override
    public void accept(String value) {
        this.value = value;
    }
}
```

Ở đây `StringSource` **không khai báo tham số kiểu** cho chính nó. Nó chọn `String` làm đối số cho `Source<T>`, nên các phương thức thực hiện interface phải nhận/trả kiểu tương ứng.

Lớp triển khai cũng có thể tiếp tục generic:

```java
final class MemorySource<E> implements Source<E> {
    private E value;

    @Override
    public E read() {
        return value;
    }

    @Override
    public void accept(E value) {
        this.value = value;
    }
}
```

Trong trường hợp này, `E` của `MemorySource<E>` là tham số kiểu **mới của lớp**, được truyền vào lần sử dụng `Source<E>`. Tên `E` không bắt buộc trùng với `T` ở khai báo interface gốc.

### Thành phần `static` bên trong interface

Một interface có thể có phương thức `static`, nhưng phương thức đó **không thể dùng trực tiếp tham số kiểu `T` của interface**:

```java
interface Factory<T> {
    // static T create() { return null; } // Sai: dùng T của interface ở static context

    static <U> U identity(U value) {
        return value;
    }
}
```

`<U>` ở `identity` là tham số kiểu riêng của phương thức. Gọi bằng `Factory.identity("Java")` hoặc `Factory.<String>identity("Java")`.

Các **trường của interface** mặc định là `public static final`, nên cũng không thể có trường instance kiểu `T` trong một interface. Trường hợp cần lưu trạng thái kiểu `T`, ta đặt trường trong **lớp triển khai**.

## 5. Ví dụ hoàn chỉnh về phạm vi kiểu trong interface

Lưu tệp **`InterfaceScopeDemo.java`**:

```java
public class InterfaceScopeDemo {
    interface Repository<T> {
        void save(T value);
        T latest();

        default String describe() {
            return "latest=" + latest();
        }

        static <U> U identity(U value) {
            return value;
        }
    }

    static final class MemoryRepository<T> implements Repository<T> {
        private T value;

        @Override
        public void save(T value) {
            this.value = value;
        }

        @Override
        public T latest() {
            return value;
        }
    }

    public static void main(String[] args) {
        Repository<String> words = new MemoryRepository<>();
        words.save("generics");

        System.out.println(words.describe());
        System.out.println(Repository.identity(17) + 1);
        System.out.println(
                Repository.<String>identity("Java").toUpperCase());
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 InterfaceScopeDemo.java
java InterfaceScopeDemo
```

`stdout`:

```text
latest=generics
18
JAVA
```

`Repository<T>` có hợp đồng instance dùng `T`. `MemoryRepository<T>` khai báo tham số kiểu riêng cho lớp triển khai và truyền tham số đó sang `Repository<T>`. Phương thức `Repository.identity` lại khai báo `<U>` riêng vì là thành phần `static` của interface.

Lưu ý `Repository<T>` ở ví dụ chỉ mô tả một kho dữ liệu trong bộ nhớ để học generic; không có nghĩa API này tự thực hiện truy cập cơ sở dữ liệu. Phần quan trọng là **phạm vi và quan hệ kiểu** giữa khai báo interface, lớp triển khai và lời gọi phương thức.

## 6. Tham số kiểu của phương thức: `<T>` có phạm vi riêng

Một phương thức generic tự khai báo danh sách tham số kiểu của nó **ngay trước kiểu trả về**:

```java
static <T> T first(T a, T b) {
    return a;
}
```

Trong chữ ký này, `<T>` thuộc riêng phương thức `first`. Nó dùng được ở kiểu tham số `a`, `b`, kiểu trả về và trong thân phương thức. Các phương thức khác không tự động biết tên `T` đó.

Một phương thức instance cũng có thể khai báo tham số kiểu riêng:

```java
<U> String format(U prefix) {
    return prefix.toString();
}
```

Đây là **phương thức minh họa trong một lớp**. Nó không cần `static` mới được gọi là generic: dấu hiệu quyết định là nó khai báo `<U>`.

Khác biệt:

| Phương thức | Có tự khai báo tham số kiểu? | Nguồn tham số kiểu |
|---|---|---|
| `T get()` trong `class Box<T>` | Không | `T` thuộc lớp `Box<T>`. |
| `<U> U echo(U value)` | Có | `U` thuộc riêng phương thức `echo`. |
| `static <U> U identity(U value)` | Có | `U` thuộc riêng phương thức `static`. |
| `void set(T value)` trong `class Box<T>` | Không | `T` thuộc lớp; phương thức dùng lại. |
| `<K,V> V choose(K key,V value)` | Có | `K` và `V` đều thuộc phương thức. |

**Một phương thức generic có thể nằm trong lớp không generic.** Chẳng hạn một lớp tiện ích bình thường có thể khai báo `static <T> T identity(T value)`.

### Lời gọi khác nhau có thể suy luận kiểu khác nhau

```java
String text = Utils.identity("Java");
Integer version = Utils.identity(17);
```

Đây là **đoạn minh họa** giả sử `Utils` có phương thức `static <T> T identity(T value)`. Trình biên dịch suy luận `T` ở từng lời gọi, chứ không thay đổi hay khai báo lại phương thức trong mã nguồn.

Có thể chỉ định đối số kiểu tường minh:

```java
String text = Utils.<String>identity("Java");
```

Dấu `<String>` nằm trước tên phương thức là **đối số kiểu lúc gọi**. Còn `<T>` trong khai báo phương thức là **tham số kiểu được khai báo**.

## 7. Ví dụ chạy được: nhiều phương thức generic độc lập

Lưu tệp **`MethodScopeDemo.java`**:

```java
public class MethodScopeDemo {
    static <T> T first(T left, T right) {
        return left;
    }

    static <T extends Comparable<? super T>> T larger(T a, T b) {
        return a.compareTo(b) >= 0 ? a : b;
    }

    static <K, V> String label(K key, V value) {
        return key + "=" + value;
    }

    static final class Folder<T> {
        private final T item;

        Folder(T item) {
            this.item = item;
        }

        <U> String describe(U prefix) {
            return prefix + ":" + item;
        }

        static <U> Folder<U> of(U item) {
            return new Folder<>(item);
        }
    }

    public static void main(String[] args) {
        System.out.println(first("A", "B"));
        System.out.println(larger(5, 9));
        System.out.println(label("id", 42));

        Folder<Integer> folder = Folder.of(7);
        System.out.println(folder.describe("number"));
        System.out.println(Folder.of("X").describe(2026));
    }
}
```

Biên dịch/chạy:

```bash
javac -encoding UTF-8 MethodScopeDemo.java
java MethodScopeDemo
```

`stdout`:

```text
A
9
id=42
number:7
2026:X
```

Hai phương thức `first` và `larger` đều đặt tên tham số kiểu là `T`, nhưng đó là **hai khai báo độc lập**. Giới hạn `T extends Comparable<? super T>` của `larger` không áp dụng cho `first`. Tương tự, `T` của `Folder<T>` và `U` của `describe` không phải một tham số kiểu.

Việc dùng tên `T` nhiều lần ở các phạm vi khác nhau là hợp lệ. Tuy nhiên, khi các khai báo lồng nhau quá sâu, nên dùng tên rõ ràng để tránh hiểu nhầm.

## 8. Đặt tên trùng nhau và hiện tượng che khuất (*shadowing*)

Một phương thức generic **có thể** khai báo tham số kiểu cùng tên với tham số kiểu của lớp bao quanh:

```java
class Envelope<T> {
    private final T data;

    Envelope(T data) {
        this.data = data;
    }

    T get() {
        return data;
    }

    <T> T echo(T value) {
        return value;
    }
}
```

Trong phương thức `echo`, `T` ở `<T> T echo(T value)` thuộc **phương thức** và che khuất tham số kiểu cũng tên của lớp `Envelope<T>`. Nó **không phải** loại dữ liệu đang được lưu trong `data`.

Chạy ví dụ hoàn chỉnh **`ShadowingDemo.java`**:

```java
public class ShadowingDemo {
    static final class Envelope<T> {
        private final T data;

        Envelope(T data) {
            this.data = data;
        }

        T get() {
            return data;
        }

        <T> T echo(T input) {
            return input;
        }

        <U> String describe(U label) {
            return label + ":" + data;
        }
    }

    public static void main(String[] args) {
        Envelope<String> envelope = new Envelope<>("Java");
        Integer result = envelope.echo(10);

        System.out.println(envelope.get());
        System.out.println(result + 1);
        System.out.println(envelope.describe("item"));
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 ShadowingDemo.java
java ShadowingDemo
```

`stdout`:

```text
Java
11
item:Java
```

`Envelope<String>` lưu chuỗi, nhưng `envelope.echo(10)` vẫn trả về `Integer` vì phương thức này có **`<T>` riêng che khuất `T` của lớp**.

Thiết kế như vậy hợp lệ nhưng dễ gây nhầm lẫn. Thông thường nên tránh khai báo trùng tên ở phạm vi lồng nhau. Viết `<U> U echo(U input)` làm rõ đây là một tham số kiểu khác, đồng thời giữ tên `T` của lớp cho phần dữ liệu lưu trữ.

**Không thể giả định hai chữ `T` là cùng tham số kiểu chỉ vì viết giống nhau.** Phải tìm vị trí khai báo gần nhất được phép nhìn thấy trong phạm vi hiện tại.

## 9. Lớp lồng nhau: `inner class` và `static nested class`

Giả sử:

```java
class Outer<T> {
    private final T value;

    Outer(T value) {
        this.value = value;
    }

    class Inner {
        T read() {
            return value;
        }
    }

    static class Nested<U> {
        private final U item;

        Nested(U item) {
            this.item = item;
        }

        U read() {
            return item;
        }
    }
}
```

Đây là **khai báo lớp minh họa độc lập**. Hai lớp lồng khác nhau:

- `Inner` là **non-static inner class**. Mỗi đối tượng `Inner` có liên hệ với một đối tượng `Outer<T>` bao ngoài, nên phương thức instance của `Inner` có thể dùng tham số kiểu `T` và trường `value` của đối tượng bao ngoài.
- `Nested<U>` là **static nested class**, có tham số kiểu `U` của **chính nó**. Nó không được dùng trực tiếp `T` của `Outer<T>`, vì bản thân `Nested` không gắn với đối tượng `Outer<T>` cụ thể.

Cách tạo tương ứng (đặt trong một phương thức):

```java
Outer<String> outer = new Outer<>("Java");
Outer<String>.Inner inner = outer.new Inner();
String a = inner.read();

Outer.Nested<Integer> nested = new Outer.Nested<>(5);
Integer b = nested.read();
```

`Outer<String>.Inner` biểu thị kiểu inner class gắn với cách tham số hóa `Outer<String>`. `Outer.Nested<Integer>` dùng tham số kiểu `U` của static nested class, không phụ thuộc `T` của lớp bao.

Không viết `Outer<String>.Nested<Integer>`: static nested class được truy cập qua tên của lớp bao ở dạng không tham số hóa, vì bản thân nó không thuộc một đối tượng `Outer<String>`.

Nếu inner class tự khai báo một tham số kiểu cùng tên `T` thì có thể xuất hiện che khuất, tương tự trường hợp phương thức ở phần trước. Vì vậy cách đặt tên tham số kiểu của các lớp lồng nhau ảnh hưởng trực tiếp tới khả năng đọc mã.

## 10. Constructor generic cũng có phạm vi riêng

Một constructor có thể khai báo tham số kiểu **khác với tham số kiểu của lớp**. Ví dụ sau sử dụng đầu vào kiểu `U` và hàm chuyển đổi để tạo giá trị kiểu `T` của lớp:

```java
import java.util.function.Function;

final class ConvertedBox<T> {
    private final T value;

    <U> ConvertedBox(
            U input,
            Function<? super U, ? extends T> converter) {
        this.value = converter.apply(input);
    }

    T get() {
        return value;
    }
}
```

Đây là **khai báo lớp minh họa**; nó cần import như ở đầu đoạn. `T` thuộc lớp `ConvertedBox<T>`, còn `U` chỉ thuộc constructor. Với ví dụ sử dụng bên trong phương thức:

```java
ConvertedBox<Integer> parsed =
        new ConvertedBox<>("123", Integer::parseInt);
System.out.println(parsed.get()); // 123
```

Constructor nhìn thấy cả `T` của lớp (trong kiểu đích `Function<? super U, ? extends T>`) lẫn `U` của chính constructor. Điều này giúp diễn đạt hai vai trò kiểu khác nhau: kiểu dữ liệu ban đầu và kiểu dữ liệu được lưu giữ.

Việc constructor generic có thể khai báo tham số kiểu riêng không làm thay đổi quy tắc: kiểu instance cuối cùng vẫn được mô tả bởi đối số kiểu của **lớp**, ví dụ `ConvertedBox<Integer>`.

## 11. Phạm vi trong giới hạn kiểu (*bounds*)

Tham số kiểu còn có thể được sử dụng ngay trong giới hạn kiểu của chính nó:

```java
static <T extends Comparable<T>> T max(T first, T second) {
    return first.compareTo(second) >= 0 ? first : second;
}
```

Đây là **phương thức minh họa bên trong một lớp**. `T` được khai báo trong `<T extends Comparable<T>>` và có thể tham chiếu trong giới hạn `Comparable<T>`. Đây là một mẫu **self-referential bound** hợp lệ, dùng khi muốn các giá trị kiểu `T` hỗ trợ so sánh với nhau.

Một dạng linh hoạt hơn thường gặp là:

```java
static <T extends Comparable<? super T>> T max(
        T first, T second) {
    return first.compareTo(second) >= 0 ? first : second;
}
```

`? super T` cho phép hợp đồng so sánh nhận `T` hoặc một kiểu cha thích hợp. Vẫn chỉ có **một khai báo `T` cho phương thức `max`**; những lần xuất hiện tiếp theo của `T` trong bound, tham số và kiểu trả về đều thuộc khai báo này.

Nếu viết `class Numeric<T extends Number>`, giới hạn ấy thuộc **tham số kiểu của lớp**, nên ràng buộc mọi cách sử dụng `Numeric<Integer>` hay `Numeric<Double>`. Nó không tự động áp vào một phương thức generic riêng khai báo `<U>`, trừ khi phương thức đó cũng có ràng buộc thích hợp.

## 12. Phạm vi kiểu không đồng nghĩa thông tin kiểu luôn tồn tại ở runtime

Một tên `T` hợp lệ trong phạm vi của lớp hoặc phương thức giúp **trình biên dịch** kiểm tra kiểu. Tuy nhiên, Java dùng **type erasure** cho phần lớn generics: khi biên dịch, tham số kiểu được xóa thành giới hạn phù hợp (hoặc `Object` khi không có bound), đồng thời compiler có thể thêm cast và bridge method để giữ hành vi kiểu.

Ví dụ:

```java
static <T> void showClass(T value) {
    System.out.println(value.getClass().getSimpleName());
}
```

Phương thức này có thể đọc **lớp runtime của đối tượng `value`** qua `getClass()`, nếu `value` không phải `null`. Nhưng điều đó **không** có nghĩa ta có thể lấy `T.class`:

```java
// Sai ngay cả khi T đã được khai báo trong phạm vi:
// Class<?> token = T.class;
// T instance = new T();
```

`T.class` và `new T()` không hợp lệ do tham số kiểu không tự cung cấp class literal hay constructor runtime có thể gọi theo cách này.

Nếu thuật toán cần biết kiểu runtime, hãy nhận một **type token** từ người gọi:

```java
static <T> boolean matches(Class<T> type, Object value) {
    return type.isInstance(value);
}
```

Đoạn này là **phương thức minh họa trong một lớp**; `String.class` có thể được truyền cho `Class<String>`:

```java
boolean yes = matches(String.class, "Java"); // true
boolean no = matches(String.class, 42);       // false
```

Hai lời gọi nằm trong một phương thức chứa hoặc tham chiếu tới `matches`. Tham số kiểu `T` vẫn có phạm vi riêng ở phương thức; đối tượng `Class<T>` được truyền vào mang thông tin runtime cần thiết để kiểm tra bằng `isInstance`.

Điều quan trọng là **lỗi dùng một tên ngoài phạm vi** và **hạn chế do type erasure** là hai loại vấn đề khác nhau. Chẳng hạn `static T get()` trong `class Box<T>` vi phạm quy tắc ngữ cảnh static; còn `new T()` không hợp lệ ngay cả bên trong phương thức instance đang nhìn thấy `T`.

## 13. Những sai lầm phổ biến về phạm vi

| Tình huống | Vì sao sai hoặc dễ nhầm? | Cách sửa |
|---|---|---|
| `class Box<T> { static T value; }` | Thành phần static không thể dùng `T` của lớp. | Dùng trường instance `T value` hoặc kiểu cụ thể cho trường static. |
| `class Box<T> { static T of(...) { ... } }` | Phương thức static không kế thừa `T` của lớp theo cách đó. | Khai báo `static <U> Box<U> of(U value)` nếu cần factory generic. |
| `interface Source<T> { static T get() { ... } }` | Phương thức static của interface không thể dùng `T` của interface. | Khai báo tham số kiểu riêng của phương thức hoặc chọn kiểu cụ thể. |
| `<T> T identity(T x)` được hiểu là dùng `T` của mọi lớp | `<T>` tự tạo một tham số kiểu của riêng phương thức. | Xem chính xác nơi khai báo và phạm vi. |
| Phương thức cùng tên `T` trong `class Box<T>` | Tham số kiểu phương thức che khuất tham số kiểu lớp. | Chọn tên `<U>` để biểu đạt sự độc lập. |
| Viết `T value;` trong một lớp không có khai báo `T` | Không tồn tại tên `T` trong phạm vi hợp lệ. | Dùng kiểu cụ thể hoặc khai báo tham số kiểu trên lớp/phương thức thích hợp. |
| `Outer<String>.Nested<Integer>` cho static nested class | Static nested class không thuộc một tham số hóa instance cụ thể của `Outer`. | Dùng `Outer.Nested<Integer>`. |
| Nghĩ `Inner` và `Nested` đều nhìn thấy `T` của `Outer<T>` | Chỉ inner class không static có thể dùng trực tiếp `T` của lớp bao trong ngữ cảnh phù hợp. | Với static nested class, khai báo tham số kiểu riêng nếu cần. |
| Dùng `new T()` khi `T` có trong phạm vi | Type erasure không cung cấp constructor của `T`. | Truyền `Supplier<T>` hoặc factory phù hợp. |
| Dùng `T.class` | Tham số kiểu không thể làm class literal. | Truyền `Class<T>` khi cần kiểm tra runtime. |
| Nghĩ hai `<T>` ở hai phương thức là cùng một biến kiểu | Mỗi danh sách tham số kiểu là khai báo riêng. | Xem từng chữ ký độc lập. |

### Cách đọc nhanh một đoạn mã generic

Khi gặp tên kiểu như `T`, `E`, `K` hoặc `V`, kiểm tra theo thứ tự:

1. Tìm **khai báo gần nhất** giới thiệu tên đó: `class X<T>`, `interface X<T>`, `<T> method(...)` hoặc constructor generic.
2. Xác định vị trí hiện tại là **instance** hay **static**. Nếu là static, không được dùng trực tiếp tham số kiểu của lớp/interface bao.
3. Xem có **tham số kiểu cùng tên** ở một phạm vi bên trong che khuất khai báo ngoài không.
4. Nếu đang trong **inner class**, kiểm tra khả năng truy cập kiểu từ lớp bao; nếu là **static nested class**, nó không được dùng trực tiếp tham số kiểu của lớp bao.
5. Phân biệt tên tham số kiểu trong **khai báo** và đối số kiểu ở **nơi sử dụng** như `Box<String>`.
6. Nếu gặp `new T()`, `T.class` hoặc kiểm tra loại tham số hóa tại runtime, xem lại giới hạn của type erasure.

## 14. Tự kiểm tra

**Câu 1.** Trong `class Store<T>`, vì sao `T get()` hợp lệ nhưng `static T getDefault()` không hợp lệ?

**Câu 2.** Với đoạn sau, chữ `T` ở `echo` thuộc khai báo nào?

```java
class Store<T> {
    <T> T echo(T value) {
        return value;
    }
}
```

**Câu 3.** `interface Mapper<I,O> { O map(I value); }` có thể khai báo `static I defaultInput()` không? Nếu cần một generic static method, phải làm gì?

**Câu 4.** Trong `class Outer<T>`, vì sao `class Inner` có thể dùng `T`, còn `static class Nested` không thể dùng trực tiếp `T` của `Outer<T>`?

**Câu 5.** Hai phương thức `static <T> T left(T x)` và `static <T> T right(T x)` có chia sẻ cùng một tham số kiểu `T` không?

**Câu 6.** `T.class` không hợp lệ trong phương thức đã khai báo `<T>` là do thiếu phạm vi hay do cách Java thực thi generics?

**Đáp án định hướng:**

1. `get()` là phương thức instance nên dùng được `T` của lớp; `getDefault()` là phương thức static, không gắn với cách tham số hóa instance.
2. `<T>` của phương thức `echo` tạo tham số mới che khuất `T` của lớp `Store<T>`.
3. Không, vì `I` thuộc interface. Hãy khai báo tham số kiểu riêng như `static <U> U identity(U value)`.
4. Inner class không static gắn với đối tượng lớp bao; static nested class độc lập với tham số kiểu của lớp bao.
5. Không. Mỗi phương thức khai báo `<T>` riêng.
6. Do hạn chế liên quan đến type erasure/reifiability tại runtime, không phải do `T` nằm ngoài phạm vi.

**Ghi nhớ:** Phạm vi tham số kiểu được xác định bởi **nơi nó được khai báo**. Tham số kiểu của lớp/interface dùng được trong những hợp đồng instance phù hợp; phương thức generic và constructor generic có tham số kiểu riêng; thành phần `static` không lấy trực tiếp tham số kiểu của lớp hoặc interface; khai báo lồng nhau có thể che khuất tên bên ngoài. Phân biệt rõ các quy tắc này sẽ giúp đọc và viết Java Generics chính xác, đặc biệt trong code có nhiều lớp, interface và utility method.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="type-parameter-scope-002">Che khuất tên kiểu và vì sao nên tránh dùng lại tên T</a>

<details>
<summary>Xem chi tiết</summary>

# Che khuất tên kiểu và vì sao nên tránh dùng lại tên T trong Java Generics

Trong Java Generics, `T` là **tên của một biến kiểu (type parameter)**, không phải một kiểu cố định hay một biến chứa dữ liệu. Tên ấy có **phạm vi (scope)** giống những tên khác trong chương trình. Một generic class có thể khai báo `T`, rồi một generic method hoặc inner class bên trong lại khai báo **một `T` mới**. Tại phạm vi bên trong, `T` mới *che khuất* (*shadow*) `T` bên ngoài.

Cú pháp này có thể hợp lệ, nhưng rất dễ khiến người đọc tưởng hai ký hiệu `T` là một kiểu duy nhất. Kết quả là sai hợp đồng API, phép gán bị compiler từ chối hoặc những dòng cast không cần thiết được thêm vào để “chữa” lỗi. Bài học tập trung vào **cơ chế shadowing của type parameter**, cách nhận biết phạm vi, phân biệt các `T` cùng tên, giới hạn kiểu và cách đặt tên giúp thiết kế generic code dễ hiểu, an toàn.

Các ví dụ chạy độc lập dùng Java 17 trở lên. Các dòng cố ý gây lỗi biên dịch sẽ được comment và giải thích riêng.

## 1. Nhắc lại: tên T xuất hiện ở đâu?

Một generic class đơn giản:

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
```

`T` được khai báo ngay sau tên class ở `Box<T>`. Mỗi lần `T` xuất hiện trong các thành viên instance nêu trên đều trỏ tới **chính type parameter của class**. Với `Box<String>`, compiler xem `Box.get()` trả `String`, còn `Box<Integer>` cho kết quả kiểu `Integer`.

So sánh:

```java
class Utilities {
    static <T> T identity(T value) {
        return value;
    }
}
```

Ở đây `<T>` nằm trước kiểu trả về của method. `T` là **type parameter của method**; class `Utilities` không khai báo `T` nào cả. Không có shadowing vì không có tên type parameter cùng tên ở một phạm vi bao quanh đang được truy cập.

Còn ví dụ sau:

```java
class Box<T> {
    <T> T echo(T input) {
        return input;
    }
}
```

Hai dấu `<T>` là **hai khai báo khác nhau**:

- `Box<T>` khai báo một `T` thuộc class.
- `<T> T echo(T input)` khai báo một `T` mới thuộc method, làm cho những lần dùng tên đơn `T` trong phạm vi method biểu thị **T của method**.
- `T` của method được suy luận theo mỗi lần gọi `echo`, độc lập với type argument của một `Box<T>` instance.
- Không có cơ chế “compiler tự nhận ra hai T giống nhau” chỉ nhờ tên trùng.

Việc hiểu chính xác **nơi khai báo** quan trọng hơn việc nhớ tên `T`.

## 2. Shadowing là gì, khác gì reuse tên ở phạm vi rời nhau?

Shadowing xảy ra khi một khai báo tên ở phạm vi bên trong che một khai báo cùng tên ở phạm vi bao quanh. Với generic type parameters, cấu trúc rất dễ nhầm:

```java
class Holder<T> {
    <T> T echo(T input) {
        return input;
    }
}
```

Tên `T` ở khai báo method che tên `T` của class trong vùng mà type parameter của method có hiệu lực. Lúc compiler phân giải `T input` hoặc `T` là kiểu trả về của `echo`, nó tìm `T` tại phạm vi method trước.

Không phải cứ thấy hai chữ `T` trong dự án là có shadowing. Hai generic methods độc lập trong cùng một class, chẳng hạn `<T> T first(T x)` và `<T> T last(T x)`, có các type-parameter scopes riêng biệt, **không lồng nhau**. Việc mỗi method dùng tên thông dụng `T` là hoàn toàn bình thường. Vấn đề nằm ở việc có **hai khai báo cùng tên với các phạm vi chồng lên nhau**, khiến người đọc dễ hiểu sai một `T` là của cấp nào.

Một type argument cũng không phải khai báo mới:

```java
class Holder<T> {
    java.util.List<T> asList(T item) {
        return java.util.List.of(item);
    }
}
```

`List<T>` ở đây đang **sử dụng** `T` của `Holder<T>`; `List<T>` không khai báo thêm `T` để che tên. Dấu `<...>` trong Java vừa có thể dùng để *khai báo type parameter* (sau class/method), vừa để *truyền type argument* (sau tên type generic), nên phải nhìn **vị trí cú pháp** trước khi kết luận.

## 3. Ví dụ chạy được: hai T cùng tên nhưng độc lập

Tạo `ShadowingDemo.java`:

```java
public class ShadowingDemo {
    static final class Holder<T> {
        private final T stored;

        Holder(T stored) {
            this.stored = stored;
        }

        T get() {
            return stored;
        }

        <T> T echo(T input) {
            return input;
        }
    }

    public static void main(String[] args) {
        Holder<String> holder = new Holder<>("Java");

        String stored = holder.get();
        Integer echoedNumber = holder.echo(42);
        Boolean echoedBoolean = holder.echo(true);

        System.out.println(stored);
        System.out.println(echoedNumber);
        System.out.println(echoedBoolean);
    }
}
```

Biên dịch và chạy bằng JDK 17+:

```bash
javac ShadowingDemo.java
java ShadowingDemo
```

`stdout`:

```text
Java
42
true
```

Phân tích theo hai phạm vi:

| Ký hiệu trong source | Được khai báo tại | Kiểu ở invocation đang xét |
| --- | --- | --- |
| `T` trong `Holder<T>` | Class `Holder` | `String` vì receiver là `Holder<String>` |
| `T stored` | Dùng `T` của class | `String` |
| `T get()` | Dùng `T` của class | Trả `String` |
| `<T>` trên `echo` | Khai báo `T` mới thuộc method | Suy luận lại trên từng lời gọi |
| `T input` trong `echo` | Dùng `T` của method | `Integer` với `echo(42)`, `Boolean` với `echo(true)` |
| `T` trả về từ `echo` | Dùng `T` của method | Tương ứng `Integer`, `Boolean` |

Hai lời gọi `echo` không hề thay type argument của `Holder<String>`. Đối tượng vẫn lưu `String` trong `stored`. Method `echo` đơn thuần nhận và trả lại một giá trị thuộc **kiểu riêng do method suy luận**.

Nếu người đọc thấy `Holder<String>` và nghĩ `echo` “chỉ nhận String” thì họ đã nhầm `T` của class với `T` của method. Đây là một lý do nên tránh khai báo lại tên `T` ở phạm vi bên trong khi hai loại dữ liệu độc lập.

## 4. Lỗi nguy hiểm hơn: tưởng T trong method là T của field

Xét class minh họa:

```java
class ConfusingStore<T> {
    private T stored;

    ConfusingStore(T stored) {
        this.stored = stored;
    }

    <T> void replace(T incoming) {
        // this.stored = incoming; // Lỗi biên dịch.
    }
}
```

Người viết có thể nghĩ `replace` nhận cùng kiểu với `stored`. Nhưng:

1. `stored` được khai báo theo type parameter `T` của **class**.
2. `<T>` ngay trước `void replace` khai báo type parameter **khác**.
3. `incoming` mang `T` của **method**, vì tên đó che khuất `T` của class ở đây.
4. Từ góc nhìn compiler, không có ràng buộc bảo đảm một giá trị kiểu method `T` có thể gán cho field kiểu class `T`.
5. Compiler từ chối `this.stored = incoming` dù cả hai chữ trong source đều viết là `T`.

Điểm này không phụ thuộc vào việc runtime có tình cờ truyền cùng loại đối tượng hay không. Thân method phải đúng với mọi type arguments được cho phép bởi khai báo của nó.

### Cách sửa đúng khi method phải nhận cùng kiểu với field

Bỏ khai báo `<T>` thừa:

```java
class Store<T> {
    private T stored;

    Store(T stored) {
        this.stored = stored;
    }

    void replace(T incoming) {
        this.stored = incoming;
    }

    T get() {
        return stored;
    }
}
```

Lúc này `T incoming` tham chiếu trực tiếp tới `T` của class. Với `Store<String>`, compiler chỉ cho `replace` nhận giá trị phù hợp với `String`.

Cần nhớ thêm: `this.` chọn **field/member của object**, nhưng **không biến tên kiểu T trong method thành T của class**. Trong ví dụ gây lỗi, `this.stored` vẫn có kiểu của class, `incoming` vẫn có kiểu của method. Viết thêm `this.` không giải quyết quan hệ kiểu đã bị tách ra.

## 5. Cách sửa khi method thực sự có kiểu độc lập

Đôi khi method cố ý nhận kiểu khác với dữ liệu mà class đang giữ. Khi đó giữ generic method là đúng, nhưng **đổi tên type parameter** cho dễ hiểu.

Tạo `ClearNamesDemo.java`:

```java
import java.util.function.Function;

public class ClearNamesDemo {
    static final class Holder<T> {
        private final T stored;

        Holder(T stored) {
            this.stored = stored;
        }

        T get() {
            return stored;
        }

        <R> R map(Function<? super T, ? extends R> mapper) {
            return mapper.apply(stored);
        }

        <U> U echo(U input) {
            return input;
        }
    }

    public static void main(String[] args) {
        Holder<String> holder = new Holder<>("Java");

        Integer length = holder.map(String::length);
        Boolean yes = holder.echo(true);

        System.out.println(holder.get());
        System.out.println(length);
        System.out.println(yes);
    }
}
```

Chạy:

```bash
javac ClearNamesDemo.java
java ClearNamesDemo
```

`stdout`:

```text
Java
4
true
```

Bây giờ vai trò từng biến kiểu sáng rõ:

- `T` của `Holder<T>` là **kiểu giá trị đang lưu**, ví dụ `String`.
- `R` của `<R> R map(...)` là **kiểu kết quả của phép biến đổi**. `Function<? super T, ? extends R>` nối đầu vào `T` của class với đầu ra `R` của method.
- `U` của `<U> U echo(U input)` là kiểu đầu vào/đầu ra riêng của method `echo`, hoàn toàn độc lập với `T`.
- Không có tên bị che khuất. Người đọc dễ thấy `map` phụ thuộc dữ liệu trong object còn `echo` thì không.

Trong nhiều API, tên có ý nghĩa hơn `T`/`U` cũng rất hữu ích: `<ID, ENTITY>`, `<INPUT, OUTPUT>` hoặc `<SOURCE, RESULT>`. Tuy nhiên, tên ngắn theo quy ước `T`, `E`, `K`, `V`, `R`, `U` vẫn phù hợp khi chữ ký đơn giản. Vấn đề cần tránh là **tái khai báo cùng tên ở phạm vi lồng nhau**, không phải cấm hoàn toàn ký tự `T`.

## 6. Bound của hai T không trở thành một bound chung

Khi type parameter có giới hạn `extends`, việc dùng lại tên càng dễ gây hiểu nhầm.

```java
class NumberHolder<T extends Number> {
    private final T number;

    NumberHolder(T number) {
        this.number = number;
    }

    <T extends CharSequence> int lengthOf(T text) {
        return text.length();
    }

    Number asNumber() {
        return number;
    }
}
```

Ở đây hai `T` khác nhau:

- `T extends Number` của class ràng buộc giá trị lưu trong `number`; compiler cho phép xem nó là `Number`.
- `T extends CharSequence` của method ràng buộc tham số `text`; compiler cho phép gọi `text.length()`.
- Bound `Number` không “chảy” vào method `T`; bound `CharSequence` cũng không áp dụng ngược cho field.
- Nếu method cố gán `text` vào `number`, compiler không có lý do để cho phép.

Chương trình thực thi `BoundShadowingDemo.java`:

```java
public class BoundShadowingDemo {
    static final class NumberHolder<T extends Number> {
        private final T number;

        NumberHolder(T number) {
            this.number = number;
        }

        <T extends CharSequence> int lengthOf(T text) {
            return text.length();
        }

        double doubleValue() {
            return number.doubleValue();
        }
    }

    public static void main(String[] args) {
        NumberHolder<Integer> holder = new NumberHolder<>(12);

        System.out.println(holder.doubleValue());
        System.out.println(holder.lengthOf("Java"));
    }
}
```

Chạy:

```bash
javac BoundShadowingDemo.java
java BoundShadowingDemo
```

`stdout`:

```text
12.0
4
```

Mã này có thể biên dịch, nhưng về khả năng đọc hiểu thì kém hơn cách đặt `<S extends CharSequence>` ở method. Hai cận rất khác nhau cùng mang tên `T` không giúp thể hiện ý nghĩa; nó tạo một sự trùng tên vô ích.

**Phiên bản rõ nghĩa hơn:**

```java
class NumberHolder<N extends Number> {
    private final N number;

    NumberHolder(N number) {
        this.number = number;
    }

    <S extends CharSequence> int lengthOf(S text) {
        return text.length();
    }
}
```

Dùng `N` cho number type, `S` cho sequence type giúp không cần suy đoán scope.

## 7. Inner class cũng có thể che khuất type parameter của outer class

Scope lồng nhau không chỉ có class–method. Một **non-static inner class** có thể khai báo một type parameter trùng tên với outer class. Đây là dạng shadowing đặc biệt dễ lẫn khi viết nhiều tầng generic.

Tạo `NestedShadowingDemo.java`:

```java
public class NestedShadowingDemo {
    static class Outer<T> {
        private final T outerValue;

        Outer(T outerValue) {
            this.outerValue = outerValue;
        }

        class Inner<T> {
            private final T innerValue;

            Inner(T innerValue) {
                this.innerValue = innerValue;
            }

            String describe() {
                return outerValue + " / " + innerValue;
            }

            T inner() {
                return innerValue;
            }
        }
    }

    public static void main(String[] args) {
        Outer<String> outer = new Outer<>("outer");
        Outer<String>.Inner<Integer> inner = outer.new Inner<>(7);

        System.out.println(inner.describe());
        System.out.println(inner.inner());
    }
}
```

Chạy:

```bash
javac NestedShadowingDemo.java
java NestedShadowingDemo
```

`stdout`:

```text
outer / 7
7
```

Theo dõi hai `T`:

1. `Outer<T>` khai báo một type parameter của outer class.
2. `Inner<T>` khai báo **type parameter riêng** cho inner class; trong phạm vi khai báo `Inner`, tên `T` của inner che tên `T` của outer.
3. Trường `outerValue` đã được khai báo với `T` của outer; nó không tự đổi kiểu chỉ vì method của inner đang tham chiếu tới nó.
4. Trường `innerValue` thuộc `Inner<T>`, vì vậy dùng `T` của inner.
5. Với `Outer<String>.Inner<Integer>`, outer value có kiểu `String` và inner value có kiểu `Integer`.

Dạng tên `Outer<String>.Inner<Integer>` đặc biệt hữu ích: nó bộc lộ rằng outer và inner có **hai lựa chọn type argument độc lập**.

### Sửa để tránh shadowing

```java
class Outer<T> {
    class Inner<U> {
        private U innerValue;
    }
}
```

Đây là khai báo minh họa. `T` thuộc outer, `U` thuộc inner; mọi lần dùng sau đó đều rõ. Khi cần tương tác với outer, inner có thể sử dụng trường hoặc phương thức outer theo quan hệ kiểu vốn đã khai báo.

### Static nested class không phải trường hợp giống hệt

Một `static class Nested<U>` đặt trong generic outer class **không** mang ngữ cảnh type argument của outer instance. Vì vậy không nên gọi việc khai báo type parameter của static nested class là một ví dụ tương đương hoàn toàn với shadowing của non-static inner class. Nếu cần static nested class chứa dữ liệu khác loại, khai báo type parameters của chính nó và truyền những dữ liệu cần thiết vào constructor.

## 8. Phương thức static và tên T của generic class

Type parameter của class gắn với việc tham số hóa các object, còn thành viên `static` thuộc lớp, không thuộc riêng `Box<String>` hay `Box<Integer>`. Vì vậy static method không thể trực tiếp dùng class-level `T` như thể nó đã có một type argument cụ thể.

```java
class Box<T> {
    private final T value;

    Box(T value) {
        this.value = value;
    }

    // static T wrong(T input) { return input; } // Không hợp lệ.

    static <U> Box<U> of(U input) {
        return new Box<>(input);
    }
}
```

Trong `static <U> Box<U> of(U input)`, `U` là type parameter **của static method**. Nó không trỏ về type parameter `T` của một object `Box<T>` bất kỳ. Việc chọn tên `U` giúp người đọc thấy rõ nguồn gốc.

Bạn cũng có thể viết `static <T> Box<T> of(T input)`, đây là generic method hợp lệ với `T` thuộc method, nhưng dùng `U` sẽ tránh gây cảm giác vô tình dùng lại `T` của class. Khi đọc mã cần tách hai tầng này, đặc biệt trong factory methods.

## 9. Shadowing khác type inference và type erasure

Ba khái niệm đôi khi bị trộn lẫn:

| Khái niệm | Xảy ra ở đâu | Tác động |
| --- | --- | --- |
| **Shadowing** | Cú pháp và scope của khai báo | Tên `T` ở phạm vi trong trỏ tới type parameter mới thay vì type parameter ngoài |
| **Type inference** | Kiểm tra lời gọi lúc biên dịch | Tìm type arguments thỏa ràng buộc của generic method/class |
| **Type erasure** | Cách biên dịch generics sang bytecode | Phần lớn type arguments không hiện diện như runtime specialization; compiler chèn cast/bridge khi cần |

Một method `<T> T echo(T)` có thể che `T` của outer class ngay trong code nguồn, sau đó tại invocation `echo(123)` compiler suy luận `T` của **method** phù hợp `Integer`. Erasure của method `T` không có bound thường là `Object`. Mỗi khái niệm trả lời một câu hỏi khác nhau:

- Shadowing: **tên T này được khai báo ở đâu?**
- Inference: **T của lời gọi cụ thể được suy luận thành kiểu gì?**
- Erasure: **kiểu generic được biểu diễn thế nào ở runtime?**

Dùng lại tên `T` không khiến JVM tạo hai class runtime mới, cũng không buộc hai type parameters cùng type argument.

## 10. Các dấu hiệu trong IDE và lỗi compiler

### 10.1. Gán method T vào class T thất bại

```java
class Store<T> {
    private T stored;

    <T> void set(T incoming) {
        // stored = incoming; // Incompatible types.
    }
}
```

Compiler không thể bảo đảm phép gán: method-level `T` và class-level `T` là hai khai báo độc lập. Một lời gọi method cụ thể có thể suy luận cùng kiểu, nhưng implementation phải đúng với mọi cách gọi hợp lệ. Sửa bằng `void set(T incoming)` nếu thật sự cần cùng class T.

### 10.2. Return type trông giống T nhưng không trùng kiểu của object

```java
class Holder<T> {
    T saved;

    <T> T echo(T input) {
        return input;
    }
}
```

Với `Holder<String> holder`, `holder.echo(1)` có thể tạo kết quả `Integer`. Nếu người viết API hứa “echo trả cùng kiểu với dữ liệu đã lưu” thì chữ ký là sai ý định, dù vẫn biên dịch.

### 10.3. Một method được khai báo với tên type variable gây hiểu nhầm

```java
class Cache<K, V> {
    <V> V convert(V input) {
        return input;
    }
}
```

`<V>` ở method che `V` của `Cache<K,V>`. `convert` không nhất thiết liên quan tới giá trị kiểu `V` trong cache. Đổi thành `<R> R convert(R input)` nếu hoàn toàn độc lập, hoặc bỏ type parameter mới nếu method phải dùng cache value type.

### 10.4. Lỗi được “chữa” bằng cast không bảo đảm

Khi gặp incompatible types do shadowing, không nên thêm unchecked cast như `(T) incoming` để làm im compiler. Type erasure có thể khiến cast không xác minh được type argument mong muốn, đẩy lỗi tới lúc đọc dữ liệu. Cần sửa **khai báo phạm vi** và API contract trước.

### 10.5. Tooling hiện gợi ý cùng tên T hai lần

Trong IDE, dùng chức năng “go to declaration” trên từng `T`. Nếu nó nhảy tới `<T>` của method thay vì `class Box<T>`, bạn đã xác định được shadowing. Hiển thị type tại từng expression, xem generic signature và bật compiler warning có thể giúp phát hiện vấn đề từ sớm. Một linter có thể cảnh báo về hidden type parameter tùy cấu hình, nhưng không nên coi cảnh báo ấy là bắt buộc trong mọi build Java.

## 11. Vì sao tránh che khuất tên type parameter?

### 11.1. Ngăn hiểu sai hợp đồng của API

Người đọc `class Service<T> { <T> T transform(T x) ... }` thường có khuynh hướng nghĩ `transform` phụ thuộc kiểu của `Service<T>`. Thực tế method `<T>` đã tách quan hệ đó. Nếu tên khác `<R>`, người đọc nhận ra mối quan hệ (hoặc sự độc lập) nhanh hơn.

### 11.2. Dễ theo dõi ràng buộc khi mở rộng method

Một method nhỏ có thể chỉ chứa `return input`, nhưng khi thêm field, mapper hay collection, cần phân biệt kiểu lưu trong class với kiểu đầu vào mới. Tên trùng khiến việc thêm code dễ dẫn tới incompatible types và cast không an toàn.

### 11.3. Refactor ít nhầm phạm vi

Một lần sửa từ `<T> T echo(T)` sang “lưu giá trị vào field T” có thể bị ảnh hưởng bởi method-level T mới. Nếu ban đầu tên rõ nghĩa `<U> U echo(U)`, khi thêm code dễ nhận thấy rằng `U` không thể gán thẳng cho field `T`.

### 11.4. Code review nhận diện đúng generic bounds

Khi class có `<T extends Number>` còn method có `<T extends CharSequence>`, việc cùng dùng `T` che giấu hai khả năng hoàn toàn khác nhau. Dùng `N` và `S` hoặc `NUMBER` và `TEXT` làm lộ các hợp đồng thực.

### 11.5. Dễ kiểm tra sự liên kết giữa input và output

Trong API chuyển đổi, `<R> R convert(Function<? super T,? extends R> f)` cho thấy dữ liệu `T` của class được biến thành `R`. Với hai biến đều viết `T`, tính độc lập này bị che mờ.

Điều cần tránh là **sự mơ hồ do trùng tên trong scope lồng nhau**. Không cần đặt những cái tên dài, phức tạp hoặc thay mọi `T` của dự án bằng một kiểu đặt tên duy nhất.

## 12. Khi nào tái sử dụng tên T vẫn ổn?

`T` là quy ước rất phổ biến cho “một kiểu chưa biết”. Việc dùng `T` trong những scope độc lập là tự nhiên:

```java
class Utilities {
    static <T> T first(java.util.List<T> values) {
        return values.get(0);
    }

    static <T> T last(java.util.List<T> values) {
        return values.get(values.size() - 1);
    }
}
```

Hai method khai báo `<T>` riêng, không lồng nhau; mỗi method chỉ có một type variable và mọi vị trí của `T` trong method ấy đều liên kết với nhau đúng như mong muốn. Tên `T` có giá trị vì đơn giản.

Tương tự, `class Box<T>` và `class Queue<T>` trong hai top-level class khác nhau dùng `T` độc lập; không có xung đột. Shadowing đáng tránh khi **một khai báo T ở scope trong che T của scope ngoài** và người đọc dễ nhầm chúng có cùng ý nghĩa.

Quy tắc thực hành:

- Một type variable duy nhất trong một scope ngắn: `T` thường đủ rõ.
- Hai type variables độc lập trong cùng chữ ký: `T,R` hoặc tên theo vai trò.
- Class generic và method generic nằm trong class: dùng tên khác nhau nếu method khai báo type variable mới.
- Nested non-static generic class: không tái dùng tên của generic outer class.
- Khi một method cần chính `T` của class: **không** khai báo thêm `<T>`.
- Khi method không liên quan tới class T: đặt `<U>`, `<R>` hoặc tên có ý nghĩa.

## 13. Phân biệt shadowing với các khái niệm dễ nhầm

### 13.1. Shadowing type parameter và shadowing local variable

```java
class Example<T> {
    private int size = 5;

    void print(int size) {
        System.out.println(size);
        System.out.println(this.size);
    }
}
```

Đoạn minh họa trên có **parameter tên size** che **field tên size**. Có thể dùng `this.size` để chỉ field. Nhưng khi method khai báo `<T>` che class `T`, `this.T` **không phải cú pháp Java hợp lệ** để chỉ biến kiểu của class. Cách sửa thông thường là không dùng lại tên type parameter hoặc thiết kế lại quan hệ kiểu; `this` không mở ra một đường truy cập “T kiểu của class” bằng một tên trực tiếp khác.

### 13.2. Shadowing khác method overriding

Overriding là việc subtype cung cấp implementation của phương thức được kế thừa theo quy tắc chữ ký/kiểu trả về. Shadowing `T` là **phân giải tên trong source code**, không phải override, không phải overload và không ảnh hưởng trực tiếp đến dynamic dispatch.

### 13.3. Shadowing khác đặt tên lớp con trùng kiểu tham số

```java
class Parent<T> {
    T value;
}

class Child<T> extends Parent<T> {
}
```

Đây là ví dụ chuyển tiếp type argument: `Child<T>` khai báo type parameter **của Child**, rồi dùng chính tham số đó làm type argument của `Parent<T>`. Hai khai báo type parameter của `Parent` và `Child` là độc lập, nhưng subclass **ràng buộc chúng tại mệnh đề `extends Parent<T>`**. Không nên chỉ nhìn hai tên `T` rồi coi đây là lỗi shadowing cùng cấu trúc như method nằm trong class.

Nếu viết `class Child<U> extends Parent<U>`, ý nghĩa không đổi mà sự phân biệt khai báo có thể rõ hơn. Chọn cách đặt tên phù hợp ngữ cảnh; đừng kết luận chỉ từ tên.

### 13.4. Overload khác tên type parameter không tạo overload khác

Hai generic methods:

```java
// Không được cùng khai báo trong một class:
static <T> void log(T value) {}
static <U> void log(U value) {}
```

sẽ xung đột vì sau type erasure chúng có cùng chữ ký runtime tương ứng `log(Object)`. Đổi tên `T` thành `U` không tạo overload mới, cũng không liên quan tới shadowing giữa class và method.

### 13.5. Type parameter và type argument

Trong `class Box<T>`, `<T>` khai báo. Trong `Box<String>`, `<String>` truyền type argument. Trong `List<T>` bên trong `Box<T>`, `T` chỉ được **sử dụng**, không khai báo mới. Những cú pháp trông giống nhau này đóng vai trò khác nhau nên phải xác định vị trí trước khi nói tới shadowing.

## 14. Type parameter shadowing và generic interface

Một generic class có thể dùng type parameter của mình để triển khai interface:

```java
interface Container<E> {
    E read();
}

class ValueContainer<T> implements Container<T> {
    private final T stored;

    ValueContainer(T stored) {
        this.stored = stored;
    }

    @Override
    public T read() {
        return stored;
    }
}
```

`T` của `ValueContainer<T>` là type argument truyền cho `Container<E>`. Phần `implements Container<T>` **không tạo biến kiểu mới**. Trong class này, `read()` trả `T` của ValueContainer đúng hợp đồng của Container.

Nếu thêm một generic method:

```java
class ValueContainer<T> implements Container<T> {
    private final T stored;

    ValueContainer(T stored) {
        this.stored = stored;
    }

    @Override
    public T read() {
        return stored;
    }

    <R> R convert(java.util.function.Function<? super T, ? extends R> f) {
        return f.apply(stored);
    }
}
```

Lúc này `T` của class liên hệ với interface và input của mapper; `R` của method mang kiểu kết quả. Không có shadowing; quan hệ kiểu trong chữ ký được biểu diễn chính xác và dễ duy trì.

## 15. Reflection có thể phân biệt hai TypeVariable tên T

Phần này dành cho người đã biết reflection; nó làm rõ rằng hai type variables cùng tên **không đồng nhất về khai báo**. Java reflection cung cấp `TypeVariable<?>` với tên và `GenericDeclaration` tương ứng.

Tạo `TypeVariableIdentityDemo.java`:

```java
import java.lang.reflect.Method;
import java.lang.reflect.TypeVariable;

public class TypeVariableIdentityDemo {
    static class Holder<T> {
        <T> T echo(T value) {
            return value;
        }
    }

    public static void main(String[] args) throws Exception {
        TypeVariable<?> classT = Holder.class.getTypeParameters()[0];
        Method method = Holder.class.getDeclaredMethod("echo", Object.class);
        TypeVariable<?> methodT = method.getTypeParameters()[0];

        System.out.println(classT.getName());
        System.out.println(methodT.getName());
        System.out.println(
                classT.getGenericDeclaration().equals(
                        methodT.getGenericDeclaration()));
    }
}
```

Chạy:

```bash
javac TypeVariableIdentityDemo.java
java TypeVariableIdentityDemo
```

`stdout`:

```text
T
T
false
```

Cả hai biến kiểu đều có `getName()` là `"T"`. Nhưng khai báo gốc khác nhau: `classT` thuộc `Holder.class`, `methodT` thuộc method `echo`. Giá trị `false` ở phép so sánh cho thấy chúng không phải cùng một generic declaration. Method `echo(T)` có type parameter không bound nên tham số runtime bị erasure thành `Object`, vì vậy `getDeclaredMethod("echo", Object.class)` tìm đúng method.

Reflection không biến type arguments generic thành runtime types riêng của từng object. Nó chỉ cho phép khảo sát metadata của các **khai báo** vẫn có trong class file. Bạn không cần reflection để sử dụng generic thông thường; ví dụ này chỉ củng cố lý do không được coi hai type variables cùng tên là một.

## 16. Bài tập: nhận diện và sửa shadowing

### Bài 1 — Dự đoán kiểu

Cho:

```java
class Container<T> {
    T get() { return null; }

    <T> T identity(T x) {
        return x;
    }
}
```

Đối với biến `Container<String> c`:

- `c.get()` có kiểu biểu thức gì?
- `c.identity(123)` có thể trả gì?
- Hai chữ `T` trong class và method có cùng khai báo không?

**Đáp án:** `get()` có kiểu tĩnh `String`; `identity(123)` có thể suy luận method T là `Integer`; hai `T` thuộc hai khai báo khác nhau. `get()` trong mẫu đang trả null, vì vậy không tự xem nó là một store có dữ liệu thật.

### Bài 2 — Tìm lỗi trong setter

```java
class Store<T> {
    private T value;

    <T> void set(T v) {
        // value = v; // Vì sao không biên dịch?
    }
}
```

**Đáp án:** `v` mang type variable T thuộc method còn `value` thuộc class. Muốn setter nhận cùng kiểu với field, xóa `<T>` ở method và dùng `void set(T v) { value = v; }`.

### Bài 3 — Method có kiểu kết quả mới

Thay vì:

```java
class Box<T> {
    T value;

    <T> T transform(java.util.function.Function<T, T> mapper) {
        return null;
    }
}
```

hãy viết method chuyển kiểu của giá trị field từ `T` thành `R`, không làm thay đổi field.

**Đáp án mẫu:**

```java
<R> R transform(java.util.function.Function<? super T, ? extends R> mapper) {
    return mapper.apply(value);
}
```

`T` lấy từ generic class, `R` do method khai báo. Nếu muốn trả một `Box<R>` thay vì `R`, có thể tạo `new Box<R>(...)` với constructor phù hợp. Đây là đoạn method minh họa cần đặt trong class có `T value`.

### Bài 4 — Hai bounds cùng tên

Một class khai báo `<T extends Number>`, method trong class khai báo `<T extends CharSequence>`. Phương thức có thể gọi `length()` hay `doubleValue()` trên parameter mang type variable T của method?

**Đáp án:** được gọi `length()` vì method T có bound `CharSequence`, không được compiler bảo đảm `doubleValue()`. Bound `Number` thuộc T của class không truyền vào T của method.

### Bài 5 — Outer và Inner

Với `Outer<String>.Inner<Integer>` trong ví dụ trước, hãy xác định kiểu của `outerValue` và `innerValue`. Nếu đổi declaration inner thành `class Inner<U>` thì annotation kiểu nên viết gì?

**Đáp án:** lần lượt `String` và `Integer`. Với khai báo `Inner<U>`, vị trí dùng vẫn có dạng `Outer<String>.Inner<Integer>`; trong source của inner, dùng `U` thay cho `T` để chỉ kiểu component của inner.

### Bài 6 — Hai generic methods độc lập

```java
static <T> T left(T a, T b) { return a; }
static <T> T right(T a, T b) { return b; }
```

Đây có phải trường hợp nên cấm tái dùng tên `T` không?

**Đáp án:** không. Hai method-level `T` có phạm vi riêng, không lồng nhau và không che một `T` outer class; cùng dùng tên theo quy ước là dễ đọc.

### Bài 7 — Review một API trước và sau

**Trước:**

```java
class Holder<T> {
    T stored;
    <T> T echo(T input) { return input; }
}
```

**Sau:**

```java
class Holder<T> {
    T stored;
    <U> U echo(U input) { return input; }
}
```

Hãy trả lời: method `echo` trong hai phiên bản có quan hệ kiểu với field `stored` không? Việc đổi tên có thay đổi hành vi runtime không?

**Đáp án:** cả hai đều không liên kết kiểu method argument với `stored`. Đổi tên từ method T sang U làm rõ hợp đồng source nhưng không thay logic, không tạo specialized class và không thay đổi cách Java Generics được xóa kiểu. Kết quả dùng method vẫn được compiler suy luận từ lời gọi như cũ.

## 17. Checklist khi đọc hoặc review tên type parameter

Khi một đoạn code generic có nhiều cấp khai báo, lần lượt hỏi:

1. **T đang xét được khai báo ở đâu?** Class, interface, record, method hay constructor?
2. Có một `<T>` **mới** nằm bên trong phạm vi class/interface đã có `T` không?
3. Method này muốn dùng kiểu lưu trong object hay muốn khai báo kiểu đầu vào/kết quả độc lập?
4. Có cùng tên `T` ở hai cấp nhưng bounds khác nhau không?
5. Những field, parameter và return type nào phải được ràng buộc với **T của class**?
6. Những vị trí nào là kiểu kết quả mới nên đặt `R` hoặc `U`?
7. Có ai dùng unchecked cast để “sửa” một lỗi vốn do khai báo lại type parameter không?
8. Trong nested class, dấu `<T>` là khai báo mới hay chỉ là type argument của một generic type khác?
9. Các type arguments ở caller có được suy luận đúng không nếu không xem tên `T` như một kiểu runtime cụ thể?
10. Tên kiểu đang giúp người đọc nhận ra quan hệ dữ liệu, hay đang che mất sự độc lập giữa các kiểu?

Nếu cùng một `T` ở hai cấp khai báo có thể khiến người đọc trả lời nhầm những câu này, hãy đổi tên type parameter ở cấp trong hoặc bỏ khai báo type parameter thừa. Giữ tên `T` khi chỉ có một nghĩa rõ ràng trong phạm vi đang đọc.

## 18. Kết luận

**Shadowing của type parameter là quy tắc về phạm vi tên lúc biên dịch.** Trong `class Box<T> { <T> T echo(T value) ... }`, `T` của method là một khai báo mới và che khuất `T` của class. Việc viết cùng chữ `T` không làm hai kiểu liên quan với nhau, cũng không khiến JVM lưu hai kiểu runtime khác nhau theo tên.

Khi method cần thao tác trên dữ liệu đã lưu, hãy **dùng trực tiếp `T` của class** mà không khai báo lại. Khi method cần một kiểu riêng, hãy đặt `<R>`, `<U>` hoặc tên theo vai trò để làm lộ quan hệ: `T` cho đầu vào hiện có, `R` cho kết quả mới, `U` cho kiểu độc lập. Phân biệt được scope, inference và erasure giúp bạn đọc generic signature chính xác, tránh cast không cần thiết và duy trì những API dễ hiểu khi chương trình phát triển.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="type-parameter-scope-003">Giới hạn dùng T của lớp trong trường và phương thức static</a>

<details>
<summary>Xem chi tiết</summary>

# Giới hạn dùng T của lớp trong trường và phương thức static (Java Generics)

## 1. Tình huống và quy tắc cần nhớ

Một lớp generic khai báo tham số kiểu sau tên lớp, ví dụ `class Box<T>`. Tham số `T` mô tả kiểu dữ liệu của **một cách tham số hóa lớp** như `Box<String>` hoặc `Box<Integer>`. Ta có thể sử dụng `T` trong trường instance, constructor và phương thức instance. Tuy nhiên, **không được lấy trực tiếp `T` của lớp để khai báo kiểu của trường hoặc phương thức `static`**.

```java
// Mảnh minh họa độc lập về cú pháp.
class Box<T> {
    private T value;                // Đúng: trường instance thuộc Box<T>.

    Box(T value) {                  // Đúng: constructor dùng T của lớp.
        this.value = value;
    }

    T get() {                       // Đúng: phương thức instance trả T.
        return value;
    }

    // private static T shared;     // Sai: static field không dùng T của lớp.
    // static T identity(T input) { // Sai: static method không dùng T của lớp.
    //     return input;
    // }

    static <U> U identity(U input) { // Đúng: U do phương thức khai báo riêng.
        return input;
    }
}
```

Với `Box<String>`, getter `get()` được kiểm tra như trả `String`; với `Box<Integer>`, getter trả `Integer`. Nhưng `Box.identity(42)` là lời gọi phương thức `static` dùng `U = Integer` của **chính phương thức**, không phụ thuộc vào một `Box<T>` nào.

Quy tắc này áp dụng với cả trường `static final`, phương thức `static`, các khối khởi tạo `static` và những phần khai báo thành viên ở **ngữ cảnh static** nếu chúng cố tham chiếu `T` của lớp. Không có ngoại lệ chỉ vì trường đặt `final` hoặc phương thức không truy cập trạng thái đối tượng.

Bài học áp dụng Java 17+, có mã chạy độc lập và những đoạn cố tình không biên dịch được giữ dưới dạng comment hoặc ghi chú.

## 2. Phân biệt phạm vi của `T`: lớp và từng đối tượng

`class Box<T>` khai báo `T` trong **type parameter list của lớp**. `T` có thể xuất hiện trong những ngữ cảnh instance sau:

| Vị trí | Ví dụ | Hợp lệ? | Giải thích |
| --- | --- | --- | --- |
| Trường instance | `private T value;` | Có | Trường gắn với mỗi đối tượng `Box<T>`. |
| Constructor | `Box(T initial)` | Có | Kiểm tra đầu vào theo kiểu `T` của đối tượng tạo ra. |
| Phương thức instance | `void set(T value)` | Có | Setter thuộc đối tượng, nên biết `T`. |
| Giá trị trả về instance | `T get()` | Có | Liên kết kiểu trả về với kiểu của đối tượng. |
| Tham số generic của trường instance | `List<T> items` | Có | Kiểu phần tử của collection vẫn là `T`. |
| Trường static | `static T shared` | Không | Thành viên thuộc lớp chung, không thể chọn một `T` của từng cách tham số hóa. |
| Kiểu trả về static | `static T getShared()` | Không | Không có `T` của lớp trong static context. |
| Tham số static | `static void accept(T value)` | Không | Cũng tham chiếu `T` của lớp từ static context. |
| Phương thức static khai báo kiểu riêng | `static <U> U echo(U value)` | Có | `U` thuộc chính phương thức, không thuộc lớp. |

Một ví dụ lớp chứa dữ liệu có kiểu rõ ràng:

```java
import java.util.*;

final class TypedList<T> {
    private final List<T> items = new ArrayList<>();

    void add(T item) {
        items.add(item);
    }

    List<T> snapshot() {
        return List.copyOf(items);
    }
}
```

`TypedList<String>` có các phần tử được trình biên dịch kiểm tra như `String`. `TypedList<Integer>` có các phần tử được kiểm tra như `Integer`. Hai đối tượng có thể cùng chạy một định nghĩa lớp, nhưng luồng dữ liệu qua thành viên instance vẫn giữ hợp đồng generic tương ứng.

Ở đây `List.copyOf` tạo một danh sách không sửa được và không nhận phần tử `null`. Đây là lựa chọn API, không phải đặc tính bắt buộc của `T`. Nếu lớp cho phép null thì nên sử dụng cách tạo snapshot phù hợp.

## 3. Tại sao trường static không được dùng `T` của lớp?

Trường instance được gắn với **đối tượng**: mỗi `Box<String>` có thể chứa `String`, mỗi `Box<Integer>` có thể chứa `Integer`. Trường `static` thì gắn với **lớp runtime**, không với một đối tượng hay một cách truyền tham số kiểu cụ thể.

Giả sử Java cho phép khai báo sau:

```java
class InvalidBox<T> {
    // static T sharedValue; // Không hợp lệ; cố tình comment.
}
```

Nếu `sharedValue` thực sự có cùng một ô lưu trữ cho cả `InvalidBox<String>` và `InvalidBox<Integer>`, compiler sẽ không thể đồng thời bảo đảm nó chỉ nhận `String` và chỉ nhận `Integer`. Cấm khai báo này là cách Java giữ hợp đồng kiểu nhất quán.

Đó là quy tắc ngôn ngữ về **static context**: tham số kiểu của một lớp generic không thể được tham chiếu từ khai báo static của chính lớp ấy. **Không phải chỉ do trình biên dịch “chưa biết dữ liệu ban đầu”**: ngay cả khi mọi constructor truyền cùng một loại dữ liệu tại một thời điểm, static member vẫn là thành viên cấp lớp.

### Trường static dùng kiểu cụ thể vẫn hợp lệ

```java
import java.util.List;

class Repository<T> {
    static int instanceCount = 0;                // Hợp lệ.
    static final String CATEGORY = "repository"; // Hợp lệ.
    static final List<String> FLAGS = List.of("A", "B"); // Hợp lệ.

    private T item;  // Hợp lệ: trường instance.
}
```

`static final List<String> FLAGS` là một **kiểu generic có tham số cụ thể `String`**, nên vẫn hợp lệ. Điều bị cấm là dùng **chính tham số `T` của lớp** ở kiểu của thành viên static, chẳng hạn `static List<T> FLAGS`.

`static final` không làm giá trị tham chiếu tự động bất biến: ví dụ `static final ArrayList<String>` vẫn có thể bị thay đổi nội dung qua các phương thức của list. `List.of(...)` trong ví dụ tạo list không sửa được để diễn đạt rõ chính sách dữ liệu.

### Không có bản sao static cho mỗi type argument

```java
class CounterBox<T> {
    private static int count;

    CounterBox() {
        count++;
    }

    static int count() {
        return count;
    }
}

// CounterBox<String> a = new CounterBox<>();
// CounterBox<Integer> b = new CounterBox<>();
// CounterBox.count() là 2, không có hai bộ đếm riêng.
```

`count` là **một** trường cho `CounterBox`, không phải `CounterBox<String>.count` và `CounterBox<Integer>.count`. Nếu muốn đếm theo kiểu dữ liệu, chương trình phải **tự truyền thông tin phân nhóm** (chẳng hạn một `Class<?>` token, key hoặc tên loại) và quản lý một ánh xạ phù hợp; Java không tự tạo static data riêng cho từng tham số kiểu.

## 4. Vì sao phương thức static cũng không dùng `T` của lớp?

Phương thức instance gọi thông qua đối tượng có kiểu cụ thể:

```java
// Mảnh minh họa, giả sử Box<T> có T get().
Box<String> title = new Box<>("Java");
String content = title.get();
```

Phương thức `get()` gắn với đối tượng `title`, nên compiler biết đó là `Box<String>`. Trái lại, phương thức `static` có thể gọi thông qua **tên lớp** mà không tạo đối tượng:

```java
// Mảnh minh họa cho một static method tự khai báo <U>.
String s = Box.identity("Java");
Integer n = Box.identity(10);
```

Nếu phương thức static cố nhận `T` của `class Box<T>`, nó sẽ không có một `Box<String>` hoặc `Box<Integer>` cụ thể để ràng buộc kiểu.

```java
class Wrong<T> {
    // static T wrong(T item) { return item; } // Lỗi biên dịch.

    static <U> U correct(U item) {
        return item;
    }
}
```

Ở `correct`, tham số kiểu `U` được khai báo **ngay trước kiểu trả về**. Mỗi lần gọi phương thức có thể suy luận `U` theo đối số và ngữ cảnh kết quả. Đây là **static generic method**, khác với phương thức static dùng `T` của lớp.

### Cú pháp chuẩn của static generic method

```java
class Utility {
    static <U> U first(U left, U right) {
        return left;
    }

    static <K, V> java.util.Map.Entry<K, V> pair(K key, V value) {
        return java.util.Map.entry(key, value);
    }
}

// String value = Utility.first("A", "B");
// Map.Entry<String,Integer> entry = Utility.pair("age", 21);
```

Lưu ý thứ tự: các modifier như `public` và `static` đứng trước danh sách type parameter `<U>`, sau đó mới đến kiểu trả về. Không viết `static U <U> first(...)`.

## 5. Ba trường hợp phải phân biệt trong lớp generic

Xét:

```java
class Holder<T> {
    private final T value;

    Holder(T value) {
        this.value = value;
    }

    T get() {                  // (1)
        return value;
    }

    <U> U convert(java.util.function.Function<? super T, ? extends U> f) { // (2)
        return f.apply(value);
    }

    static <U> U echo(U input) { // (3)
        return input;
    }
}
```

| Phương thức | Có phải generic method? | Dùng tham số kiểu nào? |
| --- | --- | --- |
| `T get()` | Không, vì không tự khai báo `<...>` | `T` của `Holder<T>` |
| `<U> U convert(...)` | Có | `T` của lớp, `U` của phương thức |
| `static <U> U echo(U)` | Có | Chỉ `U` của phương thức |

Ví dụ:

```java
Holder<String> holder = new Holder<>("Java");
String a = holder.get();                       // T = String.
Integer b = holder.convert(String::length);    // U = Integer.
Long c = Holder.echo(10L);                     // U = Long, không liên quan holder.
```

`convert` là **instance generic method**: có thể truy cập `this.value`, dùng `T` của lớp và đồng thời khai báo `U` riêng. `echo` là **static generic method**: không thể truy cập `this` hoặc `value` như trường instance.

Điểm quan trọng: một phương thức **có thể** generic mà không static, hoặc static mà không generic. Hai tính chất này độc lập. `static int count()` là static method thông thường; `<U> U convert(...)` là generic instance method.

## 6. Tránh khai báo `<T>` làm che khuất `T` của lớp

Java cho phép phương thức khai báo một tham số kiểu mới trùng **tên** với tham số kiểu của lớp. Đây là tình huống dễ đọc sai nhất:

```java
class Confusing<T> {
    private final T value;

    Confusing(T value) {
        this.value = value;
    }

    // T trong chữ ký echo là T MỚI của phương thức.
    static <T> T echo(T input) {
        return input;
    }

    // T ở đây là T của lớp.
    T get() {
        return value;
    }
}
```

Với `Confusing<String>`, getter `get()` trả `String`, nhưng `Confusing.echo(42)` vẫn trả `Integer`. Hai chữ `T` có hình thức giống nhau nhưng **không cùng một khai báo**; `T` của phương thức che khuất `T` của lớp ở phạm vi của phương thức đó.

Cách thiết kế rõ hơn:

```java
class Clear<T> {
    private final T value;

    Clear(T value) { this.value = value; }

    T get() { return value; }

    static <U> U echo(U input) { return input; }
}
```

Dùng `<U>`, `<R>` hoặc tên khác cho tham số của phương thức static để tránh người đọc tưởng nó bị ràng buộc bởi type argument ở lớp.

**Đừng “sửa” lỗi static bằng cách viết `static <T> T method(...)` nếu logic thực sự cần đọc trạng thái của một đối tượng**. Trường hợp đó nên là instance method, hoặc static method phải nhận chính đối tượng đã được tham số hóa:

```java
class Reader<T> {
    private final T value;

    Reader(T value) { this.value = value; }

    T get() { return value; }

    static <U> U readFrom(Reader<U> source) {
        return source.get();
    }
}

// Reader<String> item = new Reader<>("Java");
// String result = Reader.readFrom(item);
```

`readFrom` hợp lệ vì nguồn `Reader<U>` được truyền **tường minh dưới dạng tham số**. Nó dùng `U` của phương thức để liên hệ kiểu nguồn và kết quả, không lấy `T` của lớp từ static context.

## 7. Static factory: cách hợp lệ để tạo `Box<U>` từ ngữ cảnh static

Điều quan trọng khi sửa lỗi `static T` là **không biến đầu vào thành `Object`**. Nếu cần tạo một instance mới có kiểu giữ nguyên từ đối số, phương thức static phải tự khai báo `U` và liên kết `U` giữa tham số với kết quả:

~~~java
final class Box<T> {
    private final T value;

    private Box(T value) {
        this.value = value;
    }

    static <U> Box<U> of(U value) {
        return new Box<>(value);
    }

    T get() {
        return value;
    }
}
~~~

`Box.of("Java")` trả `Box<String>`; `Box.of(17)` trả `Box<Integer>`. Trong cả hai lời gọi, **`U` của factory** được suy luận riêng, còn `T` của class chỉ có nghĩa khi object `Box<U>` đã được tham số hóa. Đây là cách sửa đúng lỗi dùng type variable của lớp trong thành viên static mà vẫn giữ kiểu kết quả.

Bài về **phương thức static generic** triển khai riêng type witness, nhiều type parameters, callback `Function` và các factory có bound; ở đây chỉ cần nắm bất biến: **static không mượn `T` của class**, nhưng có thể tạo một `Box<U>` qua `static <U>`.

## 8. Bound thuộc tham số kiểu do static method khai báo

Khi thuật toán static cần dùng một API của giá trị đầu vào, giới hạn phải đặt trên **type parameter riêng của phương thức**, không phải dựa vào `T` của lớp bao:

~~~java
class NumericContainer<T> {
    static <U extends Number> double twice(U number) {
        return number.doubleValue() * 2;
    }
}
~~~

`NumericContainer.twice(2.5)` hợp lệ và cho `5.0`, còn `NumericContainer.twice("2.5")` bị compiler từ chối. `U extends Number` chỉ ràng buộc lời gọi `twice`; nó **không phụ thuộc cách tham số hóa** `NumericContainer<String>` hay `NumericContainer<Integer>`. Với thuật toán khác, có thể dùng bound `Comparable<? super U>` hoặc nhiều bounds nếu thật sự cần.

Chọn bounds theo thao tác cần thực hiện, và nhớ rằng **bound là ràng buộc kiểu lúc biên dịch**, không làm `U` trở thành một class token runtime có thể dùng trực tiếp như `U.class`.

## 9. Static nested class là một ranh giới khác với inner class

Một lớp lồng `static` cũng không được dùng trực tiếp `T` của generic outer class; trong khi một **non-static inner class** có thể làm vậy bởi nó gắn với một enclosing instance:

~~~java
class Outer<T> {
    private final T value;
    Outer(T value) { this.value = value; }

    class Inner {
        T read() { return value; }
    }

    static class Nested<U> {
        U data;
        Nested(U data) { this.data = data; }
    }
}
~~~

Ở nơi sử dụng, `Outer<String>.Inner` được tạo bằng `outer.new Inner()`; `Outer.Nested<Integer>` được tạo bằng `new Outer.Nested<>(42)`. Type parameter `U` thuộc chính `Nested`; nó không liên quan ngầm đến `T` của `Outer`.

Ví dụ này chỉ minh họa cùng **giới hạn ngữ cảnh static** với field/method. Cách tạo, mô hình enclosing-instance, lifecycle và generic bounds của từng loại lớp lồng được giải thích chuyên sâu trong các bài tương ứng.

## 10. Static block, static initializer và việc truy cập dữ liệu

Một khối `static { ... }` chạy khi lớp được khởi tạo theo quy tắc của Java. Nó không có đối tượng `this`, không có `T` của lớp generic, và không thể trực tiếp truy cập trường instance:

```java
class Configuration<T> {
    private T value;
    private static final String DEFAULT_LABEL;

    static {
        DEFAULT_LABEL = "READY"; // Đúng: field static có kiểu String cụ thể.
        // T v = null;          // Sai: không dùng T của lớp trong static context.
        // value = null;        // Sai: không có instance của Configuration<T>.
    }

    Configuration(T value) { this.value = value; }
    T get() { return value; }

    static String label() {
        return DEFAULT_LABEL;
    }
}
```

Một static method có thể gọi các phương thức static khác hoặc dùng static field bình thường; vấn đề chỉ xuất hiện khi nó cố sử dụng `T` của lớp hoặc truy cập một instance member khi không có đối tượng.

Với một đối tượng được truyền vào làm tham số, static method **vẫn có thể gọi instance method** của đối tượng đó, ví dụ `source.get()` ở `Reader.readFrom(...)`. Quy tắc đúng là phải có **tham chiếu đối tượng rõ ràng**, không dùng `this` hoặc truy cập trực tiếp member instance từ ngữ cảnh static.

## 11. Tại sao type erasure làm quy tắc dễ hiểu hơn?

Java generics thường được thực hiện bằng **type erasure**: kiểm tra tham số kiểu lúc biên dịch, sau đó compiler dùng bound tương ứng trong biểu diễn runtime thông thường. Vì vậy, `Box<String>` và `Box<Integer>` dùng cùng lớp runtime `Box`:

```java
// Mảnh minh họa, giả sử Box<T> có constructor Box(T).
Class<?> a = new Box<>("Java").getClass();
Class<?> b = new Box<>(123).getClass();
boolean same = a == b; // true.
```

Runtime không tạo một `Class` riêng cho từng type argument thông thường. Điều này nhất quán với việc các trường `static` thuộc về lớp chung và không được tham số hóa bởi `T` của mỗi instance.

Cũng vì `T` thường không phải kiểu *reifiable*, không thể viết:

```java
class ErasureLimits<T> {
    // T value = new T();      // Không thể khởi tạo kiểu T trực tiếp.
    // Class<T> c = T.class;   // Không có T.class.
    // T[] items = new T[10]; // Không được tạo mảng T trực tiếp.
}
```

Nếu logic static cần làm việc với một kiểu cụ thể ở runtime, truyền `Class<U>` hoặc factory `Supplier<? extends U>` (tùy mục đích) vào static generic method. Ví dụ:

```java
import java.util.function.Supplier;

class Creator<T> {
    static <U> U create(Supplier<? extends U> factory) {
        return factory.get();
    }
}

// StringBuilder b = Creator.create(StringBuilder::new);
```

Tham số `U` của `create` được suy luận từ factory/lời gọi; không cần và không được lấy `T` của `Creator<T>`.

**Lưu ý:** Không nên diễn giải type erasure như toàn bộ metadata generic luôn biến mất. Reflection vẫn có thể đọc một số khai báo type parameter/bound trong metadata, nhưng điều đó **không** biến static field thành dữ liệu riêng của `Box<String>` hoặc cho phép sử dụng `T` lớp trong static context.

## 12. Chương trình hoàn chỉnh: xác nhận sự khác nhau trong một lần chạy

Lưu đoạn sau thành tệp **`StaticTypeScopeDemo.java`**. Đây là chương trình độc lập có thể biên dịch/chạy trên Java 17+. Lớp `Box<T>` ở mức top-level giữ dữ liệu instance, bộ đếm static dùng chung, static generic methods, static nested class và non-static inner class.

```java
import java.util.List;
import java.util.Objects;

final class Box<T> {
    private final T value;
    private static int created;
    static final String KIND = "Box";
    static final List<String> TAGS = List.of("shared", "metadata");

    Box(T value) {
        this.value = Objects.requireNonNull(value);
        created++;
    }

    T get() {
        return value;
    }

    static int createdCount() {
        return created;
    }

    static <U> Box<U> of(U value) {
        return new Box<>(value);
    }

    static <U extends Number> double twice(U number) {
        return number.doubleValue() * 2;
    }

    static final class Pair<A, B> {
        private final A key;
        private final B value;

        Pair(A key, B value) {
            this.key = key;
            this.value = value;
        }

        A key() { return key; }
        B value() { return value; }
    }

    final class View {
        T read() { return value; }
    }
}

public class StaticTypeScopeDemo {
    public static void main(String[] args) {
        Box<String> name = Box.of("Java");
        Box<Integer> count = Box.of(21);

        System.out.println("instance: " + name.get() + " / " + count.get());
        System.out.println("created: " + Box.createdCount());
        System.out.println("twice: " + Box.twice(2.5));

        Box.Pair<String, Integer> pair = new Box.Pair<>("age", 21);
        System.out.println("pair: " + pair.key() + "=" + pair.value());

        Box<String>.View view = name.new View();
        System.out.println("inner: " + view.read());
        System.out.println("kind: " + Box.KIND);
        System.out.println("tags: " + Box.TAGS);
    }
}
```

Biên dịch bằng JDK 17 trở lên:

```bash
javac --release 17 -Xlint:all StaticTypeScopeDemo.java
java StaticTypeScopeDemo
```

Kết quả stdout chính xác (đã kiểm chứng bằng compiler Java 24 ở chế độ `--release 17`, không có cảnh báo `-Xlint:all`):

```text
instance: Java / 21
created: 2
twice: 5.0
pair: age=21
inner: Java
kind: Box
tags: [shared, metadata]
```

### Diễn giải theo từng thành phần

1. `Box.of("Java")` suy luận `U = String`, tạo `Box<String>`. `Box.of(21)` suy luận `U = Integer`, tạo `Box<Integer>`. Factory static không cần truy cập `T` của lớp.
2. Hai constructor làm bộ đếm `created` tăng hai lần. `Box.createdCount()` in `2` vì cả hai kiểu đối tượng cùng chia sẻ trường static.
3. `name.get()` trả `String`; `count.get()` trả `Integer` theo tham số kiểu của mỗi đối tượng.
4. `Box.twice(2.5)` suy luận `U = Double` và gọi `doubleValue()` nhờ bound `extends Number`, cho kết quả `5.0`.
5. `Box.Pair<A,B>` là một **static nested generic class** có tham số kiểu `A`/`B` riêng và không sử dụng `T` của `Box<T>`.
6. `Box<String>.View` là lớp inner gắn với đối tượng `name`, nên `read()` được kiểm tra kiểu trả về là `String`.
7. `KIND` và `TAGS` là các trường static kiểu cụ thể, dùng chung cho mọi `Box<T>`. Việc chúng có kiểu tham số hóa `List<String>` không vi phạm quy tắc, vì không dùng `T` lớp.

Mỗi kiểu dữ liệu trong luồng đọc/ghi đều được diễn đạt bằng generic type hoặc kiểu cụ thể; không cần raw type, unchecked cast hay thao tác runtime không an toàn.

## 13. Các lỗi thường gặp và cách giải quyết

| Mã hoặc tình huống | Lý do sai | Cách sửa |
| --- | --- | --- |
| `static T last;` trong `class Box<T>` | Trường static không được tham chiếu `T` lớp | Dùng kiểu cụ thể, hoặc chuyển dữ liệu thành field instance `T last`. |
| `static List<T> shared;` | `T` nằm sâu trong tham số kiểu nhưng vẫn là `T` của lớp | Dùng `List<String>` nếu hợp đồng chung thật sự là String, hoặc thiết kế kho lưu riêng theo kiểu. |
| `static final T DEFAULT = ...;` | `final` không thay đổi ngữ cảnh static | Đổi thành kiểu cụ thể hoặc tạo instance field. |
| `static T echo(T x)` | Phương thức không có `T` lớp | Khai báo `static <U> U echo(U x)` khi cần hành vi độc lập. |
| `static <T> T echo(T x)` được hiểu là dùng `T` lớp | `<T>` mới **che khuất** tham số kiểu lớp | Đặt tên `<U>` để thể hiện tham số thuộc phương thức. |
| `static void show() { System.out.println(value); }` | Trường `value` là instance member | Nhận một `Box<U>` làm tham số, hoặc chuyển `show` thành instance method. |
| `static void show() { System.out.println(this); }` | Không có `this` trong static context | Gọi qua đối tượng rõ ràng. |
| `static class Nested { T data; }` bên trong `Outer<T>` | Static nested class không dùng `T` của Outer | Tạo `Nested<U>` với type parameter riêng hoặc dùng non-static inner class. |
| `Outer<String>.Nested<Integer>` | Static nested class không thuộc một `Outer<String>` cụ thể | Dùng `Outer.Nested<Integer>`. |
| Chờ bộ đếm static riêng cho `Box<String>` và `Box<Integer>` | Cả hai sử dụng cùng lớp runtime | Truyền khóa phân nhóm riêng hoặc lưu state trong đối tượng. |
| Dùng `new T()`, `T.class` trong static generic method | Type parameter thường không reifiable | Truyền factory hoặc type token phù hợp. |
| Dùng raw `Box` chỉ để gọi static method | Raw type làm mất kiểm tra generic ở những phần khác | Gọi `Box.of(...)` hoặc `Box.createdCount()` qua tên lớp. |

### Quy tắc sửa theo mục đích

Nếu hành vi cần đọc/sửa trạng thái của một đối tượng `Box<T>`, hãy viết **instance method**: `T get()`, `void set(T item)`. Nếu hành vi độc lập và chỉ cần xử lý kiểu do mỗi lần gọi quyết định, hãy viết **static generic method**: `static <U> U identity(U item)`. Nếu dữ liệu cần chia sẻ thật sự giữa tất cả đối tượng, hãy dùng **static field có kiểu cụ thể** và xác định rõ cơ chế đồng bộ nếu có truy cập đa luồng.

Đừng giải quyết lỗi compiler bằng cách xóa hết type arguments hoặc ép kiểu từ `Object`. Những cách đó thường làm mất tính an toàn kiểu mà generics đang bảo vệ.

## 14. Câu hỏi tự kiểm tra

**Câu 1 — Phân loại.** Với `class Cache<T>`, đánh dấu những khai báo nào hợp lệ và giải thích:

```java
class Cache<T> {
    T latest;                      // A
    static int hits;               // B
    // static T shared;            // C: cố tình comment.
    static <U> U echo(U value) {    // D
        return value;
    }
}
```

Đáp án định hướng: A hợp lệ (instance field), B hợp lệ (static với kiểu cụ thể), C không hợp lệ (static sử dụng `T` lớp), D hợp lệ (`U` thuộc phương thức static).

**Câu 2 — Kết quả bộ đếm.** Một lớp generic có `static int instances` tăng mỗi lần tạo đối tượng. Sau khi tạo hai `Box<String>` và ba `Box<Integer>`, số bộ đếm là bao nhiêu? Đáp án: `5` nếu không có đối tượng khác hay lần khởi tạo nào khác làm tăng bộ đếm; tham số kiểu không chia tách static field.

**Câu 3 — Sửa lỗi kiểu.** Chuyển phương thức bị comment sang phiên bản đúng mà vẫn giữ quan hệ đầu vào–đầu ra:

```java
class Factory<T> {
    // static T create(T input) { return input; } // Sai.

    static <U> Factory<U> create(U input) {
        return new Factory<>(input);
    }

    private final T value;
    Factory(T value) { this.value = value; }
    T get() { return value; }
}
```

Giải thích vì sao type parameter của `create` phải là `U` riêng và kết quả là `Factory<U>`.

**Câu 4 — Nested và inner.** Viết `Outer<T>` có `static class Nested<U>` và `class Inner`. Lớp nào được dùng `T` của `Outer<T>`? Tại sao `Outer<String>.Inner` cần một instance `Outer<String>` để tạo ra?

**Câu 5 — Factory có bound.** Viết `static <N extends Number> Box<Double> doubleBox(N number)` trong một lớp generic. Dùng `number.doubleValue()` để tạo kết quả; giải thích tại sao `N` thuộc về phương thức và `Box<Double>` là kiểu kết quả cụ thể.

**Câu 6 — Tư duy thiết kế.** Bạn muốn cache chung cho mọi đối tượng nhưng mỗi kiểu dữ liệu có danh sách riêng. Tại sao `static List<T> values` không hợp lệ? Đề xuất một API có khóa kiểu (`Class<?>`) và thảo luận sự cần thiết của kiểm tra kiểu khi lấy dữ liệu từ một map lưu trữ dị thể. Không được giả định rằng type token tự làm phép ép kiểu generic container an toàn tuyệt đối.

## 15. Checklist ghi nhớ

- `T` khai báo ở `class Name<T>` thuộc về **lớp generic**, được sử dụng trong trường, constructor và phương thức **instance**.
- Thành viên `static` không có một `T` của lớp để dùng; `static T field`, `static T method(T input)` và `static List<T> field` đều không hợp lệ.
- `static <U> U method(U input)` hoàn toàn hợp lệ vì `U` được khai báo bởi **chính phương thức**.
- Trường `static String`, `static int` và `static List<String>` hợp lệ vì đó là các kiểu cụ thể, không phụ thuộc `T` của lớp.
- Trường `static` được chia sẻ giữa mọi cách tham số hóa `Box<T>`; Java không tạo một bộ static data cho từng `T`.
- Static nested class có thể generic bằng tham số **riêng** `<U>`; non-static inner class mới có thể dùng `T` của enclosing generic instance.
- Dùng instance method khi thao tác thuộc trạng thái đối tượng; dùng static generic method hoặc static factory khi thao tác độc lập với đối tượng.

Khi gặp lỗi compiler về tham chiếu `T` trong static context, hãy xác định **nơi `T` được khai báo** và **thành viên có gắn với đối tượng hay không**. Chỉ cần phân biệt hai phạm vi này là có thể sửa đúng nhiều lỗi cú pháp generics mà vẫn giữ kiểm tra kiểu ngay từ lúc biên dịch.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="type-parameter-scope-004">Phương thức static khai báo tham số kiểu riêng</a>

<details>
<summary>Xem chi tiết</summary>

# Phương thức static khai báo tham số kiểu riêng trong Java Generics

Khi một lớp Java có dạng `class Container<T>`, tham số kiểu `T` thuộc **cách sử dụng lớp generic** như `Container<String>` hoặc `Container<Integer>`. Nhưng một phương thức `static` thuộc **lớp**, được gọi mà không cần chọn một đối tượng `Container<T>` cụ thể. Vì thế, phương thức static **không thể trực tiếp sử dụng tham số kiểu `T` khai báo ở mức lớp**. Nếu phương thức static cần làm việc tổng quát với các kiểu dữ liệu, nó phải **tự khai báo tham số kiểu riêng**, chẳng hạn:

```java
class Container<T> {
    static <U> U identity(U value) {
        return value;
    }
}
```

Ở đây `T` là tham số kiểu của **lớp**, còn `U` là tham số kiểu của **phương thức static**. Chúng có phạm vi khác nhau và không bị ràng buộc với nhau. Cú pháp then chốt là **`static <U> ReturnType methodName(...)`**, tức đặt phần khai báo `<U>` sau modifier `static` và trước kiểu trả về.

## 1. Hai loại tham số kiểu có phạm vi khác nhau

Xét đoạn khai báo lớp sau:

```java
class Holder<T> {
    private final T stored;

    Holder(T stored) {
        this.stored = stored;
    }

    T get() {
        return stored;
    }

    static <U> U echo(U value) {
        return value;
    }
}
```

Đây là **định nghĩa lớp độc lập để minh họa**; nếu dùng trong tệp chạy khác, hãy đặt nó cùng package hoặc trong một lớp bao cho phù hợp.

- `class Holder<T>`: lớp khai báo tham số kiểu `T`. Trường instance `stored`, constructor và phương thức instance `get()` đều có thể sử dụng `T`.
- `static <U> U echo(U value)`: phương thức static **tự khai báo** tham số kiểu `U`. Nó có thể được gọi như `Holder.echo("hello")` hoặc `Holder.echo(123)`.
- `U` chỉ có hiệu lực trong chữ ký và thân của phương thức `echo`; không tự xuất hiện trong trường, constructor hay những phương thức khác của `Holder`.
- Khi có `Holder<String> textHolder`, `T = String` thuộc về kiểu của đối tượng đó. Nó **không ép** `U` ở `Holder.echo(123)` trở thành `String`.
- Một phương thức instance cũng có thể tự khai báo `<U>`; khác biệt cốt lõi là phương thức instance có quyền dùng **cả** tham số kiểu lớp `T` lẫn tham số kiểu riêng của method `U`.

Bảng đối chiếu:

| Vị trí khai báo | Ví dụ | Phạm vi sử dụng hợp lệ |
|---|---|---|
| Tham số kiểu lớp | `class Holder<T>` | Trường và phương thức **instance**, constructor, các khai báo được phép trong ngữ cảnh của kiểu generic |
| Tham số kiểu phương thức static | `static <U> U echo(U x)` | Chữ ký và thân **chính phương thức static đó** |
| Tham số kiểu phương thức instance | `<U> U change(U x)` | Chữ ký và thân method; cũng có thể dùng `T` của lớp nếu phù hợp |
| Tham số kiểu của static factory | `static <U> Holder<U> of(U x)` | `U` thuộc factory; kết quả là một đối tượng `Holder<U>` |
| Tham số kiểu của static nested class | `static class Node<U>` | `U` thuộc `Node`, không lấy `T` từ instance của lớp bao |

Điểm dễ nhớ: **`static` thuộc cấp lớp, còn type argument `T` của `Holder<T>` được chọn cho một kiểu tham số hóa khi sử dụng**. Không có một giá trị `T` duy nhất áp dụng cho tất cả `Holder<String>`, `Holder<Integer>` cùng tồn tại.

## 2. Vì sao phương thức static không sử dụng trực tiếp `T` của lớp?

Một generic class có thể được sử dụng với nhiều kiểu:

```java
Holder<String> words = new Holder<>("Java");
Holder<Integer> count = new Holder<>(7);
```

Các dòng trên là **ví dụ sử dụng** lớp `Holder<T>`; cần đặt chúng trong method hoặc ngữ cảnh khai báo biến hợp lệ.

Cùng một lớp `Holder` có các cách tham số hóa `Holder<String>` và `Holder<Integer>`. Nếu có phương thức `static T current()`, thì khi gọi `Holder.current()`, Java sẽ không có **đối tượng nào** để xác định `T` là `String` hay `Integer`. Do đó Java cấm dùng trực tiếp tham số kiểu cấp lớp trong ngữ cảnh static.

Ví dụ **không biên dịch**:

```java
class InvalidBox<T> {
    // static T last;              // Sai: static field dùng T của class.
    // static T getLast() {        // Sai: static method dùng T của class.
    //     return null;
    // }
    // static void save(T value) { // Sai: tham số dùng T của class.
    // }
}
```

Các dòng gây lỗi được comment để trình bày; nếu bỏ comment, các khai báo đó sẽ bị compiler từ chối vì tham chiếu tới type variable của generic class từ static context.

Giải pháp đúng là **khai báo type parameter ngay ở phương thức**:

```java
class ValidBox<T> {
    static <U> U identity(U value) {
        return value;
    }

    static <U> ValidBox<U> of(U value) {
        return new ValidBox<>();
    }
}
```

**Lưu ý:** phiên bản `ValidBox` vừa nêu chỉ minh họa **cú pháp**; vì `ValidBox` không định nghĩa constructor nhận giá trị hay trường lưu dữ liệu, `of(U value)` trả một hộp rỗng. Trong thiết kế thực tế, nên có trường lưu và constructor nhận `U`; ví dụ đầy đủ ở phần sau triển khai đầy đủ hành vi đó.

Bạn không cần biến **cả lớp** thành generic để có phương thức static generic. Một lớp thường cũng có thể viết `static <T> T identity(T value)`. Cơ chế này độc lập với việc lớp bao có `<T>` hay không.

## 3. Cú pháp chuẩn: khai báo `<U>` trước kiểu trả về

Khuôn dạng tổng quát của một static generic method:

```java
[annotations] [access modifier] static <TypeParameters> ReturnType methodName(
        Parameters) {
    // Thân phương thức
}
```

Các dấu ngoặc vuông và tên mô tả trên **chỉ là ký pháp diễn giải**, không phải một đoạn Java biên dịch độc lập. Ví dụ cú pháp thực:

```java
public static <T> T identity(T input) {
    return input;
}

public static <E> java.util.List<E> twice(E element) {
    return java.util.List.of(element, element);
}

private static <N extends Number> double doubled(N number) {
    return number.doubleValue() * 2;
}
```

Ba method là **các đoạn minh họa cần đặt trong một class**.

Đọc phương thức `public static <T> T identity(T input)`:

1. `public static`: modifier; phương thức thuộc lớp.
2. `<T>`: **khai báo** một tham số kiểu cấp phương thức.
3. `T`: kiểu trả về, đang **sử dụng** tham số kiểu mới khai báo.
4. `identity`: tên phương thức.
5. `T input`: kiểu tham số và tên biến đầu vào, ràng buộc đầu vào và đầu ra có cùng `T`.

Type parameter của method có thể xuất hiện trong kiểu trả về, kiểu tham số, bounds hoặc thân method. Kiểu trả về **không bắt buộc là `T`**: `void`, `int`, `double`, `List<T>` đều có thể là kiểu trả về của generic method.

Một vài lỗi cú pháp:

```java
// static T <T> identity(T input) { return input; } // Sai thứ tự.
// static T identity(T input) { return input; }     // Sai nếu T chưa được khai báo.
// static T identity<T>(T input) { return input; }  // Java không khai báo <T> sau tên method.
```

Cần phân biệt `static <T> T method(T value)` **ở khai báo** với `ClassName.<String>method("x")` **ở lời gọi tường minh**. Phần thứ nhất đặt tên tham số kiểu, phần thứ hai cung cấp đối số kiểu.

## 4. Ví dụ Java 17 chạy độc lập: generic class, static factory, static generic method

Lưu toàn bộ chương trình sau thành **`StaticGenericScopeDemo.java`**:

```java
import java.util.List;
import java.util.Map;

public class StaticGenericScopeDemo {

    static final class Container<T> {
        private final T value;

        private Container(T value) {
            this.value = value;
        }

        T get() {
            return value;
        }

        static <U> Container<U> of(U input) {
            return new Container<>(input);
        }

        static <U> U identity(U input) {
            return input;
        }

        static <K, V> Map.Entry<K, V> entry(K key, V value) {
            return Map.entry(key, value);
        }

        <U> Map.Entry<T, U> combineWith(U other) {
            return Map.entry(value, other);
        }
    }

    static <E> List<E> repeat(E element) {
        return List.of(element, element);
    }

    static <N extends Number> double doubled(N number) {
        return number.doubleValue() * 2;
    }

    public static void main(String[] args) {
        Container<String> text = Container.of("Java");
        Container<Integer> count = Container.of(8);

        System.out.println("text: " + text.get());
        System.out.println("number: " + count.get());

        Integer inferred = Container.identity(42);
        System.out.println("inferred: " + inferred);

        Number wide = Container.<Number>identity(12);
        System.out.println("explicit: " + wide);

        Map.Entry<String, Integer> data =
                Container.<String, Integer>entry("age", 20);
        System.out.println("entry: " + data.getKey()
                + "=" + data.getValue());

        Map.Entry<String, Boolean> pair = text.combineWith(true);
        System.out.println("instance: " + pair.getKey()
                + "," + pair.getValue());

        System.out.println("repeat: " + repeat("go"));
        System.out.println("doubled: " + doubled(2.5));

        Container<String> created = Container.<String>of("OK");
        System.out.println("factory: " + created.get());
    }
}
```

Biên dịch và chạy trong thư mục chứa tệp với Java 17 hoặc mới hơn:

```bash
javac StaticGenericScopeDemo.java
java StaticGenericScopeDemo
```

Kết quả stdout:

```text
text: Java
number: 8
inferred: 42
explicit: 12
entry: age=20
instance: Java,true
repeat: [go, go]
doubled: 5.0
factory: OK
```

Giải thích kỹ từng điểm:

- `Container<T>` là **generic class**. `get()` trả về `T` thuộc một cách tham số hóa cụ thể, nên `text.get()` có kiểu tĩnh `String` và `count.get()` có kiểu `Integer`.
- `static <U> Container<U> of(U input)` là **static generic factory method**. `U` do method tự khai báo; `Container.of("Java")` tạo `Container<String>`, `Container.of(8)` tạo `Container<Integer>`. Literal `8` được boxing thành `Integer` khi cần.
- `static <U> U identity(U input)` không dùng đến `T` của class. `Container.identity(42)` suy luận `U = Integer`, dù không có một biến `Container<Integer>` nào tham gia lời gọi.
- `Container.<Number>identity(12)` chỉ định **type argument `Number` cho chính static method**. Object đầu vào vẫn là `Integer` sau boxing; kết quả được nhìn qua kiểu tĩnh `Number`.
- `static <K, V> Map.Entry<K, V> entry(...)` khai báo **hai** type parameter; `Container.<String, Integer>entry("age", 20)` cung cấp cả hai theo thứ tự. `Map.entry` không chấp nhận giá trị `null` và trả entry không hỗ trợ `setValue`.
- `<U> Map.Entry<T,U> combineWith(U other)` là **instance generic method**. Nó có quyền dùng `T` của `Container<T>` **và** `U` của chính method. Với `text` là `Container<String>` và đối số `true` (boxing thành `Boolean`), kết quả có kiểu `Map.Entry<String, Boolean>`.
- `static <E> List<E> repeat(E element)` được khai báo trong lớp `StaticGenericScopeDemo` **không generic**. Điều đó cho thấy phương thức static generic không bắt buộc nằm trong một generic class. `List.of` tạo danh sách không sửa được; cũng không nhận `null`.
- `static <N extends Number> double doubled(N number)` dùng bound `N extends Number`, nên có thể gọi `doubleValue()`. `doubled(2.5)` boxing giá trị `double` thành `Double` và in `5.0`.
- `Container.<String>of("OK")` chỉ định `U = String` cho factory. Không có khái niệm “gán `T = String` cho cả lớp `Container` tại runtime”; kiểu `Container<String>` của kết quả được kiểm tra lúc biên dịch.

## 5. Static factory: ứng dụng quan trọng nhất của phương thức static generic

Một mẫu phổ biến khi xây generic class là constructor không public, còn factory công khai tự suy luận type parameter cho object mới:

```java
class Box<T> {
    private final T value;

    private Box(T value) {
        this.value = value;
    }

    public static <U> Box<U> of(U value) {
        return new Box<>(value);
    }

    public T get() {
        return value;
    }
}
```

Đây là **một lớp minh họa khác**, độc lập với `Container` của chương trình đã chạy. Có thể sử dụng:

```java
Box<String> name = Box.of("Ada");
Box<Integer> points = Box.of(100);
```

Tại sao chữ ký factory phải là `static <U> Box<U> of(U value)`?

1. Method static không được dùng `T` của lớp để viết `static Box<T> of(T value)`.
2. Khai báo `<U>` cung cấp type parameter mới, có phạm vi trong phương thức factory.
3. `Box<U>` ở kiểu trả về liên kết **kiểu đối số factory** với **kiểu của object Box được tạo**.
4. Trình biên dịch dùng `U` để kiểm tra `new Box<>(value)` và kiểu kết quả.
5. Việc tên phương thức là `of` chỉ là lựa chọn API; cũng có thể dùng `from`, `create` hoặc tên diễn đạt hợp đồng.

Một chữ ký như `static <U> Box<Object> of(U value)` vẫn là static generic method hợp lệ, nhưng nó **đánh mất** kiểu `U` trong kiểu trả về, thường không phải hợp đồng mong muốn. Thiết kế `Box<U>` mới giữ được an toàn kiểu.

Những factory phổ biến trong Java API (ví dụ `List.of(...)`, `Map.entry(...)`, `Optional.of(...)`) đều minh họa lợi ích của các static method liên quan đến tham số kiểu. Cần đọc **chữ ký thực tế** của từng API để biết method có tham số kiểu nào, quy tắc xử lý null ra sao và kết quả có mutable hay không; không suy ra mọi factory hoạt động giống nhau chỉ từ tên `of`.

## 6. Suy luận kiểu và đối số kiểu tường minh của static method

Java thường suy luận type argument từ đối số và kiểu đích:

```java
String word = Container.identity("hello");
Integer size = Container.identity(5);
Number number = Container.<Number>identity(5);
```

Các dòng trên sử dụng lớp `Container` của chương trình chạy độc lập và cần đặt trong một method hợp lệ.

- `Container.identity("hello")`: kiểu đối số `String` tạo ràng buộc đủ để suy luận `U = String`.
- `Container.identity(5)`: giá trị primitive `int` được boxing thích hợp; `U` được suy luận là `Integer`.
- `Container.<Number>identity(5)`: chọn `U = Number` tường minh; boxing tạo `Integer`, là subtype của `Number`.
- Type witness **không phải cast** và không chuyển đổi tùy ý. `Container.<String>identity(5)` không biên dịch vì `int` không thể truyền vào vị trí yêu cầu `String`.

Cú pháp của lời gọi static với đối số kiểu tường minh là:

```java
ClassName.<ConcreteType>method(arguments);
ClassName.<FirstType, SecondType>method(arguments);
```

Đây là **mẫu cú pháp mô tả**, cần thay tên lớp, kiểu, phương thức và đối số trước khi dùng. Không viết `ClassName.method<ConcreteType>(...)` theo thói quen từ ngôn ngữ khác.

Một ví dụ thường gặp về target typing với method không nhận đầu vào:

```java
static <T> List<T> empty() {
    return List.of();
}
```

Với phương thức `empty()` được đặt trong một lớp `CollectionsHelper`, người gọi có thể viết:

```java
List<String> texts = CollectionsHelper.empty();
var explicit = CollectionsHelper.<String>empty();
```

Hai lời gọi đều cho kiểu tĩnh `List<String>`; dòng đầu suy luận nhờ target type, dòng sau nhờ type witness. Nếu viết `var inferred = CollectionsHelper.empty();` trong một ngữ cảnh không đủ thông tin, kiểu thường được suy luận rộng như `List<Object>`. Phần `<T>` thuộc method `empty` và không dựa vào generic class bao.

## 7. Phương thức static với nhiều type parameter và ràng buộc

Một phương thức static có thể khai báo nhiều type parameter:

```java
public static <K, V> java.util.Map.Entry<K, V> entry(K key, V value) {
    return java.util.Map.entry(key, value);
}
```

`K` và `V` có phạm vi **trong cùng một method**; chúng độc lập với nhau. Có thể gọi `ClassName.<String, Integer>entry("age", 20)`, hoặc để compiler tự suy luận. Khi chỉ định tường minh, phải cung cấp đủ các type argument theo đúng thứ tự.

Một tham số kiểu cũng có thể có bound:

```java
public static <N extends Number> double doubled(N value) {
    return value.doubleValue() * 2;
}
```

Phần `<N extends Number>` yêu cầu type argument là `Number` hoặc subtype phù hợp. `doubled(4)` hợp lệ qua `Integer`; `doubled("4")` bị loại ở compile time. Bound `extends` **không làm phương thức trở thành instance method** và không cấp cho static method quyền dùng `T` ở mức lớp.

Có thể liên kết nhiều bound:

```java
public static <T extends Number & Comparable<T>> T larger(T a, T b) {
    return a.compareTo(b) >= 0 ? a : b;
}
```

Phương thức trên là **mẫu minh họa**, cần đặt trong class. `Integer` thỏa cả `Number` lẫn `Comparable<Integer>`. Trong cú pháp nhiều bound, nếu có bound là class thì nó phải đứng đầu, tiếp theo là các interface phân tách bằng `&`. Không khai báo lower bound `<T super Number>`; `? super T` là cú pháp wildcard ở vị trí sử dụng type, không phải tên tham số kiểu mới.

## 8. Phân biệt static generic method với instance method dùng kiểu của lớp

Giả sử một lớp generic khai báo:

```java
class Shelf<T> {
    private T item;

    Shelf(T item) {
        this.item = item;
    }

    T get() {
        return item;
    }

    void set(T next) {
        this.item = next;
    }

    <U> String describeWith(U label) {
        return label + "=" + item;
    }

    static <U> U echo(U input) {
        return input;
    }
}
```

Đây là **một lớp minh họa độc lập**.

| Thành viên | Có tự khai báo type parameter không? | Có dùng `T` của lớp không? | Ý nghĩa |
|---|---|---|---|
| `T get()` | Không | Có | Đọc phần tử theo kiểu của `Shelf<T>` |
| `void set(T next)` | Không | Có | Thay phần tử theo kiểu của instance |
| `<U> String describeWith(U label)` | Có (`U`) | Có, thông qua `item` | Method instance phối hợp cả kiểu lớp và kiểu method |
| `static <U> U echo(U input)` | Có (`U`) | Không | Method static có tham số kiểu độc lập |

Từ `Shelf<String> shelf = new Shelf<>("book")`:

- `shelf.set("new book")` hợp lệ, vì `T = String` trong kiểu instance.
- `shelf.describeWith(42)` hợp lệ, vì method instance tự suy luận `U = Integer`, trong khi trường `item` vẫn là `String`.
- `Shelf.echo(42)` hợp lệ. **Không cần** một đối tượng `Shelf<Integer>` để chọn `U = Integer`.
- `shelf.set(42)` không hợp lệ: `set` dùng `T = String` từ lớp, không có type parameter mới.
- Không nên gọi static method bằng biểu thức instance (`shelf.echo(...)`) dù một số cú pháp Java có thể cho phép: nó khiến người đọc tưởng method phụ thuộc vào trạng thái đối tượng. Hãy dùng tên lớp.

Điểm phân biệt quan trọng: `get()` **không phải một generic method tự khai báo type parameter**, mặc dù kiểu trả về của nó là `T`; nó sử dụng type parameter **do class khai báo**. Trong khi đó `static <U> U echo(U input)` là một generic method thực sự.

## 9. Static nested class: cùng quy tắc static, nhưng type parameter ở cấp lớp lồng

Một `static nested class` có thể tự generic mà **không mang tham số kiểu của outer**. Chẳng hạn:

~~~java
class Outer<T> {
    static final class Nested<U> {
        private final U data;
        Nested(U data) { this.data = data; }
        U get() { return data; }
    }
}
~~~

`Outer.Nested<String>` hợp lệ; `Outer<String>.Nested<Integer>` không phải cú pháp tham số hóa đúng của static member type. `U` thuộc chính lớp `Nested`, trong khi `T` không được dùng trực tiếp trong ngữ cảnh static đó.

Phương thức static **bên trong** `Nested<U>` cũng phải khai báo type parameter riêng nếu muốn generic, như `static <X> X identity(X input)`; nó không được dùng trực tiếp `U` của `Nested`. Đây là điểm tiếp nối quy tắc của phương thức static; chi tiết về inner class, nested factory, bounds và enclosing instance thuộc chủ đề lớp lồng.

## 10. Tránh che tên `T` của class bằng type parameter mới

Java cho phép viết `class Wrapper<T> { static <T> T echo(T input) { return input; } }`. Tuy hợp lệ, **`T` của method không phải `T` của class**; tên trùng gây ảo giác method phụ thuộc một `Wrapper<T>` instance.

Nếu hai phạm vi độc lập, nên viết `static <U> U echo(U input)`. Đổi tên không thay runtime behavior nhưng giúp phân biệt ngay nơi khai báo. Nếu method thật sự cần giá trị trong một `Wrapper<T>`, hãy nhận `Wrapper<U>` làm tham số hoặc chuyển thành instance method; đừng dùng unchecked cast để nối hai `T` không liên quan.

Cơ chế shadowing, bounds khác nhau giữa hai khai báo và các lỗi setter/return type do reuse tên `T` cần được phân tích riêng; mục đích ở đây chỉ là thiết kế chữ ký **static generic method** rõ ràng.

## 11. Vì sao type erasure không xóa khả năng kiểm tra kiểu khi biên dịch?

Generic static method tham gia vào cơ chế **type erasure** của Java giống các generic method khác:

```java
static <U> U identity(U value) {
    return value;
}
```

Với `U` không bound, kiểu đại diện sau erasure thường là `Object`. Compiler kiểm tra các lời gọi như `String s = identity("x")` và có thể chèn cast thích hợp trong bytecode. Điều này giải thích tại sao cùng một method static có thể xử lý `String`, `Integer` và các reference type khác mà không cần runtime tạo nhiều phiên bản khác nhau.

Hệ quả:

- Không thể dùng `new U()` chỉ vì method khai báo `<U>`: không có constructor runtime được suy ra từ type parameter.
- Không thể dùng trực tiếp `U.class` hoặc `obj instanceof U` cho một type variable thông thường không reifiable.
- Không thể truyền primitive như `<int>`; `<Integer>` hoặc autoboxing là cách thích hợp với Java 17.
- Một lời gọi `Container.<String>identity("x")` không tạo ra một đối tượng `Container<String>` hay thay đổi state của bất kỳ `Container` nào; chỉ lời gọi factory có trả về `Container<U>` mới tạo object theo thân factory.
- Có thể sinh bridge method khi cần bảo toàn semantics override trong những trường hợp generics và kế thừa, nhưng sự tồn tại của `<U>` ở static method **không tự tạo dynamic dispatch**.

Nếu cần **tạo đối tượng U**, thiết kế một factory rõ ràng bằng `Supplier<U>`:

```java
static <U> U create(java.util.function.Supplier<U> factory) {
    return java.util.Objects.requireNonNull(factory).get();
}
```

Đây là **method minh họa** cần đặt trong class. Lời gọi `create(StringBuilder::new)` có thể trả `StringBuilder`; thông tin cách tạo được cung cấp bởi `Supplier<U>`, không phải do `U` tự chứa một constructor.

## 12. Lỗi hay gặp với static và generic type parameter

**Lỗi 1 — Static method dùng `T` của class.** Trong `class Box<T>`, khai báo `static T identity(T x)` là sai vì `T` thuộc phạm vi instance của generic class. Sửa thành `static <U> U identity(U x)` nếu method độc lập kiểu lớp.

**Lỗi 2 — Static field mang kiểu `T` của class.** `static T lastValue;` không hợp lệ. Nếu dữ liệu thực sự thuộc về mỗi object, dùng trường instance `private T lastValue;`. Nếu dữ liệu là chung toàn lớp, chọn một kiểu static cụ thể hoặc thiết kế lưu trữ phù hợp.

**Lỗi 3 — Quên `<U>` trước return type.** `static U echo(U input)` không biên dịch nếu `U` chưa được khai báo đúng phạm vi. Viết `static <U> U echo(U input)`.

**Lỗi 4 — Đặt `<U>` ở vị trí sau tên method.** `static U echo<U>(U input)` là sai cú pháp Java. Đặt `<U>` sau modifier và trước kiểu trả về.

**Lỗi 5 — Tưởng `Container<String>` quyết định `U` cho static method.** `Container.identity(7)` được suy luận từ lời gọi; không cần tạo `Container<Integer>`. Lời gọi static được viết bằng tên lớp để làm rõ điều đó.

**Lỗi 6 — Không phân biệt static factory với instance method.** `static <U> Box<U> of(U value)` tạo `Box<U>` mới; `T get()` đọc giá trị của một instance `Box<T>`. Hai chữ ký sử dụng type parameter từ hai phạm vi khác nhau.

**Lỗi 7 — Dùng `<U>` nhưng trả về `Box<Object>`.** Nếu hợp đồng yêu cầu giữ kiểu của đối số, trả `Box<U>`. Trả về `Box<Object>` đánh mất thông tin kiểu cần thiết, dù có thể vẫn biên dịch.

**Lỗi 8 — Cho rằng static generic method là một bản riêng theo `U` ở runtime.** Thông thường Java dùng erasure. `identity("x")` và `identity(1)` gọi cùng định nghĩa method static, không tạo mã nguồn/bytecode chuyên biệt theo type argument.

**Lỗi 9 — Dùng cùng tên `T` ở class và method mà không nhận ra che khuất.** `static <T> T echo(T x)` có `T` riêng; nên chọn `U` khi cần phân biệt với `class Wrapper<T>`.

**Lỗi 10 — Hiểu nhầm static nested class kế thừa kiểu của outer class.** `static class Nested<U>` có `U` độc lập, không được dùng trực tiếp `T` của `Outer<T>`. Không dùng `Outer<String>.Nested<Integer>` như cách khai báo inner class.

**Lỗi 11 — Lạm dụng generic method khi không cần quan hệ kiểu.** Nếu chỉ in dữ liệu, `static void print(Object x)` có thể đủ rõ; `static <T> void print(T x)` hợp lệ nhưng không tự tạo lợi ích an toàn kiểu khi `T` không ràng buộc với vị trí khác.

**Lỗi 12 — Nhầm `static <T>` với phép chuyển đổi tùy ý.** `Container.<String>identity(123)` vẫn không biên dịch; method bị ràng buộc theo type argument đã chỉ định. Type witness không có chức năng tự biến số nguyên thành chuỗi.

## 13. Tự kiểm tra với đáp án

**Câu 1.** Vì sao `class Box<T> { static T get(); }` sai?  
**Đáp án:** Static method không có một cách tham số hóa instance cụ thể để sử dụng `T` của class; cần kiểu cụ thể hoặc type parameter riêng của method.

**Câu 2.** `class Box<T> { static <U> U echo(U x) { return x; } }` hợp lệ không?  
**Đáp án:** Hợp lệ; `U` thuộc phương thức static.

**Câu 3.** Nếu có `Box<String> b`, gọi `Box.echo(10)` thì `U` là gì?  
**Đáp án:** Có thể suy luận `U = Integer` qua boxing của `10`; việc `b` có `T = String` không ảnh hưởng.

**Câu 4.** `static <U> Box<U> of(U value)` mang lợi ích gì so với trả `Box<Object>`?  
**Đáp án:** Bảo toàn mối liên hệ kiểu giữa đối số truyền vào và đối tượng `Box<U>` trả về.

**Câu 5.** `static <U> void log(U x)` có phải generic method không, dù trả `void`?  
**Đáp án:** Có. Phương thức generic được nhận diện từ việc tự khai báo type parameter `<U>`, không phụ thuộc kiểu trả về.

**Câu 6.** `static <N extends Number> double doubled(N n)` có chấp nhận `String` không?  
**Đáp án:** Không. `String` không thỏa bound `Number`.

**Câu 7.** Phương thức instance có thể sử dụng đồng thời kiểu của class và kiểu tự khai báo không?  
**Đáp án:** Có, ví dụ `<U> Map.Entry<T, U> combineWith(U value)` trong `Container<T>`.

**Câu 8.** `Outer<T>.Nested<U>` là cú pháp cần dùng khi `Nested` được khai báo static không?  
**Đáp án:** Không. Static nested class không phụ thuộc một outer instance đã tham số hóa; dùng `Outer.Nested<U>`.

**Câu 9.** Một static generic method có thể khai báo hai kiểu độc lập không?  
**Đáp án:** Có: `static <K,V> Map.Entry<K,V> entry(K k,V v)`.

**Câu 10.** `Container.<Integer>identity(7)` khác `Container.identity(7)` ở đâu?  
**Đáp án:** Lời gọi thứ nhất chỉ định type argument tường minh; lời gọi thứ hai để compiler suy luận. Trong ví dụ, cả hai đều hợp lệ và trả giá trị `7` với kiểu tĩnh phù hợp.

**Câu 11.** Vì sao `new U()` không được phép chỉ nhờ khai báo `static <U>`?  
**Đáp án:** Tham số kiểu bị erasure và không cung cấp constructor runtime; cần factory/`Supplier<U>` nếu muốn tạo đối tượng.

**Câu 12.** Khi nào không cần viết `<U>` cho method trong generic class?  
**Đáp án:** Nếu method instance chỉ cần sử dụng `T` của class, ví dụ `T get()`; hoặc nếu static method chỉ xử lý kiểu cụ thể và không cần tham số kiểu độc lập.

**Câu 13.** Vì sao `static <T> T echo(T value)` trong `class Box<T>` dễ gây hiểu nhầm dù có thể hợp lệ?  
**Đáp án:** `T` của method che khuất `T` của class; nên cân nhắc dùng `<U>` để thể hiện hai phạm vi rõ ràng.

**Câu 14.** Nếu `static <U> List<U> repeat(U value)` nhận một `String`, kết quả có kiểu gì?  
**Đáp án:** Khi `U = String`, kết quả là `List<String>`; lời gọi `repeat("hi")` có thể suy luận điều đó khi biên dịch.

Quy tắc cốt lõi: **type parameter của generic class gắn với kiểu instance, còn type parameter của static generic method được khai báo ngay trên method và độc lập với instance**. Hãy đọc chữ ký `static <U> ...` như một hợp đồng mới, không phải một cách “mượn” `T` từ `class Container<T>`. Khi hiểu đúng phạm vi, bạn có thể viết static factory, hàm tiện ích và API generic an toàn mà không mắc lỗi `non-static type variable cannot be referenced from a static context`.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="type-parameter-scope-005">Lớp thành viên bên trong và tham số kiểu của lớp bao</a>

<details>
<summary>Xem chi tiết</summary>

# Lớp thành viên bên trong và tham số kiểu của lớp bao trong Java

Một **lớp thành viên bên trong** (*non-static member inner class*) được khai báo trong thân một lớp khác mà **không có từ khóa `static`**. Nếu lớp bao khai báo tham số kiểu `T`, inner class có thể sử dụng `T` trong field, constructor và phương thức của mình mà **không cần khai báo lại**. Một inner class cũng có thể khai báo tham số kiểu riêng, chẳng hạn `U`.

Ví dụ điển hình là `Outer<T>.Inner<U>`: `T` gắn với kiểu tham số hóa của lớp bao, còn `U` là tham số kiểu của chính lớp bên trong. Mỗi đối tượng inner luôn được gắn với **một đối tượng outer cụ thể**, nên cách tạo đối tượng và cách truy cập thành viên khác với `static nested class`.

Bài này giải thích phạm vi biến kiểu, cú pháp tạo đối tượng, cách đọc `Outer<String>.Inner<Integer>`, quan hệ giữa hai lớp, giới hạn của generics, sự khác biệt với `static nested class`, và các trường hợp sử dụng phù hợp. Các ví dụ Java dùng cú pháp chạy được với JDK 17 trở lên.

## 1. Cú pháp cơ bản: inner class dùng `T` của outer class

Xét lớp `Container<T>`:

```java
class Container<T> {
    private final T stored;

    Container(T stored) {
        this.stored = stored;
    }

    class View {
        T get() {
            return stored;
        }
    }
}
```

Những điểm cần đọc chính xác:

- `Container<T>` khai báo type parameter `T`.
- `View` là **non-static member inner class** vì nằm trong `Container` và không có `static`.
- `View` **không khai báo `<T>` riêng**; khi viết `T get()`, nó đang dùng `T` thuộc phạm vi của `Container<T>`.
- `View` có thể truy cập field `stored`, kể cả field `private`, bởi Java cho các thành viên thuộc cùng cấu trúc lồng truy cập nhau theo quy tắc ngôn ngữ.
- Đối tượng `View` phải được gắn với một đối tượng `Container` đã được tạo.

Trong ngữ cảnh có thể chạy:

```java
Container<String> container = new Container<>("Java");
Container<String>.View view = container.new View();

String text = view.get(); // Kiểu trả về là String.
```

Ở đây, `Container<String>.View` được hiểu là **kiểu `View` thuộc outer có kiểu `Container<String>`**. Vì `T = String`, `View.get()` được kiểm tra như phương thức trả `String`.

Không có `View<T>` ở đây: lớp `View` chưa tự khai báo tham số kiểu. Viết `Container<String>.View<String>` sẽ sai vì `View` không nhận đối số kiểu riêng.

## 2. Tại sao inner class nhìn thấy tham số kiểu của lớp bao?

Phạm vi của một type parameter khai báo trên class bao trùm các thành viên instance thích hợp của class đó, bao gồm khai báo member inner class không `static`. Vì inner class nằm trong ngữ cảnh instance của outer, Java có thể diễn đạt các thành viên của nó bằng chính tham số kiểu của outer.

```java
class ResultBox<T> {
    private final T result;

    ResultBox(T result) {
        this.result = result;
    }

    class Reader {
        T read() {
            return result;
        }

        void print() {
            System.out.println(result);
        }
    }
}
```

`Reader.read()` **không phải generic method tự khai báo `<T>`**. Nó chỉ dùng `T` từ `ResultBox<T>`. Khi đối tượng outer được sử dụng qua `ResultBox<Long>`, kết quả `read()` có kiểu `Long`; với `ResultBox<String>`, kết quả có kiểu `String`.

**Không cần lặp lại `<T>` trên `Reader`.** Việc tự khai báo `class Reader<T>` trong `ResultBox<T>` tạo một **tham số kiểu mới có cùng tên**, có khả năng che khuất `T` của outer trong phạm vi inner. Đó không phải cách “kế thừa” `T` và rất dễ gây nhầm lẫn. Nếu inner cần kiểu riêng, nên đặt tên như `<U>` để phân biệt.

Phạm vi cú pháp `T` và việc gắn inner với outer là hai khía cạnh liên quan nhưng khác nhau: `T` phục vụ **kiểm tra kiểu tĩnh**, còn một inner object có liên hệ với **đối tượng outer cụ thể** ở thời gian chạy.

## 3. Inner class có tham số kiểu riêng: `Outer<T>.Inner<U>`

Khi mỗi inner object cần thêm một kiểu độc lập, khai báo type parameter mới tại inner class:

```java
class Outer<T> {
    private final T value;

    Outer(T value) {
        this.value = value;
    }

    class Inner<U> {
        private final U extra;

        Inner(U extra) {
            this.extra = extra;
        }

        T outerValue() {
            return value;
        }

        U innerValue() {
            return extra;
        }
    }
}
```

Trong ví dụ:

- `T` được khai báo bởi `Outer<T>`.
- `U` được khai báo bởi `Inner<U>`.
- `outerValue()` trả `T` của outer.
- `innerValue()` trả `U` của inner.
- Không có ràng buộc bắt buộc rằng `T` và `U` phải là cùng một kiểu.

Cách dùng:

```java
Outer<String> outer = new Outer<>("Java");
Outer<String>.Inner<Integer> inner = outer.new Inner<>(17);

String language = inner.outerValue();
Integer version = inner.innerValue();
```

`Outer<String>.Inner<Integer>` là một kiểu có **hai thông tin generic ở hai cấp**. `String` được truyền vào `Outer<T>`, còn `Integer` được truyền vào `Inner<U>`.

Đọc từ ngoài vào trong:

| Thành phần | Ý nghĩa |
| --- | --- |
| `Outer<T>` | Lớp bao khai báo `T` |
| `Outer<String>` | Outer đã được tham số hóa với `T = String` |
| `Inner<U>` | Inner khai báo `U` riêng |
| `Outer<String>.Inner<Integer>` | Inner có `T = String` từ outer và `U = Integer` của riêng inner |
| `outer.new Inner<>(17)` | Tạo inner gắn với `outer`; `U` được suy luận từ ngữ cảnh/đối số |
| `inner.outerValue()` | Trả `String` |
| `inner.innerValue()` | Trả `Integer` |

Một outer có thể tạo **nhiều inner object với các kiểu `U` khác nhau**:

```java
Outer<String>.Inner<Integer> count = outer.new Inner<>(3);
Outer<String>.Inner<Boolean> enabled = outer.new Inner<>(true);
```

Các inner object này cùng liên hệ với outer mang `T = String`, nhưng mỗi inner có kiểu riêng cho `U`.

### Khác nhau giữa type parameter và object instance

Hai biểu thức `outer.new Inner<>(3)` và `anotherOuter.new Inner<>(3)` có thể cùng kiểu tĩnh `Outer<String>.Inner<Integer>` nếu hai outer đều là `Outer<String>`. Tuy vậy, inner tạo ra vẫn gắn với **hai outer object khác nhau** và có thể thấy hai trạng thái outer khác nhau. Type argument không mô tả **danh tính đối tượng**.

## 4. Cách tạo inner object: `outer.new Inner(...)`

Khởi tạo non-static inner class từ mã nằm ngoài outer cần một **enclosing instance** hợp lệ:

```java
Outer<String> outer = new Outer<>("Java");
Outer<String>.Inner<Integer> inner = outer.new Inner<>(17);
```

Cú pháp `outer.new Inner<>(17)` nhấn mạnh rằng inner object được tạo **trong quan hệ với chính outer object `outer`**.

Những dạng dưới đây được đưa ra để nhận diện lỗi, **không phải mã hợp lệ để chạy**:

```java
// Sai khi đứng trong ngữ cảnh không có enclosing instance thích hợp:
// Outer<String>.Inner<Integer> x = new Inner<>(17);

// Sai: inner không phải lớp static độc lập có thể tạo trực tiếp như thế.
// Outer<String>.Inner<Integer> x = new Outer.Inner<>(17);
```

Nếu tạo inner từ một **instance method của outer**, Java đã có enclosing instance hiện tại và có thể viết `new Inner<>(...)`:

```java
class Outer<T> {
    class Inner<U> {
        Inner(U data) {}
    }

    <U> Inner<U> create(U value) {
        return new Inner<>(value);
    }
}
```

Đây là đoạn minh họa độc lập với khai báo `Outer<T>` trước đó. `create` tự khai báo `<U>` vì phương thức cần biểu diễn kiểu giá trị truyền vào; inner class cũng khai báo `U`, nhưng tham số `U` của phương thức và tham số `U` của inner là **hai khai báo riêng** được liên kết thông qua `new Inner<>(value)` và kiểu trả về `Inner<U>`.

Nếu không cần khai báo `<U>` riêng trên `create`, bạn cũng có thể viết một method chỉ trả về một inner với `U` cố định, chẳng hạn `Inner<String> createLabel()`.

### Phân biệt với constructor của outer

Lệnh `new Outer<String>("Java")` tạo **outer object**. Sau đó `outer.new Inner<>(17)` tạo **inner object** liên hệ với outer đó. Java không tự tạo outer mới khi bạn gọi constructor của non-static inner class.

Một inner object có thể được truyền ra ngoài phạm vi tạo ban đầu, nhưng vẫn mang quan hệ với enclosing instance đã chọn. Đặc điểm này quan trọng trong việc thiết kế vòng đời đối tượng.

## 5. Ví dụ hoàn chỉnh Java 17+: cùng inner class, hai kiểu outer và inner khác nhau

Lưu toàn bộ nội dung sau vào **`Main.java`**. Chương trình có:

1. `Folder<T>` là lớp bao generic, giữ một nhãn kiểu `T`.
2. `Item<U>` là **non-static inner class** dùng cả `T` của outer và `U` của chính nó.
3. `Tag<V>` là **static nested class** có `V` riêng; nó không nhận `T` từ outer.
4. Hai đối tượng outer tạo inner có các cặp kiểu khác nhau.

```java
public class Main {
    static final class Folder<T> {
        private final T label;

        Folder(T label) {
            this.label = label;
        }

        final class Item<U> {
            private final U value;

            Item(U value) {
                this.value = value;
            }

            T ownerLabel() {
                return Folder.this.label;
            }

            U payload() {
                return value;
            }

            String describe() {
                return "label=" + label + ", value=" + value;
            }
        }

        static final class Tag<V> {
            private final V value;

            Tag(V value) {
                this.value = value;
            }

            V value() {
                return value;
            }
        }
    }

    public static void main(String[] args) {
        Folder<String> language = new Folder<>("Java");
        Folder<String>.Item<Integer> page = language.new Item<>(17);
        System.out.println("page: " + page.describe());

        Folder<Integer> priority = new Folder<>(3);
        Folder<Integer>.Item<String> note =
                priority.new Item<>("urgent");
        System.out.println("note: " + note.describe());

        Folder.Tag<String> tag = new Folder.Tag<>("shared");
        System.out.println("tag: " + tag.value());

        System.out.println("owner: " + page.ownerLabel());
        System.out.println("payload: " + page.payload());

        // Folder<String>.Item<String> wrong = language.new Item<>(17);
        // Không biên dịch: inner cần U = String, nhưng nhận Integer.
    }
}
```

Biên dịch/chạy trong thư mục chứa tệp:

```bash
javac -encoding UTF-8 Main.java
java Main
```

**Stdout:**

```text
page: label=Java, value=17
note: label=3, value=urgent
tag: shared
owner: Java
payload: 17
```

Diễn giải:

- `language` là `Folder<String>`; vì thế `page.ownerLabel()` có kiểu `String`.
- `page` là `Folder<String>.Item<Integer>`; vì thế `page.payload()` có kiểu `Integer`. Method `describe()` có thể đọc field `label` của outer và `value` của inner.
- `priority` là `Folder<Integer>`; vì thế `note.ownerLabel()` trả `Integer` và `note.payload()` trả `String`.
- `Folder.Tag<String>` là static nested class: `V = String` do chính `Tag<V>` khai báo, không lấy type parameter `T` của `Folder<T>`.
- Dòng sai kiểu được để comment để chương trình biên dịch bình thường; nếu bật lại, trình biên dịch từ chối do đối số constructor không khớp `U = String`.

`Folder` được khai báo `static` bên trong `Main` chỉ để dễ đóng gói chương trình độc lập. Điều đó **không làm `Item` trở thành static**: `Item` vẫn là non-static inner class của `Folder`, còn `Tag` là static nested class của `Folder`.

## 6. `Outer.this` và cách truy cập đối tượng bao

Vì một inner object có enclosing instance, code của inner có thể đọc các instance members hợp lệ của outer, kể cả thành viên `private`:

```java
class Counter<T> {
    private final T name;
    private int count;

    Counter(T name) {
        this.name = name;
    }

    class Incrementer {
        void increase() {
            count++;
        }

        T label() {
            return Counter.this.name;
        }
    }
}
```

Ở đây:

- `count++` truy cập field của enclosing `Counter` khi không có thành viên inner cùng tên gây che khuất.
- `Counter.this.name` nêu rõ **instance `Counter` bao quanh** của inner hiện tại.
- `label()` trả `T` thuộc outer; bản thân `Incrementer` không khai báo tham số kiểu mới.

`Outer.this` hữu ích khi inner và outer có các field trùng tên:

```java
class Example<T> {
    T value;

    class Inner {
        String value;

        T outerValue() {
            return Example.this.value;
        }

        String innerValue() {
            return this.value;
        }
    }
}
```

`this.value` trỏ field của inner; `Example.this.value` trỏ field của outer. Lưu ý đây là **che khuất tên field**, khác với việc che khuất **tên type parameter**. Cả hai đều liên quan đến scope nhưng do các khai báo khác nhau tạo ra.

Một inner instance có thể truy cập trạng thái outer, nhưng điều đó **không bảo đảm trạng thái bất biến**. Nếu outer có các field mutable, inner có thể quan sát hoặc gây ra thay đổi của outer theo phương thức được khai báo.

## 7. Inner class dùng `T` của outer mà không khai báo type parameter riêng

Không phải inner nào cũng cần `<U>`. Ví dụ thiết kế một con trỏ đọc dữ liệu của kho:

```java
class Vault<T> {
    private final T secret;

    Vault(T secret) {
        this.secret = secret;
    }

    class Reader {
        T read() {
            return secret;
        }
    }
}
```

Sử dụng:

```java
Vault<String> vault = new Vault<>("key");
Vault<String>.Reader reader = vault.new Reader();
String result = reader.read();
```

`Reader` không phải generic class **do bản thân nó khai báo type parameter**. Nhưng **kiểu `Vault<String>.Reader` vẫn phụ thuộc vào cách tham số hóa của outer**. Vì vậy, không nên nhầm “inner không có `<U>`” với “inner không bị ảnh hưởng bởi generics”.

Nếu mục tiêu chỉ là giữ một đối tượng tham chiếu đến outer và cung cấp các phương thức dùng `T`, kiểu thiết kế này thường đủ. Chỉ thêm `<U>` khi inner thật sự cần một kiểu độc lập mới.

## 8. Non-static inner class và static nested class

Hai hình thức cùng được khai báo **trong thân một lớp**, nhưng mối quan hệ với enclosing instance hoàn toàn khác nhau.

### 8.1. Non-static member inner class

```java
class Owner<T> {
    private final T name;

    Owner(T name) {
        this.name = name;
    }

    class Inner {
        T ownerName() {
            return name;
        }
    }
}
```

Một `Owner<String>.Inner` được gắn với object `Owner<String>`, nên có thể dùng `T` của lớp bao và truy cập field `name` của outer.

### 8.2. Static nested class

```java
class Owner<T> {
    static class Metadata<U> {
        private final U data;

        Metadata(U data) {
            this.data = data;
        }

        U data() {
            return data;
        }
    }
}
```

`Metadata<U>` không có enclosing instance `Owner<T>`, nên **không thể trực tiếp sử dụng tham số kiểu `T` thuộc lớp bao** hay đọc field instance của outer nếu không có một tham chiếu đối tượng được truyền vào.

Cách dùng:

```java
Owner.Metadata<String> meta = new Owner.Metadata<>("info");
String info = meta.data();
```

Cú pháp đúng là `Owner.Metadata<String>`. Không viết `Owner<Integer>.Metadata<String>`: static nested type không mang type argument của lớp bao trong phần định danh kiểu. Nếu nó cần thao tác với `T`, hãy khai báo tham số kiểu của chính `Metadata` hoặc truyền một object outer có kiểu cụ thể thích hợp.

### So sánh trực tiếp

| Tiêu chí | Non-static inner | Static nested |
| --- | --- | --- |
| Khai báo | `class Inner` bên trong outer | `static class Nested` bên trong outer |
| Có enclosing instance của outer | Có | Không |
| Trực tiếp dùng `T` của `Outer<T>` | Có, khi không bị che khuất | Không |
| Có thể khai báo kiểu riêng `<U>` | Có | Có |
| Kiểu tham chiếu thường gặp | `Outer<String>.Inner<Integer>` | `Outer.Nested<Integer>` |
| Tạo từ ngoài outer | `outer.new Inner<>(...)` | `new Outer.Nested<>(...)` |
| Truy cập `Outer.this` | Có khi có enclosing instance phù hợp | Không |
| Hợp lý khi | Hành vi phụ thuộc một outer object cụ thể | Thành phần chỉ được nhóm theo tên và độc lập outer object |

**Quy tắc thiết kế:** nếu nested class không cần đọc hoặc sửa trạng thái của một outer object, cân nhắc `static nested class` để tránh giữ một tham chiếu enclosing không cần thiết. Nếu nested class làm việc mật thiết với trạng thái riêng của outer, non-static inner class là lựa chọn có chủ đích.

## 9. Ràng buộc kiểu ở outer và inner

Outer có thể giới hạn `T`, inner có thể đặt bound cho `U` mà không làm thay đổi trách nhiệm của nhau:

```java
class NumberOwner<T extends Number> {
    private final T number;

    NumberOwner(T number) {
        this.number = number;
    }

    class Caption<U extends CharSequence> {
        private final U text;

        Caption(U text) {
            this.text = text;
        }

        double numericValue() {
            return number.doubleValue();
        }

        int textLength() {
            return text.length();
        }
    }
}
```

Ở đây `T extends Number` bảo đảm outer hỗ trợ `doubleValue()`, còn `U extends CharSequence` bảo đảm inner hỗ trợ `length()`. Khai báo đúng:

```java
NumberOwner<Integer> outer = new NumberOwner<>(7);
NumberOwner<Integer>.Caption<String> caption =
        outer.new Caption<>("level");
```

`NumberOwner<String>` là lỗi vì `String` không kế thừa `Number`. `Caption<Integer>` là lỗi vì `Integer` không triển khai `CharSequence`.

Bound kiểm tra **kiểu tham chiếu**, không tự kiểm tra giá trị cụ thể. `NumberOwner<Double>` vẫn có thể chứa `Double.NaN`; `Caption<String>` vẫn có thể chứa chuỗi rỗng nếu constructor không kiểm tra.

## 10. Các trường hợp phạm vi dễ nhầm

### 10.1. Không khai báo lại `<T>` chỉ để dùng `T` của outer

```java
class Outer<T> {
    class Good {
        T getValue() {
            return null;
        }
    }
}
```

`Good` dùng `T` outer. Nếu viết `class Good<T>` thì tên `T` của inner **che khuất** `T` outer trong phạm vi của inner; loại `T` mới không tự đồng nhất với `T` cũ. Để tránh nhầm, đặt tên `U` khi inner cần type parameter riêng.

Đoạn `return null` ở đây chỉ minh họa cú pháp type scope, không phải logic triển khai đầy đủ.

### 10.2. Một phương thức trong inner có thể có type parameter riêng

```java
class Outer<T> {
    class Inner {
        <U> U echo(U value) {
            return value;
        }
    }
}
```

`U` trong `<U> U echo(U value)` thuộc **method**, không thuộc `Outer` hay `Inner`. Vì vậy một outer `Outer<String>` có thể có inner gọi `echo(5)` và `echo(true)` với kiểu `U` khác nhau. Việc phương thức dùng chung tên với một type parameter ở phạm vi ngoài, nếu có, cần được xem xét để tránh che khuất.

### 10.3. Non-static member inner class khác local class và anonymous class

Một **member inner class** như `Outer<T>.Inner` có tên và được khai báo trực tiếp trong thân outer. Một **local class** được khai báo trong block/method; một **anonymous class** không có tên khai báo riêng. Các dạng class lồng này có quy tắc ngữ cảnh và phạm vi riêng, nhất là khi được khai báo trong static context.

Không nên dùng cú pháp `Outer<T>.Inner<U>` một cách máy móc cho local hoặc anonymous class: nó dành cho member inner type có tên. Bài này tập trung vào **lớp thành viên bên trong** theo đúng phạm vi bài học.

### 10.4. Khai báo inner trong static context và vai trò của `this`

Một method `static` không có enclosing instance outer hiện tại. Nó có thể tạo inner nếu được cung cấp **một outer object rõ ràng**:

```java
class Outer<T> {
    class Inner {
        T get() {
            return null;
        }
    }

    static <T> Outer<T>.Inner make(Outer<T> outer) {
        return outer.new Inner();
    }
}
```

Ở đây `<T>` của `make` là **type parameter riêng của static method**; nó không phải `T` của `class Outer<T>`. Vì nhận `Outer<T> outer` qua đối số, static method có đủ outer instance để tạo inner.

Trong khi đó, khai báo static method chỉ viết `static Outer<T>.Inner make()` mà không tự khai báo `T` là sai vì static member không thể trực tiếp dùng type parameter của outer instance.

## 11. Type erasure và quan hệ đối tượng lúc chạy

Generics trong Java chủ yếu được triển khai bằng **type erasure**: compiler kiểm tra type arguments lúc biên dịch rồi xóa phần lớn thông tin kiểu ấy khỏi bytecode runtime. Thực thể `Outer<String>.Inner<Integer>` không trở thành một lớp JVM riêng so với `Outer<Long>.Inner<Boolean>`.

Tuy vậy, non-static inner instance vẫn gắn với một enclosing outer object. Đây là **quan hệ object thật lúc chạy**, không chỉ là ký hiệu generic:

- Cú pháp `outer.new Inner(...)` chọn outer object cụ thể.
- Bên trong, `Outer.this` tham chiếu enclosing instance ấy.
- Biên dịch thường tạo cơ chế lưu tham chiếu enclosing cho non-static inner object.
- Nếu giữ inner sống lâu, outer có thể tiếp tục được giữ trong bộ nhớ qua quan hệ tham chiếu, tùy đồ thị tham chiếu thực tế.

Không nên suy luận rằng vì type erasure xóa `T` nên inner “mất quyền” truy cập outer object. **Generic type metadata** và **liên kết đối tượng** là hai cơ chế khác nhau.

Tương tự, không thể dùng `instanceof Outer<String>.Inner<Integer>` để xác minh chính xác hai type arguments; kiểu tham số hóa cụ thể không phải một runtime type có thể kiểm tra trực tiếp theo cách đó. Với dữ liệu đến từ reflection, JSON hoặc raw types, cần kiểm tra ranh giới dữ liệu bằng cơ chế thích hợp.

## 12. Khi nào inner class phù hợp?

**Phù hợp:** inner object là một **thành phần gắn với một outer instance cụ thể**, cần gọi phương thức hoặc đọc field của outer và sử dụng cùng type parameter `T`. Ví dụ con trỏ, view, iterator hoặc builder có quan hệ chặt với trạng thái của đối tượng bao.

**Cân nhắc static nested class:** nested type được nhóm theo outer về mặt tổ chức mã nhưng không cần `Outer.this` hoặc trạng thái object. Một helper chứa cấu hình riêng, mô tả metadata hay một value object độc lập thường dễ quản lý khi không có enclosing reference.

**Cân nhắc top-level class:** nếu nested type trở thành API công khai dùng rộng rãi, có vòng đời độc lập hoặc cần nhiều lớp khác phụ thuộc, một top-level class có tên riêng có thể rõ hơn. Sự lựa chọn nên dựa trên mối quan hệ nghiệp vụ và cách sử dụng, không dựa vào việc nested class có thể truy cập private members.

**Cân nhắc chi phí vòng đời:** giữ inner instance ở cache hoặc callback lâu dài có thể giữ theo enclosing object. Nếu không cần liên kết này, static nested class thường giúp giảm sự phụ thuộc và tránh giữ reference ngoài ý muốn.

## 13. Lỗi thường gặp và cách sửa

**Lỗi 1 — tạo non-static inner mà không cung cấp outer object:** dùng `outer.new Inner(...)` khi ở bên ngoài instance context; không dùng `new Outer.Inner(...)` như static nested.

**Lỗi 2 — dùng sai tên kiểu trong khai báo:** inner không có type parameter riêng thì `Outer<String>.Inner` là đúng, không viết `Outer<String>.Inner<String>`. Nếu inner khai báo `<U>`, viết `Outer<String>.Inner<Integer>`.

**Lỗi 3 — trộn kiểu outer:** một `Outer<String>.Inner<Integer>` không phải tự động là `Outer<Integer>.Inner<Integer>`. Type argument của outer tham gia vào kiểu thành viên. Đối số kiểu khác nhau không chuyển đổi được chỉ vì cùng class declaration.

**Lỗi 4 — nghĩ inner phải khai báo lại `<T>` để dùng `T` của outer:** điều đó tạo type parameter khác và có thể che khuất tên cũ. Chỉ khai báo `<U>` mới khi cần vai trò kiểu riêng.

**Lỗi 5 — dùng `T` của outer trong static nested class:** static nested không có enclosing instance và không trực tiếp mang type parameter của outer. Khai báo type parameter riêng hoặc truyền `Outer<T>` từ nơi gọi.

**Lỗi 6 — truy cập field trùng tên mà không chỉ rõ scope:** `this.value` là field của inner nếu được khai báo; `Outer.this.value` chỉ field của enclosing outer. Dùng tên đầy đủ khi quan hệ cần rõ ràng.

**Lỗi 7 — xem inner chỉ là cách nhóm file:** non-static inner có liên kết object thật. Nếu không cần liên kết và state, ưu tiên static nested hoặc top-level class.

**Lỗi 8 — hiểu type erasure là không còn outer instance:** erasure làm mất phần lớn type arguments runtime, nhưng không xóa quan hệ enclosing instance của non-static inner.

**Lỗi 9 — quên kiểm tra null/giá trị:** `Outer<String>` không buộc `T` khác `null`, `Inner<Integer>` không buộc số dương. Validation và loại tham chiếu generic là các mối quan tâm độc lập.

## 14. Tự kiểm tra

**Câu 1.** Trong `class Outer<T> { class Inner { T read() {...} } }`, `Inner` có cần khai báo `<T>` nữa không?

**Đáp án:** Không. `Inner` là non-static member inner class và dùng được `T` của outer. Tự khai báo `<T>` lần nữa sẽ tạo type parameter khác, che khuất tên cũ.

**Câu 2.** Với `Outer<String> outer`, tạo một inner có type parameter `Integer` như thế nào?

**Đáp án:** Nếu khai báo `class Inner<U>`, sử dụng `Outer<String>.Inner<Integer> x = outer.new Inner<>(17);`.

**Câu 3.** Vì sao `Outer<String>.Inner<Integer>` biểu diễn hai type arguments chứ không chỉ `Integer`?

**Đáp án:** `T = String` đến từ `Outer<T>`, `U = Integer` đến từ `Inner<U>`. Các method dùng `T` có kiểu `String`, còn method dùng `U` có kiểu `Integer`.

**Câu 4.** `static class Metadata<U>` bên trong `Outer<T>` có thể trực tiếp trả field `T` của outer không?

**Đáp án:** Không, vì static nested class không có enclosing outer instance và không trực tiếp sử dụng `T` của outer. Muốn làm việc với dữ liệu outer cần có tham số kiểu/đối tượng truyền vào phù hợp.

**Câu 5.** Hai inner objects cùng kiểu `Outer<String>.Inner<Integer>` nhưng được tạo từ hai outer objects khác nhau có bắt buộc nhìn thấy cùng state không?

**Đáp án:** Không. Kiểu tĩnh có thể giống nhau, nhưng mỗi inner gắn với một enclosing object cụ thể và do đó có thể nhìn thấy state khác nhau.

**Câu 6.** Vì sao việc giữ một inner object dài hạn có thể làm outer object tiếp tục tồn tại trong bộ nhớ?

**Đáp án:** Non-static inner object có liên hệ tham chiếu đến enclosing outer object; chừng nào graph tham chiếu còn giữ inner và không có cơ chế cắt liên kết thích hợp, outer có thể vẫn reachable qua inner.

## Kết luận

Non-static member inner class nằm trong phạm vi generic của outer, nên có thể sử dụng `T` từ `Outer<T>` mà không cần khai báo lại. Khi cần dữ liệu độc lập, inner có thể khai báo `U` và được sử dụng với kiểu như `Outer<String>.Inner<Integer>`. Khi tạo inner từ bên ngoài, phải có một enclosing object trong biểu thức `outer.new Inner(...)`. Phân biệt nó với `static nested class`: loại static có type parameter của riêng mình, không mang `T` hoặc reference instance của outer theo cách non-static inner class làm. Hiểu cả **phạm vi kiểu lúc biên dịch** và **quan hệ đối tượng khi chạy** giúp chọn cấu trúc lồng phù hợp, giảm lỗi cú pháp và kiểm soát rõ vòng đời của đối tượng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="type-parameter-scope-006">Lớp lồng static và tham số kiểu độc lập</a>

<details>
<summary>Xem chi tiết</summary>

# Lớp lồng static và tham số kiểu độc lập trong Java Generics

## 1. Mục tiêu

Trong Java, một lớp có thể khai báo một lớp khác ở bên trong thân của mình. Nếu lớp thành viên đó dùng từ khóa **static**, nó được gọi là **static nested class** (lớp thành viên lồng static). Khi lớp bao ngoài là generic, cần hiểu rất rõ ranh giới của các **type variables**.

Ví dụ:

~~~java
class Outer<T> {
    static class Nested<U> {
        U value;
    }
}
~~~

Ở đây:

- **T** được khai báo bởi **Outer<T>**.
- **U** được khai báo bởi **Nested<U>**.
- **Nested** là một member class có từ khóa **static**, vì thế nó **không kế thừa phạm vi sử dụng T trong ngữ cảnh static** của Outer.
- **Nested<U>** là một generic class theo nghĩa có type parameter riêng U, dù nó được đặt bên trong Outer.
- Bên ngoài, kiểu lồng được viết **Outer.Nested<String>**, **Outer.Nested<Integer>**, v.v. Không cần chọn trước **Outer<String>** để đặt kiểu cho Nested.

Các mục tiêu học tập:

1. Phân biệt **static nested class** và **non-static inner class** về quan hệ với instance của Outer và khả năng sử dụng T.
2. Nhận diện đúng phạm vi của **T** và **U**, tránh cho rằng type parameter của lớp bao ngoài tự được truyền vào lớp lồng static.
3. Sử dụng cú pháp khai báo, khởi tạo và tham chiếu đúng đối với **Outer.Nested<U>**.
4. Thiết kế phương thức generic trong lớp lồng static khi cần làm việc với **Outer<T>**.
5. Xử lý nhiều type parameters, generic bounds, các member static bên trong lớp lồng và quan hệ với type erasure.
6. Nhận biết các lỗi phổ biến: tham chiếu T sai scope, dùng cú pháp Outer<String>.Nested<U>, nhầm constructor với outer instance và che tên type variable.

Các ví dụ có nhãn tệp đều viết theo Java 17+; các đoạn lỗi được ghi chú riêng để không nhầm với mã runnable.

## 2. Tại sao static tạo ra một ranh giới kiểu?

Hãy bắt đầu bằng một lớp generic thông thường:

~~~java
class Box<T> {
    private T value;

    Box(T value) {
        this.value = value;
    }

    T get() {
        return value;
    }
}
~~~

T thuộc khai báo **Box<T>**. Các thành viên instance như field value, constructor và get() có thể dùng T bởi chúng liên quan đến một cách sử dụng Box<T> cụ thể, chẳng hạn Box<String>.

Một thành viên static của Box<T> không thuộc riêng Box<String> hay Box<Integer>. Nó thuộc về **Box** xét ở mức class. Vì vậy không thể viết:

~~~java
// Chỉ minh họa lỗi biên dịch:
class Box<T> {
    // static T lastValue;          // Sai: static field không được dùng T.
    // static T echo(T value) {     // Sai: method static dùng T của lớp.
    //     return value;
    // }
}
~~~

Lớp lồng static có nguyên tắc tương tự đối với type parameter của lớp bao ngoài. Nó được khai báo bên trong Outer về mặt tổ chức tên, nhưng **không tự gắn với một instance Outer<T>**.

~~~java
class Outer<T> {
    private T instanceValue;

    static class Nested {
        // T cached; // Sai: không thể dùng T của Outer tại đây.
    }
}
~~~

Đặt Nested bên trong cặp ngoặc của Outer không làm cho mỗi Nested trở thành “lớp con riêng” cho từng Outer<T>. **Outer.Nested** là một member type tĩnh, không phụ thuộc vào đối số kiểu mà một instance Outer đang mang.

Từ khóa static ở đây không nói “mọi field bên trong Nested đều phải static”. Nested vẫn có thể chứa **instance fields**, constructors và instance methods bình thường; chính **khai báo member type Nested** là static theo quan hệ với Outer.

## 3. Lớp lồng static có thể tự khai báo tham số kiểu

Cú pháp đúng:

~~~java
class Outer<T> {
    static class Nested<U> {
        private final U value;

        Nested(U value) {
            this.value = value;
        }

        U get() {
            return value;
        }
    }
}
~~~

**<U>** sau Nested là một khai báo type parameter độc lập; trong Nested<U>, U dùng được làm kiểu field value, tham số constructor và kiểu trả về get(). Trong khi đó T của Outer không dùng trực tiếp được trong phần thân Nested chỉ vì nó được khai báo bên ngoài.

Dùng lớp lồng:

~~~java
// Giả sử đang ở cùng package và Nested không bị giới hạn truy cập:
Outer.Nested<String> name = new Outer.Nested<>("Java");
Outer.Nested<Integer> count = new Outer.Nested<>(21);

String text = name.get();
Integer number = count.get();
~~~

Vì Nested được khai báo static, ta dùng **new Outer.Nested<>(...)**. Không cần tạo một instance Outer trước rồi mới có thể tạo Nested. Diamond **<>** suy luận U từ ngữ cảnh và constructor arguments; nó không phải danh sách type parameters mới.

Nếu thay U bằng T:

~~~java
class Outer<T> {
    static class Nested<T> {
        private T value;
    }
}
~~~

Thì **T của Nested<T> là một type parameter mới**, độc lập với T của Outer<T>. Trong khai báo Nested, tên T mới che tên trùng bên ngoài, nhưng ở một static nested class việc dùng type parameter bên ngoài cũng không hợp lệ. Cú pháp này có thể hợp lệ song **không được khuyến khích** vì người đọc dễ tưởng chúng là cùng một T. Tên **U**, **E**, **K** hoặc **V** thường giúp làm rõ hai phạm vi.

## 4. Ví dụ chạy độc lập: Outer<T> và Nested<U>

Lưu thành **StaticNestedDemo.java**:

~~~java
public class StaticNestedDemo<T> {
    private final T outerValue;

    StaticNestedDemo(T outerValue) {
        this.outerValue = outerValue;
    }

    T getOuterValue() {
        return outerValue;
    }

    static final class Nested<U> {
        private final U item;

        Nested(U item) {
            this.item = item;
        }

        U get() {
            return item;
        }
    }

    public static void main(String[] args) {
        StaticNestedDemo<String> outer =
                new StaticNestedDemo<>("outer-data");

        StaticNestedDemo.Nested<Integer> count =
                new StaticNestedDemo.Nested<>(42);

        StaticNestedDemo.Nested<String> label =
                new StaticNestedDemo.Nested<>("nested-data");

        System.out.println(outer.getOuterValue());
        System.out.println(count.get());
        System.out.println(label.get());
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac -encoding UTF-8 StaticNestedDemo.java
java StaticNestedDemo
~~~

Stdout chính xác:

~~~text
outer-data
42
nested-data
~~~

Quan sát ba cách lựa chọn kiểu:

- **StaticNestedDemo<String>** chọn T của Outer là String.
- **StaticNestedDemo.Nested<Integer>** chọn U của Nested là Integer.
- **StaticNestedDemo.Nested<String>** chọn U của một instance Nested khác là String.

Không có quan hệ bắt buộc giữa **T = String** của outer và **U = Integer** của count. Chúng là các type arguments ở những khai báo độc lập. Mã có thể tạo count và label mà không cần outer; trong chương trình chỉ tạo outer để minh họa sự tồn tại song song.

Nhờ kiểu trả về **U get()**, compiler biết count.get() là Integer, label.get() là String, không đòi cast thủ công.

## 5. Cú pháp tham chiếu: Outer.Nested<U>, không phải Outer<T>.Nested<U>

Với:

~~~java
class Outer<T> {
    static class Nested<U> {}
}
~~~

Cú pháp mô tả kiểu lớp lồng ở nơi sử dụng là:

~~~java
// Minh họa trong một phương thức:
Outer.Nested<String> a = new Outer.Nested<>();
Outer.Nested<Integer> b = new Outer.Nested<>();
~~~

**Outer.Nested<String>** có nghĩa member type Nested<String> được khai báo trong Outer. Tên Outer đóng vai trò định danh lớp chứa thành viên tĩnh; không cần tham số hóa Outer trước.

Một ngộ nhận phổ biến:

~~~java
// Không hợp lệ, chỉ minh họa:
// Outer<String>.Nested<Integer> value;
~~~

Không thể chọn một static member class từ dạng tên Outer<String> đã tham số hóa như với inner class không static. Dấu **<String>** sau Outer ở đây không có vai trò cho Nested. Dạng đúng là **Outer.Nested<Integer>**.

Dấu chấm trong **Outer.Nested** dùng để định danh thành viên lớp, không hàm ý một object Outer có tham chiếu đến Nested. Lớp lồng static có thể được import bằng tên đầy đủ hoặc static import thích hợp theo luật import Java; khi viết tài liệu học tập, dùng dạng đầy đủ Outer.Nested<U> giúp dễ nhìn ra nơi khai báo.

Nếu Nested được khai báo **private**, mã bên ngoài Outer không thể dùng trực tiếp tên kiểu đó dù cú pháp generic đúng. Access modifiers như public, protected, private và package-private vẫn áp dụng bình thường; phạm vi type variable và quyền truy cập là hai quy tắc khác nhau.

## 6. Đối chiếu đủ để phân biệt với non-static inner class

Một **non-static inner class** có enclosing instance, nên được dùng `T` của `Outer<T>`; một **static nested class** không có liên kết ngầm đó. Xét ví dụ tối thiểu:

~~~java
class Outer<T> {
    private final T value;
    Outer(T value) { this.value = value; }

    class Inner<U> {
        U extra;
        Inner(U extra) { this.extra = extra; }
        T outerValue() { return value; }
    }

    static class Nested<U> {
        U extra;
        Nested(U extra) { this.extra = extra; }
    }
}
~~~

~~~java
Outer<String> owner = new Outer<>("parent");
Outer<String>.Inner<Integer> inner = owner.new Inner<>(5);
Outer.Nested<Integer> nested = new Outer.Nested<>(5);
~~~

Các lệnh sử dụng ở trên phải nằm trong method hoặc ngữ cảnh khai báo biến hợp lệ. Kết quả `inner.outerValue()` có kiểu `String`; `nested` không có outer object ngầm định.

| Điểm so sánh | Static nested | Non-static inner |
| --- | --- | --- |
| Kiểu tham chiếu | `Outer.Nested<U>` | `Outer<T>.Inner<U>` |
| Enclosing instance ngầm | Không | Có |
| Trực tiếp sử dụng `T` của outer | Không | Có, khi không bị che khuất |
| Khởi tạo từ ngoài outer | `new Outer.Nested<>(...)` | `owner.new Inner<>(...)` |
| Instance fields riêng của nó | Có | Có |

Phần khác biệt này đủ để xác định ranh giới **`T` của outer**. Các trường hợp tạo inner, `Outer.this`, lifecycle và aliasing thuộc trọng tâm học riêng về non-static inner class; ở đây tiếp tục phân tích **generic API bên trong static nested type**.

## 7. Tại sao static nested class không được dùng T của Outer trong field?

Giả sử ta muốn viết:

~~~java
// Không biên dịch:
class Holder<T> {
    static class Entry {
        // T data;  // Error: non-static type variable T in static context
    }
}
~~~

Nếu Entry có thể dùng T trực tiếp, câu hỏi sẽ là: “T nào?”. **Holder<String>.Entry** và **Holder<Integer>.Entry** không phải hai member types static được tham số hóa theo Holder. T của một Holder<T> cụ thể không nằm trong phạm vi dùng được bên trong khai báo static Entry.

Sửa theo ý định:

**Nếu Entry cần dữ liệu với một kiểu bất kỳ, tự khai báo U:**

~~~java
class Holder<T> {
    static class Entry<U> {
        U data;

        Entry(U data) {
            this.data = data;
        }
    }
}
~~~

**Nếu Entry phải gắn với một Holder<T> cụ thể, chuyển thành inner class:**

~~~java
class Holder<T> {
    class Entry {
        T data;

        Entry(T data) {
            this.data = data;
        }
    }
}
~~~

**Nếu muốn Entry vẫn static nhưng liên kết kiểu với một Holder khác, truyền mối liên hệ đó một cách tường minh:**

~~~java
class Holder<T> {
    private final T value;

    Holder(T value) {
        this.value = value;
    }

    static class Entry<U> {
        private final Holder<U> owner;

        Entry(Holder<U> owner) {
            this.owner = owner;
        }

        U read() {
            return owner.value;
        }
    }
}
~~~

Trong ví dụ cuối, U thuộc Entry<U>. Field **Holder<U> owner** nói rõ owner phải là Holder mang đúng U, và read() trả U. Không cần truy cập T của lớp bao ngoài từ ngữ cảnh static; quan hệ kiểu được thiết lập qua **Holder<U>** như một parameterized type tường minh.

## 8. Static nested class có thể có phương thức generic riêng

Một type parameter của method khác với type parameter của class chứa method.

~~~java
class Outer<T> {
    static class Nested<U> {
        private final U value;

        Nested(U value) {
            this.value = value;
        }

        U get() {
            return value;
        }

        static <X> X identity(X input) {
            return input;
        }
    }
}
~~~

Ở đây:

- **T** của Outer<T> không dùng trực tiếp trong Nested.
- **U** của Nested<U> dùng được trong instance field và instance method get().
- **X** của static generic method identity là type parameter **riêng của method**, không liên quan tới T hoặc U.

Ngay cả Nested<U> là generic class, static method trong nó **không được dùng U của lớp như một kiểu chưa khai báo riêng**:

~~~java
// Không biên dịch:
class Outer<T> {
    static class Nested<U> {
        // static U last;                 // Sai: static field dùng U của Nested.
        // static U echo(U input) {       // Sai: static method dùng U của Nested.
        //     return input;
        // }
    }
}
~~~

Lựa chọn đúng là **static <X> X echo(X input)**. Khi cần trả Nested<X> từ một static factory:

~~~java
class Outer<T> {
    static final class Nested<U> {
        private final U value;

        private Nested(U value) {
            this.value = value;
        }

        static <X> Nested<X> of(X value) {
            return new Nested<>(value);
        }

        U get() {
            return value;
        }
    }
}
~~~

Factory **Nested.of("abc")** có type parameter X do **method of** tự khai báo. Nếu gọi từ ngoài class Outer, dạng đầy đủ là **Outer.Nested.of("abc")**. Phương thức không dùng U từ một instance Nested trước đó; nó tạo một instance Nested<X> mới.

Trong class generic thông thường cũng vậy: **static <X> Box<X> of(X value)** là một generic static factory có type parameter độc lập; không dùng trực tiếp type parameter T của Box<T>.

## 9. Liên kết Outer<T> với Nested<U> qua generic method

Đôi khi cần giữ Nested là static để tránh phụ thuộc ngầm vào outer instance, nhưng vẫn muốn chuyển dữ liệu từ Outer<T> thành Nested<T>. Có thể mô tả quan hệ bằng một **generic method** nhận Outer<X> và trả Nested<X>.

~~~java
class Outer<T> {
    private final T value;

    Outer(T value) {
        this.value = value;
    }

    static final class Nested<U> {
        private final U item;

        private Nested(U item) {
            this.item = item;
        }

        U get() {
            return item;
        }

        static <X> Nested<X> fromOuter(Outer<X> outer) {
            return new Nested<>(outer.value);
        }
    }
}
~~~

Dòng **static <X> Nested<X> fromOuter(Outer<X> outer)** nói rõ:

- **X** là type parameter được khai báo riêng cho static method.
- Tham số outer có kiểu **Outer<X>**, vì vậy field outer.value có kiểu X tại ngữ cảnh đó.
- Constructor nhận X, tạo **Nested<X>**.
- Người gọi không cần cast từ Object sang X; compiler kiểm tra mối liên hệ bằng chữ ký method.

Ví dụ:

~~~java
// Minh họa trong một phương thức:
Outer<String> source = new Outer<>("abc");
Outer.Nested<String> copy = Outer.Nested.fromOuter(source);
String result = copy.get();
~~~

Một static nested class được phép truy cập member private của lớp bao ngoài qua **một tham chiếu outer tường minh** nếu luật truy cập Java cho phép; các lớp lồng nhau thuộc cùng phạm vi nest có thể truy cập private members tương ứng. Điều quan trọng là **outer được truyền vào** chứ không ngầm tồn tại.

### Ví dụ runnable: static factory giữ đúng type parameter

Lưu thành **NestedFactoryDemo.java**:

~~~java
public class NestedFactoryDemo<T> {
    private final T stored;

    NestedFactoryDemo(T stored) {
        this.stored = stored;
    }

    static final class Result<U> {
        private final U value;

        private Result(U value) {
            this.value = value;
        }

        U get() {
            return value;
        }

        static <X> Result<X> from(NestedFactoryDemo<X> source) {
            return new Result<>(source.stored);
        }

        static <X> Result<X> of(X value) {
            return new Result<>(value);
        }
    }

    public static void main(String[] args) {
        NestedFactoryDemo<String> text =
                new NestedFactoryDemo<>("hello");

        NestedFactoryDemo.Result<String> fromOuter =
                NestedFactoryDemo.Result.from(text);

        NestedFactoryDemo.Result<Integer> independent =
                NestedFactoryDemo.Result.of(99);

        System.out.println(fromOuter.get());
        System.out.println(independent.get());
    }
}
~~~

Biên dịch/chạy:

~~~bash
javac -encoding UTF-8 NestedFactoryDemo.java
java NestedFactoryDemo
~~~

Stdout:

~~~text
hello
99
~~~

**from(text)** suy luận X = String nhờ tham số NestedFactoryDemo<X>. Còn **of(99)** suy luận X = Integer qua đối số int được boxing thành Integer. Cả hai factory nằm trong static nested Result<U>; X không phải U của một Result instance nào trước đó.

## 10. Tùy chọn dùng nhiều type parameters riêng

Static nested generic class có thể khai báo nhiều type parameters:

~~~java
class Context<T> {
    static final class Entry<K, V> {
        private final K key;
        private final V value;

        Entry(K key, V value) {
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
}
~~~

**K và V** đều được khai báo riêng trên Entry; **T** của Context không được tự động liên hệ tới K hoặc V.

Trong cùng package:

~~~java
Context.Entry<String, Integer> entry =
        new Context.Entry<>("count", 5);

String name = entry.key();
Integer number = entry.value();
~~~

Một static nested class cũng có thể dùng một type parameter như U để tham số hóa các kiểu generic khác:

~~~java
class Library<T> {
    static class Group<U> {
        private final java.util.List<U> items =
                new java.util.ArrayList<>();

        void add(U item) {
            items.add(item);
        }

        java.util.List<U> snapshot() {
            return java.util.List.copyOf(items);
        }
    }
}
~~~

T trong Library<T> không dùng trực tiếp ở Group<U>. Group<U> khai báo U, sau đó dùng U làm type argument cho List<U>. **List.copyOf** không chấp nhận phần tử null; đây là chính sách của List.copyOf, không phải giới hạn riêng của static nested class.

Không có yêu cầu number of parameters của Outer và Nested phải bằng nhau. Outer<T> có thể chứa Nested<U>, Nested<K,V> hoặc một static nested class không generic tùy trách nhiệm. Thiết kế tốt dùng đúng số type parameters cần diễn tả quan hệ kiểu thật sự.

## 11. Type bounds: giới hạn U độc lập với giới hạn của T

Một lớp bao ngoài có bound không truyền bound ấy ngầm vào static nested class.

~~~java
class NumericOuter<T extends Number> {
    static class Holder<U> {
        private final U item;

        Holder(U item) {
            this.item = item;
        }

        U get() {
            return item;
        }
    }
}
~~~

**T extends Number** chỉ giới hạn type arguments của NumericOuter<T>. Nó **không giới hạn U** của NumericOuter.Holder<U>. Do đó:

~~~java
// Minh họa:
NumericOuter.Holder<String> words =
        new NumericOuter.Holder<>("hello");
~~~

là hợp lệ; String không cần là Number để dùng làm U.

Nếu chính nested class cần thao tác với Number, khai báo bound trực tiếp trên U:

~~~java
class NumericOuter<T extends Number> {
    static class NumericHolder<U extends Number> {
        private final U item;

        NumericHolder(U item) {
            this.item = item;
        }

        double asDouble() {
            return item.doubleValue();
        }
    }
}
~~~

**NumericOuter.NumericHolder<Integer>** hợp lệ; **NumericOuter.NumericHolder<String>** không hợp lệ. U được giới hạn do khai báo **U extends Number** trên Nested, không phải do T extends Number ở Outer.

Không được tham chiếu đến T của Outer trong bound của static nested class:

~~~java
// Không biên dịch:
class Outer<T> {
    // static class Nested<U extends T> {}
}
~~~

Muốn biểu diễn quan hệ giữa U và một kiểu khác, có thể khai báo đầy đủ type parameters trên nested class và đặt bound giữa chúng:

~~~java
class Outer<T> {
    static class Related<X, Y extends X> {
        private final X parentValue;
        private final Y detail;

        Related(X parentValue, Y detail) {
            this.parentValue = parentValue;
            this.detail = detail;
        }
    }
}
~~~

X và Y là type parameters của Related; **Y extends X** là một quan hệ bound hợp lệ do cả X và Y đều nằm trong phạm vi của chính nested class. Nó vẫn không tự liên hệ với T của Outer. Quan hệ với một Outer<X> cụ thể phải được tạo qua tham số, field hoặc method thích hợp.

## 12. Static fields của nested class vẫn dùng chung giữa các `U`

Một `static nested class` có thể chứa cả **instance field kiểu `U`** và **static field kiểu cụ thể**:

~~~java
class Outer<T> {
    static class Counter<U> {
        private static int created;
        private final U value;

        Counter(U value) {
            this.value = value;
            created++;
        }

        U get() { return value; }
        static int createdCount() { return created; }
    }
}
~~~

Nếu tạo một `Outer.Counter<String>` và một `Outer.Counter<Integer>`, mỗi object giữ một `value` riêng nhưng cùng tăng trường `Counter.created`; giá trị trả bởi `Outer.Counter.createdCount()` là `2` nếu chưa có lần tạo nào khác. **Không** có bộ đếm riêng được Java tự sinh ra theo type argument `U`.

Không thể viết `static U lastValue` trong `Counter<U>` vì `U` thuộc generic class, còn field static không mang một `U` của instance. Với nhiều thread, `created++` không nguyên tử: dùng `AtomicInteger` hoặc đồng bộ nếu cần đếm chính xác. Nếu cần thống kê theo từng kiểu, hãy tự thiết kế map có key phân nhóm; type erasure không tạo static state riêng cho mỗi cách tham số hóa.

Ở đây điểm cần học là **hai lớp ranh giới static**: `Nested<U>` không được mượn `T` từ outer, và chính static members của `Nested<U>` cũng không được mượn `U` từ nested instance. Chi tiết class loading, `Class<T>` làm khóa và concurrency thuộc chủ đề riêng về trạng thái static dùng chung.

## 13. Không dùng trực tiếp outer instance members từ Nested

Static nested class vẫn có thể tham chiếu đến **static members** của Outer theo quyền truy cập. Nó không thể đọc **instance member** của Outer nếu chưa có outer reference.

~~~java
class Outer<T> {
    private static final String PREFIX = "item:";
    private final T value;

    Outer(T value) {
        this.value = value;
    }

    static class Nested<U> {
        private final U item;

        Nested(U item) {
            this.item = item;
        }

        String describe() {
            return PREFIX + item; // Hợp lệ: PREFIX là static.
        }

        // T readOuter() { return value; } // Sai: T và instance value không tự có.
    }
}
~~~

Muốn đọc outer value trong Nested, truyền một Outer<X>:

~~~java
class Outer<T> {
    private final T value;

    Outer(T value) {
        this.value = value;
    }

    static class Nested<U> {
        static <X> X read(Outer<X> outer) {
            return outer.value;
        }
    }
}
~~~

Ở đây **X** là type parameter của method read, không phải T của Outer hay U của Nested. Nó liên kết với kiểu của Outer<X> được truyền. Không có outer reference ngầm nào được tạo ra.

Một sự nhầm lẫn cần tránh là cho rằng việc nested class có quyền truy cập private member của Outer đồng nghĩa có thể gọi instance members khi không có instance. **Quyền truy cập (access control)** và **việc có receiver/biến kiểu đúng scope** là hai vấn đề độc lập.

## 14. Lớp lồng trong interface và record

Một số khai báo kiểu lồng trong Java có tính static theo quy tắc ngôn ngữ dù không viết từ khóa static.

### Class được khai báo bên trong interface

~~~java
interface Source<T> {
    T read();

    class Metadata<U> {
        private final U information;

        public Metadata(U information) {
            this.information = information;
        }

        public U information() {
            return information;
        }
    }
}
~~~

Member class Metadata bên trong interface được xem là **public static** (theo quy tắc implicit của interface member types). Nó không thể tự dùng type parameter T của Source<T>; vì thế khai báo U riêng.

Một cách dùng:

~~~java
Source.Metadata<String> metadata =
        new Source.Metadata<>("details");
~~~

Không viết **Source<String>.Metadata<String>** để chọn kiểu cho T của Source, vì Metadata không gắn với một instance Source<String>.

### Member record lồng trong generic class

Từ Java 16, record là một cấu trúc dữ liệu có component và các phương thức được sinh. Member record có tính static nên tự khai báo type parameter nếu cần:

~~~java
class Container<T> {
    record Entry<U>(U value) {}
}
~~~

Kiểu hợp lệ ở nơi sử dụng là **Container.Entry<Integer>**. T của Container<T> không dùng trực tiếp trong component của Entry; U thuộc khai báo record Entry<U>.

Điểm chung với static nested class là giới hạn phạm vi type variable của outer. Nếu record cần gói một giá trị kiểu T của một Container<T> cụ thể, có thể tạo generic factory nhận Container<X> và trả Entry<X> hoặc chuyển dữ liệu vào constructor Entry<X> như một tham số.

## 15. Type erasure và identity của static nested generic class

Generics trong Java chủ yếu là cấu trúc kiểm tra kiểu khi biên dịch. Việc tạo một object:

~~~java
Outer.Nested<String> words = new Outer.Nested<>("A");
Outer.Nested<Integer> counts = new Outer.Nested<>(1);
~~~

không tạo hai runtime classes khác nhau cho String và Integer. Thông thường cả hai object đều có cùng runtime Class là Outer.Nested, với thông tin type arguments đã được kiểm tra ở source code.

Với static nested class, tên bytecode thường có dạng **Outer$Nested.class**. Đây là tên lớp được javac tạo ra khi biên dịch một member nested class; dấu **$** ở file class không phải dấu phân cách ta dùng trong cú pháp Java source. Trong Java source, tham chiếu là **Outer.Nested<U>**.

Nếu dùng reflection, runtime có thể đọc metadata generic của **khai báo** nested class (ví dụ tên type parameter U). Nhưng không thể kết luận rằng getClass() trên một object bất kỳ sẽ tiết lộ đầy đủ đối số U của biến đã trỏ tới object đó. Muốn kiểm tra String hay Integer của dữ liệu runtime, phải có thông tin/giá trị kiểm tra phù hợp, ví dụ Class<U> được truyền riêng hoặc một cơ chế type token chuyên dụng.

Không dùng **new U()**, **U.class** hoặc **new U[10]** trong Nested<U> như thể U là một runtime class cụ thể. Các giới hạn cơ bản của type erasure áp dụng cho U giống các type variables khác.

## 16. Các lỗi phổ biến và cách sửa

### Lỗi 1: Dùng T của Outer trong static nested class

~~~java
// Cố ý sai:
class Outer<T> {
    static class Nested {
        // T item; // Error: cannot reference T from static context.
    }
}
~~~

**Sửa:** Nested tự khai báo **<U>**, hoặc bỏ static nếu thực sự cần inner class gắn với Outer<T>. Nếu cần truyền dữ liệu một lần, đưa nó vào constructor hoặc generic factory.

### Lỗi 2: Dùng Outer<String>.Nested<Integer>

~~~java
class Outer<T> {
    static class Nested<U> {}
}

// Cố ý sai:
// Outer<String>.Nested<Integer> entry;
~~~

**Sửa:** **Outer.Nested<Integer> entry;**. Lớp lồng static không có type argument T của một outer instance.

### Lỗi 3: Tạo static nested qua outer.new

~~~java
class Outer<T> {
    static class Nested<U> {}
}

// Không hợp lệ:
// Outer<String> owner = new Outer<>();
// var n = owner.new Nested<String>();
~~~

**Sửa:** **new Outer.Nested<String>()** hoặc **new Outer.Nested<>()** khi compiler suy luận được U. Cú pháp **owner.new Inner<>(...)** chỉ phù hợp cho inner class không static.

### Lỗi 4: Dùng U của Nested trong static method

~~~java
class Outer<T> {
    static class Nested<U> {
        // static U copy(U value) { return value; }
    }
}
~~~

**Sửa:** **static <X> X copy(X value)** hoặc dùng một instance method được phép sử dụng U.

### Lỗi 5: Để tên T giống nhau rồi tưởng hai type variables liên kết

~~~java
class Outer<T> {
    static class Nested<T> {
        T item;
    }
}
~~~

T bên trong Nested<T> là **một khai báo mới**. Dù dùng cùng ký hiệu T, Nested<T> không liên hệ kiểu với Outer<T>. **Sửa:** dùng **Nested<U>** cho dễ hiểu hoặc thiết kế field **Outer<U> owner** nếu thật sự cần liên hệ.

### Lỗi 6: Cận trên của Outer được hiểu nhầm là cận của Nested

~~~java
class Outer<T extends Number> {
    static class Nested<U> {}
}
~~~

**Outer.Nested<String>** vẫn có thể hợp lệ. Nếu U phải là Number, tự viết **static class Nested<U extends Number>**.

### Lỗi 7: Tham chiếu field instance của Outer không có object

~~~java
class Outer<T> {
    private T value;

    static class Nested<U> {
        // Object read() { return value; } // Sai, thiếu outer instance.
    }
}
~~~

**Sửa:** nhận một **Outer<X> outer** làm tham số vào generic method hoặc lưu tham chiếu Outer<U> thích hợp trong nested object.

### Lỗi 8: Tin static nested class không có instance state

~~~java
class Outer<T> {
    static class Nested<U> {
        private U value; // Hoàn toàn hợp lệ.
    }
}
~~~

Từ khóa static ở cấp member class chỉ xác định quan hệ với lớp bao ngoài. Nested vẫn là class có object, constructor, instance fields và instance methods bình thường.

### Lỗi 9: Tin static field phân chia theo type argument U

~~~java
class Outer<T> {
    static class Nested<U> {
        static int count;
    }
}
~~~

Cả Nested<String> và Nested<Integer> dùng chung static field count. Nếu cần dữ liệu theo kiểu runtime, phải thiết kế cơ chế định danh và map riêng; không dựa vào U sau erasure.

### Lỗi 10: Dùng Object và ép kiểu để vượt ranh giới

~~~java
// Thiết kế không an toàn kiểu, minh họa:
class Outer<T> {
    static class Nested {
        Object data;
    }
}
~~~

Khi caller nhận Object rồi tự cast, an toàn kiểu giảm. Nếu nested chứa dữ liệu có kiểu cụ thể, ưu tiên **Nested<U>**, **U get()**, generic factory, hay **Outer<U> owner** để compiler kiểm tra mối liên hệ.

### Lỗi 11: Xem static nested như subclass của Outer

**Outer.Nested<U>** không tự extends Outer<T>; nó chỉ được khai báo bên trong Outer. Quan hệ subtyping chỉ xuất hiện khi Nested có **extends SomeClass** hoặc **implements SomeInterface** riêng. Quyền truy cập giữa các lớp lồng nhau không tương đương kế thừa.

### Lỗi 12: Tạo inner class nhưng quên type arguments của outer

Với inner class dùng T của Outer, tên kiểu ngoài là **Outer<String>.Inner<Integer>**, tạo bằng **outer.new Inner<>(...)**. Nếu dùng raw Outer hoặc bỏ type information tùy tiện, ta có thể gặp cảnh báo hay mất độ chính xác của kiểu. Khi cần outer reference, hãy khai báo outer variable theo parameterized type đầy đủ.

## 17. Chọn static nested class hay inner class

Không có lựa chọn nào luôn tốt hơn. Xét mối quan hệ thực:

- **Chọn static nested class** khi loại dữ liệu/helper về mặt tổ chức thuộc Outer nhưng mỗi instance không cần một outer instance ngầm định. Tham số kiểu của nó nên được khai báo riêng, ví dụ **Outer.Entry<K,V>**, **Outer.Result<U>**.
- **Chọn inner class không static** khi mỗi inner instance cần truy cập trạng thái instance của một Outer<T> cụ thể và quan hệ ấy là bản chất của mô hình. Khi đó T của outer có thể dùng trực tiếp và tên kiểu ở nơi sử dụng là **Outer<T>.Inner<U>**.
- **Chọn static nested class + explicit Outer<U> reference** nếu muốn quan hệ được thể hiện rõ ở field/constructor, kiểm soát vòng đời và tránh một enclosing-instance reference tự động.
- **Chọn top-level class** nếu kiểu đó không thực sự có quan hệ tổ chức hoặc API với Outer; việc lồng class không phải điều kiện tiên quyết của generics.
- **Chọn generic method trong nested class** khi kiểu liên hệ chỉ cần cho một thao tác, chẳng hạn **static <X> Nested<X> from(Outer<X> source)**; không cần buộc mọi Nested instance mang cùng một outer object.

Khi thiết kế API, hãy xem tên kiểu xuất hiện tại nơi sử dụng có dễ hiểu không. **Outer.Nested<U>** giúp nhóm các kiểu cùng chức năng; nhưng nếu nesting quá sâu, generic signatures có thể khó đọc hơn so với một top-level type có tên rõ ràng.

## 18. Bài thực hành tự kiểm tra

**Câu 1.** Trong **class Outer<T> { static class Nested<U> {} }**, T và U có cùng scope không?

**Đáp án:** Không. T thuộc Outer<T>, U thuộc Nested<U>. Static nested class không tự dùng được T của outer trong phần thân; U dùng trong các instance members của Nested.

**Câu 2.** Có thể khai báo biến **Outer<String>.Nested<Integer> v** không?

**Đáp án:** Không. Với static nested class, viết **Outer.Nested<Integer> v**. Type argument String của Outer<String> không có vai trò ở static member type này.

**Câu 3.** Có cần instance Outer trước khi gọi **new Outer.Nested<String>()** không?

**Đáp án:** Không. Static nested class không có enclosing instance ngầm định, nên có thể tạo qua tên kiểu Outer.Nested.

**Câu 4.** Tại sao **static class Nested<T>** trong Outer<T> vẫn dùng được ký hiệu T?

**Đáp án:** Vì Nested tự khai báo **một type parameter T mới**. Nó độc lập với T được khai báo bởi Outer, dù cùng tên chữ.

**Câu 5.** Muốn Nested dùng được T của một Outer<String> cụ thể và có outer reference ngầm định, chọn loại class nào?

**Đáp án:** **Non-static inner class**, ví dụ **class Inner<U>** trong Outer<T>. Sử dụng kiểu **Outer<String>.Inner<Integer>** và khởi tạo với **outer.new Inner<>(...)**.

**Câu 6.** Nếu static nested class cần đọc giá trị từ một Outer<T> nhưng vẫn không muốn gắn ngầm với outer instance thì làm gì?

**Đáp án:** Truyền một **Outer<X>** vào constructor hoặc generic method, sử dụng X làm type parameter cục bộ của nested class/method và công bố đúng quan hệ kiểu.

**Câu 7.** Trong **static class Nested<U>**, có thể viết **static U get(U input)** không?

**Đáp án:** Không, vì U thuộc generic class và không khả dụng từ static context. Viết **static <X> X get(X input)** nếu method cần generic độc lập.

**Câu 8.** Outer<T extends Number> có buộc Nested<U> chỉ nhận U extends Number không?

**Đáp án:** Không. U độc lập. Muốn giới hạn, viết rõ **static class Nested<U extends Number>**.

**Câu 9.** Field static **count** của Nested<U> có một bản cho Nested<String> và một bản cho Nested<Integer> không?

**Đáp án:** Không. Các cách sử dụng type arguments khác nhau chia sẻ static state của cùng nested runtime class.

**Câu 10.** Tại sao **Outer.Nested<String>** và **Outer.Nested<Integer>** có thể dùng cùng một runtime class?

**Đáp án:** Java generics sử dụng type erasure; compiler kiểm tra type arguments tại source code nhưng không tạo class runtime khác nhau cho từng U.

**Câu 11.** Một nested class nằm trong interface generic có được ngầm hiểu là static không?

**Đáp án:** Có. Member class trong interface có tính public static, nên không tự có type variable của interface bao ngoài; cần khai báo type parameter riêng hoặc truyền kiểu vào một method/constructor.

**Câu 12.** Member record khai báo trong Outer<T> có dùng trực tiếp T làm record component type không?

**Đáp án:** Không. Member record có tính static; nếu muốn kiểu thành phần linh hoạt, khai báo **record Entry<U>(U value)** rồi dùng Outer.Entry<U>.

## 19. Checklist khi đọc hoặc viết mã

1. **Xác định từ khóa static.** Member type là **static class Nested** hay **class Inner**?
2. **Ghi rõ người sở hữu mỗi type variable.** T thuộc Outer, U thuộc Nested, X thuộc generic method.
3. **Kiểm tra tên kiểu dùng bên ngoài.** Static: **Outer.Nested<U>**. Inner: **Outer<T>.Inner<U>**.
4. **Kiểm tra cách khởi tạo.** Static: **new Outer.Nested<>(...)**. Inner: **outer.new Inner<>(...)**.
5. **Kiểm tra nguồn dữ liệu outer.** Nếu Nested cần instance members của Outer, có outer reference tường minh không?
6. **Kiểm tra static members trong Nested<U>.** Chúng không được dùng U của Nested như một type variable thuộc static context.
7. **Kiểm tra bounds.** Ràng buộc của outer T không tự áp dụng cho nested U.
8. **Kiểm tra thiết kế sau erasure.** Không kỳ vọng static state hay runtime class được tách theo mỗi type argument.
9. **Kiểm tra access modifiers.** Một static nested class private không thể được sử dụng trực tiếp từ bên ngoài lớp khai báo dù cú pháp generic chính xác.
10. **Ưu tiên kiểu rõ ràng.** Nếu cần liên hệ với Outer<T>, biểu diễn nó trong method signature hoặc một field kiểu Outer<U>, tránh raw type và cast không kiểm tra.

## 20. Kết luận

**Static nested class có thể generic nhưng type parameters của nó độc lập với generic outer class.** Với **class Outer<T> { static class Nested<U> {} }**, T thuộc Outer còn U thuộc Nested. Cú pháp sử dụng là **Outer.Nested<U>** và có thể khởi tạo bằng **new Outer.Nested<>(...)** mà không cần một Outer instance.

Khi thật sự cần ràng buộc cùng một kiểu với một Outer<T> cụ thể, hãy chọn **inner class không static** hoặc đưa quan hệ vào **Outer<X>** và generic method/constructor một cách tường minh. Hiểu đúng ranh giới static này giúp tránh lỗi scope, giữ an toàn kiểu và thiết kế các nested APIs rõ ràng trên Java.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="type-parameter-scope-007">Thành viên static dùng chung giữa các kiểu tham số hóa</a>

<details>
<summary>Xem chi tiết</summary>

# Thành viên `static` dùng chung giữa các kiểu tham số hóa trong Java Generics

Khi một lớp Java khai báo `class Box<T>`, người dùng có thể tạo `Box<String>`, `Box<Integer>` hoặc `Box<Customer>`. Những cách sử dụng này có **hợp đồng kiểu khác nhau ở thời điểm biên dịch**, nhưng chúng không tạo ra các bản sao độc lập của lớp `Box` tại JVM. Vì vậy, **trường `static` khai báo trên `Box` là trạng thái chung** cho các đối tượng của cùng một lớp runtime, bất kể chúng được tham số hóa bằng đối số kiểu nào.

Điều này rất quan trọng nếu đặt bộ đếm đối tượng, cache, bộ đăng ký hoặc cấu hình dùng chung vào lớp generic. Nếu hiểu nhầm rằng mỗi `Box<String>` và `Box<Integer>` sở hữu một bản sao riêng của trường `static`, chương trình có thể thống kê sai dữ liệu, trộn trạng thái giữa các kiểu hoặc gặp lỗi đồng thời. Bài học làm rõ quy tắc của trường và phương thức `static`, cơ chế *type erasure*, khởi tạo lớp, phạm vi của `T`, và cách thiết kế thành viên `static` an toàn.

## 1. Trường instance và trường `static` khác nhau thế nào?

Ví dụ:

```java
class Box<T> {
    private final T value;
    private static int created;

    Box(T value) {
        this.value = value;
        created++;
    }

    T get() {
        return value;
    }

    static int createdCount() {
        return created;
    }
}
```

Đây là **khai báo lớp Java minh họa**, chưa có `main`. Hai trường có thời gian tồn tại và chủ sở hữu khác nhau:

- `value` là **trường instance**. Mỗi đối tượng `Box` có một trường `value` riêng. Với tham chiếu `Box<String>`, compiler kiểm tra `value` theo đối số kiểu `String` thông qua constructor/getter; với `Box<Integer>`, kiểu tương ứng là `Integer`.
- `created` là **trường `static` của lớp `Box`**. Các đối tượng `Box<String>` và `Box<Integer>` cùng truy cập một trường `created` thuộc định nghĩa lớp được JVM nạp. Cứ gọi constructor là bộ đếm tăng, dù đối tượng mới chứa kiểu gì.
- `createdCount()` là **phương thức `static`**. Nó đọc bộ đếm chung và được gọi thông qua tên lớp: `Box.createdCount()`.

Cần phân biệt **tham số kiểu `T`** với **đối số kiểu tại nơi sử dụng**. `T` mô tả quan hệ kiểu đối với những thành viên instance phù hợp; nó không tạo ra một không gian bộ nhớ `static` riêng cho mỗi `T`.

| Cách khai báo | Thuộc về | Có tách riêng cho mỗi `Box<String>` và `Box<Integer>`? |
|---|---|---|
| `private T value;` | Từng đối tượng `Box` | Mỗi đối tượng có trường riêng; compiler kiểm tra theo kiểu được tham số hóa. |
| `private static int created;` | Lớp runtime `Box` | **Không**; cùng trường chung cho mọi cách tham số hóa của cùng lớp được nạp. |
| `T get()` | Phương thức instance | Gọi trên từng đối tượng và dùng hợp đồng kiểu `T` tương ứng. |
| `static int createdCount()` | Lớp runtime `Box` | **Không**; một định nghĩa phương thức chung. |
| `static <U> Box<U> of(U value)` | Phương thức của lớp, có tham số kiểu riêng `U` | Một phương thức chung; `U` được suy luận riêng cho mỗi lời gọi. |

## 2. Ví dụ chạy được: một bộ đếm chung cho nhiều đối số kiểu

Lưu tệp **`SharedStaticDemo.java`**:

```java
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

public class SharedStaticDemo {
    static final class Box<T> {
        private static final AtomicInteger CREATED = new AtomicInteger();
        private final T value;

        Box(T value) {
            this.value = Objects.requireNonNull(value, "value");
            CREATED.incrementAndGet();
        }

        T get() {
            return value;
        }

        static int createdCount() {
            return CREATED.get();
        }
    }

    public static void main(String[] args) {
        Box<String> first = new Box<>("Java");
        Box<Integer> second = new Box<>(17);
        Box<Double> third = new Box<>(2.5);

        System.out.println(first.get());
        System.out.println(second.get() + 1);
        System.out.println(third.get());
        System.out.println(Box.createdCount());
        System.out.println(first.getClass() == second.getClass());
        System.out.println(second.getClass() == third.getClass());
    }
}
```

Chạy bằng JDK 17 trở lên:

```bash
javac -encoding UTF-8 SharedStaticDemo.java
java SharedStaticDemo
```

Kết quả chính xác (`stdout`):

```text
Java
18
2.5
3
true
true
```

**Vì sao in ra `3`?** Constructor `Box(T value)` được gọi ba lần. Cả ba lần đều tăng cùng một `AtomicInteger CREATED`. Không có bộ đếm riêng cho `Box<String>`, `Box<Integer>` hoặc `Box<Double>`.

**Vì sao in ra `true` hai lần?** Khi gọi `getClass()` trên ba đối tượng, JVM trả về cùng một đối tượng `Class` đại diện cho lớp lồng runtime `SharedStaticDemo.Box`. Khác biệt về đối số kiểu là kiểm tra kiểu ở mã nguồn; thông tin này không khiến JVM tạo ba lớp runtime chuyên biệt.

Ví dụ dùng `AtomicInteger` để thao tác tăng bộ đếm an toàn khi các constructor được gọi đồng thời. Với chương trình chỉ chạy tuần tự, `static int created; created++;` cũng cho cùng kết quả, nhưng `created++` không phải thao tác nguyên tử nếu nhiều luồng thực hiện cùng lúc. `AtomicInteger` **chỉ bảo vệ riêng thao tác bộ đếm**, không tự biến mọi thành viên khác của lớp thành thread-safe.

## 3. Vì sao các cách tham số hóa lại chia sẻ thành viên `static`?

Java triển khai phần lớn generics bằng cơ chế **type erasure**. Sau khi biên dịch, một lớp generic thông thường không được nhân bản thành các lớp runtime độc lập cho từng đối số kiểu:

```java
// Đoạn minh họa bên trong một phương thức:
Box<String> strings = new Box<>("hello");
Box<Integer> numbers = new Box<>(42);
```

Cả hai phép tạo đối tượng đều khởi tạo đối tượng của cùng lớp runtime `Box`. Các chữ ký generic được compiler kiểm tra và thông tin generic trong tệp `.class` có thể còn ở metadata để các công cụ đọc, nhưng JVM không duy trì hai bản sao trạng thái `static` cho riêng `String` và `Integer`.

Sơ đồ quan hệ:

```text
                   class Box<T>
                        |
                 JVM: class Box
                        |
             +----------+----------+
             |                     |
       static CREATED          static methods
             |
        một trạng thái chung
             |
      +------+------+ 
      |             |
Box<String>     Box<Integer>
instance A       instance B
value riêng      value riêng
```

Sơ đồ trên diễn đạt **một lớp runtime và các đối tượng riêng**, không phải các tiến trình hay bộ nhớ JVM tách biệt. Lớp `Box` vẫn được biên dịch với metadata generic nhằm hỗ trợ công cụ và kiểm tra kiểu, nhưng không có “bảng `static` theo từng đối số kiểu”.

Một hệ quả là cú pháp `Box<String>.class` không hợp lệ trong Java; class literal dùng tên kiểu có thể biểu diễn ở runtime, chẳng hạn `Box.class`. Tương tự, kiểm tra `instanceof Box<String>` không được phép vì đối số `String` không thể kiểm tra trực tiếp bằng `instanceof` sau erasure; `instanceof Box<?>` có thể dùng nếu chỉ cần xác nhận đối tượng thuộc lớp `Box`.

## 4. Khởi tạo thành viên `static`: một lần cho mỗi lớp được định nghĩa

Xét:

```java
class Sequence<T> {
    private static int nextId = 1;

    static int newId() {
        return nextId++;
    }
}
```

Đây là **khai báo lớp minh họa**. Trình tự hoạt động trong một môi trường thông thường:

1. JVM nạp/định nghĩa lớp `Sequence` và chuẩn bị trạng thái `static` của nó theo quy tắc JVM.
2. Khi có hành động yêu cầu khởi tạo lớp, các giá trị khởi tạo trường `static` và khối khởi tạo `static` được thực hiện theo thứ tự khai báo.
3. `Sequence.newId()` dùng chung `nextId` cho tất cả nơi gọi, bất kể ứng dụng dự định kết quả dùng trong `Sequence<String>` hay `Sequence<Integer>`.
4. Việc tạo thêm đối tượng `Sequence<U>` với một đối số kiểu khác **không** chạy lại phần khởi tạo `static` như một lần “khởi tạo cho `U`” mới.

Không nên hiểu thành “toàn bộ JVM luôn chỉ có đúng một bản sao” trong mọi tình huống. Một lớp có thể được định nghĩa riêng bởi các **class loader** khác nhau, và mỗi định nghĩa lớp như vậy có trạng thái `static` của nó. Trong thực hành, quy tắc nên nhớ là **cùng một lớp được định nghĩa bởi cùng class loader thì chia sẻ thành viên `static`**, không phân biệt đối số kiểu Java Generics.

### Kiểm chứng việc khởi tạo `static`

Lưu tệp **`InitializationDemo.java`**:

```java
public class InitializationDemo {
    static final class Token<T> {
        private static int created;

        static {
            System.out.println("Token initialized");
        }

        Token() {
            created++;
        }

        static int count() {
            return created;
        }
    }

    public static void main(String[] args) {
        Token<String> a = new Token<>();
        Token<Integer> b = new Token<>();
        System.out.println(Token.count());
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 InitializationDemo.java
java InitializationDemo
```

`stdout`:

```text
Token initialized
2
```

Khối `static { ... }` được thực thi **một lần** khi lớp `Token` được khởi tạo, trước khi constructor đầu tiên chạy. Hai cách khởi tạo `Token<String>` và `Token<Integer>` không dẫn đến hai lần in `Token initialized`.

Lưu ý: **nạp lớp**, **liên kết lớp** và **khởi tạo lớp** là những bước khác nhau trong JVM. Không phải mọi thao tác nhìn thấy tên lớp ở mã nguồn đều bắt buộc chạy khối `static` ngay; ví dụ việc tham chiếu một hằng compile-time có thể không kích hoạt khởi tạo lớp khai báo hằng. Với ví dụ tạo đối tượng trên, việc khởi tạo lớp diễn ra trước khi constructor đầu tiên chạy.

## 5. Tại sao `static T shared` là lỗi biên dịch?

Nếu `T` là tham số kiểu của lớp:

```java
class InvalidBox<T> {
    // private static T lastValue;       // Sai
    // static T getLastValue() { ... }   // Sai
    // static void print(T value) { ... }// Sai nếu T là của lớp
}
```

Các dòng sai được **chú thích có chủ đích**. Java không cho thành viên `static` dùng trực tiếp biến kiểu không static `T` được khai báo trên lớp `InvalidBox<T>`.

Giải thích bằng một giả định mâu thuẫn: nếu `static T lastValue` được cho phép, sẽ có một trường dùng chung cho cả `InvalidBox<String>` và `InvalidBox<Integer>`. Một bên kỳ vọng trường là `String`, bên kia kỳ vọng `Integer`. JVM không thể bảo đảm cả hai hợp đồng kiểu cùng đúng đối với một trường chung trong mọi thao tác.

Cần phân biệt lỗi này với việc không thể tạo đối tượng bằng `new T()`. Cả hai liên quan đến generic nhưng thuộc hai quy tắc khác nhau: `static T field` dùng sai tham số kiểu trong ngữ cảnh static; `new T()` không hợp lệ do tham số kiểu không có đủ thông tin constructor runtime sau erasure, kể cả trong phương thức instance.

### Cách đúng: chọn kiểu cụ thể hoặc tham số kiểu của riêng phương thức

Một trường `static` có thể có **kiểu xác định độc lập với `T`**:

```java
class SafeBox<T> {
    private static long totalCreated;
    private final T value;

    SafeBox(T value) {
        this.value = value;
        totalCreated++;
    }

    static long totalCreated() {
        return totalCreated;
    }

    T get() {
        return value;
    }
}
```

Trường `totalCreated` thuộc một lớp runtime duy nhất; bản thân nó không cố mô tả giá trị `T` của từng đối tượng.

Nếu cần hàm `static` làm việc với nhiều loại giá trị, **khai báo tham số kiểu riêng của phương thức**:

```java
static <U> U identity(U input) {
    return input;
}
```

Đoạn này phải đặt trong một lớp. `<U>` không thuộc về instance nào, nên `identity("Java")` và `identity(42)` có thể cùng sử dụng định nghĩa phương thức `static` mà compiler vẫn kiểm tra quan hệ kiểu giữa tham số và kết quả ở từng lần gọi.

Chỉ đổi tên `T` thành `U` không phải bản chất của cách sửa; điều quan trọng là đặt **danh sách khai báo tham số kiểu riêng `<U>`** ở chữ ký phương thức.

## 6. Phương thức generic `static` không có trạng thái riêng theo đối số kiểu

Xét hai lời gọi:

```java
String text = Toolbox.<String>identity("Java");
Integer count = Toolbox.<Integer>identity(21);
```

Giả sử `Toolbox` khai báo `static <T> T identity(T input)`. Ở lời gọi đầu, compiler xác định `T = String`; ở lời gọi sau, `T = Integer`. Tuy nhiên, **phương thức `identity` không tự tạo hai vùng biến `static`** tương ứng. Mỗi lần gọi sử dụng các biến cục bộ và tham số của lần gọi đó; các thành viên `static` mà nó truy cập vẫn thuộc lớp khai báo.

### Ví dụ kết hợp factory generic và trạng thái static chung

Lưu thành **`FactoryCounterDemo.java`**:

```java
import java.util.Objects;

public class FactoryCounterDemo {
    static final class Box<T> {
        private static int count;
        private final T value;

        private Box(T value) {
            this.value = Objects.requireNonNull(value, "value");
            count++;
        }

        static <U> Box<U> of(U value) {
            return new Box<>(value);
        }

        T get() {
            return value;
        }

        static int count() {
            return count;
        }
    }

    public static void main(String[] args) {
        Box<String> a = Box.of("spring");
        Box<Integer> b = Box.of(99);
        Box<String> c = Box.of("java");

        System.out.println(a.get().toUpperCase());
        System.out.println(b.get());
        System.out.println(c.get());
        System.out.println(Box.count());
    }
}
```

Biên dịch và chạy:

```bash
javac -encoding UTF-8 FactoryCounterDemo.java
java FactoryCounterDemo
```

`stdout`:

```text
SPRING
99
java
3
```

Ở đây:

- `Box<T>` là lớp generic, còn `static <U> Box<U> of(U value)` là phương thức generic riêng.
- Compiler suy luận `U` cho từng lần gọi `Box.of(...)`.
- Constructor tăng `count` chung sau khi kiểm tra giá trị khác `null`.
- `Box<String>` và `Box<Integer>` vẫn chia sẻ cùng bộ đếm. Phương thức `of` không tạo bộ đếm riêng cho từng `U`.
- Ví dụ dùng `int count` để đơn giản hóa; nó chỉ phù hợp nếu không có yêu cầu cập nhật đồng thời. Khi đa luồng, thay bằng `AtomicInteger` hoặc cơ chế đồng bộ thích hợp.

## 7. Thành viên `static final` cũng dùng chung

Trường `static final` có thể biểu diễn cấu hình, hằng số hoặc tham chiếu chung:

```java
class CacheBox<T> {
    static final String DEFAULT_NAME = "cache";
    static final int MAX_ENTRIES = 100;
}
```

Những trường đó **không được nhân bản** thành một `DEFAULT_NAME` của `CacheBox<String>` và một `DEFAULT_NAME` khác của `CacheBox<Integer>`. Chúng thuộc lớp `CacheBox`.

Cần phân biệt hai nghĩa của `final`:

- Với `static final int MAX_ENTRIES = 100`, giá trị primitive cố định sau khi gán. Đây còn có thể là hằng tại thời điểm biên dịch.
- Với `static final List<String> ITEMS = new ArrayList<>()`, từ khóa `final` chỉ ngăn gán lại tham chiếu `ITEMS`; **nội dung danh sách vẫn có thể bị thay đổi** nếu đối tượng đó cho phép sửa. Danh sách là trạng thái chung giữa tất cả cách tham số hóa của lớp.

Ví dụ:

```java
import java.util.ArrayList;
import java.util.List;

class SharedMessages<T> {
    static final List<String> MESSAGES = new ArrayList<>();
}
```

Đây là **khai báo lớp minh họa**. Nếu một đối tượng được dùng qua `SharedMessages<String>` thêm phần tử vào `MESSAGES` và một đoạn mã khác dùng `SharedMessages<Integer>` đọc danh sách đó, chúng đang nhìn vào cùng một collection.

Để tránh mã bên ngoài vô tình sửa trạng thái chung, thường nên giữ trường mutable `static` ở quyền `private` và chỉ cung cấp API được kiểm soát. Đừng hiểu `final` là bảo đảm bất biến sâu.

## 8. Khi cần số liệu riêng theo từng kiểu: tự thiết kế cấu trúc phân nhóm

Có những bài toán thật sự cần thống kê riêng số hộp chứa `String`, `Integer` hoặc một loại đối tượng khác. **Java không tự chia trường `static` theo đối số generic**, vì vậy ứng dụng phải chọn khóa phân nhóm rõ ràng.

Một lựa chọn là truyền **`Class<T>`** làm type token ở ranh giới API, rồi dùng một map `static` được khóa bởi `Class<?>`. Trường map vẫn là **một map chung**; các nhóm được tách bằng khóa trong dữ liệu, không phải bằng các bản sao của lớp generic.

### Ví dụ thống kê có khóa theo lớp runtime

Tạo tệp **`PerClassStatsDemo.java`**:

```java
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class PerClassStatsDemo {
    static final class TypedBox<T> {
        private static final Map<Class<?>, AtomicInteger> COUNTS =
                new ConcurrentHashMap<>();

        private final Class<T> type;
        private final T value;

        TypedBox(Class<T> type, T value) {
            this.type = Objects.requireNonNull(type, "type");
            this.value = type.cast(value);
            COUNTS.computeIfAbsent(
                    type, ignored -> new AtomicInteger()).incrementAndGet();
        }

        T get() {
            return value;
        }

        Class<T> type() {
            return type;
        }

        static int countFor(Class<?> type) {
            AtomicInteger count = COUNTS.get(type);
            return count == null ? 0 : count.get();
        }
    }

    public static void main(String[] args) {
        TypedBox<String> a = new TypedBox<>(String.class, "A");
        TypedBox<Integer> b = new TypedBox<>(Integer.class, 1);
        TypedBox<String> c = new TypedBox<>(String.class, "B");

        System.out.println(a.get() + c.get());
        System.out.println(b.type().getSimpleName());
        System.out.println(TypedBox.countFor(String.class));
        System.out.println(TypedBox.countFor(Integer.class));
        System.out.println(TypedBox.countFor(Double.class));
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 PerClassStatsDemo.java
java PerClassStatsDemo
```

`stdout`:

```text
AB
Integer
2
1
0
```

Điểm cần phân biệt:

1. `COUNTS` vẫn chỉ là **một trường `static` chung** của `TypedBox`.
2. Mỗi khóa `String.class` hoặc `Integer.class` có một bộ đếm riêng **do chương trình tạo ra**.
3. `type.cast(value)` kiểm tra đối tượng theo `Class<T>` được truyền vào. Nếu giá trị không tương thích, phép cast ném `ClassCastException`.
4. `computeIfAbsent` trên `ConcurrentHashMap` cùng `AtomicInteger` giúp cập nhật bộ đếm an toàn cho trường hợp nhiều luồng tạo hộp cùng lúc; phần dữ liệu `value` của từng hộp được gán một lần ở constructor.
5. Trường hợp `null` cần quy định riêng: `Class.cast(null)` trả `null` nên không tự từ chối; nếu cần giá trị khác `null`, thêm `Objects.requireNonNull(value)`.

**Giới hạn của `Class<?>`:** type token như `List.class` chỉ phân biệt lớp runtime `List`, không phân biệt đầy đủ `List<String>` và `List<Integer>`. Nếu cần phân nhóm theo những kiểu tham số hóa lồng nhau, phải dùng một mô hình token giàu thông tin hơn và nêu rõ cách biểu diễn kiểu; không thể chỉ gọi `getClass()` rồi suy ra mọi đối số generic sau erasure.

## 9. Thành viên `static` và đa luồng: chia sẻ đồng nghĩa phải đồng bộ đúng

Nếu nhiều luồng tạo đồng thời các `Box<T>` khác kiểu, mọi constructor vẫn cập nhật cùng bộ đếm `static`. Ví dụ đơn giản:

```java
static int count;

Box(T value) {
    this.value = value;
    count++;
}
```

Đây là **mã minh họa đặt trong một lớp `Box<T>`**, giả sử các khai báo liên quan tồn tại. `count++` gồm đọc giá trị, cộng và ghi lại; thao tác đó **không nguyên tử**. Hai luồng có thể cùng đọc giá trị cũ rồi cùng ghi giá trị mới, dẫn đến bỏ sót lần tăng.

Giải pháp tùy mục tiêu:

| Nhu cầu | Cách làm thường dùng | Lưu ý |
|---|---|---|
| Bộ đếm chính xác dưới cập nhật đồng thời | `AtomicInteger.incrementAndGet()` hoặc `AtomicLong.incrementAndGet()` | Cập nhật nguyên tử, nhưng kiểu số vẫn có giới hạn tràn. |
| Đếm cập nhật rất nhiều luồng, ưu tiên thông lượng | `LongAdder.increment()` | Thích hợp thống kê; `sum()` khi đang cập nhật không phải snapshot tuyến tính hóa. |
| Cần giữ nhiều trường nhất quán theo một giao dịch nhỏ | `synchronized` hoặc khóa phù hợp | Chỉ dùng atomic cho một biến chưa đủ nếu nhiều biến phải thay đổi cùng nhau. |
| Cache map chia sẻ | `ConcurrentHashMap<K,V>` | Tính nguyên tử của các thao tác kết hợp phải được hiểu theo hợp đồng API. |
| Trạng thái không cần chia sẻ | Trường instance hoặc dữ liệu cục bộ phương thức | Giảm chia sẻ không cần thiết, giảm rủi ro đồng thời. |

Không thể giải quyết race condition bằng cách đổi `Box<String>` thành `Box<Integer>`: kiểu đối số generic không chia tách trạng thái `static`.

### Khi nào không nên dùng `static`?

Không nên dùng trường `static` chỉ vì muốn truy cập dữ liệu “từ bất kỳ đâu”. Cân nhắc liệu dữ liệu ấy thực sự có **vòng đời cấp lớp** hay gắn với từng đối tượng, yêu cầu, phiên làm việc hoặc tác vụ riêng. Nếu gắn với từng đối tượng, dùng trường instance `T value`. Nếu cần cấu hình/phụ thuộc chia sẻ nhưng có vòng đời theo ứng dụng, có thể quản lý bằng đối tượng dịch vụ hoặc cơ chế dependency injection thay vì ngầm lưu trạng thái mutable trong trường `static`.

## 10. Thành viên `static` của lớp lồng và của giao diện generic

### 10.1. Lớp lồng `static` không dùng `T` của lớp bao

```java
class Outer<T> {
    static final class Nested<U> {
        private final U value;

        Nested(U value) {
            this.value = value;
        }

        U get() {
            return value;
        }
    }
}
```

`Nested<U>` có tham số kiểu `U` **của chính nó**, không lấy `T` của `Outer<T>`. Khi tạo:

```java
// Đoạn minh họa trong một phương thức:
Outer.Nested<String> first = new Outer.Nested<>("A");
Outer.Nested<Integer> second = new Outer.Nested<>(2);
```

Không cần một đối tượng `Outer<String>` để tạo `Outer.Nested<String>`. Tương tự quy tắc với phương thức `static`, static nested class có vị trí độc lập với cách tham số hóa của lớp bao.

### 10.2. Interface generic cũng có phương thức `static` dùng chung

```java
interface Formatter<T> {
    String format(T value);

    static <U> U identity(U value) {
        return value;
    }
}
```

`Formatter<T>` dùng `T` trong hợp đồng phương thức instance `format(T value)`. Nhưng `identity` là phương thức `static` của interface, nên nó khai báo tham số kiểu riêng `<U>`.

Có thể gọi `Formatter.identity("Java")`; không cần chọn một triển khai `Formatter<String>` và cũng không thể dùng `T` của interface trực tiếp để khai báo phương thức static. Trường khai báo trong interface là `public static final` theo quy tắc ngôn ngữ, không phải trường instance gắn với từng kiểu tham số hóa.

## 11. Phân biệt bộ đếm lớp generic với bộ đếm lớp con

Cùng một trường `static` được khai báo trên lớp cha có thể được lớp con truy cập nếu quyền truy cập cho phép. Việc hai lớp con khác nhau cùng kế thừa một lớp generic **không tự tạo bản sao mới** cho trường `static` của lớp cha.

Ví dụ:

```java
class Base<T> {
    protected static int total;

    Base() {
        total++;
    }
}

final class TextBox extends Base<String> { }
final class NumberBox extends Base<Integer> { }
```

Đây là **nhóm khai báo minh họa**. `TextBox` và `NumberBox` dùng hai cách tham số hóa của `Base<T>`, nhưng constructor của `Base` luôn tăng `Base.total`. Hai lớp con đều có quan hệ kế thừa với **cùng trường được khai báo ở `Base`**.

Nếu lớp con khai báo **một trường `static` mới cùng tên**, đó là *field hiding* (che khuất trường), tạo một trường khác thuộc lớp con. Điều ấy xuất phát từ **khai báo thêm trường**, không phải từ việc chọn đối số kiểu `String` hay `Integer`.

Tương tự, phương thức `static` có thể bị che khuất theo quy tắc static method hiding, không có dispatch đa hình giống phương thức instance được override. Khi cần gọi một tiện ích hoặc bộ đếm, nên ghi rõ tên **lớp khai báo** để tránh nhầm.

## 12. Quy tắc gọi và quan sát thành viên `static`

Hãy ưu tiên cú pháp:

```java
Box.createdCount();
Box.<String>of("Java");
```

Đây là **mẫu lời gọi**, giả sử `Box` định nghĩa hai phương thức `static` tương ứng. Tên lớp ở đầu lời gọi giúp người đọc thấy rõ thành viên thuộc về lớp, không phải một đối tượng `Box<String>` cụ thể.

Không nên diễn giải cú pháp `Box<String>.createdCount()` như cơ chế chọn một bộ đếm riêng theo `String`. Dạng gọi thành viên `static` nên dùng tên lớp generic ở dạng `Box`, không dùng một kiểu tham số hóa làm đối tượng sở hữu trạng thái riêng.

Nếu đã có tham chiếu:

```java
Box<String> item = Box.of("A");
```

một số thành viên `static` có thể được truy cập qua biểu thức trong Java, nhưng cách viết `item.createdCount()` gây hiểu nhầm rằng trạng thái phụ thuộc `item`. Trình biên dịch và công cụ kiểm tra mã thường khuyến nghị truy cập bằng tên lớp: `Box.createdCount()`.

**Một lưu ý về thời điểm chạy:** nếu dùng một biểu thức thay vì tên lớp để tham chiếu thành viên `static`, biểu thức định vị có thể vẫn được đánh giá theo quy tắc Java dù đối tượng không thực sự dùng làm “đích” gọi phương thức `static`. Vì vậy viết tên lớp trực tiếp vừa dễ hiểu vừa tránh các hiệu ứng phụ không cần thiết.

## 13. Các sai lầm thường gặp

| Nhận định hoặc đoạn mã | Vấn đề | Cách hiểu/cách sửa |
|---|---|---|
| “`Box<String>` có một bộ đếm static, `Box<Integer>` có một bộ đếm khác.” | Đối số kiểu không tạo bản sao lớp runtime. | Bộ đếm được khai báo trên `Box` là chung cho cả hai. |
| `static T last;` bên trong `class Box<T>` | `T` của lớp không được dùng trực tiếp ở static context. | Dùng kiểu cụ thể, trường instance `T` hoặc một thiết kế map có khóa kiểu phù hợp. |
| `static T identity(T value)` với `T` chỉ khai báo trên lớp | Phương thức static không thể dùng tham số kiểu của lớp. | `static <U> U identity(U value)`. |
| Đổi từ `static int count` sang `static final List<T> shared` | Vẫn dùng `T` của lớp trong thành viên static, nên không hợp lệ. | Dùng kiểu cố định hoặc quản lý dữ liệu theo khóa với hợp đồng rõ ràng. |
| “`static final List<String>` là bất biến sâu.” | `final` không ngăn sửa đối tượng danh sách. | Dùng dữ liệu bất biến hoặc bảo vệ quyền ghi. |
| `count++` từ nhiều luồng | Mất lần cập nhật do race condition. | Dùng `AtomicInteger`, `LongAdder` hoặc đồng bộ phù hợp. |
| “Mọi class loader trong JVM có cùng một biến static.” | Các class loader khác nhau có thể định nghĩa lớp cùng tên nhưng khác danh tính runtime. | Hiểu phạm vi chia sẻ theo từng định nghĩa lớp. |
| “`Box<String>.class` và `Box<Integer>.class` là hai class object.” | Class literal của kiểu được tham số hóa như vậy không hợp lệ. | Dùng `Box.class`; đối tượng tham số hóa có thể có cùng `getClass()`. |
| “Đổi `T` trong lời gọi generic static sẽ reset cache.” | Type argument chỉ là thông tin kiểu của lời gọi. | Nếu cần reset theo nhóm, thiết kế API với khóa nhóm rõ ràng. |
| “`static final` bảo đảm truy cập dữ liệu mutable an toàn đa luồng.” | `final` không làm mọi thao tác trên đối tượng được trỏ tới trở thành thread-safe. | Chọn kiểu dữ liệu đồng thời hoặc bảo vệ truy cập. |

## 14. Bài tập tự kiểm tra

**Câu 1.** Xét:

```java
class Bucket<T> {
    static int count;
    Bucket() { count++; }
}
```

Nếu tạo hai `Bucket<String>` và một `Bucket<Integer>` trong cùng lớp runtime rồi đọc `Bucket.count`, kết quả là bao nhiêu nếu không có thao tác khác?

**Câu 2.** Vì sao `static T latest;` không hợp lệ bên trong `class Bucket<T>`, dù `T` đã được khai báo?

**Câu 3.** Trong `static <U> Bucket<U> of(U item)`, `U` thuộc về lớp hay phương thức? Việc gọi `of("A")` rồi `of(12)` có tạo hai bản sao bộ đếm không?

**Câu 4.** Khi chương trình in `boxString.getClass() == boxInteger.getClass()` là `true`, điều đó nói gì về generics ở runtime?

**Câu 5.** Nếu muốn đếm riêng các hộp chứa `String` và `Integer`, có thể thiết kế một map `static` như thế nào? Tại sao vẫn nói chỉ có một trường `static`?

**Câu 6.** Tại sao bộ đếm `static int count` với `count++` có thể sai trong chương trình đa luồng? `AtomicInteger.incrementAndGet()` khắc phục điểm nào?

**Đáp án định hướng:** (1) `3` vì mọi lần chạy constructor tăng cùng một trường `static`. (2) Thành phần static không thể dùng trực tiếp tham số kiểu của lớp. (3) `U` thuộc phương thức, các lời gọi dùng chung một định nghĩa phương thức và một bộ đếm của lớp. (4) Các đối số kiểu không tạo lớp runtime chuyên biệt riêng; generics chủ yếu được kiểm tra lúc biên dịch. (5) Một `Map<Class<?>, AtomicInteger>` dùng `String.class`, `Integer.class` làm khóa; map vẫn là một trường chung của lớp. (6) `count++` không nguyên tử; atomic increment ngăn việc mất lần tăng do cập nhật đồng thời trên bộ đếm.

**Ghi nhớ:** Thành viên `static` thuộc về **lớp runtime được định nghĩa**, không thuộc riêng từng đối số kiểu của một lớp generic. `Box<String>`, `Box<Integer>` và `Box<Double>` có thể có dữ liệu instance khác nhau, nhưng chia sẻ các trường `static` của cùng `Box`. Nếu muốn dữ liệu được phân nhóm theo kiểu, hãy tự biểu diễn các nhóm bằng cấu trúc dữ liệu và khóa phù hợp, đồng thời bảo vệ trạng thái chung khi có nhiều luồng.


</details>

- [Quay lại đầu trang](#back-to-top)

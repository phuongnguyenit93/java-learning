<a id="back-to-top"></a>

# Kế thừa generic

## Menu
- [Kế thừa lớp generic với đối số kiểu cố định](#generic-inheritance-001)
- [Kế thừa và chuyển tiếp tham số kiểu](#generic-inheritance-002)
- [Thêm tham số kiểu mới ở lớp con](#generic-inheritance-003)
- [Quan hệ giữa lớp generic và giao diện generic](#generic-inheritance-004)
- [Vì sao một lớp không thể kế thừa cùng giao diện với hai đối số kiểu khác nhau](#generic-inheritance-005)
- [Kiểu thành viên được thay thế thế nào qua các mức kế thừa](#generic-inheritance-006)
- [Kiểu trả về hiệp biến và hợp đồng phương thức](#generic-inheritance-007)
- [Generic với lớp abstract, lớp final và hệ phân cấp sealed](#generic-inheritance-008)

## <a id="generic-inheritance-001">Kế thừa lớp generic với đối số kiểu cố định</a>

<details>
<summary>Xem chi tiết</summary>

# Kế thừa lớp generic với đối số kiểu cố định trong Java

## 1. Bài toán và ý tưởng cốt lõi

Một lớp generic khai báo tham số kiểu, chẳng hạn `Storage<T>`, để cùng một cấu trúc lưu trữ phục vụ nhiều loại dữ liệu. Khi tạo **lớp con**, Java cho phép chọn một kiểu cụ thể cho tham số của lớp cha ngay trong mệnh đề `extends`:

```java
// Ví dụ cú pháp; các khai báo nằm trong một tệp Java thích hợp.
class Storage<T> {
    private final T value;

    Storage(T value) {
        this.value = value;
    }

    T get() {
        return value;
    }
}

class TextStorage extends Storage<String> {
    TextStorage(String text) {
        super(text);
    }
}
```

`TextStorage extends Storage<String>` nói rằng:

- `Storage<T>` là **lớp cha generic**.
- `TextStorage` là **lớp con không generic**, vì tên lớp không khai báo `<...>`.
- `String` là **đối số kiểu cố định** (*fixed type argument*) được gắn vào `T` của lớp cha.
- Từ góc nhìn của `TextStorage`, phương thức kế thừa `get()` có kiểu trả về **`String`**; constructor cha `Storage(T)` nhận dữ liệu tương ứng **`String`**.
- Mọi lần tạo `new TextStorage(...)` đều tuân theo cùng hợp đồng `Storage<String>`. Người gọi không thể biến nó thành `Storage<Integer>` bằng cách thêm type arguments.

Mục đích của kiểu kế thừa này là biến một abstraction chung thành **một subtype có vai trò cụ thể**: ví dụ lưu chuỗi, dữ liệu số nguyên, danh sách tên hoặc bản ghi một loại domain nhất định. Trình biên dịch giữ quan hệ kiểu giữa cha và con, nên người dùng không phải ép kiểu thủ công từ `Object`.

Bài viết sử dụng Java **17+**, từ cú pháp và cơ chế kiểm tra kiểu tới override, constructor chaining, polymorphism, wildcard và lỗi phổ biến. Mục 12 có tệp Java độc lập đã được biên dịch và chạy kiểm chứng.

## 2. Khai báo lớp cha generic và lớp con cố định kiểu

Cú pháp tổng quát:

```java
class Parent<T> {
    // T có thể xuất hiện trong trường instance, constructor, phương thức...
}

class Child extends Parent<ConcreteType> {
    // Không còn khai báo type parameter T cho chính Child.
}
```

`ConcreteType` phải là kiểu tham chiếu hợp lệ và đáp ứng các **bound** của tham số kiểu lớp cha. Ví dụ `String`, `Integer`, `User`, `List<String>` đều có thể là type argument nếu không vi phạm bound; `int`, `boolean` không thể làm generic type argument trực tiếp.

Một ví dụ lớp cha có getter và setter:

```java
class Cell<T> {
    private T data;

    Cell(T data) {
        this.data = data;
    }

    T read() {
        return data;
    }

    void replace(T next) {
        data = next;
    }
}

final class StringCell extends Cell<String> {
    StringCell(String text) {
        super(text);
    }
}

// Trong một phương thức Java:
StringCell cell = new StringCell("Java");
String value = cell.read();
cell.replace("Generics");
// cell.replace(100); // Lỗi biên dịch: StringCell kế thừa Cell<String>.
```

`StringCell` không cần tự khai báo `read()` hay `replace(...)`: sau khi `T` được cố định thành `String` trong mệnh đề `extends`, compiler kiểm tra các phương thức kế thừa theo kiểu tương ứng.

**Ghi nhớ:** Trong khai báo lớp con, `extends Cell<String>` nghĩa là cố định type argument của **lớp cha**. Đây không phải cú pháp khai báo một tham số kiểu mới. So sánh:

```java
class A extends Cell<String> { // A không generic.
    A() { super("fixed"); }
}

class B<U> extends Cell<U> {    // B vẫn generic và truyền U lên cha.
    B(U initial) { super(initial); }
}
```

`A` có một hợp đồng duy nhất `Cell<String>`. `B<Integer>` dùng `Cell<Integer>`, còn `B<String>` dùng `Cell<String>`, tùy đối số kiểu ở lúc sử dụng `B<U>`.

## 3. Bảng ánh xạ tham số kiểu qua kế thừa

Khi gặp `class Child extends Parent<String>`, nên kiểm tra toàn bộ các vị trí sử dụng `T` trong API lớp cha, không chỉ một getter:

| Thành viên khai báo trong `Parent<T>` | Góc nhìn từ `Child extends Parent<String>` |
| --- | --- |
| `private T value;` | Dữ liệu bên trong cha chịu ràng buộc `String` |
| `Parent(T initial)` | Constructor cha cần dữ liệu tương thích `String` |
| `T get()` | Khi gọi trên `Child`, kết quả là `String` |
| `void set(T item)` | Khi gọi trên `Child`, đối số phải tương thích `String` |
| `List<T> all()` | Khi gọi trên `Child`, kết quả có kiểu `List<String>` |
| `Optional<T> find()` | Khi gọi trên `Child`, kết quả có kiểu `Optional<String>` |
| `<R> R map(Function<? super T,? extends R> f)` | Tham số đầu vào của hàm phù hợp `String`, còn `R` vẫn được phương thức suy luận riêng |
| `static <U> U echo(U value)` | `U` thuộc phương thức static; không bị cố định thành `String` |

Không phải mọi API của lớp cha đều bị thay tất cả tham số kiểu thành `String`. Chỉ **những lần tham chiếu `T` của lớp cha** được ánh xạ theo `extends Parent<String>`. Tham số kiểu riêng của một generic method, ví dụ `<R>`, vẫn độc lập.

Đặc biệt, lớp con **không thể trực tiếp truy cập trường `private T value`** của cha, dù đã cố định `T` thành `String`. Generics không thay đổi quy tắc truy cập `private`/`protected`. Hãy dùng getter, phương thức được bảo vệ hoặc constructor cha thay vì phá vỡ encapsulation.

## 4. Constructor lớp con và lời gọi `super(...)`

Constructor **không được kế thừa**. Dù lớp con cố định type argument, nó vẫn cần constructor của riêng mình để khởi tạo phần cha. Nếu lớp cha không có constructor không tham số có thể truy cập, lớp con phải gọi constructor thích hợp bằng `super(...)`:

```java
class Parent<T> {
    private final T data;

    Parent(T data) {
        this.data = java.util.Objects.requireNonNull(data);
    }

    T get() {
        return data;
    }
}

final class EmailValue extends Parent<String> {
    EmailValue(String email) {
        super(email); // T của Parent<T> được cố định thành String.
    }
}
```

Ở Java 17, lời gọi `super(email)` phải là câu lệnh đầu tiên của constructor (trừ trường hợp cú pháp bổ sung được các phiên bản Java mới hơn cho phép). Lời gọi tới `Parent(T)` thực chất được compiler kiểm tra với `T = String` khi đi qua kiểu `Parent<String>`.

Một lỗi thường gặp:

```java
final class Wrong extends Parent<String> {
    // Wrong() {} // Lỗi nếu Parent<T> không có constructor no-arg khả dụng.
    Wrong(String text) {
        super(text);
    }
}
```

Không ghi `super<String>(text)` để chỉ định type argument của lớp cha. Kiểu `String` đã được chọn ở **mệnh đề extends**. `super(text)` là lời gọi constructor, không phải nơi lặp lại `Parent<String>`.

### Tái sử dụng kiểm tra invariant của lớp cha

Một lớp cha có thể chặn null hoặc xác thực dữ liệu ngay trong constructor:

```java
class NonNullValue<T> {
    private final T value;

    NonNullValue(T value) {
        this.value = java.util.Objects.requireNonNull(value);
    }

    T get() { return value; }
}

final class NonNullTitle extends NonNullValue<String> {
    NonNullTitle(String title) {
        super(title);
    }
}
```

`NonNullTitle` kế thừa quy tắc không cho phép `null` của cha. Tuy nhiên, generics **không tự cấm null**: nếu constructor cha không kiểm tra, truyền `null` có thể hợp lệ về mặt kiểu. Phân biệt **type safety** với **data validation**.

## 5. Ghi đè phương thức sau khi cố định type argument

Nếu cha có phương thức `T get()`, lớp con `extends Parent<String>` có thể override bằng phương thức trả `String`:

```java
import java.util.Locale;

class Base<T> {
    private final T value;

    Base(T value) {
        this.value = value;
    }

    public T get() {
        return value;
    }
}

final class UppercaseText extends Base<String> {
    UppercaseText(String value) {
        super(value);
    }

    @Override
    public String get() {
        return super.get().toUpperCase(Locale.ROOT);
    }
}
```

Tại `UppercaseText`, `T` của `Base<T>` đã được cố định thành `String`, vì vậy `@Override public String get()` khớp hợp đồng. `@Override` giúp compiler phát hiện lỗi tên/chữ ký khi người viết tưởng đang override nhưng thực tế lại overload.

Ví dụ nếu lớp cha có setter `void set(T value)` thì lớp con cố định kiểu phải override bằng `void set(String value)` tương ứng (với cùng hoặc độ truy cập rộng hơn, cùng quy tắc checked exception). Một overload nhận `Object` thường là phương thức khác, hoặc tạo xung đột erasure tùy chữ ký: không dùng `Object` để né ràng buộc generic.

### Bridge method và type erasure

Trong ví dụ `Base<T>.get()`, type erasure làm chữ ký JVM cơ bản của getter cha tương ứng với kiểu trả `Object` (khi `T` không bound). Getter được override trong `UppercaseText` có kiểu trả `String`. Compiler có thể sinh thêm **bridge method** để giữ đa hình ở mức bytecode. Bridge là chi tiết triển khai do compiler xử lý; người học **không nên tự viết** bridge.

Kết quả ở mã nguồn vẫn bảo đảm:

```java
Base<String> base = new UppercaseText("java");
String text = base.get(); // "JAVA" nhờ dispatch động.
```

Kiểu của biến `base` là `Base<String>`; phương thức được chọn lúc chạy là implementation override trong `UppercaseText`. Generic type checking và dynamic dispatch cùng phối hợp.

## 6. Đa hình: lớp con có thể được tham chiếu bằng kiểu cha đã cố định

Quan hệ kế thừa đúng là:

```java
class Parent<T> {
    Parent(T value) {}
}

class TextChild extends Parent<String> {
    TextChild() {
        super("text");
    }
}

// Parent<String> one = new TextChild(); // Hợp lệ.
// Parent<Integer> two = new TextChild(); // Lỗi biên dịch.
```

`TextChild` là subtype của `Parent<String>`, **không phải** subtype của `Parent<Integer>` hoặc `Parent<Object>`.

Một nguyên lý rất quan trọng: dù `String extends Object`, kiểu `Parent<String>` **không kế thừa** `Parent<Object>`. Generics Java là **invariant**. Ví dụ compiler phải từ chối:

```java
Parent<String> s = new TextChild();
// Parent<Object> o = s; // Sai: không tương thích về type argument.
```

Nếu phép gán này được phép, các API nhận `Parent<Object>` có thể đưa một kiểu khác `String` vào cấu trúc đã cam kết lưu chuỗi. Java giữ ràng buộc bằng cách từ chối chuyển đổi kiểu này.

### Tham chiếu wildcard khi chưa cần biết type argument

Nếu chỉ muốn duyệt các đối tượng thuộc nhiều kiểu cha generic, hãy dùng wildcard:

```java
// Ví dụ trong một phương thức, giả sử cha có get().
Parent<?> a = new TextChild();
Object value = a.get();
```

`Parent<?>` chấp nhận một `Parent<String>`, `Parent<Integer>`, v.v. Tuy nhiên `get()` lúc này chỉ có thể coi là trả `Object` ở compile-time (trừ khi bound cung cấp kiểu cụ thể), và các thao tác ghi vào tham số chưa biết sẽ bị hạn chế.

Khi thiết kế API, **giữ `Parent<String>` nếu cần biết chính xác kiểu dữ liệu**; dùng `Parent<?>` nếu chỉ cần xử lý đối tượng như một thể hiện bất kỳ của abstraction.

## 7. Lớp con không generic khác lớp con generic thế nào?

Ba chiến lược thường gặp:

```java
class Holder<T> {
    private final T value;

    Holder(T value) { this.value = value; }

    T get() { return value; }
}

// (1) Cố định T thành String.
class StringHolder extends Holder<String> {
    StringHolder(String value) { super(value); }
}

// (2) Giữ type parameter U cho lớp con, chuyển U lên cha.
class ForwardHolder<U> extends Holder<U> {
    ForwardHolder(U value) { super(value); }
}

// (3) Lớp con generic nhưng cha cố định String.
class TaggedStringHolder<K> extends Holder<String> {
    private final K tag;

    TaggedStringHolder(K tag, String value) {
        super(value);
        this.tag = tag;
    }

    K tag() { return tag; }
}
```

Bảng so sánh:

| Kiểu lớp con | Kiểu của lớp cha | Kiểu getter kế thừa | Ý nghĩa |
| --- | --- | --- | --- |
| `StringHolder` | `Holder<String>` | `String` | Lớp con cố định hoàn toàn kiểu giá trị. |
| `ForwardHolder<Integer>` | `Holder<Integer>` | `Integer` | Lớp con tiếp tục generic theo `U`. |
| `TaggedStringHolder<Long>` | `Holder<String>` | `String` | Dữ liệu cha vẫn là chuỗi, còn `K` chỉ dành cho nhãn của lớp con. |

Lớp con **có thể generic** nhưng vẫn dùng đối số kiểu cố định ở cha. Đừng suy luận "lớp con có `<K>` nên type argument của cha phải là `K`": hai quyết định độc lập.

### Cố định một số tham số và giữ lại số khác

Với lớp cha có hai type parameters:

```java
class Pair<A, B> {
    private final A first;
    private final B second;

    Pair(A first, B second) {
        this.first = first;
        this.second = second;
    }

    A first() { return first; }
    B second() { return second; }
}

// Cố định A, giữ B do lớp con khai báo.
class NamedPair<V> extends Pair<String, V> {
    NamedPair(String name, V value) {
        super(name, value);
    }
}

// NamedPair<Integer> score = new NamedPair<>("Lan", 9);
// score.first() là String; score.second() là Integer.
```

`NamedPair<V> extends Pair<String,V>` là ví dụ **cố định một phần** của generic superclass. `String` được cố định cho tham số `A`; `V` của lớp con truyền tiếp vào tham số `B` của cha.

## 8. Kế thừa qua nhiều cấp

Cố định type argument có thể diễn ra ở một mức trung gian và tiếp tục duy trì ở các mức sau:

```java
class Base<T> {
    private final T value;
    Base(T value) { this.value = value; }
    T get() { return value; }
}

class TextBase extends Base<String> {
    TextBase(String value) { super(value); }
}

final class LocalizedText extends TextBase {
    LocalizedText(String value) { super(value); }
}

// String s = new LocalizedText("Xin chào").get();
```

`LocalizedText` không viết `extends Base<String>` trực tiếp, nhưng thông qua `TextBase`, nó vẫn kế thừa getter theo kiểu `String`. Type argument cố định ở một nhánh kế thừa tiếp tục ràng buộc các lớp con bên dưới.

Các lớp con cũng có thể triển khai thêm interface hoặc phương thức mới, nhưng không thể "đổi" kiểu đã cố định ở cha thành một kiểu khác chỉ bằng khai báo mới:

```java
// Không có nghĩa vụ và cũng không có cú pháp hợp lệ để viết:
// class Broken extends TextBase<Integer> {}
// TextBase vốn không phải generic class.
```

Nếu cần một phiên bản chứa `Integer`, hãy tạo một lớp con khác trực tiếp từ `Base<Integer>`, hoặc thiết kế một lớp con tiếp tục generic như `ForwardHolder<U>`.

## 9. Cố định kiểu khi kế thừa lớp có bound

Lớp cha có thể giới hạn type argument:

```java
class NumericHolder<T extends Number> {
    private final T number;

    NumericHolder(T number) {
        this.number = number;
    }

    T get() { return number; }

    double asDouble() {
        return number.doubleValue();
    }
}

class IntegerHolder extends NumericHolder<Integer> {
    IntegerHolder(Integer value) {
        super(value);
    }
}

// IntegerHolder ok = new IntegerHolder(7);
// class Bad extends NumericHolder<String> { } // Sai bound Number.
```

`Integer` kế thừa `Number`, nên `NumericHolder<Integer>` hợp lệ. `String` không kế thừa `Number`, nên `NumericHolder<String>` không biên dịch.

Một bound kiểu interface hoạt động tương tự:

```java
class SortedValue<T extends Comparable<T>> {
    private final T value;

    SortedValue(T value) {
        this.value = value;
    }

    boolean before(T other) {
        return value.compareTo(other) < 0;
    }
}

class TextOrder extends SortedValue<String> {
    TextOrder(String value) {
        super(value);
    }
}
```

`String` triển khai `Comparable<String>`, vì vậy đáp ứng ràng buộc của `SortedValue<T>`. Bound yêu cầu **kiểu cố định của lớp con** phải thỏa các API mà superclass sử dụng.

Khi chọn type argument, hãy kiểm tra các bound từ chữ ký cha trước; đừng dùng raw type hoặc unchecked cast nhằm vượt qua lỗi compiler.

## 10. Đối số kiểu cố định có thể là một generic type khác

`ConcreteType` trong `extends Parent<ConcreteType>` không nhất thiết phải là kiểu đơn giản như `String`; nó có thể là kiểu đã tham số hóa đầy đủ:

```java
import java.util.*;

class Value<T> {
    private final T value;
    Value(T value) { this.value = value; }
    T get() { return value; }
}

final class NameListValue extends Value<List<String>> {
    NameListValue(Collection<String> names) {
        super(List.copyOf(names));
    }
}

// NameListValue people = new NameListValue(List.of("Lan", "Mai"));
// List<String> names = people.get();
```

Với `NameListValue`, type argument `T` của cha được cố định thành **`List<String>`**, không chỉ `String`. Getter của cha có kiểu trả `List<String>`.

Ở đây `List.copyOf` chủ đích tạo danh sách không sửa được và không chứa `null`. Tính chất này đến từ factory, **không tự động được bảo đảm bởi generic inheritance**. Nếu lớp cần kiểm soát dữ liệu mutable được chia sẻ qua alias, hãy thiết kế defensive copy hoặc một chính sách sở hữu dữ liệu rõ ràng.

Có thể mở rộng mẫu này cho `Map<String, Integer>`, `Optional<User>` hoặc một record domain. Tuy nhiên, làm lớp con chỉ để đặt alias ngắn hơn có thể khiến hệ thống lớp phình to; hãy cân nhắc liệu lớp con có thêm ngữ nghĩa, validation hoặc hành vi thật sự không.

## 11. Kết hợp lớp con cố định kiểu với interface generic

Một lớp con có thể cố định superclass đồng thời triển khai một interface generic. Hai quan hệ phải được kiểm tra riêng:

```java
interface Formatter<T> {
    String format(T value);
}

class Box<T> {
    private final T value;
    Box(T value) { this.value = value; }
    T get() { return value; }
}

final class PrintableText extends Box<String> implements Formatter<String> {
    PrintableText(String value) {
        super(value);
    }

    @Override
    public String format(String value) {
        return "[" + value + "]";
    }
}
```

`Box<String>` bảo đảm dữ liệu kế thừa là `String`. `Formatter<String>` bảo đảm chữ ký `format(String)` phù hợp với hợp đồng interface. Đó là hai cách sử dụng type argument cố định, có thể phối hợp trên cùng một lớp con.

Ngược lại, khai báo `implements Formatter<Integer>` sẽ yêu cầu implementation của `format(Integer)`, chứ không tự đổi kiểu cha `Box<String>`. Java cho phép các type arguments khác nhau khi hai abstraction diễn đạt những vai trò độc lập, miễn là các chữ ký và quan hệ kế thừa không xung đột.

**Hạn chế:** một lớp không thể kế thừa cùng một generic interface theo hai type arguments khác nhau qua hai đường kế thừa xung đột (ví dụ cố đạt đồng thời `Comparable<String>` và `Comparable<Integer>`). Sự trùng nhau sau type erasure và ràng buộc kế thừa ngăn kiểu thiết kế đó.

## 12. Chương trình Java 17+ hoàn chỉnh và kiểm chứng

Tạo tệp **`FixedTypeInheritanceDemo.java`** đúng nội dung dưới đây. Tệp gom các ví dụ về superclass generic, lớp con cố định type argument, lớp con tiếp tục generic, override và interface cố định kiểu. Mã không có lỗi cố ý hoặc phụ thuộc thư viện ngoài JDK.

```java
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class FixedTypeInheritanceDemo {
    static abstract class Storage<T> {
        private final T value;

        protected Storage(T value) {
            this.value = Objects.requireNonNull(value);
        }

        public T get() {
            return value;
        }

        public abstract String describe();
    }

    static final class TextStorage extends Storage<String> {
        TextStorage(String text) {
            super(text);
        }

        @Override
        public String get() {
            return super.get().toUpperCase(Locale.ROOT);
        }

        @Override
        public String describe() {
            return "Text(" + get() + ")";
        }
    }

    static final class NumberStorage extends Storage<Integer> {
        NumberStorage(Integer number) {
            super(number);
        }

        @Override
        public String describe() {
            return "Integer(" + get() + ")";
        }
    }

    static final class GenericStorage<U> extends Storage<U> {
        GenericStorage(U value) {
            super(value);
        }

        @Override
        public String describe() {
            return "Generic(" + get() + ")";
        }
    }

    interface Formatter<T> {
        String format(T value);
    }

    static final class TextFormatter implements Formatter<String> {
        @Override
        public String format(String value) {
            return value + "!";
        }
    }

    static void display(Storage<?> storage) {
        System.out.println(storage.describe());
    }

    public static void main(String[] args) {
        Storage<String> text = new TextStorage("java");
        Storage<Integer> count = new NumberStorage(7);
        Storage<Double> pi = new GenericStorage<>(3.14);

        System.out.println("get: " + text.get());
        System.out.println("count: " + count.get());
        System.out.println("pi: " + pi.get());

        for (Storage<?> item : List.of(text, count, pi)) {
            display(item);
        }

        Formatter<String> formatter = new TextFormatter();
        System.out.println("format: " + formatter.format("hello"));
        System.out.println("same class: " +
                (new GenericStorage<>("x").getClass()
                        == new GenericStorage<>(10).getClass()));
    }
}
```

Lưu tệp bằng UTF-8. Biên dịch bằng JDK 17 trở lên:

```bash
javac --release 17 -Xlint:all FixedTypeInheritanceDemo.java
java FixedTypeInheritanceDemo
```

Kết quả **stdout chính xác** (đã biên dịch với chế độ `--release 17` và chạy thành công):

```text
get: JAVA
count: 7
pi: 3.14
Text(JAVA)
Integer(7)
Generic(3.14)
format: hello!
same class: true
```

**Diễn giải luồng chạy:**

1. `TextStorage extends Storage<String>` cố định type argument, nên biến `Storage<String> text` nhận được instance `TextStorage`. Override `get()` trả chuỗi viết hoa `JAVA`.
2. `NumberStorage extends Storage<Integer>` là một lớp con cố định kiểu khác của cùng superclass; `count.get()` được compiler hiểu là `Integer`.
3. `GenericStorage<U> extends Storage<U>` **không cố định type argument ở khai báo lớp**, vì `U` tiếp tục được lựa chọn cho từng cách dùng `GenericStorage<U>`. `new GenericStorage<>(3.14)` tạo `GenericStorage<Double>` tương thích `Storage<Double>`.
4. `List.of(text, count, pi)` và vòng lặp dùng `Storage<?>` để đi qua các đối tượng có type arguments khác nhau. Phương thức `display` chỉ gọi `describe()`, không cần biết kiểu dữ liệu của từng đối tượng.
5. `TextFormatter implements Formatter<String>` minh họa interface generic được cố định thành `String`; compiler kiểm tra `format(String)`.
6. Hai cách tham số hóa `GenericStorage<String>` và `GenericStorage<Integer>` có cùng `Class` runtime vì type erasure; do đó dòng cuối in `true`. Điều này không làm mất kiểm tra generic tại compile-time.

Trong chương trình, các lớp lồng được khai báo `static` để tệp độc lập và dễ chạy. Type arguments của `Storage<T>` vẫn được kiểm tra tại các trường/method instance; từ khóa `static` ở đây chỉ mô tả **vị trí lồng của lớp** trong `FixedTypeInheritanceDemo`.

## 13. Những hiểu nhầm và lỗi compiler phổ biến

| Sai lầm / tình huống | Vấn đề | Cách xử lý |
| --- | --- | --- |
| `class Child extends Parent` | Dùng **raw superclass**, đánh mất thông tin type argument | Khai báo `extends Parent<String>` hoặc một type argument phù hợp. |
| `class Child extends Parent<>` | Diamond `<>` không dùng trong mệnh đề `extends` | Ghi type argument cụ thể: `extends Parent<String>`. |
| `class Child<T> extends Parent<T>` nhưng mong cha luôn giữ `String` | `T` vẫn do người sử dụng Child lựa chọn | Nếu muốn cố định: `class Child extends Parent<String>`. |
| `class Child extends Parent<String>` rồi truyền `Integer` vào `super(...)` | Constructor cha yêu cầu `String` | Chuyển đổi đầu vào có chủ đích trước khi gọi `super(...)`. |
| Lớp cha không có constructor no-arg, con bỏ `super(...)` | Không có constructor cha phù hợp để gọi ngầm | Khai báo constructor con và gọi `super(value)`. |
| Override `T get()` bằng kiểu không tương thích sau khi cố định `T` | Chữ ký override không khớp kiểu trả về | Với `Parent<String>`, trả `String` hoặc kiểu trả về covariant hợp lệ. |
| Gán `Parent<String>` sang `Parent<Object>` | Generics Java invariant | Giữ type argument chính xác hoặc dùng `Parent<?>` nếu chỉ cần đọc chung. |
| Tạo `extends Parent<int>` | Primitive không là generic type argument | Sử dụng `Integer`. |
| `extends NumericParent<String>` với `T extends Number` | Không đạt bound của superclass | Chọn `Integer`/`Double` hoặc kiểu số thỏa bound. |
| Trong lớp con, cố truy cập `private T value` của cha | `private` không đổi vì generic inheritance | Dùng accessor hoặc API được thiết kế rõ ràng. |
| Kế thừa cùng generic interface qua hai type arguments xung đột | Java không cho kiểu kế thừa không nhất quán | Phân tách abstraction hoặc thống nhất hợp đồng. |
| `instanceof Parent<String>` | Type argument `String` không được kiểm tra bằng `instanceof` thông thường | Dùng `instanceof Parent<?>` rồi xác thực nội dung nếu cần. |
| Nghĩ `extends Parent<String>` tạo lớp runtime riêng cho `Parent<String>` | Generic type arguments phần lớn bị erase | Phân biệt kiểu nguồn compile-time và `Class` runtime. |
| Dùng raw type hoặc unchecked cast để qua lỗi compiler | Có nguy cơ heap pollution và `ClassCastException` | Thiết kế lại quan hệ kiểu, dùng wildcard/bound đúng vai trò. |

### Dùng `@Override` để nhận ra lỗi sớm

Luôn cân nhắc đặt `@Override` khi lớp con cố định kiểu ghi đè phương thức từ cha:

```java
class Parent<T> {
    public T get() { return null; }
}

class Child extends Parent<String> {
    @Override
    public String get() {
        return "ok";
    }
}
```

Nếu vô tình viết `get(String arg)`, annotation `@Override` sẽ giúp phát hiện rằng đó không phải override của `get()`. Đây là cách thực hành tốt khi generic substitution làm chữ ký inherited dễ nhìn nhầm.

## 14. Khi nào nên cố định kiểu, khi nào giữ lớp con generic?

**Cố định kiểu trong `extends` khi** lớp con mang một ý nghĩa chuyên biệt và mọi instance hợp lệ của nó cần cùng một loại dữ liệu. Ví dụ `TextStorage` cần thao tác trên chuỗi, `IntegerHolder` cung cấp logic cho giá trị số nguyên. Lớp con có thể bổ sung validation, định dạng, method domain hoặc tối ưu hóa mà vẫn tái sử dụng abstraction chung.

**Tiếp tục generic** với `class ForwardStorage<U> extends Storage<U>` khi hành vi mới vẫn áp dụng được cho nhiều loại dữ liệu. Đừng tạo các lớp con cố định kiểu nếu việc duy nhất của chúng là gọi `super(...)` mà không tạo thêm giá trị về mặt mô hình hay API.

Nếu chỉ muốn gán một cái tên cho một tổ hợp generic phổ biến như `List<String>`, hãy cân nhắc giữ nguyên type rõ ràng trong API, dùng record/domain object có ý nghĩa hoặc composition. Kế thừa implementation collection như `HashMap<String,Integer>` chỉ để tạo tên ngắn đôi khi khiến lớp khó bảo trì, nhất là khi đối tượng không thật sự là một subtype có hành vi riêng.

Ví dụ thiết kế domain phù hợp:

```java
record UserId(String value) {
    UserId {
        java.util.Objects.requireNonNull(value);
    }
}

class Identifier<T> {
    private final T value;

    Identifier(T value) {
        this.value = java.util.Objects.requireNonNull(value);
    }

    T get() { return value; }
}

final class UserIdentifier extends Identifier<UserId> {
    UserIdentifier(UserId id) {
        super(id);
    }
}
```

`UserIdentifier` cố định kiểu của cha thành một record domain cụ thể `UserId`. Cách thiết kế này có ý nghĩa nếu API của hệ thống muốn phân biệt ID người dùng với các ID khác và áp dụng hành vi/validation riêng.

## 15. Bài tập và tiêu chí tự đánh giá

**Bài 1 — Getter có kiểu cố định.** Viết `class Box<T>` giữ `T value` và getter `T get()`. Tạo `class LongBox extends Box<Long>`; thiết kế constructor gọi `super(Long)`. Thử gán `LongBox` cho `Box<Long>`, sau đó thử gán cho `Box<Number>` và giải thích vì sao phép gán thứ hai thất bại.

**Bài 2 — Override.** Viết `class MessageBox extends Box<String>`, override getter để thêm tiền tố `"Message: "`. Dùng `Box<String> ref = new MessageBox("Hi")` và gọi `ref.get()`; giải thích sự phối hợp của type checking và dynamic dispatch.

**Bài 3 — Giữ type parameter.** Viết `class History<T> extends Box<List<T>>`. Vì sao lớp con **vẫn generic**, dù kiểu của cha là `Box<List<T>>`? Kiểm tra `History<Integer>` có getter trả `List<Integer>`. Nếu thay `T` bằng `String`, hãy mô tả lớp con cố định thành `Box<List<String>>`.

**Bài 4 — Bound.** Viết `class PositiveInteger extends NumericHolder<Integer>` chỉ chấp nhận số dương. Đưa validation vào constructor hoặc một factory có tên rõ. Kiểm tra caller không thể tạo đối tượng chứa `String` và giải thích hai lớp bảo vệ độc lập: **bound generic** và **ràng buộc nghiệp vụ giá trị dương**.

**Bài 5 — Chuỗi kế thừa.** Tạo `Base<T> → FixedBase extends Base<String> → FinalText extends FixedBase`. Phương thức getter ở `FinalText` trả kiểu gì? Có được khai báo `FinalText extends FixedBase<Integer>` không? Vì sao?

**Bài 6 — Interface generic.** Tạo `interface Serializer<T>` có `String serialize(T item)`. Viết `UserSerializer implements Serializer<User>` với record `User(String name)`; nêu kiểu tham số bắt buộc của phương thức override. Nếu đồng thời kế thừa `Box<String>`, hai ràng buộc generic có vai trò gì?

**Bài 7 — Reflection và runtime.** Dự đoán kết quả kiểm tra `new GenericStorage<>("a").getClass() == new GenericStorage<>(1).getClass()`. Vì sao kết quả không có nghĩa Java cho phép gán `GenericStorage<String>` sang `GenericStorage<Integer>`?

**Bài 8 — Thiết kế API.** Một nhóm chỉ cần hàm `display(Storage<?> storage)` để in mô tả. Khi nào nên nhận wildcard `Storage<?>`, khi nào cần giữ `Storage<String>` để gọi phương thức dành cho chuỗi? Hãy liên hệ mục đích của API với độ an toàn của thao tác.

Bạn đã nắm bài khi có thể trả lời: **lớp con khai báo type parameter mới hay không; tham số nào của superclass được cố định; kiểu của constructor/getter/setter sau khi cố định là gì; và vì sao `Parent<String>` không đồng nghĩa `Parent<Object>`**. Việc nắm chính xác phép ánh xạ kiểu qua `extends` giúp xây dựng cây kế thừa tái sử dụng được mà vẫn bảo đảm an toàn kiểu tại thời điểm biên dịch.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="generic-inheritance-002">Kế thừa và chuyển tiếp tham số kiểu</a>

<details>
<summary>Xem chi tiết</summary>

# Kế thừa và chuyển tiếp tham số kiểu trong Java Generics

Khi thiết kế một hệ thống class và interface generic, bạn thường không chỉ viết `class Box<T>` rồi dùng trực tiếp `Box<String>`. Một class con có thể **giữ nguyên type parameter** của class cha, **cố định type argument**, **đổi vị trí các type arguments** hoặc **tạo một generic type lồng nhau** để truyền lên. Tương tự, một generic interface có thể được kế thừa bởi interface khác hoặc được class triển khai với một type argument phù hợp.

Đây là ý nghĩa của **kế thừa và chuyển tiếp tham số kiểu** (*generic inheritance / forwarding type parameters*): quy tắc subtype của Java phải đi kèm với những phép thay thế kiểu nhất quán. Bài này giải thích cách đọc các quan hệ ấy, vì sao không được đồng nhất `Parent<ChildType>` với `Parent<ParentType>`, cách override sau khi type argument đã được chuyên biệt hóa và những lỗi phổ biến do type erasure.

Các chương trình đầy đủ dùng **Java 17+**. Mỗi ví dụ có public class được lưu trong tệp cùng tên; phần chỉ minh họa chữ ký sẽ được ghi chú riêng.

## 1. Ôn nhanh: type parameter và type argument không phải một

Xét hai khai báo:

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

class StringBox extends Box<String> {
    StringBox(String value) {
        super(value);
    }
}
```

Đây là các khai báo Java hợp lệ nhưng chưa có `main`.

- `T` trong `Box<T>` là **type parameter** do class `Box` khai báo.
- `String` trong `Box<String>` là **type argument** truyền cho `Box` khi khai báo superclass của `StringBox`.
- `StringBox` **không khai báo** một type parameter của riêng nó; nó kế thừa từ một cách sử dụng cụ thể của `Box`.
- Ở code nguồn, `StringBox.get()` được compiler xem như trả `String` vì `T` của `Box` đã được ràng buộc thành `String`.
- Java vẫn tạo một superclass/subclass theo ngữ nghĩa runtime thông thường; generic type argument không tạo một biến thể class JVM mới.

Đọc `extends Box<String>` như câu: “class hiện tại là subtype của `Box` với type argument `String`”. Đừng đọc `<String>` thành “khai báo type parameter String”.

## 2. Quy tắc cốt lõi của việc chuyển tiếp

Một lớp con **có thể khai báo type parameter mới** và dùng nó làm type argument cho cha:

```java
class Parent<T> {
    T value;
}

class Child<U> extends Parent<U> {
}
```

Ở đây:

1. `Parent<T>` khai báo biến kiểu `T`.
2. `Child<U>` khai báo một biến kiểu **riêng** `U`.
3. `extends Parent<U>` truyền `U` của `Child` làm **type argument** để áp dụng `Parent<T>`.
4. Đối với `Child<String>`, kiểu kế thừa là `Parent<String>`. Do đó field `value` của `Parent` có kiểu tĩnh `String` khi truy cập qua reference phù hợp.

Bạn cũng có thể viết `class Child<T> extends Parent<T>`. Lúc đó hai chữ `T` thuộc **hai khai báo khác nhau**, nhưng mệnh đề `extends Parent<T>` ánh xạ type parameter của cha sang type parameter của con. Tên giống nhau chỉ là quy ước, còn **mối quan hệ nằm trong phần `extends`**.

Một cách kiểm tra bằng bảng:

| Khai báo | Type parameter do ai sở hữu? | Mapping ở mệnh đề extends |
| --- | --- | --- |
| `Parent<T>` | `Parent` sở hữu `T` | Chưa có mapping |
| `Child<U>` | `Child` sở hữu `U` | `Parent.T ← Child.U` |
| `Child<String>` | Dùng `String` làm argument của Child | Suy ra `Parent.T = String` |
| `Child<Integer>` | Dùng `Integer` làm argument của Child | Suy ra `Parent.T = Integer` |

Phép ánh xạ được compiler áp dụng xuyên suốt field, method và các interface của class cha. Nếu lớp cha có `T get()` thì một `Child<String>` có method kế thừa mà caller sử dụng dưới dạng trả `String`.

## 3. Chuyển tiếp nguyên một type parameter: ví dụ chạy được

Tạo tệp `ForwardingDemo.java`:

```java
public class ForwardingDemo {
    static class Storage<T> {
        private T value;

        Storage(T value) {
            this.value = value;
        }

        T get() {
            return value;
        }

        void set(T value) {
            this.value = value;
        }
    }

    static final class AuditedStorage<E> extends Storage<E> {
        private int updates;

        AuditedStorage(E value) {
            super(value);
        }

        @Override
        void set(E value) {
            super.set(value);
            updates++;
        }

        int updates() {
            return updates;
        }
    }

    public static void main(String[] args) {
        AuditedStorage<String> names = new AuditedStorage<>("An");
        names.set("Binh");

        String name = names.get();
        Storage<String> baseView = names;

        System.out.println(name);
        System.out.println(baseView.get());
        System.out.println(names.updates());
    }
}
```

Biên dịch và chạy:

```bash
javac ForwardingDemo.java
java ForwardingDemo
```

Kết quả `stdout`:

```text
Binh
Binh
1
```

Điểm quan trọng:

- `Storage<T>` khai báo hợp đồng kiểu cho `value`, `get()` và `set(T)`.
- `AuditedStorage<E>` khai báo kiểu `E` của riêng nó và **chuyển tiếp** thành `Storage<E>`.
- Với `AuditedStorage<String>`, compiler nhìn `get()` trả `String` và `set(...)` nhận `String`.
- `@Override void set(E value)` tiếp tục đúng hợp đồng của cha với type argument đã được áp dụng.
- `Storage<String> baseView = names` hợp lệ: một subtype có thể được upcast sang **supertype với cùng cách tham số hóa**.
- Nếu viết `names.set(123)`, compilation thất bại vì yêu cầu `String`.

Lưu ý: kế thừa generic không có nghĩa constructor của cha được tự động kế thừa. Constructor `AuditedStorage(E)` gọi `super(value)` để khởi tạo trạng thái cha. `Storage<T>` có thể định nghĩa constructor và generic methods riêng; hai khái niệm đó không thay thế nhau.

## 4. Phân biệt chuyển tiếp và cố định type argument

Lớp con **không nhất thiết** phải generic. Nếu nó luôn chuyên xử lý chuỗi, thay vì khai báo `<U>` rồi chuyển `U` lên cha, nó có thể cố định type argument một lần:

```java
// Giả sử Storage<T> có constructor Storage(T) và T get() như mục 3.
class TextStorage extends Storage<String> {
    TextStorage(String initial) {
        super(initial);
    }
}
```

Với `TextStorage`, kiểu kế thừa là `Storage<String>`; `get()` trả String ở source level. Ngược lại, `AuditedStorage<E> extends Storage<E>` trong mục 3 vẫn giữ E để mỗi cách dùng `AuditedStorage<String>` hoặc `AuditedStorage<Integer>` có hợp đồng riêng.

Khác biệt cần đọc ở chính mệnh đề kế thừa: `Storage<String>` là **kiểu cố định**, còn `Storage<E>` là **chuyển tiếp tham số kiểu**. Constructor lớp con vẫn cần khởi tạo phần superclass qua `super(initial)` nếu cha không có constructor không tham số phù hợp. Kế thừa không tự tạo constructor mới.

Bài này dùng hai hình thức ấy để so sánh cơ chế *forwarding*; khi cần tự thiết kế lớp chuyên biệt cố định kiểu, hãy xác định nó thực sự mang hành vi/domain invariant gì thay vì tạo subclass chỉ để đổi tên `Storage<String>`.

## 5. Biến đổi type argument khi truyền lên class cha

Một lớp con có thể dùng type parameter của mình bên trong một **kiểu được tham số hóa** để truyền cho cha:

```java
class Parent<T> {
    T value;
}

class Group<E> extends Parent<java.util.List<E>> {
}
```

Mapping không còn là `Parent.T ← E` mà là **`Parent.T ← List<E>`**.

Với `Group<String>`, kiểu kế thừa là `Parent<List<String>>`. Bởi vậy field `value` của cha có kiểu tĩnh `List<String>`, không phải `String`.

Tạo `NestedForwardingDemo.java`:

```java
import java.util.List;

public class NestedForwardingDemo {
    static class Container<T> {
        private final T data;

        Container(T data) {
            this.data = data;
        }

        T get() {
            return data;
        }
    }

    static final class Batch<E> extends Container<List<E>> {
        Batch(List<E> items) {
            super(List.copyOf(items));
        }

        int count() {
            return get().size();
        }
    }

    public static void main(String[] args) {
        Batch<String> batch = new Batch<>(List.of("A", "B"));

        List<String> items = batch.get();
        Container<List<String>> base = batch;

        System.out.println(items);
        System.out.println(batch.count());
        System.out.println(base.get().get(0));
    }
}
```

Chạy:

```bash
javac NestedForwardingDemo.java
java NestedForwardingDemo
```

`stdout`:

```text
[A, B]
2
A
```

Phân tích:

1. `Batch<E>` sở hữu `E`.
2. `extends Container<List<E>>` dùng toàn bộ kiểu `List<E>` làm type argument cho `Container<T>`.
3. Với `Batch<String>`, compiler suy ra `Container<List<String>>`.
4. `get()` vì thế có kiểu trả về `List<String>`; người gọi mới có thể dùng tiếp `get(0)` để nhận `String`.
5. `List.copyOf` làm danh sách component không sửa được và từ chối phần tử null; đây là lựa chọn triển khai của `Batch`, **không** phải quy tắc tự động của generic inheritance.

Cách đọc kiểu lồng: **bắt đầu ở type parameter cha, thay biểu thức type argument được truyền, rồi tiếp tục thay từng parameter của con**.

## 6. Nhiều type parameters: chuyển tiếp, đổi vị trí và cố định một phần

Với kiểu cha `Pair<K,V>`, lớp con có nhiều cách áp dụng type arguments:

```java
class Pair<K, V> {
    K key;
    V value;
}

// Giữ nguyên thứ tự nhưng dùng tên khác:
class Same<A, B> extends Pair<A, B> {
}

// Đổi vị trí:
class Swapped<X, Y> extends Pair<Y, X> {
}

// Cố định khóa là String, chỉ để giá trị generic:
class NamedValue<T> extends Pair<String, T> {
}

// Bọc giá trị thành danh sách:
class MultiValue<T> extends Pair<String, java.util.List<T>> {
}
```

Đây là các khai báo minh họa, không có constructor/main riêng.

Bảng ánh xạ:

| Class con | Áp dụng type arguments của `Pair<K,V>` |
| --- | --- |
| `Same<Integer,String>` | `K=Integer`, `V=String` |
| `Swapped<Integer,String>` | `K=String`, `V=Integer` |
| `NamedValue<Double>` | `K=String`, `V=Double` |
| `MultiValue<Long>` | `K=String`, `V=List<Long>` |

Sự **đổi vị trí** nằm ở `extends Pair<Y,X>`, chứ không nằm ở tên `X` hay `Y`. Không tự hiểu `K` luôn là “type parameter đầu tiên của lớp con”: thứ tự và biểu thức được viết trong danh sách type arguments mới quyết định.

### Ví dụ đầy đủ về hoán đổi type arguments

Tạo `SwappedDemo.java`:

```java
public class SwappedDemo {
    static class Pair<K, V> {
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

    static final class Swapped<X, Y> extends Pair<Y, X> {
        Swapped(Y key, X value) {
            super(key, value);
        }
    }

    public static void main(String[] args) {
        Swapped<Integer, String> item =
                new Swapped<>("code", 42);

        String key = item.key();
        Integer value = item.value();

        System.out.println(key);
        System.out.println(value);
    }
}
```

Chạy `javac SwappedDemo.java` và `java SwappedDemo`:

```text
code
42
```

Ở `Swapped<Integer,String>`, `X=Integer` và `Y=String`. Do `extends Pair<Y,X>`, phương thức kế thừa `key()` trả `String` và `value()` trả `Integer`. Viết constructor theo `Swapped(Y key, X value)` là một cách giữ sự nhất quán với mapping; về API thực tế, tên `Swapped` chỉ nên dùng khi thứ tự ngữ nghĩa có lý do.

## 7. Chuỗi kế thừa nhiều tầng: áp dụng mapping từ tầng dưới lên

Khi có ba tầng, bạn phải tổng hợp các phép thay thế kiểu. Xét:

```java
class Base<A> {
}

class Middle<B> extends Base<java.util.List<B>> {
}

class Leaf<C> extends Middle<C> {
}
```

Với `Leaf<String>`:

1. `Leaf.C = String`.
2. `Leaf<C> extends Middle<C>` → `Middle.B = String`.
3. `Middle<B> extends Base<List<B>>` → `Base.A = List<String>`.
4. `Leaf<String>` vì thế là subtype của `Middle<String>` **và** `Base<List<String>>`.

Nếu tầng tiếp theo cố định `C=Integer`, class không generic đó sẽ là subtype của `Base<List<Integer>>`.

### Chương trình ba tầng có thể chạy

Tạo `InheritanceChainDemo.java`:

```java
import java.util.List;

public class InheritanceChainDemo {
    static class Base<T> {
        private final T value;

        Base(T value) {
            this.value = value;
        }

        T get() {
            return value;
        }
    }

    static class Middle<E> extends Base<List<E>> {
        Middle(List<E> value) {
            super(List.copyOf(value));
        }
    }

    static final class Leaf<X> extends Middle<X> {
        Leaf(List<X> value) {
            super(value);
        }
    }

    public static void main(String[] args) {
        Leaf<String> leaf = new Leaf<>(List.of("one", "two"));
        Middle<String> middle = leaf;
        Base<List<String>> base = leaf;

        System.out.println(leaf.get());
        System.out.println(middle.get().size());
        System.out.println(base.get().get(1));
    }
}
```

Chạy:

```bash
javac InheritanceChainDemo.java
java InheritanceChainDemo
```

`stdout`:

```text
[one, two]
2
two
```

Mẹo đọc code: vẽ một chuỗi `Leaf<X> → Middle<X> → Base<List<X>>`; sau đó thay `X=String`. Đừng nhảy thẳng từ `Leaf<String>` lên `Base<String>` — đó là kết luận sai vì tầng `Middle` đã bọc type argument trong `List`.

## 8. Generic interface được kế thừa và chuyển tiếp như thế nào?

Java cho phép một interface kế thừa interface generic khác. Có thể giữ nguyên parameter, cố định parameter, hoặc biến đổi nó thành một biểu thức kiểu khác.

```java
interface Source<T> {
    T read();
}

interface MutableSource<E> extends Source<E> {
    void write(E value);
}

interface TextSource extends Source<String> {
}

interface BatchSource<E> extends Source<java.util.List<E>> {
}
```

Ý nghĩa:

- `MutableSource<Integer>` là subtype của `Source<Integer>`; `read()` trả `Integer` và `write(...)` nhận `Integer`.
- `TextSource` không generic nhưng mọi implementation của nó phải đáp ứng `Source<String>`.
- `BatchSource<Double>` là subtype của `Source<List<Double>>`, vì `E` được bọc thành `List<E>` trước khi truyền.

Chú ý: một interface kế thừa interface khác bằng `extends`, không dùng `implements`. Class mới sử dụng `implements` khi thực thi giao diện.

### Ví dụ interface nhiều tầng và implementation

Tạo `InterfaceForwardingDemo.java`:

```java
public class InterfaceForwardingDemo {
    interface Reader<T> {
        T read();
    }

    interface NamedReader<T> extends Reader<T> {
        String name();
    }

    static final class IntReader implements NamedReader<Integer> {
        @Override
        public Integer read() {
            return 7;
        }

        @Override
        public String name() {
            return "counter";
        }
    }

    static <T> void print(Reader<T> reader) {
        System.out.println(reader.read());
    }

    public static void main(String[] args) {
        IntReader reader = new IntReader();
        Reader<Integer> general = reader;

        System.out.println(reader.name());
        print(general);
    }
}
```

Chạy `javac InterfaceForwardingDemo.java` rồi `java InterfaceForwardingDemo`:

```text
counter
7
```

Luồng type mapping là `IntReader implements NamedReader<Integer> extends Reader<Integer>`. Method `read()` có kiểu trả về `Integer` trong implementation và khi được truy cập qua `Reader<Integer>`.

## 9. Class generic implements generic interface và extends generic class

Một class có thể vừa kế thừa superclass vừa triển khai interface, dùng **cùng một type parameter** để đảm bảo quan hệ kiểu xuyên suốt hai nhánh.

Tạo `RepositoryDemo.java`:

```java
import java.util.ArrayList;
import java.util.List;

public class RepositoryDemo {
    interface Repository<T> {
        void add(T value);
        List<T> all();
    }

    static class Named<T> {
        private final String name;

        Named(String name) {
            this.name = name;
        }

        String name() {
            return name;
        }
    }

    static final class MemoryRepository<E>
            extends Named<E> implements Repository<E> {

        private final List<E> values = new ArrayList<>();

        MemoryRepository(String name) {
            super(name);
        }

        @Override
        public void add(E value) {
            values.add(value);
        }

        @Override
        public List<E> all() {
            return List.copyOf(values);
        }
    }

    public static void main(String[] args) {
        MemoryRepository<String> repo =
                new MemoryRepository<>("languages");

        repo.add("Java");
        repo.add("Kotlin");

        Repository<String> api = repo;

        System.out.println(repo.name());
        System.out.println(api.all());
    }
}
```

Chạy:

```bash
javac RepositoryDemo.java
java RepositoryDemo
```

`stdout`:

```text
languages
[Java, Kotlin]
```

`MemoryRepository<E>` khai báo một biến kiểu `E`. Nó được dùng ở **hai nơi**: `extends Named<E>` và `implements Repository<E>`. Như vậy, khi tạo `MemoryRepository<String>`, cả contract kế thừa lẫn contract interface đều dùng `String`. `List.copyOf` trong `all()` là quyết định của implementation để trả snapshot không sửa được; type parameter `E` tự nó không áp đặt immutability hay chính sách null.

Một class cũng có thể chuyển tiếp type arguments khác nhau cho superclass và interface nếu thiết kế yêu cầu, ví dụ một type dùng cho khóa và một type cho dữ liệu; chỉ cần các chữ ký method tương thích và không tạo xung đột kế thừa.

## 10. Bound của type parameter khi chuyển tiếp

Nếu class cha yêu cầu type parameter thỏa **upper bound**, class con phải cung cấp type argument đáp ứng bound tại điểm khai báo `extends`.

Ví dụ:

```java
class NumericBox<N extends Number> {
    private final N number;

    NumericBox(N number) {
        this.number = number;
    }

    double asDouble() {
        return number.doubleValue();
    }
}
```

Một class con chuyển tiếp hợp lệ:

```java
class Measured<T extends Number> extends NumericBox<T> {
    Measured(T number) {
        super(number);
    }
}
```

`T extends Number` trên `Measured` bảo đảm mọi type argument của `Measured<T>` phù hợp yêu cầu `N extends Number` của `NumericBox`. Không thể viết một class con tổng quát `class Bad<T> extends NumericBox<T>` nếu `T` của Bad không có bound phù hợp: compiler không chứng minh được mọi `T` thỏa `Number`.

### Ví dụ đầy đủ

Tạo `BoundedInheritanceDemo.java`:

```java
public class BoundedInheritanceDemo {
    static class NumericBox<N extends Number> {
        private final N number;

        NumericBox(N number) {
            this.number = number;
        }

        double twice() {
            return number.doubleValue() * 2;
        }
    }

    static final class Measured<T extends Number>
            extends NumericBox<T> {
        Measured(T value) {
            super(value);
        }
    }

    public static void main(String[] args) {
        Measured<Integer> items = new Measured<>(5);
        Measured<Double> weight = new Measured<>(1.25);

        System.out.println(items.twice());
        System.out.println(weight.twice());
    }
}
```

Chạy `javac BoundedInheritanceDemo.java` và `java BoundedInheritanceDemo`:

```text
10.0
2.5
```

Bound **không cần viết giống hệt từng ký tự**, nhưng type argument phải thực sự thỏa tất cả các ràng buộc liên quan. Class con có thể dùng một bound **hẹp hơn** nếu cần, miễn là thỏa bound lớp cha. Ví dụ, `<T extends Integer>` hợp lệ dù `Integer` là lớp `final`: vì không có lớp con của `Integer`, `T` chỉ có thể là `Integer`. Tuy vậy, khi không cần tổng quát hóa, dùng trực tiếp `Integer` thường rõ hơn. Với interface bounds, có thể kết hợp nhiều contract bằng `&` khi cần.

### Kế thừa một type đã cố định bound

`class IntegerBox extends NumericBox<Integer>` hợp lệ, vì `Integer` kế thừa `Number`. Lớp này không cần tự khai báo biến kiểu nếu mục tiêu chỉ dùng `Integer`.

## 11. Override phương thức khi T đã được áp dụng

Khi lớp cha khai báo method sử dụng type parameter `T`, lớp con phải override **đúng hợp đồng kiểu sau khi áp dụng type argument**.

Tạo `OverrideGenericDemo.java`:

```java
public class OverrideGenericDemo {
    static class Formatter<T> {
        String format(T value) {
            return "base:" + value;
        }
    }

    static final class IntegerFormatter extends Formatter<Integer> {
        @Override
        String format(Integer value) {
            return "int:" + value;
        }
    }

    public static void main(String[] args) {
        Formatter<Integer> format = new IntegerFormatter();

        System.out.println(format.format(12));
    }
}
```

Chạy:

```bash
javac OverrideGenericDemo.java
java OverrideGenericDemo
```

`stdout`:

```text
int:12
```

- `Formatter<T>` khai báo `format(T)`.
- `IntegerFormatter extends Formatter<Integer>` chuyên biệt hóa `T = Integer`.
- `format(Integer)` là override hợp lệ ở source level. Dùng `@Override` giúp compiler phát hiện chữ ký không tương thích.
- Biến `Formatter<Integer>` giữ đối tượng `IntegerFormatter`, nên dynamic dispatch gọi implementation ở subclass.

**Sai lầm phổ biến:** viết `format(Object)` trong `IntegerFormatter` và nghĩ đó chắc chắn override `format(T)` sau khi erasure. Ở source level, khi superclass được nhìn qua `Formatter<Integer>`, chữ ký cần đáp ứng là `format(Integer)`; một `format(Object)` khai báo thêm có thể không override như tác giả mong đợi, thậm chí gây name clash tùy chữ ký. Luôn dùng `@Override` và kiểm tra compiler thay vì đoán dựa trên bytecode bị xóa kiểu.

### Return type cũng được chuyên biệt hóa

Nếu superclass có `T get()` và subclass extends `Parent<String>`, subclass có thể override bằng `String get()`. Nếu generic method hoặc superclass contract có kiểu trả về khác, Java cho phép covariant return theo quy tắc override áp dụng, nhưng không được trả kiểu không tương thích.

Ví dụ `Object get()` không phải là cách override hợp lệ của `String get()` khi hợp đồng của subtype yêu cầu trả `String`; kiểu trả về bị mở rộng sai hướng.

## 12. Bridge method: vì sao JVM vẫn gọi đúng override sau erasure?

Phần này giải thích cơ chế phía sau, không bắt buộc để viết các generic class thường ngày.

Ở mã nguồn, `Formatter<T>.format(T)` được chuyên biệt hóa ở subclass thành `format(Integer)`. Sau **type erasure**, method của superclass có dạng gần `format(Object)` trong bytecode, trong khi method do subclass viết nhận `Integer`. Để giữ quan hệ override/dynamic dispatch, compiler có thể tạo một **bridge method** tổng hợp `format(Object)` ở subclass, thực hiện cast và chuyển lời gọi tới `format(Integer)`.

Nhìn từ Java source, bạn chỉ cần override đúng kiểu. Không nên tự viết bridge method.

### Quan sát bridge bằng reflection

Tạo `BridgeMethodDemo.java`:

```java
import java.lang.reflect.Method;

public class BridgeMethodDemo {
    static class Parent<T> {
        T echo(T input) {
            return input;
        }
    }

    static final class Child extends Parent<String> {
        @Override
        String echo(String input) {
            return input.toUpperCase();
        }
    }

    public static void main(String[] args) {
        Parent<String> view = new Child();
        System.out.println(view.echo("java"));

        boolean bridgeFound = false;
        for (Method method : Child.class.getDeclaredMethods()) {
            if (method.getName().equals("echo") && method.isBridge()) {
                bridgeFound = true;
            }
        }
        System.out.println(bridgeFound);
    }
}
```

Chạy:

```bash
javac BridgeMethodDemo.java
java BridgeMethodDemo
```

`stdout` với `javac` từ JDK 17:

```text
JAVA
true
```

`Child` override `echo(String)`. Trong bytecode của subclass do `javac` tạo, bridge `echo(Object)` giữ dispatch đúng qua reference `Parent<String>`. Dùng `isBridge()` để nhận biết method do compiler sinh; ví dụ chỉ in một Boolean, tránh phụ thuộc thứ tự trả về của `getDeclaredMethods()`.

Nếu muốn khảo sát thủ công, có thể chạy `javap -p -c BridgeMethodDemo$Child` trong môi trường shell phù hợp; cú pháp quote tên class chứa `$` phụ thuộc shell. Bridge là hệ quả của erasure, không phải một type parameter mới cần chuyển tiếp trong mã nguồn.

## 13. Generic method có type parameter riêng: không nhầm với kế thừa T

Một class generic có thể kế thừa type parameter từ cha **và** khai báo generic method độc lập.

```java
class Repository<T> {
    T get() {
        return null;
    }

    <R> R convert(java.util.function.Function<? super T, ? extends R> fn) {
        return fn.apply(get());
    }
}
```

Ở đây:

- `T` thuộc class và được áp dụng khi subclass extends `Repository<String>`.
- `R` thuộc riêng phương thức `convert`. Mỗi lời gọi có thể suy luận kiểu `R` khác.
- `Function<? super T, ? extends R>` liên kết dữ liệu `T` đang được lưu với kiểu kết quả `R`.
- Nếu lớp con là `StringRepository extends Repository<String>` thì `convert(String::length)` có thể trả `Integer`, dù class con không generic.

**Đừng tự khai báo lại `<T>` trong method nếu muốn dùng T của class.** Ví dụ:

```java
class Box<T> {
    private T value;

    // <T> void set(T x) { value = x; } // T ở method che T của class.
    void set(T x) {
        value = x;
    }
}
```

Tình huống này thuộc **scope/shadowing**, không phải chuyển tiếp type parameter giữa parent và child. Nhưng nó thường xuất hiện trong cùng codebase nên cần phân biệt.

## 14. Chuyển tiếp type arguments không biến generics thành hiệp biến

Khi khai báo `Child<T> extends Parent<T>`, `Child<Integer>` là subtype của **`Parent<Integer>`**. Nó không tự trở thành subtype của `Parent<Number>` chỉ vì Integer extends Number: những cách tham số hóa của cùng generic type mặc định **invariant**.

```java
class Parent<T> {}
class Child<T> extends Parent<T> {}

// Trong một phương thức:
Child<Integer> child = new Child<>();
Parent<Integer> exact = child;
Parent<? extends Number> readView = child;
// Parent<Number> wrong = child; // Không hợp lệ.
```

`Parent<? extends Number>` phù hợp cho API **quan sát** một giá trị Number nếu Parent có getter trả T, nhưng không cho phép tùy tiện ghi Number vào một vùng có kiểu phần tử thực tế chưa biết. Không dùng raw type/cast unchecked để biến `Parent<Integer>` thành `Parent<Number>`: làm vậy phá quan hệ type safety mà forwarding vừa bảo toàn.

## 15. Không được kế thừa cùng generic interface với hai arguments bất tương thích

Một class không thể đồng thời trở thành subtype của **cùng một generic interface** với hai type arguments khác nhau thông qua hai nhánh kế thừa.

Ví dụ cố ý sai:

```java
interface Consumer<T> {
    void accept(T item);
}

class TextConsumer implements Consumer<String> {
    public void accept(String item) {}
}

// Không hợp lệ: một đường kế thừa Consumer<String>,
// đường kia lại đòi Consumer<Integer>.
// class Bad extends TextConsumer implements Consumer<Integer> {
//     public void accept(Integer item) {}
// }
```

Đoạn code có khai báo `TextConsumer` hợp lệ nhưng `Bad` được comment vì gây lỗi biên dịch. Một subtype không thể có đồng thời hai instantiations phân biệt của cùng generic interface trong supertype hierarchy.

Một dạng xung đột khác:

```java
interface StringSource extends java.util.function.Supplier<String> {}
interface IntegerSource extends java.util.function.Supplier<Integer> {}

// Không hợp lệ:
// class Both implements StringSource, IntegerSource {
//     ...
// }
```

Dù có thể viết các phương thức nhận/đưa dữ liệu khác loại riêng, hai nhánh `Supplier<String>` và `Supplier<Integer>` đều mô tả cùng generic interface bị kế thừa với type arguments khác nhau; Java từ chối.

Tương tự, đừng kỳ vọng một method overload phân biệt hai type arguments của cùng raw generic type:

```java
// Sai do cùng erasure:
// void use(java.util.List<String> xs) {}
// void use(java.util.List<Integer> xs) {}
```

Hai chữ ký đều xóa thành tham số kiểu `List`, gây name clash.

## 16. Type erasure và raw type: giữ quan hệ ở compile time

Generic inheritance được kiểm tra ở mã nguồn, nhưng JVM không tạo riêng những class như `ChildOfParentString` chỉ vì `extends Parent<String>`. Java generics chủ yếu dùng **type erasure**:

- Type parameter không có bound thường xóa về `Object`.
- Type parameter có bound sẽ xóa về bound đầu tiên.
- Compiler có thể chèn casts và bridge methods.
- Type arguments có thể còn xuất hiện dưới dạng **metadata generic của khai báo**; điều đó không có nghĩa mỗi instance mang tham số kiểu được kiểm tra đầy đủ ở runtime.

### Tránh extends raw superclass

```java
class Box<T> {
    T value;
}

// Không nên:
class RawChild extends Box {
}
```

Raw type `Box` làm mất thông tin `T` ở subtype declaration. Thao tác trên field/method có thể trở nên yếu kiểm tra kiểu, phát cảnh báo và đưa `ClassCastException` tới nơi đọc dữ liệu.

Sửa bằng một trong hai hướng, tùy API mong muốn:

```java
class TypedChild<U> extends Box<U> {
}

class StringChild extends Box<String> {
}
```

- `TypedChild<U>` chuyển tiếp `U`.
- `StringChild` cố định type argument.
- Cả hai giữ quan hệ kiểu có thể được compiler kiểm tra.

Không dùng `@SuppressWarnings("unchecked")` để che warning từ raw superclass nếu có thể biểu diễn quan hệ thực bằng type arguments.

### Ranh giới runtime

Một biến `Parent<String>` không đảm bảo tuyệt đối dữ liệu luôn là String nếu có raw alias, unchecked casts hoặc nguồn bên ngoài không kiểm chứng. Generics bảo vệ phần lớn thao tác **khi các API tuân thủ kiểu tĩnh**. Với dữ liệu deserialize hoặc reflection, cần validation runtime thích hợp; type arguments không phải hệ thống kiểm tra nghiệp vụ độc lập.

## 17. Reflection: nhìn thấy superclass type argument đã khai báo

Thông tin generic trong **khai báo superclass** thường có thể được đọc bằng reflection, dù runtime của một object không mang specialized class riêng cho mọi type argument.

Tạo `GenericSuperclassDemo.java`:

```java
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

public class GenericSuperclassDemo {
    static class Parent<T> {
    }

    static final class StringChild extends Parent<String> {
    }

    static class ForwardChild<U> extends Parent<U> {
    }

    public static void main(String[] args) {
        Type fixed = StringChild.class.getGenericSuperclass();
        Type forwarded = ForwardChild.class.getGenericSuperclass();

        System.out.println(fixed.getTypeName());
        System.out.println(forwarded.getTypeName());
        System.out.println(fixed instanceof ParameterizedType);
    }
}
```

Chạy:

```bash
javac GenericSuperclassDemo.java
java GenericSuperclassDemo
```

`stdout` trên JDK 17, với các class lồng như ví dụ:

```text
GenericSuperclassDemo$Parent<java.lang.String>
GenericSuperclassDemo$Parent<U>
true
```

Giải thích:

- `StringChild` ghi rõ superclass `Parent<String>` nên generic superclass metadata thể hiện `String`.
- `ForwardChild<U>` truyền một **type variable U** cho `Parent`; metadata của declaration biểu diễn `Parent<U>`.
- `getGenericSuperclass()` trả mô tả `Type`; khi chứa type arguments, thường là `ParameterizedType`.
- Đây là cách đọc **khai báo**, không phải suy ra type argument runtime của mọi instance qua `getClass()`.

Ví dụ `new ForwardChild<String>().getClass()` trả `ForwardChild.class`. Kiểu `String` không được lưu thành một subclass runtime mới. Nếu framework cần biết type arguments sau nhiều tầng kế thừa, nó phải phân tích generic metadata kết hợp mapping type variables qua từng tầng, hoặc yêu cầu thêm thông tin type tại runtime.

## 18. Những mô hình kế thừa generic thường dùng trong Java Core

### 18.1. Generic base + specialized subclass

Mẫu:

```java
class Response<T> {
    // Holds T
}

class TextResponse extends Response<String> {
}
```

Phù hợp khi subtype `TextResponse` có hành vi, tên miền hoặc invariant đặc thù. Nếu không thêm giá trị thiết kế, có thể dùng trực tiếp `Response<String>` để tránh quá nhiều lớp.

### 18.2. Generic base + forwarding generic subclass

Mẫu:

```java
class BaseRepository<ID, E> {
}

class CachedRepository<K, V> extends BaseRepository<K, V> {
}
```

`CachedRepository<Long,User>` trở thành subtype của `BaseRepository<Long,User>`. Mẫu này hữu ích khi lớp con thêm caching, logging hay measurement nhưng **không thay hợp đồng kiểu của dữ liệu**.

### 18.3. Generic interface + implementation cố định kiểu

Mẫu:

```java
interface Parser<T> {
    T parse(String input);
}

class IntegerParser implements Parser<Integer> {
    @Override
    public Integer parse(String input) {
        return Integer.parseInt(input);
    }
}
```

`Parser<Integer>` cam kết phương thức trả `Integer`; implementation có thể dựa vào `Integer.parseInt`. Đây là cách rõ ràng để dùng cùng một interface chung với nhiều kiểu kết quả khác nhau.

### 18.4. Interface kế thừa có thay đổi cấu trúc type argument

Mẫu:

```java
interface Reader<T> {
    T read();
}

interface PageReader<T> extends Reader<java.util.List<T>> {
}
```

`PageReader<String>` là một `Reader<List<String>>`. Sự bọc `List<T>` chuyển ý nghĩa của `read()` từ “đọc một T” thành “đọc một danh sách T”, trong khi vẫn tuân thủ generic contract của interface cha.

### 18.5. Kế thừa generic với multiple bounds

Mẫu:

```java
interface Named {
    String name();
}

class Registry<T extends Named> {
}

class SortedRegistry<U extends Named & Comparable<U>>
        extends Registry<U> {
}
```

Lớp con thêm bound `Comparable<U>` đồng thời vẫn thỏa yêu cầu `Named` của cha. Sự tồn tại của bound mới không tự làm superclass trở thành một loại generic khác; `Registry.T` vẫn được ánh xạ từ `SortedRegistry.U`.

Không nên thêm bound chỉ để chữ ký phức tạp. Bound phải phục vụ một thao tác mà compiler cần chứng minh trong subclass hoặc API.

## 19. Các lỗi thường gặp và cách sửa

| Sai lầm | Vì sao sai | Cách sửa |
| --- | --- | --- |
| Coi `class Child<T> extends Parent<T>` là dùng **cùng một khai báo T** ở hai class | Mỗi class khai báo biến kiểu của riêng nó; mệnh đề `extends` tạo mapping | Ghi rõ `Parent.T ← Child.T` |
| Viết `class Child<T> extends Parent` | Dùng raw superclass, mất quan hệ kiểu | Viết `extends Parent<T>` hoặc cố định `Parent<String>` |
| Nghĩ `Child<String>` luôn là `Parent<String>` | Subclass có thể truyền `List<T>` hoặc đảo vị trí | Đọc **type arguments trong `extends`** |
| Gán `Child<Integer>` cho `Parent<Number>` | Generic invariance | Giữ `Parent<Integer>` hoặc dùng view `Parent<? extends Number>` nếu chỉ đọc |
| Không lặp bound khi truyền vào cha có bound | Compiler không chứng minh subtype đáp ứng bound | Khai báo `<T extends Number>` hay type argument cụ thể thỏa bound |
| Viết override bằng `Object` vì nghĩ T đã xóa | Source-level override phải tương thích sau áp dụng type arguments | Dùng `@Override` với kiểu đã chuyên biệt hóa |
| Tưởng constructor được kế thừa như method | Constructor không được thừa kế | Khai báo constructor con và gọi `super(...)` |
| Mong implement cùng interface generic theo hai kiểu khác nhau | Hai instantiations không thể đồng thời kế thừa vào cùng subtype | Tách abstraction hoặc thiết kế một mapping nhất quán |
| Nghĩ class generic runtime có instance riêng cho mỗi T | Java generics dùng erasure | Phân biệt compiler type argument và runtime class |
| Nhầm `<R>` của generic method với `T` của class | Hai type parameters thuộc scope khác nhau | Dùng tên theo vai trò và theo dõi nơi khai báo |
| Ép kiểu unchecked để vượt lỗi inheritance | Có thể gây heap pollution, ClassCastException | Sửa mệnh đề extends/implements và hợp đồng kiểu |
| Coi `List<Integer>` là subtype `List<Number>` vì phần tử kế thừa | Generic invariance | Dùng wildcard theo chiều đọc/ghi |

## 20. Cách phân tích một cây kế thừa generic bằng giấy hoặc IDE

Khi gặp một class thực tế có nhiều tầng, thực hiện sáu bước:

1. **Ghi các type parameters tại mỗi declaration.** Ví dụ `Base<K,V>`, `Middle<T>`, `Leaf<E>`.
2. **Ghi nguyên biểu thức ở extends/implements.** Đừng lược `List<E>` thành `E` hoặc đảo vị trí type arguments.
3. **Lập mapping từ con sang cha.** Ví dụ `Middle<T> extends Base<String,List<T>>` nghĩa là `Base.K=String`, `Base.V=List<T>`.
4. **Áp dụng type argument của caller.** Với `Leaf<Integer>` suy ra từng bước đến `Base<String,List<Integer>>`.
5. **Kiểm tra method và field kế thừa theo mapping.** Một method `V get()` của Base trả `List<Integer>` ở Leaf này, không trả `Integer`.
6. **Đọc thêm hợp đồng override, bound và runtime.** Kiểu trả về tĩnh đúng chưa đủ: method có side effect gì, null có được phép không, dữ liệu có mutable không?

Ví dụ sơ đồ:

```text
Base<K, V>
    ▲
    | extends Base<String, List<T>>
Middle<T>
    ▲
    | extends Middle<E>
Leaf<E>

Leaf<Integer>
    -> Middle<Integer>
    -> Base<String, List<Integer>>
```

Nếu trên IDE bạn thấy `Base<String,List<Integer>>` là một supertype của `Leaf<Integer>`, đó chính là kết quả của phép chuyển tiếp từng tầng. Chỉ cần tìm type argument expression ở mỗi cạnh là có thể tự suy luận, không cần đoán.

## 21. Bài tập tự kiểm tra có lời giải

### Bài 1 — Truyền nguyên type parameter

Cho:

```java
class P<T> {
    T get() { return null; }
}

class C<U> extends P<U> {
}
```

Hỏi `C<String>` có `get()` trả kiểu gì tại compile time? Có là subtype của `P<String>` không?

**Đáp án:** `String`; có, vì `P.T ← C.U` rồi `U=String`. Việc `get()` hiện trả null trong thân minh họa không thay kiểu tĩnh.

### Bài 2 — Cố định type argument

Cho `class Text extends P<String> {}`. Hỏi `Text` còn là generic class không và có dùng `P<Integer>` làm kiểu biến để trỏ tới `Text` được không?

**Đáp án:** `Text` không generic; nó kế thừa `P<String>`. Không thể gán `Text` vào `P<Integer>` vì các generic instantiations đó không tương thích.

### Bài 3 — Chuyển tiếp biểu thức type

```java
class Root<X> {}
class Middle<Y> extends Root<java.util.Map<String, Y>> {}
class End<Z> extends Middle<java.util.List<Z>> {}
```

Hãy suy luận kiểu Root của `End<Integer>`.

**Đáp án:** `End<Integer>` → `Middle<List<Integer>>` → `Root<Map<String,List<Integer>>>`. Không được bỏ lớp `List` của ánh xạ tầng End hay lớp `Map` của Middle.

### Bài 4 — Đổi thứ tự hai parameters

```java
class Pair<K,V> {}
class Flip<A,B> extends Pair<B,A> {}
```

Hỏi `Flip<String,Long>` là subtype của `Pair<String,Long>` hay `Pair<Long,String>`?

**Đáp án:** `Pair<Long,String>`, vì `A=String`, `B=Long` rồi cha được áp dụng `K=B=Long` và `V=A=String`.

### Bài 5 — Interface forwarding

```java
interface Source<T> { T read(); }
interface Many<E> extends Source<java.util.List<E>> {}
```

`Many<Double>.read()` có kiểu trả về nào?

**Đáp án:** `List<Double>`. Không phải `Double`, vì `Source.T ← List<E>`.

### Bài 6 — Bound

```java
class Numeric<T extends Number> {}
// class GenericBad<U> extends Numeric<U> {}
class GenericGood<U extends Number> extends Numeric<U> {}
```

Vì sao `GenericBad` gây lỗi? Vì `U` không có bound bảo đảm `U` là subtype của `Number`. `GenericGood` khẳng định quan hệ đó trong declaration.

### Bài 7 — Override

```java
class Base<T> {
    T echo(T x) { return x; }
}

class TextBase extends Base<String> {
    @Override
    String echo(String x) { return x.toUpperCase(); }
}
```

Vì sao method con override hợp lệ? Vì superclass đã được áp dụng `T=String`. Compiler có thể tạo bridge method vì erasure để giữ dispatch runtime.

### Bài 8 — Invariance

Cho `class C<T> extends P<T> {}` và `C<Integer> c = ...`. Phân biệt:

- `P<Integer> exact = c;` — **hợp lệ**.
- `P<Number> wider = c;` — **không hợp lệ**.
- `P<? extends Number> readable = c;` — **hợp lệ**.

Lý do: subtype relation đến `P<Integer>` được khai báo trực tiếp, còn `P<Number>` không có quan hệ subtype qua generic argument; wildcard upper bound phù hợp kiểu dữ liệu của `c`.

### Bài 9 — Tự thiết kế

Thiết kế `interface Loader<T> { T load(); }` và `class ListLoader<E> implements Loader<List<E>>`. Viết implementation lưu một `List<E>`, trả snapshot qua `List.copyOf`. Thử `ListLoader<String>` và kiểm tra `Loader<List<String>>` nhận được. Giải thích phần nào do generic mapping, phần nào do lựa chọn immutable snapshot.

### Bài 10 — Phát hiện raw type

Cho `class Child extends Parent {}` trong đó `Parent<T>` generic. Hãy viết hai phương án sửa:

1. `class Child<U> extends Parent<U>` — tiếp tục generic.
2. `class Child extends Parent<String>` — cố định kiểu.

Giải thích vì sao chọn phương án nào phụ thuộc hợp đồng API, không phải vì một cú pháp luôn tốt hơn.

## 22. Tóm tắt các quy tắc cần nhớ

- `class Child<U> extends Parent<U>`: **chuyển tiếp** type parameter của con để tham số hóa cha.
- `class Child extends Parent<String>`: **cố định** type argument ở superclass; con không nhất thiết generic.
- `class Child<T> extends Parent<List<T>>`: **biến đổi biểu thức type argument**; với `Child<String>`, kiểu cha là `Parent<List<String>>`.
- `class Flip<A,B> extends Pair<B,A>`: **đổi vị trí** hai type arguments của cha.
- `interface Child<E> extends Parent<E>`: generic interface cũng có thể chuyển tiếp type parameter.
- `class Impl<E> implements Service<E>`: class triển khai một generic interface theo mapping chỉ định.
- Qua nhiều tầng, phép thay thế được **hợp thành**: đọc từng cạnh `extends`/`implements` từ subtype lên supertype.
- Mọi bound của supertype phải được thỏa ở nơi truyền type argument. `@Override` giúp xác minh method sau khi chuyên biệt hóa.
- `Child<Integer>` có thể là `Parent<Integer>`, nhưng **không tự là `Parent<Number>`**. Dùng wildcard khi cần biểu diễn quan hệ đọc/ghi linh hoạt.
- Java generics dùng erasure: quan hệ kiểu là bảo đảm compile time; bridge method hoặc casts có thể giữ tương thích runtime, chứ không tạo JVM class chuyên biệt cho mỗi `T`.

Hãy luôn tự hỏi: **“Type parameter này được khai báo ở class nào, biểu thức nào được truyền qua cạnh kế thừa, và cuối cùng những method kế thừa có kiểu gì ở điểm sử dụng?”** Trả lời chính xác ba câu đó là bạn có thể đọc, viết và kiểm tra một cây kế thừa generic trong Java Core mà không dựa vào các cast thiếu an toàn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="generic-inheritance-003">Thêm tham số kiểu mới ở lớp con</a>

<details>
<summary>Xem chi tiết</summary>

# Thêm tham số kiểu mới ở lớp con khi kế thừa Generics trong Java

Một lớp con của generic class **có thể khai báo nhiều tham số kiểu hơn lớp cha**. Lớp con quyết định tham số kiểu nào được truyền cho lớp cha và tham số kiểu nào được dùng cho dữ liệu/hành vi bổ sung của chính nó. Chẳng hạn, nếu `Box<T>` chứa một giá trị `T`, lớp con `TaggedBox<T, M>` có thể kế thừa giá trị đó và thêm một dấu nhãn (metadata) thuộc kiểu `M`.

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

class TaggedBox<T, M> extends Box<T> {
    private final M marker;

    TaggedBox(T value, M marker) {
        super(value);
        this.marker = marker;
    }

    M marker() {
        return marker;
    }
}
```

Đây là **hai định nghĩa lớp minh họa**, chưa có phương thức `main`. Quan hệ cốt lõi nằm ở dòng `class TaggedBox<T, M> extends Box<T>`:

- `<T, M>` khai báo **hai type parameters của lớp con**. `T` là kiểu giá trị, `M` là kiểu nhãn do lớp con bổ sung.
- `extends Box<T>` truyền **chỉ `T`** cho type parameter của lớp cha. `Box<T>` không tự biết `M`.
- `super(value)` gọi constructor của lớp cha để thiết lập trạng thái `T`.
- `marker` và `marker()` là phần mở rộng gắn với type parameter `M` riêng của lớp con.
- `TaggedBox<String, Integer>` là subtype của `Box<String>` vì nó `extends Box<T>` với `T = String`; nó không phải subtype của `Box<Integer>`.

Thiết kế này rất hữu ích khi một subtype cần bổ sung thông tin như mã định danh, nhãn, kiểu đơn vị đo, kiểu trạng thái hoặc chính sách xử lý, trong khi vẫn dùng lại hợp đồng chung của lớp cha.

## 1. Cú pháp tổng quát: lớp con khai báo kiểu rồi ánh xạ lên lớp cha

Khuôn dạng thường gặp:

```java
class Parent<A> {
    // Thành viên dựa trên A
}

class Child<A, B> extends Parent<A> {
    // Có thể dùng A được kế thừa theo quan hệ với Parent<A>
    // và thêm trạng thái/hành vi dựa trên B
}
```

Ví dụ trên chỉ minh họa **cú pháp kế thừa**, chưa có chức năng của cặp `A`, `B`. Quan trọng là:

1. Danh sách `<A, B>` sau tên `Child` **khai báo** type parameters của `Child`.
2. Danh sách `<A>` sau `Parent` trong `extends Parent<A>` là **các type arguments** truyền cho lớp cha.
3. Trình biên dịch áp dụng ánh xạ đó để xác định kiểu của các thành viên `Parent` khi nhìn từ `Child`.
4. Tham số kiểu thêm ở lớp con `B` không bắt buộc phải có vai trò nào trong lớp cha; nó có thể chỉ phục vụ trường và method mới.

`extends` mô tả **quan hệ kế thừa lớp**, còn các dấu `<...>` mô tả **tham số hóa kiểu**. Không có quy tắc rằng lớp con phải chỉ dùng đúng số tham số kiểu của lớp cha, cũng không bắt buộc giữ nguyên tên hay thứ tự type parameter.

Một ví dụ có ánh xạ khác:

```java
class Parent<T> { }

class Child<K, V> extends Parent<V> { }
```

Lớp con này gửi **`V`** cho `T` của lớp cha, để `K` cho chức năng riêng của lớp con. Với `Child<String, Integer>`, supertype được tham số hóa là `Parent<Integer>`, **không phải `Parent<String>`**. Tên `K`, `V` chỉ là quy ước: quan hệ thực sự do `extends Parent<V>` quyết định.

## 2. Lớp con giữ kiểu cha và thêm kiểu mới

Giả sử lớp cha quản lý phần tử `T`; lớp con cần thêm metadata thuộc kiểu `M`:

```java
class Item<T> {
    private final T data;

    Item(T data) {
        this.data = data;
    }

    T data() {
        return data;
    }
}

class LabeledItem<T, M> extends Item<T> {
    private final M label;

    LabeledItem(T data, M label) {
        super(data);
        this.label = label;
    }

    M label() {
        return label;
    }
}
```

Hai lớp này là **định nghĩa minh họa thay thế**, không đặt trùng tên với các lớp cùng tên trong một package nếu đã có. Khi dùng trong một method:

```java
LabeledItem<String, Integer> item =
        new LabeledItem<>("book", 101);

String value = item.data();
Integer label = item.label();
```

Nhờ `extends Item<T>`, kiểu trả về của phương thức kế thừa `data()` là `String` tại `LabeledItem<String, Integer>`, không phải `Object`. Còn `label()` có kiểu `Integer` nhờ tham số kiểu mới `M`.

Nếu bạn viết `LabeledItem<String, Integer>` rồi truyền một `Boolean` vào vị trí `M`, compiler có thể báo lỗi ngay. Bạn cũng không cần cast một nhãn `Integer` về đúng kiểu sau khi đọc.

Điều kiện tiên quyết: các type arguments truyền cho class/interface cha phải đáp ứng **mọi upper bound** trong khai báo của cha. Ví dụ nếu cha là `class Parent<T extends Number>`, lớp con không thể đơn giản viết `class Child<T, M> extends Parent<T>` với `T` hoàn toàn không bound; phải bảo đảm `T` thỏa `Number`.

## 3. Lớp con có thể cố định kiểu cha nhưng vẫn thêm kiểu riêng

Không phải tham số kiểu của lớp con nào cũng cần chuyển cho lớp cha. Ví dụ:

```java
class Base<T> {
    private final T value;

    Base(T value) {
        this.value = value;
    }

    T get() {
        return value;
    }
}

class TextMetadata<M> extends Base<String> {
    private final M metadata;

    TextMetadata(String text, M metadata) {
        super(text);
        this.metadata = metadata;
    }

    M metadata() {
        return metadata;
    }
}
```

Đây là **hai lớp minh họa độc lập**. `TextMetadata<M>` có đúng một type parameter `M` của nó, nhưng kế thừa `Base<String>` với **type argument cố định `String`**, không gửi `M` lên cha.

Khi gọi:

```java
TextMetadata<Long> entry = new TextMetadata<>("hello", 99L);

String text = entry.get();
Long meta = entry.metadata();
```

`get()` luôn trả về `String` cho mọi `TextMetadata<M>`, còn `metadata()` thay đổi kiểu theo `M`. Bởi vậy:

- `TextMetadata<Long>` là subtype của `Base<String>`.
- `TextMetadata<Boolean>` cũng là subtype của `Base<String>`.
- `TextMetadata<Long>` không tự nhiên trở thành subtype của `Base<Long>`.

Đây là cách thiết kế tốt khi subtype đại diện cho **một loại chuyên biệt của phần dữ liệu cha**, nhưng phần dữ liệu bổ sung vẫn cần tổng quát.

## 4. Ví dụ Java 17 chạy độc lập: ba cách thêm tham số kiểu ở lớp con

Lưu toàn bộ chương trình sau thành **`ExtraSubclassTypeParameterDemo.java`**:

```java
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public class ExtraSubclassTypeParameterDemo {
    static class Box<T> {
        private final T value;

        protected Box(T value) {
            this.value = value;
        }

        public T get() {
            return value;
        }

        public String describe() {
            return "Box(" + value + ")";
        }
    }

    static final class TaggedBox<T, M> extends Box<T> {
        private final M marker;

        private TaggedBox(T value, M marker) {
            super(value);
            this.marker = marker;
        }

        static <V, L> TaggedBox<V, L> of(V value, L marker) {
            return new TaggedBox<>(value, marker);
        }

        public M marker() {
            return marker;
        }

        @Override
        public String describe() {
            return "Tagged(" + get() + ", " + marker + ")";
        }

        public <U> TaggedBox<U, M> mapValue(
                Function<? super T, ? extends U> mapper) {
            Objects.requireNonNull(mapper, "mapper");
            return TaggedBox.of(mapper.apply(get()), marker);
        }
    }

    static final class TextTagged<M> extends Box<String> {
        private final M metadata;

        TextTagged(String text, M metadata) {
            super(text);
            this.metadata = metadata;
        }

        M metadata() {
            return metadata;
        }
    }

    interface Identifiable<ID> {
        ID id();
    }

    static final class Identified<T, ID>
            extends Box<T> implements Identifiable<ID> {
        private final ID id;

        Identified(T value, ID id) {
            super(value);
            this.id = id;
        }

        @Override
        public ID id() {
            return id;
        }
    }

    static final class NumericTagged<N extends Number, M>
            extends Box<N> {
        private final M marker;

        NumericTagged(N value, M marker) {
            super(value);
            this.marker = marker;
        }

        double twice() {
            return get().doubleValue() * 2;
        }

        M marker() {
            return marker;
        }
    }

    public static void main(String[] args) {
        TaggedBox<String, Integer> tagged = TaggedBox.of("Java", 7);
        System.out.println("tagged: " + tagged.describe());
        System.out.println("types: " + tagged.get().toUpperCase()
                + "/" + (tagged.marker() + 1));

        Box<String> parentView = tagged;
        System.out.println("upcast: " + parentView.get());
        System.out.println("dispatch: " + parentView.describe());

        TaggedBox<Integer, Integer> mapped =
                tagged.mapValue(String::length);
        System.out.println("mapped: " + mapped.describe());

        TextTagged<Long> fixed = new TextTagged<>("Hi", 99L);
        System.out.println("fixed: " + fixed.get()
                + "/" + fixed.metadata());

        Identified<List<Integer>, String> identified =
                new Identified<>(List.of(2, 4, 6), "batch-1");
        System.out.println("identified: " + identified.id()
                + "=" + identified.get());

        Identifiable<String> interfaceView = identified;
        System.out.println("interface: " + interfaceView.id());

        NumericTagged<Integer, String> numeric =
                new NumericTagged<>(8, "units");
        System.out.println("numeric: " + numeric.twice()
                + "/" + numeric.marker());

        List<Box<String>> boxes = List.of(tagged, fixed);
        System.out.println("boxes: " + boxes.size());
    }
}
```

Biên dịch và chạy với JDK 17 trở lên:

```bash
javac ExtraSubclassTypeParameterDemo.java
java ExtraSubclassTypeParameterDemo
```

Stdout theo đúng thứ tự:

```text
tagged: Tagged(Java, 7)
types: JAVA/8
upcast: Java
dispatch: Tagged(Java, 7)
mapped: Tagged(4, 7)
fixed: Hi/99
identified: batch-1=[2, 4, 6]
interface: batch-1
numeric: 16.0/units
boxes: 2
```

### Phân tích ví dụ

**Trường hợp A — `TaggedBox<T, M> extends Box<T>`.** Lớp con giữ nguyên kiểu giá trị từ cha qua `T`, rồi bổ sung nhãn `M`. Khi `T = String`, `get()` trả `String`; khi `M = Integer`, `marker()` trả `Integer`. Biểu thức `tagged.marker() + 1` hợp lệ nhờ unboxing wrapper.

**Trường hợp B — upcasting giữ đúng kiểu cha.** `Box<String> parentView = tagged;` hợp lệ vì `TaggedBox<String, Integer>` kế thừa `Box<String>`. Qua `parentView`, compiler vẫn biết `get()` trả `String`, nhưng không cho gọi trực tiếp `marker()`, vì phương thức đó không được khai báo trên `Box<String>`. Lời gọi `parentView.describe()` vẫn thực hiện **dynamic dispatch** tới override của `TaggedBox`, nên kết quả là `Tagged(Java, 7)`.

**Trường hợp C — generic method riêng trong generic subclass.** `mapValue` khai báo `<U>` thuộc **method**, độc lập với `T` và `M` thuộc **lớp**. Nó biến giá trị từ kiểu `T` sang `U` nhưng giữ nhãn `M`. Vì vậy `TaggedBox<String, Integer>` biến thành `TaggedBox<Integer, Integer>` khi ánh xạ `String::length`. Wildcard `Function<? super T, ? extends U>` cho phép function tiêu thụ kiểu rộng hơn `T` và tạo kết quả thuộc subtype thích hợp của `U`.

**Trường hợp D — `TextTagged<M> extends Box<String>`.** Kiểu dữ liệu của lớp cha được **cố định** là `String`; `M` chỉ dành cho metadata của subclass. `TextTagged<Long>` vẫn là một `Box<String>`, không phải `Box<Long>`.

**Trường hợp E — `Identified<T, ID> extends Box<T> implements Identifiable<ID>`.** Lớp con chuyển `T` cho superclass và `ID` cho interface. Một subclass có thể mở rộng **một class** và thực thi **nhiều interface**; các type arguments ở mỗi quan hệ được kiểm tra theo khai báo tương ứng. Ở đây `Identified<List<Integer>, String>` lưu danh sách số nguyên và mã định danh chuỗi; qua `Identifiable<String>`, method `id()` vẫn trả `String`.

**Trường hợp F — `NumericTagged<N extends Number, M>`.** Subclass áp đặt bound `N extends Number` để dùng `doubleValue()`. Bound `Number` cũng bảo đảm `N` thích hợp làm type argument của `Box<N>`. `NumericTagged<Integer, String>` hợp lệ, nhưng `NumericTagged<String, Integer>` sẽ không biên dịch.

**Trường hợp G — collection của kiểu cha.** `List<Box<String>> boxes = List.of(tagged, fixed);` hợp lệ vì **cả hai đối tượng** có supertype `Box<String>` và ngữ cảnh đích phù hợp. Điều này không đồng nghĩa có thể trực tiếp gán cả một `List<TaggedBox<String, Integer>>` sang `List<Box<String>>`: Java generic collection vẫn **invariant**, như phần dưới.

## 5. Ánh xạ type parameter giữa cha và con không nhất thiết cùng tên

Trình biên dịch không dựa vào tên `T`, `U`, `K`, `V` để nối kiểu cha–con. Nó dựa vào **type argument biểu thức trong mệnh đề `extends`**.

```java
class Parent<P> {
    P get() {
        return null;
    }
}

class Child<A, B> extends Parent<B> {
    private A extra;
}
```

Đây là **lớp minh họa**, không dùng `null` làm giá trị hợp đồng sản phẩm; phần thân chỉ để biểu diễn kiểu trả về. `Child<String, Integer>` kế thừa `Parent<Integer>` và `get()` trả kiểu `Integer`, còn `extra` là `String`.

Có thể dùng kiểu tổng hợp làm type argument của cha:

```java
class Parent<T> { }

class Aggregated<A, B> extends Parent<java.util.List<A>> {
    private final B metadata;

    Aggregated(B metadata) {
        this.metadata = metadata;
    }
}
```

Với `Aggregated<String, Long>`, supertype của nó là `Parent<List<String>>`. Như vậy subclass **không nhất thiết** phải truyền trực tiếp một type parameter trần; nó có thể truyền một parameterized type được tạo từ tham số kiểu của lớp con. Tuy nhiên, mọi kiểu trong khai báo phải hợp lệ và thỏa bounds của cha.

**Nhận biết đúng phạm vi:** ký hiệu trong `class Child<A,B>` do lớp con khai báo; `Parent<B>` sử dụng ký hiệu `B` ấy làm type argument. `Parent` không “nhận thêm” type parameter `A`; chính subtype mới là nơi sở hữu thông tin thêm.

## 6. Thêm một kiểu mới để thực thi generic interface

Mẫu kế thừa tổng quát còn xuất hiện khi lớp con triển khai interface:

```java
interface HasKey<K> {
    K key();
}

class ValueHolder<T> {
    private final T value;

    ValueHolder(T value) {
        this.value = value;
    }

    T get() {
        return value;
    }
}

class KeyedHolder<T, K> extends ValueHolder<T>
        implements HasKey<K> {
    private final K key;

    KeyedHolder(T value, K key) {
        super(value);
        this.key = key;
    }

    @Override
    public K key() {
        return key;
    }
}
```

Đây là **ba khai báo minh họa**, không cần kết hợp với lớp `Box` ở chương trình chạy đầy đủ. Khi dùng `KeyedHolder<String, Long>`:

- Quan hệ superclass: `ValueHolder<String>`, cho nên `get()` trả `String`.
- Quan hệ interface: `HasKey<Long>`, cho nên `key()` trả `Long`.
- `T` và `K` đều được khai báo trên lớp con, rồi truyền vào **hai hợp đồng khác nhau**.

Có thể gán một đối tượng `KeyedHolder<String, Long>` cho biến kiểu `ValueHolder<String>` hoặc `HasKey<Long>`. Nhưng qua biến kiểu interface, chỉ nhìn thấy các method do interface khai báo.

Nếu interface tự kế thừa generic interface khác, cũng có thể **khai báo thêm type parameter**:

```java
interface Reader<T> {
    T read();
}

interface AuditedReader<T, AUDIT> extends Reader<T> {
    AUDIT audit();
}
```

`AuditedReader<String, Integer>` là subtype của `Reader<String>`, đồng thời có `audit()` trả `Integer`. Đây là **kế thừa interface**, không phải class inheritance; cú pháp vẫn sử dụng ánh xạ type parameter tương tự.

## 7. Type bounds khi lớp con thêm tham số kiểu

Nếu superclass có bound:

```java
class NumericBox<N extends Number> {
    private final N number;

    NumericBox(N number) {
        this.number = number;
    }

    double value() {
        return number.doubleValue();
    }
}
```

thì subclass chuyển một type parameter của mình lên cha phải khai báo bound tương thích:

```java
class Measured<N extends Number, UNIT> extends NumericBox<N> {
    private final UNIT unit;

    Measured(N number, UNIT unit) {
        super(number);
        this.unit = unit;
    }

    UNIT unit() {
        return unit;
    }
}
```

Đây là hai lớp minh họa hợp lệ đặt trong cùng bối cảnh thích hợp. `Measured<Integer, String>` hợp lệ; `Measured<String, String>` không hợp lệ vì `String` không phải subtype của `Number`.

Cách viết sai:

```java
// class WrongMeasured<N, UNIT> extends NumericBox<N> { }
// Không biên dịch: N không được ràng buộc là subtype của Number.
```

Lớp con cũng có thể đặt bound lên **tham số kiểu mới** nếu cần, ví dụ `<T, M extends Comparable<M>>`, với điều kiện các bound và kiểu được sử dụng phù hợp. Không cần áp bound chỉ để thêm tham số: `M` tự do là hợp lệ nếu API không cần thao tác chuyên biệt trên `M`.

### Nhiều bounds và ràng buộc kết hợp

Một type parameter có thể có multiple bounds như `<X extends Number & Comparable<X>>`. Khi có bound là class, nó phải đứng trước interface bound, sau đó các interface phân cách bằng `&`. Không viết `<X super Number>` để khai báo lower bound; `? super X` là wildcard tại vị trí sử dụng một generic type.

Tham số kiểu lớp con có thể liên hệ nhau theo bound, nhưng việc thêm quá nhiều ràng buộc khiến chữ ký khó hiểu. Hãy đặt bound để phản ánh những thao tác thực sự cần thiết, chứ không chỉ để tăng độ phức tạp.

## 8. Kiểm tra subtype đúng với từng type argument

`TaggedBox<String,Integer> extends Box<String>`, nên một đối tượng TaggedBox ấy có thể được dùng ở nơi nhận `Box<String>`; type argument **M=Integer** chỉ phục vụ phần metadata của lớp con.

```java
TaggedBox<String, Integer> tagged = TaggedBox.of("x", 1);
Box<String> base = tagged;
// Box<Object> tooWide = tagged; // Không hợp lệ.
List<? extends Box<String>> view = List.of(tagged);
```

**Phân biệt hai tầng bất biến:** `Box<String>` không là `Box<Object>`; tương tự `List<TaggedBox<String,Integer>>` không tự là `List<Box<String>>`. Nếu chỉ đọc phần tử, bounded wildcard như `List<? extends Box<String>>` giúp biểu diễn đúng quan hệ; nó không cho quyền ghi thêm một Box tùy ý. Đây là giới hạn của **type arguments tại nơi sử dụng**, không phủ nhận việc bản thân TaggedBox kế thừa Box<String>.

## 9. Những giới hạn của Java khi thiết kế subclass generic

### 9.1. Java chỉ hỗ trợ một superclass

Một class Java chỉ có thể `extends` **một** class, nhưng có thể `implements` nhiều interface. Vì vậy muốn tích hợp nhiều hợp đồng kiểu, hãy chọn một superclass rồi thêm các interface generic hoặc ưu tiên composition khi phù hợp. Thêm type parameter mới không biến Java thành ngôn ngữ có multiple class inheritance.

### 9.2. Không kế thừa hai cách tham số hóa của cùng interface

Một subtype không thể đồng thời kế thừa `Holder<String>` và `Holder<Integer>` của cùng khai báo `Holder<T>`, kể cả khi các đường kế thừa đi qua interface trung gian. Đây là quy tắc nhất quán superinterface của Java, **không chỉ** là xung đột overload do erasure; interface rỗng `Marker<T>` cũng chịu hạn chế ấy.

Nếu một lớp cần vừa giữ giá trị T vừa cung cấp ID, hai **interface khác nhau** như `ValueSource<T>` và `Identifiable<ID>` (đã dùng ở mục 4/6) diễn đạt vai trò rõ hơn. Nếu cần hai instantiations của **cùng** interface, tạo hai đối tượng và phối hợp bằng composition thay vì bắt một lớp kế thừa cả hai.

### 9.3. Raw type làm mất kiểm tra kiểu

Viết `extends Base` thay vì `extends Base<String>` khi `Base` là generic class có thể dẫn tới sử dụng **raw type** và mất bảo đảm kiểu. Không nên bỏ `<...>` chỉ để compiler chấp nhận mã cũ. Trong phần lớn chương trình hiện đại, lớp con cần **truyền type argument phù hợp** cho generic superclass.

Ví dụ:

```java
// Tránh: class RawChild<X> extends Box { ... }
// Ưu tiên: class TypedChild<X> extends Box<X> { ... }
```

Hai dòng là **phác thảo cú pháp**, không phải định nghĩa lớp hoàn chỉnh. Raw type có thể gây unchecked warnings, cast do compiler thêm vào, và nguy cơ `ClassCastException` ở runtime.

### 9.4. Type erasure và việc không thể kiểm tra tham số kiểu cụ thể tại runtime

`TaggedBox<String, Integer>` và `TaggedBox<Integer, String>` đều là các cách tham số hóa của cùng lớp `TaggedBox`, không phải hai class runtime riêng.

Ví dụ hợp lệ:

```java
if (obj instanceof TaggedBox<?, ?>) {
    System.out.println("some tagged box");
}
```

Đây là đoạn minh họa cần có biến `obj` thuộc kiểu phù hợp và nằm trong method. Không viết `obj instanceof TaggedBox<String, Integer>` như thể JVM có thể kiểm tra mọi type argument đã bị xóa. Kết quả `instanceof TaggedBox<?, ?>` xác nhận loại raw class một cách an toàn cho pattern chung, **không xác nhận** `T = String` và `M = Integer`.

Lưu ý: dùng `@Override` trên method override thực sự giúp compiler kiểm tra chữ ký. Với generic inheritance, bridge method có thể được compiler tạo sau erasure để duy trì overriding; không nên tự viết bridge method hoặc dựa vào sự tồn tại của nó như một API công khai.

## 10. Tham số kiểu của subclass và tham số kiểu của method độc lập

Trong `TaggedBox<T, M>`, bạn có thể khai báo một generic method riêng:

```java
public <U> TaggedBox<U, M> mapValue(
        Function<? super T, ? extends U> mapper) {
    return TaggedBox.of(mapper.apply(get()), marker);
}
```

Đây là **trích đoạn method** từ ví dụ `TaggedBox<T,M>`; cần có `Function`, truy cập `get()`, trường `marker` và factory `of` thích hợp trong class chứa method.

Ba phạm vi kiểu khác nhau:

- `T`: class type parameter được truyền cho superclass `Box<T>`.
- `M`: class type parameter bổ sung của subclass, giữ nguyên trong kết quả.
- `U`: **method type parameter mới** mô tả kiểu của giá trị sau biến đổi.

Lời gọi `TaggedBox<String, Integer>.mapValue(String::length)` không phải đổi `T` của object đang tồn tại. Nó **tạo một `TaggedBox<Integer, Integer>` mới** trong ví dụ, còn object cũ vẫn chứa `String`. Đây là lợi ích của thiết kế bất biến: phép biến đổi trả về object mới với quan hệ type parameters được thể hiện tường minh.

Một static factory thuộc `TaggedBox<T, M>` phải tự khai báo type parameters của **method**, vì static context không dùng trực tiếp tham số kiểu của class. Ví dụ:

```java
static <V, L> TaggedBox<V, L> of(V value, L marker) {
    return new TaggedBox<>(value, marker);
}
```

Chữ `V`, `L` được chọn để nhấn mạnh chúng thuộc riêng factory; chúng không “mượn” `T`, `M` của một instance. Có thể đặt tên factory là `<T, M>` theo quy tắc shadowing, nhưng làm vậy dễ gây hiểu nhầm trong bài học về phạm vi kiểu.

## 11. Những lỗi phổ biến khi thêm type parameter ở lớp con

**Lỗi 1 — Nhầm số type parameter của cha và con.** Nếu cha là `Box<T>`, con vẫn có thể là `TaggedBox<T, M>`. Tuy nhiên, mệnh đề `extends Box<T>` phải truyền **đúng một** type argument phù hợp cho `Box`; không viết `extends Box<T, M>` nếu `Box` chỉ khai báo một tham số kiểu.

**Lỗi 2 — Quên chuyển type parameter cho lớp cha.** `extends Box` dùng raw type; `extends Box<T>` giữ an toàn kiểu và quan hệ kiểu trả về của các method kế thừa.

**Lỗi 3 — Tưởng `M` tự động nằm trong superclass.** `TaggedBox<T, M> extends Box<T>` không làm `Box<T>` biết `M`. Muốn sử dụng `M` qua một biến có kiểu `Box<T>`, cần thiết kế thêm API ở lớp cha, một interface khác hoặc kiểm tra/cast an toàn khi có lý do chính đáng.

**Lỗi 4 — Không gọi constructor cha phù hợp.** Nếu `Box<T>` chỉ có constructor `Box(T value)`, constructor lớp con phải gọi `super(value)` làm thao tác đầu tiên hoặc dùng constructor delegation hợp lệ. Không có constructor mặc định `Box()` để compiler tự gọi.

**Lỗi 5 — Truyền sai type parameter cho cha.** `Child<A,B> extends Parent<B>` nghĩa là kiểu cha là `Parent<B>`. Nếu chủ ý giữ kiểu `A` mà lại viết `Parent<B>`, các method kế thừa sẽ có kiểu sai với hợp đồng mong muốn.

**Lỗi 6 — Quên bound khi cha có bound.** `Base<N extends Number>` không chấp nhận một `N` tự do hoàn toàn từ lớp con; khai báo `<N extends Number, M>` hoặc dùng một type argument thỏa ràng buộc.

**Lỗi 7 — Nhầm quan hệ subtype với variance.** `TaggedBox<String, Integer>` là subtype của `Box<String>`, nhưng không của `Box<Object>` hay `TaggedBox<Object, Integer>`. Cần wildcard phù hợp nếu API chỉ muốn đọc ở kiểu rộng.

**Lỗi 8 — Nghĩ một lớp con với `<T, M>` luôn phải dùng `T` làm kiểu cha.** Không bắt buộc. `class Child<T, M> extends Parent<M>` và `class Child<M> extends Parent<String>` có thể là thiết kế hợp lệ; phải chọn ánh xạ theo ý nghĩa dữ liệu.

**Lỗi 9 — Nhầm tham số kiểu class và method.** `<U> TaggedBox<U, M> mapValue(...)` khai báo `U` thuộc method; `U` không thay đổi kiểu của một object `TaggedBox<T, M>` đã có.

**Lỗi 10 — Cố dùng `T` hoặc `M` của class trong static factory mà không khai báo riêng.** Phương thức static phải có type parameters mới như `static <V,L> TaggedBox<V,L> of(...)`.

**Lỗi 11 — Sử dụng raw type rồi cast tùy tiện.** Nếu bắt buộc tương tác với mã Java cũ, cô lập raw type và validate dữ liệu tại ranh giới; đừng dùng cast không kiểm tra làm giải pháp kế thừa tổng quát.

**Lỗi 12 — Giả định `final` làm các trường kiểu `T` và `M` bất biến sâu.** `private final T value` chỉ ngăn gán lại tham chiếu. Nếu `T` hoặc `M` trỏ đến list mutable, trạng thái bên trong list vẫn có thể thay đổi.

**Lỗi 13 — Viết nhiều nhánh kế thừa cùng một generic interface với type argument xung đột.** Java không chấp nhận kế thừa cùng interface đó đồng thời dưới hai phép tham số hóa khác nhau trong một hệ phân cấp. Cần sửa mô hình hoặc sử dụng interface với nhiều tham số kiểu.

## 12. Cách quyết định có nên thêm type parameter ở lớp con

Khi thiết kế API, hãy kiểm tra bốn câu hỏi:

1. **Dữ liệu mới có một kiểu độc lập, ổn định trong suốt vòng đời object không?** Ví dụ `TaggedBox<T, M>` cần giữ type của nhãn, nên `M` có giá trị.
2. **Lớp cha đã mô tả phần nào?** Nếu cha chỉ quản lý `T`, subclass chỉ nên bổ sung thứ không thuộc hợp đồng đó; đừng thay đổi ý nghĩa `T` bất ngờ.
3. **Quan hệ `extends Parent<...>` có bảo toàn đúng kiểu kế thừa không?** Việc điền `T`, `M`, `List<T>` hay kiểu cố định cần theo ý nghĩa nghiệp vụ, không theo tên ký hiệu.
4. **Có cần inheritance không?** Nếu type mới chỉ là một thành phần dữ liệu kết hợp nhưng không thật sự là một subtype của cha, composition có thể đơn giản hơn.

Ví dụ thay thế bằng composition nếu không có quan hệ “is-a” thực sự:

```java
final class Annotated<T, M> {
    private final T value;
    private final M metadata;

    Annotated(T value, M metadata) {
        this.value = value;
        this.metadata = metadata;
    }

    T value() {
        return value;
    }

    M metadata() {
        return metadata;
    }
}
```

Đây là **một lớp minh họa**, không kế thừa `Box<T>`; nó gộp hai thành phần dưới một kiểu thay vì tạo subtype. Nếu người dùng API không cần thay `Annotated<T, M>` vào chỗ `Box<T>`, đây có thể là lựa chọn dễ duy trì hơn.

## 13. Câu hỏi tự kiểm tra có đáp án

**Câu 1.** `class Child<T, M> extends Parent<T>` khai báo bao nhiêu type parameters ở lớp con?  
**Đáp án:** Hai: `T` và `M`. Chỉ `T` được truyền làm type argument cho `Parent<T>`.

**Câu 2.** Với `Child<String, Integer> extends Parent<T>`, supertype được tham số hóa là gì?  
**Đáp án:** `Parent<String>` nếu khai báo cụ thể là `class Child<T,M> extends Parent<T>`.

**Câu 3.** `class Child<K,V> extends Parent<V>` và `Child<String,Long>` thì `Parent` có kiểu gì?  
**Đáp án:** `Parent<Long>`. Ánh xạ do `extends Parent<V>` xác định.

**Câu 4.** `TextTagged<Long> extends Box<String>` có trở thành `Box<Long>` không?  
**Đáp án:** Không. Lớp cha được cố định ở `String`; `Long` chỉ là type argument của metadata lớp con.

**Câu 5.** `TaggedBox<String,Integer>` có gán được cho `Box<String>` không?  
**Đáp án:** Có, do kế thừa; nhưng nó không tự gán được cho `Box<Object>`.

**Câu 6.** Nếu `Box<T>` có constructor `Box(T value)` và không có no-arg constructor, constructor của subclass nên làm gì?  
**Đáp án:** Gọi `super(value)` hoặc constructor delegation phù hợp để khởi tạo phần cha.

**Câu 7.** `class Measured<N,UNIT> extends NumericBox<N>` có hợp lệ nếu `NumericBox<N extends Number>` không?  
**Đáp án:** Không. Cần ràng buộc `N extends Number` ở lớp con hoặc truyền một type argument đã thỏa bound.

**Câu 8.** Có thể vừa `extends Box<T>` vừa `implements Identifiable<ID>` không?  
**Đáp án:** Có. Class chỉ có một superclass, nhưng có thể thực thi interface generic với type argument độc lập.

**Câu 9.** `<U> TaggedBox<U,M> mapValue(...)` thuộc phạm vi kiểu nào?  
**Đáp án:** `U` thuộc method, `T` và `M` thuộc class. Kết quả có thể mang kiểu giá trị mới `U` và giữ metadata kiểu `M`.

**Câu 10.** `List<TaggedBox<String,Integer>>` có phải subtype của `List<Box<String>>` không?  
**Đáp án:** Không. Nếu chỉ đọc, cân nhắc `List<? extends Box<String>>`.

**Câu 11.** Vì sao không dùng `instanceof TaggedBox<String,Integer>` để xác minh hai type arguments?  
**Đáp án:** Do type erasure; phép kiểm tra kiểu tham số hóa cụ thể đó không hợp lệ ở runtime Java thông thường.

**Câu 12.** Khi nào nên tạo `Annotated<T,M>` bằng composition thay vì `TaggedBox<T,M> extends Box<T>`?  
**Đáp án:** Khi dữ liệu chỉ cần kết hợp hai thành phần mà không cần hợp đồng subtype “is-a Box”; composition giúp tránh ràng buộc kế thừa không cần thiết.

**Câu 13.** Type parameter mới `M` có bắt buộc khác kiểu `T` không?  
**Đáp án:** Không. `TaggedBox<String,String>` vẫn hợp lệ; đây là hai vai trò kiểu độc lập, không yêu cầu hai lớp khác nhau.

**Câu 14.** Nếu lớp con có `static <V,L> TaggedBox<V,L> of(V value,L tag)`, `V` và `L` có phải `T` và `M` của instance class không?  
**Đáp án:** Không; chúng được khai báo riêng ở method static và dùng để suy luận kiểu của object `TaggedBox<V,L>` được tạo.

**Câu 15.** Nếu `Box<String> parentView` trỏ đến một `TaggedBox<String,Integer>`, `parentView.describe()` gọi bản nào?  
**Đáp án:** Nếu `describe()` là phương thức instance được override, Java dùng dynamic dispatch tới bản override của `TaggedBox`; còn static type `Box<String>` quyết định những method nào được gọi trực tiếp.

**Tổng kết:** lớp con generic **được quyền thêm tham số kiểu mới** mà không sửa số tham số kiểu của cha. Điều quan trọng là khai báo `<...>` trên lớp con, ánh xạ rõ các kiểu trong `extends Parent<...>` và `implements Interface<...>`, giữ đúng bounds, constructor, quan hệ subtype và giới hạn invariance. Nếu quan hệ kế thừa thực sự có ý nghĩa, thiết kế này cho phép mở rộng dữ liệu/hành vi mà vẫn bảo toàn khả năng kiểm tra kiểu ở compile time.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="generic-inheritance-004">Quan hệ giữa lớp generic và giao diện generic</a>

<details>
<summary>Xem chi tiết</summary>

# Quan hệ giữa lớp generic và giao diện generic trong Java

Trong Java, một **lớp generic** có thể `implements` một **giao diện generic**, hoặc `extends` một lớp generic khác. Khi kế thừa, lớp con phải **giữ nguyên**, **chuyển tiếp**, hoặc **cố định** các tham số kiểu theo cú pháp khai báo. Nhờ đó, hợp đồng kiểu của phương thức được duy trì từ interface đến implementation và đến nơi gọi.

Ví dụ, `Store<K,V>` định nghĩa khả năng lưu dữ liệu theo khóa. `MapStore<K,V> implements Store<K,V>` giữ hai tham số kiểu để tái sử dụng, còn `UserStore extends MapStore<Long,User>` cố định cặp kiểu khóa–giá trị cho một nghiệp vụ. Mỗi cách ảnh hưởng trực tiếp tới chữ ký phương thức mà trình biên dịch chấp nhận.

Sau bài học, bạn có thể giải thích quan hệ giữa `class` và `interface` generic, phân biệt kế thừa giữ tham số với chuyên biệt hóa, thực hiện đúng `@Override`, đọc chữ ký qua nhiều tầng kế thừa, nhận diện các lỗi về bất biến kiểu, type erasure và raw type. Các ví dụ hoàn chỉnh dùng Java 17 trở lên.

## 1. Ba quan hệ cần phân biệt

Trong Java, các khai báo sau biểu diễn ba quan hệ khác nhau:

```java
interface Reader<K, V> {
    V read(K key);
}

interface Store<K, V> extends Reader<K, V> {
    void put(K key, V value);
}

class MemoryStore<K, V> implements Store<K, V> {
    private final java.util.Map<K, V> map = new java.util.HashMap<>();

    @Override
    public void put(K key, V value) {
        map.put(key, value);
    }

    @Override
    public V read(K key) {
        return map.get(key);
    }
}
```

1. `Store<K,V> extends Reader<K,V>`: một **interface kế thừa interface** khác và bổ sung hợp đồng.
2. `MemoryStore<K,V> implements Store<K,V>`: một **class triển khai interface** và phải cung cấp các phương thức abstract bắt buộc.
3. `class Specialized extends MemoryStore<String,Integer>`: một **class kế thừa class** khác, có thể cố định các đối số kiểu của lớp cha.

Từ `extends` có hai cách dùng trong cấu trúc kế thừa: `class Child extends Parent` và `interface Sub extends Base`. `implements` là quan hệ class triển khai interface. Dấu `extends` trong `T extends Number` lại có vai trò **ràng buộc type parameter**; đây là một ngữ cảnh cú pháp khác.

**Các ký hiệu `K`, `V` trong khai báo không phải tên lớp hay biến runtime.** Chúng là tham số kiểu. Còn `Store<String,Integer>` là một **kiểu đã được tham số hóa** với các đối số `String` và `Integer`.

## 2. Interface generic xác định hợp đồng, class generic thực hiện hợp đồng

Một interface generic mô tả phương thức dưới dạng quan hệ kiểu:

```java
interface Repository<ID, ENTITY> {
    void save(ID id, ENTITY entity);
    java.util.Optional<ENTITY> findById(ID id);
}
```

Lớp triển khai có thể giữ nguyên hai type parameter:

```java
class MemoryRepository<ID, ENTITY>
        implements Repository<ID, ENTITY> {

    private final java.util.Map<ID, ENTITY> data =
            new java.util.HashMap<>();

    @Override
    public void save(ID id, ENTITY entity) {
        data.put(id, entity);
    }

    @Override
    public java.util.Optional<ENTITY> findById(ID id) {
        return java.util.Optional.ofNullable(data.get(id));
    }
}
```

`MemoryRepository<ID,ENTITY>` **khai báo lại hai tham số cùng vai trò ở chính class**, rồi **truyền chúng** vào `Repository<ID,ENTITY>`. Việc đặt tên `ID` và `ENTITY` giống interface cho dễ đọc, nhưng **đối số kiểu không buộc trùng tên**:

```java
class AlternateStore<K, V> implements Repository<K, V> {
    private final java.util.Map<K, V> data = new java.util.HashMap<>();

    @Override
    public void save(K key, V value) {
        data.put(key, value);
    }

    @Override
    public java.util.Optional<V> findById(K key) {
        return java.util.Optional.ofNullable(data.get(key));
    }
}
```

`K` tương ứng `ID`, còn `V` tương ứng `ENTITY`. **Tên type parameter không định danh quan hệ kế thừa**; sự tương thích xuất phát từ vị trí type argument và chữ ký phương thức.

Khi khai báo:

```java
Repository<Long, String> repo = new MemoryRepository<>();
repo.save(1L, "ready");
java.util.Optional<String> found = repo.findById(1L);
```

compiler xem `save` nhận `(Long,String)` và `findById` trả `Optional<String>`. `new MemoryRepository<>()` sử dụng **diamond operator** để suy luận type arguments từ ngữ cảnh; không phải raw type.

### Những phương thức nào cần cài đặt?

Một class **concrete** implements `Repository<ID,ENTITY>` phải triển khai tất cả abstract methods của hợp đồng, trừ khi đã được kế thừa từ superclass theo cách hợp lệ. `@Override` giúp compiler phát hiện chữ ký sai. Phương thức triển khai interface abstract phải có visibility `public`:

```java
@Override
public void save(ID id, ENTITY entity) { ... }
```

Bỏ `public`, đảo kiểu đầu vào hoặc đổi kiểu trả về không tương thích có thể khiến class **không triển khai đúng** phương thức bắt buộc. Nếu chưa cung cấp đủ phương thức abstract, class phải được khai báo `abstract`.

Generic interface chỉ quy định **loại dữ liệu** và những chữ ký/hành vi đã mô tả. Nó không tự tạo lưu trữ, không tự kiểm tra tính duy nhất ID, không tự xử lý giao dịch và không mặc nhiên thread-safe.

## 3. Lớp không generic cố định đối số kiểu của interface

Nếu implementation chỉ phục vụ một loại dữ liệu, nó có thể không khai báo `<...>`:

```java
interface Sink<T> {
    void accept(T value);
}

class StringSink implements Sink<String> {
    @Override
    public void accept(String value) {
        System.out.println(value);
    }
}
```

`StringSink` đã **chuyên biệt hóa** `T` thành `String`. Chữ ký bắt buộc là `accept(String)`. Không cần và không nên khai báo `class StringSink<T>` nếu nghiệp vụ của lớp chỉ dành cho chuỗi.

Điều tương tự với hai tham số kiểu:

```java
class UserRepository
        implements Repository<Long, User> {
    // Phải triển khai save(Long, User)
    // và findById(Long) trả Optional<User>.
}
```

`User` là kiểu nghiệp vụ minh họa; đoạn trên chỉ là **khai báo hình dạng** và sẽ cần thân phương thức thực tế để là một concrete class hợp lệ. Trong chương trình chạy hoàn chỉnh ở phần 6, `UserStore` được chuyên biệt hóa bằng cách kế thừa một implementation generic đã hoàn chỉnh.

### Cố định một phần, giữ một phần

Có thể cố định kiểu khóa nhưng giữ kiểu giá trị tùy chọn:

```java
class LongKeyRepository<T> implements Repository<Long, T> {
    private final java.util.Map<Long, T> data = new java.util.HashMap<>();

    @Override
    public void save(Long id, T item) {
        data.put(id, item);
    }

    @Override
    public java.util.Optional<T> findById(Long id) {
        return java.util.Optional.ofNullable(data.get(id));
    }
}
```

`LongKeyRepository<T>` vẫn generic theo `T`, nhưng **khóa luôn có kiểu `Long`**. Dùng `LongKeyRepository<Order>` hay `LongKeyRepository<User>` tùy đối tượng nghiệp vụ. `LongKeyRepository<String>` không yêu cầu lại kiểu khóa khi khởi tạo.

Phân biệt rõ **class generic hay không** với **kiểu cha/interface mà class chọn**: một class không có `<T>` vẫn có thể implements hoặc extends một generic type bằng đối số kiểu cụ thể.

## 4. Class generic kế thừa class generic bên cạnh `implements`

`implements Repository<K,V>` buộc một **class** thực hiện hợp đồng interface; `extends MemoryRepository<K,V>` lại **kế thừa class đã có implementation**. Hai quan hệ có thể kết hợp: lớp con tái sử dụng implementation của superclass, còn hợp đồng interface của superclass tiếp tục áp dụng sau phép thế type arguments.

Dùng `MemoryRepository<ID,ENTITY>` đã khai báo tại mục 2, ta có ba hình thức:

```java
class AuditedRepository<K,V> extends MemoryRepository<K,V> {}
class LongKeyRepository<V> extends MemoryRepository<Long,V> {}
class FixedCounts extends MemoryRepository<String,Integer> {}
```

- `AuditedRepository<K,V>` chuyển cả K và V lên superclass; bản thân class con vẫn generic.
- `LongKeyRepository<V>` cố định kiểu khóa Long và giữ V thay đổi được.
- `FixedCounts` cố định cả hai: `Repository<String,Integer>` là interface contract của nó qua superclass.

Không cần viết lại `implements Repository<...>` nếu quan hệ đã được superclass triển khai đúng; nếu thêm interface mới, class vẫn phải đáp ứng mọi method bắt buộc của interface đó. Constructor không được kế thừa và cần gọi `super(...)` phù hợp khi superclass yêu cầu. Các khai báo trên nhằm so sánh **extends class** với **implements interface**; chương trình ở mục 6 mới minh họa hành vi có lưu trữ và override thực tế.

## 5. Interface generic kế thừa interface generic

`interface` có thể `extends` **nhiều interface**:

```java
interface Reader<K, V> {
    V find(K key);
}

interface Writer<K, V> {
    void save(K key, V value);
}

interface ReadWrite<K, V> extends Reader<K, V>, Writer<K, V> {
}
```

`ReadWrite<String,Integer>` thừa hưởng cả `find(String)` trả `Integer` và `save(String,Integer)`. Dù `ReadWrite` không tự thêm method nào, nó kết hợp hai hợp đồng kiểu.

Tương tự class, interface con có thể cố định đối số kiểu của interface cha:

```java
interface StringReader extends Reader<Long, String> {
}
```

Đọc là: `StringReader` có `find(Long)` và trả `String`. Tên `StringReader` ở đây nhấn mạnh kiểu kết quả, không phải kiểu khóa.

Một interface cũng có thể sắp xếp lại type parameter một cách rõ ràng:

```java
interface Flipped<V, K> extends Reader<K, V> {
}
```

Nếu dùng `Flipped<String,Long>`, hợp đồng inherited là `Reader<Long,String>`. **Thứ tự khai báo type parameter có ý nghĩa**; compiler gắn theo vị trí, không theo tên biến.

### Kế thừa `default` methods

```java
interface Reader<K, V> {
    java.util.Optional<V> find(K key);

    default boolean contains(K key) {
        return find(key).isPresent();
    }
}
```

Class implements `Reader<String,Integer>` nhận được `contains(String)` nếu không override, miễn implementation cung cấp `find(String)`. `default` method dùng type parameter thuộc interface cha với cách chuyên biệt hóa đã chọn.

Nếu class nhận hai `default` methods cùng chữ ký từ hai interface không liên quan, Java có thể yêu cầu override để giải quyết xung đột. Việc generic hóa interface không tự xóa quy tắc giải quyết xung đột này.

## 6. Chương trình Java 17+ hoàn chỉnh: `extends` và `implements` cùng hoạt động

Tạo tệp **`Main.java`**. Ví dụ gồm interface chỉ đọc `Reader<K,V>`, interface đọc/ghi `Store<K,V>`, lớp triển khai generic `MapStore<K,V>`, lớp con cố định kiểu `UserStore` và lớp con giữ nguyên kiểu `CountingStore<K,V>`.

```java
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class Main {
    record User(long id, String name) {}

    interface Reader<K, V> {
        Optional<V> find(K key);
    }

    interface Store<K, V> extends Reader<K, V> {
        void put(K key, V value);
    }

    static class MapStore<K, V> implements Store<K, V> {
        private final Map<K, V> data = new LinkedHashMap<>();

        @Override
        public void put(K key, V value) {
            data.put(
                    Objects.requireNonNull(key),
                    Objects.requireNonNull(value));
        }

        @Override
        public Optional<V> find(K key) {
            return Optional.ofNullable(data.get(key));
        }
    }

    static final class UserStore extends MapStore<Long, User> {
        void putUser(User user) {
            put(user.id(), user);
        }
    }

    static final class CountingStore<K, V> extends MapStore<K, V> {
        private int writes;

        @Override
        public void put(K key, V value) {
            super.put(key, value);
            writes++;
        }

        int writes() {
            return writes;
        }
    }

    public static void main(String[] args) {
        Store<String, Integer> stock = new MapStore<>();
        stock.put("SKU-01", 7);
        System.out.println("stock: "
                + stock.find("SKU-01").orElse(-1));

        UserStore users = new UserStore();
        users.putUser(new User(42L, "Lan"));
        System.out.println("user: "
                + users.find(42L).map(User::name).orElse("missing"));

        CountingStore<String, String> preferences =
                new CountingStore<>();
        preferences.put("theme", "dark");
        preferences.put("lang", "vi");
        System.out.println("writes: " + preferences.writes());
        System.out.println("theme: "
                + preferences.find("theme").orElse("-"));

        Reader<String, Integer> view = stock;
        System.out.println("reader: "
                + view.find("SKU-01").orElse(-1));

        // stock.put("SKU-01", "seven"); // Sai: V phải là Integer.
        // users.put("42", new User(42L, "Lan")); // Sai: K phải là Long.
    }
}
```

Dùng JDK 17 trở lên, trong thư mục chứa tệp:

```bash
javac -encoding UTF-8 Main.java
java Main
```

Kết quả stdout:

```text
stock: 7
user: Lan
writes: 2
theme: dark
reader: 7
```

**Giải thích từng quan hệ:**

1. `Store<K,V> extends Reader<K,V>`: `find(K)` là hợp đồng được kế thừa từ interface cha; `put(K,V)` do interface con bổ sung.
2. `MapStore<K,V> implements Store<K,V>`: class generic giữ hai tham số kiểu để cài đặt hợp đồng đọc và ghi bằng `Map<K,V>`. Các `@Override` có chữ ký tương thích với hợp đồng interface.
3. `UserStore extends MapStore<Long,User>`: class không generic cố định cặp kiểu cha. `put` được thừa hưởng với hợp đồng `(Long,User)`, `find` trả `Optional<User>`, nên method `putUser(User)` có thể gọi `put(user.id(),user)` an toàn kiểu.
4. `CountingStore<K,V> extends MapStore<K,V>`: class con vẫn generic, override `put(K,V)` và gọi `super.put(...)`. Chỉ sau lời gọi lưu thành công, bộ đếm `writes` được tăng.
5. `Store<String,Integer> stock = new MapStore<>()`: biến được khai báo theo **interface**, đối tượng được tạo bằng implementation. Caller phụ thuộc hợp đồng `Store`, không cần biết class dùng `LinkedHashMap` ở bên trong.
6. `Reader<String,Integer> view = stock`: interface `Store<String,Integer>` **là subtype** của `Reader<String,Integer>` vì quan hệ `extends`; biến `view` chỉ có thể gọi những method thấy được trên `Reader`.
7. Hai dòng comment lỗi cố ý minh họa compiler sẽ ngăn sai kiểu khi lời gọi đi qua các tham chiếu có type arguments rõ ràng.

Phương thức `find` dùng `Optional` để biểu thị khóa có thể chưa tồn tại. Đây là **quyết định thiết kế API**; generics không tự chọn `Optional`. Ví dụ lưu trong bộ nhớ và không thread-safe, không đại diện cho transaction, persistence hay phân quyền truy cập dữ liệu.

## 7. Đọc kiểu hiệu lực qua nhiều tầng kế thừa

Xem chuỗi sau:

```java
interface Source<T> {
    T read();
}

abstract class AbstractSource<T> implements Source<T> {
}

class TextSource extends AbstractSource<String> {
    @Override
    public String read() {
        return "hello";
    }
}
```

Mối quan hệ kiểu theo từng tầng:

```text
Source<T>
    ↑ implements
AbstractSource<T>
    ↑ extends (T = String)
TextSource
```

Khi xem `TextSource` như `Source<String>`, phương thức `read()` trả `String`. `AbstractSource<T>` được khai báo `abstract` vì chưa cài đặt `read()`, nhưng vẫn hợp lệ khi triển khai interface generic mà chưa cung cấp hết phương thức ở lớp trung gian.

**Không phải mọi lớp trong chuỗi đều cần generic.** Lớp trung gian có thể giữ `T`, còn lớp cuối có thể cố định `T = String` để biểu diễn nghiệp vụ.

Nếu một lớp cha generic có constructor nhận `T`, lớp con phải gọi `super(...)` với giá trị phù hợp sau khi đã chọn type arguments. Việc kế thừa generic không tự tạo constructor trong lớp con.

### Truyền tham số kiểu qua superclass và interface

```java
interface Identifiable<ID> {
    ID id();
}

abstract class Entity<ID> implements Identifiable<ID> {
}

final class UserEntity extends Entity<Long> {
    private final Long id;

    UserEntity(Long id) {
        this.id = id;
    }

    @Override
    public Long id() {
        return id;
    }
}
```

`UserEntity` cố định `ID = Long` qua superclass `Entity<Long>`, nên cũng đáp ứng `Identifiable<Long>`. Lúc sử dụng:

```java
Identifiable<Long> entity = new UserEntity(5L);
Long id = entity.id();
```

Không cần và không được tự giả định `UserEntity` triển khai `Identifiable<String>` chỉ vì `Long` hoặc `String` đều là `Object`.

## 8. Kiểm tra chữ ký phương thức override sau khi chuyên biệt hóa

Giả sử:

```java
interface Formatter<T> {
    String format(T value);
}
```

Một class cố định `T = Integer` phải triển khai chữ ký tương ứng:

```java
class IntegerFormatter implements Formatter<Integer> {
    @Override
    public String format(Integer value) {
        return "value=" + value;
    }
}
```

Phương thức `format(Integer)` chính là implementation của hợp đồng `Formatter<Integer>`. Dòng sau **không thay thế** hợp đồng ấy:

```java
// Ví dụ sai khi thay phương thức bắt buộc:
// public String format(String value) { return value; }
```

Dù tên method vẫn là `format`, tham số `String` không đúng. Nếu lớp concrete không có method đúng chữ ký, compiler sẽ yêu cầu triển khai method hoặc khai báo lớp `abstract`.

### Tương thích kiểu trả về

Java cho phép **covariant return type** đối với các kiểu trả về tham chiếu thích hợp. Ví dụ interface khai báo `Number read()`, implementation có thể trả `Integer`. Nhưng generic container mặc định **bất biến**, nên không suy ra rằng method khai báo trả `List<Number>` có thể override bằng `List<Integer>`.

`@Override` bảo vệ người viết khỏi việc vô tình tạo method khác chữ ký khi định triển khai interface hay override method của superclass. Nó không thay thế unit test cho logic nghiệp vụ.

### Bridge methods do type erasure

```java
interface Processor<T> {
    T process(T value);
}

class StringProcessor implements Processor<String> {
    @Override
    public String process(String value) {
        return value.strip();
    }
}
```

Ở cấp mã nguồn, implementation viết `process(String)`. Java biên dịch generics chủ yếu theo cơ chế **type erasure**, nên compiler có thể sinh một **bridge method tổng hợp** để duy trì dispatch đa hình khi chữ ký đã xóa kiểu khác nhau. Bạn không cần và không nên tự viết bridge method; hãy khai báo đúng generic signature và để compiler xử lý chi tiết bytecode.

## 9. Giới hạn của tính thay thế: `extends` lớp khác với subtype giữa generic types

Giả sử:

```java
class Animal {}
class Dog extends Animal {}
```

Mặc dù `Dog extends Animal`, hai kiểu sau **không có quan hệ subtype trực tiếp**:

```java
java.util.List<Dog> dogs = new java.util.ArrayList<>();

// Không biên dịch:
// java.util.List<Animal> animals = dogs;
```

Nếu gán được, caller qua `List<Animal>` có thể thêm `Animal` không phải `Dog` vào danh sách thực chất chỉ dành cho `Dog`.

Quy tắc tương tự với các interface do bạn tự khai báo:

```java
// Trong ngữ cảnh có Store<Long,Dog> dogs:
Store<Long, Dog> dogStore = null;

// Không biên dịch:
// Store<Long, Animal> animalStore = dogStore;
```

**Cần phân biệt:**

- `Store<K,V> extends Reader<K,V>` xác lập quan hệ subtype **giữa hai khai báo interface** với cùng cách tham số hóa tương ứng.
- `Store<Long,Dog>` và `Store<Long,Animal>` là hai cách tham số hóa **cùng một generic interface**, vốn mặc định bất biến theo đối số kiểu.

Vì `Store<Long,Dog>` là subtype của `Reader<Long,Dog>`, phép gán `Reader<Long,Dog> view = dogStore` hợp lệ. Nhưng không thể nhảy sang `Store<Long,Animal>` chỉ nhờ `Dog extends Animal`.

### Sử dụng wildcard đúng nhu cầu

Nếu một API **chỉ đọc** các giá trị thuộc họ `Animal`, có thể biểu diễn:

```java
Reader<Long, ? extends Animal> readOnly = dogStore;
```

Vì `Reader` không có thao tác ghi, kiểu wildcard `? extends Animal` cho phép sử dụng kết quả đọc như giá trị tương thích với `Animal` sau khi xử lý `Optional`. Nếu API cho phép ghi, wildcard cần được chọn cẩn thận theo hướng dữ liệu; `? extends Animal` không cho phép ghi một `Animal` tùy ý vào một `Store<Long, ? extends Animal>`.

Quy tắc thường được gọi là “producer extends, consumer super” (PECS) là gợi ý về chiều đọc/ghi, không phải cơ chế tự biến generic types thành covariant.

## 10. Ràng buộc type parameter trong kế thừa

Khi một interface cần đảm bảo thực thể có ID đúng kiểu, có thể khai báo bound:

```java
interface HasId<ID> {
    ID id();
}

interface EntityRepository<ID, E extends HasId<ID>> {
    void save(E entity);
    java.util.Optional<E> find(ID id);
}
```

`E extends HasId<ID>` yêu cầu kiểu entity phải cung cấp method `id()` trả đúng loại `ID`. Nếu class giữ nguyên cả hai type parameters, nó cũng phải thỏa bound:

```java
class InMemoryEntityRepository<ID, E extends HasId<ID>>
        implements EntityRepository<ID, E> {

    private final java.util.Map<ID, E> data =
            new java.util.HashMap<>();

    @Override
    public void save(E entity) {
        data.put(entity.id(), entity);
    }

    @Override
    public java.util.Optional<E> find(ID id) {
        return java.util.Optional.ofNullable(data.get(id));
    }
}
```

Nếu viết `class Broken<ID,E> implements EntityRepository<ID,E>`, compiler từ chối vì `E` chưa được ràng buộc để chắc chắn là subtype phù hợp của `HasId<ID>`. Cách sửa là truyền một kiểu cụ thể đúng bound hoặc lặp lại bound thích hợp ở khai báo class con.

**Một lớp không được tự nới rộng bound của type parameter cha** theo cách làm mất hợp đồng của cha. Khi subclass tiếp tục generic, hãy đảm bảo type arguments được truyền lên superclass hoặc interface đều thỏa tất cả bound cần thiết.

### Có thể thêm ràng buộc ở lớp con

Giả sử `Box<T>` cho phép mọi reference type, lớp con có thể hạn chế hơn:

```java
class NumberBox<N extends Number> extends Box<N> {
    NumberBox(N value) {
        super(value);
    }

    double asDouble() {
        return get().doubleValue();
    }
}
```

`NumberBox<Integer>` hợp lệ, `NumberBox<String>` bị từ chối. Lớp con chọn một tập đối số kiểu hẹp hơn nhưng vẫn thỏa yêu cầu tổng quát của `Box<N>`.

## 11. Type erasure và xung đột kế thừa

Java dùng **type erasure**: trong bytecode, type variables được xóa về upper bound (hoặc `Object` nếu không có bound), và compiler chèn cast/bridge methods khi cần. Một instance `MapStore<String,Integer>` không phải runtime class riêng so với `MapStore<Long,User>`; cả hai cùng có runtime class `MapStore` trong chương trình.

Các cú pháp sau không hợp lệ:

```java
// Không được kiểm tra chính xác cả type argument:
// if (obj instanceof Store<String, Integer>) { ... }

// Không có class literal cho kiểu tham số hóa:
// Store<String, Integer>.class
```

Có thể dùng `obj instanceof Store<?, ?>` để kiểm tra đối tượng thuộc một cách tham số hóa **không biết rõ type arguments**. Nó không kiểm chứng kiểu khóa và giá trị bên trong.

### Một generic interface không được kế thừa với hai type arguments khác nhau

Đoạn dưới **cố ý sai**, bởi một class không được thừa hưởng đồng thời hai cách tham số hóa không tương thích của cùng interface:

```java
interface Processor<T> {
    T process(T input);
}

interface StringProcessor extends Processor<String> {
}

interface IntegerProcessor extends Processor<Integer> {
}

// Không biên dịch:
// class Mixed implements StringProcessor, IntegerProcessor { ... }
```

`StringProcessor` đòi `Processor<String>`; `IntegerProcessor` đòi `Processor<Integer>`. Sau type erasure, JVM không thể coi `Mixed` như đã triển khai cùng một generic interface theo hai cách không tương thích. Không thể giải quyết bằng việc thêm hai overload `process(String)` và `process(Integer)` rồi tuyên bố cả hai interface đã được kế thừa hợp lệ.

### Phương thức có cùng erasure

Trong cùng một type, hai overload `void handle(List<String>)` và `void handle(List<Integer>)` có cùng dạng tham số `List` sau erasure, nên xung đột. Đây là lý do không nên dựa vào type arguments để phân biệt overload khi chữ ký runtime giống nhau.

## 12. Raw type, unchecked warnings và nguy cơ mất hợp đồng

Khi một class implements/extends generic type nhưng **bỏ type arguments**, Java có thể xử lý như raw type vì tương thích với mã cũ:

```java
// Không khuyến nghị:
class LegacyStore extends java.util.HashMap {
}
```

`HashMap` có hai type parameters, nhưng raw `HashMap` không còn hợp đồng khóa/giá trị cụ thể cho nhiều thao tác. Khi gọi các method generic qua raw reference, cảnh báo `unchecked` có thể xuất hiện; dữ liệu sai có thể chỉ phát hiện khi lấy ra và cast.

Cùng vấn đề với:

```java
java.util.Map<String, Integer> typed = new java.util.HashMap<>();
java.util.Map raw = typed;
raw.put("count", "not an integer"); // Cảnh báo unchecked.
```

Sau đó đọc `Integer count = typed.get("count");` có thể ném `ClassCastException` ở runtime. Chương trình học nên dùng đầy đủ đối số kiểu (`Map<String,Integer>`), hạn chế raw ở vùng tích hợp legacy và kiểm tra dữ liệu thay vì tắt warnings tùy tiện.

**`new HashMap<>()` không phải raw type.** Diamond nhờ compiler suy luận type arguments; khác với `new HashMap()`.

## 13. Các lỗi phổ biến và cách sửa

**Lỗi 1 — quên truyền type parameter của class con lên cha:** `class Child<T> extends Parent` là raw inheritance nếu `Parent` generic. Dùng `extends Parent<T>` khi cần giữ cùng quan hệ kiểu, hoặc `extends Parent<String>` khi cần cố định.

**Lỗi 2 — tự dùng `T` nhưng chưa khai báo `T`:**

```java
// Sai:
// class Child extends Box<T> { }

// Đúng khi giữ T:
class Child<T> extends Box<T> {
    Child(T value) { super(value); }
}
```

**Lỗi 3 — implementation sai chữ ký sau chuyên biệt hóa:** với `implements Formatter<Integer>`, method phải nhận `Integer` theo hợp đồng, không nhận `String`. Dùng `@Override` và để compiler kiểm tra.

**Lỗi 4 — dùng tên type parameters như thể chúng phải giống cha:** `class Child<A,B> implements Store<A,B>` vẫn hợp lệ dù interface khai báo `Store<K,V>`. Quan trọng là **quan hệ theo vị trí và bound**, không phải tên.

**Lỗi 5 — hiểu `extends` generic là tự cho phép hiệp biến:** `Dog extends Animal` không kéo theo `Store<Long,Dog> extends Store<Long,Animal>`. Cần wildcard hoặc mô hình API phù hợp khi chỉ đọc/ghi ở mức trừu tượng.

**Lỗi 6 — thừa hưởng một interface với hai cách tham số hóa:** ví dụ đồng thời `Processor<String>` và `Processor<Integer>` trên cùng một class không hợp lệ. Tách vai trò hoặc dùng interface khác.

**Lỗi 7 — override trả kiểu không tương thích:** `Optional<Dog>` không phải subtype của `Optional<Animal>`; quy tắc covariant return không hợp thức hóa những generic wrappers bất biến.

**Lỗi 8 — bỏ bound khi subclass generic:** nếu interface cha đòi `E extends HasId<ID>`, lớp con giữ `E` cũng cần đảm bảo bound ấy.

**Lỗi 9 — nhầm type erasure với việc compiler không kiểm tra:** thông tin generic chủ yếu kiểm tra ở compile time, nhưng raw/cast không kiểm chứng vẫn có thể phá vỡ hợp đồng. Không dựa vào `instanceof Store<String,Integer>` để kiểm tra toàn bộ kiểu.

**Lỗi 10 — quên hợp đồng hành vi:** kế thừa chữ ký không tự đảm bảo dữ liệu luôn bền vững, bất biến, an toàn đa luồng hay đã được phân quyền. Implementation và tài liệu phải xác định các cam kết đó.

## 14. Khi nào nên giữ generic và khi nào nên chuyên biệt hóa?

Chọn **lớp generic implements generic interface** khi nhiều kiểu dữ liệu cần một thuật toán, ví dụ `MapStore<K,V>` cung cấp thao tác lưu theo khóa cho nhiều cặp kiểu.

Chọn **lớp con generic extends lớp generic** khi cần thêm hành vi chung mà vẫn giữ cùng type arguments, như `CountingStore<K,V> extends MapStore<K,V>`.

Chọn **lớp không generic cố định type arguments** khi nghiệp vụ cần một hợp đồng rõ ràng, ví dụ `UserStore extends MapStore<Long,User>` hoặc `IntegerFormatter implements Formatter<Integer>`.

Chọn **interface con chuyên biệt hóa** khi muốn đặt tên cho một vai trò API xác định, ví dụ `interface UserReader extends Reader<Long,User>`; điều này vẫn là interface, chưa có implementation.

Không nên lạm dụng tạo lớp kế thừa chỉ để đổi tên; cân nhắc composition khi các thành phần chỉ cần cộng tác thay vì quan hệ **is-a**. Generic inheritance hữu ích nhất khi lớp con thực sự **là một phiên bản** của kiểu cha và tuân thủ hợp đồng mà client mong đợi.

| Tình huống | Mẫu khai báo |
| --- | --- |
| Interface con giữ kiểu | `interface Store<K,V> extends Reader<K,V>` |
| Class generic triển khai interface | `class MapStore<K,V> implements Store<K,V>` |
| Class con vẫn generic | `class CountingStore<K,V> extends MapStore<K,V>` |
| Class con cố định kiểu | `class UserStore extends MapStore<Long,User>` |
| Class generic cố định một type argument | `class LongKeyStore<T> implements Repository<Long,T>` |
| Interface con cố định kiểu | `interface UserReader extends Reader<Long,User>` |
| Class trung gian chưa triển khai đủ | `abstract class AbstractSource<T> implements Source<T>` |

## 15. Bài tập tự kiểm tra

**Câu 1.** Với `interface Sink<T> { void accept(T x); }`, chữ ký đúng khi `class Logger implements Sink<String>` là gì?

**Đáp án:** `public void accept(String x)`. Type parameter `T` đã được cố định thành `String`, và method override phải có visibility thích hợp.

**Câu 2.** `class Child<T> extends Box<T>` khác `class Child extends Box<String>` ở đâu?

**Đáp án:** Dạng đầu giữ lớp con generic và chuyển `T` lên lớp cha; dạng sau tạo class con không generic và cố định kiểu `String` cho superclass.

**Câu 3.** Vì sao `Store<String,Integer>` có thể gán cho `Reader<String,Integer>`?

**Đáp án:** Vì khai báo `Store<K,V> extends Reader<K,V>` xác lập quan hệ subtype giữa hai interface sau khi áp dụng cùng cặp type arguments. Client có thể xem store như reader và chỉ gọi API đọc.

**Câu 4.** Nếu `Dog extends Animal`, có thể gán `Store<Long,Dog>` cho biến `Store<Long,Animal>` không?

**Đáp án:** Không. Generic types bất biến theo type arguments; nếu gán được, caller có thể lưu một `Animal` không phải `Dog` vào store dành cho `Dog`.

**Câu 5.** Vì sao `class Broken<ID,E> implements EntityRepository<ID,E>` không hợp lệ khi `E` của interface bị bound bởi `HasId<ID>`?

**Đáp án:** Type parameter `E` trên `Broken` chưa có bound phù hợp nên compiler không thể bảo đảm nó thỏa `E extends HasId<ID>`. Phải khai báo bound tương ứng hoặc dùng một kiểu cụ thể đáp ứng hợp đồng.

**Câu 6.** Có thể một class vừa implements `Processor<String>` vừa implements `Processor<Integer>` thông qua hai interface con khác nhau không?

**Đáp án:** Không, nếu hai đường kế thừa dẫn tới cùng một generic interface với hai cách tham số hóa không tương thích. Java từ chối do xung đột kiểu và erasure.

**Câu 7.** Đổi `MapStore<K,V>` thành `MapStore` raw để tránh viết type arguments có lợi cho an toàn kiểu không?

**Đáp án:** Không. Raw type loại bỏ phần lớn kiểm tra type arguments ở các thao tác và có thể dẫn đến cảnh báo `unchecked` hoặc `ClassCastException` khi runtime.

## Kết luận

Quan hệ giữa **lớp generic** và **giao diện generic** được thiết lập qua `implements` và `extends`. Interface định nghĩa hợp đồng kiểu; class có thể giữ tham số (`MapStore<K,V>`), cố định một phần (`LongKeyRepository<T>`) hoặc cố định toàn bộ (`UserStore extends MapStore<Long,User>`), miễn các chữ ký và bound được thực hiện đúng. Kế thừa giữa các khai báo type không làm các cách tham số hóa khác nhau tự có quan hệ subtype: generics Java vẫn bất biến theo mặc định. Dùng `@Override`, kiểm tra bound và tránh raw type giúp giữ đúng hợp đồng từ thiết kế đến caller, trong khi những yêu cầu nghiệp vụ và runtime phải được kiểm chứng riêng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="generic-inheritance-005">Vì sao một lớp không thể kế thừa cùng giao diện với hai đối số kiểu khác nhau</a>

<details>
<summary>Xem chi tiết</summary>

# Vì sao một lớp không thể kế thừa cùng giao diện với hai đối số kiểu khác nhau?

Trong Java Generics, một lớp có thể triển khai nhiều interface, và một generic interface có thể được sử dụng với nhiều đối số kiểu. Tuy nhiên, **cùng một lớp hoặc interface không thể đồng thời kế thừa hai cách tham số hóa khác nhau của cùng một generic interface**. Ví dụ, Java không cho một lớp vừa là `Handler<String>` vừa là `Handler<Integer>` thông qua hai nhánh kế thừa, kể cả khi bạn dự định viết hai phương thức overload `handle(String)` và `handle(Integer)`.

Quy tắc này thường gây bất ngờ khi thiết kế các interface như `Listener<T>`, `Handler<T>`, `Repository<T>`, `Comparable<T>` hoặc `Converter<I, O>`. Nguyên nhân liên quan đến **tính nhất quán của quan hệ kế thừa** và cách Java biên dịch generic bằng **type erasure**. Bài này phân tích lỗi trực tiếp và gián tiếp, lý do overload không cứu được thiết kế, trường hợp kế thừa cùng một đối số kiểu là hợp lệ và các cách mô hình hóa nhu cầu xử lý nhiều kiểu an toàn hơn.

## 1. Nhắc lại: khai báo generic và từng kiểu được tham số hóa

Giả sử có interface:

```java
interface Handler<T> {
    void handle(T value);
}
```

`T` là **tham số kiểu** (*type parameter*) được khai báo trên `Handler<T>`. Những kiểu sau là hai **cách tham số hóa** (*parameterizations*) khác nhau của cùng interface:

```java
Handler<String>
Handler<Integer>
```

Hai dòng trên là tên kiểu minh họa, **không phải câu lệnh Java độc lập**. Với `Handler<String>`, compiler yêu cầu phương thức nhận `String`; với `Handler<Integer>`, yêu cầu nhận `Integer`.

Một lớp xử lý chuỗi hoàn toàn hợp lệ:

```java
final class TextHandler implements Handler<String> {
    @Override
    public void handle(String value) {
        System.out.println(value.toUpperCase());
    }
}
```

Một lớp khác xử lý số nguyên cũng hợp lệ:

```java
final class NumberHandler implements Handler<Integer> {
    @Override
    public void handle(Integer value) {
        System.out.println(value * 2);
    }
}
```

Điểm cần nhớ: **nhiều lớp khác nhau được phép chọn những đối số kiểu khác nhau**. Điều bị cấm là **một kiểu duy nhất cùng lúc kế thừa cả hai cách tham số hóa của cùng interface**.

## 2. Lỗi trực tiếp: một lớp muốn `implements Handler<String>, Handler<Integer>`

Đoạn dưới đây **cố ý không hợp lệ**:

```java
interface Handler<T> {
    void handle(T value);
}

// KHÔNG BIÊN DỊCH:
class Impossible implements Handler<String>, Handler<Integer> {
    public void handle(String value) { }
    public void handle(Integer value) { }
}
```

Lỗi nằm ngay ở quan hệ kế thừa `implements Handler<String>, Handler<Integer>`. Java không cho ghi hai cách tham số hóa khác nhau của cùng generic interface cho một lớp. Với dạng khai báo trực tiếp này, compiler cũng có thể báo rằng interface `Handler` bị lặp trong danh sách `implements`.

Dễ nhầm rằng do `handle(String)` và `handle(Integer)` là hai overload hợp lệ trong lớp thông thường, Java sẽ chấp nhận. Nhưng **overload ở cấp phương thức không giải quyết được điều kiện hợp lệ ở cấp hệ phân cấp kiểu**.

Điều này khác hoàn toàn với việc khai báo hai phương thức overload trong một lớp không triển khai `Handler<T>`:

```java
final class MultiMethod {
    void handle(String text) {
        System.out.println(text);
    }

    void handle(Integer number) {
        System.out.println(number);
    }
}
```

`MultiMethod` là một khai báo lớp Java hợp lệ. Có hai phương thức nhận hai kiểu khác nhau, nhưng không có tuyên bố rằng đối tượng vừa là `Handler<String>` vừa là `Handler<Integer>`. Sự khác biệt nằm ở **hợp đồng interface được kế thừa**, không phải ở khả năng viết overload nói chung.

## 3. Lỗi gián tiếp qua hai nhánh interface: trường hợp khó phát hiện hơn

Ngay cả khi không viết hai lần `Handler<...>` trực tiếp trong cùng danh sách `implements`, quy tắc vẫn áp dụng.

```java
interface Handler<T> {
    void handle(T value);
}

interface TextHandler extends Handler<String> { }

interface NumberHandler extends Handler<Integer> { }

// CỐ Ý SAI:
class BothHandlers implements TextHandler, NumberHandler {
    public void handle(String value) { }
    public void handle(Integer value) { }
}
```

Sơ đồ kế thừa:

```text
             Handler<T>
              /     \
     Handler<String> Handler<Integer>
            |             |
       TextHandler    NumberHandler
             \           /
              BothHandlers
                  X
```

Mỗi nhánh riêng lẻ là hợp lệ: `TextHandler` chọn `String`, `NumberHandler` chọn `Integer`. Nhưng khi `BothHandlers` muốn kế thừa cả hai, hệ phân cấp buộc cùng một lớp phải đồng thời là hai cách tham số hóa không tương thích của `Handler`.

Compiler sẽ từ chối khai báo này. Thông báo cụ thể thay đổi tùy compiler và ngữ cảnh, thường nói rằng `Handler` không thể được kế thừa với hai đối số kiểu khác nhau. **Bổ sung cả hai overload vào thân lớp vẫn không giải quyết được.**

Quy tắc cũng áp dụng khi một trong hai nhánh đi qua **lớp cha** thay vì interface trung gian.

```java
interface Reader<T> {
    T read();
}

abstract class BaseReader<T> implements Reader<T> { }

// CỐ Ý SAI:
class MixedReader extends BaseReader<String>
        implements Reader<Integer> {
    public Integer read() {
        return 1;
    }
}
```

`BaseReader<String>` đã thiết lập quan hệ `Reader<String>`. `MixedReader` không được đồng thời bổ sung `Reader<Integer>`. Lỗi không thể sửa chỉ bằng cách chọn một kiểu trả về của `read()`; chính hệ thống kế thừa đã không nhất quán.

## 4. Quy tắc của Java: không có hai superinterface là các cách tham số hóa khác nhau của cùng một generic interface

Có thể diễn đạt nguyên tắc như sau:

> Trong hệ phân cấp của một kiểu Java, không được kế thừa cùng một generic interface dưới hai đối số kiểu khác nhau.

Ở đây **“cùng một generic interface”** có nghĩa cùng khai báo, ví dụ `Handler<T>`, không phải “hai interface bất kỳ đều có chữ `T`”.

**“Kế thừa”** bao gồm cả đường trực tiếp và đường gián tiếp qua `extends` hoặc `implements`. Nếu một lớp `Child` đã nhận `Handler<String>` từ lớp cha hoặc interface cha, nó không thể nhận thêm `Handler<Integer>` từ nhánh khác.

Tuy nhiên, hai con đường dẫn đến **chính xác cùng cách tham số hóa** là trường hợp khác. Ví dụ `Left extends ValueSource<String>` và `Right extends ValueSource<String>` có thể cùng được triển khai bởi một lớp, miễn các hợp đồng khác không xung đột. Phần tiếp theo có chương trình chạy được để minh họa trường hợp hợp lệ này.

Một lưu ý quan trọng: quy tắc không chỉ tồn tại vì một số cặp phương thức bị trùng sau erasure. **Ngay cả generic interface không khai báo phương thức nào**, hai cách tham số hóa khác nhau của cùng interface vẫn không thể cùng xuất hiện trong một hệ phân cấp.

```java
interface Marker<T> { }
interface TextMarker extends Marker<String> { }
interface NumberMarker extends Marker<Integer> { }

// CỐ Ý SAI, dù Marker<T> không có phương thức nào:
// final class TwoMarkers implements TextMarker, NumberMarker { }
```

Vì vậy, hãy coi đây là **quy tắc nhất quán danh tính superinterface của Java**, không chỉ là một bài toán tránh trùng tên phương thức có thể chữa bằng overload.

## 5. Type erasure giải thích vì sao hai chữ ký không tự trở thành hai hợp đồng độc lập

Java Generics chủ yếu được kiểm tra ở **compile time** và được triển khai bằng **type erasure**. Xét interface:

```java
interface Handler<T> {
    void handle(T value);
}
```

Ở mã nguồn:

- `Handler<String>` cần một thao tác nhận `String`.
- `Handler<Integer>` cần một thao tác nhận `Integer`.

Nhưng khi compiler xóa tham số kiểu không có bound, phương thức generic của `Handler<T>` có chữ ký JVM về cơ bản như:

```java
void handle(Object value);
```

Đoạn trên chỉ là **mô hình chữ ký sau erasure**, không phải chỉ dẫn sửa interface gốc. Có một khai báo interface runtime `Handler`, không có hai interface runtime tách biệt cho `Handler<String>` và `Handler<Integer>`.

Khi một lớp triển khai **một** cách tham số hóa, chẳng hạn `Handler<String>`, compiler có thể tự tạo **bridge method** để giữ khả năng gọi đa hình đúng với chữ ký bị xóa kiểu. Nhưng không nên hiểu rằng compiler sẽ tự tạo hai “slot interface generic” độc lập cho hai đối số kiểu khác nhau. Java chủ động từ chối hệ phân cấp mâu thuẫn, thay vì chờ tới khi chạy mới phát hiện thiếu nhất quán.

### Ví dụ hợp lệ để hiểu bridge method

Lưu tệp **`BridgeDemo.java`**:

```java
public class BridgeDemo {
    interface Handler<T> {
        T transform(T value);
    }

    static final class UppercaseHandler
            implements Handler<String> {
        @Override
        public String transform(String value) {
            return value.toUpperCase();
        }
    }

    public static void main(String[] args) {
        Handler<String> handler = new UppercaseHandler();
        System.out.println(handler.transform("java"));
    }
}
```

Biên dịch và chạy:

```bash
javac -encoding UTF-8 BridgeDemo.java
java BridgeDemo
```

`stdout`:

```text
JAVA
```

Ở mã nguồn, `UppercaseHandler` chỉ cần viết `String transform(String)`. Sau erasure, interface có phương thức dạng `Object transform(Object)`, vì vậy compiler tạo một bridge method tổng hợp ở lớp triển khai để bảo toàn lời gọi qua `Handler<String>`.

Có thể xem chi tiết bytecode:

```bash
javap -p -v 'BridgeDemo$UppercaseHandler'
```

Lệnh trên sử dụng cách đặt tên class file của lớp lồng `BridgeDemo$UppercaseHandler` trong môi trường shell có hỗ trợ dấu nháy đơn; nếu ở Windows PowerShell, tên này cũng được bảo toàn trong nháy đơn. Với compiler `javac`, output có cờ `ACC_BRIDGE` và `ACC_SYNTHETIC` ở phương thức cầu nối tương ứng.

**Bridge method là công việc tự động của compiler để hỗ trợ một hợp đồng kế thừa hợp lệ**, không phải cơ chế cho phép kế thừa hai cách tham số hóa khác nhau.

## 6. Trường hợp hợp lệ: hai nhánh cùng kế thừa đúng một đối số kiểu

Hai nhánh interface có thể cùng chỉ về `ValueSource<String>`.

Lưu chương trình độc lập **`SameTypeDiamondDemo.java`**:

```java
public class SameTypeDiamondDemo {
    interface ValueSource<T> {
        T value();
    }

    interface FromDatabase extends ValueSource<String> { }

    interface FromCache extends ValueSource<String> { }

    static final class SharedSource
            implements FromDatabase, FromCache {
        @Override
        public String value() {
            return "ready";
        }
    }

    public static void main(String[] args) {
        SharedSource source = new SharedSource();
        ValueSource<String> generic = source;
        FromDatabase database = source;
        FromCache cache = source;

        System.out.println(generic.value());
        System.out.println(database.value());
        System.out.println(cache.value());
    }
}
```

Biên dịch và chạy bằng Java 17:

```bash
javac -encoding UTF-8 SameTypeDiamondDemo.java
java SameTypeDiamondDemo
```

Kết quả:

```text
ready
ready
ready
```

Mặc dù có hai đường kế thừa, cả hai đều yêu cầu **cùng một kiểu** `ValueSource<String>`. Lớp `SharedSource` chỉ cần thực hiện một hợp đồng `String value()`.

Đối chiếu:

| Hai đường kế thừa | Hợp lệ? | Lý do |
|---|---|---|
| `ValueSource<String>` và `ValueSource<String>` | Có, nếu không có xung đột khác | Cùng một cách tham số hóa. |
| `ValueSource<String>` và `ValueSource<Integer>` | Không | Hai cách tham số hóa khác nhau của cùng `ValueSource`. |
| `Handler<String>` và `Formatter<Integer>` | Có thể | Hai generic interface khác nhau; còn phải xét chữ ký của chúng. |
| `InterfaceA<String>` và `InterfaceB<String>` | Có thể | Cùng đối số `String` nhưng là hai khai báo interface khác nhau. |

Như vậy **không phải cứ có hình thoi kế thừa là lỗi**. Vấn đề nằm ở việc kế thừa **không nhất quán các đối số kiểu của cùng interface generic**.

## 7. Khác generic interface thì có thể sử dụng các đối số kiểu khác nhau

Java cho một lớp triển khai nhiều **generic interface khác nhau**, nếu các phương thức mà chúng yêu cầu có thể được cài đặt nhất quán.

Lưu **`DifferentInterfacesDemo.java`**:

```java
public class DifferentInterfacesDemo {
    interface TextProvider<T> {
        T text();
    }

    interface NumberProvider<T> {
        T number();
    }

    static final class Combined
            implements TextProvider<String>, NumberProvider<Integer> {
        @Override
        public String text() {
            return "hello";
        }

        @Override
        public Integer number() {
            return 42;
        }
    }

    public static void main(String[] args) {
        Combined combined = new Combined();

        TextProvider<String> textProvider = combined;
        NumberProvider<Integer> numberProvider = combined;

        System.out.println(textProvider.text());
        System.out.println(numberProvider.number() + 1);
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 DifferentInterfacesDemo.java
java DifferentInterfacesDemo
```

`stdout`:

```text
hello
43
```

Một lớp `Combined` đồng thời triển khai `TextProvider<String>` và `NumberProvider<Integer>`. Không có lỗi “cùng interface generic với hai đối số kiểu”, vì `TextProvider` và `NumberProvider` là **hai interface khác nhau**.

Cần thận trọng: không phải mọi cặp interface khác nhau đều tự động tương thích. Nếu chúng định nghĩa những phương thức có chữ ký va chạm nhưng kiểu trả về hoặc yêu cầu override không tương thích, Java vẫn có thể báo lỗi. Quy tắc đang học chỉ là một **điều kiện cần của hệ phân cấp**, không thay thế tất cả quy tắc overload, override và method erasure.

## 8. Giải pháp thứ nhất: composition — giữ hai handler riêng và ủy quyền

Nếu yêu cầu nghiệp vụ thực sự cần xử lý cả chuỗi và số nguyên, một giải pháp tự nhiên là dùng **hai đối tượng khác nhau** thực hiện cùng generic interface theo hai cách tham số hóa, rồi cho một lớp điều phối giữ tham chiếu tới chúng.

Thay vì ép một lớp thành cả `Handler<String>` và `Handler<Integer>`, lớp điều phối **chứa** hai handler riêng và cung cấp các hàm nghiệp vụ rõ ràng.

Lưu **`CompositionDemo.java`**:

```java
public class CompositionDemo {
    interface Handler<T> {
        String handle(T value);
    }

    static final class TextHandler implements Handler<String> {
        @Override
        public String handle(String value) {
            return "text=" + value.toUpperCase();
        }
    }

    static final class NumberHandler implements Handler<Integer> {
        @Override
        public String handle(Integer value) {
            return "number=" + (value * 2);
        }
    }

    static final class Router {
        private final Handler<String> textHandler = new TextHandler();
        private final Handler<Integer> numberHandler = new NumberHandler();

        String processText(String value) {
            return textHandler.handle(value);
        }

        String processNumber(Integer value) {
            return numberHandler.handle(value);
        }
    }

    public static void main(String[] args) {
        Router router = new Router();

        System.out.println(router.processText("java"));
        System.out.println(router.processNumber(21));
    }
}
```

Biên dịch và chạy:

```bash
javac -encoding UTF-8 CompositionDemo.java
java CompositionDemo
```

`stdout`:

```text
text=JAVA
number=42
```

Ưu điểm:

1. `TextHandler` là `Handler<String>` và được kiểm tra kiểu tại compile time.
2. `NumberHandler` là `Handler<Integer>` và có hợp đồng kiểu độc lập.
3. `Router` không `implements Handler<String>, Handler<Integer>`; nó chỉ **phối hợp** hai đối tượng.
4. Không có ép kiểu không được kiểm tra, không cần raw type, và mã không bị ràng buộc bởi xung đột superinterface.

Đây thường là lựa chọn tốt nếu hai hành vi có logic khác nhau hoặc có vòng đời/phụ thuộc khác nhau. Composition không phải thủ thuật để “lách compiler”; nó diễn đạt một mô hình đối tượng khác phù hợp hơn với nhu cầu.

## 9. Giải pháp thứ hai: một kiểu chung có ý nghĩa cho toàn bộ các trường hợp

Nếu muốn **một handler duy nhất** xử lý nhiều dạng sự kiện và có một hợp đồng thống nhất, hãy mô hình hóa một **kiểu nghiệp vụ chung**. Thay vì yêu cầu đối tượng làm hai loại `Handler<...>`, nó thực hiện `Handler<Event>` với một phân cấp sự kiện rõ ràng.

Java 17 hỗ trợ `sealed interface` và `record`, thuận tiện để mô tả tập loại sự kiện có giới hạn.

Lưu **`EventHandlerDemo.java`**:

```java
public class EventHandlerDemo {
    interface Handler<T> {
        String handle(T value);
    }

    sealed interface Event permits TextEvent, NumberEvent { }

    record TextEvent(String text) implements Event { }

    record NumberEvent(int number) implements Event { }

    static final class UnifiedHandler implements Handler<Event> {
        @Override
        public String handle(Event event) {
            if (event instanceof TextEvent text) {
                return "text:" + text.text().toUpperCase();
            }
            if (event instanceof NumberEvent number) {
                return "number:" + (number.number() * 2);
            }
            throw new IllegalStateException("Unexpected event");
        }
    }

    public static void main(String[] args) {
        Handler<Event> handler = new UnifiedHandler();

        System.out.println(handler.handle(new TextEvent("java")));
        System.out.println(handler.handle(new NumberEvent(21)));
    }
}
```

Chạy trên JDK 17+:

```bash
javac -encoding UTF-8 EventHandlerDemo.java
java EventHandlerDemo
```

`stdout`:

```text
text:JAVA
number:42
```

Ở đây:

- `Event` là kiểu chung đại diện cho dữ liệu mà handler chấp nhận.
- `TextEvent` và `NumberEvent` là hai kiểu cụ thể trong phân cấp.
- `UnifiedHandler` thực hiện **duy nhất `Handler<Event>`**, nên không vi phạm quy tắc kế thừa generic.
- Phép `instanceof` với pattern variable được hỗ trợ từ Java 16, có sẵn trong Java 17.
- Các nhánh `if` thể hiện logic nghiệp vụ rõ ràng; tính đúng đắn của chúng do chương trình chịu trách nhiệm, không tự được đảm bảo hoàn toàn bởi generic.

Có thể dùng `Handler<Object>` nếu yêu cầu thực sự là chấp nhận mọi đối tượng, nhưng kiểu `Object` làm hợp đồng rộng và giảm khả năng giới hạn đầu vào. Một supertype nghiệp vụ như `Event` thường tốt hơn khi tập đầu vào được xác định.

## 10. Giải pháp thứ ba: giữ lớp triển khai generic và chuyên biệt hóa ở nơi tạo

Đôi khi cùng một thuật toán xử lý được mọi kiểu mà không cần phân biệt `String` hay `Integer`. Khi đó, lớp triển khai nên **tiếp tục generic**:

```java
interface Handler<T> {
    T handle(T value);
}

final class IdentityHandler<T> implements Handler<T> {
    @Override
    public T handle(T value) {
        return value;
    }
}
```

Đây là các khai báo minh họa. Bây giờ tạo **hai đối tượng** của cùng lớp generic:

```java
Handler<String> texts = new IdentityHandler<>();
Handler<Integer> numbers = new IdentityHandler<>();
```

Mỗi đối tượng có một hợp đồng kiểu được xác định tại nơi sử dụng. Không có **một đối tượng hoặc một lớp** được tuyên bố kế thừa đồng thời hai cách tham số hóa của `Handler<T>`.

Khi thiết kế lớp generic, nhớ phân biệt:

- `class IdentityHandler<T> implements Handler<T>`: khai báo lớp generic chuyển tiếp tham số kiểu của nó vào interface.
- `Handler<String> h = new IdentityHandler<>()`: một lần **sử dụng** lớp ấy với đối số kiểu `String`.
- `Handler<Integer> h = new IdentityHandler<>()`: lần sử dụng khác với kiểu `Integer`.
- `class Impossible implements Handler<String>, Handler<Integer>`: cố tạo **một quan hệ kế thừa không hợp lệ**, khác bản chất với hai lần khởi tạo ở trên.

Có thể chuyên biệt hóa qua lớp con một lần theo một kiểu cụ thể. Ví dụ `class StringIdentity extends IdentityHandler<String>` là hợp lệ nếu `IdentityHandler<T>` không bị khai báo `final`. Tuy nhiên, lớp con đó vẫn không được tự thêm `implements Handler<Integer>` vì đã kế thừa `Handler<String>` từ lớp cha.

## 11. Vì sao wildcard `<?>` và raw type không khắc phục được lỗi kế thừa?

### 11.1. Wildcard giúp diễn đạt kiểu chưa biết ở vị trí sử dụng

Wildcard thích hợp khi cần giữ một tham chiếu tới handler mà **chưa biết** đối số kiểu:

```java
Handler<?> anyHandler;
```

Đây là một đoạn khai báo biến trong ngữ cảnh có `Handler<T>`. Nó không khai báo thêm một interface mới, cũng không làm `Handler<String>` và `Handler<Integer>` trở thành hai vai trò mà cùng một lớp được phép kế thừa.

Nếu phương thức là `void handle(T value)`, một tham chiếu `Handler<?>` **không cho phép tùy tiện gọi** `handle("text")` hay `handle(42)`, vì compiler không biết tham số kiểu đã bị wildcard che đi là gì. Có thể lưu các handler khác nhau trong một tập hợp các tham chiếu wildcard, nhưng khi thực sự gọi cần giữ **mối liên hệ giữa handler và kiểu dữ liệu đầu vào**.

Ví dụ helper generic:

```java
static <T> void dispatch(Handler<T> handler, T value) {
    handler.handle(value);
}
```

Đoạn này là một **phương thức minh họa**. Khi truyền vào `Handler<String>` cùng `String`, compiler có thể kiểm tra quan hệ kiểu. Nếu chỉ có `Handler<?>` và một đối tượng đầu vào bất kỳ, cần thêm thông tin hoặc một mô hình đóng gói cặp handler–giá trị phù hợp.

### 11.2. Raw type bỏ kiểm tra nhưng không làm hệ phân cấp trở nên đúng

Raw type là cách viết `Handler` không có đối số kiểu. Nó tồn tại chủ yếu để tương thích mã Java trước generics.

Không nên sửa thiết kế bằng việc bỏ `<String>` hoặc `<Integer>` chỉ để né lỗi. Raw type làm mất hợp đồng kiểu và có thể gây cảnh báo `rawtypes`, `unchecked`, thậm chí khiến lỗi kiểu xuất hiện lúc chạy. Nếu quan hệ kế thừa ban đầu mâu thuẫn, phải sửa **mô hình đối tượng**, không phải che giấu đối số generic.

### 11.3. Ép kiểu không kiểm tra không biến lớp thành interface khác

Một cast như `(Handler<Integer>) (Object) handlerOfString` có thể vượt qua một số kiểm tra biên dịch bằng cảnh báo hoặc thao tác không an toàn, nhưng **không thay đổi cấu trúc interface mà lớp thực sự triển khai**. Giá trị được truyền sai kiểu có thể gây `ClassCastException` khi đi qua bridge method hoặc mã xử lý. Đây không phải giải pháp cho bài toán kế thừa hai cách tham số hóa.

## 12. Câu chuyện erasure còn ảnh hưởng đến overload như thế nào?

Ngay cả khi không kế thừa interface, Java cũng cấm một số overload có cùng chữ ký sau type erasure. Ví dụ **cố ý sai**:

```java
import java.util.List;

class WrongOverloads {
    void process(List<String> texts) { }

    // Không được khai báo cùng lúc:
    // void process(List<Integer> numbers) { }
}
```

Nếu bỏ dấu chú thích ở phương thức thứ hai, compiler báo va chạm chữ ký: cả hai cùng erasure về tham số kiểu `List`. Java không dùng đối số generic `String` hay `Integer` để phân biệt hai phương thức có cùng tên ở runtime.

Tuy nhiên, cần phân biệt hai quy tắc:

- **Name clash do erasure**: có thể xuất hiện khi định nghĩa hoặc kế thừa những phương thức có chữ ký bị xóa kiểu giống nhau.
- **Không được kế thừa hai cách tham số hóa khác nhau của cùng một generic interface**: là ràng buộc của hệ phân cấp kiểu. Quy tắc này còn áp dụng khi interface **không có phương thức nào**, như `Marker<T>` đã minh họa.

Hiểu sự khác nhau giúp tìm nguyên nhân gốc. Không nên đọc mọi thông báo biên dịch về generic rồi kết luận “chỉ cần đổi tên hoặc overload thêm phương thức”.

## 13. Những lỗi thường gặp và cách xử lý

| Tình huống | Vấn đề | Hướng xử lý |
|---|---|---|
| `implements Handler<String>, Handler<Integer>` | Hai cách tham số hóa khác nhau của cùng interface. | Dùng hai handler độc lập, composition hoặc một kiểu chung. |
| Hai interface cha lần lượt `extends Handler<String>` và `extends Handler<Integer>` | Xung đột kế thừa **gián tiếp**. | Thiết kế lại phân cấp; đừng cố ghép hai nhánh vào cùng lớp. |
| `extends Base<String> implements Handler<Integer>` khi `Base<T> implements Handler<T>` | Lớp cha đã mang `Handler<String>`. | Giữ cùng đối số kiểu hoặc bỏ quan hệ xung đột. |
| Viết thêm `handle(String)` và `handle(Integer)` | Overload không sửa được quan hệ superinterface. | Không tuyên bố kế thừa hai `Handler<...>`; dùng overload trên lớp điều phối nếu cần. |
| Tưởng hai interface khác nhau cũng luôn bị cấm | Quy tắc áp vào cùng **một khai báo generic interface**. | `A<String>` và `B<Integer>` có thể cùng xuất hiện nếu phương thức tương thích. |
| Hai nhánh cùng `Handler<String>` mà nghĩ luôn lỗi | Kế thừa cùng một cách tham số hóa có thể hợp lệ. | Kiểm tra xung đột phương thức/default riêng nếu có. |
| Tưởng interface rỗng `Marker<T>` miễn trừ quy tắc | Kế thừa hai đối số kiểu khác nhau vẫn không hợp lệ. | Không tạo quan hệ kế thừa mâu thuẫn dù interface không có phương thức. |
| Dùng `Handler<?>` rồi truyền tùy ý `String`/`Integer` | Wildcard là kiểu chưa biết, không xác nhận kiểu tham số để ghi. | Giữ thông tin kiểu bằng phương thức generic hoặc API chuyên biệt. |
| Dùng raw `Handler` hoặc cast không kiểm tra | Mất bảo vệ compile time và có nguy cơ lỗi runtime. | Giữ các interface tham số hóa rõ ràng; sửa thiết kế gốc. |
| Cố overload `process(List<String>)` và `process(List<Integer>)` | Name clash do erasure. | Đổi tên phương thức hoặc thiết kế một API generic có chữ ký phù hợp. |

## 14. Checklist thiết kế khi cần nhiều vai trò generic

Khi gặp yêu cầu một lớp xử lý nhiều kiểu, hãy kiểm tra:

1. **Có đang kế thừa cùng generic interface qua hai đường không?** Tra toàn bộ `extends`/`implements` liên quan, không chỉ danh sách trực tiếp.
2. **Các đối số kiểu cuối cùng có giống nhau hoàn toàn không?** Hai nhánh đều là `Source<String>` có thể hợp lệ; một nhánh `Source<String>` và một nhánh `Source<Integer>` thì không.
3. **Đó có phải là hai generic interface thật sự khác nhau?** Nếu là `TextProvider<String>` và `NumberProvider<Integer>`, xét tiếp chữ ký để quyết định có thể triển khai chung không.
4. **Một đối tượng có thực sự cần đóng hai vai trò không?** Nếu chỉ cần phối hợp hai chiến lược, composition thường đơn giản hơn kế thừa.
5. **Có một supertype nghiệp vụ tự nhiên không?** Nếu xử lý các loại sự kiện chung, `Handler<Event>` có thể hợp lý hơn `Handler<Object>` hoặc hai cách tham số hóa.
6. **Có thể dùng cùng một thuật toán generic cho nhiều instance không?** `IdentityHandler<T>` có thể tạo các đối tượng `IdentityHandler<String>` và `IdentityHandler<Integer>` độc lập.
7. **Compiler có đang báo name clash do erasure không?** Kiểm tra các phương thức overload và bridge method; tránh sửa một lỗi kế thừa bằng cách gây thêm lỗi chữ ký.
8. **Có đang dùng raw type, wildcard hoặc unchecked cast để giấu vấn đề không?** Những kỹ thuật này không thay đổi ràng buộc kế thừa và có thể khiến API kém an toàn hơn.

## 15. Tự kiểm tra

**Câu 1.** Vì sao đoạn sau không hợp lệ?

```java
interface Consumer<T> {
    void accept(T value);
}
interface TextConsumer extends Consumer<String> { }
interface IntegerConsumer extends Consumer<Integer> { }

// class Both implements TextConsumer, IntegerConsumer { ... }
```

**Câu 2.** Hai interface `A extends Provider<String>` và `B extends Provider<String>` có nhất thiết xung đột khi một lớp `implements A, B` không?

**Câu 3.** Vì sao hai phương thức `handle(String)` và `handle(Integer)` trong một lớp không giải quyết được lỗi `implements Handler<String>, Handler<Integer>`?

**Câu 4.** Nếu `interface Marker<T> {}` không có phương thức, vì sao vẫn không thể kết hợp `Marker<String>` và `Marker<Integer>` vào một hệ phân cấp?

**Câu 5.** Nêu hai hướng thiết kế thay thế cho một lớp muốn xử lý cả thông điệp chuỗi và thông điệp số nguyên.

**Câu 6.** `Handler<?>` có nghĩa một handler nhận được mọi loại giá trị không? Nếu không, cần làm gì để gọi phương thức tiêu thụ dữ liệu an toàn?

**Gợi ý đáp án:**

1. `Both` sẽ kế thừa `Consumer<String>` và `Consumer<Integer>` thông qua hai nhánh — hai cách tham số hóa khác nhau của cùng interface.
2. Không. Nếu hai nhánh giữ đúng một cách tham số hóa và các yêu cầu phương thức không xung đột, lớp có thể triển khai cả hai.
3. Vì quy tắc kiểm tra **quan hệ superinterface** xảy ra độc lập với việc thân lớp có overload phù hợp hay không.
4. Vì đây là ràng buộc cấu trúc hệ phân cấp Java, không chỉ là việc có phương thức bị xung đột sau erasure.
5. Composition với hai đối tượng `Handler<String>`/`Handler<Integer>`, hoặc một `Handler<Event>` với kiểu sự kiện chung. Ngoài ra, có thể dùng nhiều instance của lớp `Handler<T>` generic.
6. Không. `<?>` che đối số kiểu nên compiler không biết giá trị nào hợp lệ để truyền vào phương thức nhận `T`; phải giữ hoặc khôi phục mối quan hệ kiểu qua API generic hoặc mô hình đối tượng đúng.

**Điểm cần nhớ:** Một generic interface có thể được sử dụng với nhiều đối số kiểu **ở những đối tượng và hệ phân cấp khác nhau**, nhưng **một kiểu Java không được kế thừa cùng một generic interface với hai đối số kiểu khác nhau**, kể cả qua các nhánh gián tiếp. Type erasure giải thích những khó khăn về phương thức JVM; quy tắc nhất quán hệ phân cấp còn chặt chẽ hơn xung đột chữ ký. Khi cần xử lý nhiều kiểu, hãy chọn **composition, các interface độc lập hoặc một kiểu nghiệp vụ chung**, thay vì bỏ kiểm tra generics.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="generic-inheritance-006">Kiểu thành viên được thay thế thế nào qua các mức kế thừa</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểu thành viên được thay thế thế nào qua các mức kế thừa trong Java Generics

## 1. Vấn đề cốt lõi

Khi một lớp generic được kế thừa, **tham số kiểu của lớp cha** có thể được điền bằng một kiểu cụ thể, bằng tham số kiểu của lớp con, hoặc bằng một kiểu tham số hóa phức tạp hơn. Trình biên dịch Java dùng quan hệ **extends** / **implements** để xác định kiểu tĩnh của những thành viên kế thừa tại mỗi mức.

Ví dụ đơn giản:

~~~java
class Parent<T> {
    protected T value;

    T get() {
        return value;
    }

    void set(T value) {
        this.value = value;
    }
}

class Child extends Parent<String> {}
~~~

Trong ngữ cảnh **Child**, các thành viên kế thừa được nhìn như:

- Field **value** có kiểu tĩnh **String** ở các biểu thức truy cập hợp lệ.
- Method **get()** có kiểu trả về **String**.
- Method **set(String value)** nhận String, không phải một Object tùy ý.

Đây là sự **thế đối số kiểu** (*type substitution*) theo khai báo **Child extends Parent<String>**. Parent<T> không phải được sao chép thành một class mới Parent<String> ở runtime; Java thực hiện việc kiểm tra và suy luận quan hệ kiểu khi biên dịch.

Bài học sẽ làm rõ:

1. Cách thay T bằng đối số kiểu ở một cấp kế thừa.
2. Cách kết hợp các phép thay thế khi đi qua **nhiều cấp**.
3. Vì sao một thành viên kế thừa có thể là **List<String>**, **Map<String, Integer>** thay vì chỉ String hay Integer.
4. Ảnh hưởng tới field, getter/setter, override, overloaded method và generic method riêng.
5. Kế thừa một phần tham số kiểu, đổi tên, đổi thứ tự tham số, giới hạn bound và triển khai interface.
6. Giới hạn của raw type, invariance, erasure, bridge method và cách đọc API cho đúng.

Tất cả chương trình hoàn chỉnh dưới đây dùng Java 17 trở lên. Các đoạn ghi “minh họa lỗi” không được biên dịch như chương trình chạy.

## 2. Phân biệt khai báo type parameter và sử dụng type argument

Trong đoạn:

~~~java
class Parent<T> {}
class Middle<U> extends Parent<U> {}
class Leaf extends Middle<String> {}
~~~

Ta có ba vị trí với ba ý nghĩa:

- **<T>** ngay sau Parent: Parent khai báo type parameter **T**.
- **<U>** ngay sau Middle: Middle khai báo type parameter **U** của riêng mình.
- **Parent<U>** trong **extends Parent<U>**: Middle dùng U làm **type argument** để lấp vào vị trí T của Parent.
- **Middle<String>** trong extends của Leaf: Leaf dùng **String** làm type argument để lấp vào U.

Khi đi từ Leaf lên Parent, ta thực hiện hai bước:

~~~text
Leaf extends Middle<String>
Middle<U> extends Parent<U>

Ở Leaf:
  U := String
  T := U := String

Suy ra Leaf kế thừa Parent<String>.
~~~

Việc T và U khác tên không ảnh hưởng kết quả. Tên type variable được khai báo ở từng cấp, còn điều quan trọng là **vị trí** và biểu thức type argument trong mệnh đề extends.

Đừng đọc **extends Parent<U>** như “Parent tự khai báo U” hoặc “U đổi tên thành T vĩnh viễn”. Đó chỉ là một quan hệ giữa hai khai báo generic. Parent vẫn có một type parameter riêng là T; Middle chọn một cách biểu diễn T thông qua U.

## 3. Thế kiểu ở một cấp: field và method thường

Xét:

~~~java
class Box<T> {
    protected T item;

    Box(T item) {
        this.item = item;
    }

    T get() {
        return item;
    }

    void replace(T value) {
        item = value;
    }
}

class StringBox extends Box<String> {
    StringBox(String value) {
        super(value);
    }

    void printUppercase() {
        System.out.println(get().toUpperCase());
    }
}
~~~

Khi compiler kiểm tra **StringBox**, nó biết Box được dùng với **T = String**. Vì thế get() trả String và có thể gọi **toUpperCase()** mà không cast. **replace(T)** được nhìn qua StringBox như method nhận String.

Constructor của Box cũng dùng T. Tại **super(value)** của StringBox, compiler kiểm tra tham số constructor theo kiểu String; tuy nhiên **constructor không được kế thừa**, nên StringBox phải tự khai báo constructor hoặc dựa vào một constructor khác hợp lệ.

Mã nguồn Box<T> chỉ được khai báo một lần. Java không cần tạo một phiên bản source riêng của Box cho từng kiểu. Khi tham chiếu qua **Box<String>**, phương thức get vẫn được kiểm tra có kiểu trả về String.

Không phải mọi thành viên được “thay” như nhau:

| Thành viên khai báo trong Box<T> | Nhìn từ StringBox kế thừa Box<String> |
| --- | --- |
| Field instance **T item** | Giá trị field có kiểu tĩnh String |
| Method instance **T get()** | Kiểu trả về String |
| Method instance **void replace(T)** | Tham số là String |
| Method **List<T> all()** | Kiểu trả về List<String> |
| Method **Map<String,T> map()** | Kiểu trả về Map<String,String> |
| Generic method **<R> R convert(Function<T,R>)** | T được thế thành String, R vẫn là type parameter của từng lời gọi method |
| Static method **static <U> U echo(U)** | U độc lập, không chịu tác động của T = String |

Một field **private** cũng có type được xác lập trong định nghĩa class cha, nhưng lớp con không được phép truy cập trực tiếp field ấy vì luật truy cập Java. Quan hệ thay kiểu và **quyền truy cập** là hai vấn đề khác nhau.

## 4. Nhiều mức kế thừa: phép thế kiểu được hợp thành

Đây là trường hợp thường gây khó hiểu:

~~~java
import java.util.List;

class Base<T> {
    protected final T data;

    Base(T data) {
        this.data = data;
    }

    T get() {
        return data;
    }
}

class Middle<U> extends Base<List<U>> {
    Middle(List<U> items) {
        super(items);
    }

    U first() {
        return get().get(0);
    }
}

class Final extends Middle<String> {
    Final(List<String> names) {
        super(names);
    }
}
~~~

Có ba cấp:

~~~text
Base<T>           : T
    ^
    | T := List<U>
Middle<U>         : U
    ^
    | U := String
Final             : không khai báo type parameter mới
~~~

Bước 1: **Middle<U> extends Base<List<U>>**. T của Base không phải U, mà là **List<U>**. Trong Middle, **get() trả List<U>**, và **data** mang kiểu List<U>.

Bước 2: **Final extends Middle<String>**. Ở Final, U của Middle được điền thành String.

Bước 3: Thế tiếp U vào biểu thức **List<U>**: kết quả **List<String>**. Vì thế Final có quan hệ kế thừa với **Base<List<String>>**, không phải Base<String>.

Bảng theo dõi:

| Thành viên | Trong Base<T> | Nhìn qua Middle<U> | Nhìn qua Final |
| --- | --- | --- | --- |
| **data** | T | List<U> | List<String> |
| **get()** | T | List<U> | List<String> |
| **first()** | Chưa có | U | String |
| **Middle(List<U>)** | Không thuộc Base | Constructor nhận List<U> | Constructor của Final gọi super với List<String> |

**Lưu ý:** Bảng mô tả kiểu tĩnh và hợp đồng source code. Đây không phải việc JVM thay type parameters trong field và method của một bản class runtime mới cho Final.

## 5. Ví dụ chạy độc lập: ba cấp, kiểu phần tử lồng nhau

Lưu thành **MultiLevelTypesDemo.java**:

~~~java
import java.util.List;

public class MultiLevelTypesDemo {
    static class Base<T> {
        private final T data;

        Base(T data) {
            this.data = data;
        }

        T get() {
            return data;
        }

        void show(T value) {
            System.out.println(value);
        }
    }

    static class Middle<U> extends Base<List<U>> {
        Middle(List<U> data) {
            super(data);
        }

        U first() {
            return get().get(0);
        }
    }

    static final class Final extends Middle<String> {
        Final(List<String> data) {
            super(data);
        }

        String upperFirst() {
            return first().toUpperCase();
        }
    }

    public static void main(String[] args) {
        Final child = new Final(List.of("java", "spring"));

        List<String> names = child.get();
        String first = child.first();
        Base<List<String>> parent = child;

        System.out.println(names);
        System.out.println(first);
        System.out.println(child.upperFirst());
        parent.show(List.of("typed"));
    }
}
~~~

Biên dịch/chạy:

~~~bash
javac -encoding UTF-8 MultiLevelTypesDemo.java
java MultiLevelTypesDemo
~~~

Stdout chính xác:

~~~text
[java, spring]
java
JAVA
[typed]
~~~

Phân tích:

- **child.get()** trả List<String>, vì T của Base được thay bằng List<U>, rồi U được thay bằng String.
- **child.first()** trả String, vì first được khai báo trả U trong Middle<U>.
- **child.upperFirst()** gọi toUpperCase được vì compiler biết first() là String.
- **Base<List<String>> parent = child** hợp lệ: Final là subtype của Base<List<String>> qua hai tầng extends.
- **parent.show(List.of("typed"))** hợp lệ vì show(T) tại biến parent là show(List<String>).
- Biểu thức **Base<String> wrong = child** sẽ không biên dịch; Final không phải một Base<String>.

Đoạn demo dùng các lớp lồng static để gói chương trình trong một file. Từng class Base<T>, Middle<U>, Final vẫn có quan hệ kế thừa generic bình thường; static ở đây chỉ giải quyết quan hệ với lớp chương trình bao ngoài.

## 6. Cách tính phép thế kiểu một cách có hệ thống

Với chuỗi:

~~~java
class Root<A> {}
class Middle<B> extends Root<java.util.List<B>> {}
class Lower<C> extends Middle<java.util.Set<C>> {}
class Bottom extends Lower<String> {}
~~~

Hãy đi từ dưới lên và thế lần lượt:

~~~text
Bottom -> Lower<String>:
    C := String

Lower<C> -> Middle<Set<C>>:
    B := Set<C>
    sau C := String, B := Set<String>

Middle<B> -> Root<List<B>>:
    A := List<B>
    sau B := Set<String>, A := List<Set<String>>

Kết quả:
    Bottom là subtype của Root<List<Set<String>>>.
~~~

Phép thế diễn ra trên **biểu thức kiểu hoàn chỉnh**. Nếu type parameter nằm sâu trong List, Map hoặc Set, ta thay nó ở vị trí tương ứng rồi giữ nguyên phần vỏ kiểu.

Một cách ghi tổng quát:

~~~text
Root<A>
  Middle<B> extends Root<F(B)>
  Lower<C> extends Middle<G(C)>
  Bottom extends Lower<X>

Tại Bottom: A := F(G(X)).
~~~

F và G ở đây chỉ là ký hiệu mô tả “biểu thức kiểu” (ví dụ F(B) = List<B>, G(C) = Set<C>), **không phải cú pháp khai báo type constructor tổng quát trong Java**.

Với nhiều type parameters, hãy lập bảng riêng cho từng tên để tránh nhầm lẫn và ưu tiên đi theo mệnh đề extends thực tế thay vì đoán theo tên lớp.

## 7. Nhiều tham số kiểu và thay đổi thứ tự

Xét một lớp cha nhận hai tham số kiểu:

~~~java
class Pair<A, B> {
    A left;
    B right;

    Pair(A left, B right) {
        this.left = left;
        this.right = right;
    }

    A left() { return left; }
    B right() { return right; }
}

class Flipped<X, Y> extends Pair<Y, X> {
    Flipped(Y left, X right) {
        super(left, right);
    }
}

class Concrete extends Flipped<Integer, String> {
    Concrete(String left, Integer right) {
        super(left, right);
    }
}
~~~

Đọc chậm từng bước:

1. Flipped<X,Y> extends Pair<Y,X>: **A = Y**, **B = X**.
2. Concrete extends Flipped<Integer,String>: **X = Integer**, **Y = String**.
3. Ghép hai bước: **A = String**, **B = Integer**.
4. Concrete vì thế có quan hệ subtype với **Pair<String,Integer>**.

Khi đọc thành viên kế thừa qua Concrete:

- **left()** trả String.
- **right()** trả Integer.
- **left** là String, **right** là Integer (tùy quyền truy cập).
- Constructor Concrete nhận String trước, Integer sau do chính chữ ký đã khai báo; nó không tự kế thừa constructor của Pair.

Không được suy ra rằng Concrete là Pair<Integer,String> chỉ vì Concrete kế thừa Flipped<Integer,String>. **Mệnh đề extends Pair<Y,X> đã đảo hai vị trí**.

Một lớp con cũng có thể **giữ linh hoạt một tham số và cố định tham số kia**:

~~~java
class KeyValue<K, V> {
    K key;
    V value;
}

class StringKeyValue<V> extends KeyValue<String, V> {}
class LongStringValue extends StringKeyValue<String> {}
~~~

Ở StringKeyValue<V>: K = String, V còn tự do. Ở LongStringValue: V = String, suy ra cả K và V của KeyValue đều là String. Tên LongStringValue chỉ là tên ví dụ; việc tham số hóa thực tế phải được suy từ extends, **không phải từ tên class**.

## 8. Ví dụ chạy: hai tham số kiểu qua lớp trung gian

Lưu thành **PermutationDemo.java**:

~~~java
public class PermutationDemo {
    static class Pair<A, B> {
        private final A left;
        private final B right;

        Pair(A left, B right) {
            this.left = left;
            this.right = right;
        }

        A left() { return left; }
        B right() { return right; }
    }

    static class Swapped<X, Y> extends Pair<Y, X> {
        Swapped(Y left, X right) {
            super(left, right);
        }
    }

    static final class Fixed extends Swapped<Integer, String> {
        Fixed(String name, Integer score) {
            super(name, score);
        }
    }

    public static void main(String[] args) {
        Fixed item = new Fixed("Alice", 91);
        Pair<String, Integer> parent = item;

        String name = parent.left();
        Integer score = parent.right();

        System.out.println(name);
        System.out.println(score);
        System.out.println(parent.left() + ":" + parent.right());
    }
}
~~~

Chạy:

~~~bash
javac -encoding UTF-8 PermutationDemo.java
java PermutationDemo
~~~

Stdout:

~~~text
Alice
91
Alice:91
~~~

**Fixed extends Swapped<Integer,String>**, còn Swapped<X,Y> extends Pair<Y,X>. Do đó A = String, B = Integer. Phép gán **Pair<String,Integer> parent = item** được kiểm tra tại thời gian biên dịch và không cần ép kiểu.

## 9. Phương thức nhận tham số kiểu của cha cũng được thay thế

Giả sử lớp cha không chỉ trả T mà còn nhận T:

~~~java
class Repository<T> {
    private T current;

    void save(T item) {
        current = item;
    }

    T load() {
        return current;
    }
}

class TextRepository extends Repository<String> {}
~~~

Trong ngữ cảnh TextRepository:

~~~java
TextRepository repo = new TextRepository();
repo.save("ok");
String value = repo.load();
~~~

Compiler xem **save** nhận String, **load** trả String. Nếu viết **repo.save(123)**, code sai kiểu tại compile time.

Với lớp trung gian:

~~~java
class ListRepository<E> extends Repository<java.util.List<E>> {}
class NameRepository extends ListRepository<String> {}
~~~

Tại NameRepository, **save** nhận List<String> và **load** trả List<String>. Nó không nhận trực tiếp String vì T của Repository được điền bằng **toàn bộ List<E>**.

Cần phân biệt **kiểu tham số phương thức** với **giá trị runtime**. Nếu save chấp nhận một List<String>, list đó vẫn có thể mutable hoặc chứa null tùy implementation. Generic substitution không tự tạo invariant “list không rỗng”, “không null” hay “bất biến”.

## 10. Override: chữ ký inherited phải được nhìn sau phép thế

Khi lớp con override một method, Java kiểm tra hợp đồng dựa trên kiểu của generic superclass sau khi áp dụng type arguments.

~~~java
abstract class Provider<T> {
    abstract T provide();
}

class StringProvider extends Provider<String> {
    @Override
    String provide() {
        return "ready";
    }
}
~~~

Provider<T>.provide() có kiểu trả T. Với StringProvider extends Provider<String>, method cần triển khai trả String hoặc một return type tương thích theo luật override.

Một ví dụ đa tầng:

~~~java
abstract class Base<T> {
    abstract T create();
}

abstract class Middle<U> extends Base<java.util.List<U>> {}

class Final extends Middle<String> {
    @Override
    java.util.List<String> create() {
        return java.util.List.of("ok");
    }
}
~~~

Final phải trả **List<String>**, vì T của Base được thế thành List<U>, U thành String. Nếu viết **String create()**, method không override đúng hợp đồng và không thể dùng để triển khai abstract method.

### Covariant return type

Khi method của superclass sau phép thế trả một reference type rộng, override có thể trả subtype phù hợp:

~~~java
abstract class NumberProvider<T extends Number> {
    abstract T get();
}

class IntegerProvider extends NumberProvider<Number> {
    @Override
    Integer get() {
        return 10;
    }
}
~~~

Ở IntegerProvider, T của NumberProvider được chọn là Number; **Integer** là subtype của Number nên kiểu trả về Integer là covariant và hợp lệ.

Nhưng **List<Integer>** không là subtype của **List<Number>**, do generic invariance. Vì vậy không thể chỉ dựa vào quan hệ Integer extends Number để đổi kiểu trả về **List<Number>** thành **List<Integer>** khi override.

### Mức truy cập và checked exceptions

Lớp con không được giảm access level của một method được override. Nếu method trong interface là public, implementation phải public. Với method của class, mức truy cập và checked exceptions cũng phải tuân theo luật Java thông thường. Generic substitution chỉ thay type variables trong chữ ký; nó không bỏ qua các luật override khác.

## 11. Phân biệt override với overload và field hiding

Hai phương thức trùng tên chưa chắc override nhau.

~~~java
class Base<T> {
    void set(T value) {
        System.out.println("base");
    }
}

class TextChild extends Base<String> {
    void set(Integer value) {
        System.out.println("integer");
    }
}
~~~

Trong TextChild có **set(String)** được kế thừa qua Base<String> và **set(Integer)** do TextChild khai báo. Đây là **overloading**: hai signatures nhận kiểu khác nhau.

Nếu TextChild muốn override method nhận String, viết:

~~~java
class TextChild extends Base<String> {
    @Override
    void set(String value) {
        System.out.println("string");
    }
}
~~~

Dùng @Override giúp compiler kiểm tra chủ định. Trong các trường hợp có type erasure và name clash, một overload tưởng hợp lệ còn có thể dẫn đến lỗi nếu hai declarations có cùng erasure; không nên chỉ dựa vào tên biến hoặc cảm giác “khác generic type arguments” để đoán.

**Field hiding** lại là chuyện khác. Nếu lớp con khai báo field cùng tên với field ở cha, Java có thể có **hai field khác nhau** và phép truy cập field được giải quyết theo kiểu tĩnh của biểu thức, không dùng dynamic dispatch như instance method. Generic substitution không biến field hiding thành overriding.

Ví dụ:

~~~java
class Parent<T> {
    T value;
}

class Child extends Parent<String> {
    Integer value;
}
~~~

Child có field value kiểu Integer riêng, còn **((Parent<String>) child).value** truy cập field của Parent có kiểu String. Thiết kế field hiding thường gây nhầm, nên ưu tiên tên khác hoặc đóng gói bằng getter/setter.

## 12. Generic method của lớp cha có scope riêng

Một phương thức có thể tự khai báo type parameter **R**, độc lập với T của class:

~~~java
import java.util.function.Function;

class Box<T> {
    private final T item;

    Box(T item) {
        this.item = item;
    }

    <R> R map(Function<? super T, ? extends R> mapper) {
        return mapper.apply(item);
    }
}

class StringBox extends Box<String> {
    StringBox(String value) {
        super(value);
    }
}
~~~

Ở StringBox, compiler thay T thành String trong method đã kế thừa. Tuy nhiên **R không bị thế thành String**; R vẫn do **mỗi lời gọi map** xác định.

Qua StringBox, chữ ký logic của map là:

~~~text
<R> R map(Function<? super String, ? extends R> mapper)
~~~

Ví dụ:

~~~java
// Minh họa trong một phương thức:
StringBox box = new StringBox("Java");
Integer length = box.map(String::length);
String upper = box.map(String::toUpperCase);
~~~

Lần đầu R được suy luận Integer; lần sau R là String. Đây là điểm phân biệt:

- **T** thuộc class Box<T>, nên khi StringBox extends Box<String>, T đã được xác định.
- **R** thuộc generic method map, nên mỗi lời gọi vẫn tự chọn R.

Một generic method của lớp cha như **<X> X identity(X input)** không liên quan T và vẫn giữ nguyên khả năng chấp nhận nhiều X qua mọi lớp con. Type arguments của class không “thay tất cả dấu <...> xuất hiện trong class cha”.

## 13. Ví dụ chạy: method-level type parameter vẫn độc lập

Lưu thành **InheritedGenericMethodDemo.java**:

~~~java
import java.util.function.Function;

public class InheritedGenericMethodDemo {
    static class Box<T> {
        private final T item;

        Box(T item) {
            this.item = item;
        }

        <R> R map(Function<? super T, ? extends R> mapper) {
            return mapper.apply(item);
        }

        T value() {
            return item;
        }
    }

    static class WordBox extends Box<String> {
        WordBox(String word) {
            super(word);
        }
    }

    public static void main(String[] args) {
        WordBox box = new WordBox("Java");

        Integer length = box.map(String::length);
        String uppercase = box.map(String::toUpperCase);
        String original = box.value();

        System.out.println(length);
        System.out.println(uppercase);
        System.out.println(original);
    }
}
~~~

Biên dịch/chạy:

~~~bash
javac -encoding UTF-8 InheritedGenericMethodDemo.java
java InheritedGenericMethodDemo
~~~

Stdout:

~~~text
4
JAVA
Java
~~~

Quan hệ kiểu: từ WordBox extends Box<String>, T được thế bằng String trong **value()** và phần đầu vào Function của **map**. R vẫn độc lập, có thể là Integer hoặc String tùy mapper. Cả ba biến được gán trực tiếp, không cần cast.

## 14. Interface generic qua nhiều cấp extends/implements

Không chỉ các lớp cha mới tham gia phép thế. Generic interface cũng có thể được tham số hóa xuyên nhiều tầng.

~~~java
interface Source<T> {
    T fetch();
}

interface SequenceSource<E> extends Source<java.util.List<E>> {}

abstract class AbstractSequence<U> implements SequenceSource<U> {}

class StringSequence extends AbstractSequence<String> {
    @Override
    public java.util.List<String> fetch() {
        return java.util.List.of("a", "b");
    }
}
~~~

Lần theo đường đi:

~~~text
StringSequence
  extends AbstractSequence<String>
  implements SequenceSource<String>
  extends Source<List<String>>
~~~

Method fetch được khai báo trong Source<T> trả T. Ở StringSequence, kết quả trở thành **List<String>**. Việc implementation nằm trong class cuối cùng không làm thay đổi hợp đồng của interface đã kế thừa.

Thay thế qua interface với nhiều type parameters có thể phức tạp hơn:

~~~java
interface Transformer<I, O> {
    O apply(I input);
}

interface TextTransformer<T> extends Transformer<T, String> {}

class NumberFormatter implements TextTransformer<Integer> {
    @Override
    public String apply(Integer input) {
        return "n=" + input;
    }
}
~~~

Ở NumberFormatter, T của TextTransformer được thế bằng Integer. Interface cha trở thành Transformer<Integer,String>; vì vậy method apply nhận Integer và trả String.

Nơi gọi có thể dùng:

~~~java
Transformer<Integer, String> converter = new NumberFormatter();
String text = converter.apply(5);
~~~

Không cast vì quan hệ subtype và method signature đã được compiler kiểm tra.

## 15. Tính bất biến: subtype class không đồng nghĩa subtype type arguments

Xét:

~~~java
class Holder<T> {}
class NumberHolder extends Holder<Integer> {}
~~~

Vì NumberHolder extends Holder<Integer>, các phép gán sau đúng:

~~~java
NumberHolder direct = new NumberHolder();
Holder<Integer> asIntegerHolder = direct;
Holder<? extends Number> asNumberReader = direct;
~~~

Nhưng:

~~~java
// Cố ý sai:
// Holder<Number> asNumberHolder = direct;
~~~

**Holder<Integer> không phải subtype của Holder<Number>**. Dù Integer extends Number, type arguments của những generic classes như Holder mặc định **invariant**.

Một trường hợp nhiều cấp:

~~~java
class Root<T> {}
class Mid<U> extends Root<java.util.List<U>> {}
class Leaf extends Mid<Integer> {}
~~~

Leaf là subtype của **Root<List<Integer>>**, không phải **Root<List<Number>>** hay **Root<Collection<Integer>>** chỉ nhờ quan hệ subtype giữa kiểu bên trong.

Nếu API cần linh hoạt ở *nơi sử dụng*, có thể dùng bounded wildcard, ví dụ **Root<? extends List<? extends Number>>** phù hợp với một Root<List<Integer>> ở nhiều tình huống. Tuy nhiên, khi đọc/ghi qua các wildcard, quyền thao tác có thể bị giới hạn. Không nên thay biểu thức extends thực tế bằng supertype rộng rồi giả định mọi phép ghi vẫn hợp lệ.

## 16. Bounds vẫn phải thỏa qua mỗi cấp

Một generic superclass có thể giới hạn type parameter:

~~~java
class NumericBox<T extends Number> {
    protected final T value;

    NumericBox(T value) {
        this.value = value;
    }

    double amount() {
        return value.doubleValue();
    }
}
~~~

Subclass generic phải cung cấp một type argument chắc chắn là subtype của Number:

~~~java
class NumericMiddle<U extends Number> extends NumericBox<U> {
    NumericMiddle(U value) {
        super(value);
    }
}

class IntegerBox extends NumericMiddle<Integer> {
    IntegerBox(Integer value) {
        super(value);
    }
}
~~~

Nếu viết **class Invalid<U> extends NumericBox<U>**, compiler báo lỗi vì U không bound không được bảo đảm là Number. Bound của class cha được kiểm tra tại điểm sử dụng type argument trong extends.

Đôi khi type argument là một kiểu cụ thể luôn đáp ứng bound, nên lớp con không cần khai báo cùng bound:

~~~java
class FixedNumeric<X> extends NumericBox<Integer> {
    FixedNumeric(Integer value) {
        super(value);
    }
}
~~~

Ở FixedNumeric<X>, X độc lập và không tham gia extends; việc X không có bound không ảnh hưởng NumericBox<Integer>. Tuy nhiên khai báo X không được dùng ở đâu sẽ gây rối thiết kế nếu không có mục đích thật.

### Bound liên quan nhiều type parameters

~~~java
class OrderedPair<T extends Comparable<? super T>> {
    T left;
    T right;
}

class TextPair extends OrderedPair<String> {}
~~~

String đáp ứng bound Comparable<? super String>, nên TextPair hợp lệ. Khi mở rộng class generic có các bound phức tạp, hãy kiểm tra **toàn bộ type argument** thay vì chỉ nhìn tên T: các dạng Number, Comparable<? super T>, InterfaceA & InterfaceB có thể giới hạn những cách thay hợp lệ.

Bounds không loại trừ null. Nếu NumericBox<Integer> giữ null, **amount()** có thể ném NullPointerException. Đây là vấn đề giá trị runtime, không phải sai phép thế kiểu.

## 17. Static members không mang type parameter của instance

Type parameter của class cha gắn với phần khai báo generic nhưng không được dùng như một kiểu ở static context của chính class đó.

~~~java
class Base<T> {
    static int instanceCount;

    // static T last; // Không hợp lệ.
    static <U> U echo(U value) {
        return value;
    }
}

class Child extends Base<String> {}
~~~

**Base<String>** và **Base<Integer>** không có hai phiên bản static state riêng. Child kế thừa quan hệ từ Base<String>, nhưng một static field của Base vẫn thuộc Base chung, không được thay kiểu T theo mỗi subtype.

Phương thức **static <U> U echo(U value)** khai báo U riêng của method. Khi gọi **Base.echo("x")** hoặc **Child.echo(1)**, U được suy luận độc lập từ argument, không bị khóa thành String chỉ vì Child extends Base<String>.

Static methods bị **hiding**, không phải overriding động như instance methods. Không nên dùng việc tham số hóa superclass để suy đoán một static method được chuyên biệt theo T.

## 18. Constructor, private member và phần nào thực sự được kế thừa

Mô hình “mọi thứ thuộc cha đều bị thay kiểu” là không chính xác. Khi phân tích generics, cần kết hợp luật kế thừa thông thường:

- **Constructor không được kế thừa**. Lớp con khai báo constructor của mình và dùng super(...) phù hợp.
- Field/method private của superclass không tự trở thành các thành viên truy cập trực tiếp trong subclass. Chúng vẫn tồn tại trong class cha và có thể được gọi gián tiếp qua API public/protected phù hợp.
- Một method không truy cập được vì access modifier sẽ không trở nên truy cập được chỉ nhờ type argument được xác định.
- Instance method được override có dynamic dispatch; field access không có dynamic dispatch giống method.
- Static fields/methods thuộc class, không thuộc một cách tham số hóa riêng T.

Ví dụ:

~~~java
class Base<T> {
    private final T secret;

    Base(T secret) {
        this.secret = secret;
    }

    public T reveal() {
        return secret;
    }
}

class Child extends Base<String> {
    Child(String text) {
        super(text);
    }

    String readSecret() {
        // return secret; // Không hợp lệ: private trong Base.
        return reveal();  // Hợp lệ: inherited public method trả String.
    }
}
~~~

Đúng là phép thế cho biết **secret** trong Base<String> mang kiểu String và **reveal()** trả String. Nhưng Child không được đọc field secret trực tiếp. Đó là giới hạn access control, không liên quan đến sai type substitution.

## 19. Type erasure và bridge method: sự khác nhau giữa nguồn và bytecode

Ở Java source, khi viết:

~~~java
class Base<T> {
    T value() { return null; }
}

class StringBase extends Base<String> {
    @Override
    String value() { return "ok"; }
}
~~~

Compiler hiểu StringBase override method trả String của Base<String>. Nhưng **type erasure** thường thay type variable T không bound bằng Object trong chữ ký bytecode của Base.

Để giữ khả năng dispatch của JVM và hợp đồng override sau erasure, compiler có thể tạo một **bridge method** ở StringBase, đại ý có chữ ký trả Object rồi ủy quyền tới method trả String. Bridge method được tạo tự động; người viết Java không nên tự khai báo method trùng chữ ký bị erase.

Điều này giải thích hai điều cùng đúng:

1. Ở Java source, **String s = new StringBase().value()** hợp lệ mà không có cast thủ công.
2. Runtime **không tạo một class Base<String> riêng** với method value() thực sự trả String ở mọi vị trí bytecode.

Nếu trường hợp có bridge method, bạn có thể quan sát bằng công cụ **javap -c -p** trên file class đã biên dịch. Tên/chữ ký bridge cụ thể là chi tiết triển khai, không nên dựa vào để viết logic ứng dụng.

### Một cảnh báo về overload và erasure

Hai method như **void accept(List<String>)** và **void accept(List<Integer>)** không thể cùng tồn tại như hai overload riêng trong một class vì chúng có cùng erasure. Kế thừa generic nhiều cấp cũng có thể dẫn đến **name clash** khi một subclass khai báo method tưởng như khác kiểu nhưng sau erasure xung đột với inherited member.

Không dùng raw type hay ép kiểu unchecked để “sửa” một name clash. Hãy xác định rõ contract cần override, điều chỉnh signatures phù hợp hoặc chọn tên method khác khi đó thực sự là hai thao tác khác nghĩa.

## 20. Raw type làm mất độ chính xác của phép thế

Với:

~~~java
class Base<T> {
    T get() { return null; }
}

class Good extends Base<String> {}
class Legacy extends Base {}
~~~

Good kế thừa Base<String>, nên **Good.get()** được nhìn từ source là String. Legacy mở rộng **raw Base**; thông tin type argument đã bị bỏ, nên get() thường được nhìn qua chữ ký đã erase là Object.

~~~java
Good good = new Good();
String text = good.get();

Legacy legacy = new Legacy();
Object rawValue = legacy.get();
~~~

Khi cast rawValue sang String, bạn tự chịu rủi ro runtime. Raw inheritance thường có từ code Java cũ trước generics hoặc những API chưa được cập nhật. Trong code mới, nên dùng type arguments đầy đủ.

**Raw type không tương đương wildcard**. Ví dụ Base<?> vẫn là parameterized type với kiểu thành phần chưa xác định, và compiler áp đặt kiểm tra an toàn kiểu; Base raw bỏ qua nhiều kiểm tra. Nếu không biết đối số kiểu, hãy cân nhắc wildcard ở vị trí tham chiếu thích hợp, nhưng mệnh đề extends của một class cần một supertype hợp lệ theo luật Java, không dùng wildcard trực tiếp như **extends Base<?>**.

## 21. Type inference trong chuỗi generic inheritance

Compiler có thể suy luận type arguments của constructors và methods nhờ ngữ cảnh, nhưng **không tự biến một lớp con thành subtype của mọi cách tham số hóa có vẻ tương thích**.

~~~java
class Parent<T> {
    Parent(T value) {}
}

class Child<U> extends Parent<java.util.List<U>> {
    Child(java.util.List<U> values) {
        super(values);
    }
}
~~~

Caller có thể viết:

~~~java
Child<String> child = new Child<>(java.util.List.of("x", "y"));
Parent<java.util.List<String>> parent = child;
~~~

Diamond ở **new Child<>(...)** suy luận U = String từ tham số constructor và kiểu đích. Sau đó quan hệ extends cho biết T của Parent bằng List<U>, tức List<String>.

Nếu dùng **var child = new Child<>(List.of("x"))** trong ngữ cảnh đủ rõ, compiler vẫn suy luận từ constructor argument và không mất type safety chỉ vì var. Tuy nhiên type inference không thay đổi khai báo extends và không chữa được việc truyền sai type arguments.

Cần phân biệt **type inference tại nơi gọi** với **type substitution khi kế thừa**:

- Inference chọn type arguments phù hợp cho một generic constructor/method theo ngữ cảnh.
- Substitution áp dụng các type arguments đã chọn qua những mệnh đề extends/implements để xác định member types của supertypes.

Hai bước cùng tham gia kiểm tra kiểu, nhưng không phải cùng một cơ chế.

## 22. Sai lầm hay gặp

### Lỗi 1: Thấy Child extends Middle<String> rồi cho rằng Base<T> có T = String

Nếu **Middle<U> extends Base<List<U>>**, T phải là List<String>, không phải String. Luôn thế qua đầy đủ từng mệnh đề extends.

### Lỗi 2: Đổi tên type parameter rồi cho rằng kiểu khác đi

~~~java
class Base<T> {}
class Mid<U> extends Base<U> {}
~~~

Tên T và U khác nhau nhưng quan hệ là T := U. Đổi tên chỉ ảnh hưởng cách đọc source; không có chuyển đổi giá trị runtime nào diễn ra.

### Lỗi 3: Nghĩ rằng List<Integer> là List<Number>

Generic types thông thường invariant. Một lớp extends Base<List<Integer>> không đồng thời extends Base<List<Number>> chỉ vì Integer extends Number.

### Lỗi 4: Override với kiểu sai sau phép thế

~~~java
abstract class Base<T> {
    abstract T read();
}

class Wrong extends Base<String> {
    // String mới là kiểu cần trả:
    // @Override Integer read() { return 1; } // Sai.
    @Override String read() { return "ok"; }
}
~~~

Kiểu trả Integer không tương thích với String mà hợp đồng Base<String> yêu cầu.

### Lỗi 5: Cố dùng một tham số generic method như thể đã được class thay

Với **<R> R map(Function<T,R>)**, chỉ T bị thế qua class inheritance; R là một type parameter khác thuộc method. Phần R vẫn được caller xác định.

### Lỗi 6: Dùng constructor của superclass như constructor được kế thừa

Constructor không được kế thừa. Dù superclass khai báo Base(T), subclass vẫn cần constructor đúng và gọi **super(...)** thích hợp khi cần.

### Lỗi 7: Dùng private field từ subclass

Thế kiểu chỉ xác định kiểu tĩnh. Nó không vượt qua private access; gọi getter hợp lệ hoặc chỉnh API theo thiết kế đóng gói.

### Lỗi 8: Dùng raw Base thay cho Base<String>

Raw type xóa nhiều thông tin generic ở source và đưa kết quả quay lại Object/erasure. Dùng một parameterized supertype đầy đủ nếu biết kiểu.

### Lỗi 9: Trông đợi generic static field phân chia theo T

Static fields không thuộc riêng mỗi type argument. Một Base<Integer> và Base<String> không có static fields được chuyên biệt tự động theo T.

### Lỗi 10: Nhầm field hiding với method override

Method instance có thể override và dispatch động; field cùng tên ở lớp con che field lớp cha nhưng không override. Kiểu tĩnh của expression quyết định field nào được truy cập.

### Lỗi 11: Cho rằng mọi member đều được kế thừa trực tiếp

Constructors và private members không thuộc cùng tập inherited accessible members như public/protected methods. Luật kế thừa và access modifiers vẫn áp dụng ngoài generics.

### Lỗi 12: Dùng cast không kiểm tra để giả mạo supertype

Một **Base<List<Integer>>** không thể được xem an toàn thành **Base<List<String>>** chỉ vì cùng class Base sau erasure. Cast không đúng có thể dẫn tới ClassCastException khi đọc dữ liệu. Hãy sửa type relationships thay vì che lỗi.

## 23. Phương pháp đọc một hierarchy thực tế

Khi gặp một chuỗi kế thừa chưa quen, làm theo trình tự:

1. **Tìm generic declaration gốc.** Ghi tên type parameters của class hoặc interface có member đang quan tâm, ví dụ Base<T> với T get().
2. **Đọc mệnh đề extends/implements tại cấp ngay dưới.** Ghi chính xác type argument đi vào từng vị trí, kể cả List<U>, Map<K,V> hay một parameterized type lồng sâu.
3. **Lặp ở các cấp tiếp theo.** Thay type variables bên trong biểu thức kiểu đã ghi bằng type arguments của cấp dưới.
4. **Không bỏ mất các lớp bọc generic.** Nếu T = List<U>, sau khi U = String thì T = List<String>, không phải String.
5. **Xác định member được kế thừa hay override.** Kiểm tra private, static, constructors và access levels; đừng tưởng mọi thành viên đều dispatch giống nhau.
6. **Nếu method có <R>, ghi riêng R.** Các type parameters của method có scope và inference riêng.
7. **Kiểm tra bounds và invariance.** Một mệnh đề extends phải thỏa bound; List<Integer> không tự đổi thành List<Number>.
8. **Kiểm tra output bằng kiểu tham chiếu hợp lệ.** Thử khai báo Base<CorrectType> parent = child để compiler xác nhận kết quả suy luận.
9. **Xem erasure khi thật cần.** Nếu gặp name clash hoặc bridge method, nhớ source type checking và bytecode erasure là hai lớp khác nhau.
10. **Ưu tiên type arguments tường minh trong tài liệu.** Một bảng thế kiểu thường dễ hiểu hơn một chuỗi type declarations dài không được chú giải.

Ví dụ bài toán:

~~~java
class Root<T> {
    T read() { return null; }
}

class Level1<A> extends Root<java.util.Map<String, A>> {}

class Level2<B> extends Level1<java.util.List<B>> {}

class Final extends Level2<Integer> {}
~~~

Bảng:

| Cấp | Kiểu tham số được điền |
| --- | --- |
| Level2<Integer> | B = Integer |
| Level1<List<B>> | A = List<Integer> |
| Root<Map<String,A>> | T = Map<String,List<Integer>> |

Suy ra **Final.read()** có kiểu tĩnh **Map<String,List<Integer>>**. Không có bất kỳ phép cast hay chuyển đổi dữ liệu nào trong quá trình thế này.

## 24. Câu hỏi tự kiểm tra

**Câu 1.** Base<T> khai báo T get(); Middle<U> extends Base<List<U>>; Leaf extends Middle<String>. Kiểu get() ở Leaf là gì?

**Đáp án:** List<String>. Thế T = List<U>, tiếp theo U = String.

**Câu 2.** Vì sao **Leaf** ở câu trên không tự là Base<String>?

**Đáp án:** Mệnh đề extends trung gian đã đặt List<U> vào T của Base; cuối cùng T phải là List<String>, không phải String.

**Câu 3.** Pair<A,B>, Swapped<X,Y> extends Pair<Y,X>, Fixed extends Swapped<Integer,String>: left() và right() trả kiểu gì?

**Đáp án:** left() trả String, right() trả Integer; vì A = Y = String, B = X = Integer.

**Câu 4.** Với Repository<T>.save(T), lớp NameRepo extends Repository<List<String>> nhận tham số gì ở save?

**Đáp án:** List<String>, không phải String. Phép thế áp dụng toàn bộ biểu thức kiểu của T.

**Câu 5.** Generic method của cha **<R> R map(Function<T,R>)** được dùng từ subclass extends Base<String>. Có phải R thành String không?

**Đáp án:** Không. Chỉ T được cố định thành String; R vẫn do generic method và từng lời gọi quyết định.

**Câu 6.** Lớp con extends Provider<Number> có được override Number get() bằng Integer get() không?

**Đáp án:** Có, nếu các điều kiện override khác cũng thỏa: Integer là subtype của Number và return type covariant hợp lệ.

**Câu 7.** Tại sao không được override method trả List<Number> bằng method trả List<Integer> chỉ vì Integer extends Number?

**Đáp án:** List<Integer> không là subtype của List<Number> do invariance.

**Câu 8.** Constructor của Base<T> được tự động kế thừa bởi Child extends Base<String> không?

**Đáp án:** Không. Child phải có constructor phù hợp và gọi super(...) khi cần theo luật khởi tạo của Java.

**Câu 9.** Static method <U> U echo(U x) trong Base<T> có bị cố định thành U = String khi gọi qua Child extends Base<String> không?

**Đáp án:** Không. U là type parameter riêng của static method; T của Base không dùng trong static context theo cách đó.

**Câu 10.** Raw subclass **class Legacy extends Base** có giữ kết quả T get() được chuyên biệt thành String không?

**Đáp án:** Không. Raw type bỏ type arguments; với T không bound, kiểu sau erasure thường là Object, và an toàn kiểu giảm.

**Câu 11.** Nếu nhiều cấp extends/implements dẫn tới interface Source<List<Integer>>, một tham chiếu Source<List<Number>> có tự gán từ đối tượng đó không?

**Đáp án:** Không, vì List<Integer> và List<Number> là parameterized types khác nhau và generic invariance áp dụng qua các cấp.

**Câu 12.** Khi compiler sinh bridge method cho một override generic, có nghĩa Java tạo một runtime class Base<String> mới không?

**Đáp án:** Không. Bridge hỗ trợ dispatch sau erasure; nó không tạo một bản runtime class riêng cho từng type argument.

## 25. Ghi nhớ cốt lõi

Để biết kiểu của thành viên kế thừa, **không đoán từ tên lớp con**. Hãy đi theo đúng từng mệnh đề extends/implements, thay type arguments ở mỗi cấp và ghép các phép thế theo thứ tự. Nếu **Middle<U> extends Base<List<U>>** và **Final extends Middle<String>**, thì **Base<T>** được nhìn qua Final với **T = List<String>**; vì vậy field T, method T get() và method nhận T đều dùng List<String> trong những ngữ cảnh hợp lệ.

Những type parameters của generic method có scope riêng, static members không gắn với mỗi T, constructors không được kế thừa, private members không tự truy cập được, và erasure không xóa khả năng kiểm tra kiểu tại source code. Tách biệt rõ **phép thế kiểu**, **luật kế thừa/override** và **biểu diễn bytecode** là chìa khóa đọc đúng những hierarchy generic nhiều tầng.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="generic-inheritance-007">Kiểu trả về hiệp biến và hợp đồng phương thức</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểu trả về hiệp biến và hợp đồng phương thức khi kế thừa Generics trong Java

## 1. Bài toán và kết quả cần nắm

Khi một lớp con **ghi đè (override)** phương thức của lớp cha, Java cho phép phương thức ở lớp con trả về **một kiểu cụ thể hơn** so với kiểu trả về trong hợp đồng cha. Cơ chế này gọi là **covariant return type — kiểu trả về hiệp biến**. Nó rất hữu ích khi kế thừa lớp generic: lớp cha định nghĩa thao tác bằng tham số kiểu `T`; lớp con cố định `T` thành một kiểu cụ thể, rồi cung cấp kết quả chính xác hơn mà vẫn dùng được thông qua tham chiếu của lớp cha.

Ví dụ:

```java
class Animal {
    String name() { return "animal"; }
}

class Dog extends Animal {
    String bark() { return "gâu"; }
}

class AnimalFactory {
    Animal create() { return new Animal(); }
}

class DogFactory extends AnimalFactory {
    @Override
    Dog create() { return new Dog(); } // Hợp lệ: Dog là subtype của Animal.
}
```

Với `DogFactory`, phương thức `create()` có thể trả `Dog`, nhưng lời gọi qua tham chiếu `AnimalFactory` vẫn có hợp đồng trả `Animal`. Hành vi thực tế của đối tượng vẫn được chọn bằng **dynamic dispatch**, không phải bằng kiểu trả về ở nơi gọi.

Mục tiêu bài học:

- Hiểu chính xác khi nào kiểu trả về của override có thể cụ thể hơn.
- Phân biệt **hiệp biến kiểu trả về** với **bất biến (invariance) của generic type arguments**.
- Đọc và viết đúng contract khi lớp con kế thừa `Parent<T>`, đặc biệt `extends Parent<Dog>`.
- Hiểu giới hạn đối với **tham số phương thức, visibility, checked exceptions, `final`/`static`**, và các kiểu primitive/`void`.
- Hiểu vai trò của **type erasure** và **bridge method**, tránh tự thêm cast hoặc làm mất an toàn kiểu.
- Áp dụng vào API factory, repository, provider, builder và các collection bằng chương trình Java 17+ chạy được.

## 2. Hiệp biến kiểu trả về là gì?

Giả sử `Dog extends Animal`. Khi override một phương thức instance trong `AnimalSource` có kết quả `Animal`, `DogSource` có thể trả `Dog`:

```java
class AnimalSource {
    public Animal get() {
        return new Animal();
    }
}

class DogSource extends AnimalSource {
    @Override
    public Dog get() {
        return new Dog();
    }
}
```

Quy tắc cốt lõi là **kiểu trả về của phương thức override phải tương thích để thay thế kiểu trả về trong hợp đồng phương thức bị override** (*return-type substitutability*). Với các kiểu tham chiếu thông thường, một subtype phù hợp của kiểu trả về cha được chấp nhận.

Lời gọi minh họa:

```java
DogSource direct = new DogSource();
Dog dog = direct.get(); // Kiểu biểu thức get() là Dog.

AnimalSource general = direct;
Animal animal = general.get(); // Kiểu biểu thức get() là Animal.
```

Cả hai lời gọi chạy trên **cùng đối tượng** `DogSource` và đều thực thi implementation `DogSource.get()`. Kiểu tĩnh của biến ở nơi gọi quyết định những gì compiler cho phép sử dụng tiếp:

```java
Dog concrete = direct.get();
concrete.bark();                  // Hợp lệ.
Animal generalResult = general.get();
// generalResult.bark();          // Lỗi biên dịch: Animal không khai báo bark().
```

Hợp đồng của lớp cha bảo đảm người dùng nhận được **ít nhất một `Animal`**. Lớp con đáp ứng hợp đồng bằng việc cung cấp một `Dog`, vốn là một `Animal` hợp lệ.

### Vì sao có lợi?

Nếu Java bắt buộc override trả đúng kiểu `Animal`, client `DogSource` phải cast:

```java
// Không cần làm vậy khi DogSource.get() trả Dog:
// Dog dog = (Dog) direct.get();
```

Covariant return cho phép client có kiểu kết quả đủ cụ thể, giữ kiểm tra lúc biên dịch và giảm cast trong các API chuyên biệt.

## 3. Ghi đè trước hết phải đúng hợp đồng phương thức

Covariant return **chỉ thay đổi điều kiện về kiểu trả về**; nó không có nghĩa lớp con được tùy ý sửa chữ ký phương thức. Để override đúng, vẫn cần thỏa các quy tắc Java về:

1. **Tên phương thức và danh sách tham số** phải khớp theo quy tắc override/subsignature; không thể tùy tiện thay `Animal` thành `Dog` ở **tham số đầu vào** rồi vẫn gọi đó là override.
2. Kiểu trả về phải **return-type-substitutable** với kiểu trả về cha.
3. Mức truy cập của phương thức override không được **hẹp hơn** mức truy cập của phương thức cha.
4. Phương thức override không được khai báo checked exception mới hoặc rộng hơn những gì hợp đồng cha cho phép (có thể khai báo tập ngoại lệ hẹp hơn).
5. Không override phương thức `final`, `private` hay `static` theo nghĩa đa hình của instance method. `static` là *method hiding* với quy tắc riêng.
6. Constructor **không phải phương thức được override** và không có kiểu trả về; áp dụng hiệp biến kiểu trả về cho constructor là sai phạm vi.

Ví dụ có visibility và checked exception:

```java
import java.io.IOException;

class Reader {
    protected Animal load() throws IOException {
        return new Animal();
    }
}

class DogReader extends Reader {
    @Override
    public Dog load() {
        return new Dog();
    }
}
```

`DogReader.load()` mở rộng quyền truy cập từ `protected` lên `public`, thu hẹp kiểu kết quả từ `Animal` xuống `Dog`, và loại bỏ checked exception `IOException`. Cả ba điều đều phù hợp.

Ví dụ không hợp lệ:

```java
class BadDogReader extends Reader {
    // @Override
    // private Animal load() throws Exception {
    //     return new Dog();
    // }
    // Sai: private làm hẹp truy cập; Exception rộng hơn IOException.
}
```

**Đừng phân biệt override chỉ bằng câu hỏi “tên có giống không?”**. Luôn nhìn đủ hợp đồng, bao gồm danh sách tham số, kiểu trả về, visibility và exceptions.

## 4. Sử dụng `T` của lớp cha generic

Giờ xét lớp cha được tham số hóa:

```java
class Container<T> {
    private final T value;

    Container(T value) {
        this.value = value;
    }

    public T get() {
        return value;
    }
}
```

Khi lớp con cố định type argument:

```java
class DogContainer extends Container<Dog> {
    DogContainer(Dog value) {
        super(value);
    }

    @Override
    public Dog get() {
        return super.get();
    }
}
```

Với `DogContainer extends Container<Dog>`, tham số kiểu `T` của superclass được cố định là `Dog`, nên client thấy `Container<Dog>.get()` có kết quả `Dog` ngay cả khi không có override. Việc ghi đè ở lớp con vẫn hợp lệ, và có thể thêm validation, logging hoặc biến đổi giá trị khi thật sự cần.

```java
Container<Dog> parentView = new DogContainer(new Dog());
Dog value = parentView.get(); // Được kiểm tra là Dog.
```

**Hai ý phải tách rõ:**

- **Thế tham số kiểu (*generic substitution*):** `T` ở `Container<T>` được diễn giải như `Dog` trong `Container<Dog>`.
- **Hiệp biến kiểu trả về:** khi override một phương thức hợp lệ, lớp con có thể chọn kiểu trả về tương thích nhưng **cụ thể hơn** kiểu mà phương thức cha yêu cầu.

Trong ví dụ này, override trả `Dog` cũng là cùng kiểu đã được chuyên biệt hóa. Ví dụ tiếp theo thể hiện cả chuyên biệt hóa theo generic và hiệp biến subtype ở cấp kết quả.

### Khi kiểu trả về được làm hẹp thêm

```java
class Mammal extends Animal {}

class MammalContainer extends Container<Mammal> {
    MammalContainer(Mammal value) {
        super(value);
    }
}

class SpecificMammal extends Mammal {}

class SpecificMammalContainer extends Container<Mammal> {
    SpecificMammalContainer(SpecificMammal value) {
        super(value);
    }

    @Override
    public SpecificMammal get() {
        return (SpecificMammal) super.get();
    }
}
```

`Container<Mammal>.get()` có hợp đồng trả `Mammal`. Lớp con có thể trả `SpecificMammal` vì đây là subtype. Nhưng cách cast ở đây **chỉ an toàn nếu invariant thực sự bảo đảm mọi đối tượng trong container là `SpecificMammal`**. Nếu phương thức cha có thể bị dùng để thay đổi giá trị thành `Mammal` khác, cách override sẽ nguy hiểm.

Thiết kế tốt hơn thường lưu kiểu chuyên biệt ngay từ đầu hoặc giữ đường khởi tạo bảo đảm invariant, ví dụ dùng lớp con `extends Container<SpecificMammal>`. Không dùng covariance như một lý do để ép kiểu không được chứng minh.

## 5. Covariant return và `extends Parent<String>`

Với kế thừa có type argument cố định, Java ánh xạ chữ ký inherited method thành một contract có kiểu:

```java
class Parent<T> {
    public T read() {
        return null;
    }
}

class TextChild extends Parent<String> {
    @Override
    public String read() {
        return "Java";
    }
}
```

`String` là kiểu kết quả mà lời gọi `Parent<String>.read()` công khai. Khi override bằng `String`, lớp con đáp ứng đúng contract. Lúc compile, người gọi có thể viết:

```java
Parent<String> base = new TextChild();
String result = base.read(); // "Java"; không cần cast.
```

Nếu `Parent<T>` có bound:

```java
class NumberParent<T extends Number> {
    private final T number;
    NumberParent(T number) {
        this.number = number;
    }
    public T read() { return number; }
}

class IntegerChild extends NumberParent<Integer> {
    IntegerChild(Integer n) {
        super(n);
    }

    @Override
    public Integer read() {
        return super.read();
    }
}
```

`Integer` đáp ứng `T extends Number`. Tại source level, caller `NumberParent<Integer>` nhận `Integer`. Khi kiểm tra erasure ở bytecode, compiler xử lý các khác biệt chữ ký cần thiết bằng bridge method; client không phải viết thêm cầu nối.

### Getter không phải nơi duy nhất có covariance

Có thể override một phương thức tạo bản sao để nó trả chính subtype:

```java
class Copyable<T> {
    private final T value;
    Copyable(T value) { this.value = value; }
    public Copyable<T> copy() {
        return new Copyable<>(value);
    }
}

class StringCopyable extends Copyable<String> {
    StringCopyable(String value) { super(value); }

    @Override
    public StringCopyable copy() {
        return new StringCopyable("copy");
    }
}
```

`StringCopyable` là subtype của `Copyable<String>`, nên kiểu trả về `StringCopyable` phù hợp với kiểu trả về `Copyable<String>` của cha. Đây là mẫu **self-returning API** được dùng ở builder, factory, copy/clone và fluent API.

Không có nghĩa Java có cơ chế “self type” tuyệt đối cho mọi cây kế thừa. Nếu lớp con sâu hơn cần trả về đúng lớp con mới, có thể phải override tiếp hoặc xem xét generic self-type pattern khi cần.

## 6. Phân biệt kiểu trả về với kiểu tham số phương thức

Hiệp biến kết quả không áp dụng đối xứng để bạn tự ý đổi **tham số đầu vào**:

```java
class AnimalHandler {
    public Animal handle(Animal item) {
        return item;
    }
}

class DogHandler extends AnimalHandler {
    // KHÔNG phải override của handle(Animal).
    public Dog handle(Dog item) {
        return item;
    }
}
```

`handle(Dog)` là **overload**, vì danh sách tham số thay đổi. Lời gọi thông qua `AnimalHandler` vẫn chọn chữ ký `handle(Animal)`; nếu lớp con không override chữ ký đó, implementation cha vẫn chạy.

Để override chính xác:

```java
class CorrectDogHandler extends AnimalHandler {
    @Override
    public Dog handle(Animal item) {
        return new Dog();
    }
}
```

`CorrectDogHandler.handle(Animal)` giữ nguyên tham số `Animal` nhưng có thể thu hẹp kết quả thành `Dog`. Gắn `@Override` giúp compiler phát hiện nhầm lẫn giữa override và overload.

Trong generic inheritance, cũng phải nhớ `List<Dog>` **không** phải subtype của `List<Animal>` dù `Dog extends Animal`. Đây là câu chuyện về **invariance của kiểu đã tham số hóa**, khác với covariance của kiểu trả về là một `Dog` đơn lẻ.

## 7. Quan trọng: `List<Dog>` không thay được `List<Animal>`

Giả sử lớp cha khai báo:

```java
class AnimalService {
    public java.util.List<Animal> findAll() {
        return java.util.List.of(new Animal());
    }
}
```

Lớp con không được override thành `List<Dog>`:

```java
class BadDogService extends AnimalService {
    // @Override
    // public java.util.List<Dog> findAll() {
    //     return java.util.List.of(new Dog());
    // }
    // Sai: List<Dog> không phải subtype của List<Animal>.
}
```

Tại sao? Nếu điều đó được chấp nhận, code dùng tham chiếu `AnimalService` có thể xem kết quả như `List<Animal>` và (nếu list có thể sửa) thêm một `Animal` không phải `Dog`, phá hợp đồng `List<Dog>`. Java giữ generic types mặc định **invariant**.

### Cách 1: thiết kế hợp đồng cha bằng wildcard phù hợp

Nếu API chỉ có nhu cầu **cung cấp/đọc** danh sách các `Animal`, lớp cha có thể trả `List<? extends Animal>`:

```java
class AnimalCatalog {
    public java.util.List<? extends Animal> animals() {
        return java.util.List.of(new Animal());
    }
}

class DogCatalog extends AnimalCatalog {
    @Override
    public java.util.List<Dog> animals() {
        return java.util.List.of(new Dog());
    }
}
```

Ở đây `List<Dog>` **là subtype của** `List<? extends Animal>`, do đó override hợp lệ. Người dùng tham chiếu cha đọc phần tử như `Animal`; không thể tùy ý thêm `Animal` vào danh sách trả về qua biến wildcard:

```java
AnimalCatalog catalog = new DogCatalog();
java.util.List<? extends Animal> list = catalog.animals();
Animal first = list.get(0);
// list.add(new Animal()); // Lỗi biên dịch: phần tử có kiểu capture chưa biết.
```

Với `List<? extends Animal>`, việc thêm giá trị cụ thể không được bảo đảm an toàn; chỉ thêm `null` mới được phép về mặt hệ thống kiểu (implementation có thể từ chối).

### Cách 2: generic hóa hợp đồng cha

Nếu cần giữ đúng kiểu phần tử, có thể thiết kế:

```java
class Service<T extends Animal> {
    public java.util.List<T> findAll() {
        return java.util.List.of();
    }
}

class DogService extends Service<Dog> {
    @Override
    public java.util.List<Dog> findAll() {
        return java.util.List.of(new Dog());
    }
}
```

`Service<Dog>.findAll()` đã có kiểu `List<Dog>` từ phép thế `T = Dog`; override ở `DogService` bảo toàn hợp đồng chính xác. Đây thường là cách phù hợp khi domain API cần thao tác theo một kiểu duy nhất xuyên suốt.

**Đừng thay hợp đồng bằng raw `List` hoặc cast unchecked** chỉ để compiler im lặng: làm vậy sẽ che giấu lỗi quan hệ kiểu và có thể dẫn tới `ClassCastException` khi sử dụng dữ liệu.

## 8. Interface generic và kiểu trả về hiệp biến

Covariant return không chỉ áp dụng giữa hai class. Một lớp triển khai interface cũng có thể trả một kiểu cụ thể hơn kiểu kết quả interface yêu cầu:

```java
interface Producer<T> {
    T produce();
}

class AnimalProducer implements Producer<Animal> {
    @Override
    public Dog produce() {
        return new Dog();
    }
}
```

`Producer<Animal>.produce()` có hợp đồng trả `Animal`; trả `Dog` là đáp ứng hợp đồng, vì `Dog extends Animal`.

Sử dụng qua interface:

```java
Producer<Animal> producer = new AnimalProducer();
Animal item = producer.produce();
```

Điểm cần nhớ: `AnimalProducer implements Producer<Animal>` **không** làm nó thành `Producer<Dog>`:

```java
// Producer<Dog> dogs = new AnimalProducer(); // Không hợp lệ.
```

Một instance có thể tạo ra `Dog` nhưng type argument của interface vẫn là `Animal`; generic assignment tuân theo invariance.

### Khi interface cố định sẵn kết quả

```java
interface StringProvider {
    CharSequence text();
}

class FixedStringProvider implements StringProvider {
    @Override
    public String text() {
        return "Hello";
    }
}
```

`String implements CharSequence`, nên `String` là một covariant return type hợp lệ. Mẫu này xuất hiện ở interface SDK, builder và adapter khi implementation cung cấp một đối tượng có API cụ thể hơn.

## 9. Type erasure và bridge method: vì sao đa hình vẫn đúng?

Java generics được triển khai chủ yếu dựa trên **type erasure**. Khi compiler biên dịch `class Source<T extends Animal>`, phần `T` thường được thay bằng bound `Animal` trong chữ ký tương ứng ở bytecode. Lớp con `Source<Dog>` lại có phương thức override trả kiểu `Dog`.

Ví dụ ý tưởng:

```java
class Source<T extends Animal> {
    public T read() {
        return null;
    }
}

class DogSource extends Source<Dog> {
    @Override
    public Dog read() {
        return new Dog();
    }
}
```

Sau erasure, JVM nhìn những method descriptor có kiểu trả về khác nhau. Để đảm bảo lời gọi qua `Source<Dog>` vẫn tới implementation `DogSource.read()`, compiler có thể sinh một **bridge method** tổng hợp, chuyển lời gọi kiểu erasure sang phương thức chuyên biệt.

Đây là cơ chế quan trọng để giữ **đa hình** khi generic method signatures và covariant returns gặp nhau. Bridge methods:

- Do compiler tạo ra khi cần; không phải phương thức bạn tự viết trong mã nguồn.
- Thường mang dấu `synthetic` và `bridge` ở bytecode.
- Có thể được quan sát qua reflection bằng `Method.isBridge()` hoặc `javap -c -p`.
- Không có nghĩa runtime duy trì một lớp chuyên biệt riêng cho `Source<Dog>`.
- Không phải lý do để bỏ `@Override`, dùng raw type hay tự ép kiểu.

Trong ví dụ đầy đủ ở mục 12, `DogSource` override cả `read()` và `copy()`. Với compiler Java đã thử, reflection đếm được **2 bridge method** do các chữ ký được làm cụ thể hơn. Nếu sửa thiết kế hoặc phiên bản compiler, không nên sử dụng số lượng bridge method như một hợp đồng business logic.

### Có nên tự viết bridge?

Không. Mã nguồn chỉ cần viết đúng override và để compiler thực hiện phần tương thích runtime. Tự viết hai phương thức khác nhau chỉ bởi kiểu trả về cũng **không hợp lệ**, vì Java không cho overload bằng kiểu trả về đơn thuần.

## 10. Kết quả primitive, `void`, checked exceptions và `static`

Các ví dụ `Dog`/`Animal` đều là **reference types**. Với primitive và `void`, không áp dụng hiệp biến như quan hệ lớp:

```java
class BaseCount {
    int size() { return 1; }
}

class ChildCount extends BaseCount {
    // @Override long size() { return 1L; } // Sai: long không thay int.
    @Override int size() { return 2; } // Đúng.
}
```

`int` và `long` có chuyển đổi số học mở rộng khi gán biểu thức, nhưng **không tạo quan hệ subtype** để thay đổi kiểu trả về khi override. Tương tự, một phương thức trả `void` phải được override bằng phương thức trả `void`.

### Checked exceptions

```java
import java.io.IOException;
import java.io.FileNotFoundException;

class ParentLoader {
    Animal load() throws IOException { return new Animal(); }
}

class ChildLoader extends ParentLoader {
    @Override
    Dog load() throws FileNotFoundException { return new Dog(); }
}
```

`FileNotFoundException` là subtype của `IOException`, vì vậy lớp con thu hẹp checked exception và thu hẹp kiểu trả về — cả hai hợp lệ. Ngược lại, thêm `throws Exception` khi lớp cha chỉ cho phép `IOException` là không hợp lệ.

### Quy tắc `static` và `final`

`static` methods không tham gia dynamic dispatch theo kiểu override instance; cùng tên/chữ ký ở lớp con là **hiding**. Không dùng `@Override` cho method static:

```java
class BaseFactory {
    static Animal create() { return new Animal(); }
}

class ChildFactory extends BaseFactory {
    // Đang che khuất static method, không phải override đa hình.
    static Dog create() { return new Dog(); }
}
```

Các quy tắc tương thích khi hiding vẫn cần được tuân thủ; đừng dùng hiding như cách thay thế override để biểu diễn trạng thái đối tượng.

Phương thức `final` không thể override nên không thể cung cấp kiểu trả về hiệp biến bằng cách override; `private` method không được kế thừa như một phương thức có thể override. Constructor luôn không có kiểu trả về và không phải đối tượng của quy tắc hiệp biến.

## 11. API tự trả subtype: factory, copy và builder

Một ứng dụng thường gặp của covariant return là lớp con muốn các thao tác fluent hoặc clone/copy trả lại **chính loại đối tượng chuyên biệt**:

```java
class BuilderBase {
    public BuilderBase configure() {
        return this;
    }
}

class DogBuilder extends BuilderBase {
    @Override
    public DogBuilder configure() {
        return this;
    }

    Dog build() {
        return new Dog();
    }
}
```

Người sử dụng `DogBuilder` gọi `configure()` sẽ nhận `DogBuilder` và có thể gọi tiếp `build()` mà không cần cast:

```java
DogBuilder builder = new DogBuilder();
Dog dog = builder.configure().build();
```

Tuy nhiên, khi API yêu cầu tự trả **mỗi subtype cuối cùng qua nhiều cấp**, covariant return override ở một cấp không tự “biến hình” theo subclass mới. Cần override tại các subtype sâu hơn hoặc cân nhắc mô hình self-bounded generics (*CRTP-like pattern*):

```java
abstract class Fluent<S extends Fluent<S>> {
    protected abstract S self();

    public S configure() {
        return self();
    }
}

final class Specialized extends Fluent<Specialized> {
    @Override
    protected Specialized self() {
        return this;
    }

    Specialized finish() {
        return this;
    }
}
```

Mẫu này giữ kiểu trả về xuyên qua API bằng generic parameter; nó khác với việc chỉ override một phương thức trả `Base` thành `Child`. Dùng khi hệ thống có nhiều lớp builder/fluent thực sự cần bảo đảm kiểu, không ép thêm phức tạp cho API nhỏ.

## 12. Chương trình Java 17+ độc lập, đã kiểm thử

Tạo file tên **`CovariantReturnDemo.java`**. Chương trình dưới đây minh họa cùng lúc kiểu trả về hiệp biến trên một phương thức thường, lớp cha generic có bound, interface, collection wildcard và bridge method.

```java
import java.util.List;
import java.util.Arrays;
import java.lang.reflect.Method;

public class CovariantReturnDemo {
    static class Animal {
        private final String name;
        Animal(String name) { this.name = name; }
        String name() { return name; }
    }

    static final class Dog extends Animal {
        Dog(String name) { super(name); }
        String bark() { return "gâu"; }
    }

    static class Source<T extends Animal> {
        private final T value;
        Source(T value) { this.value = value; }
        public T read() { return value; }
        public Source<T> copy() { return new Source<>(value); }
    }

    static final class DogSource extends Source<Dog> {
        DogSource(Dog value) { super(value); }
        @Override public Dog read() { return super.read(); }
        @Override public DogSource copy() { return new DogSource(read()); }
    }

    interface Provider<T> {
        T get();
    }

    static final class AnimalProvider implements Provider<Animal> {
        @Override public Dog get() { return new Dog("Nâu"); }
    }

    static class AnimalCatalog {
        public List<? extends Animal> animals() {
            return List.of(new Animal("Chung"));
        }
    }

    static final class DogCatalog extends AnimalCatalog {
        @Override public List<Dog> animals() {
            return List.of(new Dog("Mực"), new Dog("Vàng"));
        }
    }

    public static void main(String[] args) {
        DogSource child = new DogSource(new Dog("Bim"));
        Source<Dog> parent = child;
        System.out.println("parent: " + parent.read().name());
        System.out.println("child: " + child.read().bark());
        System.out.println("copy: " + child.copy().read().name());

        Provider<Animal> provider = new AnimalProvider();
        System.out.println("provider: " + provider.get().name());

        AnimalCatalog catalog = new DogCatalog();
        System.out.println("catalog: " +
                catalog.animals().stream().map(Animal::name).toList());

        long bridges = Arrays.stream(DogSource.class.getDeclaredMethods())
                .filter(Method::isBridge)
                .count();
        System.out.println("bridges: " + bridges);
    }
}
```

Biên dịch và chạy (JDK 17 hoặc mới hơn):

```bash
javac --release 17 -Xlint:all CovariantReturnDemo.java
java CovariantReturnDemo
```

**Stdout chính xác** của bản trên (đã biên dịch với `--release 17 -Xlint:all` và chạy thành công):

```text
parent: Bim
child: gâu
copy: Bim
provider: Nâu
catalog: [Mực, Vàng]
bridges: 2
```

Giải thích từng dòng:

1. `Source<Dog> parent = child` hợp lệ vì `DogSource extends Source<Dog>`. Kiểu tĩnh của `parent.read()` là `Dog` theo `T = Dog`; implementation runtime là `DogSource.read()`.
2. `child.read().bark()` hợp lệ vì lớp con override trả `Dog`, cho phép dùng API riêng của `Dog`.
3. `child.copy()` trả `DogSource` — một subtype của `Source<Dog>` — nhờ covariant return, nên tiếp tục gọi `read()` với kiểu đúng.
4. `Provider<Animal> provider` dùng implementation `AnimalProvider.get()` trả `Dog`. Điều này đúng vì `Dog` là `Animal`; nó **không** làm biến `provider` có kiểu `Provider<Dog>`.
5. `AnimalCatalog.animals()` trả `List<? extends Animal>`, cho phép `DogCatalog.animals()` override bằng `List<Dog>`. Danh sách tên được giữ thứ tự từ `List.of(...)`.
6. Reflection đếm bridge methods được compiler tạo trong `DogSource` cho `read()` và `copy()`. Phần này chỉ nhằm kiểm chứng cơ chế biên dịch, không phải điều kiện mà logic ứng dụng nên dựa vào.

**Ghi chú về tính xác định:** `Dog.name()` lấy từ lớp `Animal`, chuỗi `gâu` được ghi tường minh; thứ tự của `List.of(...)` giữ nguyên theo đầu vào. Mã không phụ thuộc múi giờ, mạng, I/O file hay dữ liệu ngẫu nhiên. Trên một bản cài đặt compiler thông thường tương thích với ví dụ này, số bridge method phản ánh đúng các override chuyên biệt; khi thay đổi chữ ký, kết quả đếm có thể đổi.

## 13. Những lỗi thường gặp

| Biểu hiện / đoạn mã | Nguyên nhân | Sửa đúng |
| --- | --- | --- |
| `Animal get()` ở cha, `Object get()` ở con | Mở rộng kiểu trả về thay vì trả subtype; không bảo đảm contract | Giữ `Animal` hoặc trả một subtype như `Dog`. |
| `int count()` ở cha, `long count()` ở con | Primitive không covariant theo quan hệ kế thừa lớp | Giữ `int` trong override. |
| `List<Animal> findAll()` ở cha, `List<Dog> findAll()` ở con | Generic type arguments invariant | Thay contract cha bằng `List<? extends Animal>` nếu chỉ đọc, hoặc generic hóa cha thành `Service<T>`. |
| Override `handle(Animal)` bằng `handle(Dog)` | Đổi tham số tạo overload thay vì override | Giữ danh sách tham số, chỉ làm hẹp kiểu kết quả hợp lệ. |
| Thêm `throws Exception` khi cha chỉ khai báo `IOException` | Checked exception rộng hơn contract | Giữ hoặc thu hẹp checked exceptions. |
| Giảm `public` thành `protected` hay `private` | Override không được thu hẹp khả năng truy cập | Giữ `public` hoặc tăng mức truy cập khi hợp lệ. |
| Gắn `@Override` trên `static` method | Static method được hide, không override động | Thiết kế instance method nếu cần đa hình. |
| Ghi đè method `final` | `final` cấm override | Sửa thiết kế, composition hoặc dùng phương thức mở rộng khác. |
| Gọi `new Parent<Dog>` rồi cast thành `Parent<Animal>` | Invariance; phép cast không làm hợp đồng an toàn | Dùng `Parent<? extends Animal>` cho mục đích đọc, hoặc API generic phù hợp. |
| Quên `@Override` | Lỗi chính tả/chữ ký dễ biến thành overload ngoài ý muốn | Thêm `@Override` cho mọi override thật sự. |
| Tự cast `(Dog) super.get()` mà không kiểm soát invariant | Có thể gây `ClassCastException` | Cố định type argument ở cha hoặc kiểm tra invariant, tránh cast vô căn cứ. |
| Cố overload hai method chỉ khác kiểu trả về | Java không phân giải overload chỉ bằng kết quả | Dùng tên khác hoặc tham số khác có ý nghĩa. |
| Tự viết bridge method vì nghĩ erasure làm mất đa hình | Compiler đã sinh bridge khi cần | Viết override có type safety và để compiler xử lý bytecode. |
| Dùng raw `List`/`Source` cho hết lỗi generic | Bỏ bớt kiểm tra kiểu; có thể sinh unchecked warnings | Sửa quan hệ kiểu thật, không che cảnh báo. |

### Cách đọc lỗi compiler khi override

Khi compiler báo kiểu trả về "incompatible", hãy lần lượt trả lời:

1. Phương thức cha có **đúng chữ ký sau khi thế type arguments** nào? `T` đã cố định thành `Dog`, `Animal` hay `List<Animal>`?
2. Phương thức con có thật sự override (cùng tên/danh sách tham số hợp lệ) hay chỉ overload?
3. Kết quả con có phải subtype phù hợp của kết quả cha **sau phép thế generics** không?
4. Nếu kết quả là một generic type, liệu quan hệ subtype thực sự tồn tại hay bạn đang suy từ `Dog extends Animal` sang `List<Dog> extends List<Animal>` một cách sai lầm?
5. Mức truy cập, checked exception, `static`, `final` có cản trở override không?
6. Có raw type/unchecked conversion từ code cũ khiến chữ ký bạn nhìn thấy khó hiểu không?

Đây là thứ tự kiểm tra hiệu quả hơn so với việc thử thêm cast rồi xóa từng cảnh báo.

## 14. Bài tập tự luyện

**Bài 1 — Override cơ bản.** Tạo `class Vehicle` và `class Bike extends Vehicle`. Viết `VehicleFactory.create(): Vehicle` và `BikeFactory.create(): Bike`. Gọi qua biến `VehicleFactory` trỏ tới `BikeFactory`, ghi chú sự khác nhau giữa kiểu tĩnh của kết quả và class runtime.

**Bài 2 — Generic superclass với kiểu cố định.** Viết `class Box<T>` có `T get()`. Tạo `class BikeBox extends Box<Bike>` và override `get()` nếu muốn thêm hành vi. Chứng minh `Box<Bike> ref = new BikeBox(...)` hợp lệ và `Box<Vehicle> ref = new BikeBox(...)` không hợp lệ.

**Bài 3 — Hiệp biến kết quả factory.** Viết `class CopySource<T>` có `CopySource<T> duplicate()`. Tạo `class StringSource extends CopySource<String>` với override trả `StringSource`. Giải thích vì sao kiểu trả về con không phá hợp đồng cha.

**Bài 4 — Collection và wildcard.** Cho `List<Vehicle> vehicles()` ở cha. Thử override bằng `List<Bike> vehicles()` và xem lỗi. Chuyển kiểu kết quả cha thành `List<? extends Vehicle>` rồi kiểm chứng override hợp lệ. Thử `list.add(new Vehicle())` trên biến `List<? extends Vehicle>` và giải thích việc compiler từ chối.

**Bài 5 — Interface generic.** Định nghĩa `Provider<T>` với `T get()`. Tạo `Provider<Vehicle>` bằng implementation trả `Bike`. Tại sao `Provider<Bike> p = ...` không suy ra hợp lệ chỉ từ việc implementation trả `Bike`?

**Bài 6 — Bytecode bridge.** Biên dịch ví dụ `Source<T extends Animal>`/`DogSource`, chạy `javap -c -p` trên class con (với tên nested class tương ứng). Tìm các phương thức `bridge` do compiler sinh; giải thích tại sao gọi qua tham chiếu cha vẫn đi tới override con.

**Bài 7 — Hợp đồng ngoại lệ.** Lớp cha khai báo `Animal read() throws IOException`. Viết lớp con override trả `Dog`, có thể khai báo `throws FileNotFoundException`. Thử thay `IOException` bằng `Exception` trong override và giải thích lỗi.

**Bài 8 — Đọc chữ ký.** Giả sử `class AnimalRepo<T extends Animal> { List<T> all(); }`. Tạo `class DogRepo extends AnimalRepo<Dog>`. Kiểu kết quả của `all()` là gì? Có cần `@Override` để đơn thuần kế thừa không? Vì sao cố ghi đè thành `List<Animal>` là sai?

## 15. Tổng kết nhanh

- **Covariant return** cho phép override phương thức instance bằng kiểu trả về là subtype phù hợp của kiểu trả về mà hợp đồng cha yêu cầu.
- Trong generic inheritance, phải **thế tham số kiểu của lớp cha** trước khi quyết định kiểu kết quả ở lớp con, ví dụ `Parent<T>` với `extends Parent<Dog>`.
- `Dog extends Animal` **không kéo theo** `List<Dog> extends List<Animal>`. Invariance của generic types và covariance của kiểu trả về là hai khái niệm khác nhau.
- `List<Dog>` có thể thay `List<? extends Animal>` khi contract chỉ cần đọc các giá trị như `Animal`.
- Kiểu trả về có thể thu hẹp, nhưng **tham số, mức truy cập, checked exception, static/final** vẫn phải tuân thủ quy tắc override.
- **Bridge methods** là chi tiết compiler để kết hợp erasure với polymorphism; không cần tự triển khai.
- Chọn hợp đồng API trước, dùng `@Override` và kiểu generic chính xác, kiểm thử compile-time; đừng dùng raw type hoặc unchecked cast để che các quan hệ kiểu sai.

Khi đọc một override trong lớp generic, hãy tự đặt câu hỏi cuối cùng: **“Nếu client chỉ biết đến lớp cha, kết quả mà lớp con trả ra có luôn thỏa hợp đồng ấy hay không?”** Nếu câu trả lời có thể được compiler chứng minh bằng quan hệ subtype và generic đúng đắn, covariant return là một công cụ mạnh giúp API chuyên biệt mà vẫn giữ tính thay thế (substitutability).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="generic-inheritance-008">Generic với lớp abstract, lớp final và hệ phân cấp sealed</a>

<details>
<summary>Xem chi tiết</summary>

# Generic với lớp abstract, lớp final và hệ phân cấp sealed trong Java

Trong Java, **generic** mô tả các quan hệ kiểu bằng tham số như `T`, `E`, `K` hoặc `V`. Còn **`abstract`**, **`final`** và **`sealed`** điều khiển cách một lớp được khởi tạo hoặc được kế thừa. Chúng giải quyết những vấn đề khác nhau nên có thể phối hợp trong một hệ phân cấp.

Ví dụ `abstract class Repository<T>` cho phép định nghĩa hợp đồng dùng chung nhưng không cho tạo trực tiếp repository trừu tượng; `final class MemoryRepository<T> extends Repository<T>` hoàn thiện hợp đồng và không cho kế thừa tiếp; `sealed abstract class Result<T> permits Success, Failure` hạn chế tập các **lớp con trực tiếp** được phép kế thừa.

**Ý chính:** `abstract`, `final`, `sealed` không làm mất tính generic. Thông tin kiểu vẫn được kiểm tra lúc biên dịch, còn quy tắc kế thừa vẫn tuân theo Java.

## 1. Ba cơ chế: abstract, final, sealed giải quyết việc gì?

| Cơ chế | Có thể khai báo generic? | Có thể `new` trực tiếp? | Kế thừa tiếp có được không? |
|---|---|---|---|
| `abstract class Base<T>` | Có | Không | Có, nếu không bị quy tắc khác hạn chế |
| `final class Value<T>` | Có | Có, nếu constructor truy cập được | Không; mọi kiểu con trực tiếp bị cấm |
| `sealed class Base<T> permits A, B` | Có | Có, nếu không đồng thời `abstract` và constructor hợp lệ | Chỉ những lớp con trực tiếp được cho phép |
| `abstract sealed class Base<T> permits A, B` | Có | Không | Chỉ những lớp con trực tiếp được cho phép |
| `non-sealed class A<T> extends Base<T>` | Có | Có nếu không `abstract` | Mở lại kế thừa cho các lớp con tiếp theo |

`non-sealed` chỉ có ý nghĩa khi một lớp hoặc interface đang **kế thừa trực tiếp một lớp/interface sealed** (ở vị trí được phép trong hệ phân cấp). Nó không phải modifier dùng tùy ý cho một class không có supertype sealed trực tiếp.

Một **direct permitted subclass** của sealed class phải nêu rõ một trong ba hướng tiếp tục hệ phân cấp: `final`, `sealed` hoặc `non-sealed`. `abstract` có thể kết hợp với `sealed` hoặc `non-sealed`; `abstract` không thể kết hợp với `final`. Lớp `final` đóng hẳn nhánh đó. Lớp `sealed` giới hạn tiếp nhánh sau. Lớp `non-sealed` mở nhánh đó cho các lớp con không được liệt kê trong danh sách `permits` của ông bà.

Từ **Java 17**, sealed classes và sealed interfaces là tính năng chính thức, không cần bật preview. Riêng một số dạng *pattern matching for switch* trong Java 17 vẫn thuộc preview; không nhầm hai tính năng này.

## 2. Generic abstract class: hợp đồng kiểu nhưng không có đối tượng trừu tượng

Lớp `abstract` thường dùng khi ta muốn chia sẻ trạng thái và hành vi chung, đồng thời yêu cầu lớp con cài đặt những thao tác còn thiếu:

```java
abstract class AbstractStore<T> {
    private final T initial;

    protected AbstractStore(T initial) {
        this.initial = initial;
    }

    public T initial() {
        return initial;
    }

    public abstract String describe(T value);
}
```

Đây là **định nghĩa lớp minh họa**, chưa có `main`. Các điểm quan trọng:

- `<T>` thuộc **lớp**. Trường `initial`, constructor, phương thức `initial()` và `describe(T)` cùng nói về kiểu `T` của mỗi cách tham số hóa.
- Không thể viết `new AbstractStore<String>("x")` để tạo trực tiếp đối tượng của một abstract class.
- Constructor của abstract class **vẫn được chạy** khi lớp con gọi `super(...)`. `abstract` không đồng nghĩa với “không có constructor”.
- Phương thức `abstract String describe(T value)` chỉ có chữ ký, không có thân; lớp con cụ thể phải cài đặt phương thức này hoặc được kế thừa một cài đặt hợp lệ.
- Một abstract class generic có thể chứa cả phương thức abstract lẫn concrete và trạng thái chung.

Một lớp con cố định `T`:

```java
final class StringStore extends AbstractStore<String> {
    StringStore(String initial) {
        super(initial);
    }

    @Override
    public String describe(String value) {
        return "text=" + value;
    }
}
```

Lúc này compiler biết `T = String`, nên chữ ký abstract `describe(T)` trở thành `describe(String)` theo quan hệ kiểu nguồn. `StringStore` không generic nhưng kế thừa một **generic superclass được tham số hóa**.

Một lớp con khác vẫn giữ type parameter:

```java
final class SimpleStore<E> extends AbstractStore<E> {
    SimpleStore(E initial) {
        super(initial);
    }

    @Override
    public String describe(E value) {
        return "item=" + value;
    }
}
```

`SimpleStore<Integer>` là subtype của `AbstractStore<Integer>`. Chữ `E` không bắt buộc trùng `T`: quan hệ nằm ở `extends AbstractStore<E>`. Cả `StringStore` và `SimpleStore<E>` là **hai thiết kế minh họa có thể đặt cùng nhau trong package phù hợp**; chúng không phải hai nhánh của một sealed hierarchy.

## 3. Generic final class: khóa kế thừa, không khóa tham số kiểu

Một `final class` không thể là superclass của một class khác, nhưng **vẫn có thể generic**:

```java
final class ResultBox<T> {
    private final T value;

    ResultBox(T value) {
        this.value = value;
    }

    T get() {
        return value;
    }
}
```

Có thể dùng `ResultBox<String>`, `ResultBox<Integer>` hoặc `ResultBox<List<String>>`. Modifier `final` **không giới hạn loại type arguments**. Nó chỉ cấm người khác khai báo `class SpecialBox extends ResultBox<String>`.

Cần phân biệt ba ý nghĩa khác nhau của chữ `final`:

1. **`final class`**: cấm kế thừa lớp.
2. **`final` trên method**: không cho override method đó trong subclass hợp lệ.
3. **`final` trên field**: ngăn gán lại field sau khi nó được khởi tạo phù hợp; **không bảo đảm đối tượng mà field trỏ tới bất biến sâu**.

Ví dụ, `final class Data<T>` có trường `private final List<T> items` vẫn có thể lộ một `ArrayList<T>` mutable ra ngoài; người gọi có thể sửa các phần tử thông qua tham chiếu list. Nếu mục tiêu là value object bất biến, phải thiết kế dữ liệu và API phù hợp bên cạnh modifier `final`.

Trong thiết kế generic, `final class` đặc biệt thích hợp với lớp giá trị, helper cụ thể, hoặc một nhánh **kết thúc** của hệ phân cấp sealed. Nó không đồng nghĩa với “lớp chưa được phép đa hình”: đối tượng của một lớp final vẫn có thể được nhìn qua **superclass** hoặc **interface** mà nó triển khai.

## 4. Generic sealed class/interface: giới hạn tập lớp con trực tiếp

Một sealed generic class có thể nêu những lớp được quyền kế thừa trực tiếp:

```java
abstract sealed class Result<T> permits Success, Failure {
    public abstract T orElse(T fallback);
}

final class Success<T> extends Result<T> {
    private final T value;

    Success(T value) {
        this.value = value;
    }

    @Override
    public T orElse(T fallback) {
        return value;
    }
}

final class Failure<T> extends Result<T> {
    @Override
    public T orElse(T fallback) {
        return fallback;
    }
}
```

Đây là **ba khai báo lớp minh họa**, không phải chương trình chạy độc lập. `Success<T>` và `Failure<T>` là hai subclass **trực tiếp** của `Result<T>` và đều `final`, vì vậy nhánh này không cho kế thừa thêm qua hai loại đó.

**Quy tắc cốt lõi của `sealed`:**

- Lớp cha dùng `sealed` và thường ghi `permits` để liệt kê direct subclasses.
- Mỗi direct subclass được phép phải có quan hệ **`extends`** đúng với superclass sealed (hoặc **`implements`** đối với sealed interface).
- Direct subclass phải được khai báo rõ là `final`, `sealed`, hoặc `non-sealed`.
- Với **named module**, những subclass được phép nằm trong **cùng module** với sealed parent. Trong **unnamed module**, chúng phải nằm trong **cùng package**. Điều kiện này không nói rằng toàn bộ codebase phải chỉ có một package.
- Danh sách `permits` chứa **tên các lớp/interface được cho phép**, không chứa cách tham số hóa như `Success<String>`. Chẳng hạn `permits Success, Failure`, không phải `permits Success<T>, Failure<T>`.
- Trong trường hợp tất cả các direct subclasses cùng nằm trong **cùng compilation unit**, compiler có thể suy ra tập permitted subclasses khi bỏ `permits`; ghi rõ `permits` thường dễ đọc hơn trong tài liệu.
- `sealed` hạn chế **cấu trúc kế thừa trực tiếp**, không tự hạn chế tập **type arguments** mà một generic class có thể nhận.

Giữa `Result<String>` và `Result<Integer>`, Java vẫn áp dụng kiểm tra kiểu generic như bình thường. `sealed` không làm hai cách tham số hóa trở thành subtype của nhau.

### `sealed interface` cũng có thể generic

```java
sealed interface Response<T> permits Ok, Error {
    T orElse(T fallback);
}

record Ok<T>(T value) implements Response<T> {
    @Override
    public T orElse(T fallback) {
        return value;
    }
}

record Error<T>(String message) implements Response<T> {
    @Override
    public T orElse(T fallback) {
        return fallback;
    }
}
```

Đây là **một hệ phân cấp minh họa riêng** cho Java 17. Các `record` là `final` ngầm định, nên có thể là direct implementors của sealed interface. `Ok<String>` và `Error<String>` đều là `Response<String>`, nhưng `Error<Integer>` là `Response<Integer>`. Record không thể `extends` class khác vì nó đã kế thừa `java.lang.Record`, nhưng có thể `implements` generic sealed interface.

Hai nhánh `Ok<T>` và `Error<T>` nên tồn tại cùng package/module với `Response<T>` theo quy tắc sealed; ví dụ được viết cùng compilation unit để dễ đọc. Đây là ví dụ thiết kế **tổng kiểu (sum type) theo các trường hợp thành công/thất bại**, không phải ngoại lệ runtime bắt buộc.

## 5. Ba đường đi của một nhánh sealed: `final`, `sealed`, `non-sealed`

Một direct subclass của sealed class phải chọn một trong ba hướng:

```java
sealed abstract class Node<T> permits Leaf, Branch, OpenNode {
    protected final T value;

    Node(T value) {
        this.value = value;
    }
}

final class Leaf extends Node<String> {
    Leaf(String value) {
        super(value);
    }
}

sealed class Branch<T> extends Node<T> permits FinalBranch {
    Branch(T value) {
        super(value);
    }
}

final class FinalBranch extends Branch<Integer> {
    FinalBranch(Integer value) {
        super(value);
    }
}

non-sealed class OpenNode<T> extends Node<T> {
    OpenNode(T value) {
        super(value);
    }
}

final class CustomOpenNode extends OpenNode<Long> {
    CustomOpenNode(Long value) {
        super(value);
    }
}
```

Đây là **một sơ đồ lớp minh họa bằng Java 17**. Quan hệ của các nhánh:

- `Leaf` là `final`: kết thúc nhánh `Node<String>`.
- `Branch<T>` là `sealed`: nó mở một tầng phân loại nhưng chỉ cho phép `FinalBranch` trực tiếp kế thừa; `FinalBranch` dùng `T = Integer`.
- `OpenNode<T>` là `non-sealed`: từ đây các subtype không nhất thiết phải được `Node<T>` liệt kê trong `permits`. `CustomOpenNode` hợp lệ khi `extends OpenNode<Long>`.
- `final class CustomOpenNode` **không phải** direct subclass của `Node`, nên không cần nằm trong `permits Leaf, Branch, OpenNode` của `Node`.
- `sealed` và `non-sealed` không thay đổi quan hệ generic: `OpenNode<Long>` là subtype của `Node<Long>`, không mặc nhiên là subtype của `Node<Object>`.

Tính đóng của hệ phân cấp cần được hiểu chính xác: nếu có một nhánh `non-sealed`, **tập toàn bộ subclass gián tiếp không còn đóng**. `permits` chỉ đóng tập subclass **trực tiếp** của đúng sealed parent. Do đó khi thiết kế một API cần biết đầy đủ mọi subtype để xử lý theo từng nhánh, phải cân nhắc tác động của `non-sealed`.

## 6. Ví dụ Java 17 hoàn chỉnh: generic, abstract, final và sealed phối hợp

Chương trình dưới đây minh họa một hệ phân cấp generic duy nhất. Root `Item<T>` vừa `abstract` vừa `sealed`. Nó có ba direct permitted subclasses: `TextItem` kết thúc nhánh, `NumberItem<N>` mở lại nhánh, và `FlagItem` tiếp tục là sealed cho hai loại cờ.

Lưu toàn bộ mã thành **`SealedGenericHierarchyDemo.java`**:

```java
import java.util.List;

public class SealedGenericHierarchyDemo {

    static abstract sealed class Item<T>
            permits TextItem, NumberItem, FlagItem {

        private final T value;

        protected Item(T value) {
            this.value = value;
        }

        public final T value() {
            return value;
        }

        public abstract String kind();

        public String describe() {
            return kind() + ":" + value;
        }
    }

    static final class TextItem extends Item<String> {
        TextItem(String text) {
            super(text);
        }

        @Override
        public String kind() {
            return "text";
        }

        int length() {
            return value().length();
        }
    }

    static non-sealed class NumberItem<N extends Number>
            extends Item<N> {
        NumberItem(N number) {
            super(number);
        }

        @Override
        public String kind() {
            return "number";
        }

        double doubled() {
            return value().doubleValue() * 2;
        }
    }

    static final class IntegerItem extends NumberItem<Integer> {
        IntegerItem(Integer number) {
            super(number);
        }

        @Override
        public String kind() {
            return "integer";
        }
    }

    static abstract sealed class FlagItem
            extends Item<Boolean> permits OnFlag, OffFlag {

        protected FlagItem(Boolean value) {
            super(value);
        }

        @Override
        public String kind() {
            return "flag";
        }
    }

    static final class OnFlag extends FlagItem {
        OnFlag() {
            super(true);
        }
    }

    static final class OffFlag extends FlagItem {
        OffFlag() {
            super(false);
        }
    }

    static <T> T unwrap(Item<T> item) {
        return item.value();
    }

    static void printAll(List<? extends Item<?>> items) {
        for (Item<?> item : items) {
            System.out.println("item: " + item.describe());
        }
    }

    public static void main(String[] args) {
        TextItem text = new TextItem("Java");
        IntegerItem concreteNumber = new IntegerItem(21);
        Item<String> textView = text;
        Item<Integer> numberView = concreteNumber;
        Item<Boolean> flagView = new OnFlag();

        System.out.println("text value: " + unwrap(textView));
        System.out.println("text length: " + text.length());
        System.out.println("twice: " + concreteNumber.doubled());
        System.out.println("flag value: " + unwrap(flagView));
        System.out.println("dispatch: " + numberView.describe());

        NumberItem<Double> fractional = new NumberItem<>(2.5);
        System.out.println("open branch: " + fractional.describe());
        System.out.println("off branch: " + new OffFlag().describe());

        List<Item<?>> items = List.of(
                textView, numberView, flagView);
        printAll(items);
    }
}
```

Chạy với **JDK 17 hoặc mới hơn** trong thư mục chứa tệp:

```bash
javac SealedGenericHierarchyDemo.java
java SealedGenericHierarchyDemo
```

**Stdout mong đợi:**

```text
text value: Java
text length: 4
twice: 42.0
flag value: true
dispatch: integer:21
open branch: number:2.5
off branch: flag:false
item: text:Java
item: integer:21
item: flag:true
```

### Giải thích từng mối quan hệ

**Root generic abstract sealed:** `Item<T>` khai báo `T` cho toàn bộ lớp. Nó có constructor `protected Item(T value)`, method trả `T`, abstract method `kind()` và concrete method `describe()`. Không thể `new Item<String>("x")` vì lớp là abstract. Một class bên ngoài danh sách `permits` không thể trực tiếp `extends Item<...>`.

**Nhánh final cố định kiểu:** `TextItem extends Item<String>`. Method `value()` kế thừa trả `String`, nên `text.length()` có thể gọi `value().length()` mà không cast. Modifier `final` cấm bất kỳ class nào tiếp tục `extends TextItem`.

**Nhánh non-sealed vẫn generic:** `NumberItem<N extends Number> extends Item<N>` giữ tham số kiểu `N` và đặt bound `Number`. Vì `non-sealed`, `IntegerItem extends NumberItem<Integer>` được phép mà không xuất hiện trong `permits` của `Item<T>`. Đây là **subclass gián tiếp** của root, đi qua direct subclass `NumberItem`.

**Nhánh sealed cấp hai:** `FlagItem extends Item<Boolean> permits OnFlag, OffFlag` cố định `T = Boolean` và giới hạn trực tiếp các lớp con thành `OnFlag`, `OffFlag`. `FlagItem` vẫn abstract; cả hai lớp con là final và kế thừa `kind()` đã được `FlagItem` cài đặt.

**Generic method đọc kiểu cha:** `<T> T unwrap(Item<T> item)` có tham số kiểu riêng của method. Nó nhận `Item<String>` hoặc `Item<Boolean>` mà không mất kiểu trả về. Khi gọi `unwrap(textView)`, kết quả có kiểu `String`; khi gọi `unwrap(flagView)`, có kiểu `Boolean`. Type parameter method này không được khai báo bởi `Item`.

**Đa hình và dispatch:** biến `numberView` có kiểu tĩnh `Item<Integer>`, nhưng trỏ tới `IntegerItem`. Lời gọi `numberView.describe()` thực thi phương thức `describe()` kế thừa của `Item`; bên trong, lời gọi `kind()` là một virtual call đến bản override `IntegerItem.kind()`, nên kết quả `integer:21`. Generic type và dynamic dispatch bổ sung cho nhau, không phải là cùng một cơ chế.

**Danh sách kiểu hỗn hợp:** `List<Item<?>>` có thể chứa nhiều `Item<T>` có các `T` khác nhau. `printAll(List<? extends Item<?>>)` chỉ đọc và in mỗi item, không giả định tất cả có chung kiểu giá trị cụ thể. Ngược lại, `List<Item<String>>` không được gán trực tiếp từ `List<TextItem>` vì tính bất biến (*invariance*) của generic collection.

## 7. Quan hệ giữa sealed hierarchy và tham số kiểu

Tập subtype được quản lý bằng **tên lớp**, còn đối số kiểu được quản lý bằng **hợp đồng generics**. Ví dụ:

```java
// Root:
abstract sealed class Event<T> permits TextEvent, GenericEvent { }

// Nhánh cố định:
final class TextEvent extends Event<String> { }

// Nhánh giữ tham số:
final class GenericEvent<U> extends Event<U> { }
```

Ba khai báo này là **ví dụ độc lập** với chương trình `Item<T>` ở trên. `TextEvent` là subtype của `Event<String>`; `GenericEvent<Integer>` là subtype của `Event<Integer>`. Tên `U` khác tên `T` của cha nhưng việc ánh xạ `extends Event<U>` mới quyết định quan hệ kiểu.

Không được viết:

```java
// sealed class Event<T> permits TextEvent<String> { }
// Sai: permits cần tên class, không phải một parameterized type.
```

Tương tự, sealed parent không thể “cho phép `Event<String>` nhưng cấm `Event<Integer>`” chỉ bằng việc liệt kê type arguments trong `permits`. Nếu miền dữ liệu cần các nhánh cố định kiểu, thiết kế subclass cụ thể như `TextEvent extends Event<String>`; nếu cần hạn chế kiểu theo bound, dùng generic bounds; nếu cần điều kiện dữ liệu runtime, kiểm tra trong constructor/factory.

Java vẫn giữ tính **invariant**: dù `String` là subtype của `Object`, `Event<String>` không tự là subtype của `Event<Object>`. Có thể dùng `Event<?>` khi chỉ cần quan sát một event bất kỳ hoặc `Event<? extends CharSequence>` khi cần đọc giá trị đáp ứng một hợp đồng rộng hơn.

## 8. Sealed và pattern matching: khả năng xử lý theo tập nhánh

Khi hệ phân cấp được đóng, compiler có nhiều thông tin hơn về các subclass khả dĩ. Điều này có ích cho pattern matching, phân tích nhánh và công cụ bảo trì API.

Ví dụ một nhánh `Result<T>` chỉ có `Success` và `Failure`: người đọc có thể thiết kế xử lý cho hai trường hợp đó. Tuy nhiên phải phân biệt:

- **Java 17:** sealed classes/interfaces là tính năng chính thức. Pattern matching cho `instanceof` đã có thể dùng theo Java 17. Nhưng **pattern matching for `switch`** trong Java 17 thuộc preview, vì vậy đừng mặc nhiên sao chép một mẫu `switch` dựa trên sealed patterns rồi kỳ vọng `javac File.java` chạy không cần cờ preview.
- **Java 21 trở lên:** pattern matching for `switch` được chuẩn hóa; một số dạng xử lý sealed hierarchy có thể kiểm tra tính bao phủ các trường hợp ngay khi biên dịch, tùy kiểu selector và pattern. Cú pháp/điều kiện cụ thể phải theo phiên bản JDK đang dùng.
- **Có `non-sealed`:** dù root liệt kê một direct subclass `non-sealed`, phía dưới nhánh đó có thể còn các subtype không xác định trước; không suy diễn tập toàn bộ concrete class là hữu hạn chỉ từ `permits`.

Trong Java 17, một cách đọc nhánh không cần tính năng preview là `instanceof` pattern với các kiểu **reifiable**:

```java
static String category(Object obj) {
    if (obj instanceof SealedGenericHierarchyDemo.TextItem) {
        return "text";
    }
    if (obj instanceof SealedGenericHierarchyDemo.NumberItem<?>) {
        return "numeric";
    }
    if (obj instanceof SealedGenericHierarchyDemo.FlagItem) {
        return "flag";
    }
    return "other";
}
```

Đây là **method minh họa** có thể đặt trong một class truy cập được các nested types của `SealedGenericHierarchyDemo` (ví dụ chính bên trong lớp đó). `NumberItem<?>` có wildcard không hạn chế, nên kiểm tra `instanceof` kiểu đó hợp lệ. Không dùng `obj instanceof NumberItem<Integer>` để kiểm tra type argument cụ thể; dạng đó không reifiable theo Java erasure.

## 9. Abstract + sealed, final + generic và non-sealed có thể phối hợp

Đừng coi ba modifier này là những lựa chọn bắt buộc loại trừ nhau trong toàn bộ hệ phân cấp:

- **Root `abstract sealed`**: không thể khởi tạo trực tiếp và giới hạn các subclass trực tiếp.
- **Nhánh `final`**: là kiểu cụ thể hoàn thiện hợp đồng rồi dừng kế thừa.
- **Nhánh `sealed`**: tiếp tục khống chế các nhánh con của chính nó; có thể đồng thời `abstract`.
- **Nhánh `non-sealed`**: mở tiếp khả năng tạo subtype. Có thể là concrete hoặc abstract; việc mở lại có ý nghĩa theo quy tắc direct sealed supertype.
- **Generic ở từng cấp**: root `Base<T>`, nhánh `Branch<U> extends Base<U>`, nhánh `Fixed extends Base<String>`, hay nhánh thêm kiểu `Tagged<T, M> extends Base<T>` đều được compiler kiểm tra độc lập với sealed modifier.

Một lớp **không thể vừa `final` vừa `abstract`** vì abstract yêu cầu class có thể được subclass để triển khai, còn final cấm subclass. Một lớp cũng không được kết hợp `final` với `sealed` hoặc `non-sealed` như thể chúng mô tả ba cách kế thừa đồng thời; direct permitted subclass phải chọn một chế độ hợp lệ.

Constructor `protected` của sealed abstract class cho phép subclass được quyền khởi tạo phần cha bằng `super(...)`, nhưng không mở quyền `extends` cho một class bị loại khỏi `permits`. Access modifier và sealed restriction là hai tầng kiểm tra khác nhau.

## 10. Lỗi thường gặp và cách khắc phục

**Lỗi 1 — Nghĩ `final` cấm generics.** `final class Box<T>` hợp lệ. `final` cấm kế thừa lớp, không cấm truyền `Box<String>`, `Box<Integer>`.

**Lỗi 2 — `new` trực tiếp abstract generic class.** `new AbstractStore<String>(...)` không hợp lệ; tạo một subclass concrete đáp ứng constructor và triển khai các abstract method, hoặc cung cấp factory trả về subclass phù hợp.

**Lỗi 3 — Quên cài đặt abstract generic method.** Nếu cha có `abstract T read()`, lớp con concrete cần triển khai method với return type đáp ứng quan hệ kiểu sau thay thế type arguments; chỉ khai báo `class Child extends Parent<String>` không tự cài đặt method.

**Lỗi 4 — Dùng `permits` với đối số kiểu.** `permits Good<String>` sai. Danh sách `permits` liệt kê các **kiểu con được khai báo**, chẳng hạn `permits Good, Bad`.

**Lỗi 5 — Direct permitted subclass không chọn `final`, `sealed`, `non-sealed`.** Với một class kế thừa trực tiếp sealed parent, chỉ `class Good extends Parent` là chưa đủ; phải chỉ rõ modifier phù hợp như `final class Good...`, `sealed class Good...` hoặc `non-sealed class Good...`.

**Lỗi 6 — Cho rằng một lớp ở nơi bất kỳ cũng được dùng `non-sealed`.** `non-sealed` có quy tắc ngữ cảnh riêng và cần direct sealed supertype. Không thêm nó vào một class bình thường chỉ để nói rằng class ấy “mở rộng được”.

**Lỗi 7 — Đặt direct permitted subclass sai module/package.** Với named module, chúng cần cùng module; với unnamed module, cần cùng package. Chỉ khai báo `permits` không vô hiệu hóa điều kiện vị trí hoặc kiểm tra truy cập.

**Lỗi 8 — Nghĩ mọi lớp con gián tiếp phải nằm trong `permits` của root.** Nếu root cho phép `non-sealed NumberItem`, lớp `IntegerItem extends NumberItem<Integer>` không phải direct subclass của root và không cần có tên ở root permits.

**Lỗi 9 — Tưởng `sealed` đóng mọi lớp con sau nhánh `non-sealed`.** Một khi một nhánh hợp lệ được đánh dấu `non-sealed`, nó có thể mở rộng về sau; thiết kế phân loại phải cân nhắc khả năng này.

**Lỗi 10 — Nhầm quan hệ kế thừa class với covariance của generic type.** `TextItem extends Item<String>` không kéo theo `Item<String> extends Item<Object>`. Dùng wildcard như `Item<?>` hoặc `Item<? extends CharSequence>` khi muốn nhận nhiều tham số hóa.

**Lỗi 11 — Gọi method của subclass thông qua biến kiểu superclass.** `Item<String> item = new TextItem("x");` không cho gọi trực tiếp `item.length()` nếu `Item<T>` không khai báo `length()`. Static type quyết định method có thể gọi; runtime dispatch chỉ quyết định triển khai override của method đã có.

**Lỗi 12 — Tưởng type arguments được giữ nguyên ở runtime vì sealed.** Sealed kiểm soát quan hệ kế thừa; Java generics thông thường vẫn bị erasure. Không kiểm tra `instanceof Item<String>`, mà dùng `Item<?>` ở vị trí thích hợp hoặc truyền runtime type token nếu bài toán cần.

**Lỗi 13 — Phụ thuộc vào preview switch của Java 17.** Nếu bạn dùng pattern switch từ tài liệu phiên bản mới, xác nhận JDK và cờ preview trước khi chạy; ví dụ đầy đủ trong bài tránh yêu cầu preview.

**Lỗi 14 — Dùng `final` như cam kết bất biến sâu.** Generic class final với field final có thể vẫn giữ một object mutable. Muốn value object an toàn, cân nhắc defensive copy, API đọc và xác định rõ trạng thái có được thay đổi hay không.

**Lỗi 15 — Mở quá nhiều nhánh `non-sealed` dù muốn dữ liệu dạng tổng kiểu hữu hạn.** Nếu yêu cầu xử lý đầy đủ một tập trạng thái đóng, nên dùng các nhánh final/sealed thật sự; chỉ dùng non-sealed khi có lý do cần extension point cho người khác.

## 11. Khi nào nên chọn kiểu thiết kế nào?

| Tình huống | Thiết kế gợi ý | Lý do |
|---|---|---|
| Chia sẻ trạng thái và yêu cầu subclass triển khai thao tác | `abstract class Base<T>` | Có constructor, trường, method chung và abstract method |
| Đối tượng generic dùng làm giá trị, không có chủ đích mở rộng qua inheritance | `final class Value<T>` hoặc `record Value<T>(T x)` | Giảm bề mặt mở rộng; record tự cung cấp các thành phần theo ngữ nghĩa record |
| Chỉ có tập direct variants được phép | `sealed interface Result<T> permits ...` | Định hình rõ hệ phân cấp các trường hợp |
| Cần root abstract và một số nhánh concrete, một số nhánh cần phân loại tiếp | `abstract sealed class Node<T> permits ...` | Kết hợp hợp đồng, trạng thái và giới hạn nhánh |
| Muốn một nhánh được bên thứ ba mở rộng | Direct subclass `non-sealed` | Mở lại kế thừa tại nhánh đó |
| Cần bổ sung dữ liệu nhưng không có quan hệ “is-a” thực sự | Composition như `record Tagged<T, M>(T data, M metadata)` | Tránh ràng buộc kế thừa không cần thiết |

Chọn `sealed` khi sự đóng/mở của hệ phân cấp là **hợp đồng có chủ đích**, không chỉ vì muốn hạn chế số lượng class. Nếu code bên ngoài module/package cần tự bổ sung variant, một hệ phân cấp sealed đóng hoàn toàn có thể không phù hợp. Còn nếu cần mô hình hóa những trạng thái xác định như thành công/thất bại hoặc loại node cố định, sealed giúp hợp đồng dễ đọc và dễ kiểm soát hơn.

Không dùng `sealed` để thay cho kiểm tra dữ liệu runtime: `sealed` không đảm bảo một `Result<String>` có chuỗi hợp lệ, không chống `null`, cũng không tự làm đối tượng thread-safe.

## 12. Bài tập tự kiểm tra có đáp án

**1.** `abstract class Store<T>` có thể có constructor và method concrete không?  
**Đáp án:** Có. `abstract` ngăn tạo trực tiếp class và cho phép abstract method, nhưng constructor và concrete method hoàn toàn hợp lệ.

**2.** `final class Box<T>` có nhận `String` và `Integer` làm type arguments không?  
**Đáp án:** Có, `final` không cấm tham số hóa `T`.

**3.** `sealed abstract class Result<T> permits Ok, Error` có cho `class Other extends Result<String>` ở cùng package không?  
**Đáp án:** Không, nếu `Other` không được đưa vào tập direct permitted subclasses. Ở cùng package chỉ thỏa một điều kiện vị trí, không thay cho `permits`.

**4.** Một direct permitted subclass phải dùng modifier nào?  
**Đáp án:** Một trong `final`, `sealed` hoặc `non-sealed` phù hợp với cách tiếp tục hệ phân cấp.

**5.** `non-sealed class NumberItem<N extends Number> extends Item<N>` có thể có `final class IntegerItem extends NumberItem<Integer>` không?  
**Đáp án:** Có. `IntegerItem` là subclass của nhánh non-sealed, không cần xuất hiện trong root `Item` permits.

**6.** Từ `TextItem extends Item<String>`, biến `Item<Object> o = new TextItem("x");` có hợp lệ không?  
**Đáp án:** Không. `TextItem` là subtype của `Item<String>`, không phải `Item<Object>` do generics invariant.

**7.** Có thể viết `permits Ok<String>, Error<Integer>` để đóng generic hierarchy theo type arguments không?  
**Đáp án:** Không. `permits` liệt kê direct subclasses theo tên khai báo, không nhận parameterized types.

**8.** Tại sao `Item<Boolean> b = new OnFlag()` hợp lệ?  
**Đáp án:** `OnFlag extends FlagItem` và `FlagItem extends Item<Boolean>`; quan hệ transitive inheritance giữ đúng type argument `Boolean`.

**9.** `final` trên class có đồng nghĩa field kiểu `List<T>` bên trong không thể sửa được không?  
**Đáp án:** Không. `final class` cấm subclass, không tự làm phần tử và object bên trong immutable.

**10.** Với Java 17, sealed class có cần `--enable-preview` không?  
**Đáp án:** Không. Sealed classes/interfaces đã chính thức; một số syntax pattern switch ở Java 17 mới là preview.

**11.** Generic method `<T> T unwrap(Item<T> x)` có dùng được với nhiều nhánh của sealed hierarchy không?  
**Đáp án:** Có, miễn mỗi nhánh có supertype `Item<T>` thích hợp; `T` được suy luận hoặc chỉ định theo lời gọi.

**12.** Khi nào nhánh `non-sealed` làm giảm khả năng liệt kê hữu hạn toàn bộ subtype?  
**Đáp án:** Khi nhánh đó cho phép tiếp tục có các subtype gián tiếp không nằm trong root `permits`, vì vậy toàn bộ cây không còn đóng.

**13.** Một `record R<T>(T value)` có thể triển khai sealed interface không?  
**Đáp án:** Có, nếu là direct permitted implementor phù hợp. Record là final ngầm định và không thể kế thừa class tùy ý.

**14.** Có nên dùng `abstract` + `final` để “không tạo được và không kế thừa được” không?  
**Đáp án:** Không. Hai modifier này mâu thuẫn trên class; với utility class, dùng class thường/final và constructor private khi cần, còn abstract class có chủ đích làm superclass.

**15.** `sealed` có làm mất type erasure không?  
**Đáp án:** Không. Hai cơ chế độc lập: sealed giới hạn direct subtypes, generic erasure ảnh hưởng thông tin type arguments tại runtime.

**16.** Để một subclass được phép từ sealed parent tiếp tục giới hạn một tầng nữa, chọn modifier gì?  
**Đáp án:** `sealed` với tập subclasses được phép của chính nhánh đó; các nhánh ở tầng tiếp theo tiếp tục chọn `final`, `sealed` hoặc `non-sealed`.

**17.** Trong ví dụ, vì sao `numberView.describe()` in `integer:21` dù `describe()` được định nghĩa ở root?  
**Đáp án:** `describe()` gọi `kind()`, còn `kind()` được override ở `IntegerItem`. Runtime dynamic dispatch chọn bản `kind()` đó.

**18.** Nếu muốn cho một cặp dữ liệu có generic type mà không cần is-a hierarchy, nên cân nhắc gì?  
**Đáp án:** Composition, ví dụ `record Pair<A,B>(A first, B second)`, thay vì tạo subclass chỉ để chứa thêm trường.

Tóm lại, **generics quyết định quan hệ kiểu**, còn **`abstract`/`final`/`sealed` quyết định hợp đồng khởi tạo và mở rộng lớp**. Với Java 17, bạn có thể dùng root generic abstract sealed, phân nhánh bằng final/sealed/non-sealed, giữ type arguments an toàn khi kế thừa và bổ sung hành vi theo từng subtype. Thiết kế đúng khi tập subtype được kiểm soát theo nhu cầu thực tế, không đánh đổi tính rõ ràng chỉ để sử dụng nhiều modifier.


</details>

- [Quay lại đầu trang](#back-to-top)

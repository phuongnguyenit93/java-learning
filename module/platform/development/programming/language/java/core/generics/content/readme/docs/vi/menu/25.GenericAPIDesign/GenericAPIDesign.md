<a id="back-to-top"></a>

# Thiết kế API

## Menu
- [Chọn tham số kiểu ở cấp lớp hay cấp phương thức](#generic-api-design-001)
- [Đặt tên và viết tài liệu cho quan hệ giữa các kiểu](#generic-api-design-002)
- [Dùng kiểu chính xác khi cần giữ quan hệ đọc–ghi](#generic-api-design-003)
- [Dùng wildcard cho đầu vào linh hoạt theo PECS](#generic-api-design-004)
- [Tránh tham số kiểu chỉ xuất hiện một lần mà không tạo quan hệ cần thiết](#generic-api-design-005)
- [Thiết kế kho lưu trữ nhiều kiểu với Class&lt;T&gt; làm khóa](#generic-api-design-006)
- [Khoanh vùng cast không kiểm tra và nêu bất biến bảo đảm an toàn](#generic-api-design-007)
- [Ảnh hưởng của đổi giới hạn và thứ tự giới hạn đến tương thích nhị phân](#generic-api-design-008)
- [Cân bằng tính linh hoạt, độ dễ hiểu và khả năng suy luận kiểu](#generic-api-design-009)

## <a id="generic-api-design-001">Chọn tham số kiểu ở cấp lớp hay cấp phương thức</a>

<details>
<summary>Xem chi tiết</summary>

# Chọn tham số kiểu ở cấp lớp hay cấp phương thức trong Java Generics

## Mục tiêu

Trong Java, hai chữ ký sau đều sử dụng generics:

```java
class Box<T> {
    private T value;
    Box(T value) { this.value = value; }
    T get() { return value; }
}

static <T> T identity(T value) {
    return value;
}
```

Tuy nhiên, ý nghĩa của hai biến kiểu T là khác nhau. Trong `Box<T>`, T là một phần **hợp đồng kiểu của object**: constructor, field và những method instance có thể cùng duy trì một invariant. Trong `identity`, T chỉ cần gắn đầu vào với kết quả của **một lần gọi**. Những lần gọi sau có thể suy luận một T hoàn toàn khác.

Nguyên tắc hữu ích nhất là **đặt tham số kiểu ở phạm vi nhỏ nhất vẫn bao quát mọi quan hệ kiểu mà API phải bảo đảm**. Nếu một object giữ kiểu đó xuyên suốt nhiều thao tác, dùng generic class/interface. Nếu kiểu chỉ thuộc một thuật toán, dùng generic method. Nếu không cần một tên T để liên hệ đầu vào với đầu ra, cân nhắc wildcard.

Sau bài, bạn có thể:

- Phân biệt type parameters của class, interface, method và constructor.
- Chọn đúng scope cho trạng thái `Box<T>`, `Repository<E>` và các utility methods.
- Kết hợp class-level E với method-level K/R/U.
- Viết static generic factory và tránh tham chiếu T của class trong static context.
- Nhận diện type parameter shadowing, generic method giả an toàn, raw type và erasure name clash.
- Dùng wildcard, PECS, target typing và type inference để tối ưu public API.
- Chạy chương trình Java 17 hợp lệ và kiểm chứng các ví dụ cố ý bị compiler từ chối.

## 1. T thuộc đối tượng hay thuộc lời gọi?

| Nội dung | Type parameter cấp lớp | Type parameter cấp method |
| --- | --- | --- |
| Cú pháp | `class Box<T>`, `interface Store<E>` | `static <T> T identity(T)` |
| Phạm vi | Các thành viên thích hợp của class, đặc biệt instance fields/methods | Chỉ method khai báo T |
| Kiểu được xác định tại | Việc dùng class/interface như `Box<String>` | Từng lời gọi method |
| Quan hệ thường diễn đạt | `set(T)` và `get():T` phải thống nhất | `convert(T):R` của một thao tác |
| Ví dụ | `Repository<User>`, `Cache<K,V>` | `<T,R> map(...)`, `<T> copy(...)` |
| Có thể dùng class T trong static? | Không | Static method tự khai báo `<T>` mới |
| Có cần lưu giá trị T? | Không bắt buộc nhưng thường gắn với contract lâu dài | Không tự tạo generic state cho class |

T cấp lớp không có nghĩa JVM tạo một class riêng cho từng T. Sau *type erasure*, `Box<String>` và `Box<Integer>` thường có cùng runtime class Box. Compiler mới là nơi bảo vệ các quan hệ kiểu tương ứng ở mã nguồn.

Câu hỏi đầu tiên khi review: **T có đại diện cho thứ object này chứa hoặc cung cấp nhất quán trong nhiều thao tác, hay chỉ là biến dùng một lần trong một phép toán?**

## 2. Class-level T: giữ invariant qua nhiều method

Một hộp có thể được khai báo:

```java
final class Box<T> {
    private T value;

    Box(T value) {
        this.value = value;
    }

    T get() {
        return value;
    }

    void set(T next) {
        value = next;
    }
}
```

Caller:

```java
Box<String> title = new Box<>("Java");
String current = title.get();
title.set("Generics");

// title.set(42); // Lỗi biên dịch.
```

Cùng một T liên kết field, constructor, getter và setter. Khi một tham chiếu mang kiểu `Box<String>`, `get()` trả String và `set` đòi String. Đó là lý do phù hợp để khai báo T ở cấp lớp.

Một instance khác có thể có kiểu khác:

```java
Box<Integer> counter = new Box<>(7);
counter.set(9);
```

T không bị cố định cho mọi Box trong toàn ứng dụng; từng cách tham số hóa class có contract riêng ở compile time.

### Trường hợp thực tế nên dùng class-level type parameter

- `Repository<E>` lưu nhiều E, nhận E ở `add` và trả E ở `get`.
- `Cache<K,V>` luôn dùng K làm khóa và V làm giá trị.
- `Queue<T>` hỗ trợ `offer(T)` và `poll():T`.
- `Result<T>` mô tả một kết quả typed.
- `ConfiguredMapper<T,R>` lưu callback T→R để áp dụng nhiều lần.
- `Comparable<T>` diễn đạt loại đối tượng mà một implementation biết cách so sánh.

Không phải cứ class không có field T thì không được generic: một interface có thể khai báo một **hợp đồng liên kết nhiều phương thức** dù nó không sở hữu state. Tiêu chí chính là phạm vi cần giữ một T thống nhất.

## 3. Method-level T: thuật toán dùng trong từng lần gọi

Ví dụ:

```java
static <T> T identity(T value) {
    return value;
}
```

Lời gọi:

```java
String name = identity("An");
Integer count = identity(3);
```

Compiler suy luận T độc lập cho mỗi lời gọi. Method không cần biết hoặc giữ giá trị kiểu T giữa các lần gọi.

Cú pháp tổng quát:

```text
[modifiers] <TypeParameters> ReturnType methodName(parameters)
```

`<T>` nằm **trước kiểu trả về**. Không viết `static T <T> identity(...)`.

### Nhiều type parameters trong một method

```java
static <T, R> R convert(
        T input, Function<? super T, ? extends R> mapper) {
    return mapper.apply(input);
}
```

T liên hệ với kiểu input; R liên hệ với kiểu output. Một lần gọi có thể String→Integer, lần khác Integer→String. Nếu không có cấu hình hay state giữ lâu dài quan hệ T/R, đặt hai biến ở method là tự nhiên.

Java suy luận generic method type arguments từ đối số, target types, lambda/method reference và bounds. Trong trường hợp cần rõ ràng:

```java
List<Integer> lengths =
        Operations.<String, Integer>mapAll(
                List.of("A", "Java"), String::length);
```

Nhưng lời gọi ngắn hơn thường đủ:

```java
List<Integer> lengths =
        Operations.mapAll(List.of("A", "Java"), String::length);
```

`Operations` là utility class trong chương trình phần 8. Explicit type arguments nên dùng khi cần giúp inference hoặc làm rõ ý định; không nên viết mọi nơi nếu chúng chỉ lặp lại điều compiler đã biết.

### Generic method không bắt buộc static

Cả hai đều hợp lệ:

```java
class Worker {
    <T> T apply(T input) {
        return input;
    }

    static <T> T echo(T input) {
        return input;
    }
}
```

Phân loại cấp method nói về vị trí khai báo biến kiểu, không phải từ khóa static. Static có thêm giới hạn riêng với class-level type variables ở phần 6.

## 4. Repository cấp lớp và phép nhóm cấp method

Một thiết kế phổ biến:

```java
final class Repository<E> {
    private final List<E> items = new ArrayList<>();

    void add(E element) {
        items.add(element);
    }

    List<E> snapshot() {
        return List.copyOf(items);
    }

    <K> Map<K, List<E>> groupBy(
            Function<? super E, ? extends K> classifier) {
        return items.stream().collect(Collectors.groupingBy(
                classifier,
                LinkedHashMap::new,
                Collectors.toList()));
    }
}
```

Đoạn cần các imports `List`, `ArrayList`, `Map`, `LinkedHashMap`, `Function`, `Collectors`. Ví dụ đầy đủ ở phần 8.

Giả sử:

```java
Repository<String> names = new Repository<>();
names.add("An");
names.add("Binh");
names.add("Anh");

Map<Integer, List<String>> byLength =
        names.groupBy(String::length);

Map<Character, List<String>> byFirstLetter =
        names.groupBy(text -> text.charAt(0));
```

E=String được giữ xuyên suốt trạng thái repository. Nhưng K được suy luận theo **từng lần nhóm**, có thể Integer hoặc Character. Không nên thêm K vào `Repository<E,K>` nếu repository không có một loại khóa cố định trong cả đời object.

### Vì sao callback có wildcard?

`Function<? super E, ? extends K>` mô tả một mapper:

- Nó **tiêu thụ** E, nên có thể nhận loại cha của E (super).
- Nó **sản xuất** kết quả tương thích K, nên có thể trả một subtype của K (extends).

Đây là nguyên tắc **PECS — Producer Extends, Consumer Super**. Type variable K ở method liên kết kiểu kết quả của mapper với kiểu khóa của `Map<K,List<E>>`.

### Snapshot và mutability là hợp đồng khác

`List.copyOf(items)` tạo list không sửa được về mặt cấu trúc, không chấp nhận phần tử null và không deep-copy các object E mutable. Các tính chất đó không làm thay đổi quyết định E đặt cấp lớp hay K đặt cấp method.

## 5. Class generic vẫn có thể chứa method generic riêng

Một Box với giá trị T cố định có thể hỗ trợ phép map ra loại R bất kỳ:

```java
final class Box<T> {
    private final T value;

    Box(T value) {
        this.value = value;
    }

    <R> R map(Function<? super T, ? extends R> mapper) {
        return mapper.apply(value);
    }
}
```

Lời gọi:

```java
Box<String> title = new Box<>("Java");
Integer length = title.map(String::length);
String upper = title.map(String::toUpperCase);
```

Trong cả hai lời gọi, Box vẫn chứa String. Nhưng R của method lần đầu là Integer, lần sau là String. Nếu R bị đặt lên class (`Box<T,R>`), một caller có thể phải xác định R ngay lúc tạo Box dù chưa biết mình sẽ map thành gì.

Ngược lại, nếu class được tạo để **giữ chính mapper T→R lâu dài**:

```java
final class ConfiguredMapper<T, R> {
    private final Function<? super T, ? extends R> mapper;

    ConfiguredMapper(Function<? super T, ? extends R> mapper) {
        this.mapper = mapper;
    }

    R apply(T value) {
        return mapper.apply(value);
    }
}
```

lúc này class-level T/R hoàn toàn hợp lý. Không có quy tắc “mọi hàm map đều đặt R ở method”; scope do thời gian tồn tại của contract quyết định.

## 6. Static không được dùng T của class như type variable

**Tệp BadStaticScope.java, cố ý không biên dịch:**

```java
class BadStaticScope<T> {
    static T cache;
    static T create() { return null; }
}
```

`javac --release 17` từ chối hai chỗ dùng T:

```text
non-static type variable T cannot be referenced from a static context
```

Static member thuộc về class chứ không thuộc một instance `BadStaticScope<String>` hoặc `BadStaticScope<Integer>` cụ thể. T của instance không thể tự động trở thành một loại cho static field.

### Static generic factory đúng cách

```java
final class Box<T> {
    private final T value;

    private Box(T value) {
        this.value = value;
    }

    static <X> Box<X> of(X value) {
        return new Box<>(value);
    }
}
```

Lời gọi:

```java
Box<String> text = Box.of("hello");
Box<Integer> number = Box.of(10);
```

X là **biến kiểu riêng của static method**, được suy luận tại từng lời gọi. Static factory không tham chiếu T của một Box đang tồn tại.

Java cho phép dùng cùng tên T cho type parameter của class và của method static, nhưng chúng là **hai declarations khác nhau**. Chọn tên X trong ví dụ để làm rõ phạm vi.

**Không có generic field với cú pháp `static <T> T cache`**: method/constructor có type parameter khai báo riêng, field không có cơ chế đó.

## 7. Shadowing type parameter

**Tệp BadShadowing.java, cố ý không biên dịch:**

```java
class BadShadowing<T> {
    private T value;

    <T> void replace(T next) {
        value = next;
    }
}
```

Compiler thông báo:

```text
incompatible types: T#1 cannot be converted to T#2
```

T trong `<T> void replace(...)` là một type variable mới và **che** T của class. Hai T được viết giống hệt nhưng là hai khai báo khác nhau.

Nếu mục tiêu là cập nhật field kiểu T của Box, sửa thành:

```java
class GoodBox<T> {
    private T value;

    void replace(T next) {
        value = next;
    }
}
```

Nếu method cần một loại khác thật sự, dùng tên U:

```java
<U> Pair<T, U> pairWith(U extra) {
    return new Pair<>(value, extra);
}
```

Đây là một method của Box<T> trong chương trình đầy đủ phần 8, có field `value` và record `Pair<A,B>`.

T/E/K/V/R/U/X chỉ là tên quy ước. Compiler quan tâm tới **nơi khai báo và các ràng buộc**, không suy đoán rằng hai ký tự T ở các scope khác nhau phải tương đương.

## 8. Chương trình Java 17 đầy đủ đã kiểm thử

Chương trình kết hợp `Box<T>`, `Repository<E>`, utility methods với T/R riêng, constructor generic với U riêng và static generic factory.

**Tệp GenericScopeDemo.java:**

```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.stream.Collectors;

public class GenericScopeDemo {
    record Pair<A, B>(A first, B second) {}

    static final class Box<T> {
        private T value;
        Box(T value) { this.value = value; }
        T get() { return value; }
        void set(T next) { value = next; }

        <R> R map(Function<? super T, ? extends R> mapper) {
            return mapper.apply(value);
        }

        <U> Pair<T, U> pairWith(U extra) {
            return new Pair<>(value, extra);
        }

        static <X> Box<X> of(X value) {
            return new Box<>(value);
        }
    }

    static final class Repository<E> {
        private final List<E> items = new ArrayList<>();
        void add(E element) { items.add(element); }
        List<E> snapshot() { return List.copyOf(items); }

        <K> Map<K, List<E>> groupBy(
                Function<? super E, ? extends K> classifier) {
            return items.stream().collect(Collectors.groupingBy(
                    classifier, LinkedHashMap::new, Collectors.toList()));
        }
    }

    static final class Operations {
        private Operations() {}

        static <T> T choose(T a, T b, BinaryOperator<T> chooser) {
            return chooser.apply(a, b);
        }

        static <T, R> List<R> mapAll(
                List<? extends T> source,
                Function<? super T, ? extends R> mapper) {
            List<R> result = new ArrayList<>();
            for (T item : source) {
                result.add(mapper.apply(item));
            }
            return result;
        }

        static <T> void append(
                List<? extends T> source, List<? super T> destination) {
            destination.addAll(source);
        }

        static void printSize(List<?> items) {
            System.out.println("size=" + items.size());
        }
    }

    static final class Envelope<T> {
        final T payload;
        final String audit;

        <U> Envelope(T payload, U auditData) {
            this.payload = payload;
            this.audit = String.valueOf(auditData);
        }
    }

    public static void main(String[] args) {
        Box<String> title = Box.of("Java");
        System.out.println("box=" + title.get());
        title.set("Generics");
        System.out.println("updated=" + title.get());
        System.out.println("mapped=" + title.map(String::length));
        System.out.println("pair=" + title.pairWith(2026));

        Box<Integer> count = Box.of(7);
        System.out.println("otherBox=" + count.get());

        Repository<String> names = new Repository<>();
        names.add("An");
        names.add("Binh");
        names.add("Anh");
        System.out.println("repo=" + names.snapshot());
        System.out.println("groups=" + names.groupBy(String::length));

        int chosen = Operations.choose(4, 9, Integer::max);
        System.out.println("chosen=" + chosen);
        System.out.println("lengths="
                + Operations.mapAll(List.of("A", "Java"), String::length));

        List<Number> destination = new ArrayList<>(List.of(1.5));
        Operations.append(List.of(2, 3), destination);
        System.out.println("append=" + destination);
        Operations.printSize(names.snapshot());

        Envelope<String> e = new Envelope<>("payload", 1001L);
        System.out.println("envelope=" + e.payload + "/" + e.audit);
        System.out.println("classGenericAndMethodGeneric="
                + title.<Integer>map(String::length));
    }
}
```

Chạy:

```bash
javac --release 17 -Xlint:all GenericScopeDemo.java
java GenericScopeDemo
```

**Stdout đúng đã kiểm chứng:**

```text
box=Java
updated=Generics
mapped=8
pair=Pair[first=Generics, second=2026]
otherBox=7
repo=[An, Binh, Anh]
groups={2=[An], 4=[Binh], 3=[Anh]}
chosen=9
lengths=[1, 4]
append=[1.5, 2, 3]
size=3
envelope=payload/1001
classGenericAndMethodGeneric=8
```

`javac --release 17 -Xlint:all` không phát warning cho chương trình thử. `LinkedHashMap` trong `groupBy` giúp khóa được in theo thứ tự gặp trong dữ liệu: 2, 4, 3.

### T của Box và R của map là hai phạm vi

`title` luôn là `Box<String>`: `set/get` dùng String. `map(String::length)` lại trả Integer. Lần gọi khác `map(String::toUpperCase)` trả String. R được suy luận độc lập, Box không biến thành `Box<String,Integer>`.

### U của pairWith không đổi T của Box

`pairWith(2026)` trả `Pair<String,Integer>`. String gắn với Box; Integer gắn với method. Record in ra `Pair[first=Generics, second=2026]`.

### E của Repository và K của groupBy

E=String áp dụng cho repository và snapshot. K=Integer chỉ áp dụng cho một lời gọi nhóm theo độ dài String.

### Utility class không có generic state

`Operations.choose`, `mapAll`, `append` dùng những biến T/R khác nhau ở từng method. Không cần `Operations<T,R>` và không phải tạo object để dùng những thuật toán này.

## 9. Constructor generic cũng có scope riêng

`Envelope<T>` của phần 8 có constructor:

```java
<U> Envelope(T payload, U auditData) {
    this.payload = payload;
    this.audit = String.valueOf(auditData);
}
```

T thuộc class và được giữ trong field payload. U chỉ thuộc một lần khởi tạo; constructor nhận auditData kiểu U rồi chuyển sang String. Caller viết:

```java
Envelope<String> e = new Envelope<>("payload", 1001L);
```

T được xác định là String, U tương thích Long. Object vẫn là `Envelope<String>` chứ không phải `Envelope<String,Long>`.

Dấu diamond `<>` tại `new Envelope<>(...)` giúp suy luận **class type arguments**. Constructor type parameter U được suy luận theo chữ ký constructor, độc lập với T.

Generic constructor hữu ích nếu một input tạm thời có quan hệ kiểu cần diễn đạt lúc khởi tạo. Nếu U chỉ xuất hiện một lần và hoàn toàn không cần quan hệ với vị trí khác, một input Object đôi khi cũng có thể đủ; không cần generic chỉ để làm code có nhiều dấu ngoặc nhọn.

## 10. Bounded type parameters ở cả class lẫn method

Bound cấp lớp:

```java
class NumericBox<N extends Number> {
    private final N value;

    NumericBox(N value) {
        this.value = value;
    }

    double asDouble() {
        return value.doubleValue();
    }
}
```

Nếu toàn bộ instance luôn giữ một subtype Number, bound cấp lớp hợp lý.

Bound cấp method:

```java
static <N extends Number> double square(N value) {
    double x = value.doubleValue();
    return x * x;
}
```

Method chỉ xử lý một giá trị Number, không giữ trạng thái N.

Nhiều bounds:

```java
static <T extends Number & Comparable<T>> T max(T a, T b) {
    return a.compareTo(b) >= 0 ? a : b;
}
```

Nếu có class bound, nó đứng trước các interface bounds; ví dụ Number trước Comparable. **Bound và scope là hai quyết định độc lập**: bound quyết định khả năng của type, scope quyết định nơi quan hệ type phải tồn tại.

## 11. Khi không cần đặt tên T, dùng wildcard

Một method chỉ lấy size:

```java
static <T> int size(List<T> values) {
    return values.size();
}
```

có thể được diễn đạt ngắn hơn:

```java
static int size(List<?> values) {
    return values.size();
}
```

Đây là hai lựa chọn thay thế, **không đặt đồng thời cùng tên trong một class** do cùng erasure.

`List<?>` cho biết kiểu phần tử không quan trọng. Khi type variable không liên kết đầu ra, callback hoặc tham số khác, wildcard có thể rõ nghĩa hơn.

Ngược lại, một method copy cần ràng buộc giữa nguồn và đích:

```java
static <T> void append(
        List<? extends T> source,
        List<? super T> destination) {
    destination.addAll(source);
}
```

T liên kết producer với consumer. `List<Integer>` có thể truyền làm source, `List<Number>` làm destination. Hai `List<?>` độc lập không bảo đảm có chung element type, nên không thể thay chữ ký này mà vẫn hỗ trợ `addAll` an toàn.

Một phép đọc thuần túy từ subtype Number có thể nhận:

```java
static double sum(List<? extends Number> values) {
    double total = 0;
    for (Number value : values) {
        total += value.doubleValue();
    }
    return total;
}
```

Không cần `<T extends Number>` nếu T không thêm quan hệ hữu ích. Nhưng “T chỉ xuất hiện một lần thì luôn bỏ T” không phải luật tuyệt đối; inference, overload, compatibility và các body implementation vẫn có thể ảnh hưởng lựa chọn.

## 12. Mẫu thiết kế sai: generic return không có bằng chứng

Một phương thức:

```java
// Anti-pattern: không tự chứng minh an toàn kiểu.
@SuppressWarnings("unchecked")
static <T> T pretend(Object value) {
    return (T) value;
}
```

cho phép caller yêu cầu String từ một Integer. `<T>` không làm phép cast trở nên an toàn; `ClassCastException` có thể xuất hiện khi kết quả được dùng dưới dạng String.

Nếu cần kiểm tra class runtime thực:

```java
static <T> T checked(Class<T> token, Object value) {
    return token.cast(value);
}
```

Class token cung cấp loại runtime để kiểm tra. Tuy nhiên `Class<T>` không tự biểu diễn `List<String>` vì type arguments của List không reifiable; dữ liệu generic lồng nhau cần validation riêng.

Một generic method an toàn cần thể hiện **quan hệ giữa type parameter và dữ liệu/callback có thể kiểm chứng**, không chỉ có ký hiệu T trong return type.

## 13. Overload và type erasure

**Tệp BadErasureOverloads.java, cố ý không biên dịch:**

```java
import java.util.List;

class BadErasureOverloads {
    void store(List<String> list) {}
    void store(List<Integer> list) {}
}
```

Hai chữ ký cùng bị erasure thành `store(List)`, nên `javac` báo `name clash ... have the same erasure`.

Nếu hai method cùng làm một thuật toán, hãy cân nhắc gộp thành một `<T> void store(List<T>)`. Nếu chúng làm hai nghiệp vụ riêng, đặt tên phản ánh nghiệp vụ. Không nên thêm một tham số giả hoặc cast raw chỉ để tránh compiler mà làm API khó hiểu.

## 14. Type inference không có nghĩa cùng runtime class

Một method:

```java
static <T> void combine(T a, T b) {
    System.out.println(a + " / " + b);
}
```

không bắt buộc hai object có cùng `getClass()`. Compiler có thể chọn T là một **common supertype** hợp lệ cho hai đối số khác runtime class, tùy ngữ cảnh.

Vì vậy T cùng tên diễn đạt quan hệ assignability ở compile time, **không phải phép kiểm tra runtime rằng a và b cùng exact class**. Khi nghiệp vụ có yêu cầu nghiêm ngặt hơn, hãy sử dụng loại domain cụ thể, class token hoặc validation độc lập.

## 15. Đổi scope generic có thể phá API public

So sánh:

```java
interface Store<E> {
    void add(E value);
    E get(int index);
}
```

với:

```java
interface Store {
    <E> void add(E value);
    <E> E get(int index);
}
```

Phiên bản đầu có E thống nhất cho cùng một Store typed. Ở bản sau, E của add và E của get được suy luận độc lập và không còn bằng chứng rằng các giá trị liên hệ với nhau. Caller dùng `Store<String>` cũng không thể tiếp tục khai báo như cũ.

Chuyển type parameter từ class xuống method (hoặc ngược lại) có thể ảnh hưởng source compatibility, overload resolution, override, reflection signatures và trong một số thay đổi còn ảnh hưởng binary compatibility. **Không mặc định đó là refactor chỉ sửa cú pháp.**

Một utility class mang quá nhiều type parameters khiến caller phải viết các type arguments không liên quan tới trạng thái có thể được đơn giản hóa bằng method-level generics, nhưng phải kiểm tra caller và hợp đồng public trước khi sửa.

## 16. Bảng quyết định nhanh

| Nhu cầu | Đặt type parameter ở đâu? | Lý do |
| --- | --- | --- |
| Box giữ T, set/get T | `Box<T>` | State và nhiều method cần T thống nhất. |
| Repository chỉ chứa E | `Repository<E>` | E là invariant của instance. |
| Repository nhóm theo khóa K tùy callback | `Repository<E>` + `<K> groupBy` | K chỉ thuộc một lời gọi. |
| Cache key/value typed | `Cache<K,V>` | Hai type thuộc trạng thái dài hạn. |
| Convert T→R một lần | `static <T,R> R convert(...)` | Không có state generic. |
| Mapper được cấu hình, tái sử dụng | `ConfiguredMapper<T,R>` | Hàm mapping được giữ trong object. |
| Tính size List bất kỳ | `int size(List<?>)` | Không cần đặt tên type. |
| Copy source vào destination | `static <T> append(List<? extends T>,List<? super T>)` | T liên kết hai vị trí. |
| Factory tạo Box<X> bất kỳ | `static <X> Box<X> of(X)` | Static có X độc lập. |
| Constructor có audit U dùng một lần | `Envelope<T>`, constructor `<U>` | T thuộc class, U thuộc construction. |

## 17. Checklist review API

1. T đang liên kết những field, tham số và kết quả nào?
2. Có cần cùng T xuyên suốt nhiều lần gọi trên một receiver không?
3. Caller có cần xác định T khi tạo instance không?
4. Kết quả một phép map có thể đổi loại ở mỗi lời gọi không? Nếu có, R nên ở method.
5. Static method có đang cố dùng T cấp lớp không?
6. Một method có khai báo lại `<T>` và che T cấp lớp không?
7. Có thể thay generic variable không cần thiết bằng `?` không?
8. Cần PECS để cho phép đọc subtype hoặc ghi vào supertype không?
9. Generic return có chứng minh được liên hệ giữa kiểu và dữ liệu thực không?
10. Có overload generic nào sau erasure bị trùng chữ ký không?
11. Các type arguments có được suy luận thoải mái mà không cần cast quá mức không?
12. Khi thay scope, có làm đổi public contract, overrides hoặc cách khai báo của caller không?
13. Đã thử các trường hợp String/Integer và kết hợp kiểu khác nhau trong một chương trình biên dịch thật chưa?

## 18. Bài tập và đáp án

**1.** Box có field value, getter và setter. Chọn generic class hay generic method?

**Đáp án:** `Box<T>` vì T là invariant của trạng thái và tất cả thao tác.

**2.** Một method đảo `Pair<A,B>` nhưng không giữ state?

**Đáp án:** Có thể là `static <A,B> Pair<B,A> reverse(Pair<A,B>)`, vì A/B liên hệ trong một lần gọi.

**3.** Vì sao K ở `Repository<E>.<K>groupBy` không phải cấp lớp?

**Đáp án:** Một repository chứa E cố định nhưng có thể nhóm bằng các loại khóa khác nhau ở những calls độc lập.

**4.** `class Box<T> { T x; <T> void set(T v) { x=v; } }` lỗi tại đâu?

**Đáp án:** Method khai báo lại T, che T class. Xóa `<T>` ở method.

**5.** Static factory có thể dùng T của class không?

**Đáp án:** Không trực tiếp; khai báo `static <X> Box<X> of(X)` với X riêng.

**6.** Khi nào dùng `List<?>` thay `<T> List<T>`?

**Đáp án:** Khi loại phần tử không cần liên kết với output/các tham số khác, chẳng hạn chỉ đọc size.

**7.** Vì sao copy List Integer vào List Number cần một T?

**Đáp án:** T liên hệ kiểu phần tử producer với kiểu destination có thể nhận, thường thông qua `? extends T` và `? super T`.

**8.** Generic constructor `<U> Envelope(T,U)` có khiến object mang hai type arguments?

**Đáp án:** Không; object vẫn có class type `Envelope<T>`. U chỉ trong constructor.

**9.** Cùng một T trong `combine(T,T)` có buộc runtime class giống nhau không?

**Đáp án:** Không; compiler có thể chọn một common supertype hợp lệ.

**10.** Khi nào `ConfiguredMapper<T,R>` tốt hơn method `<T,R> convert(...)`?

**Đáp án:** Khi object thật sự giữ mapper T→R để tái sử dụng qua nhiều lần gọi.

**11.** Có nên dùng `<T> T pretend(Object)` để cast dữ liệu không biết kiểu?

**Đáp án:** Không; không có quan hệ an toàn. Dùng Class token hoặc validator tương ứng với contract runtime.

**12.** Có thể chuyển `Store<E>` thành interface không generic với method `<E>` mà không đổi hợp đồng không?

**Đáp án:** Không. Quan hệ kiểu giữa các lần add/get bị mất, và kiểu khai báo caller có thể không còn hợp lệ.

## 19. Tổng kết

- **Cấp lớp/interface:** chọn khi T là một phần hợp đồng của object, gắn với nhiều method hoặc trạng thái lâu dài.
- **Cấp method:** chọn khi T/R/K chỉ diễn đạt quan hệ của một lần gọi; có thể suy luận lại ở những lần gọi sau.
- **Kết hợp cả hai:** `Box<T>` với `<R> map`, `Repository<E>` với `<K> groupBy`.
- **Constructor generic:** có thể nhận type U tạm thời nhưng không làm class có thêm type parameter.
- **Static context:** không dùng trực tiếp class T; static generic factory tự khai báo biến kiểu riêng.
- **Wildcard:** dùng khi không cần đặt tên type parameter chỉ để đọc hoặc quan sát dữ liệu.
- **Tránh các bẫy:** shadowing, unchecked cast, method generic không có invariant, overload trùng erasure và refactor phá public contract.

**Quy tắc cuối:** Đặt type parameter ở phạm vi nhỏ nhất nhưng vẫn bảo toàn đầy đủ invariant và quan hệ kiểu mà API đã hứa với caller.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="generic-api-design-002">Đặt tên và viết tài liệu cho quan hệ giữa các kiểu</a>

<details>
<summary>Xem chi tiết</summary>

# Đặt tên và viết tài liệu cho quan hệ giữa các kiểu trong Java Generics

Khi thiết kế API Java dùng Generics, một chữ ký **đúng về mặt cú pháp** chưa chắc đã **dễ hiểu về mặt hợp đồng**. Những tên `T`, `E`, `K`, `V`, `R`; giới hạn `extends`; wildcard `? extends`/`? super`; và thông tin viết trong Javadoc cùng giúp trả lời:

- Kiểu nào là **đầu vào**, kiểu nào là **kết quả**?
- Hai tham số có **phải cùng một kiểu** hay chỉ có quan hệ subtype/supertype?
- Một tham số là **nguồn sản xuất dữ liệu** hay **đích tiêu thụ dữ liệu**?
- Giới hạn kiểu nằm ở **lớp**, **phương thức**, hay một wildcard dùng ở **vị trí tham số**?
- Điều gì được compiler bảo vệ và điều gì thuộc **hợp đồng runtime** như `null`, mutation, ownership và exception?

Ví dụ cùng một mục tiêu “chuyển từng phần tử từ nguồn sang dạng kết quả mới”:

```java
// Chữ ký đúng nhưng khó hiểu nếu thiếu tên vai trò:
static <T, U> java.util.List<U> transform(
        Iterable<? extends T> x,
        java.util.function.Function<? super T, ? extends U> y);

// Có thể đặt tên theo vai trò để dễ đọc:
static <S, R> java.util.List<R> map(
        Iterable<? extends S> source,
        java.util.function.Function<? super S, ? extends R> mapper);
```

Cả hai đều diễn đạt cùng **quan hệ kiểu** trong ví dụ: đầu vào kiểu `S`, kết quả kiểu `R`; nguồn cung cấp `S`; mapper nhận một supertype thích hợp của `S` và trả subtype thích hợp của `R`. Tuy nhiên, phiên bản sau cho biết mục đích qua tên method, biến `source`/`mapper` và ký hiệu `S`/`R` trước cả khi đọc Javadoc.

**Nguyên tắc chính:** **tên mô tả vai trò, chữ ký mô tả ràng buộc compiler, Javadoc giải thích ngữ nghĩa và hành vi không thể suy ra chỉ từ kiểu**. Không dùng lời chú thích để bù cho một chữ ký truyền tải sai quan hệ kiểu.

## 1. Quy ước tên type parameters trong Java

Java thường dùng **tên chữ in hoa ngắn**, nhất là khi vai trò đã trở thành quy ước chung:

| Tên | Vai trò thông dụng | Ngữ cảnh |
| --- | --- | --- |
| `T` | Type tổng quát | `Box<T>`, `List<T>`, phương thức generic đơn giản |
| `E` | Element | `Collection<E>`, `List<E>`, `Set<E>` |
| `K` | Key | `Map<K, V>` |
| `V` | Value | `Map<K, V>`, `Optional<T>` thì `T` |
| `R` | Result/Return | `Function<T, R>`, API map/convert |
| `U`, `S` | Kiểu thứ hai/nguồn | Phương thức liên hệ hai kiểu độc lập |
| `T1`, `T2` | Các kiểu cùng vai trò nhưng phân biệt vị trí | Một vài abstraction nhiều đối số |

Đây là **quy ước**, không phải các tên có ý nghĩa đặc biệt trong compiler. `class Box<T>` và `class Box<V>` đều có thể hợp lệ nếu mọi tham chiếu tương ứng được đổi nhất quán.

Với interface/map:

```java
interface Lookup<K, V> {
    V get(K key);
}
```

Tên `K` và `V` giúp đọc nhanh rằng `get` nhận **key** và trả về **value**. Một API dùng `<T,U>` cho cùng vai trò có thể biên dịch, nhưng làm người đọc phải đoán nhiều hơn.

### Khi nào dùng tên dài?

Nếu một API có nhiều type parameters với quan hệ phức tạp, có thể chọn tên rõ nghĩa như `<SOURCE, RESULT>`, `<INPUT, OUTPUT>` hoặc `<KEY, VALUE>` khi quy ước codebase cho phép. Java cho phép các định danh đó, dù trong thư viện Java chuẩn tên ngắn `T`, `U`, `R`, `K`, `V` phổ biến hơn.

Tên dài **không tự giải quyết được thiết kế sai**. Ví dụ `<INPUT, OUTPUT>` không mô tả an toàn kiểu nếu method vẫn thực hiện unchecked casts không có bằng chứng. Hãy giữ các type parameter names ngắn và nhất quán khi vai trò đã rõ; dùng tên giàu ngữ nghĩa khi sự tương phản thật sự giúp đọc một chữ ký khó.

### Tên của biến và method cũng quan trọng

So sánh:

```java
static <T> void f(java.util.List<? extends T> a,
                  java.util.List<? super T> b);

// Dễ hiểu hơn:
static <T> void copy(
        java.util.List<? extends T> source,
        java.util.List<? super T> destination);
```

Chữ ký đầu có quan hệ kiểu phù hợp nhưng không nêu mục đích; chữ ký sau làm nổi bật **hướng dữ liệu**. Trong code triển khai, tên `source`, `destination`, `mapper`, `predicate`, `comparator`, `factory`, `fallback` và `result` thường quan trọng không kém tên `T`.

## 2. Đọc quan hệ kiểu trước khi chọn tên

Một thiết kế generic thường xoay quanh bốn dạng quan hệ:

1. **Cùng kiểu:** nhiều vị trí sử dụng cùng type variable `T`, ví dụ `T choose(T left, T right)`.
2. **Hai kiểu độc lập nhưng có phép chuyển:** `<S,R> R convert(S source, Function<? super S,? extends R> mapper)`.
3. **Ràng buộc subtype:** `<T extends Number>` hoặc `<T extends Comparable<? super T>>`.
4. **Quan hệ biến thiên ở vị trí dùng:** `Collection<? extends T>` làm nguồn, `Collection<? super T>` làm đích.

Ví dụ đơn giản:

```java
static <T> T first(java.util.List<T> items) {
    return items.get(0);
}
```

Tên `T` xuất hiện ở `List<T>` và kiểu trả về `T`. Đây là một **mối liên hệ do compiler kiểm tra**: `List<String>` cho `String` khi gọi, `List<Integer>` cho `Integer` (theo quy tắc suy luận và kiểu tĩnh của lời gọi).

Nếu đổi kiểu trả về thành `Object`, method vẫn có thể chạy nhưng sẽ **mất liên hệ kiểu** ở chữ ký:

```java
static Object firstUnclear(java.util.List<?> items) {
    return items.get(0);
}
```

Đó có thể là API hợp lý khi ý định chỉ đọc một giá trị không biết kiểu, nhưng không thay thế được hợp đồng `<T> T first(List<T>)` nếu caller cần nhận kết quả mà không cast.

**Câu hỏi thiết kế:** có một quan hệ kiểu thực sự nào đáng được biểu diễn bằng **cùng type variable** không? Nếu có, đừng làm yếu chữ ký thành `Object` rồi viết Javadoc “caller tự cast kết quả”.

## 3. Scope của type parameter: thuộc class hay method?

Type parameter có một **nơi khai báo**. `T` ở cấp lớp và `T` ở một method riêng không tự động là cùng biến kiểu.

```java
class Box<T> {
    private final T value;

    Box(T value) {
        this.value = value;
    }

    T get() {
        return value; // T của class Box
    }

    static <U> Box<U> of(U value) {
        return new Box<>(value); // U của static method of
    }
}
```

`Box<String>` giữ một `String` ở góc nhìn compiler. `Box<Integer>` giữ một `Integer`. Method `of` là **static generic method** tự khai báo `U`; nó không tham chiếu trực tiếp `T` của class trong static context.

Một lỗi API dễ gây nhầm:

```java
class Store<T> {
    // T của method bên dưới là T MỚI, không phải T của Store:
    <T> void accept(T value) { }
}
```

Đoạn này có thể biên dịch nhưng method-level `T` **che khuất tên** `T` của class. Nếu mục tiêu là chỉ nhận `T` mà store đang mang, hãy viết:

```java
class Store<T> {
    void accept(T value) { }
}
```

Nếu mục tiêu là một kiểu thứ hai độc lập, viết `<U>` và tài liệu hóa quan hệ giữa `U` và `T`.

**Không nên tái sử dụng cùng tên type parameter ở các scope lồng nhau chỉ vì compiler cho phép.** Người đọc khó phân biệt hợp đồng kiểu lớp và hợp đồng kiểu method, nhất là trong API nhiều overload.

## 4. Đừng khai báo type parameter khi không tạo ra mối liên hệ hữu ích

Xét một utility chỉ in ra collection:

```java
static <T> void printAll(java.util.Collection<T> items) {
    for (T item : items) {
        System.out.println(item);
    }
}
```

Code này đúng, nhưng nếu `T` chỉ xuất hiện đúng một lần trong khai báo và method không cần mối liên hệ đặc biệt, một wildcard có thể đơn giản hơn:

```java
static void printAll(java.util.Collection<?> items) {
    for (Object item : items) {
        System.out.println(item);
    }
}
```

Dấu `?` diễn đạt rằng method **không cần biết hoặc đặt tên** element type. Điều này phù hợp với những API chỉ quan sát, đếm, log hoặc lấy kích thước.

Tuy nhiên, **không phải cứ xuất hiện một lần là phải dùng wildcard**. Một generic method vẫn cần `T` khi liên hệ kiểu với return type, callback, bound, một khai báo ngoại lệ liên quan, hoặc khi API cần type inference theo ngữ cảnh. Quy tắc thực tế:

- **Cần đặt tên cho kiểu để ràng buộc nhiều vị trí?** Dùng type parameter `<T>`.
- **Chỉ cần chấp nhận một container của kiểu chưa biết?** Dùng `?` nếu không làm mất ý nghĩa.
- **Chỉ có một kiểu cố định của nghiệp vụ?** Đặt kiểu cụ thể, chẳng hạn `Collection<String>`, đừng tổng quát hóa không cần thiết.

Thiết kế tốt giảm số lượng type variables mà người dùng phải suy luận.

## 5. Quan hệ nguồn–đích: ghi ngay trong chữ ký bằng PECS

Trong Generics, **PECS** là nguyên tắc quen thuộc: *Producer Extends, Consumer Super*.

- Nguồn **sản xuất** các giá trị đọc như `T`: `Iterable<? extends T>`.
- Đích **tiêu thụ** các giá trị `T`: `Collection<? super T>`.

Một API sao chép:

```java
static <T> int copyInto(
        Iterable<? extends T> source,
        java.util.Collection<? super T> destination) {
    int accepted = 0;
    for (T item : source) {
        if (destination.add(item)) {
            accepted++;
        }
    }
    return accepted;
}
```

Với `List<Integer>` làm nguồn và `List<Number>` làm đích:

```java
java.util.List<Integer> integers = java.util.List.of(2, 4, 6);
java.util.List<Number> numbers = new java.util.ArrayList<>();

int accepted = copyInto(integers, numbers); // 3
```

Nếu viết `copyInto(List<T>, List<T>)`, API sẽ gò bó hơn cần thiết vì `List<Integer>` **không phải subtype** của `List<Number>`: generics Java **bất biến**.

### Điều PECS cho phép, và điều nó không hứa

- `? extends T` cho phép đọc những phần tử như `T`; không có nghĩa có thể thêm một `T` bất kỳ vào nguồn qua tham chiếu wildcard.
- `? super T` cho phép thêm `T` vào đích; đọc từ đích nói chung chỉ có thể dùng an toàn như `Object` nếu không biết kiểu chính xác của destination.
- PECS là quy tắc **tương thích kiểu**; nó không chứng minh rằng destination mutable, không cấm null, hoặc rằng `add` chắc chắn trả `true`. Các điểm này cần được tài liệu hóa theo hợp đồng collection cụ thể.

Với một `Set` đích có sẵn phần tử trùng, `add` có thể trả `false`. Do đó ví dụ `copyInto` trả **số lần `destination.add(item)` báo `true`**, không tuyên bố số phần tử nguồn đã duyệt hoặc số phần tử cuối cùng của mọi loại collection.

Nếu API muốn trả số phần tử *đã xử lý*, hãy đặt tên `processedCount` và tăng biến đếm theo mỗi lần duyệt. **Tên kết quả, Javadoc và logic phải khớp nhau.**

## 6. Quan hệ biến đổi: đặt tên `S` và `R` rõ đầu vào/đầu ra

Một phép `map` thường liên hệ **source type** `S` với **result type** `R`:

```java
static <S, R> java.util.List<R> map(
        Iterable<? extends S> source,
        java.util.function.Function<? super S, ? extends R> mapper) {
    java.util.List<R> result = new java.util.ArrayList<>();
    for (S item : source) {
        result.add(mapper.apply(item));
    }
    return result;
}
```

Phân tích chữ ký:

- `<S, R>`: hai type parameters độc lập, không yêu cầu `S` là subtype của `R`.
- `Iterable<? extends S>`: nguồn có thể cung cấp các subtype của `S`.
- `Function<? super S, ? extends R>`: callback chấp nhận kiểu phù hợp để xử lý `S` và có kết quả phù hợp để xem như `R`.
- `List<R>`: danh sách kết quả có element type `R`.

Lời gọi:

```java
java.util.List<Integer> ids = java.util.List.of(2, 4);
java.util.List<String> labels = map(ids, id -> "id-" + id);
```

Compiler suy luận mối liên hệ giữa `Integer` và `String` từ biểu thức lambda, target type và chữ ký `map`. Không cần ép kiểu `(List<String>)` hoặc đưa `Object` vào public API.

### Tại sao không chỉ dùng `Function<S,R>`?

`Function<S,R>` cũng hợp lệ nhưng thường **hẹp hơn** `Function<? super S, ? extends R>` trong những API cần nhận callback có kiểu đầu vào rộng hơn hoặc đầu ra hẹp hơn. Tuy nhiên, không nên rải wildcard theo thói quen: wildcard làm chữ ký dài hơn và đôi khi khiến suy luận kiểu/diagnostics phức tạp. Hãy dùng wildcard tại những vị trí API thực sự cần linh hoạt kiểu.

Với một interface đã được thiết kế có type parameters phù hợp, có thể chọn cách ngắn hơn để ưu tiên khả năng sử dụng. **Sự tổng quát phải phục vụ trường hợp sử dụng**, không phải mục tiêu tự thân.

## 7. Upper bounds: tên và Javadoc phải làm rõ ràng buộc

Một generic method cần so sánh các phần tử có thể dùng bound:

```java
static <T extends Comparable<? super T>> java.util.Optional<T>
greatest(Iterable<? extends T> source) {
    T best = null;
    boolean found = false;
    for (T item : source) {
        java.util.Objects.requireNonNull(item, "element");
        if (!found || item.compareTo(best) > 0) {
            best = item;
            found = true;
        }
    }
    return found ? java.util.Optional.of(best)
                 : java.util.Optional.empty();
}
```

`T extends Comparable<? super T>` nói rằng `T` có khả năng so sánh với chính `T` hoặc với một supertype thích hợp. Dạng này hữu ích hơn `Comparable<T>` trong một số mô hình kế thừa, vì một subtype có thể thừa hưởng khả năng so sánh được khai báo ở supertype.

**Đọc chính xác:** `extends` trong type parameter declaration `<T extends ...>` diễn đạt **upper bound**, kể cả bound là interface; `? super T` là **lower-bounded wildcard tại vị trí dùng**, không phải cú pháp khai báo type variable.

Javadoc của `greatest` cần giải thích cả các quyết định ngoài chữ ký:

- Với nguồn rỗng, trả `Optional.empty()` thay vì `null`.
- Với phần tử `null`, ném `NullPointerException`, vì ví dụ cấm null.
- Dựa vào **natural ordering** của từng giá trị `T`.
- Lấy phần tử lớn nhất trong dữ liệu hiện có; không sắp xếp hoặc sửa collection nguồn.
- Chưa cung cấp bảo đảm thread-safe/snapshot cho một nguồn mutable bị sửa đồng thời.

Bound chỉ chứng minh compiler có thể gọi `compareTo` phù hợp. Nó **không tự quyết định** hành vi với empty input hoặc null.

## 8. JavaDoc chuẩn: `@param <T>` khác `@param value`

Javadoc có cú pháp riêng để tài liệu hóa **type parameter**:

```java
/**
 * Creates a box containing one value.
 *
 * @param <T> type of the boxed value
 * @param value value to store; must be non-null
 * @return a box containing {@code value}
 * @throws NullPointerException if {@code value} is null
 */
static <T> Box<T> of(T value) { /* ... */ }
```

Phân biệt:

- `@param <T>`: mô tả **type parameter T** của method/class.
- `@param value`: mô tả **tham số giá trị `value`** của method.
- `@return`: mô tả kết quả và các điều kiện quan trọng của kết quả.
- `@throws` hoặc `@exception`: mô tả lỗi được API cố ý phát ra theo hợp đồng.

Các dấu `<T>` bên trong tag `@param` là **cú pháp Javadoc đặc biệt** cho type parameters. Chúng không tương đương với `@param T` (mô tả một formal parameter tên `T`, nếu có).

Javadoc hỗ trợ dùng `{@code ...}` cho đoạn mã, `{@link ...}` để liên kết API liên quan; sử dụng chúng để làm rõ thuật ngữ nhưng không cần lạm dụng markup.

### Javadoc cho class-level type parameter

```java
/**
 * Stores exactly one value.
 *
 * @param <T> type of the stored value
 */
public final class Box<T> {
    private final T value;

    private Box(T value) {
        this.value = java.util.Objects.requireNonNull(value);
    }

    public T get() {
        return value;
    }
}
```

Nếu class có `<K,V>`, tài liệu hóa **cả hai**:

```java
/**
 * Associates keys with values.
 *
 * @param <K> type of keys used to locate values
 * @param <V> type of values stored in the association
 */
interface Registry<K, V> { /* ... */ }
```

Không chỉ ghi `@param <K> key` khi hợp đồng cần nêu thêm key equality/hash hoặc có cho phép null key không. Quy tắc Java generic `K`/**`V`** không tự mang những semantics đó; tác giả API phải mô tả ở tài liệu tương ứng.

## 9. Javadoc cho quan hệ `S` → `R`

Với `map`, Javadoc nên giải thích các mối liên hệ thay vì chỉ lặp nguyên văn signature:

```java
/**
 * Applies a mapper to each source element, in iteration order.
 *
 * @param <S> source element type
 * @param <R> resulting element type
 * @param source elements to transform; may contain null elements
 * @param mapper transformation applied once to each visited element
 * @return a new mutable list of mapped values, preserving source order
 * @throws NullPointerException if source or mapper is null
 */
static <S, R> java.util.List<R> map(
        Iterable<? extends S> source,
        java.util.function.Function<? super S, ? extends R> mapper) {
    java.util.Objects.requireNonNull(source, "source");
    java.util.Objects.requireNonNull(mapper, "mapper");
    java.util.List<R> result = new java.util.ArrayList<>();
    for (S value : source) {
        result.add(mapper.apply(value));
    }
    return result;
}
```

Ở ví dụ này, `map` không cấm `null` trong nguồn và không từ chối mapper trả `null`, vì `ArrayList` cho phép null. Do đó tài liệu đúng có thể nói rõ **result may contain null elements** nếu điều này quan trọng với API công khai.

Nếu muốn trả list không sửa được, có thể chọn `List.copyOf(result)` khi hợp đồng cấm null, nhưng cần cập nhật **cả code và Javadoc**, bởi `List.copyOf` từ chối `null` và có semantics snapshot cấu trúc. Đừng mô tả “immutable result” trong Javadoc rồi thực tế trả một `ArrayList` mutable.

### Javadoc không thay thế chú thích wildcard từng ký tự

Không cần giải thích mọi dấu `? extends` và `? super` theo cách máy móc ở mọi method nếu tài liệu cấp module đã có. Thay vào đó, mô tả **vai trò**: nguồn đọc được, callback xử lý, kết quả được tạo mới, đích nhận dữ liệu. Nhưng với API khó hiểu, một `@apiNote` hoặc ví dụ lời gọi `List<Integer>` → `Collection<Number>` có thể giúp người sử dụng hiểu lý do wildcard tồn tại.

## 10. Ví dụ hoàn chỉnh Java 17+: các quan hệ kiểu và tên rõ ràng

Lưu vào **`GenericApiNamingDemo.java`**:

```java
import java.util.*;
import java.util.function.*;

public class GenericApiNamingDemo {
    /** A pair whose first and second components may differ in type. */
    record Pair<A, B>(A first, B second) {}

    /** A container carrying one value of type T. */
    static final class Box<T> {
        private final T value;

        private Box(T value) {
            this.value = Objects.requireNonNull(value, "value");
        }

        /**
         * Builds a box from an input value.
         *
         * @param <U> type of the input and stored value
         * @param value a non-null value
         * @return a box containing the value
         * @throws NullPointerException if value is null
         */
        static <U> Box<U> of(U value) {
            return new Box<>(value);
        }

        T get() {
            return value;
        }
    }

    /**
     * Sends each element from a source into a destination.
     *
     * @param <T> type accepted from the source
     * @param source elements to read, in iteration order
     * @param destination collection receiving source elements
     * @return number of destination add calls that returned true
     * @throws NullPointerException if source or destination is null
     * @throws UnsupportedOperationException if destination rejects adds
     */
    static <T> int copyInto(
            Iterable<? extends T> source,
            Collection<? super T> destination) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(destination, "destination");
        int accepted = 0;
        for (T item : source) {
            if (destination.add(item)) {
                accepted++;
            }
        }
        return accepted;
    }

    /**
     * Transforms source elements into a new mutable list.
     *
     * @param <S> source element type
     * @param <R> resulting element type
     * @param source values to transform, in iteration order
     * @param mapper function mapping each S into a compatible R
     * @return new mutable list of mapped values in source order
     * @throws NullPointerException if source or mapper is null
     */
    static <S, R> List<R> map(
            Iterable<? extends S> source,
            Function<? super S, ? extends R> mapper) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(mapper, "mapper");
        List<R> result = new ArrayList<>();
        for (S value : source) {
            result.add(mapper.apply(value));
        }
        return result;
    }

    /**
     * Finds the greatest value under its natural ordering.
     *
     * @param <T> comparable element type
     * @param source non-null iterable containing no null elements
     * @return greatest element, or Optional.empty() if source is empty
     * @throws NullPointerException if source or any element is null
     */
    static <T extends Comparable<? super T>> Optional<T> greatest(
            Iterable<? extends T> source) {
        Objects.requireNonNull(source, "source");
        T best = null;
        boolean seen = false;
        for (T value : source) {
            Objects.requireNonNull(value, "element");
            if (!seen || value.compareTo(best) > 0) {
                best = value;
                seen = true;
            }
        }
        return seen ? Optional.of(best) : Optional.empty();
    }

    public static void main(String[] args) {
        List<Integer> ids = List.of(2, 4, 6);
        List<Number> numbers = new ArrayList<>();

        System.out.println("Copied: " + copyInto(ids, numbers));
        System.out.println("Numbers: " + numbers);

        List<String> labels = map(ids, x -> "id-" + x);
        System.out.println("Mapped: " + labels);

        System.out.println("Greatest: "
                + greatest(ids).orElseThrow());
        System.out.println("Empty maximum: "
                + greatest(List.<Integer>of()));

        Pair<String, Integer> pair = new Pair<>("age", 20);
        System.out.println("Pair: " + pair.first()
                + "=" + pair.second());

        Box<String> box = Box.of("Java");
        System.out.println("Box: " + box.get());
    }
}
```

Biên dịch và chạy với Java 17 trở lên:

```bash
javac -encoding UTF-8 -Xlint:all GenericApiNamingDemo.java
java GenericApiNamingDemo
```

Standard output:

```text
Copied: 3
Numbers: [2, 4, 6]
Mapped: [id-2, id-4, id-6]
Greatest: 6
Empty maximum: Optional.empty
Pair: age=20
Box: Java
```

Diễn giải về **quan hệ giữa các kiểu**, không chỉ kết quả tính toán:

1. `copyInto` nhận nguồn `List<Integer>` và đích `Collection<Number>`, vì `Integer` phù hợp `Number`. Compiler áp dụng PECS, không cần raw types.
2. `map` nhận các `Integer`, callback tạo `String`, và trả về `List<String>`. Hai type parameters `S` và `R` có vai trò khác nhau.
3. `greatest` dùng `Comparable<? super T>`, trả `Optional<T>`, nên loại dữ liệu đầu ra vẫn liên hệ chặt với đầu vào. Nguồn rỗng nhận `Optional.empty()`.
4. `Pair<A,B>` chỉ ra hai thành phần **có thể có kiểu khác nhau**, không ép chúng vào cùng một `T`.
5. `Box<T>` giữ quan hệ kiểu instance; static factory `Box.of` tự khai báo `U`, tạo box có type argument được suy luận.

Với `copyInto`, hợp đồng không hứa thao tác là **atomic**: nếu collection đích ném exception trong lúc duyệt, các phần tử trước đó có thể đã được thêm. Với nguồn/đích alias nhau hoặc bị sửa đồng thời, cần chính sách sử dụng riêng; ví dụ không hứa sao chép an toàn trên mọi loại collection trong tình huống ấy.

## 11. Phân biệt `T` giống nhau với hai kiểu có quan hệ khác nhau

Xét hai API:

```java
static <T> T choose(T first, T second) {
    return first;
}

static <A, B> Pair<A, B> pair(A first, B second) {
    return new Pair<>(first, second);
}
```

Ở `choose`, cùng type variable `T` cho phép compiler tìm một kiểu `T` phù hợp với cả hai đối số qua type inference. **Điều này không nhất thiết buộc hai object có cùng runtime class.** Ví dụ, khi truyền `String` và `Integer`, compiler có thể suy luận một common supertype phù hợp trong một ngữ cảnh nhất định. Nếu API thật sự yêu cầu “hai phần tử phải đúng cùng runtime class”, một chữ ký `<T> T choose(T,T)` **chưa tự bảo đảm** điều đó.

Ở `pair`, `A` và `B` được giữ độc lập, rõ rằng first và second có thể khác kiểu.

Cẩn trọng với tên `T1`, `T2`: chúng phân biệt các type variables, **không bắt buộc hai kiểu khác nhau**. Caller vẫn có thể sử dụng cùng `String` cho cả `T1` và `T2`. Nên tài liệu hóa vai trò “first component” và “second component”, không viết sai “must be distinct Java types” nếu chữ ký không có khả năng ép điều kiện ấy.

### Khi nào dùng tên `A`, `B`?

Với cấu trúc cặp độc lập `Pair<A,B>`, hai ký hiệu đơn giản, trung tính thường phù hợp. Với cặp khóa/giá trị, `Entry<K,V>` thường rõ nghĩa hơn. Chọn theo **semantics**, không chỉ theo số lượng type parameters.

## 12. Phân biệt upper bound, wildcard và hợp đồng nghiệp vụ

Ba chữ ký có vai trò khác nhau:

```java
static <T extends Number> void acceptNumber(T value) { }

static void readNumbers(List<? extends Number> numbers) { }

static <T> void append(T value, Collection<? super T> target) { }
```

- `<T extends Number>`: khai báo **named type variable** với upper bound; có thể tái sử dụng `T` ở nhiều vị trí trong chữ ký.
- `List<? extends Number>`: một **use-site wildcard**; tham số có thể là list của subtype Number nhưng phần tử chỉ đọc an toàn như `Number`.
- `Collection<? super T>`: wildcard cho consumer; có thể thêm `T`.

Không viết Javadoc dạng “`? extends Number` means every item must have runtime class Number”. Các subtype như `Integer`, `Double` phù hợp ở compile time, còn runtime data vẫn cần đúng invariant generic.

Một upper bound cũng không hàm ý API luôn “nhận mọi subclass” nếu method có thêm điều kiện runtime. Tương tự, wildcard không quyết định null policy: `List<? extends Number>` có thể chứa null nếu backing collection cho phép; type system không mặc định cấm null.

## 13. Quan hệ với callback: input `super`, output `extends`

Một kiểu callback điển hình:

```java
Function<? super T, ? extends R>
```

Nó kết hợp hai vai trò:

- **Tham số đầu vào** của function tiêu thụ một giá trị kiểu `T`: dùng `? super T` để chấp nhận function khai báo nhận một supertype phù hợp.
- **Kết quả** của function cung cấp một giá trị kiểu `R`: dùng `? extends R` để chấp nhận function trả subtype phù hợp.

Ví dụ:

```java
java.util.function.Function<Number, String> describe =
        number -> "n=" + number;

java.util.List<Integer> input = java.util.List.of(1, 2);
java.util.List<CharSequence> output = map(input, describe);
```

Trong một ngữ cảnh target typing phù hợp, `S` có thể là `Integer`, còn `R` là `CharSequence`: `Function<Number,String>` chấp nhận `Integer` như một `Number` và trả `String` là subtype của `CharSequence`. Điều này diễn tả tính linh hoạt mà `Function<S,R>` đúng chính xác có thể không cho phép.

**Nhưng hãy kiểm tra tính dễ dùng:** generics của Java có inference phức tạp khi lồng nhiều wildcard. Nếu người sử dụng thường phải thêm cast/type witness để gọi một API thường nhật, nên cân nhắc overload hoặc thay đổi chữ ký. API rộng nhất về lý thuyết không nhất thiết là API dễ sử dụng nhất.

## 14. Bảo đảm compile time và hợp đồng runtime: phải viết tách riêng

Một chữ ký như:

```java
static <T> void addAll(
        Collection<? extends T> source,
        Collection<? super T> destination)
```

cho compiler biết **quan hệ kiểu giữa các phần tử nguồn và đích**. Nhưng nó không nói:

- Destination có **mutable** và hỗ trợ `add` không?
- `null` có được chấp nhận không?
- Input có thể rỗng không?
- Khi gặp lỗi giữa chừng, destination có thể bị sửa một phần không?
- Thứ tự iteration của nguồn có được giữ lại ở đích không? `Set` có thể sắp xếp/loại bỏ trùng tùy implementation.
- Source và destination có được dùng chung đối tượng/alias không?
- Operation có thread-safe không? Có sao chép snapshot không?

Những điều này thuộc **semantic contract** và nên viết trong Javadoc của API có phạm vi công khai. Ví dụ:

> *Adds each source element to destination in iteration order. This operation may modify destination before throwing. The destination must support additions; null elements must be accepted by its contract.*

Trong README, câu này là **mẫu nội dung giải thích**, không phải lời khẳng định mọi `Collection` chấp nhận null hoặc thứ tự được lưu giữ. Ghi rõ điều kiện và hành vi cụ thể của method đang thiết kế.

### Nullness và generic type arguments

`List<String>` **không có nghĩa non-null list element** trong hệ thống kiểu Java tiêu chuẩn. Nếu API cấm null, ghi rõ trong tài liệu và kiểm tra ở boundary (`Objects.requireNonNull`) hoặc sử dụng một hệ nullness annotation đã được codebase quy định. Đừng dựa vào chữ `String` để suy ra điều compiler không bảo đảm.

### Ownership và mutability

`List<R>` trong chữ ký `map` không nói kết quả mutable hay unmodifiable. Nếu hàm trả `new ArrayList<>()`, Javadoc có thể nêu “new mutable list”; nếu trả `List.copyOf`, cần nêu chính sách null, tính không sửa được của container và bản chất shallow copy. Cập nhật Javadoc cùng lúc khi thay đổi implementation ảnh hưởng public contract.

## 15. Thể hiện lỗi và lựa chọn khi không có dữ liệu

Một method `max` có thể chọn giữa:

```java
static <T extends Comparable<? super T>> T maxNonEmpty(
        Collection<? extends T> elements) { /* ... */ }

static <T extends Comparable<? super T>> Optional<T> maxOptional(
        Collection<? extends T> elements) { /* ... */ }
```

Hai chữ ký cùng mô tả bound kiểu `T`, nhưng khác nhau về **hợp đồng dữ liệu rỗng**.

- `maxNonEmpty` có thể yêu cầu input không rỗng và ném `NoSuchElementException` hoặc `IllegalArgumentException` theo thiết kế.
- `maxOptional` có thể trả `Optional.empty()` nếu input rỗng.

Một README hoặc Javadoc tốt phải nêu chính xác lựa chọn của implementation, không chỉ khẳng định “trả về T lớn nhất”.

Cũng đừng dùng `Optional<T>` để tránh nói rõ null policy: `Optional` không được thiết kế như wrapper chứa `null`; cần quyết định hành vi nếu phần tử nguồn có null.

## 16. Overload, type inference và tên dễ hiểu

Một API “quá generic” có thể làm người gọi khó đọc lỗi compiler:

```java
static <A, B, C, D> D route(A a, B b,
        java.util.function.BiFunction<? super A, ? super B, ? extends C> f,
        java.util.function.Function<? super C, ? extends D> g) {
    return g.apply(f.apply(a, b));
}
```

Dù có thể hợp lệ, người sử dụng phải giải bốn type parameters và nhiều wildcard. Chỉ tạo mức tổng quát đó nếu có use case đáng giá. Nếu luồng nghiệp vụ luôn “parse → validate → save”, các tên class/domain và operation rõ nghĩa thường tốt hơn một hàm `route` bốn kiểu trừu tượng.

Khi method generic trả giá trị theo target type, đôi khi nên dùng biến kết quả có kiểu rõ ràng:

```java
List<String> names = map(List.of(1, 2), x -> "item-" + x);
```

Nếu type inference vẫn gặp khó ở một lời gọi phức tạp, có thể chọn lambda với tham số kiểu rõ hoặc cung cấp type witness phù hợp như `SomeClass.<Integer,String>map(...)`. Nhưng **đừng dùng unchecked cast như cách chữa mặc định**; đó có thể chỉ đẩy lỗi vào runtime.

### Tránh overload chỉ khác tên type parameter

```java
// KHÔNG THỂ cùng khai báo trong một class nếu erasure chữ ký trùng:
static <T> void process(List<T> values) {}
static <U> void process(List<U> values) {}
```

Đổi tên `T` thành `U` không tạo ra overload khác; cả hai có cùng erasure signature. Một public API phải phân biệt overload bằng **thông tin tham số thực sự khác**, không bằng chữ cái của type parameters.

## 17. Viết ví dụ Javadoc để chứng minh tính hữu dụng của wildcard

Với public API truyền dữ liệu từ nguồn sang đích, một ví dụ ngắn có giá trị hơn việc giải thích PECS trừu tượng nhiều lần:

```java
List<Integer> ids = List.of(1, 2);
List<Number> numbers = new ArrayList<>();

int accepted = copyInto(ids, numbers);
```

Nó cho người dùng biết:

- Nguồn `List<Integer>` được chấp nhận mặc dù đích là `List<Number>`.
- Không dùng `List<Object>` làm kiểu đích mặc định nếu không cần.
- `copyInto` ghi vào destination, chứ không tạo list output mới.
- Return value diễn đạt số lần destination chấp nhận phần tử theo hợp đồng của method.

Một Javadoc tốt nên đi kèm **ít nhất một ví dụ với các kiểu có quan hệ subtype** nếu sự linh hoạt của wildcard là tính năng chính. Nếu chỉ thử `List<String>` → `List<String>`, bạn chưa chứng minh ích lợi thực sự của `extends` và `super`.

## 18. Lỗi đặt tên và ghi tài liệu thường gặp

**Lỗi 1 — “Dùng `T` ở mọi vị trí đều là cùng một kiểu.”** Không. Scope của type parameter khác nhau, và type inference có thể chọn common supertype cho nhiều đối số.

**Lỗi 2 — “`E`, `K`, `V` là từ khóa bắt buộc.”** Không. Chúng là quy ước giúp diễn đạt element/key/value.

**Lỗi 3 — “Khi input và output khác nhau, cứ dùng cùng `T`.”** Không. Nếu hai vai trò cần có thể khác kiểu, dùng `<S,R>` hoặc tên tương ứng để giữ hợp đồng đúng.

**Lỗi 4 — “Cứ thêm `<T>` dù không cần vì trông generic hơn.”** Không. Với API chỉ quan sát collection không liên hệ kiểu, `Collection<?>` có thể rõ hơn `<T> Collection<T>`.

**Lỗi 5 — “`List<T>` và `List<? extends T>` tương đương.”** Không. Wildcard cho nguồn có tính linh hoạt về subtype mà dạng invariant không tự có.

**Lỗi 6 — “`? super T` ở nguồn giúp đọc chính xác T.”** Không. Nó thường phù hợp cho **consumer**; khi đọc qua wildcard super, kiểu giá trị an toàn có thể chỉ là `Object`.

**Lỗi 7 — “Javadoc có `@param T` là đủ cho type parameter.”** Không. Type parameter dùng cú pháp `@param <T>`.

**Lỗi 8 — “`@param <T>` thay được việc giải thích null/mutability.”** Không. Tag mô tả vai trò kiểu; semantic contract phải được ghi riêng.

**Lỗi 9 — “Trả `List<R>` tự động đồng nghĩa kết quả là unmodifiable.”** Không. Interface và generic type argument không quyết định mutability.

**Lỗi 10 — “`T extends Number` có nghĩa chỉ nhận runtime Number chính xác.”** Không. Subtype như `Integer` cũng phù hợp theo upper-bound rules.

**Lỗi 11 — “Một `Pair<A,B>` bắt buộc A và B khác nhau.”** Không. Hai type parameters độc lập có thể cùng được gắn với một kiểu.

**Lỗi 12 — “JavaDoc có thể hứa rằng unchecked cast an toàn dù mã không kiểm tra gì.”** Không. Javadoc không khiến một cast kiểm tra type argument bị erasure; hợp đồng cần được thiết kế và chứng minh bởi API.

**Lỗi 13 — “Tên source/destination không quan trọng vì compiler đã kiểm tra.”** Không. Compiler kiểm tra tính hợp lệ kiểu, còn người dùng cần biết **hướng side effects và ownership** để dùng API đúng.

## 19. Bài tập tự kiểm tra

**Câu 1.** Nếu API nhận một giá trị kiểu `T` và trả đúng cùng loại kiểu `T`, dùng `<T>` hay `<S,R>`?

Thường dùng `<T>` để thể hiện quan hệ cùng type variable, miễn đúng hợp đồng thực tế.

**Câu 2.** `Map<K,V>` có buộc key/value là hai runtime classes khác nhau không?

Không. `K` và `V` là hai type parameters độc lập; cùng một type argument có thể gắn cho cả hai.

**Câu 3.** Vì sao `copyInto(Iterable<? extends T>, Collection<? super T>)` chấp nhận `List<Integer>` và `Collection<Number>`?

Nguồn sản xuất `Integer` phù hợp `T`, còn đích có thể tiêu thụ `T` thông qua lower-bounded wildcard; `Integer` là subtype của `Number`.

**Câu 4.** `static <S,R> List<R> map(..., Function<? super S, ? extends R>)` cần giải thích gì trong Javadoc?

Vai trò source/result types, cách mapper được áp dụng, thứ tự duyệt, mutability/copy của kết quả, null policy và exceptions.

**Câu 5.** Tại sao `@param <T>` khác `@param value`?

Một tag mô tả **type parameter**, tag còn lại mô tả **formal value parameter**.

**Câu 6.** Một phương thức chỉ cần `Collection` để log từng phần tử, có nên dùng `<T>` không?

Nếu không cần liên hệ kiểu `T` với kết quả hay tham số khác, `Collection<?>` thường ngắn và diễn đạt chính xác hơn.

**Câu 7.** Khi API trả `Optional<T>`, kiểu có tự nói nguyên nhân empty không?

Không. Javadoc phải giải thích empty nghĩa là không có phần tử, không tìm thấy hay kết quả bị lọc theo một quy tắc nào đó.

**Câu 8.** `<T extends Comparable<? super T>>` đảm bảo gì?

Đảm bảo có khả năng so sánh phù hợp với `T` ở cấp kiểu, cho phép gọi `compareTo` theo hợp đồng. Nó không tự quyết định null policy hoặc hành vi với danh sách rỗng.

**Câu 9.** Nếu method trả `new ArrayList<R>()`, Javadoc nói “unmodifiable list” có đúng không?

Không. `ArrayList` mutable; tài liệu phải khớp việc triển khai hoặc phải thay đổi implementation.

**Câu 10.** Có thể phân biệt hai overload chỉ bằng `<T>` và `<U>` khi parameter types khác nhau duy nhất ở tên type variable không?

Không nếu erased signatures tương đương. Việc đổi tên type variables không tạo overload phân biệt.

## 20. Checklist khi review một generic API

Trước khi công bố/chỉnh sửa một public generic API, kiểm tra:

1. **Tên type parameters:** dùng `E/K/V/T/R/S/U` nhất quán; có cần tên dài hơn để tránh nhầm vai trò không?
2. **Nơi khai báo:** type variable ở class hay method? Có bị che tên bởi `<T>` lồng nhau không?
3. **Quan hệ cùng kiểu:** có giữ được `T` giữa tham số và kết quả, hay vô tình mất thành `Object`?
4. **Nguồn–đích:** wildcard `extends`/`super` có phản ánh chiều đọc/ghi không?
5. **Bound:** có thật sự cần `extends Number`, `Comparable<? super T>` hoặc một interface cụ thể không?
6. **Type inference:** các use case phổ biến gọi method có tự nhiên và dễ chẩn đoán lỗi không?
7. **Javadoc type parameters:** đã có `@param <T>`, `@param <R>` cho mọi type parameter công khai cần tài liệu chưa?
8. **Javadoc value parameters:** tham số tên `source`, `destination`, `mapper`, `comparator` được mô tả đúng vai trò chưa?
9. **Return/exception contracts:** tài liệu có nói rõ empty, null, mutability, ownership, partial failure, aliases và side effects khi quan trọng không?
10. **Ví dụ:** đã có một lời gọi thể hiện quan hệ kiểu thật (ví dụ `Integer` → `Number`, `Integer` → `String`) chưa?
11. **Đồng bộ tài liệu với code:** nếu thay `ArrayList` bằng `List.copyOf`, đổi null policy hoặc chỉ số đếm, Javadoc đã cập nhật chưa?
12. **An toàn trước cast/raw:** có tránh `@SuppressWarnings("unchecked")` làm phương tiện che quan hệ kiểu sai không?

**Kết luận:** Đặt tên type parameters trong Java là một phần của **thiết kế hợp đồng**, không chỉ vấn đề phong cách. Chọn `T` cho một kiểu có quan hệ nhất quán, `E` cho element, `K/V` cho key/value, `S/R` cho source/result khi hữu ích; dùng `? extends` và `? super` để mô tả rõ producer/consumer; khai báo bounds đủ để compiler bảo vệ các thao tác thực sự cần. Sau đó, dùng Javadoc `@param <T>`, `@param`, `@return` và `@throws` để ghi những điều chữ ký chưa thể nói: null, dữ liệu rỗng, mutation, ownership, side effects, partial failure và ý nghĩa của kết quả. Một generic API tốt cho người đọc thấy được **các quan hệ kiểu ngay trong chữ ký**, còn tài liệu làm rõ **hành vi thực tế**.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="generic-api-design-003">Dùng kiểu chính xác khi cần giữ quan hệ đọc–ghi</a>

<details>
<summary>Xem chi tiết</summary>

# Dùng kiểu chính xác khi cần giữ quan hệ đọc–ghi trong Java Generics

## 1. Vấn đề thiết kế: một danh sách vừa cung cấp, vừa nhận lại cùng kiểu dữ liệu

Một lời khuyên quen thuộc khi viết generic APIs là **PECS — Producer Extends, Consumer Super**:

- Nếu collection chỉ **cung cấp** các giá trị kiểu T, chữ ký `? extends T` thường linh hoạt.
- Nếu collection chỉ **tiếp nhận** các giá trị kiểu T, chữ ký `? super T` thường linh hoạt.
- Nhưng khi **cùng một tham số vừa được đọc ra, vừa được ghi lại** và chương trình cần giữ **chính xác mối quan hệ kiểu ở hai hướng**, lựa chọn tốt có thể là **một type parameter T nhất quán**, chẳng hạn `List<T>`.

Ví dụ:

~~~java
static <T> T replaceFirst(List<T> values, T replacement) {
    return values.set(0, replacement);
}
~~~

Hàm này làm ba việc dưới **cùng một T**:

1. Nhận một `List<T>`.
2. Ghi giá trị `replacement` có kiểu T vào danh sách.
3. Trả về giá trị T cũ mà `List.set` cung cấp.

Caller dùng `List<String>` thì T là String; caller dùng `List<Integer>` thì T là Integer. Không cần ép kiểu, không cần wildcard capture ở bên ngoài, và compiler kiểm tra rằng giá trị thay thế tương thích với đúng danh sách được truyền vào.

~~~java
List<String> words = new ArrayList<>(List.of("old", "other"));

String previous = replaceFirst(words, "new"); // "old"
~~~

Nếu thử ghi số nguyên vào danh sách chuỗi:

~~~java
// replaceFirst(words, 99);
// Không biên dịch: không có T thống nhất giữa List<String> và Integer.
~~~

Đây là ví dụ cốt lõi của chủ đề **“dùng kiểu chính xác khi cần giữ quan hệ đọc–ghi”**. Không phải mọi wildcard đều xấu; nhưng wildcard là công cụ biểu diễn **một hướng linh hoạt** hoặc **một kiểu chưa xác định**, không thay thế được tất cả các ràng buộc giữa các lần đọc và ghi.

## 2. Định nghĩa “kiểu chính xác” trong generic API

Trong bài này, **kiểu chính xác** nghĩa là chữ ký giữ cùng một type argument có tên ở các vị trí cần liên hệ, ví dụ `List<T>`, `T`, `UnaryOperator<T>` hoặc `Map<K,V>`. Nó **không** có nghĩa runtime object chỉ được chứa chính xác một class không có subclass.

Ví dụ:

~~~java
List<Number> numbers = new ArrayList<>();
numbers.add(Integer.valueOf(1));
numbers.add(Double.valueOf(2.5));
~~~

Đây vẫn là `List<Number>` hợp lệ; Number là một kiểu tham chiếu mà Integer và Double đều có thể dùng tại vị trí nhận Number.

Cần phân biệt:

- `List<String>`: type argument đã cụ thể là String.
- `List<T>` trong generic method: type argument được caller và compiler xác định khi gọi; **các vị trí có cùng T phải nhất quán**.
- `List<?>`: danh sách có một type argument **chưa biết**, không được tự coi là String hay Number.
- `List<? extends Number>`: type argument là **một subtype chưa xác định** của Number.
- `List<? super Integer>`: type argument là **một supertype chưa xác định** của Integer.

**Tính invariant** của Java generics rất quan trọng:

~~~java
List<Integer> ints = new ArrayList<>();

// List<Number> invalid = ints; // Không biên dịch.
List<? extends Number> readable = ints; // Hợp lệ.
~~~

Dù Integer extends Number, `List<Integer>` không tự là subtype của `List<Number>`. Nếu điều đó được phép, code có thể ghi Double vào một danh sách vốn cam kết chứa Integer.

## 3. Ba kiểu chữ ký và khả năng đọc–ghi

| Loại tham số | Đọc ra an toàn | Ghi vào an toàn | Dùng khi |
| --- | --- | --- | --- |
| `List<T>` | T | Một giá trị tương thích T | Cần dùng cùng một T ở hai hướng. |
| `List<? extends T>` | T | Không thể thêm một T tùy ý | Chỉ cần lấy giá trị từ producer. |
| `List<? super T>` | Object (không bảo đảm đọc thành T) | T và các subtype phù hợp | Chỉ cần cung cấp các T cho consumer. |
| `List<?>` | Object | Không thể thêm giá trị non-null tùy ý | Chưa biết type argument, cần đọc/xử lý tổng quát. |
| `List<Object>` | Object | Bất kỳ Object | Danh sách thực sự được khai báo để chứa Object. |

Giải thích “không thể ghi” ở bảng: trình biên dịch không cho ghi **một giá trị T tùy ý** vào một collection có unknown captured type argument. Có những thao tác hợp lệ như `add(null)` về mặt kiểu tĩnh hoặc dùng **wildcard capture helper** để luân chuyển những giá trị đã được đọc từ chính collection đó. Vì vậy không nên hiểu câu này là “không một thao tác sửa đổi nào có thể tồn tại”.

Ngoài kiểu tĩnh, collection phải **có khả năng sửa đổi**: `List.of(...)`, `List.copyOf(...)` và danh sách từ `Stream.toList()` không cho các thao tác sửa đổi thông thường. Chữ ký `List<T>` không bảo đảm `set`, `add` hoặc `replaceAll` sẽ thành công ở runtime nếu list implementation không hỗ trợ.

## 4. Chỉ đọc: tại sao ? extends T là lựa chọn tốt

Ví dụ cần tính tổng một danh sách số:

~~~java
static double sum(List<? extends Number> values) {
    double total = 0;
    for (Number value : values) {
        total += value.doubleValue();
    }
    return total;
}
~~~

Có thể gọi:

~~~java
double a = sum(List.of(1, 2, 3));       // Integer elements.
double b = sum(List.of(1.5, 2.5));      // Double elements.
~~~

`? extends Number` cho phép danh sách có type argument Number, Integer, Double hoặc subtype phù hợp. Khi đọc, mỗi phần tử **ít nhất có thể được xem như Number**.

Nhưng không được làm:

~~~java
static void invalid(List<? extends Number> values) {
    // values.add(Integer.valueOf(5)); // Không biên dịch.
    // values.add(Double.valueOf(5));  // Không biên dịch.
}
~~~

Bởi compiler không biết actual type argument của collection là Integer, Double hay một subtype khác. Việc “Integer là Number” chưa đủ chứng minh Integer có thể được thêm vào mọi danh sách `List<? extends Number>`.

**Nguyên tắc:** nếu thực sự chỉ đọc, hãy chấp nhận `List<? extends Number>` để caller không phải sao chép List<Integer> thành List<Number>. Nhưng nếu method định **thay đổi chính các số** và trả lại cùng type argument, cần thiết kế khác.

## 5. Chỉ ghi: tại sao ? super T là lựa chọn tốt

Ví dụ đưa những Integer mới vào một đích:

~~~java
static void appendIds(List<? super Integer> target) {
    target.add(10);
    target.add(20);
}
~~~

Có thể gọi với:

~~~java
List<Integer> integerTarget = new ArrayList<>();
List<Number> numberTarget = new ArrayList<>();
List<Object> objectTarget = new ArrayList<>();

appendIds(integerTarget);
appendIds(numberTarget);
appendIds(objectTarget);
~~~

Tất cả đích đều có thể tiếp nhận một Integer. Nhưng **đọc từ `List<? super Integer>` chỉ bảo đảm Object**:

~~~java
List<? super Integer> target = new ArrayList<Number>();
target.add(42);

Object value = target.get(0); // Hợp lệ.
// Integer invalid = target.get(0); // Không biên dịch.
~~~

Lý do: actual type argument có thể là Integer, Number hoặc Object. Compiler không thể hứa một giá trị đọc ra từ List<Object> luôn là Integer.

Đừng cast tùy tiện thành Integer nếu dữ liệu có thể được thêm từ những nguồn khác. Nếu cần đọc lại dưới dạng Integer hoặc giữ loại chính xác của phần tử, một chữ ký có type variable T hoặc một invariant được xác thực sẽ hợp lý hơn.

## 6. Khi cùng một List vừa nhận T vừa trả T: chọn List<T>

Một API cập nhật có thể trả **giá trị trước khi thay đổi**:

~~~java
static <T> T replaceFirst(List<T> values, T replacement) {
    return values.set(0, replacement);
}
~~~

Thử ba cách thiết kế:

~~~java
// Biến thể chỉ đọc:
static Number first(List<? extends Number> values) {
    return values.get(0);
}

// Biến thể chỉ ghi:
static void append(List<? super Integer> values, Integer x) {
    values.add(x);
}

// Biến thể cần đọc–ghi cùng kiểu:
static <T> T replaceFirst(List<T> values, T replacement) {
    return values.set(0, replacement);
}
~~~

`first` dùng một upper bounded wildcard vì lấy ra Number là đủ. `append` dùng lower bounded wildcard vì chỉ đẩy Integer vào. `replaceFirst` cần một T thống nhất cho danh sách, phần tử mới và giá trị trả về.

Trong generic code, một **named type parameter** không phải chỉ là cách viết dài hơn của wildcard. Nó cho phép mô hình hóa **quan hệ bằng nhau giữa các vị trí kiểu** của cùng một lời gọi.

### 6.1. Vì sao không dùng List<? extends T> cho cập nhật?

Giả sử:

~~~java
static <T> void invalid(
        List<? extends T> values, T replacement) {
    // values.set(0, replacement); // Không biên dịch.
}
~~~

Dù replacement có kiểu T, type argument bị bắt (*captured*) của `values` có thể là subtype T hẹp hơn. Vì vậy compiler không thể cho phép viết T tổng quát vào list đó.

### 6.2. Vì sao không dùng List<? super T> cho cả đầu ra?

~~~java
static <T> T invalidRead(List<? super T> values) {
    // return values.get(0); // Không biên dịch.
    throw new UnsupportedOperationException();
}
~~~

Mảnh code trên chỉ nhằm minh họa kiểu. `get` của list loại này chỉ có thể đọc như Object. Việc ném UnsupportedOperationException trong ví dụ là placeholder kiểm tra chữ ký và **không phải cách triển khai đề xuất** cho một API thực tế.

Sửa thiết kế bằng `List<T>` nếu cần đọc và ghi cùng T:

~~~java
static <T> T takeAndReplace(List<T> list, T replacement) {
    return list.set(0, replacement);
}
~~~

Một trường hợp khác là tách đường đọc và ghi ra **hai tham số** producer/consumer nếu bài toán thực chất chỉ truyền một chiều.

## 7. Một API sửa nhiều phần tử: UnaryOperator<T>

Giả sử muốn biến đổi từng phần tử của một danh sách **ngay tại chỗ**, bảo đảm mapper trả cùng T:

~~~java
static <T> void editAll(
        List<T> values, UnaryOperator<T> editor) {
    values.replaceAll(editor);
}
~~~

`UnaryOperator<T>` là specialization của `Function<T,T>`. Nó nhận T và trả T, rất phù hợp với thao tác **đọc → biến đổi → ghi cùng loại**.

Sử dụng:

~~~java
List<String> words = new ArrayList<>(List.of("hello", "world"));

editAll(words, String::toUpperCase);
System.out.println(words); // [HELLO, WORLD]
~~~

Chữ ký `List.replaceAll` của Java định nghĩa một `UnaryOperator<E>` phù hợp với kiểu phần tử E của danh sách. Compiler bảo đảm mapper không đột ngột biến một List<String> thành danh sách Integer.

### 7.1. Vì sao Function<T,R> không thay UnaryOperator<T> trong tình huống này?

Một `Function<String,Integer>` biến String thành Integer. Nếu muốn **thay thế phần tử trong chính List<String>**, kết quả Integer không thể lưu trở lại mà vẫn giữ `List<String>`.

~~~java
Function<String, Integer> length = String::length;

// List<String> words = ...
// words.replaceAll(length); // Không biên dịch.
~~~

Nếu nghiệp vụ muốn tạo loại danh sách mới, hãy tạo **List<R> mới** qua `Stream.map` hoặc một method biến đổi không sửa nguồn. Không nên ép unchecked để biến một List<String> thành List<Integer> trên cùng object.

## 8. Hoán đổi giữa hai danh sách: quan hệ T phải nhất quán

Khi cần trao đổi phần tử giữa **hai List**, cả hai đầu vào đều vừa cung cấp vừa nhận lại phần tử kiểu T:

~~~java
static <T> void swapFirst(List<T> left, List<T> right) {
    T oldLeft = left.get(0);
    left.set(0, right.get(0));
    right.set(0, oldLeft);
}
~~~

Gọi hợp lệ:

~~~java
List<String> left = new ArrayList<>(List.of("A"));
List<String> right = new ArrayList<>(List.of("B"));

swapFirst(left, right); // left chứa B, right chứa A.
~~~

Nhưng:

~~~java
List<String> words = new ArrayList<>(List.of("A"));
List<Integer> codes = new ArrayList<>(List.of(1));

// swapFirst(words, codes); // Không biên dịch.
~~~

Compiler không tìm được **một T đồng nhất** cho cả `List<String>` và `List<Integer>`. Nếu cho phép, phương thức có thể ghi Integer vào List<String> và String vào List<Integer>, phá cam kết kiểu.

Đây là ví dụ mà hai chữ ký kiểu `List<? extends Object>` sẽ **quá yếu**: cả hai list đều được chấp nhận để đọc Object, nhưng không có bảo đảm hai captured element types có thể ghi chéo cho nhau.

### 8.1. “Cùng T” chặt hơn “đều là subtype của Number”

~~~java
List<Integer> ints = new ArrayList<>(List.of(1));
List<Double> doubles = new ArrayList<>(List.of(2.5));

// swapFirst(ints, doubles); // Không biên dịch.
~~~

Dù Integer và Double đều extends Number, không có phép hoán đổi an toàn giữa hai list đó theo type arguments khai báo.

Nếu nghiệp vụ thật sự cần một danh sách hỗn hợp Number, caller có thể chủ động tạo hai `List<Number>` từ đầu. Nhưng không nên thay đổi kiểu nhìn của List<Integer> bằng unchecked cast để làm cho lời gọi được chấp nhận.

## 9. Copy một chiều khác hoàn toàn hoán đổi hai chiều

Đối với **truyền dữ liệu một chiều**, không nên bắt cả hai list có đúng cùng type argument nếu điều đó hạn chế caller vô ích.

Một chữ ký linh hoạt:

~~~java
static <T> void copyForward(
        List<? extends T> source,
        List<? super T> target) {

    for (T value : source) {
        target.add(value);
    }
}
~~~

Giải thích:
- Source **sinh** ra T → `? extends T`.
- Target **nhận** T → `? super T`.
- Method không đọc target như T để ghi ngược về source, nên không cần hai loại phần tử phải bằng nhau.

Ví dụ:

~~~java
List<Integer> source = List.of(7, 8);
List<Number> destination = new ArrayList<>();

copyForward(source, destination);

System.out.println(destination); // [7, 8]
~~~

`List<Integer>` có thể làm producer cho T là Number, và `List<Number>` nhận Number. Với một method chỉ truyền một chiều, chữ ký PECS thường đúng ngữ nghĩa và tái sử dụng tốt hơn `<T> void copy(List<T>,List<T>)`.

### 9.1. Đối chiếu với Collections.copy

Thư viện chuẩn cũng có chữ ký:

~~~java
public static <T> void copy(
        List<? super T> dest,
        List<? extends T> src);
~~~

Trong `Collections.copy`, đích đứng **trước** nguồn; ý nghĩa type bounds vẫn như trên.

**Lưu ý hành vi rất quan trọng:** `Collections.copy` dùng thao tác thay thế phần tử theo chỉ số, không tự mở rộng danh sách. Destination phải có kích thước **ít nhất bằng source**, nếu không ném IndexOutOfBoundsException. Trong khi `copyForward` ví dụ ở trên dùng `add` để nối dữ liệu vào cuối một list mutable.

Vì vậy hai method có chữ ký PECS tương tự nhưng không hề có cùng hành vi về kích thước và vị trí lưu.

## 10. Wildcard capture: sửa trong chính một List<?> mà không biết T

Khi chỉ có:

~~~java
List<?> values;
~~~

không thể thêm tùy ý một String/Integer vì actual element type là unknown. Nhưng đôi khi tác vụ **không cần biết kiểu đó**, chỉ cần chuyển vị trí các phần tử **bên trong cùng list**.

Ví dụ hoán đổi hai phần tử:

~~~java
static void swapUnknown(List<?> values) {
    swapCaptured(values);
}

private static <T> void swapCaptured(List<T> values) {
    T first = values.get(0);
    values.set(0, values.get(1));
    values.set(1, first);
}
~~~

**Đây là mẫu wildcard capture helper**:
1. Method public nhận List<?> để không yêu cầu caller biết loại phần tử.
2. Compiler có thể capture wildcard thành một type variable tạm thời cho **cùng object** khi gọi helper.
3. Bên trong helper, mọi lần đọc/ghi dùng **cùng T**, do đó hoán đổi các giá trị đã được đọc từ danh sách vẫn type-safe.
4. Không có một giá trị T mới tùy ý nào từ bên ngoài được đưa vào danh sách.
5. Cần list mutable và đủ phần tử, nếu không có thể ném UnsupportedOperationException/IndexOutOfBoundsException.

Sử dụng:

~~~java
List<Integer> codes = new ArrayList<>(List.of(1, 2));
swapUnknown(codes);
System.out.println(codes); // [2, 1]
~~~

Điều này chứng tỏ câu “wildcard list không bao giờ ghi được” là quá đơn giản. **Với helper đã capture cùng một unknown type**, một thao tác nội bộ không làm thay đổi loại phần tử vẫn có thể an toàn.

### 10.1. Vì sao capture không giải quyết việc hoán đổi giữa hai wildcard lists?

Giả sử:

~~~java
static void invalid(List<?> first, List<?> second) {
    // Không thể tự lấy phần tử của first rồi set vào second
    // vì hai wildcard có thể là hai captured types khác nhau.
}
~~~

`first` có thể là List<String>, `second` có thể là List<Integer>. Việc cả hai dùng dấu `?` không chứng minh chúng có cùng element type.

Một helper có **một** T được áp dụng lên cùng list cho phép hoán đổi nội bộ. Nhưng để hoán đổi an toàn **giữa hai lists**, cần quan hệ kiểu mạnh hơn ở public API, ví dụ `<T> void swapFirst(List<T>, List<T>)`.

## 11. Ví dụ Java 17+ hoàn chỉnh, đã kiểm chứng

Lưu nội dung sau thành **ExactTypeDemo.java**:

~~~java
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

public class ExactTypeDemo {
    static <T> T replaceFirst(
            List<T> values, T replacement) {
        return values.set(0, replacement);
    }

    static <T> void editAll(
            List<T> values, UnaryOperator<T> editor) {
        values.replaceAll(editor);
    }

    static <T> void swapFirst(
            List<T> left, List<T> right) {
        T oldLeft = left.get(0);
        left.set(0, right.get(0));
        right.set(0, oldLeft);
    }

    static <T> void copyForward(
            List<? extends T> source, List<? super T> target) {
        for (T value : source) {
            target.add(value);
        }
    }

    static void swapUnknown(List<?> values) {
        swapCaptured(values);
    }

    private static <T> void swapCaptured(List<T> values) {
        T first = values.get(0);
        values.set(0, values.get(1));
        values.set(1, first);
    }

    public static void main(String[] args) {
        List<String> words =
                new ArrayList<>(List.of("old", "middle"));

        System.out.println(
                "previous=" + replaceFirst(words, "new")
        );

        editAll(words, String::toUpperCase);
        System.out.println("edited=" + words);

        List<String> left =
                new ArrayList<>(List.of("A"));
        List<String> right =
                new ArrayList<>(List.of("B"));

        swapFirst(left, right);
        System.out.println(
                "swapped=" + left + "/" + right
        );

        List<Integer> codes =
                new ArrayList<>(List.of(1, 2));

        swapUnknown(codes);
        System.out.println("capture=" + codes);

        List<Integer> source = List.of(7, 8);
        List<Number> target = new ArrayList<>();

        copyForward(source, target);
        target.add(3.5);
        System.out.println("copied=" + target);

        List<? extends Number> producer = source;
        Number n = producer.get(0);
        System.out.println("read=" + n);

        List<? super Integer> consumer = target;
        consumer.add(9);

        Object item = consumer.get(0);
        System.out.println("written=" + target);
        System.out.println(
                "readBackType=" +
                item.getClass().getSimpleName()
        );
    }
}
~~~

Biên dịch bằng JDK hỗ trợ Java 17:

~~~bash
javac --release 17 -Xlint:all ExactTypeDemo.java
java ExactTypeDemo
~~~

**Stdout chính xác:**

~~~text
previous=old
edited=[NEW, MIDDLE]
swapped=[B]/[A]
capture=[2, 1]
copied=[7, 8, 3.5]
read=7
written=[7, 8, 3.5, 9]
readBackType=Integer
~~~

### 11.1. Giải thích từng kết quả

**`previous=old`**: `replaceFirst` vừa nhận String mới, vừa trả String cũ từ cùng List<String>. Generic method bảo toàn quan hệ T giữa danh sách, giá trị mới và return type.

**`edited=[NEW, MIDDLE]`**: `UnaryOperator<String>` đọc String và trả String; List.replaceAll có thể ghi kết quả trở lại đúng danh sách.

**`swapped=[B]/[A]`**: hai List<String> cùng một T nên có thể trao đổi giá trị ở hai hướng.

**`capture=[2, 1]`**: public method nhận List<?> và helper capture type argument nội bộ để hoán đổi hai giá trị có cùng kiểu đã được đọc từ chính danh sách đó.

**`copied=[7, 8, 3.5]`**: List<Integer> đưa Integer sang List<Number> qua producer extends / consumer super. Sau đó đích có thể thêm một Double vì type argument thực sự của đích là Number.

**`read=7`**: `List<? extends Number>` cho phép đọc Number an toàn từ List<Integer>.

**`written=[7, 8, 3.5, 9]`**: `List<? super Integer>` cho phép ghi Integer vào một List<Number>.

**`readBackType=Integer`**: biến đọc từ consumer wildcard có kiểu tĩnh Object; ở runtime đối tượng cụ thể tại vị trí 0 là Integer, nhưng điều đó **không đổi hợp đồng đọc** của `List<? super Integer>` thành Integer.

### 11.2. Điều kiện runtime của ví dụ

Mọi thao tác ghi dùng `ArrayList` mutable. Nếu truyền `List.of(...)` làm đích của `set`, `replaceAll` hoặc `add`, phương thức có thể ném UnsupportedOperationException dù **biên dịch hoàn toàn hợp lệ**.

`replaceFirst`, `swapFirst` và `swapCaptured` còn giả định danh sách có phần tử ở chỉ số được truy cập. Nếu list rỗng hoặc chỉ có một phần tử trong phép swap hai vị trí, có thể ném IndexOutOfBoundsException. Đây là **điều kiện dữ liệu**, không phải vấn đề generic type argument.

Không nên dùng unchecked casts để né những điều kiện đó: cast không thể biến một list immutable thành mutable hoặc tạo phần tử chưa tồn tại.

## 12. Những đoạn code cố ý không biên dịch

Các ví dụ sau được viết để **thử quy tắc kiểu**, không ghép cùng class runnable ở trên nếu muốn chạy chương trình hoàn chỉnh.

### 12.1. Thay một Integer vào List<String>

~~~java
List<String> words = new ArrayList<>(List.of("A"));

// replaceFirst(words, 42);
// Lỗi: không có T nhất quán giữa List<String> và Integer.
~~~

### 12.2. Hoán đổi List<String> và List<Integer>

~~~java
List<String> names = new ArrayList<>(List.of("A"));
List<Integer> ids = new ArrayList<>(List.of(1));

// swapFirst(names, ids);
// Lỗi suy luận: T có các equality constraints không tương thích.
~~~

### 12.3. Ghi qua extends

~~~java
List<? extends Number> source = List.of(1, 2);

// source.add(3);
// Không thể thêm một Number/Integer tùy ý vào captured subtype.
~~~

### 12.4. Đọc như T từ super

~~~java
List<? super Integer> sink = new ArrayList<Number>();

// Integer value = sink.get(0);
// Không biên dịch: kết quả chỉ có kiểu an toàn Object.
~~~

Cẩn thận: `sink.get(0)` có thể còn gây IndexOutOfBoundsException nếu đích chưa có phần tử; lỗi biên dịch ở đây xảy ra **trước khi xét runtime** vì type mismatch.

### 12.5. Ghi mapper trả loại khác vào List ban đầu

~~~java
List<String> names = new ArrayList<>(List.of("A"));
Function<String, Integer> toLength = String::length;

// names.replaceAll(toLength);
// Không hợp lệ: cần UnaryOperator<String>, không phải Function<String,Integer>.
~~~

Các ví dụ này minh họa compiler chặn **mất quan hệ kiểu** mà không cần chạy chương trình. Không được sửa bằng cách ép raw List rồi ghi sai element type.

## 13. Dùng kiểu chính xác cho Map<K,V> khi cần đọc và cập nhật value

Tương tự `List<T>`, một `Map<K,V>` có quan hệ giữa:
- Kiểu khóa `K`.
- Kiểu giá trị `V` đọc từ map.
- Kiểu giá trị `V` sẽ ghi lại vào map.

Ví dụ:

~~~java
static <K, V> V replaceValue(
        Map<K, V> map, K key, V replacement) {
    return map.put(key, replacement);
}
~~~

Nếu caller có `Map<String,Integer>`, method chỉ nhận value tương thích Integer; `Map<String,Number>` có thể nhận Integer hoặc Double dưới contract Number.

Với nhu cầu sửa giá trị dựa trên giá trị cũ:

~~~java
static <K, V> void editValue(
        Map<K, V> map, K key, UnaryOperator<V> change) {

    if (!map.containsKey(key)) {
        return;
    }

    V oldValue = map.get(key);
    map.put(key, change.apply(oldValue));
}
~~~

Bản code này được viết như **mảnh method độc lập** và cần `import java.util.Map`, `import java.util.function.UnaryOperator`. Nó cập nhật value với cùng V. Nếu map đang chứa một key gắn với null value, `oldValue` là null và `change` cần tự xử lý null; nếu map immutable, `put` có thể không được hỗ trợ.

### 13.1. Tại sao không lạm dụng Map<? extends K, ? extends V>?

Một map được nhìn qua producer wildcards:

~~~java
Map<? extends String, ? extends Number> view = someMap;
~~~

có thể cho đọc dữ liệu như String/Number ở những vị trí phù hợp, nhưng không bảo đảm cho phép cập nhật bằng một Number tùy ý vào value vì actual value type có thể là Integer, Double hoặc subtype Number khác.

Với lower bounds, có thể ghi một V phù hợp nhưng đọc lại chưa chắc ra V. Nếu API **thực sự cập nhật đúng giá trị V của Map<K,V> và trả V cũ**, named type parameters thường diễn đạt mối quan hệ tốt hơn.

Nếu nhiệm vụ là **copy entries một chiều** từ một map nguồn sang một map đích khác, có thể thiết kế wildcard tương ứng; không cần biến mọi method thành invariant khi không có quan hệ đọc-ghi hai chiều cần giữ.

## 14. So sánh chính xác: List<T>, List<? extends T>, List<? super T>

Ba chữ ký có cùng một ký tự T nhưng không có cùng ngữ nghĩa.

~~~java
static <T> void exact(List<T> list, T value) {
    list.add(value);
}

static <T> T produce(List<? extends T> list) {
    return list.get(0);
}

static <T> void consume(List<? super T> list, T value) {
    list.add(value);
}
~~~

Trong `exact`:
- Một T được suy luận từ toàn bộ các ràng buộc của lời gọi.
- Danh sách và value được gắn cùng một type argument T.
- Code có thể đọc từ list dưới dạng T khi cần.

Trong `produce`:
- List có unknown subtype của T.
- Method chỉ dùng giá trị đọc ra như T.
- Không thể thêm tùy ý value T trở lại.

Trong `consume`:
- List có unknown supertype của T.
- Method ghi value T được.
- Khi đọc, chỉ bảo đảm Object.

Điểm chính: **PECS quyết định vị trí wildcard dựa trên vai trò**, còn **named T quyết định ràng buộc chung giữa các vị trí kiểu**. Chúng bổ sung nhau, không loại trừ nhau.

## 15. Khi nào một chữ ký wildcard linh hoạt hơn và khi nào quá yếu?

### 15.1. Wildcard linh hoạt hơn: chỉ cần chuyển một chiều

~~~java
static <T> void appendAll(
        Collection<? extends T> source,
        Collection<? super T> target) {
    for (T element : source) {
        target.add(element);
    }
}
~~~

`Collection<Integer>` có thể chuyển vào `Collection<Number>`. Nếu dùng cả hai tham số `Collection<T>`, caller phải thỏa ràng buộc equality chặt hơn và có thể bị từ chối dù dữ liệu di chuyển một chiều hoàn toàn an toàn.

### 15.2. Wildcard quá yếu: cần trao đổi hai chiều

~~~java
static <T> void exchange(List<T> a, List<T> b) {
    T first = a.get(0);
    a.set(0, b.get(0));
    b.set(0, first);
}
~~~

Không thể thay bừa bằng hai tham số `List<? extends T>` rồi vẫn giữ logic, vì hai lists có thể nắm những captured subtypes khác nhau.

### 15.3. Không cần type parameter nếu chỉ một vị trí

Nếu chỉ cần nhận danh sách không rõ type và in số phần tử:

~~~java
static int sizeOf(List<?> list) {
    return list.size();
}
~~~

Không có một mối quan hệ giữa các vị trí kiểu phải đặt tên, nên `<?>` thường gọn và rõ hơn:

~~~java
static <T> int sizeOfOverGeneric(List<T> list) {
    return list.size();
}
~~~

Method thứ hai vẫn hợp lệ, nhưng type parameter T không đem lại giá trị về mối quan hệ kiểu nếu implementation và API không sử dụng T ở đâu khác. **Đừng thêm type parameter chỉ để tránh wildcard**; hãy dùng nó khi thật sự cần kết nối các vị trí.

## 16. Lưu ý về method references và kiểu Function đọc–ghi

Một API nhận mapper để **thay đổi List<T> tại chỗ** thường muốn:

~~~java
static <T> void modify(
        List<T> values, UnaryOperator<T> mapper) {
    values.replaceAll(mapper);
}
~~~

Một API tạo danh sách kết quả mới có thể muốn:

~~~java
static <T, R> List<R> transform(
        List<? extends T> input,
        Function<? super T, ? extends R> mapper) {

    List<R> output = new ArrayList<>();
    for (T item : input) {
        output.add(mapper.apply(item));
    }
    return output;
}
~~~

Ở hàm thứ hai:
- Input **chỉ được đọc** → `List<? extends T>`.
- Mapper nhận T và trả R (hoặc subtype phù hợp).
- Output là **List<R> mới**, không cần ép về List<T>.
- Hai type parameters T và R mô tả rõ việc **đầu vào và đầu ra có thể khác kiểu**.

Dùng:

~~~java
List<Integer> lengths = transform(
        List.of("Java", "Go"),
        String::length
); // [4, 2]
~~~

Nếu phải giữ danh sách gốc và kiểu phần tử gốc, hãy dùng API invariant cùng UnaryOperator<T>; nếu thật sự chuyển sang một loại kết quả mới, dùng generic Function<T,R> và tạo container R riêng.

## 17. Invariance không có nghĩa đối tượng con bị cấm

Một ngộ nhận:

> “List<T> là kiểu chính xác, tức chỉ chấp nhận phần tử có class runtime trùng hoàn toàn T.”

Sai. Với `T = Number`:

~~~java
List<Number> values = new ArrayList<>();
values.add(Integer.valueOf(4));
values.add(Double.valueOf(1.5));
~~~

Hai object có runtime classes khác nhau nhưng đều tương thích với **Number ở compile time**.

Cái không được phép là **thay type argument của collection** bằng một subtype rồi dùng nó như collection chứa supertype:

~~~java
List<Integer> integers = new ArrayList<>();
// List<Number> asNumbers = integers; // Không biên dịch.
~~~

Nghĩa của invariant là quan hệ subtype giữa **List<Integer> và List<Number>** không tự được kế thừa từ quan hệ Integer <: Number. Nó không ngăn `List<Number>` chứa những instance thuộc subclasses of Number.

## 18. Tránh sửa kiểu bằng raw cast hoặc unchecked cast

Cách làm sai dễ thấy:

~~~java
List<Integer> integers = new ArrayList<>(List.of(1, 2));

// Không nên làm:
List raw = integers;
raw.add("text");
~~~

Nếu alias `integers` đọc phần tử "text" dưới kiểu Integer, nó có thể ném ClassCastException. Mục đích của generic method với cùng T là **không tạo đường đưa sai type argument vào data structure**.

Đừng dùng:

~~~java
// List<Number> forged = (List<Number>) (List<?>) integers;
~~~

để vượt qua invariance khi triển khai swap/copy hai chiều. Dù unchecked cast được compiler cho phép ở một vị trí có suppress warning, nó không biến collection thành loại runtime khác, và không đảm bảo dữ liệu tiếp tục đúng kiểu khi có thao tác ghi.

Nếu API hiện tại đòi invariant nhưng nghiệp vụ chỉ cần copy một chiều, hãy **đổi chữ ký thành PECS**. Nếu thực sự cần read/write hai chiều, hãy **đổi cấu trúc dữ liệu** để có cùng type argument ngay từ nơi tạo, hoặc đưa ra quy tắc chuyển đổi rõ ràng tạo object mới.

## 19. Ngoại lệ từ implementation và chính sách dữ liệu

Một generic signature hợp lệ không có nghĩa mọi thao tác thành công ở runtime.

### 19.1. Danh sách không sửa được

~~~java
List<String> fixed = List.of("A");

// replaceFirst(fixed, "B");
// Runtime UnsupportedOperationException khi gọi set.
~~~

Muốn sửa, tạo mutable copy:

~~~java
List<String> mutable = new ArrayList<>(fixed);
replaceFirst(mutable, "B");
~~~

### 19.2. Chỉ số không hợp lệ

~~~java
List<String> empty = new ArrayList<>();

// replaceFirst(empty, "A");
// Runtime IndexOutOfBoundsException.
~~~

Bài tập production cần check `isEmpty()`, quy định lỗi rõ, hoặc chọn API không phụ thuộc một index cố định.

### 19.3. Chính sách null

Java generics không tự cấm null. Một số List implementations chấp nhận null, còn factory methods như `List.of` và `List.copyOf` không chấp nhận null theo hợp đồng. Nếu API muốn cấm null, có thể yêu cầu `Objects.requireNonNull` và ghi chính sách đó rõ.

### 19.4. Side effects và thread safety

`List<T>` không tự khiến list thread-safe. Nếu nhiều thread gọi `swapFirst`, `editAll`, `replaceFirst` trên cùng mutable lists không đồng bộ, có thể gặp race conditions hoặc trạng thái bất nhất. Khi xử lý song song, cần đồng bộ phù hợp, sử dụng data structures hỗ trợ concurrency hoặc thiết kế immutable/functional mới.

## 20. Bảng chọn chữ ký API

| Mục tiêu thực tế | Gợi ý chữ ký | Lý do |
| --- | --- | --- |
| Chỉ đếm phần tử | `size(List<?>)` | Không cần biết T. |
| Chỉ đọc số | `sum(List<? extends Number>)` | Chấp nhận List<Integer>, List<Double>, List<Number>. |
| Chỉ thêm Integer | `append(List<? super Integer>)` | Chấp nhận đích List<Integer>, Number, Object. |
| Thay một phần tử và trả phần tử cũ | `<T> T replace(List<T>, T)` | Đọc và ghi cùng T. |
| Sửa mọi phần tử tại chỗ | `<T> void edit(List<T>, UnaryOperator<T>)` | T→T và ghi vào List<T>. |
| Hoán đổi hai danh sách cùng type argument | `<T> void swap(List<T>, List<T>)` | Bảo đảm cùng T ở hai hướng. |
| Hoán đổi bên trong một list chưa biết T | `swap(List<?>)` + capture helper | Chỉ đổi vị trí giá trị của cùng captured T. |
| Copy từ producer sang consumer | `<T> copy(List<? extends T>, List<? super T>)` | Không buộc hai type arguments giống hệt. |
| Biến T thành R và tạo kết quả mới | `<T,R> List<R> map(List<? extends T>, Function<? super T,? extends R>)` | Cho phép đổi loại dữ liệu. |
| Cập nhật map và trả giá trị V cũ | `<K,V> V replace(Map<K,V>,K,V)` | Giữ quan hệ key/value ở cả đọc và ghi. |

Đây là **gợi ý chữ ký theo vai trò**, không phải yêu cầu mọi method trên thế giới có đúng một hình dạng. Chọn theo bất biến dữ liệu, yêu cầu sửa đổi, và mức linh hoạt cần cấp cho caller.

## 21. Checklist review API generics

1. **Ghi rõ chiều luồng dữ liệu**: collection chỉ tạo ra giá trị, chỉ nhận giá trị hay cả hai?
2. **Nếu chỉ đọc**, cân nhắc `? extends T` để chấp nhận subtype producers.
3. **Nếu chỉ ghi**, cân nhắc `? super T` để chấp nhận supertype consumers.
4. **Nếu cần đọc–ghi cùng loại**, dùng **named type parameter T** thống nhất giữa collection, input values, mapper và output.
5. **Nếu có hai collection tương tác**, xác định chiều copy hay exchange; một chiều có thể dùng PECS, hai chiều có thể cần cùng T.
6. **Nếu chỉ dùng T ở một vị trí**, xem có thể dùng `<?>` thay cho type parameter T vô ích không.
7. **Không coi List<Object> và List<?> giống nhau**: List<Object> chấp nhận Object khi ghi; List<?> giữ unknown element type.
8. **Khi wildcard cần thao tác nội bộ**, cân nhắc capture helper và bảo đảm không trộn hai unknown captures khác nhau.
9. **Tránh unchecked casts và raw types** để phá invariance hoặc đánh lừa compiler.
10. **Kiểm tra mutability/indices/null** vì type safety không bảo đảm thao tác trên List thực sự được hỗ trợ.
11. **Tránh shared mutable state không đồng bộ**, nhất là method đổi nhiều collections.
12. **Dùng UnaryOperator<T> cho in-place transform**, Function<T,R> cho transform có thể đổi kiểu và trả container mới.
13. **Không lạm dụng type parameter để tạo sự phức tạp giả**: chính xác khi cần bảo toàn quan hệ; wildcard khi chỉ cần linh hoạt một hướng.
14. **Biên dịch với Java release mục tiêu** và thử cả trường hợp hợp lệ, không hợp lệ để xác nhận compile-time contract.

## 22. Câu hỏi tự kiểm tra

**1.** Tại sao `List<? extends Number>` cho đọc Number nhưng không cho thêm một Integer tùy ý?

**Đáp án:** Actual element type có thể là một subtype Number chưa biết (ví dụ Double), nên một Integer mới chưa chắc an toàn với mọi khả năng.

**2.** Tại sao `List<? super Integer>` nhận Integer nhưng `get` chỉ có thể dùng như Object?

**Đáp án:** Actual type argument có thể là Integer, Number hoặc Object; compiler không biết mọi phần tử được đọc đều là Integer.

**3.** Vì sao `<T> T replaceFirst(List<T>,T)` tốt hơn hai wildcards cho tác vụ cập nhật?

**Đáp án:** Cùng T liên hệ kiểu danh sách, replacement và giá trị trả về; method cần đồng thời ghi và đọc đúng loại đã khai báo.

**4.** `List<Integer>` có thể gán thành `List<Number>` không?

**Đáp án:** Không. Java generic types nhìn chung invariant; nếu gán được, một Double có thể được thêm vào list dành cho Integer.

**5.** Hai List<String> dùng được với `<T> void swapFirst(List<T>,List<T>)` không?

**Đáp án:** Có. Compiler chọn T là String và mọi giá trị hoán đổi giữ được type.

**6.** Hai List<String> và List<Integer> có hoán đổi bằng method đó được không?

**Đáp án:** Không. Compiler không tìm được một T thống nhất vì hai parameterized list types bất biến.

**7.** Vì sao `copyForward(List<? extends T>,List<? super T>)` có thể chấp nhận List<Integer> và List<Number>?

**Đáp án:** Một chiều dữ liệu: source sinh Integer dùng như Number, target nhận Number; không có yêu cầu ghi ngược Number vào List<Integer>.

**8.** `List<?>` có cho đổi chỗ hai phần tử trong chính nó được không?

**Đáp án:** Có thể, nếu list mutable, qua generic helper để capture unknown element type rồi đọc/ghi những giá trị lấy từ cùng list. Không được tự thêm một giá trị bên ngoài với type bất kỳ.

**9.** Vì sao hai `List<?>` khác nhau không thể mặc nhiên hoán đổi phần tử?

**Đáp án:** Hai wildcards có thể là hai captured types khác nhau, như String và Integer; không có bảo đảm hai chiều.

**10.** Tại sao `UnaryOperator<T>` phù hợp khi dùng `List.replaceAll`?

**Đáp án:** UnaryOperator nhận T và trả T, phù hợp với việc lấy element T rồi ghi trở lại cùng List<T>.

**11.** Khi cần biến String thành Integer và trả list mới, nên thiết kế API thế nào?

**Đáp án:** Dùng `Function<? super T,? extends R>` để tạo R và một `List<R>` mới; không biến List<String> gốc thành List<Integer> bằng unchecked cast.

**12.** `List<T>` có đảm bảo có thể gọi `set` không?

**Đáp án:** Không. List.of và những immutable/unmodifiable implementations có thể ném UnsupportedOperationException ở runtime; type parameter không mô tả mutability.

**13.** Trong Map<K,V>, nếu đọc value V rồi áp dụng UnaryOperator<V> và put lại, nên dùng kiểu gì?

**Đáp án:** `Map<K,V>` cùng type variable V ở chỗ đọc/ghi; nó giữ value type thống nhất.

**14.** Nếu một method chỉ trả `list.size()` thì có cần `<T>` không?

**Đáp án:** Thường không. `List<?>` đủ vì không có quan hệ T qua nhiều vị trí cần biểu đạt.

**15.** Nếu compiler chặn một phép ghi vào `List<? extends T>`, nên sửa bằng raw cast không?

**Đáp án:** Không. Hãy xem method có cần named T cho quan hệ đọc–ghi, hay cần cấu trúc mới/PECS để mô tả đúng chiều dữ liệu. Unchecked cast có thể tạo heap pollution.

## 23. Ghi nhớ

**Đặt wildcard theo hướng dữ liệu, đặt named type parameter theo quan hệ cần bảo toàn.**

~~~text
Đọc một chiều:    List<? extends T> ────→ T
Ghi một chiều:    T ────→ List<? super T>

Đọc rồi ghi lại cùng loại:
                  List<T> ────→ T ──transform──→ T ────→ List<T>

Hoán đổi hai chiều:
                  List<T> ⇄ List<T>
                  (cùng một T)

Một list chưa biết phần tử:
                  List<?> ──capture T──→ đọc/ghi nội bộ cùng T
~~~

Chọn **`List<T>`** hoặc **`Map<K,V>`** khi method cần lấy phần tử/vị trí có kiểu được khai báo rồi ghi lại một giá trị **phù hợp cùng type parameter**. Chọn **`? extends T`** khi muốn nhận producer linh hoạt, **`? super T`** khi muốn nhận consumer linh hoạt, và **`<?>`** khi không cần đặt tên cho loại phần tử.

Không vì một warning hay lỗi inference mà ép raw/unchecked để lách hệ thống kiểu. Đúng hơn là xem lại **mối quan hệ giữa các đầu vào, các giá trị được đọc và các giá trị được ghi**. Một chữ ký generic tốt phải mô tả chính xác mối quan hệ đó, đủ linh hoạt cho caller nhưng không mở đường phá vỡ tính an toàn của dữ liệu.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="generic-api-design-004">Dùng wildcard cho đầu vào linh hoạt theo PECS</a>

<details>
<summary>Xem chi tiết</summary>

# Dùng wildcard cho đầu vào linh hoạt theo PECS trong thiết kế API Java Generics

## 1. Vấn đề: API nhận được `List<Dog>` nhưng lại từ chối `List<Animal>`

Bạn đang xây một API nhận danh sách động vật:

```java
class Animal {
    String name() {
        return "animal";
    }
}

class Dog extends Animal {
}

class Cat extends Animal {
}
```

Một phiên bản đầu tiên:

```java
static void printNames(List<Animal> animals) {
    for (Animal animal : animals) {
        System.out.println(animal.name());
    }
}
```

Nhưng caller có một biến:

```java
List<Dog> dogs = new ArrayList<>();
```

và gặp lỗi:

```java
// Không biên dịch:
// printNames(dogs);
```

**Nguyên nhân:** Java Generics là *invariant*: dù `Dog extends Animal`, `List<Dog>` không phải subtype của `List<Animal>`.

Nếu method chỉ **đọc** danh sách, buộc caller chuyển đổi/copy sang `List<Animal>` là không cần thiết. Chữ ký linh hoạt hơn:

```java
static void printNames(List<? extends Animal> animals) {
    for (Animal animal : animals) {
        System.out.println(animal.name());
    }
}
```

Bây giờ method có thể nhận `List<Animal>`, `List<Dog>`, `List<Cat>` và các danh sách chứa subtype thích hợp khác, trong khi vẫn đọc mỗi phần tử như `Animal`.

Nếu method có nhiệm vụ **thêm `Dog`** vào một collection, hướng wildcard phải đảo lại:

```java
static void addDefaultDog(List<? super Dog> target) {
    target.add(new Dog());
}
```

Một `List<Dog>`, `List<Animal>` hoặc `List<Object>` đều có thể là đích nhận `Dog`.

Quy tắc nhớ là **PECS — Producer Extends, Consumer Super**:

- **Producer `? extends T`**: nguồn cung cấp đối tượng mà ta có thể đọc như `T`.
- **Consumer `? super T`**: đích tiêu thụ/nhận đối tượng `T` mà ta có thể ghi vào.
- Nếu không cần đọc hoặc ghi kiểu cụ thể, `<?>` có thể đủ.
- Nếu phải vừa đọc vừa ghi theo cùng một kiểu được xác định, một **type parameter `<T>`** hoặc kiểu cụ thể thường thích hợp hơn một wildcard.

Bài này tập trung vào việc chọn **chữ ký phương thức API linh hoạt nhưng an toàn**, không đơn thuần thay `List<T>` bằng wildcard ở mọi vị trí. Các ví dụ hợp lệ và không hợp lệ đã được kiểm tra với Java 17.

## 2. Phân biệt quan hệ subtype của giá trị và của generic container

Một `Dog` là một `Animal`:

```java
Dog dog = new Dog();
Animal animal = dog;
```

Nhưng:

```java
List<Dog> dogs = new ArrayList<>();

// Không hợp lệ:
// List<Animal> animals = dogs;
```

Đây là **invariance** của parameterized types. Cùng quy tắc với `Collection<Dog>`, `Set<Dog>` và nhiều generic types khác.

### 2.1. Vì sao Java không cho gán `List<Dog>` sang `List<Animal>`?

Giả sử gán được:

```java
List<Dog> dogs = new ArrayList<>();
List<Animal> animals = dogs; // Giả sử được phép.
animals.add(new Cat());      // Cat cũng là Animal.
Dog value = dogs.get(0);     // Nhưng slot có thể là Cat!
```

Nếu Java cho phép, đoạn code có thể phá hợp đồng `List<Dog>`: danh sách đã giữ `Cat` nhưng một caller khác tin mỗi phần tử là `Dog`.

Java chặn từ compile time thay vì đợi đến lúc đọc.

### 2.2. Mảng Java khác Generic collections

Mảng tham chiếu trong Java có tính *covariant*:

```java
Dog[] dogs = new Dog[1];
Animal[] animals = dogs;

// Có thể biên dịch, nhưng ném ArrayStoreException:
// animals[0] = new Cat();
```

JVM có runtime component type để chặn phép gán `Cat` vào mảng thật `Dog[]`.

Generic `List<Dog>` thường không có runtime class riêng `List<Dog>` để JVM thực hiện store check theo type argument. Vì vậy Generic invariant giúp ngăn nguy cơ sai kiểu ngay tại source.

**Không suy từ mảng sang Generics.** `Animal[]` và `List<Animal>` có những quy tắc subtype khác nhau.

### 2.3. Wildcard mở linh hoạt nhưng cũng giới hạn thao tác

Một tham số `List<? extends Animal>` nhận được nhiều danh sách con của Animal, đổi lại caller method không được tự thêm bất kỳ `Dog` hay `Cat` tùy ý vào list đó.

Một tham số `List<? super Dog>` nhận được nhiều đích tương thích để thêm Dog, đổi lại khi đọc phần tử, method không thể giả định object nhận về luôn là Dog.

Đây không phải sự bất tiện ngẫu nhiên, mà là **đúng mức bảo đảm kiểu mà chữ ký có thể cung cấp**.

## 3. Producer Extends: đọc dữ liệu với `? extends T`

Xem API:

```java
static String firstName(List<? extends Animal> source) {
    return source.get(0).name();
}
```

Ở nơi gọi:

```java
List<Dog> dogs = List.of(new Dog());
List<Cat> cats = List.of(new Cat());

String fromDogs = firstName(dogs);
String fromCats = firstName(cats);
```

Ở bên trong method:

- `source` có thể thực là `List<Dog>`, `List<Cat>` hoặc `List<Animal>`.
- Bất cứ phần tử nào đọc được đều có thể dùng như `Animal`.
- Method không có quyền biết **kiểu phần tử cụ thể ẩn sau wildcard**.

### 3.1. Đọc dưới dạng bound là an toàn

```java
Animal first = source.get(0);
```

Nếu kiểu phần tử thật là Dog, Dog vẫn là Animal. Nếu kiểu thật là Cat, Cat cũng là Animal. Đọc như Animal giữ đúng hợp đồng chung.

Ngược lại:

```java
// Không biên dịch:
// Dog first = source.get(0);
```

Nếu nguồn thật là List<Cat>, biến Dog sẽ sai. Compiler không cho khẳng định như vậy.

### 3.2. Vì sao không thêm `new Dog()` vào producer?

```java
static void invalid(List<? extends Animal> source) {
    // Không biên dịch:
    // source.add(new Dog());
}
```

Giả sử caller đã truyền `List<Cat>`. Việc thêm Dog vào đó là sai. Compiler mô hình hóa dấu `?` bằng một **capture type** chưa biết, giới hạn trên bởi Animal; không thể chứng minh Dog hợp lệ với mọi capture type có thể có.

Đây là nguyên tắc **Producer Extends**: một nguồn được khai báo nhằm **cung cấp** phần tử, không dùng nó để đưa phần tử có subtype tùy chọn trở lại.

### 3.3. `null` là trường hợp đặc biệt

Về mặt type system, `source.add(null)` có thể được chấp nhận vì null có thể gán cho mọi reference type phù hợp. Nhưng:

- Danh sách có thể không cho phép null.
- Danh sách có thể immutable/unmodifiable.
- Việc thêm null hiếm khi phù hợp mục đích “producer”.

Đừng biến ngoại lệ cú pháp này thành lời khuyên sử dụng. **PECS nói về giá trị khác null và hợp đồng kiểu thông thường**, còn mutability/null policy là các quy tắc riêng.

### 3.4. `? extends` không có nghĩa `List` chỉ đọc vật lý

Một `List<? extends Animal>` có thể tham chiếu tới một `ArrayList<Dog>` mutable. Wildcard chỉ giới hạn **những thao tác kiểu an toàn qua tham chiếu đó**; nó không biến object thành immutable hoặc thread-safe.

Method vẫn có thể gọi các thao tác khác như `clear()` nếu collection cho phép, dù điều đó thường là hành vi bất ngờ đối với một API nguồn. Khi thiết kế một API chỉ đọc, vừa dùng wildcard đúng hướng vừa cam kết không đột biến nguồn theo contract.

## 4. Consumer Super: ghi giá trị với `? super T`

Giả sử bạn có Dog mới và cần đưa vào đích:

```java
static void addDefaultDog(List<? super Dog> target) {
    target.add(new Dog());
}
```

Các loại đích hợp lệ:

```java
List<Dog> dogs = new ArrayList<>();
List<Animal> animals = new ArrayList<>();
List<Object> objects = new ArrayList<>();
```

Cả ba có thể truyền vào phương thức trên:

```java
addDefaultDog(dogs);
addDefaultDog(animals);
addDefaultDog(objects);
```

Vì mọi danh sách đó đều có thể chứa một Dog theo hợp đồng kiểu đã khai báo.

### 4.1. `? super Dog` là một kiểu chưa biết nhưng không hẹp hơn Dog

Trong `List<? super Dog>`, type argument thật có thể là `Dog`, `Animal`, `Object`, hoặc một supertype phù hợp khác của Dog. Bất kể là loại nào, ghi Dog đều an toàn:

```java
target.add(new Dog());
```

Thêm một subtype của Dog (nếu Dog không final) cũng an toàn theo quan hệ subtype.

Nhưng thêm Animal chung hoặc Cat thì không:

```java
// Không an toàn và không biên dịch:
// target.add(new Animal());
// target.add(new Cat());
```

Nếu đích thực là List<Dog>, Animal/Cat không đáp ứng yêu cầu của danh sách.

### 4.2. Đọc từ `? super Dog` chỉ được bảo đảm là Object

```java
Object value = target.get(0);
```

được phép. Nhưng:

```java
// Không biên dịch:
// Dog value = target.get(0);
```

vì đích thực có thể là `List<Object>` và chứa một String, Integer hoặc bất kỳ Object khác từ trước.

**Consumer Super:** người viết method có quyền **đặt Dog vào**, nhưng không thể giả định **mọi phần tử có sẵn** đều là Dog.

### 4.3. Dùng super cho đầu ra có quyền ghi

Tham số đích còn có thể dùng interface tổng quát:

```java
static void addDog(Collection<? super Dog> target) {
    target.add(new Dog());
}
```

Nếu chỉ cần `add`, nhận `Collection` linh hoạt hơn nhận `List`. Caller có thể cung cấp `ArrayList<Animal>`, `HashSet<Animal>` hoặc các implementation `Collection` khác có `add` phù hợp.

**Nguyên tắc thiết kế bổ sung:** nhận interface **hẹp nhất về khả năng method cần**, không phải lúc nào cũng dùng `List` vì quen tay. Nếu cần truy cập theo index, `List` là hợp lý; nếu chỉ thêm, `Collection` thường đủ.

## 5. Bảng quyền đọc và ghi cần nhớ

Xét kiểu `Dog extends Animal`.

| Kiểu tham chiếu | Đọc phần tử an toàn thành | Ghi giá trị khác null có thể chứng minh an toàn |
| --- | --- | --- |
| `List<Dog>` | `Dog` | `Dog` và subtype của Dog |
| `List<Animal>` | `Animal` | `Animal` và subtype của Animal |
| `List<? extends Animal>` | `Animal` | Không ghi tùy ý Dog/Animal |
| `List<? super Dog>` | `Object` | `Dog` và subtype của Dog |
| `List<?>` | `Object` | Không ghi phần tử tùy ý |
| `List<Object>` | `Object` | Bất kỳ Object |

`List<? extends Animal>` không đồng nghĩa `List<Animal>`. Nó có thể tham chiếu tới danh sách Dog/Cat khác nhau.

`List<? super Dog>` cũng không đồng nghĩa `List<Dog>`. Nó có thể tham chiếu tới danh sách Animal hoặc Object.

### 5.1. Giới hạn nào do Generics, giới hạn nào do runtime collection?

Compiler có thể cho gọi `add` nhưng runtime object có thể không sửa được:

```java
List<Dog> fixed = List.of(new Dog());

// Cú pháp Generic hợp lệ, nhưng runtime ném
// UnsupportedOperationException nếu gọi:
fixed.add(new Dog());
```

Tương tự với `Collections.unmodifiableList` hoặc những collection implementation có giới hạn riêng. PECS **không cấp quyền sửa vật lý**; nó kiểm tra tính tương thích kiểu.

## 6. Hai hướng trên cùng một phương thức: copy từ producer sang consumer

Đây là mẫu tiêu biểu nhất của PECS:

```java
static <T> void appendAll(
        List<? extends T> source,
        Collection<? super T> target) {

    for (T item : source) {
        target.add(item);
    }
}
```

`source` là **producer** của T: ta đọc từng phần tử như T.

`target` là **consumer** của T: ta thêm từng phần tử T vào đó.

`<T>` liên kết hai bên để compiler xác minh được mọi phần tử lấy từ source đều hợp lệ cho target.

### 6.1. Caller truyền List<Dog> và List<Animal>

```java
List<Dog> dogs = new ArrayList<>();
List<Animal> animals = new ArrayList<>();

appendAll(dogs, animals);
```

Compiler có thể suy luận type variable `T` thích hợp với quan hệ subtype giữa Dog và Animal. Không cần ép `List<Dog>` thành `List<Animal>`.

### 6.2. Caller cũng có thể truyền List<Dog> và List<Object>

```java
List<Object> objects = new ArrayList<>();
appendAll(dogs, objects);
```

Hợp đồng cho phép vì Object cũng có thể nhận Dog. Điều này thể hiện cách wildcard giữ được sự linh hoạt tại **cả hai** phía.

### 6.3. Vì sao không viết hai tham số đều là `List<T>`?

```java
static <T> void rigid(List<T> source, List<T> target) {
    target.addAll(source);
}
```

Chữ ký này liên kết cả hai List với **cùng type argument T chính xác**. Khi caller có `List<Dog>` và `List<Animal>`, việc suy luận chung T bị hạn chế bởi invariance.

Chữ ký PECS:

```java
static <T> void flexible(
        List<? extends T> source,
        List<? super T> target) {
    target.addAll(source);
}
```

diễn đạt rõ quan hệ producer/consumer, nên linh hoạt hơn và an toàn.

### 6.4. Lưu ý về tên `append` so với `copy`

`appendAll` ở ví dụ **thêm** các phần tử vào cuối `Collection`. Nó có thể làm kích thước đích tăng.

Trong JDK, `Collections.copy` là một API khác: nó **ghi đè** các vị trí đã tồn tại trong danh sách đích, không tự nới danh sách để append. Đừng nhầm hai semantics khi đọc code.

### 6.5. Side effect và aliasing cần có trong contract

Nếu `source` và `target` trỏ tới cùng mutable collection, một vòng lặp `for-each` vừa duyệt vừa thêm có thể dẫn tới `ConcurrentModificationException` hoặc hành vi không mong đợi. Chữ ký PECS chỉ bảo đảm kiểu, không giải quyết aliasing.

Khi thiết kế API, hãy xác định có cho `source == target` hay không. Nếu muốn hỗ trợ, có thể tạo snapshot nguồn trước hoặc dùng thuật toán phù hợp, đồng thời cân nhắc chi phí bộ nhớ.

## 7. Ví dụ JDK: `Collections.copy` đã dùng PECS

Java 17 khai báo method tương đương:

```java
public static <T> void copy(
        List<? super T> dest,
        List<? extends T> src)
```

Tại đây:

- `src` (*source*) là producer, nên `? extends T`.
- `dest` (*destination*) là consumer, nên `? super T`.

Ví dụ:

```java
List<Integer> numbers =
        new ArrayList<>(List.of(3, 7));

List<Number> target =
        new ArrayList<>(List.of(0, 0, 0));

Collections.copy(target, numbers);
System.out.println(target); // [3, 7, 0]
```

`Collections.copy` thay các phần tử đích ở index tương ứng. **Điều kiện:** danh sách đích phải có kích thước ít nhất bằng danh sách nguồn. `new ArrayList<>(capacity)` chỉ đặt capacity, **không tạo sẵn các phần tử**, nên không thể dùng như một đích có kích thước đủ cho `Collections.copy`.

### 7.1. Khi muốn thêm thay vì ghi đè

Dùng:

```java
target.addAll(numbers);
```

hoặc một API `appendAll` rõ nghĩa.

Chữ ký instance method của `Collection<E>`:

```java
boolean addAll(Collection<? extends E> collection)
```

dùng wildcard `extends` ở **đối số nguồn**. Collection hiện tại đóng vai trò consumer của E; collection được truyền vào cung cấp các phần tử là E hoặc subtype.

### 7.2. Vì sao `Collections.copy` có type parameter T?

T liên kết **nguồn** và **đích**. Nếu chỉ có hai wildcard độc lập không ràng buộc như:

```java
// Không diễn đạt đủ quan hệ giữa hai loại:
// void copy(List<?> target, List<?> source)
```

thì compiler không thể an toàn lấy một object chưa biết kiểu từ source rồi ghi vào target chưa biết kiểu.

**Điểm mấu chốt:** PECS thường kết hợp wildcard **với một `<T>` liên kết hai tham số**, không phải loại bỏ type parameter.

## 8. Generic method để ghép nhiều nguồn có subtype khác nhau

Giả sử bạn muốn ghép danh sách Dog và Cat thành một danh sách Animal mới:

```java
static <T> List<T> concat(
        List<? extends T> left,
        List<? extends T> right) {

    List<T> result = new ArrayList<>(
            left.size() + right.size());
    result.addAll(left);
    result.addAll(right);
    return result;
}
```

Call:

```java
List<Dog> dogs = List.of(new Dog());
List<Cat> cats = List.of(new Cat());

List<Animal> combined = concat(dogs, cats);
```

Cả hai nguồn là producer của các subtype phù hợp với T; T được suy luận từ arguments và target type để tạo kết quả `List<Animal>`.

### 8.1. Vì sao kết quả nên là `List<T>`?

Method **tự tạo** một danh sách mới mà nó sở hữu. Nó biết đã chỉ thêm các giá trị phù hợp với T, nên có thể trả `List<T>`; caller sẽ dùng kết quả theo contract rõ ràng.

Trả `List<? extends T>` ở đây có thể khiến caller khó thêm phần tử T vào kết quả mà không mang lại lợi ích tương ứng. **Wildcard thường hữu ích ở vị trí tham số đầu vào**; kiểu trả về quá nhiều wildcard có thể làm API khó sử dụng.

### 8.2. Tránh che quan hệ kiểu sau khi gộp

```java
List<? extends Animal> combined = concat(dogs, cats);
```

có thể dùng nếu chỉ đọc, nhưng caller sau đó không được thêm Dog vào qua tham chiếu `? extends Animal`. Nếu method thật sự tạo một `List<Animal>` mới cho caller thao tác, khai báo:

```java
List<Animal> combined = concat(dogs, cats);
```

thường thể hiện ý định tốt hơn.

### 8.3. Kiểm tra mutable/immutable

`new ArrayList<>()` tạo một result có thể sửa. Nếu API hứa immutable snapshot, bạn có thể trả `List.copyOf(result)` khi dữ liệu không chứa null và contract phù hợp. `List.copyOf` có những giới hạn riêng như không chấp nhận null; việc “đổi sang unmodifiable” không nên làm mà không xét nghiệp vụ.

## 9. PECS trong `Function`, `Comparator` và những API khác

PECS không chỉ áp dụng cho danh sách. Nó còn xuất hiện trong các functional interfaces.

### 9.1. `Function<? super T, ? extends R>`

Xét generic transform:

```java
static <T, R> void transformAll(
        Iterable<? extends T> source,
        Function<? super T, ? extends R> mapper,
        Collection<? super R> target) {

    for (T item : source) {
        target.add(mapper.apply(item));
    }
}
```

Mỗi thành phần có một vai trò:

- `Iterable<? extends T> source`: nguồn cung cấp T.
- `Function<? super T, ? extends R> mapper`: **tiêu thụ** một T, **tạo ra** R.
- `Collection<? super R> target`: đích nhận R.

Đây là PECS nhìn ở nhiều lớp của một lời gọi.

Ví dụ:

```java
Function<Animal, String> names = Animal::name;

List<Dog> dogs = new ArrayList<>();
List<String> result = new ArrayList<>();

transformAll(dogs, names, result);
```

Dù mapper nhận `Animal`, nó xử lý Dog vì Dog là subtype của Animal. `? super T` trên input của Function thể hiện chính sự linh hoạt này.

### 9.2. Vì sao result của Function là `? extends R`?

Nếu một mapper trả subtype của R, caller vẫn có thể dùng giá trị đó như R. Wildcard `extends` ở **đầu ra** diễn đạt hành vi producer.

Ví dụ kiểu tưởng tượng: một `Function<Animal, Dog>` tạo Dog; nơi cần `Function<? super Animal, ? extends Animal>` có thể chấp nhận khi các ràng buộc tương thích. R được dùng làm kiểu chung của dữ liệu kết quả.

Đừng đảo thành `Function<? extends T, ? super R>` khi muốn tiêu thụ T và trả R: đầu vào `? extends T` có thể quá hẹp để nhận mọi T; đầu ra `? super R` có thể trả kiểu rộng hơn R, làm mất bảo đảm kết quả là R.

### 9.3. `Comparator<? super T>`

`List<E>.sort` có chữ ký:

```java
void sort(Comparator<? super E> comparator)
```

Với `List<Dog>`, bạn có thể sử dụng một `Comparator<Animal>` vì Comparator nhận các Dog như Animal.

```java
Comparator<Animal> byName =
        Comparator.comparing(Animal::name);

List<Dog> dogs = new ArrayList<>();
dogs.sort(byName);
```

Comparator là **consumer của đối tượng cần so sánh**: nó nhận hai E và tính thứ tự, nên `? super E` là hướng hợp lý.

### 9.4. `Comparable<? super T>` trong API chuẩn

JDK còn dùng kiểu:

```java
<T extends Comparable<? super T>>
```

trong những API như `Collections.max(Collection<? extends T>)`. Điều này cho phép một T được so sánh thông qua Comparable của T **hoặc của supertype phù hợp**.

Đây là ví dụ kết hợp:

- Bound của **type variable T**.
- Wildcard `? super T` ở interface tiêu thụ đối tượng để so sánh.
- `Collection<? extends T>` là nguồn giá trị để chọn phần tử lớn nhất.

Không nhất thiết phải học thuộc mọi chữ ký JDK, nhưng việc đọc API này giúp bạn nhìn ra PECS xuất hiện ở những nơi ngoài `List`.

## 10. `List<?>` khi không cần biết kiểu phần tử

Một API chỉ đếm phần tử:

```java
static int count(List<?> items) {
    return items.size();
}
```

không cần `<T>` vì method không liên kết loại phần tử với return value hoặc tham số khác.

`List<?>` tương đương “list của **một loại nào đó chưa biết**”, không phải “list của Object”:

```java
List<String> words = List.of("Java");
List<?> any = words; // Hợp lệ.

// Không hợp lệ:
// List<Object> objects = words;
```

### 10.1. Đọc qua `List<?>`

```java
Object first = any.get(0);
```

hợp lệ. Nhưng compiler không hứa `String` chỉ từ kiểu tĩnh List<?>.

### 10.2. Không thêm tùy ý vào `List<?>`

```java
// Không biên dịch:
// any.add("new");
```

Vì kiểu phần tử thật có thể là Integer, Dog, Cat hoặc loại khác. Dấu `?` không có bound cụ thể để chứng minh String có thể được thêm.

### 10.3. Khi nào `<T>` không tốt hơn `<?>`?

Một chữ ký:

```java
static <T> int countGeneric(List<T> items) {
    return items.size();
}
```

vẫn có thể hợp lệ, nhưng T không đem lại thông tin quan hệ mới. Khi method chỉ quan sát kích thước hoặc in đối tượng dưới dạng Object, `List<?>` thường đơn giản và chính xác hơn.

**Mẹo thiết kế:** chỉ khai báo type parameter khi **tên T cần liên kết ít nhất hai vị trí có ý nghĩa**, hoặc phục vụ bound/return type/constraint. Đây là heuristic, không phải cú pháp bắt buộc của Java.

## 11. Wildcard capture: khi compiler đặt tên cho dấu `?`

Một số thao tác an toàn với *cùng một danh sách* vẫn khó diễn đạt trực tiếp bằng `List<?>`.

Ví dụ muốn đổi chỗ hai phần tử của list, giữ nguyên kiểu thật của chúng:

```java
static void swapFirstTwo(List<?> items) {
    swapCaptured(items);
}

private static <T> void swapCaptured(List<T> items) {
    T first = items.get(0);
    items.set(0, items.get(1));
    items.set(1, first);
}
```

`swapCaptured` dùng một type parameter T để thể hiện rằng cả hai phần tử được đọc/ghi **trên cùng danh sách có cùng captured type**.

### 11.1. Vì sao không tự thêm đối tượng tùy ý?

Helper chỉ lấy giá trị đang thuộc list rồi ghi lại vào cùng list. Nó **không đưa một Dog bên ngoài vào List<?> có thể thật là List<Cat>**.

Đây là trường hợp wildcard capture giúp diễn đạt một thao tác an toàn mà `?` trần không cho compiler biết cách nối thông tin “cùng kiểu” giữa hai lệnh đọc/ghi.

### 11.2. Wildcard capture không vượt qua giới hạn subtype

```java
static void unsafe(List<?> items) {
    // items.add(new Dog()); // Vẫn không hợp lệ.
}
```

Việc bổ sung helper không cho phép tùy tiện thêm một object khác kiểu thực. Type variable trong helper chỉ đặt tên cho một kiểu chưa biết và giữ quan hệ đó nhất quán.

### 11.3. Cần mutable List và đủ phần tử

`swapCaptured` có thể ném `IndexOutOfBoundsException` nếu list ít hơn hai phần tử, hoặc `UnsupportedOperationException` nếu list không cho sửa.

PECS/capture giải quyết bài toán **kiểu tĩnh**, không kiểm tra length hoặc mutability. API production có thể kiểm tra `size() >= 2` và tài liệu hóa yêu cầu mutable.

## 12. Khi nào **không** dùng wildcard?

PECS là hướng dẫn cho phương thức **nhận** dữ liệu; lạm dụng wildcard ở mọi vị trí làm API kém rõ.

### 12.1. Phải vừa đọc vừa ghi các giá trị cùng T

```java
static <T> void replaceFirst(List<T> list, T replacement) {
    list.set(0, replacement);
}
```

`List<T>` liên kết kiểu của list và replacement. Đây thường là thiết kế thích hợp nếu method cần ghi một T do caller cung cấp và vẫn đọc T từ list.

Dùng `List<? extends T>` sẽ khó ghi replacement. Dùng `List<? super T>` lại không bảo đảm đọc ra T.

### 12.2. Trả về wildcard làm caller khó sử dụng

Một API:

```java
static List<? extends Animal> makeAnimals() {
    return List.of(new Dog(), new Cat());
}
```

có thể hợp lệ nếu chỉ muốn công bố danh sách đọc được. Nhưng nếu API thật sự tạo ra `List<Animal>` rõ ràng, return type:

```java
static List<Animal> makeAnimals() {
    return new ArrayList<>(List.of(new Dog(), new Cat()));
}
```

dễ dùng hơn khi caller cần thêm Animal.

Không chọn wildcard ở return type chỉ để “nâng tính generic”; nó có thể đẩy phức tạp về phía caller.

### 12.3. Không dùng wildcard để che thiếu mô hình domain

Nếu một method cần biết liệu đầu vào là danh sách Dog hay Cat để thực hiện hành động rất khác nhau, `List<? extends Animal>` không tự cung cấp runtime generic argument. Hãy dùng polymorphism trên Animal, strategy, visitor hoặc cấu trúc domain rõ thay vì cast `List<?>` về List<Dog> một cách unchecked.

### 12.4. Không nhầm `? extends` với immutability

`List<? extends T>` chỉ cung cấp contract hạn chế ở compile time, không đóng băng object thật. Khi cần không cho người khác sửa collection, dùng copy/unmodifiable view theo contract, cùng kiểm soát aliasing.

## 13. Ví dụ Java 17 hoàn chỉnh: PECS trong API thực tế

Tạo file **`PecsApiDemo.java`**:

```java
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

public class PecsApiDemo {
    static class Animal {
        private final String name;

        Animal(String name) {
            this.name = name;
        }

        String name() {
            return name;
        }

        @Override
        public String toString() {
            return getClass().getSimpleName()
                    + "(" + name + ")";
        }
    }

    static final class Dog extends Animal {
        Dog(String name) {
            super(name);
        }
    }

    static final class Cat extends Animal {
        Cat(String name) {
            super(name);
        }
    }

    static <T> void appendAll(
            List<? extends T> source,
            Collection<? super T> target) {
        for (T item : source) {
            target.add(item);
        }
    }

    static <T, R> void transformAll(
            Iterable<? extends T> source,
            Function<? super T, ? extends R> mapper,
            Collection<? super R> target) {
        for (T item : source) {
            target.add(mapper.apply(item));
        }
    }

    static <T> List<T> concat(
            List<? extends T> left,
            List<? extends T> right) {
        List<T> result = new ArrayList<>(
                left.size() + right.size());
        result.addAll(left);
        result.addAll(right);
        return result;
    }

    static void addDefaultDog(List<? super Dog> target) {
        target.add(new Dog("Bolt"));
    }

    static String firstName(
            List<? extends Animal> animals) {
        return animals.get(0).name();
    }

    static void swapFirstTwo(List<?> items) {
        swapCaptured(items);
    }

    private static <T> void swapCaptured(List<T> items) {
        T first = items.get(0);
        items.set(0, items.get(1));
        items.set(1, first);
    }

    public static void main(String[] args) {
        List<Dog> dogs = new ArrayList<>(
                List.of(new Dog("Rex"), new Dog("Amy")));
        List<Cat> cats = new ArrayList<>(
                List.of(new Cat("Miu")));

        List<Animal> animals = new ArrayList<>();
        List<Object> objects = new ArrayList<>();

        appendAll(dogs, animals);
        appendAll(dogs, objects);
        addDefaultDog(animals);

        System.out.println("animals=" + animals);
        System.out.println("objects=" + objects);
        System.out.println("first=" + firstName(dogs));

        List<Animal> combined = concat(dogs, cats);
        System.out.println("combined=" + combined);

        List<String> names = new ArrayList<>();
        Function<Animal, String> getName = Animal::name;
        transformAll(dogs, getName, names);
        System.out.println("names=" + names);

        Comparator<Animal> byName =
                Comparator.comparing(Animal::name);
        dogs.sort(byName);
        System.out.println("sorted=" + dogs);

        List<Integer> numbers = new ArrayList<>(
                List.of(3, 7));
        List<Number> numericTargets = new ArrayList<>(
                List.of(0, 0, 0));

        Collections.copy(numericTargets, numbers);
        System.out.println("copy=" + numericTargets);

        List<String> words = new ArrayList<>(
                List.of("one", "two"));
        swapFirstTwo(words);
        System.out.println("swapped=" + words);

        List<?> unknown = dogs;
        System.out.println("unknown-read=" + unknown.get(0));

        List<? super Dog> sink = animals;
        Object readBack = sink.get(0);
        System.out.println("super-read=" + readBack);
    }
}
```

Biên dịch với Java 17:

```bash
javac --release 17 -Xlint:all PecsApiDemo.java
java PecsApiDemo
```

Đã kiểm chứng: chương trình biên dịch thành công **không có warning** với `-Xlint:all` và cho stdout chính xác:

```text
animals=[Dog(Rex), Dog(Amy), Dog(Bolt)]
objects=[Dog(Rex), Dog(Amy)]
first=Rex
combined=[Dog(Rex), Dog(Amy), Cat(Miu)]
names=[Rex, Amy]
sorted=[Dog(Amy), Dog(Rex)]
copy=[3, 7, 0]
swapped=[two, one]
unknown-read=Dog(Amy)
super-read=Dog(Rex)
```

### 13.1. `animals` và `objects`

`appendAll(dogs, animals)` và `appendAll(dogs, objects)` đều hợp lệ nhờ liên kết `<T>` giữa producer và consumer. Sau đó `addDefaultDog(animals)` thêm `Dog(Bolt)`.

`List<Dog>` không cần bị ép hoặc copy thành `List<Animal>` **trước khi truyền**. Các giá trị được copy vào danh sách đích trong method vì đó là yêu cầu cụ thể của `appendAll`.

### 13.2. `first=Rex`

`firstName(List<? extends Animal>)` nhận `List<Dog>` và đọc một `Animal`, gọi `name()`. API không cần biết type argument chính xác của list.

### 13.3. `combined`

`concat` tạo một danh sách kết quả có thể chứa cả Dog và Cat. Caller khai báo `List<Animal>`, compiler suy luận T phù hợp. Không có sự gán sai `List<Dog>` sang `List<Animal>`; đây là **một collection mới**.

### 13.4. `names`

`Function<Animal,String>` có thể xử lý Dog vì đầu vào mapper là `? super T`. `transformAll` đọc Dog từ `Iterable<? extends T>` và ghi String vào `Collection<? super R>`.

### 13.5. `sorted`

`Comparator<Animal>` được chấp nhận bởi `List<Dog>.sort(Comparator<? super Dog>)`. Nó so sánh Dog thông qua hành vi đã định nghĩa trên Animal. Kết quả sắp xếp Amy trước Rex.

### 13.6. `copy=[3, 7, 0]`

`Collections.copy` **ghi đè** hai vị trí đầu của danh sách đích có ba phần tử. Không phải append: vị trí thứ ba giữ `0`.

### 13.7. `swapped=[two, one]`

Helper generic capture type của một `List<?>` cụ thể rồi đọc/ghi chính những phần tử cũ. Đây không phải thao tác thêm một String bất kỳ vào một List<?>.

### 13.8. `unknown-read` và `super-read`

`List<?> unknown` cho phép đọc như Object. `List<? super Dog> sink` cũng chỉ cho đọc như Object. Runtime object tình cờ là Dog trong ví dụ, nhưng compiler không thể suy ra `Dog` chỉ từ chữ ký `? super Dog`.

## 14. Bốn ví dụ cố ý không biên dịch

Hãy đặt **mỗi ví dụ trong tệp riêng** để quan sát đúng diagnostic, không chép các lỗi này vào `PecsApiDemo.java`.

### 14.1. `WrongInvariant.java`

```java
import java.util.List;

class WrongInvariant {
    static class Animal {
    }

    static class Dog extends Animal {
    }

    static void test(List<Dog> dogs) {
        List<Animal> animals = dogs;
    }
}
```

Chạy:

```bash
javac --release 17 WrongInvariant.java
```

Compiler báo:

```text
error: incompatible types:
List<Dog> cannot be converted to List<Animal>
```

Giải pháp nếu chỉ đọc:

```java
List<? extends Animal> animals = dogs;
```

Nếu cần một `List<Animal>` độc lập để thêm Cat:

```java
List<Animal> animals = new ArrayList<>(dogs);
```

Đoạn sửa cần import `ArrayList` nếu ở tệp riêng.

### 14.2. `WrongProducer.java`

```java
import java.util.List;

class WrongProducer {
    static class Animal {
    }

    static class Dog extends Animal {
    }

    static void test(List<? extends Animal> source) {
        source.add(new Dog());
    }
}
```

Chạy:

```bash
javac --release 17 WrongProducer.java
```

Compiler báo nội dung chính:

```text
error: incompatible types:
Dog cannot be converted to CAP#1
```

`CAP#1` là capture type chưa biết, có upper bound Animal. Nếu danh sách thật là `List<Cat>`, thêm Dog sẽ sai.

**Sửa:** nếu thực sự là đích nhận Dog, định nghĩa API nhận `List<? super Dog>`. Nếu đây là nguồn, đừng thêm phần tử vào nó.

### 14.3. `WrongConsumer.java`

```java
import java.util.List;

class WrongConsumer {
    static class Animal {
    }

    static class Dog extends Animal {
    }

    static void test(List<? super Dog> target) {
        Dog dog = target.get(0);
    }
}
```

Chạy:

```bash
javac --release 17 WrongConsumer.java
```

Compiler báo:

```text
error: incompatible types:
CAP#1 cannot be converted to Dog
```

Một `List<? super Dog>` có thể là `List<Object>`; phần tử hiện có không chắc là Dog. Dùng `Object dog = target.get(0)` nếu chỉ cần một Object, hoặc thiết kế chữ ký khác khi cần đọc đúng Dog.

### 14.4. `WrongWildcard.java`

```java
import java.util.List;

class WrongWildcard {
    static void test(List<?> input) {
        input.add("hello");
    }
}
```

Chạy:

```bash
javac --release 17 WrongWildcard.java
```

Compiler báo:

```text
error: incompatible types:
String cannot be converted to CAP#1
```

`List<?>` có type argument chưa biết. Nếu method cần ghi String, dùng `List<? super String>` hoặc `List<String>` theo khả năng đọc/ghi cần thiết.

Các trường hợp sai trên đã được kiểm tra trực tiếp bằng `javac --release 17`. Số dòng và tên capture variable có thể khác theo code/JDK, nhưng nguyên nhân type safety không thay đổi.

## 15. Thiết kế PECS cho API có nhiều thao tác

Một API thực thường có nhiều chức năng đọc/ghi khác nhau. Đừng cố ép tất cả vào **một** chữ ký wildcard quá tổng quát.

### 15.1. API chỉ đọc

```java
static double sum(
        Collection<? extends Number> numbers) {
    double result = 0;
    for (Number n : numbers) {
        result += n.doubleValue();
    }
    return result;
}
```

Ví dụ method minh họa cần import `Collection`. Nó đọc Number, không sửa tập dữ liệu. `? extends Number` cho caller đưa collection chứa Integer, Double và subtype Number khác.

### 15.2. API chỉ ghi

```java
static void emitDefaults(
        Collection<? super Integer> target) {
    target.add(1);
    target.add(2);
}
```

Nó có thể nhận `Collection<Integer>`, `Collection<Number>`, `Collection<Object>` khi những collection ấy có thể sửa.

### 15.3. API copy giữa nguồn và đích

```java
static <T> void transfer(
        Iterable<? extends T> source,
        Collection<? super T> target) {
    for (T value : source) {
        target.add(value);
    }
}
```

T liên kết loại phần tử đi qua cả hai phía. `Iterable` có thể phù hợp hơn `List` nếu chỉ cần vòng lặp.

### 15.4. API đọc và sửa theo cùng một kiểu

```java
static <T> void replaceFirst(
        List<T> values, T replacement) {
    values.set(0, replacement);
}
```

Không ép thành wildcard nếu T phải đồng nhất ở nơi đọc/ghi và giữa các tham số.

### 15.5. API chỉ cần kích thước

```java
static int sizeOf(Collection<?> values) {
    return values.size();
}
```

`<?>` diễn đạt chính xác vì không có quan hệ kiểu nào cần được đặt tên.

## 16. Wildcard ở return type và public APIs

Một nguyên tắc thiết kế hữu ích là **thường ưu tiên wildcard cho input parameters**, ít khi trả về wildcard nếu caller cần thao tác tiếp.

Ví dụ:

```java
static List<? extends Animal> getAnimals() {
    return List.of(new Dog(), new Cat());
}
```

Caller có thể duyệt Animal nhưng không dễ thêm Animal qua kiểu return đó.

Nếu method làm chủ collection và thực sự công bố danh sách Animal:

```java
static List<Animal> getAnimals() {
    List<Animal> result = new ArrayList<>();
    result.add(new Dog());
    result.add(new Cat());
    return result;
}
```

thì kiểu return `List<Animal>` có thể rõ ràng hơn.

Tuy vậy return wildcard **không luôn sai**. Trong những API cần biểu đạt một tập cụ thể chưa xác định hoặc bảo vệ contract đọc mà không cho caller ghi kiểu tùy ý, nó có thể phù hợp. Quyết định dựa trên nhu cầu caller, không theo một lệnh cấm cứng.

### 16.1. `? extends` không thực hiện defensive copy

Nếu trả thẳng một mutable list dưới kiểu `List<? extends Animal>`, caller vẫn có thể có alias khác sửa list. Hoặc chính caller có thể gọi `clear()` trên tham chiếu nếu object cho phép.

Nếu mục tiêu là tránh mutation hoặc giữ snapshot, dùng các biện pháp về ownership và unmodifiable copies thích hợp, không chỉ wildcard.

### 16.2. API với nhiều phiên bản generic

Khi API có một helper generic và một overload nhận `List<?>`, cần cân nhắc **erasure/name clash**. Hai overload chỉ khác type argument của cùng raw List có thể không được định nghĩa chung vì erasure trùng nhau.

Ví dụ cố tình sai:

```java
// Không thể cùng nằm trong một class:
// void inspect(List<String> values) {}
// void inspect(List<Integer> values) {}
```

Cả hai erase thành tham số `List`. Wildcard giúp linh hoạt, nhưng không cho phép overload methods chỉ khác generic type arguments đã bị xóa.

## 17. PECS và `Optional`, `Stream`, `Predicate`

Quy tắc hướng input/output còn xuất hiện trong các API không phải Collection.

### 17.1. `Optional.map`

Chữ ký có dạng:

```java
<U> Optional<U> map(
    Function<? super T, ? extends U> mapper)
```

Mapper **nhận T** (consumer) nên input có `? super T`; mapper **sinh U** (producer) nên output có `? extends U`.

Đây là chính mẫu Function của `transformAll`, chỉ khác container Optional.

### 17.2. `Stream.map`

Stream API cũng dùng `Function<? super T, ? extends R>` để cho phép lambda/method reference tiêu thụ supertype và sinh subtype của result type theo inference.

Ví dụ:

```java
List<String> names = dogs.stream()
        .map(Animal::name)
        .toList();
```

Phương thức trên giả định `dogs` là `List<Dog>` và có method name ở Animal. `Animal::name` nhận một Animal nên có thể áp dụng cho từng Dog.

### 17.3. `Predicate<? super T>`

Một predicate **tiêu thụ** T để kiểm tra:

```java
static <T> long countMatching(
        Iterable<? extends T> source,
        Predicate<? super T> check) {
    long count = 0;
    for (T value : source) {
        if (check.test(value)) count++;
    }
    return count;
}
```

Ví dụ này cần import `java.util.function.Predicate`. Nguồn là producer (`extends`), predicate là consumer (`super`).

### 17.4. Không đổi hướng wildcard theo tên interface

`Supplier<? extends T>` là producer vì phương thức `get()` **trả** T. `Consumer<? super T>` là consumer vì phương thức `accept` **nhận** T. `Comparator<? super T>` tiêu thụ đối tượng so sánh.

Hãy nhìn **cách interface dùng T**, không chỉ tên interface. Một `Function` vừa tiêu thụ input vừa tạo output nên có cả hai hướng.

## 18. Wildcard bounds và suy luận kiểu

Một phương thức PECS tổng quát:

```java
static <T> void appendAll(
        List<? extends T> from,
        Collection<? super T> to) {
    to.addAll(from);
}
```

khi gọi:

```java
appendAll(dogs, animals);
```

cần tìm một T cho phép:

- Elements của `dogs` đọc được dưới dạng T.
- `animals` nhận được T.

Compiler giải những ràng buộc này qua generic method inference.

### 18.1. Explicit type witness nếu cần

Trong một số trường hợp, bạn có thể viết:

```java
MyClass.<Animal>appendAll(dogs, animals);
```

`MyClass` là class chứa static method trong ví dụ minh họa. Chỉ rõ T=Animal có thể giúp giải thích ý định trong code khó suy luận, nhưng **không biến một phép gán sai thành đúng**.

Khi không cần, để Java inference thường giúp code gọn hơn.

### 18.2. Khi inference báo incompatible bounds

Nếu method nhận producer/consumer với kiểu không tương thích, compiler có thể báo:

```text
inference variable T has incompatible bounds
```

Lúc đó hãy ghi ra các quan hệ subtype cần thỏa và xác định ràng buộc nào xung đột. Ví dụ cố chuyển một `List<Cat>` vào một `List<Dog>` là không an toàn vì Cat không thuộc kiểu phần tử Dog.

Không thêm raw cast hoặc `@SuppressWarnings("unchecked")` chỉ để tắt compiler. Nếu cần chuyển đổi Cat thành Dog vì nghiệp vụ, hãy định nghĩa một **mapper** thật sự có return type Dog, thay vì ép Generics bỏ qua loại dữ liệu.

## 19. Không phải wildcard nào cũng có thể kết hợp tùy ý

### 19.1. Hai wildcard không tự có quan hệ với nhau

```java
static void broken(
        List<?> from, List<?> to) {
    // Không biên dịch:
    // to.add(from.get(0));
}
```

Mỗi dấu `?` có **capture type riêng**. Compiler không được giả định type argument ẩn của hai list giống nhau.

Giải pháp khi muốn copy an toàn là một `<T>` liên kết các phía:

```java
static <T> void copyOne(
        List<? extends T> from,
        List<? super T> to) {
    to.add(from.get(0));
}
```

### 19.2. `? extends Number` không phải `? super Number`

- `List<? extends Number>` có thể chứa Integer, Double, BigDecimal; đọc như Number.
- `List<? super Number>` có thể là List<Number> hoặc List<Object>; có thể thêm Number.

Đảo wildcard làm thay đổi hẳn chức năng của API. Một phương thức chỉ muốn tính tổng không cần `? super Number`. Một phương thức ghi Number vào đích không nên nhận `? extends Number`.

### 19.3. `List<Object>` không phải “nhận mọi list”

```java
static void inspect(List<Object> list) {
}
```

chỉ nhận những tham chiếu phù hợp `List<Object>`, không tự nhận `List<String>`. Nếu method chỉ quan sát mọi list, dùng `List<?>`; nếu cần đọc như một parent type, dùng `List<? extends Parent>`.

## 20. Những ngộ nhận thường gặp

**Ngộ nhận 1: `Dog extends Animal` kéo theo `List<Dog> extends List<Animal>`.** Sai: Generic parameterized types invariant.

**Ngộ nhận 2: `? extends T` là cách viết List chỉ đọc tuyệt đối.** Sai: nó hạn chế ghi phần tử T tùy ý; không bảo đảm object immutable hoặc không có alias sửa.

**Ngộ nhận 3: `? super Dog` đọc ra Dog được.** Sai: phần tử có thể tới từ `List<Object>`; đọc dưới kiểu an toàn chung `Object`.

**Ngộ nhận 4: `List<?>` chính là `List<Object>`.** Sai: `List<?>` nhận các danh sách với type arguments khác nhau; `List<Object>` không phải supertype của mọi `List<T>`.

**Ngộ nhận 5: Chỉ dùng PECS mà không cần `<T>`.** Sai: khi liên kết nguồn, đích, mapper và return type, type parameter `<T>` giúp các phần của chữ ký có chung quan hệ kiểu.

**Ngộ nhận 6: Mọi API nên dùng `? extends` ở đầu vào.** Sai: nếu đầu vào là đích để thêm T, phải cân nhắc `? super T`.

**Ngộ nhận 7: `? super T` có nghĩa cho phép thêm mọi supertype của T.** Sai: method có thể thêm T hoặc subtype; không được tùy ý thêm Object/Animal khi đích thực có thể là List<Dog>.

**Ngộ nhận 8: PECS làm copy dữ liệu hoặc bảo đảm không mutation.** Sai: wildcard chỉ giới hạn compile-time typing. Side effects, aliasing, null và mutability cần contract riêng.

**Ngộ nhận 9: `Collections.copy` có nghĩa append.** Sai: nó ghi đè các phần tử ở index, đích phải có đủ kích thước.

**Ngộ nhận 10: `Comparator<Animal>` không dùng được với `List<Dog>`.** Sai: `List.sort` nhận `Comparator<? super E>` nên Comparator của Animal có thể so sánh Dog.

**Ngộ nhận 11: Wildcard capture là mẹo bỏ qua type safety.** Sai: helper đặt tên cho một captured type để bảo toàn quan hệ, không làm mọi object khác kiểu có thể được thêm vào list.

**Ngộ nhận 12: Chỉ cần compiler chấp nhận `add` là collection chắc chắn cho sửa.** Sai: runtime collection có thể unmodifiable hoặc từ chối null.

## 21. Checklist chọn wildcard theo vai trò của tham số

Khi thiết kế một method public, hãy hỏi lần lượt:

1. **Method chỉ cần đọc các phần tử như T?** Dùng `Collection<? extends T>` hoặc interface phù hợp.
2. **Method chỉ cần đưa các phần tử T vào đích?** Dùng `Collection<? super T>`.
3. **Method chỉ cần size/duyệt Object?** Dùng `Collection<?>` thay vì khai báo T không cần thiết.
4. **Method cần liên kết kiểu giữa nhiều tham số?** Khai báo `<T>` và đặt wildcard đúng hướng cho từng tham số.
5. **Method cần vừa đọc và ghi trên cùng cấu trúc theo T?** Xem `Collection<T>`/`List<T>` hoặc capture helper.
6. **Có cần list semantics thật không?** Nếu không, cân nhắc `Iterable`/`Collection`.
7. **Return type có thể giữ một `List<T>` rõ ràng không?** Tránh wildcard trả về gây khó khăn cho caller khi không cần.
8. **Dữ liệu có null hoặc collection unmodifiable không?** Viết contract và kiểm thử độc lập với Generics.
9. **Nguồn và đích có thể alias cùng object không?** Đánh giá side effect và modification during iteration.
10. **Cần conversion thực sự giữa hai loại không có subtype relation?** Dùng mapper/adapter, không ép cast unchecked.
11. **Có compiler warning về raw/unchecked types không?** Sửa nguồn type safety, không tự động suppress.
12. **Có test cho các trường hợp subtype, supertype và lỗi compile không?** Đảm bảo API thật sự nhận được các input mong muốn mà vẫn từ chối sai kiểu.

### 21.1. Quy trình review nhanh

Một reviewer có thể đọc chữ ký:

```java
static <T, R> void transformAll(
        Iterable<? extends T> source,
        Function<? super T, ? extends R> mapper,
        Collection<? super R> target)
```

và hỏi:

- `source` có đúng producer của T không?
- `mapper` có nhận T/supertype và trả R/subtype không?
- `target` có nhận R không?
- Có method nào sửa source hoặc lộ mutable alias trái hợp đồng không?
- Return/exception semantics có rõ không?

Nếu câu trả lời nhất quán, chữ ký PECS đang phản ánh đúng luồng dữ liệu.

## 22. Bài tập có đáp án

### Bài 1. Nhận `List<Dog>` để đọc Animal

Viết kiểu tham số của method đọc tên các Animal mà caller có thể truyền `List<Dog>` hoặc `List<Cat>`.

**Đáp án:**

```java
static void printAll(List<? extends Animal> source) {
    for (Animal value : source) {
        System.out.println(value.name());
    }
}
```

Vì source là producer của Animal, dùng `? extends Animal`.

### Bài 2. Thêm Dog vào nhiều kiểu list

Muốn nhận `List<Dog>`, `List<Animal>` và `List<Object>` để thêm Dog, dùng kiểu nào?

**Đáp án:**

```java
static void addDog(List<? super Dog> target) {
    target.add(new Dog());
}
```

Đích là consumer của Dog.

### Bài 3. Vì sao đọc một Dog từ `List<? super Dog>` không an toàn?

**Đáp án:** Tham chiếu có thể thật là `List<Object>` đã chứa dữ liệu không phải Dog. Kiểu đọc an toàn mà compiler bảo đảm là Object, không phải Dog.

### Bài 4. Sửa method copy cứng nhắc

```java
static <T> void copy(List<T> src, List<T> dest) {
    dest.addAll(src);
}
```

Làm sao cho phép copy Dog sang List<Animal>?

**Đáp án:**

```java
static <T> void copy(
        List<? extends T> src,
        List<? super T> dest) {
    dest.addAll(src);
}
```

Source producer (`extends`), destination consumer (`super`).

### Bài 5. `List<?>` và `List<Object>` có giống nhau không?

**Đáp án:** Không. `List<?>` có thể tham chiếu tới `List<String>`, `List<Integer>` và nhiều kiểu khác; `List<Object>` không phải supertype của tất cả `List<T>`.

### Bài 6. `Collections.copy` có thêm mới các phần tử vào danh sách đích không?

**Đáp án:** Không. Nó thay các phần tử theo index và yêu cầu đích có kích thước đủ. Nếu muốn append, dùng `addAll` hoặc API chuyên thêm phần tử.

### Bài 7. Vì sao có `Comparator<? super E>` trong `List.sort`?

**Đáp án:** Comparator nhận các phần tử E để so sánh, tức là **consumer** của E. Comparator của supertype như Animal có thể so sánh các Dog, nên `? super E` phù hợp.

### Bài 8. Vì sao một `Function` vừa có `super` vừa có `extends`?

**Đáp án:** Function vừa **tiêu thụ input** T vừa **sản xuất output** R, nên input `? super T` và output `? extends R`.

### Bài 9. Hai `List<?>` có thể copy qua nhau trực tiếp không?

**Đáp án:** Không phải bằng cách tùy ý `to.add(from.get(0))`, vì hai wildcard có capture types độc lập. Cần một type parameter liên kết nguồn/đích qua `? extends T` và `? super T`, hoặc một helper capture khi thao tác trên cùng nguồn phù hợp.

### Bài 10. Khi nào dùng `List<T>` thay vì `List<? extends T>`?

**Đáp án:** Khi method cần quan hệ kiểu chính xác để **đọc lẫn ghi T trên cùng list**, hoặc cần caller cung cấp một T và lưu vào list đó. `List<? extends T>` không cho thêm T tùy ý vì type argument thật có thể hẹp hơn T.

### Bài 11. `List<? extends Animal>` có chắc immutable không?

**Đáp án:** Không. Nó có thể là mutable `ArrayList<Dog>`; wildcard chỉ giới hạn thao tác theo generic type, không chặn mọi mutation hoặc alias.

### Bài 12. Compiler từ chối đưa Cat vào `List<? super Dog>` vì sao?

**Đáp án:** Đích có thể là `List<Dog>`, nên Cat không tương thích. `? super Dog` bảo đảm an toàn khi thêm Dog/subtype của Dog, không phải mọi Animal.

## 23. Tổng kết

**PECS là công cụ thiết kế đầu vào linh hoạt có kiểm tra kiểu**, không phải mẹo ghi `? extends`/`? super` tùy ý:

- **Producer Extends**: `Collection<? extends T>` cho phép caller cung cấp collections có phần tử T hoặc subtype; đọc được như T.
- **Consumer Super**: `Collection<? super T>` cho phép caller cung cấp collections có thể nhận T; thêm T an toàn.
- **Kết hợp `<T>`** để liên kết source/target: `copy(List<? extends T>, List<? super T>)`.
- **`Function<? super T, ? extends R>`** vừa tiêu thụ T, vừa sinh R; `Comparator<? super T>` tiêu thụ T để so sánh.
- **`<?>`** thích hợp khi không cần nói đến kiểu phần tử cụ thể; wildcard capture helper thích hợp khi cần giữ nhất quán một kiểu chưa biết.
- **`List<T>`** vẫn là lựa chọn đúng khi method phải vừa đọc vừa ghi chính T.

Dù compiler xác minh PECS, contract ứng dụng vẫn phải quy định kích thước, null, mutability, ownership và aliasing. Một API tốt chọn **kiểu tham số rộng đủ để nhận đầu vào hợp lệ nhưng không rộng đến mức mất bảo đảm**, đồng thời trả kiểu kết quả dễ sử dụng cho caller. Kết hợp quy tắc này với các ví dụ Java 17 và kiểm thử compiler sẽ giúp bạn đọc và thiết kế các Generic APIs rõ ràng, linh hoạt và an toàn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="generic-api-design-005">Tránh tham số kiểu chỉ xuất hiện một lần mà không tạo quan hệ cần thiết</a>

<details>
<summary>Xem chi tiết</summary>

# Tránh tham số kiểu chỉ xuất hiện một lần mà không tạo quan hệ cần thiết

## Mục tiêu bài học

Khi thiết kế Java Generic API, một lỗi thường gặp là khai báo `<T>` cho mọi phương thức nhìn thấy một kiểu generic, kể cả khi `T` không giúp ràng buộc bất kỳ thông tin nào. Ví dụ:

```java
static <T> void printValues(List<T> values) {
    for (T value : values) {
        System.out.println(value);
    }
}
```

Method này chỉ **đọc rồi in** phần tử. Nếu không cần biết chính xác `T`, chữ ký thường rõ hơn khi viết:

```java
static void printValues(List<?> values) {
    for (Object value : values) {
        System.out.println(value);
    }
}
```

**Nguyên tắc:** Chỉ đặt tên cho một type variable `T` khi tên đó mang thông tin có ích: liên kết nhiều vị trí kiểu, giới hạn kiểu một cách cần thiết, diễn đạt kiểu kết quả theo đầu vào, hoặc cho phép một thao tác nội bộ an toàn mà wildcard bên ngoài không biểu diễn trực tiếp được. Nếu `T` chỉ xuất hiện một lần và không tạo ràng buộc cần thiết, một wildcard hoặc một supertype thông thường thường dễ đọc hơn.

Tuy nhiên, đây là **quy tắc thiết kế (heuristic), không phải lệnh cấm tuyệt đối**. Có ngoại lệ đáng kể: wildcard capture helper, generic factory trả collection rỗng, type inference dựa trên return target, và những type bounds quan trọng. Không nên thay cơ học mọi `<T>` xuất hiện một lần bằng `?`.

Sau bài này, bạn có thể:

- Phân biệt tham số kiểu **có ý nghĩa ràng buộc** với một tham số chỉ làm phức tạp chữ ký.
- Chọn đúng giữa `Object`, `List<?>`, `List<? extends Number>`, `List<? super T>` và `List<T>`.
- Giải thích vì sao `<T> void print(List<T>)` thường là quá mức, còn `<T> void append(List<? super T>, T)` lại có quan hệ kiểu thực sự.
- Nhận ra các trường hợp `T` có mặt ở **đầu vào và đầu ra**, trong callback, hoặc trong bounds `Comparable<? super T>`.
- Hiểu tại sao `<T> List<T> emptyTyped()` vẫn có ích dù không nhận `T` từ tham số đầu vào.
- Hiểu wildcard capture: API public `List<?>`, implementation private dùng `<T>` để đọc rồi ghi lại **cùng một kiểu phần tử chưa biết**.
- Biết những rủi ro khi đổi chữ ký public API: source compatibility, generic reflection metadata, overload/override và binary descriptors.
- Kiểm chứng ví dụ Java 17 bằng `javac`/`java`/`javap`.

Bài thuộc chủ đề **Thiết kế API** trong Java Core — Generics, dành cho Java 17+. Các chương trình đầy đủ đã được chạy thực tế với `javac -Xlint:all`; những đoạn trích khác có ghi chú về ngữ cảnh và khả năng biên dịch.

## 1. Type parameter là một biến biểu thị **quan hệ kiểu**

Trong generic method:

```java
static <T> T identity(T input) {
    return input;
}
```

cùng một `T` xuất hiện ở tham số và return type. Nếu caller truyền `String`, compiler có thể suy ra kiểu kết quả liên quan với kiểu đầu vào:

```java
String s = identity("Java");
Integer n = identity(42);
```

Ý nghĩa của `T` không chỉ là “method dùng được với nhiều kiểu”. Nó diễn đạt **giá trị đầu ra có cùng kiểu tĩnh suy luận được từ giá trị đầu vào**.

Một hàm không generic:

```java
static Object identityObject(Object input) {
    return input;
}
```

có thể trả chính object ấy, nhưng kiểu return chỉ là `Object`. Caller muốn lấy `String` thường phải cast hoặc dùng logic khác. Như vậy `identity(T)` có quan hệ cần giữ, không nên thay `T` bằng `Object`.

Đối chiếu với method:

```java
static <T> void display(T value) {
    System.out.println(value);
}
```

Ở đây nếu method chỉ đưa value vào `println(Object)`, không có thêm tham số hay kết quả cần ràng buộc, ta có thể viết đơn giản:

```java
static void display(Object value) {
    System.out.println(value);
}
```

Cả hai minh họa cùng ý định “nhận một object và in”. Sự khác biệt trong overload/type inference có thể tồn tại ở một API phức tạp; hãy đánh giá chữ ký cụ thể, nhưng **generic `T` không thêm bảo đảm thiết thực** trong implementation đơn giản này.

## 2. Đọc vai trò của kiểu trước khi đếm số lần xuất hiện

Một bảng khởi đầu:

| Chữ ký | `T` có tạo quan hệ thiết yếu không? | Thiết kế gợi ý |
| --- | --- | --- |
| `<T> void print(List<T> data)` | Không, nếu chỉ đọc dưới dạng `Object` | `void print(List<?> data)` |
| `<T> boolean containsNull(List<T> data)` | Không, nếu chỉ quan sát phần tử | `boolean containsNull(List<?> data)` |
| `<T extends Number> double sum(List<T> xs)` | Thường không, nếu chỉ cần các `Number` | `double sum(List<? extends Number> xs)` |
| `<T> T identity(T x)` | Có: input → return | Giữ `<T>` |
| `<T> void append(List<? super T> dst, T value)` | Có: dst chấp nhận value | Giữ `<T>` |
| `<T> long countIf(List<? extends T> src, Predicate<? super T> test)` | Có: predicate phải nhận phần tử nguồn | Giữ `<T>` |
| `<T> List<T> repeat(T x, int n)` | Có: kiểu phần tử kết quả theo `x` | Giữ `<T>` |
| `<T> List<T> emptyTyped()` | Có ý nghĩa với target typing dù chỉ một vị trí xuất hiện | Giữ được `<T>` |
| `private <T> void swapCaptured(List<T> xs,...)` | Có ý nghĩa ở **thân phương thức** qua wildcard capture | Không thay máy móc |

Cần nhìn **hợp đồng public + cách dùng trong body + mục tiêu suy luận kiểu**. Đếm ký tự `T` là một tín hiệu code review, không phải bằng chứng đủ để tự động refactor.

## 3. Vì sao `List<?>` thường đúng khi chỉ đọc?

Java generics **invariant**. Dù `String extends Object`:

```java
List<String> names = List.of("Lan", "Minh");
// List<Object> objects = names; // Lỗi compile time.
```

`List<String>` không phải subtype của `List<Object>`. Nếu chữ ký nhận:

```java
static void print(List<Object> values)
```

thì method **không** nhận trực tiếp biến `List<String>` hoặc `List<Integer>`.

Nếu method chỉ cần duyệt, không đòi ràng buộc giữa hai vị trí kiểu:

```java
static void print(List<?> values) {
    for (Object value : values) {
        System.out.println(value);
    }
}
```

thì chấp nhận an toàn `List<String>`, `List<Integer>`, `List<Dog>` và nhiều loại list khác.

Dấu `?` nghĩa là “kiểu phần tử nào đó mà caller không cần công khai tên”; compiler **không** giả định đó là `Object` để cho ghi tùy tiện. Khi đọc, giá trị có thể dùng như `Object`.

**Quan trọng:** `List<?>` không đồng nghĩa một list bất biến. Tùy implementation, các thao tác như `clear()`, `remove(int)` hay `iterator().remove()` có thể sửa cấu trúc. Điều compiler hạn chế là việc **thêm một giá trị non-null tùy ý** vì không biết element type thật.

Không nên gọi `List<?>` là “read-only list” tuyệt đối. Nó là **góc nhìn có kiểu phần tử chưa xác định**, hữu ích cho các thao tác quan sát.

## 4. `Object` hay wildcard? Đừng coi chúng là thay thế tương đương

Với một giá trị đơn lẻ, không cần quan hệ kiểu:

```java
static void log(Object event)
```

rõ ràng hơn `static <T> void log(T event)` khi body chỉ gọi `String.valueOf` hoặc `System.out.println`.

Nhưng đối với **container**, `List<Object>` khác hẳn `List<?>`:

```java
static void inspect(List<?> source) {
    for (Object x : source) System.out.println(x);
}
```

chấp nhận nhiều `List<E>`, còn:

```java
static void inspectObjects(List<Object> source) {
    for (Object x : source) System.out.println(x);
}
```

chỉ chấp nhận các tham chiếu phù hợp `List<Object>` (và subtype theo quan hệ class/type parameter tương ứng). Nó không chấp nhận `List<String>` trực tiếp vì invariance.

Một quy tắc nhanh:

- **Một object tự do:** `Object` nếu không có ràng buộc kiểu khác.
- **Một cấu trúc generic có phần tử không cần gọi tên:** `List<?>`, `Collection<?>`, `Class<?>`, v.v.
- **Nguồn chỉ được đọc dưới supertype xác định:** `List<? extends Number>`.
- **Có liên hệ giữa nhiều vị trí kiểu:** type variable `T`.

## 5. Nếu chỉ đọc `Number`, hãy để wildcard diễn đạt điều đó

Thiết kế thường thấy:

```java
static <T extends Number> double sum(List<T> source) {
    double total = 0;
    for (T n : source) total += n.doubleValue();
    return total;
}
```

Trong method này, caller không nhận lại một giá trị có kiểu `T`, cũng không có tham số thứ hai bị ràng buộc bởi `T`. Method chỉ cần `Number.doubleValue()`.

Có thể viết:

```java
static double sum(List<? extends Number> source) {
    double total = 0;
    for (Number n : source) total += n.doubleValue();
    return total;
}
```

Nó nhận `List<Integer>`, `List<Double>` hoặc danh sách subtype khác của `Number`, và công bố rõ **cần một nguồn cung cấp các Number**.

Không nên thay thành `List<Number>` nếu vẫn muốn nhận `List<Integer>`, vì Java generic invariance. Không nên dùng raw `List` rồi cast từng phần tử nếu wildcard đã biểu diễn được ý định.

Với dữ liệu không đáng tin, `List<? extends Number>` là **cam kết compile time của tham chiếu nguồn**, không phải cơ chế validate một raw collection đã bị heap pollution. Đó là một vấn đề khác cần xử lý tại ranh giới dữ liệu.

## 6. Chương trình A — API tối giản và API cần quan hệ kiểu

Tạo tệp **`ApiSignatureDemo.java`**:

```java
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public class ApiSignatureDemo {
    static void printValues(List<?> source) {
        for (Object value : source) {
            System.out.println("item: " + value);
        }
    }

    static boolean containsNull(List<?> source) {
        for (Object value : source) {
            if (value == null) return true;
        }
        return false;
    }

    static double sum(List<? extends Number> source) {
        double total = 0;
        for (Number number : source) {
            total += number.doubleValue();
        }
        return total;
    }

    static <T> long countIf(
            List<? extends T> source,
            Predicate<? super T> predicate) {
        long count = 0;
        for (T value : source) {
            if (predicate.test(value)) count++;
        }
        return count;
    }

    static <T> void append(List<? super T> target, T value) {
        target.add(value);
    }

    static <T> List<T> repeat(T value, int times) {
        if (times < 0) {
            throw new IllegalArgumentException("times");
        }
        List<T> result = new ArrayList<>();
        for (int i = 0; i < times; i++) {
            result.add(value);
        }
        return List.copyOf(result);
    }

    public static void main(String[] args) {
        List<String> words =
                List.of("Java", "Generic", "API");

        printValues(List.of("A", "B"));
        System.out.println("null: " + containsNull(words));
        System.out.println("sum: " + sum(List.of(2, 3.5)));

        Predicate<CharSequence> longText =
                s -> s.length() >= 4;
        System.out.println("long words: "
                + countIf(words, longText));

        List<Number> numbers = new ArrayList<>();
        append(numbers, Integer.valueOf(10));
        append(numbers, Double.valueOf(2.5));
        System.out.println("target: " + numbers);

        System.out.println("repeat: " + repeat("ok", 3));
    }
}
```

Biên dịch và chạy:

```bash
javac -Xlint:all ApiSignatureDemo.java
java ApiSignatureDemo
```

**Stdout đã kiểm chứng trên Java 17:**

```text
item: A
item: B
null: false
sum: 5.5
long words: 2
target: [10, 2.5]
repeat: [ok, ok, ok]
```

Chương trình biên dịch không cảnh báo dưới `-Xlint:all`.

### Phân tích nhóm không cần `<T>`

`printValues(List<?>)` không mang một kiểu phần tử riêng vào tham số khác hoặc return type: đọc như `Object` là đủ.

`containsNull(List<?>)` chỉ kiểm tra từng phần tử và trả `boolean`, hoàn toàn không cần đặt tên type parameter.

**Một chi tiết Java đáng nhớ:** không nên triển khai `containsNull` bằng cách giả định `source.contains(null)` luôn trả boolean. Một số implementation list từ `List.of(...)` từ chối thao tác tìm `null` và có thể ném `NullPointerException`. Bởi vậy ví dụ **duyệt phần tử** rồi so `== null` để có kết quả rõ theo hợp đồng của method tự viết.

`sum(List<? extends Number>)` chỉ cần đọc phần tử như `Number`, nên bound có thể nằm ở wildcard mà không phải một type variable được đặt tên.

### Phân tích nhóm **thật sự cần** `<T>`

`countIf` nối hai tham số:

```java
List<? extends T> source
Predicate<? super T> predicate
```

Source cung cấp những giá trị dùng như `T`, còn predicate có thể **tiêu thụ** các `T` ấy. Ví dụ `List<String>` phối hợp an toàn với `Predicate<CharSequence>` vì predicate nhận một supertype của `String`. Nếu bỏ `T`, phương thức mất cách diễn đạt ràng buộc giữa loại phần tử và loại callback.

`append` nối loại phần tử `T` cần ghi và một target chấp nhận `T`:

```java
List<? super T> target
T value
```

Với `List<Number>`, `Integer` và `Double` đều là lựa chọn an toàn trong lời gọi phù hợp. Không được tùy tiện thay `T` ở đích thành `?`, vì qua `List<?>` compiler không cho thêm các giá trị non-null không biết kiểu.

`repeat(T,int)` nối input kiểu `T` với output `List<T>`: caller nhận danh sách chứa đúng loại đã suy luận từ `value`. Đây là một cam kết đầu vào–đầu ra có giá trị.

**Chính sách `null`:** `repeat` dùng `List.copyOf`, nên không hỗ trợ `null` làm phần tử. Đây là đặc điểm của implementation ví dụ, độc lập với việc chọn `T` hay wildcard.

## 7. Vì sao `countIf` cần `? super T` ở callback?

Đọc lời gọi trong chương trình A:

```java
Predicate<CharSequence> longText =
        value -> value.length() >= 4;

long result = countIf(words, longText);
```

`words` là `List<String>`. Mỗi `String` có thể dùng như `CharSequence`. Nếu predicate chấp nhận mọi `CharSequence`, nó chắc chắn đánh giá được mọi `String`.

Chữ ký:

```java
static <T> long countIf(
        List<? extends T> source,
        Predicate<? super T> predicate)
```

là phiên bản **PECS** có quan hệ:

- `source` **sản xuất** giá trị `T` → `? extends T`.
- `predicate` **tiêu thụ** giá trị `T` → `? super T`.

Cặp `T` không phải trang trí; nó buộc hai tham số phối hợp đúng. Đây là ví dụ tiêu biểu về một generic method có ích dù không trả `T`.

### Khi nào có thể không cần `T` cho callback?

Nếu yêu cầu đơn giản là “đọc `Object` và in”, bạn không cần callback generic phức tạp:

```java
static void dump(List<?> values) {
    for (Object value : values) System.out.println(value);
}
```

Nhưng nếu caller đưa callback typed, hãy giữ ràng buộc phù hợp thay vì hạ tất cả về `Object` rồi dùng unchecked cast.

## 8. Việc dùng một type variable có thể ở nhiều **vị trí logic**, không phải chỉ nhiều tham số

Không chỉ quan hệ giữa hai method parameters mới đáng giữ. Những nơi sau cũng là lý do hợp lệ:

**Input → return:**

```java
static <T> T identity(T value) {
    return value;
}
```

**Input → element type của collection trả về:**

```java
static <T> List<T> singleton(T value) {
    return List.of(value);
}
```

**Input từ nhiều nguồn → một loại đích:**

```java
static <T> void copyInto(
        java.util.Collection<? extends T> source,
        java.util.Collection<? super T> destination) {
    for (T item : source) destination.add(item);
}
```

Đây là các đoạn method minh họa có thể đặt trong một class với imports phù hợp. Trong `copyInto`, `T` liên kết producer và consumer; đó là lý do không thể đơn giản loại bỏ type variable nếu muốn compiler giữ cùng hợp đồng linh hoạt.

**Một bound tham chiếu lại `T`** cũng có ý nghĩa, chẳng hạn:

```java
<T extends Comparable<? super T>>
```

nói rằng mỗi `T` có thể được so sánh với giá trị loại `T` theo giao diện `Comparable` phù hợp. Nó không giống một `<T>` chỉ đặt tên cho một đối số mà method không dùng quan hệ nào.

## 9. Đừng quên ngoại lệ lớn: wildcard capture helper

Quy tắc “`T` xuất hiện một lần thì dùng `?`” **không được áp dụng máy móc** vào generic helper nhằm **capture một wildcard**.

Ví dụ caller truyền `List<?>`; bạn cần hoán đổi hai phần tử. Vì không biết kiểu phần tử cụ thể, Java từ chối lời ghi tùy ý như:

```java
static void badSwap(List<?> items, int i, int j) {
    // items.set(i, items.get(j)); // Lỗi vì hai biểu thức bị capture độc lập.
}
```

Đoạn trên là minh họa đã comment thao tác nguy hiểm. Khi nhìn ở mức API public, `List<?>` vẫn là hợp đồng phù hợp: caller không cần biết kiểu phần tử. Nhưng **bên trong** cần ràng buộc giá trị đọc ra với kiểu phần tử của **cùng list**.

Giải pháp là helper có type variable riêng:

```java
static void swapAny(List<?> items, int i, int j) {
    swapCaptured(items, i, j);
}

private static <T> void swapCaptured(
        List<T> items, int i, int j) {
    T first = items.get(i);
    items.set(i, items.get(j));
    items.set(j, first);
}
```

Tại phương thức public, caller thấy `List<?>`. Khi `swapCaptured(items, ...)` được gọi, compiler dùng **wildcard capture conversion** để gán tên `T` cho đúng một kiểu phần tử chưa biết nhưng nhất quán ở **lời gọi helper ấy**.

Tại helper:

- `items.get(i)` có kiểu `T`.
- `items.set(i, ...)` nhận một giá trị kiểu `T`.
- Giá trị đã lấy từ **chính `items`** có thể được ghi trở lại an toàn.

Dù trong **chữ ký** helper `T` có thể chỉ xuất hiện ở `List<T>`, **body** dùng `T` để bảo toàn quan hệ đọc–ghi. Đây là một **ngoại lệ có ý nghĩa** đối với heuristic “xuất hiện một lần”.

## 10. Chương trình B — Wildcard capture trong implementation

Tạo tệp **`WildcardCaptureDemo.java`**:

```java
import java.util.ArrayList;
import java.util.List;

public class WildcardCaptureDemo {
    static void swapAny(List<?> items, int i, int j) {
        swapCaptured(items, i, j);
    }

    private static <T> void swapCaptured(
            List<T> items, int i, int j) {
        T first = items.get(i);
        items.set(i, items.get(j));
        items.set(j, first);
    }

    static void duplicateFirst(List<?> items) {
        duplicateCaptured(items);
    }

    private static <T> void duplicateCaptured(List<T> items) {
        if (!items.isEmpty()) {
            T first = items.get(0);
            items.add(first);
        }
    }

    public static void main(String[] args) {
        List<String> words =
                new ArrayList<>(List.of("left", "right"));
        swapAny(words, 0, 1);
        System.out.println("swapped: " + words);

        duplicateFirst(words);
        System.out.println("duplicated: " + words);

        List<Integer> nums =
                new ArrayList<>(List.of(1, 2));
        swapAny(nums, 0, 1);
        System.out.println("numbers: " + nums);
    }
}
```

Chạy:

```bash
javac -Xlint:all WildcardCaptureDemo.java
java WildcardCaptureDemo
```

**Stdout đã kiểm chứng:**

```text
swapped: [right, left]
duplicated: [right, left, right]
numbers: [2, 1]
```

Compiler không đưa cảnh báo khi dùng `-Xlint:all`.

### Tại sao `duplicateFirst` an toàn?

`duplicateFirst(List<?>)` không biết kiểu phần tử là `String`, `Integer` hay một class khác. Helper `duplicateCaptured(List<T>)` lấy một phần tử `T` từ **chính danh sách ấy** và thêm lại **cùng phần tử ấy**. Nó không bịa ra giá trị mới có kiểu không xác định.

Hợp đồng public vẫn không cho caller thêm một `Object` tùy tiện, nhưng implementation có thể dùng capture conversion để thực hiện **các thao tác nội bộ nhất quán**.

Bản thân generic capture không làm mutable list trở thành immutable hoặc ngược lại. Nếu `items` là list bất biến tạo bởi `List.of`, `set`/`add` có thể ném `UnsupportedOperationException`. Ví dụ dùng `new ArrayList<>(...)` để những thao tác đó hợp lệ.

Ngoài ra, `swapAny` cần index hợp lệ; index ngoài phạm vi có thể gây `IndexOutOfBoundsException`. Đây là điều kiện thông thường của list operations, không phải lỗi generics.

### Có cần tự viết swap helper trong sản phẩm?

Thư viện chuẩn đã có `Collections.swap(List<?>, int, int)`, nên ví dụ ở đây nhằm **minh họa capture**. Trong code sản phẩm, cân nhắc API chuẩn khi phù hợp; không tự duy trì utility lặp lại nếu không có yêu cầu riêng.

## 11. Ngoại lệ thứ hai: `T` chỉ xuất hiện trong return type vẫn có ích

Một generic factory có thể **không nhận bất kỳ tham số nào liên quan `T`** nhưng vẫn mang type parameter cho caller suy luận từ kiểu đích:

```java
static <T> List<T> emptyTyped() {
    return List.of();
}
```

Ví dụ:

```java
List<String> words = emptyTyped();
List<Integer> numbers = MyApi.<Integer>emptyTyped();
```

Dòng thứ hai là ví dụ minh họa cần thay `MyApi` bằng class có khai báo `emptyTyped`.

Không thể đơn giản thay return type bằng `List<?>` mà giữ nguyên công dụng:

```java
// List<String> names = someMethodReturningListWildcard();
```

`List<?>` không thể gán trực tiếp vào `List<String>` khi chưa có bằng chứng ràng buộc kiểu, trong khi `List<T>` của generic factory có thể được compiler chọn từ target type.

Ở factory **không lưu phần tử**, như `List.of()`, việc tạo một collection rỗng có thể an toàn cho mọi `T`. Đây là lý do thư viện chuẩn có các generic factories kiểu như `Collections.emptyList()`.

**Giới hạn cần hiểu:** “`T` chỉ trong return type” không cho phép một implementation tự tạo một object `T` tùy ý mà không có nguồn dữ liệu hoặc factory đáng tin. Một method:

```java
// Không được giả lập an toàn bằng unchecked cast:
// static <T> T magic() { return (T) new Object(); }
```

có thể tạo hợp đồng sai và gây `ClassCastException` ở caller. Chỉ dùng return-only generic khi implementation có căn cứ bảo toàn contract (ví dụ danh sách rỗng, dữ liệu được kiểm tra hoặc nguồn kiểu hợp lệ).

## 12. Chương trình C — Return-only generic, factory và self-bound

Tạo tệp **`ReturnOnlyTypeDemo.java`**:

```java
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

public class ReturnOnlyTypeDemo {
    static <T> List<T> emptyTyped() {
        return List.of();
    }

    static <T> T fromFactory(Supplier<? extends T> factory) {
        return factory.get();
    }

    static <T extends Comparable<? super T>> T minimum(
            List<? extends T> source) {
        if (source.isEmpty()) {
            throw new IllegalArgumentException("empty");
        }

        T best = source.get(0);
        for (T candidate : source) {
            if (candidate.compareTo(best) < 0) {
                best = candidate;
            }
        }
        return best;
    }

    public static void main(String[] args) {
        List<String> names = emptyTyped();
        List<Integer> ids =
                ReturnOnlyTypeDemo.<Integer>emptyTyped();

        System.out.println("empty strings: " + names);
        System.out.println("empty integers: " + ids);
        System.out.println("library empty: "
                + Collections.<String>emptyList());

        String message = fromFactory(() -> "ready");
        System.out.println("factory: " + message);
        System.out.println("minimum: "
                + minimum(List.of(5, 2, 9)));
    }
}
```

Chạy:

```bash
javac -Xlint:all ReturnOnlyTypeDemo.java
java ReturnOnlyTypeDemo
```

**Stdout đã kiểm chứng:**

```text
empty strings: []
empty integers: []
library empty: []
factory: ready
minimum: 2
```

Chương trình được biên dịch bằng `-Xlint:all` không có cảnh báo.

### Ba lý do khác nhau khiến `T` cần thiết

1. **`emptyTyped()`**: thông qua target typing, caller nhận được `List<String>`, `List<Integer>`, v.v. một cách type-safe vì list rỗng không mang phần tử sai kiểu. Type variable ở đây không liên kết với input nhưng vẫn biểu diễn output contract có ích.
2. **`fromFactory(Supplier<? extends T>)`**: ràng buộc kiểu do factory sản xuất với kiểu giá trị trả về. Tồn tại quan hệ callback output → method output.
3. **`minimum`**: dùng bound `T extends Comparable<? super T>` để bảo đảm có thể so sánh các `T`, đồng thời giữ kiểu phần tử ở kết quả. Không thể chỉ thay return type bằng `Object` mà giữ được lời hứa kiểu gốc.

**Quy tắc rút ra:** Không đếm số lần `T` trong text một cách máy móc. Hãy hỏi “type variable này đang chứng minh điều gì cho caller hoặc cho body?”

## 13. Một type parameter được dùng trong bound cũng có thể mang hợp đồng quan trọng

Ví dụ:

```java
static <T extends Comparable<? super T>> T minimum(
        List<? extends T> source)
```

`Comparable<? super T>` cho phép các giá trị `T` tự so sánh một cách hợp lệ với `T` qua một kiểu đối số thích hợp của `Comparable`.

Nếu thay return bằng `Comparable<?>` hoặc `Object`, caller mất kiểu phần tử cụ thể. Nếu xóa ràng buộc `Comparable`, compiler không còn biết `compareTo` dùng được.

Điều này minh họa một lý do khác để giữ type parameter: **giới hạn hành vi/capability của các giá trị và mối quan hệ kiểu giữa chúng**.

Tuy nhiên, cũng không nên đưa `<T extends Comparable<? super T>>` vào một method chỉ `System.out.println(value)` khi không sử dụng khả năng so sánh. Bound có ý nghĩa khi API hoặc body **thực sự cần** những thao tác mà nó hứa.

## 14. Khi nào cần `? extends`, khi nào cần `? super`?

PECS — *Producer Extends, Consumer Super* — là gợi ý xác định wildcard theo hướng dữ liệu:

| Vị trí | Mục đích | Chữ ký thường phù hợp |
| --- | --- | --- |
| Nguồn chỉ đọc các giá trị kiểu `T` | Producer | `Collection<? extends T>` |
| Đích nhận các giá trị kiểu `T` | Consumer | `Collection<? super T>` |
| Callback nhận `T` | Consumer of T | `Predicate<? super T>`, `Consumer<? super T>` |
| Callback trả các giá trị dùng như `R` | Producer of R | `Function<? super T, ? extends R>` |
| Chỉ duyệt phần tử dưới dạng Object | Không cần đặt tên element type | `Collection<?>` |
| Cần ràng buộc cùng element type ở nhiều vị trí | Quan hệ tên `T` | Generic method với `<T>` |

Đừng thay **bất cứ** `List<T>` nào bằng `List<?>` chỉ vì nó ngắn hơn. Nếu method cần **thêm một giá trị `T`**, hoặc truyền phần tử từ một nguồn sang đích có loại tương thích, tên `T` có thể rất quan trọng.

Và ngược lại: `<T>` không phải một “điểm cộng” về mức độ generic nếu nó không chứng minh quan hệ nào. Một chữ ký `List<?>` dễ đọc đôi khi **mạnh hơn về thiết kế**, vì nó không để lộ một type variable không cần thiết.

## 15. Rủi ro khi đổi sang `List<Object>` hoặc raw `List`

Hai cách viết sai mục tiêu thường thấy:

```java
static void print(List<Object> source)
```

và:

```java
static void print(List source)
```

Dạng `List<Object>` không nhận một biến `List<String>` do invariance. Dạng raw `List` bỏ type arguments, có thể kéo theo `rawtypes` warnings và mở đường cho unchecked operations trong code phức tạp.

Dạng phù hợp cho việc chỉ đọc:

```java
static void print(List<?> source)
```

vừa chấp nhận nhiều element types, vừa giữ compiler kiểm tra giới hạn được phép đối với dữ liệu.

Khi cần một loại phần tử cụ thể để đọc, ví dụ `Number`, chọn `List<? extends Number>` thay vì raw `List` cộng cast bằng tay. Khi cần các elements chính xác là `String`, chữ ký `List<String>` vẫn có thể là hợp đồng đúng: không phải API nào cũng nên dùng wildcard.

## 16. Negative tests: compiler phải từ chối những cách sử dụng sai

Tạo tệp **`InvalidApiSignatures.java`**. Tệp này **cố ý không biên dịch**:

```java
import java.util.List;

public class InvalidApiSignatures {
    static void use(List<?> items) {
        items.add("bad");
    }

    static void assign(List<String> names) {
        List<Object> objects = names;
    }

    static <T> void wronglySet(
            List<T> items, Object replacement) {
        items.set(0, replacement);
    }
}
```

Chạy:

```bash
javac InvalidApiSignatures.java
```

Với Java compiler được dùng để kiểm chứng, ba lỗi liên quan:

```text
error: incompatible types: String cannot be converted to CAP#1
error: incompatible types: List<String> cannot be converted to List<Object>
error: incompatible types: Object cannot be converted to T
3 errors
```

**Phân tích:**

1. `items.add("bad")` qua `List<?>`: compiler không biết kiểu element thực và không thể bảo đảm chuỗi này được phép thêm.
2. `List<Object> objects = names`: generic invariance ngăn phép gán; dù `String` là subtype của `Object`, hai kiểu list không có quan hệ ấy.
3. `items.set(0,replacement)`: `Object` tùy ý không bảo đảm là `T`. Một method đặt tên `T` vẫn không cho phép ghi bừa dữ liệu sai kiểu.

**Điều cần nhớ:** việc bỏ một type variable thừa không phải là nới mọi kiểm tra. Wildcard vẫn giữ các ràng buộc an toàn và compiler từ chối thao tác mà dữ liệu nguồn không chứng minh được.

## 17. Một cách nhìn khác: Type variable tạo “phương trình kiểu”

Bạn có thể mô hình hóa một số hợp đồng như các phương trình:

```text
identity:
    Input = T
    Output = T

repeat:
    Input = T
    Output elements = T

append:
    Source value = T
    Destination accepts any supertype of T

countIf:
    Source supplies T
    Predicate accepts any supertype of T

printValues:
    Source supplies some unknown element type
    Output = void
    No connection requiring a named T

sum:
    Source supplies subtypes of Number
    Output = primitive double
    No connection requiring original subtype T
```

Nếu việc **đặt tên** một loại có thể thay bằng một mô tả hướng dữ liệu đơn giản mà không mất thông tin caller cần, type parameter đó có thể không đáng có trong public signature.

Nếu việc loại bỏ tên kiểu làm mất khả năng ràng buộc hai vị trí, làm yếu kiểu trả về, hoặc phá thao tác cần cùng element type ở implementation, giữ type parameter là có cơ sở.

## 18. Vấn đề public API và implementation detail

Một method public thường nên thể hiện **ít chi tiết nội bộ nhất**, miễn vẫn đủ contract:

```java
public static void process(List<?> input)
```

đủ nếu chỉ duyệt và không cần giữ tên element type.

Ở implementation nội bộ, một helper generic riêng có thể cần cho capture:

```java
private static <T> void processCaptured(List<T> input)
```

Đây không phải mâu thuẫn: **public contract** và **implementation algorithm** có mức độ biểu đạt khác nhau.

Lợi ích của API public tối giản:

- Reader không phải đi tìm “`T` ràng buộc cái gì?” nếu thực ra nó không có nhiệm vụ ấy.
- Generic inference ít điểm cần xét hơn.
- Javadoc tập trung vào điều caller thật sự có thể bảo đảm.
- Hạn chế việc lộ một tham số kiểu vô nghĩa như một phần của API contract.

Nhưng public API không nên giản lược tới mức caller mất generic result hoặc cross-parameter relationship. Chữ ký ngắn nhất không phải lúc nào cũng là chữ ký tốt nhất.

## 19. Cẩn thận với refactor trên API đã phát hành

Khi refactor một method:

```java
public static <T> void printValues(List<T> values)
```

thành:

```java
public static void printValues(List<?> values)
```

sự thay đổi có thể giữ **erased JVM descriptor** tương đương:

```text
(Ljava/util/List;)V
```

Nhưng **không nên suy từ cùng descriptor rằng việc đổi public API luôn vô hại**. Có nhiều tầng tương thích:

- **Source compatibility:** Một số overload resolution, method references, type inference hoặc source-level declarations có thể phụ thuộc vào generic form cũ.
- **Binary compatibility:** Ở trường hợp đơn giản, cùng erased descriptor có thể giúp code đã biên dịch tiếp tục liên kết, nhưng phải xét toàn bộ signature, return type, inheritance và bridge methods.
- **Reflection metadata:** `getGenericParameterTypes()` và generic `Signature` thay đổi giữa `List<T>` và `List<?>`. Framework dùng reflection có thể quan sát khác biệt.
- **Override/overload:** Khi thay chữ ký trong hierarchy generic, cần kiểm tra override rules và name clash sau erasure.
- **Documentation/semantic contract:** Người dùng API có thể hiểu type parameter cũ như một lời hứa về quan hệ kiểu dù implementation chưa tận dụng.

Với thư viện public, hãy biên dịch **downstream callers** và chạy compatibility tests trước khi thay chữ ký. Đừng đổi chỉ vì IDE báo “type parameter used once” nếu API có nhiều người dùng.

### Hai overload không thể chỉ khác wildcard/generic parameter cùng erasure

Không thể định nghĩa cùng class hai method chỉ khác `List<T>` và `List<?>` với cùng tên/tham số còn lại, vì erasure đều tương ứng `List`. Ví dụ về thiết kế **không hợp lệ**:

```java
// Không được cùng tồn tại trong một class:
// static <T> void process(List<T> data) {}
// static void process(List<?> data) {}
```

Compiler sẽ báo name clash do cùng erasure. Wildcard không tạo một overload runtime khác.

## 20. Quan sát erased signature bằng `javap`

Sau khi biên dịch `ApiSignatureDemo.java`:

```bash
javap -p -s ApiSignatureDemo
```

Trích từ bản chạy đã kiểm chứng:

```text
static void printValues(java.util.List<?>);
  descriptor: (Ljava/util/List;)V

static boolean containsNull(java.util.List<?>);
  descriptor: (Ljava/util/List;)Z

static double sum(java.util.List<? extends java.lang.Number>);
  descriptor: (Ljava/util/List;)D

static <T> long countIf(
    java.util.List<? extends T>,
    java.util.function.Predicate<? super T>);
  descriptor: (Ljava/util/List;Ljava/util/function/Predicate;)J

static <T> void append(java.util.List<? super T>, T);
  descriptor: (Ljava/util/List;Ljava/lang/Object;)V

static <T> java.util.List<T> repeat(T, int);
  descriptor: (Ljava/lang/Object;I)Ljava/util/List;
```

Tại bytecode, các generic types và wildcards trong method descriptors dùng erased types như `List`, `Predicate`, `Object`. Metadata generic `Signature` có thể giữ `T`/`?` để compiler và reflection xem khai báo.

**Vì vậy:** cùng erased descriptor **không** có nghĩa các source-level generic contracts tương đương. Compiler vẫn áp dụng generic type checking ở source. Những ràng buộc có ích ở `countIf`, `append` và `repeat` vẫn quan trọng dù bytecode không tạo specialized method cho từng `T`.

## 21. Những câu hỏi code review nên hỏi

Khi thấy một method khai báo `<T>`, hãy kiểm tra theo thứ tự:

1. `T` xuất hiện ở **những vị trí nào trong chữ ký**? Một input, nhiều inputs, return, callback, bound?
2. Caller có nhận lại một giá trị hoặc collection mang kiểu **suy luận từ input** không?
3. `T` có ràng buộc một **producer** với một **consumer** không?
4. Body có cần **giữ cùng element type** để đọc rồi ghi lại vào cùng container không (wildcard capture)?
5. Nếu bỏ `T`, compiler còn bảo đảm được điều caller cần không?
6. Nếu chỉ đọc như `Object`, có thể dùng `List<?>` hoặc `Object` rõ ràng hơn không?
7. Nếu chỉ cần upper bound `Number`, có thể dùng `? extends Number` mà không lộ `T` không?
8. Nếu method trả generic empty/factory value, có cần `T` để target typing không?
9. Có overload/override, Java reflection hay framework nào phụ thuộc metadata cũ không?
10. Thay đổi đó sẽ ảnh hưởng code caller, binary descriptors hay generic `Signature` ở mức nào?

**Không có quy tắc “`T` xuất hiện đúng hai lần thì luôn hợp lệ”.** Một `T` xuất hiện nhiều lần nhưng không ràng buộc có ích vẫn có thể là dấu hiệu thiết kế khó hiểu. Ngược lại, helper capture có một `T` trên public-facing signature nhưng body cần nó.

## 22. Những sai lầm dễ mắc

**Sai 1 — “Hễ có `<T>` là API type-safe hơn.”** Generic type variable không tự thêm bảo đảm nếu không liên kết điều gì quan trọng. `print(List<?>)` có thể biểu đạt contract chính xác và rõ ràng hơn `<T> print(List<T>)`.

**Sai 2 — “`T` chỉ xuất hiện một lần là lỗi compile.”** Không đúng; nhiều chữ ký ấy hoàn toàn hợp lệ. Đây là vấn đề thiết kế và tính dễ đọc, không phải một quy tắc cú pháp.

**Sai 3 — “Tất cả `<T>` một lần nên thay bằng `?`.”** Không đúng vì wildcard capture helpers và generic factories trả kiểu từ target context là những trường hợp đáng giữ.

**Sai 4 — “Thay `List<T>` bằng `List<Object>` là đơn giản nhất.”** Có thể phá khả năng nhận `List<String>` vì Java generics invariant. Với input chỉ đọc, `List<?>` hoặc `List<? extends Bound>` thường phù hợp hơn.

**Sai 5 — “`List<?>` là immutable.”** Nó không cho thêm phần tử non-null không rõ kiểu qua tham chiếu ấy, nhưng một list mutable vẫn có thể bị xóa phần tử/hoán đổi thông qua capture hoặc các operation khác.

**Sai 6 — “Nếu method không trả `T`, `<T>` chắc chắn không cần.”** `countIf`/ `append` vẫn cần `T` để ràng buộc các tham số, dù return là `long` hoặc `void`.

**Sai 7 — “Một factory `<T> List<T> empty()` là thừa vì không nhận input.”** Không đúng: nó có thể cung cấp một typed empty list từ ngữ cảnh đích, giữ khả năng `List<String> x = empty()`.

**Sai 8 — “Wildcard capture helper là bất hợp lý vì T chỉ ở `List<T>`.”** Helper có thể cần `T` trong body để ghi lại giá trị đọc từ cùng danh sách mà không dùng unchecked casts.

**Sai 9 — “`? extends T` cho phép thêm T bất kỳ.”** Không. Khi một list sản xuất subtype của `T`, compiler không biết subtype thực để cho thêm một `T` tùy ý. Dùng `? super T` khi danh sách là destination nhận `T`.

**Sai 10 — “Dùng raw types để tránh vấn đề inference.”** Raw types xóa ràng buộc generic, dẫn đến cảnh báo unchecked và có thể gây `ClassCastException` muộn. Hãy sửa chữ ký đúng vai trò dữ liệu thay vì bỏ generic checks.

**Sai 11 — “Mọi refactor generic/wildcard giữ nguyên erased descriptor đều tương thích hoàn toàn.”** Không đúng ở mọi ngữ cảnh; generic reflection, source-level inference, hierarchy và contract có thể thay đổi. Cần compatibility review.

**Sai 12 — “Một biến tên T chứng minh input thuộc một runtime class T riêng.”** Type erasure khiến các kiểu generic không có specialized runtime class theo mỗi type argument. Type parameter biểu đạt ràng buộc compile-time.

## 23. Bài tập và đáp án

### Bài 1 — Chọn chữ ký khi chỉ hiển thị dữ liệu

API chỉ duyệt và in `List<E>`, không dùng quan hệ với kết quả hay tham số nào khác. Chọn:

```java
static <T> void show(List<T> xs)
static void show(List<?> xs)
static void show(List<Object> xs)
```

**Đáp án:** `show(List<?>)` thường rõ nhất. `List<Object>` quá hạn chế với `List<String>`/ `List<Integer>`; `<T>` không đóng góp quan hệ quan trọng trong tình huống này.

### Bài 2 — Khi nào cần ràng buộc input/output?

```java
static <T> T identity(T x) { return x; }
```

Có nên thay return thành `Object` không?

**Đáp án:** không nếu muốn caller giữ kiểu suy luận từ `x`. `T` liên kết tham số và kết quả; đổi thành `Object` làm yếu static contract.

### Bài 3 — Cần `T` dù return void

```java
static <T> void append(List<? super T> dst, T value)
```

`T` có thật sự cần không?

**Đáp án:** có. Nó ràng buộc loại giá trị đưa vào với khả năng chấp nhận của destination list. Không thể thay `dst` thành `List<?>` rồi thêm `value` non-null tùy ý.

### Bài 4 — Upper bound chỉ để đọc

Method cộng `doubleValue()` của các phần tử, không trả dữ liệu có kiểu `T`. So sánh:

```java
<T extends Number> double sum(List<T> values)
double sum(List<? extends Number> values)
```

**Đáp án:** phương án thứ hai thường biểu đạt đúng nhu cầu với ít type variable hơn, đồng thời chấp nhận lists của các subtype `Number`.

### Bài 5 — Ngoại lệ capture

Vì sao `private static <T> void swapCaptured(List<T> list,...)` có ích khi public method nhận `List<?>`?

**Đáp án:** helper dùng `T` để giữ nhất quán kiểu phần tử được lấy ra và được ghi lại vào **cùng list**; wildcard capture cung cấp một tên cho unknown element type ở một lời gọi.

### Bài 6 — Return-only factory

`static <T> List<T> empty()` không nhận `T` trong input. Có nên thay bằng `List<?>` không?

**Đáp án:** không nếu muốn `List<String> names = empty()` được compiler kiểm tra hợp lệ. `T` biểu diễn kiểu element do caller chọn bằng target typing; list rỗng là implementation an toàn.

### Bài 7 — Type inference của predicate

`List<String> data` và `Predicate<CharSequence> predicate`. Vì sao chữ ký `countIf(List<? extends T>,Predicate<? super T>)` chấp nhận cặp đó?

**Đáp án:** `String` có thể dùng như `CharSequence`; predicate nhận một supertype của phần tử nguồn. `T` nối hai tham số để diễn đạt điều kiện này.

### Bài 8 — Overloading sau erasure

Có thể định nghĩa đồng thời hai overload:

```java
<T> void handle(List<T> list)
void handle(List<?> list)
```

không?

**Đáp án:** không, nếu cùng method name và cùng các tham số khác; erased parameter type của cả hai là `List`, gây name clash.

### Bài 9 — Method chỉ đọc `Object`

`static <T> void audit(T event)` chỉ gọi `System.out.println(event)`. Có phương án nào đơn giản hơn?

**Đáp án:** `static void audit(Object event)` thường đủ. Không cần type variable vì không có quan hệ giữa nhiều vị trí kiểu.

### Bài 10 — Ngăn nhầm `List<?>` với `List<Object>`

Tại sao `List<String>` truyền được cho `show(List<?>)` mà không cho `show(List<Object>)`?

**Đáp án:** `?` là wildcard chấp nhận một element type chưa biết theo capture, còn `List<Object>` là tham số hóa cụ thể. Java generics invariant.

### Bài 11 — Binary và reflection

Một method public `<T> void print(List<T>)` đổi thành `void print(List<?>)`. Hai method có thể cùng descriptor erased không? Có thể thay không cần kiểm thử không?

**Đáp án:** descriptor có thể cùng `(Ljava/util/List;)V`, nhưng generic `Signature` và source-level contract khác; các overload/inheritance/framework có thể chịu ảnh hưởng. Cần kiểm tra downstream callers và compatibility.

### Bài 12 — Thiết kế một transform API

Bạn cần nhận một list chứa subtype của `T`, chuyển từng phần tử bằng một `Function` nhận `T` hoặc supertype, và trả `List<R>` mà mapper có thể tạo subtype của `R`. Chữ ký nào phù hợp?

```java
static <T, R> List<R> map(
        List<? extends T> source,
        Function<? super T, ? extends R> mapper)
```

**Đáp án:** đây là cách biểu đạt hợp đồng linh hoạt an toàn: source là producer của `T`, mapper consumer của `T`/producer của `R`, và kết quả có element type `R`. Hai type variables cần thiết vì liên kết nhiều vị trí kiểu.

## 24. Checklist thiết kế generic API

- [ ] Tôi có thể giải thích chính xác `T` liên kết những vị trí nào không?
- [ ] Nếu method chỉ quan sát một container bất kỳ, đã cân nhắc `List<?>`?
- [ ] Nếu method chỉ quan sát một subtype của `Number`, đã cân nhắc `? extends Number`?
- [ ] Nếu method chỉ nhận một object mà không trả/so sánh theo cùng kiểu, `Object` có đủ không?
- [ ] Nếu output cần giữ kiểu từ input, tôi có giữ `<T>`?
- [ ] Nếu cần quan hệ producer/consumer, tôi có chọn đúng `? extends T`/`? super T`?
- [ ] Nếu cần ghi lại phần tử cùng list chưa biết kiểu, tôi có dùng helper capture thay unchecked cast?
- [ ] Nếu generic factory trả collection rỗng, tôi có giữ typed output có ích cho caller?
- [ ] Nếu có bound `Comparable<? super T>`, API thật sự sử dụng khả năng so sánh không?
- [ ] Có tránh được raw types và cast không thể chứng minh không?
- [ ] Tôi có kiểm chứng cả nguồn subtype và callback supertype bằng `javac`?
- [ ] Nếu là API đã phát hành, tôi đã kiểm tra generic `Signature`, callers, overloads và compatibility?
- [ ] Không xem `List<?>` là immutable hoặc `List<Object>` là cha của `List<String>`.
- [ ] Tôi chỉ làm chữ ký ngắn hơn **khi không mất quan hệ kiểu cần thiết**, không refactor máy móc.

## Kết luận

Một type parameter có tên như `T` đáng dùng khi nó **truyền đạt ràng buộc**: nối input với output, nối source với destination/callback, thể hiện self-bound hoặc cho phép wildcard capture trong implementation. Nếu method chỉ dùng một loại phần tử ở một vị trí mà không cần ràng buộc tiếp, một **wildcard `?`** hoặc supertype cụ thể như `Object`/`Number` thường làm API dễ hiểu hơn.

Vì vậy, **“tránh type parameter chỉ xuất hiện một lần” là quy tắc hướng dẫn, không phải phép thay văn bản**. Luôn xét public contract, thân method, generic return-only factories, capture helpers và compatibility trước khi sửa. Thiết kế tốt không tối đa số lượng `<T>`, cũng không tối thiểu chúng bằng mọi giá — nó biểu diễn **đúng những quan hệ kiểu mà caller cần compiler bảo vệ**.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="generic-api-design-006">Thiết kế kho lưu trữ nhiều kiểu với Class&lt;T&gt; làm khóa</a>

<details>
<summary>Xem chi tiết</summary>

# Thiết kế kho lưu trữ nhiều kiểu với `Class<T>` làm khóa trong Java Generics

## 1. Bài toán: một kho chứa nhiều kiểu dữ liệu nhưng API vẫn an toàn kiểu

Một cấu trúc như `Map<String, Object>` có thể chứa tên người dùng kiểu `String`, số lần thử kiểu `Integer`, hoặc thời gian chờ kiểu `Duration`. Tuy nhiên, API đọc thường phải ép kiểu:

```java
Object raw = values.get("retryCount");
Integer retries = (Integer) raw;
```

Đây là đoạn minh họa, giả định có biến `values`. Compiler không thể chứng minh `"retryCount"` luôn được liên kết với `Integer`. Nếu mã khác đặt `String` vào cùng khóa, lời ép kiểu có thể ném `ClassCastException` lúc chạy.

Một thiết kế khác dùng **runtime class token `Class<T>` như khóa**:

```java
registry.put(String.class, "Java");
registry.put(Integer.class, 3);

String language = registry.get(String.class);
Integer retries = registry.get(Integer.class);
```

Khi caller đưa `String.class`, compiler biết giá trị tương ứng cần có kiểu `String`. Với `Integer.class`, compiler biết giá trị cần là `Integer`. Bên trong vẫn có thể dùng một `Map<Class<?>, Object>`, nhưng **public API generic** kết nối kiểu của key và value.

Thiết kế này thường được gọi là **typesafe heterogeneous container**: một container không khai báo một type parameter chung như `Box<T>`, mà **mỗi entry có một mối quan hệ kiểu riêng** thông qua khóa lớp.

Điểm cốt lõi:

> Không phải `Map<Class<?>, Object>` tự an toàn kiểu. Tính an toàn đến từ việc chỉ cho phép truy cập map qua API như `<T> void put(Class<T>, T)` và `<T> T get(Class<T>)`, đồng thời dùng `Class.cast` để giữ ràng buộc ở runtime.

Bài này xây một registry từ mức tối thiểu tới thiết kế thực dụng: type tokens, `Class.cast`, `null`, thay thế giá trị, subclass/interface, identity của key, giới hạn với `List<String>`, đồng thời, khả năng thay đổi của dữ liệu, kiểm thử và lỗi thường gặp. Các chương trình hoàn chỉnh sử dụng Java 17 trở lên.

## 2. Kiểu của key và kiểu của value liên hệ thế nào?

Xét method:

```java
public <T> void put(Class<T> type, T value)
```

`T` là một **method type parameter**. Nó xuất hiện đồng thời ở key và value:

- `Class<T>` cho biết một class token cụ thể đang được dùng.
- `T value` nói giá trị phải tương thích với kiểu token đó.
- Caller không cần viết `<T>` tường minh nếu compiler suy luận được từ lời gọi.

Tương tự:

```java
public <T> T get(Class<T> type)
```

Khi truyền `String.class` có kiểu `Class<String>`, kết quả được định kiểu thành `String`.

Ví dụ:

```java
registry.put(String.class, "hello");   // Hợp lệ
registry.put(Integer.class, 123);      // Hợp lệ

// CỐ Ý KHÔNG BIÊN DỊCH:
// registry.put(String.class, 123);     // String và Integer không tương thích
```

Để biết kiểu, compiler không cần đọc nội dung runtime của map: **chữ ký generic** đủ để kiểm tra các lời gọi được viết đúng kiểu.

Chữ ký không làm Java Generics trở thành reified. Nếu code dùng raw types, casts không kiểm tra, reflection hoặc truy cập backing map từ một đường khác, quan hệ key/value vẫn có thể bị phá. Vì vậy cần khóa chặt **đường ghi** và dùng runtime validation.

## 3. Thiết kế tối thiểu: `Map<Class<?>, Object>`

Một class registry cơ bản:

```java
final class Registry {
    private final Map<Class<?>, Object> values = new HashMap<>();

    public <T> void put(Class<T> type, T value) {
        values.put(type, type.cast(value));
    }

    public <T> T get(Class<T> type) {
        return type.cast(values.get(type));
    }
}
```

Đây là mảnh khai báo minh họa; cần import `Map` và `HashMap`, hoặc dùng tên đầy đủ như trong chương trình bên dưới.

Tại sao field là `Map<Class<?>, Object>` chứ không phải `Map<Class<T>, T>`?

- **Không có một `T` chung cho mọi entry.** Một khóa `String.class` có giá trị `String`, khóa `Integer.class` có giá trị `Integer`.
- `Class<?>` cho phép map nhận class token của nhiều lớp.
- `Object` cho phép lưu các object reference thuộc nhiều class.
- Chữ ký generic của **method** `<T>` sẽ đảm bảo từng thao tác có đúng quan hệ kiểu.

Cũng đừng nghĩ `Map<Class<?>, Object>` là một `Map<Class<T>, T>` cho một `T` duy nhất. Generic parameter ở cấp class sẽ giới hạn toàn bộ registry vào một loại, không đúng mục đích heterogeneous.

`Class.cast` là bước xác minh runtime bổ sung: nếu `type` đại diện `String` và `value` không phải `String`, nó sẽ ném `ClassCastException`. Với `null`, `Class.cast(null)` trả `null`, nên cần chính sách `null` riêng nếu ứng dụng không muốn chấp nhận.

## 4. Ví dụ chạy được: registry tối thiểu

Tạo tệp **`BasicTypeRegistryDemo.java`**:

```java
import java.util.HashMap;
import java.util.Map;

public class BasicTypeRegistryDemo {

    static final class Registry {
        private final Map<Class<?>, Object> values = new HashMap<>();

        public <T> void put(Class<T> type, T value) {
            values.put(type, type.cast(value));
        }

        public <T> T get(Class<T> type) {
            return type.cast(values.get(type));
        }
    }

    public static void main(String[] args) {
        Registry registry = new Registry();

        registry.put(String.class, "Java 17");
        registry.put(Integer.class, 4);
        registry.put(Boolean.class, true);

        String label = registry.get(String.class);
        Integer attempts = registry.get(Integer.class);
        Boolean enabled = registry.get(Boolean.class);

        System.out.println(label);
        System.out.println(attempts);
        System.out.println(enabled);
        System.out.println(registry.get(Double.class) == null);
    }
}
```

Biên dịch và chạy:

```bash
javac BasicTypeRegistryDemo.java
java BasicTypeRegistryDemo
```

Stdout:

```text
Java 17
4
true
true
```

Giải thích:

1. Registry chỉ có **một map nội bộ**, không phải ba map cho `String`, `Integer`, `Boolean`.
2. `put(String.class, "Java 17")` giữ đúng cặp class/value.
3. `get(Integer.class)` trả kết quả kiểu `Integer`; caller không cần ép `(Integer)`.
4. Vì `Double.class` chưa có entry, `Map.get` trả `null`, và `Class.cast(null)` trả `null`.
5. Đây là ví dụ đơn giản để thấy cơ chế, **chưa quyết định chính sách null và primitive class tokens**.

Khi muốn dùng thiết kế trong thư viện, đừng dừng ở ví dụ tối thiểu: cần làm rõ khóa vắng, `null`, cập nhật, thread safety và khả năng lưu generic parameterized types.

## 5. Vì sao `Class<T>` là một “type token”?

Một class literal:

```java
Class<String> s = String.class;
Class<Integer> n = Integer.class;
```

là reference tới runtime `Class` objects của `String` và `Integer`.

Token cung cấp API như:

- `type.cast(object)` — kiểm tra và trả object với kiểu `T`, hoặc ném `ClassCastException`.
- `type.isInstance(object)` — kiểm tra object không null có là instance phù hợp không.
- `type.getName()` — tên class để log/diagnostic.
- `type.isPrimitive()` — nhận diện token đại diện primitive.
- `type.isInterface()` — nhận diện interface.
- `type.isAssignableFrom(otherClass)` — kiểm tra khả năng gán runtime theo chiều thích hợp.

Registry ở đây dùng token theo **identity của class runtime**, không phải theo chuỗi tên đơn thuần. `String.class` là một key; `CharSequence.class` là key khác ngay cả khi một giá trị `String` có thể được lưu dưới cả hai.

Cần phân biệt:

```java
registry.put(String.class, "text");
```

khác với:

```java
registry.put(CharSequence.class, "text");
```

Hai lời gọi có thể chứa cùng một object thực nhưng ghi vào **hai khóa khác nhau**.

## 6. `Class.cast` bảo vệ quan hệ key–value thế nào?

Một implementer có thể viết:

```java
@SuppressWarnings("unchecked")
public <T> T get(Class<T> type) {
    return (T) values.get(type);
}
```

Khi cast sang một type variable `T`, compiler không thể kiểm tra đầy đủ tại runtime vì erasure. Lời gọi của client có thể vẫn nhận được lỗi khi kết quả được dùng như kiểu cụ thể.

Thay bằng:

```java
public <T> T get(Class<T> type) {
    return type.cast(values.get(type));
}
```

có hai lợi ích:

1. **Ý định rõ**: result phải thuộc runtime class mà caller đưa vào.
2. **Vị trí kiểm tra rõ**: `Class.cast` thực hiện kiểm tra kiểu cụ thể theo token, không dựa vào giả định rằng dữ liệu map luôn đúng.

Nên dùng `type.cast(value)` cả ở đường `put` để phòng những đường gọi không hoàn toàn typed, chẳng hạn raw `Class` do mã legacy cung cấp.

Tuy nhiên, `Class.cast` **không kiểm tra bên trong `List<String>`**. Nếu type là `List.class`, nó chỉ xác nhận một object thuộc interface `List`, không quan tâm phần tử là `String` hay `Integer`. Giới hạn ấy được phân tích riêng ở phần sau.

## 7. API thực dụng: cấm null, trả giá trị cũ và xóa đúng kiểu

Để có hợp đồng dễ kiểm tra, giả sử registry chọn:

- `null` **không được dùng làm value**.
- Class token `null` bị từ chối.
- Primitive token như `int.class` bị từ chối.
- Mỗi `Class<?>` key chỉ có **một value**; `put` mới thay value cũ.
- `get` trả `null` khi key vắng; `find` có thể trả `Optional<T>`.
- `remove` trả giá trị cũ có kiểu `T`, hoặc `null` khi không tồn tại.

Tệp **`StrictTypeRegistryDemo.java`**:

```java
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class StrictTypeRegistryDemo {

    static final class Registry {
        private final Map<Class<?>, Object> entries = new HashMap<>();

        private static <T> Class<T> checkToken(Class<T> type) {
            Objects.requireNonNull(type, "type");

            if (type.isPrimitive()) {
                throw new IllegalArgumentException(
                        "Primitive class token is unsupported");
            }

            return type;
        }

        public <T> T put(Class<T> type, T value) {
            type = checkToken(type);
            Objects.requireNonNull(value, "value");

            Object previous = entries.put(type, type.cast(value));
            return type.cast(previous);
        }

        public <T> T get(Class<T> type) {
            type = checkToken(type);
            return type.cast(entries.get(type));
        }

        public <T> Optional<T> find(Class<T> type) {
            return Optional.ofNullable(get(type));
        }

        public <T> T remove(Class<T> type) {
            type = checkToken(type);
            return type.cast(entries.remove(type));
        }

        public boolean contains(Class<?> type) {
            checkToken(type);
            return entries.containsKey(type);
        }
    }

    public static void main(String[] args) {
        Registry registry = new Registry();

        System.out.println(registry.put(String.class, "first"));
        System.out.println(registry.put(String.class, "second"));
        System.out.println(registry.get(String.class));
        System.out.println(registry.contains(String.class));
        System.out.println(registry.find(Integer.class).isPresent());
        System.out.println(registry.remove(String.class));
        System.out.println(registry.contains(String.class));
    }
}
```

Biên dịch và chạy trên Java 17:

```bash
javac StrictTypeRegistryDemo.java
java StrictTypeRegistryDemo
```

Stdout:

```text
null
first
second
true
false
second
false
```

Giải thích:

- `put` trả về **giá trị cũ** cùng class nếu đã có entry; lần đầu trả `null`.
- `get` và `remove` có return type `T` theo token.
- `containsKey` giúp phân biệt vắng mặt trong hợp đồng nếu cần.
- `Optional.ofNullable` diễn đạt “có hoặc không có giá trị” khi value null bị cấm.
- `checkToken` loại primitive class tokens vì chúng không thể được dùng tương tự reference tokens trong `Class.cast`.

Ví dụ này **không an toàn khi nhiều thread truy cập cùng lúc**, vì backing map là `HashMap`. Nếu cần concurrent access, dùng một thiết kế khác thay vì giả định API generic đã xử lý concurrency.

## 8. Chính sách null: quyết định rõ từ đầu

`HashMap` cho phép `null` làm value. `Class.cast(null)` cũng trả `null`. Vì vậy registry tối thiểu không phân biệt được:

```java
registry.get(String.class) == null
```

có nghĩa “không có entry” hay “entry có value null”.

Có ba hướng thiết kế hợp lệ:

**Hướng A — Không chấp nhận null value.** Dùng `Objects.requireNonNull(value)` trong `put`; khi đó `get(...) == null` có thể biểu thị key không tồn tại. Đây là lựa chọn của `StrictTypeRegistryDemo`.

**Hướng B — Cho phép null nhưng có API `contains`.** `containsKey(type)` phân biệt key vắng và key hiện diện với null. `Optional<T>` **không** thể hiện riêng ba trạng thái vắng / có null / có non-null.

**Hướng C — Dùng mô hình kết quả riêng.** Ví dụ record hoặc sealed type mô tả `Absent`, `PresentNull`, `PresentValue<T>` nếu nghiệp vụ cần phân biệt đầy đủ.

Không nên đặt `Optional<T>` như value map một cách máy móc để giải quyết toàn bộ. Nó thêm một tầng đối tượng và vẫn cần thống nhất `Optional.empty()` biểu diễn điều gì.

Đối với `ConcurrentHashMap`, `null` key và `null` value không được chấp nhận; chính sách cấm null là cần thiết nếu lựa chọn backing map này.

## 9. Primitive tokens: `int.class` khác `Integer.class`

Một chi tiết nâng cao:

```java
System.out.println(int.class.isPrimitive());
System.out.println(Integer.class.isPrimitive());
```

Hai dòng trên (khi đặt trong `main`) in:

```text
true
false
```

Java có class token cho primitive để phục vụ reflection, nhưng generic type arguments không được là primitive. Trong các ngữ cảnh class literal, `int.class` có thể tham gia API với kiểu `Class<Integer>` theo quy tắc đặc biệt của `Class` literals, **không có nghĩa `int.class` tương đương `Integer.class`**.

Runtime class token của `int` không phải runtime class của boxed `Integer` instance. Trong một registry lưu `Object`, hợp đồng đơn giản nhất là:

- **Dùng `Integer.class`** khi muốn lưu một giá trị `Integer` (được autobox từ `int` nếu cần).
- **Từ chối `int.class`** khi đăng ký value bằng `Class.cast`.
- Tương tự dùng `Boolean.class`, `Double.class` thay cho primitive tokens.

`Class.cast` trên primitive class token không thực hiện phép boxing/unboxing chung như một converter. Đừng coi `int.class.cast(1)` là cách chuyển `Integer` sang primitive `int`.

## 10. Registry định danh theo chính class token, không tìm subtype tự động

Khi gọi:

```java
registry.put(String.class, "A");
```

registry ghi key `String.class`. Một lời gọi sau:

```java
registry.get(CharSequence.class);
```

không tự tìm entry của `String.class`, dù `String implements CharSequence`.

Nguyên nhân: `Map.get` tìm **đúng key**, không chạy thuật toán duyệt class hierarchy.

Nếu nghiệp vụ cần “tìm tất cả service implement interface X”, đó là **một bài toán khác**:

- Có thể đăng ký rõ bằng key interface: `registry.put(CharSequence.class, "A")`.
- Có thể thiết kế resolver dùng `isAssignableFrom` để tìm ứng viên phù hợp.
- Nhưng khi có nhiều implementation phù hợp, resolver cần quy tắc ưu tiên/độ duy nhất; không thể trả tùy ý entry đầu tiên từ map như một hợp đồng chắc chắn.

Thiết kế chính của bài ưu tiên **lookup theo class key chính xác**, vì đơn giản, xác định và dễ bảo đảm type safety.

## 11. Chương trình minh họa key interface và key implementation

Tệp **`ExactClassKeyDemo.java`**:

```java
import java.util.HashMap;
import java.util.Map;

public class ExactClassKeyDemo {
    static final class Registry {
        private final Map<Class<?>, Object> values = new HashMap<>();

        <T> void put(Class<T> type, T value) {
            values.put(type, type.cast(value));
        }

        <T> T get(Class<T> type) {
            return type.cast(values.get(type));
        }
    }

    public static void main(String[] args) {
        Registry registry = new Registry();
        registry.put(String.class, "concrete");

        System.out.println(registry.get(String.class));
        System.out.println(registry.get(CharSequence.class));

        registry.put(CharSequence.class, "interface");
        System.out.println(registry.get(String.class));
        System.out.println(registry.get(CharSequence.class));
    }
}
```

Biên dịch/chạy:

```bash
javac ExactClassKeyDemo.java
java ExactClassKeyDemo
```

Stdout:

```text
concrete
null
concrete
interface
```

`String.class` và `CharSequence.class` là hai keys riêng. Cả hai có thể trỏ tới các object có runtime class `String`, nhưng API nhận diện chúng theo **class tokens người đăng ký đã chọn**.

Đây là hành vi nên ghi rõ trong tài liệu API; nếu không, caller dễ lầm rằng `get(SuperType.class)` sẽ tự tìm tất cả subtype instances.

## 12. `Class<T>` invariance và wildcard trong registry

Generic class `Class<T>` là **invariant**. Mặc dù `Integer extends Number`, không thể tự gán `Class<Integer>` cho biến `Class<Number>`:

```java
// CỐ Ý KHÔNG BIÊN DỊCH:
// Class<Number> token = Integer.class;
```

Nhưng:

```java
Class<? extends Number> numericToken = Integer.class;
```

là hợp lệ.

Lúc dùng một token wildcard, compiler chỉ biết **một subtype chưa xác định** của `Number`:

```java
Class<? extends Number> type = Integer.class;
```

Một API:

```java
<T> T get(Class<T> type)
```

có thể trả một giá trị được nhìn an toàn như `Number` khi nhận một `Class<? extends Number>`, nhưng không thể giả định nó là **bất kỳ** subtype nào do caller tùy ý chọn.

Các helper nên dùng `Class<? extends Base>` nếu muốn nhận plugin implementations, còn public `get(Class<T>)` giữ quan hệ **cùng một type parameter T** giữa class token và result.

Đừng thay chữ ký bằng raw `Class` để “giải quyết” các lỗi wildcard. Khi cần, dùng capture helper hoặc chuyển value trong phạm vi bound đã biết.

## 13. Khi có key lấy động từ `Class.forName`

Một ứng dụng plugin có thể nhận class name từ cấu hình:

```java
Class<?> loaded = Class.forName("java.lang.String");
```

Token có kiểu tĩnh `Class<?>`: compiler không biết một `T` cụ thể. Nếu cần giới hạn plugin về một interface hoặc base class, hãy kiểm tra runtime:

```java
Class<? extends CharSequence> type =
        loaded.asSubclass(CharSequence.class);
```

`asSubclass` trả một token có bound đã được kiểm tra, hoặc ném `ClassCastException` nếu class không phải subtype thích hợp.

**Không làm điều này cho mọi class name từ input chưa tin cậy rồi khởi tạo tùy ý.** Việc load/instantiate có thể chạy code, dùng tài nguyên và đụng tới chính sách quyền truy cập. Sử dụng allowlist hoặc một danh sách plugin đã được xác thực khi triển khai runtime registry.

Trong registry theo class key, API nhận `Class<?>` giúp đọc metadata hoặc kiểm tra có entry; nhưng nếu muốn `get` trả về một kiểu cụ thể có compile-time guarantee, **người gọi phải có token được ràng buộc phù hợp**. Truyền một class name dạng string không tự làm compiler biết kiểu kết quả.

## 14. Không dùng khóa chuỗi để giả vờ đã tạo quan hệ kiểu

So sánh hai API:

```java
Object get(String key);
<T> T get(Class<T> type);
```

API thứ nhất không thể suy từ một chuỗi `"port"` ra return type `Integer` trừ khi có schema/registry khác và caller cast.

API thứ hai liên kết giá trị trả về trực tiếp với type argument của `Class<T>` trong method signature. Nó phù hợp với dữ liệu mà **mỗi class chỉ có một entry** và class identity là key hợp lý.

Nhưng nếu nghiệp vụ muốn **hai chuỗi khác nhau** như `"firstName"` và `"lastName"` cùng mang kiểu `String`, registry class-key không giải quyết đủ:

```java
registry.put(String.class, "first");
registry.put(String.class, "last");
```

Lần ghi thứ hai sẽ **thay thế** lần đầu vì trùng key.

Giải pháp có thể là một **typed key object** kết hợp tên và class token, ví dụ `Key<T>("firstName", String.class)`, hoặc key gồm namespace + type. Đó là một cấu trúc khác với bài toán “mỗi runtime class là một key”.

## 15. Khi một class cần nhiều giá trị: typed key có tên

Nếu nghiệp vụ cần nhiều giá trị cùng kiểu, có thể dùng một khóa có danh tính riêng, đồng thời lưu token để duy trì relation với value.

Một mô hình:

```java
record Key<T>(String name, Class<T> type) {
}
```

Nhưng cần lưu ý: generic `Key<T>` bị erasure, và Java record equality sẽ so **name và type**; nếu hai key có cùng name và class token, chúng trùng theo equality dù được tạo hai lần.

Bản thiết kế hợp lý có thể có:

```java
Map<Key<?>, Object> values;
<T> void put(Key<T> key, T value);
<T> T get(Key<T> key);
```

Và kiểm tra `key.type().cast(value)` ở cả hai chiều. Mẫu này thêm một chiều **định danh nghiệp vụ** ngoài class runtime.

Ở bài hiện tại, trọng tâm vẫn là `Class<T>` làm key. Typed named keys là **phương án mở rộng** khi một class cần nhiều hơn một registration hoặc cần key namespace.

## 16. Ví dụ mở rộng: `Key<T>` kết hợp tên và class token

Tệp **`NamedTypedKeyDemo.java`**:

```java
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class NamedTypedKeyDemo {

    record Key<T>(String name, Class<T> type) {
        Key {
            Objects.requireNonNull(name);
            Objects.requireNonNull(type);
        }
    }

    static final class Registry {
        private final Map<Key<?>, Object> values = new HashMap<>();

        <T> void put(Key<T> key, T value) {
            Objects.requireNonNull(value);
            values.put(key, key.type().cast(value));
        }

        <T> T get(Key<T> key) {
            return key.type().cast(values.get(key));
        }
    }

    public static void main(String[] args) {
        Registry registry = new Registry();

        Key<String> first = new Key<>("firstName", String.class);
        Key<String> last = new Key<>("lastName", String.class);

        registry.put(first, "An");
        registry.put(last, "Nguyen");

        System.out.println(registry.get(first));
        System.out.println(registry.get(last));
    }
}
```

Biên dịch/chạy:

```bash
javac NamedTypedKeyDemo.java
java NamedTypedKeyDemo
```

Stdout:

```text
An
Nguyen
```

Đây là một giải pháp có **hai khóa khác nhau cùng class token `String.class`**. Nó cho thấy khi nào phải nâng cấp thiết kế so với `Map<Class<?>,Object>`.

Lưu ý: nếu cần name case-insensitive, namespace, tenant hoặc các ràng buộc hợp lệ khác, phải tự định nghĩa quy tắc định danh ở record/class key. Generic signature không thay thế dữ liệu nghiệp vụ.

## 17. Giới hạn rất quan trọng: `List<String>` và `List<Integer>` dùng cùng `List.class`

Java Generics có **type erasure**. Một parameterized type như `List<String>` không có class literal riêng:

```java
// CỐ Ý KHÔNG BIÊN DỊCH:
// Class<List<String>> stringList = List<String>.class;
```

Runtime chỉ có:

```java
Class<?> listClass = List.class;
```

Hệ quả trong class-key registry:

- Không có hai keys riêng tên `List<String>.class` và `List<Integer>.class`.
- `List.class` chỉ xác minh rằng object thuộc interface `List`.
- Không thể dùng `type.cast(value)` với `List.class` để xác minh mọi phần tử là `String`.
- Một registry có một entry cho `List.class` theo thiết kế cơ bản, không phân biệt generic type arguments.

### Ví dụ đọc đúng giới hạn

Tệp **`ListClassTokenLimitDemo.java`**:

```java
import java.util.List;

public class ListClassTokenLimitDemo {
    public static void main(String[] args) {
        List<?> text = List.of("A", "B");
        List<?> numbers = List.of(1, 2);

        System.out.println(List.class.isInstance(text));
        System.out.println(List.class.isInstance(numbers));

        Object mixed = List.of("A", 10);
        List<?> accepted = List.class.cast(mixed);

        System.out.println(accepted.size());
        System.out.println(accepted.get(1));
    }
}
```

Biên dịch/chạy:

```bash
javac ListClassTokenLimitDemo.java
java ListClassTokenLimitDemo
```

Stdout:

```text
true
true
2
10
```

`List.class` chấp nhận cả danh sách chuỗi, danh sách số và danh sách trộn. Điều đó đúng theo raw runtime interface, nhưng **không phải kiểm tra type argument**.

Khi cần lưu **generic type lồng nhau** làm key, phải dùng một loại key có thể mô tả `Type`, `ParameterizedType`, type reference hoặc schema riêng, và cần chính sách validation nếu muốn tin dữ liệu runtime.

## 18. Vì sao `Map<Type,Object>` cũng không tự an toàn?

Một lựa chọn nâng cao là dùng `java.lang.reflect.Type` làm key, ví dụ từ `TypeReference<List<String>>` và `TypeReference<List<Integer>>`. Nhờ `ParameterizedType`, hai keys có thể phân biệt **cấu trúc generic**.

Nhưng không được viết:

```java
// Ý tưởng chưa đủ để an toàn:
Map<Type, Object> values;
```

rồi tuyên bố đã an toàn kiểu.

Vì sao?

1. `Type` mô tả generic declaration/type schema, **không tự validate một object bất kỳ**.
2. `Type` không cung cấp `cast` tổng quát như `Class<T>` để kiểm tra `List<String>` ở runtime.
3. Một caller có thể đưa `List<Object>` trộn dữ liệu vào map nếu API dùng casts không kiểm tra.
4. Nếu cần `List<String>`, bộ kiểm tra phải duyệt từng phần tử và kiểm tra `String` theo schema, hoặc dựa vào một deserializer có validation rõ ràng.

Vì vậy hãy tách:

- **Class-key registry**: mối quan hệ runtime class đơn giản; dùng `Class.cast`.
- **Generic type-key registry**: key giữ cấu trúc `Type`, cần mô hình validation và trust boundary chặt hơn.

Không xây một API `get(Type)` trả `<T> T` không kiểm tra rồi gọi nó an toàn chỉ vì key chứa chữ `List<String>`.

## 19. Vấn đề identity: Class objects, class loaders và module

`Class` objects được JVM gắn với **runtime type definition**. Hai class có cùng tên nhị phân nhưng được định nghĩa bởi các class loaders khác nhau có thể là **hai runtime classes khác nhau**.

Đây là điều quan trọng trong plugin registries:

- Không dùng `Class.getName()` như một thay thế đáng tin cho class identity.
- `Class.cast` và `isAssignableFrom` kiểm tra quan hệ type thực, không chỉ so chuỗi tên.
- `Map<Class<?>,Object>` dùng class token làm key theo `equals/hashCode` của `Class`, gắn với runtime class identity.
- Nếu plugin được unload/reload bằng class loader mới, các tokens và entries cũ không tự chuyển thành token của class mới.

**Class-loader leaks:** một singleton registry sống lâu mà giữ keys là plugin `Class<?>` hoặc values là plugin instances có thể giữ class loader/plugin không được giải phóng. Khi có plugin lifecycle, phải hỗ trợ remove/close/clear theo phạm vi và không giữ references lâu hơn cần thiết.

Đây là vấn đề quản lý vòng đời tham chiếu, không phải lỗi của Generic type checking.

## 20. Quyền sở hữu và mutability: type-safe không có nghĩa immutable

Giả sử một registry chứa `ArrayList` dưới token `List.class` hoặc một class cụ thể `ArrayList.class`. Caller lấy về một reference tới **chính object** đang được giữ trong map.

Nếu object đó mutable, caller có thể sửa nó:

```java
List<String> names = new ArrayList<>();
names.add("A");
registry.put(List.class, names); // Cần xem chính sách generic API và raw List.
```

Đây là đoạn thảo luận, không phải mẫu khuyến khích: dùng `List.class` chỉ đại diện raw interface và không giữ `String` trong token. Không nên dùng ví dụ này làm chứng cứ một `List<String>` đã được runtime kiểm tra.

Một hướng tốt hơn là thiết kế registry lưu **các service objects hoặc cấu hình immutable**, và xác định caller có được thay đổi đối tượng sau `put` hay không.

Các chính sách có thể gồm:

- Lưu reference nguyên trạng: nhanh, nhưng thay đổi của caller được nhìn thấy qua registry.
- Tạo defensive copy với loại dữ liệu phù hợp.
- Lưu immutable value objects/records, hoặc snapshot không sửa được.
- Giới hạn quyền truy cập với registry hoặc dịch vụ API đọc-only.

`Class<T>` đảm bảo **kiểu runtime khi đặt/lấy**, không tự sao chép hoặc làm bất biến giá trị.

## 21. Khi nào nên dùng `ConcurrentHashMap`?

`HashMap` không hỗ trợ truy cập thay đổi đồng thời không đồng bộ một cách an toàn. Nếu nhiều threads có thể gọi `put`/`get`/`remove` trên cùng registry, phải chọn chiến lược thread safety.

Một lựa chọn là:

```java
private final ConcurrentHashMap<Class<?>, Object> values =
        new ConcurrentHashMap<>();
```

Các ưu điểm:

- `get`, `put`, `putIfAbsent`, `remove` hỗ trợ các bảo đảm concurrency thích hợp của `ConcurrentHashMap`.
- Không cho phép null key/value, phù hợp với chính sách từ chối null.
- Có các atomic operations cho nhiều trường hợp như `computeIfAbsent`.

Những giới hạn:

- Thread safety của map **không khiến các object value mutable trở thành thread-safe**.
- Nếu logic cần tính nhất quán qua nhiều keys hoặc nhiều bước, từng map operation độc lập chưa đảm bảo transaction.
- Với `computeIfAbsent`, mapping function nên ngắn gọn và không tự cập nhật chính map theo cách có thể gây lỗi hoặc deadlock.
- Không nên cho rằng mọi side effects trong factory được chạy đúng một lần trong mọi tình huống lỗi/retry bên ngoài.

Nếu registry chỉ dùng trong một scope đơn luồng, `HashMap` có thể đủ và đơn giản hơn.

## 22. Ví dụ đầy đủ: concurrent type registry

Tệp **`ConcurrentTypeRegistryDemo.java`**:

```java
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

public class ConcurrentTypeRegistryDemo {

    static final class Registry {
        private final ConcurrentMap<Class<?>, Object> values =
                new ConcurrentHashMap<>();

        private static <T> Class<T> checkType(Class<T> type) {
            Objects.requireNonNull(type, "type");

            if (type.isPrimitive()) {
                throw new IllegalArgumentException(
                        "Primitive class tokens not supported");
            }

            return type;
        }

        <T> T registerIfAbsent(Class<T> type, T value) {
            type = checkType(type);
            Objects.requireNonNull(value, "value");

            Object previous = values.putIfAbsent(
                    type, type.cast(value));

            return type.cast(previous);
        }

        <T> T get(Class<T> type) {
            type = checkType(type);
            return type.cast(values.get(type));
        }

        <T> T getOrCreate(
                Class<T> type,
                Supplier<? extends T> factory) {

            type = checkType(type);
            Objects.requireNonNull(factory, "factory");
            Class<T> checked = type;

            Object found = values.computeIfAbsent(
                    checked,
                    ignored -> checked.cast(
                            Objects.requireNonNull(factory.get())));

            return checked.cast(found);
        }
    }

    public static void main(String[] args) {
        Registry registry = new Registry();

        System.out.println(
                registry.registerIfAbsent(String.class, "first"));
        System.out.println(
                registry.registerIfAbsent(String.class, "second"));

        System.out.println(registry.get(String.class));
        System.out.println(
                registry.getOrCreate(Integer.class, () -> 5));
        System.out.println(
                registry.getOrCreate(Integer.class, () -> 99));
    }
}
```

Biên dịch/chạy:

```bash
javac ConcurrentTypeRegistryDemo.java
java ConcurrentTypeRegistryDemo
```

Stdout:

```text
null
first
first
5
5
```

Giải thích:

- `putIfAbsent` chỉ đặt `"first"` khi token `String.class` chưa có entry.
- Lần đăng ký `"second"` trả về giá trị đã lưu `"first"` thay vì ghi đè.
- `computeIfAbsent` tạo giá trị `Integer` khi chưa có; lần kế tiếp đọc giá trị cũ `5`.
- `Supplier<? extends T>` cho phép factory trả một subtype phù hợp.
- `Class.cast` xác minh class ở ranh giới ghi/đọc.

Đây là ví dụ tuần tự chạy `main` để có stdout xác định. Muốn khẳng định tính đúng đắn dưới concurrency thật, còn cần test stress phù hợp và thiết kế rõ lifecycle của các service values.

## 23. Lazy factories và scope của service registry

Một class-key registry thường dùng trong dependency injection hoặc service locator:

```java
registry.put(Logger.class, logger);
registry.put(Clock.class, clock);
```

Đây là code minh họa; giả định `Logger`/`Clock` phù hợp và đã được khởi tạo.

Một API có thể chọn:

- **Eager registration**: gọi `put` với value đã tạo.
- **Lazy creation**: `getOrCreate(token, supplier)` khi token chưa có value.
- **Per-request scope**: mỗi request có registry riêng.
- **Application scope**: một registry chung trong suốt vòng đời ứng dụng.

Chọn scope ảnh hưởng mạnh đến mutability và concurrency. Một singleton registry cho toàn ứng dụng có thể giữ service lâu hơn cần thiết. Một registry theo request đơn giản hơn về isolation nhưng không nên làm chậm hệ thống nếu dịch vụ khởi tạo tốn kém.

Nếu cần **đa dạng cấu hình** cho cùng một class (ví dụ hai database connections), class-key một mình không đủ để phân biệt; dùng named typed keys, qualifiers hoặc một service container có phạm vi/định danh.

## 24. `getRequired`: trả thông báo lỗi rõ khi thiếu entry

Một API `get(Class<T>)` trả `null` giúp linh hoạt, nhưng nhiều ứng dụng muốn thiếu service là lỗi cấu hình.

Có thể thêm:

```java
<T> T getRequired(Class<T> type) {
    T value = get(type);

    if (value == null) {
        throw new IllegalStateException(
                "No registration for " + type.getName());
    }

    return value;
}
```

Đây là method minh họa cần nằm trong class đã định nghĩa `get(type)`.

Ưu điểm:

- Caller không phải tự unbox/dereference null.
- Thông báo nêu **class token bị thiếu**.
- Dễ tìm lỗi cấu hình sớm.

Điều kiện: registry cần cấm null values để `null` có nghĩa chính xác là “chưa đăng ký”. Nếu cho phép null value, phải dùng `containsKey` để phân biệt.

Một phương thức thay thế:

```java
<T> Optional<T> find(Class<T> type)
```

tốt khi missing entry là trạng thái bình thường. Đừng ném exception cho một trường hợp “không tìm thấy” hoàn toàn được mong đợi nếu API có thể diễn đạt rõ bằng `Optional`.

## 25. Những giới hạn của registry class-key

Bảng quyết định:

| Nhu cầu | `Map<Class<?>, Object>` phù hợp? | Ghi chú |
|---|---|---|
| Một service instance cho mỗi runtime class token | Có | API `<T> get(Class<T>)` rõ và an toàn |
| Một `String` và một `Integer` cùng trong registry | Có | Hai class keys khác nhau |
| Một value dưới interface token và một value dưới implementation token | Có | Hai entries tách biệt |
| Tự tìm subtype từ base interface | Không tự động | Cần resolver và chính sách ambiguity |
| Hai giá trị `String` theo tên `first`, `last` | Không đủ | Thêm named typed key/qualifier |
| Phân biệt `List<String>` và `List<Integer>` | Không | `List.class` chung; cần generic type key/schema |
| Xác minh nội dung `List<String>` runtime | Không | `Class.cast(List.class)` chỉ kiểm tra raw `List` |
| Lưu dữ liệu primitive không boxing | Không trực tiếp | Values là reference objects |
| Đọc/ghi đồng thời | Tùy backing map | Cân nhắc `ConcurrentHashMap` và value thread safety |
| Tự tạo object từ class token | Không tự động | Cần factory, constructor reflection hoặc DI |
| Đảm bảo immutable values | Không | Cần contract/copy riêng |
| Dùng cùng class qua nhiều class loaders | Không đồng nhất | Runtime Class identity phụ thuộc loader |

Một API tốt nên công bố những giới hạn này để caller không dựa trên giả định quá mức về type safety.

## 26. Anti-pattern: raw `Class`, unchecked cast và map bị lộ

**Không nên** cung cấp backing map ra ngoài:

```java
Map<Class<?>, Object> mutableBacking() {
    return entries;
}
```

Caller có thể ghi `entries.put(String.class, 123)` và phá invariant mà public `put` đang cố bảo vệ.

**Không nên** dùng raw `Class` trong phương thức chính:

```java
Object get(Class type)
```

Dạng này mất mối quan hệ generic giữa token và result và buộc caller cast.

**Không nên** viết:

```java
<T> T get(Class<T> type) {
    return (T) entries.get(type);
}
```

rồi chỉ gắn `@SuppressWarnings("unchecked")` để bỏ cảnh báo. `Class.cast` thể hiện runtime validation rõ ràng hơn.

**Không nên** dùng `Class.forName(userInput).newInstance()` một cách mù quáng để tự đăng ký. Reflection construction có lỗi truy cập, thiếu constructor, tác dụng phụ và an ninh; ngay cả `Class.newInstance()` cũ cũng không phải API được khuyến khích.

**Không nên** cố nhét nhiều parameterized types dưới `List.class` rồi cho rằng map sẽ giữ từng type argument.

Những lỗi trên thường không lộ khi test đúng dữ liệu, nhưng trở thành bug tại ranh giới legacy/unchecked hoặc khi thiết kế lớn hơn.

## 27. Type safety không che giấu lỗi ngữ nghĩa

Một registry hoàn toàn an toàn về runtime class vẫn có thể sai **nghiệp vụ**.

Ví dụ:

```java
registry.put(Integer.class, 8080);
```

Về type, đây là một `Integer` hợp lệ. Nhưng đó là cổng HTTP, số người dùng hay số retries? Nếu hệ thống cần nhiều `Integer` khác nhau, `Integer.class` không phải key đủ rõ.

Tương tự, một `Clock` service có thể được đăng ký dưới `Clock.class` nhưng dùng timezone hoặc lịch giả lập không đúng cho nghiệp vụ. Class token chỉ kiểm tra loại đối tượng; nó không xác minh semantic correctness.

Khi những loại thông tin cùng class xuất hiện nhiều, dùng:

- Named typed key (tên + `Class<T>`).
- Qualifier annotations với registry có quy tắc xử lý.
- Các value classes/records chuyên biệt như `Port`, `RetryCount`, `Timeout`.
- Một container DI có scopes/qualifiers phù hợp.

Đây là **thiết kế API rõ nghĩa**, không phải một giới hạn ngôn ngữ có thể giải quyết bằng generic syntax.

## 28. Bài thực hành: kiểm tra các lựa chọn API

### Bài 1 — Vì sao field là `Map<Class<?>, Object>`?

**Đáp án:** Mỗi key thuộc một class khác nhau, mỗi value cũng thuộc class tương ứng. Không có một `T` chung cho toàn container; method-level `<T>` khôi phục quan hệ khi thao tác.

### Bài 2 — `put` sai kiểu có biên dịch không?

```java
registry.put(String.class, 42);
```

**Đáp án:** Không hợp lệ trong typed code thông thường vì generic inference không thể làm `42` tương thích `String` với `Class<String>`.

### Bài 3 — `get` có cần cast ở caller không?

```java
String s = registry.get(String.class);
```

**Đáp án:** Không. Chữ ký `<T> T get(Class<T>)` khiến compiler suy luận `T = String`.

### Bài 4 — `Class.cast` có từ chối null không?

**Đáp án:** Không; `String.class.cast(null)` trả null. Hãy tự dùng `Objects.requireNonNull` nếu muốn cấm null values.

### Bài 5 — Có một entry của String, lookup bằng CharSequence?

**Đáp án:** `get(CharSequence.class)` không tự tìm `String.class`. Keys khác nhau; chỉ trả value nếu đã đăng ký với chính `CharSequence.class`.

### Bài 6 — Có thể có hai entry String khác nhau theo class key không?

**Đáp án:** Không nếu map chỉ dùng `String.class` làm khóa; bản ghi mới thay bản ghi cũ. Cần typed named keys/qualifiers nếu muốn cả hai.

### Bài 7 — Có `List<String>.class` không?

**Đáp án:** Không. Java không tạo class literal cho parameterized type đó.

### Bài 8 — `List.class.cast(...)` xác minh phần tử String chưa?

**Đáp án:** Chưa. Nó chỉ kiểm tra object là `List`. Cần kiểm tra từng phần tử hoặc dùng decoder/schema khác nếu cần.

### Bài 9 — Vì sao không dùng `(T)` ở getter?

**Đáp án:** Cast tới type variable bị erasure, khó xác minh runtime class cụ thể. `type.cast(...)` sử dụng class token của caller để kiểm tra trước khi trả.

### Bài 10 — Registry concurrent có giải quyết thread safety của service object?

**Đáp án:** Không. `ConcurrentHashMap` bảo vệ thao tác map; value mutable vẫn cần cơ chế concurrency riêng.

### Bài 11 — `int.class` có nên là key cho `Integer`?

**Đáp án:** Không theo API mẫu. Dùng `Integer.class` làm key; `int.class` là primitive token, không thực hiện boxing trong `Class.cast`.

### Bài 12 — Nếu key thiếu và value null đều được cho phép?

**Đáp án:** Dùng `containsKey` hoặc result type rõ để phân biệt. `get == null` không đủ.

### Bài 13 — Plugin class loader ảnh hưởng class key thế nào?

**Đáp án:** Class cùng tên do loaders khác nhau định nghĩa có runtime Class identities khác nhau; key không tự trùng.

### Bài 14 — Khi nào nên tách registry thành named keys?

**Đáp án:** Khi nhiều values cùng runtime class cần phân biệt theo tên, scope hoặc qualifier nghiệp vụ.

### Bài 15 — Có thể lấy class token để tự tạo instance bằng constructor không?

**Đáp án:** Có thể dùng reflection với điều kiện class có constructor phù hợp và quyền truy cập thích hợp, nhưng registry không nên mặc định tự tạo mọi class khi thiếu entry.

## 29. Checklist đưa vào code thực tế

1. Viết hợp đồng rõ: **mỗi class token có tối đa một value** hay cần multiple keys?
2. Định nghĩa chính sách với **null token, null value, primitive class token**.
3. Đảm bảo backing map **private**, không expose đường ghi khác.
4. Dùng `<T> put(Class<T>, T)` và `<T> get(Class<T>)`.
5. Dùng `type.cast` ở ranh giới dữ liệu khi cần runtime validation.
6. Quy định `put` ghi đè hay `putIfAbsent`; có cần trả value cũ không?
7. Thiết kế API missing key: `get` nullable, `find` Optional, hoặc `getRequired` ném lỗi.
8. Phân biệt key interface với key implementation — lookup **theo key chính xác**.
9. Không giả định `List.class` phân biệt generic arguments.
10. Nếu phải lưu `List<String>` và `List<Integer>`, thiết kế generic `Type` key cùng validator thay vì unchecked cast.
11. Với concurrent access, dùng map/locks phù hợp; bảo vệ cả mutable values nếu cần.
12. Nếu factory lazy, kiểm tra exception, null và side effects.
13. Xác định lifecycle, clear/remove khi scope hoặc plugin kết thúc.
14. Tránh giữ Class tokens từ plugin loader lâu hơn cần thiết.
15. Đừng trộn class type safety với semantics của qualifiers hoặc keys.
16. Viết unit test cả **đường sai kiểu**, **key vắng**, **replace**, **null**, **interface token** và **repeated same-class keys**.
17. Không bỏ qua lỗi compiler bằng raw types.
18. Ghi tài liệu giải thích `Class<T>` giúp runtime class checking nhưng không reify parameterized types.

## 30. Tổng kết và tài liệu tham khảo

**Kho lưu trữ nhiều kiểu dùng `Class<T>` làm khóa** là một thiết kế hữu ích khi muốn giữ nhiều loại object trong cùng container mà caller vẫn nhận kết quả theo kiểu cụ thể:

```java
registry.put(String.class, "text");
registry.put(Integer.class, 7);

String text = registry.get(String.class);
Integer number = registry.get(Integer.class);
```

Cốt lõi là field nội bộ `Map<Class<?>, Object>` và các method-level type parameters liên kết **`Class<T>` với `T`**. `Class.cast` giúp kiểm tra runtime class tại đường ghi/đọc, còn map phải được giữ private để bảo toàn invariant.

Nhưng thiết kế này **không phải type token cho mọi parameterized type**: nó không phân biệt `List<String>` với `List<Integer>` bởi cả hai dùng `List.class`. Class key cũng không tự giải quyết nhiều entries cùng class, lookup theo subtype, mutation của values, chính sách null hay concurrency.

Chọn registry class-key khi **runtime class identity** đúng là key nghiệp vụ. Nếu bài toán cần named/qualified keys hoặc generic nested schemas, dùng abstraction mở rộng cùng validation tương ứng.

Tài liệu chuẩn:

- [Java SE 17 `Class<T>`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html): `cast`, `isInstance`, primitive tokens, class identity.
- [Java SE 17 `Map<K,V>`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Map.html): map key semantics, `put`, `get`, `containsKey`.
- [Java SE 17 `ConcurrentHashMap`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentHashMap.html): thread-safe map operations và `computeIfAbsent`.
- [Java SE 17 `Optional<T>`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Optional.html): biểu diễn kết quả có thể vắng.
- [Java Language Specification §4.5 — Parameterized Types](https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html#jls-4.5): type arguments và invariance.
- [Java Language Specification §4.6 — Type Erasure](https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html#jls-4.6): giới hạn runtime của Generics.
- [Java Language Specification §4.7 — Reifiable Types](https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html#jls-4.7): vì sao `List<String>` không có class literal riêng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="generic-api-design-007">Khoanh vùng cast không kiểm tra và nêu bất biến bảo đảm an toàn</a>

<details>
<summary>Xem chi tiết</summary>

# Khoanh vùng cast không kiểm tra và nêu bất biến bảo đảm an toàn trong Java Generics

Khi thiết kế một API generic, đôi lúc bạn phải tích hợp mã cũ dùng **raw type**, lưu dữ liệu bằng `Object[]`, nhận một `Object` từ nguồn bên ngoài hoặc chuyển một tham chiếu chưa đủ thông tin kiểu về dạng tham số hóa. Java có thể cảnh báo **unchecked cast**: compiler không chứng minh đầy đủ được kiểu đích ở runtime, đặc biệt khi kiểu đích chứa đối số generic đã bị *type erasure*.

Phản ứng phù hợp **không phải** là gắn `@SuppressWarnings("unchecked")` lên toàn bộ class hoặc dùng một cast lớn rồi hy vọng. Thiết kế tốt phải giải quyết đồng thời hai việc:

1. **Khoanh vùng phép cast** tại một biên rõ ràng, càng nhỏ càng tốt: một biến local, một phương thức chuyển đổi nội bộ, hoặc một adapter dành riêng cho API legacy.
2. **Nêu và duy trì bất biến (*invariant*)** chứng minh cast an toàn trong phạm vi được giả định: dữ liệu đến từ đâu, ai được phép ghi, kiểu nào được phép lưu, liệu tham chiếu có rò rỉ, thời điểm nào dữ liệu được xác minh và có đường nào phá hợp đồng không.

Ví dụ:

```java
// Tóm tắt ý tưởng của một class generic:
class Buffer<T> {
    private final Object[] data = new Object[8];

    void set(int index, T value) {
        data[index] = value;
    }

    T get(int index) {
        // Bất biến cần chứng minh: phần tử tại vị trí hợp lệ đã
        // được ghi bằng T qua những đường được class kiểm soát.
        @SuppressWarnings("unchecked")
        T value = (T) data[index];
        return value;
    }
}
```

Đoạn này **chỉ minh họa vị trí cast**, chưa xử lý `size`, chỉ số hoặc phần tử chưa khởi tạo. Chương trình hoàn chỉnh trong bài sẽ xử lý các điểm đó. Nếu bạn không thể đưa ra chứng minh cho bất biến, bạn không có cơ sở để gọi cast là an toàn chỉ vì warning đã biến mất.

## 1. Hiểu đúng “unchecked” trong Java Generics

Xét một giá trị không biết nguồn gốc:

```java
Object input = java.util.List.of("An", 42);

// KHÔNG có phép kiểm tra runtime đầy đủ List<String>:
@SuppressWarnings("unchecked")
java.util.List<String> names = (java.util.List<String>) input;
```

Cast này có thể thành công vì object thật là một `List`; JVM không quét từng phần tử để xác minh đối số kiểu `String`. Khi sau đó đọc phần tử thứ hai dưới kiểu `String`, lỗi có thể xuất hiện:

```java
// String second = names.get(1); // ClassCastException
```

Đừng nhầm ba loại tình huống:

| Tình huống | JVM/compiler kiểm tra được gì? | Nhận xét |
| --- | --- | --- |
| `(String) input` | Runtime class có tương thích `String` | Checked cast thông thường |
| `(List<?>) input` | Runtime object có là `List` | Không kiểm tra kiểu từng phần tử |
| `(List<String>) input` | Không xác minh đầy đủ đối số kiểu `String` | Có thể có unchecked warning |
| `(T) item` trong method generic | Thường chỉ theo erasure/bound của `T` | Cần invariant hoặc runtime token |
| `(String[]) array` | Runtime component type của chính mảng | Khác cast tới generic `List<String>` |

**Unchecked warning không có nghĩa mọi cast đều sai.** Nó nói rằng **compiler không thể chứng minh trọn vẹn điều bạn đang khẳng định**. Có những vị trí nội bộ mà người triển khai giữ invariant đủ mạnh để biện minh cho cast; cũng có những cast trực tiếp từ dữ liệu không đáng tin mà không một lời chú thích nào làm chúng trở nên an toàn.

### Erasure giải thích vì sao compiler cần cảnh báo

Trong generics thông thường, `List<String>` và `List<Integer>` không có hai runtime class `List` chuyên biệt. `List<String>.class` không tồn tại. Vì vậy, kiểm tra runtime kiểu bên ngoài không thay thế kiểm tra type arguments bên trong.

Cũng vì vậy, một `Class<T>` có thể xác minh **một runtime class cụ thể** như `String.class`, nhưng không đủ để kiểm tra một cấu trúc lồng nhau như `Map<String,List<Integer>>` chỉ bằng `Class.cast`.

## 2. Một phép cast được biện minh bằng điều gì?

Để đánh giá một unchecked cast, nên viết một câu chứng minh cụ thể:

> Mọi phần tử được đọc tại vị trí hợp lệ của vùng lưu trữ này được ghi trước đó bằng các thao tác nhận `T`; vùng lưu trữ không bị công khai dưới dạng alias có thể sửa; không có đường ghi kiểu rộng hơn `T` không qua kiểm tra; việc đọc được thực hiện theo hợp đồng đồng bộ hóa đã công bố.

Từ câu đó, có các nghĩa vụ cần kiểm tra:

1. **Nguồn dữ liệu:** giá trị được tạo trong code typed hay lấy từ `Object`/raw/JSON/legacy API? Nếu chưa đáng tin, kiểm tra ở biên nhập.
2. **Đường ghi:** toàn bộ `add`, `set`, `replace`, deserialize, reflection wrapper có thật sự bảo toàn điều kiện kiểu không?
3. **Đường đọc:** chỉ đọc phần tử đã được khởi tạo trong vùng chỉ số hợp lệ, không nhầm `capacity` với `size`.
4. **Đóng gói:** nội bộ `Object[]` hoặc collection có bị trả ra để caller tự sửa không?
5. **Aliasing:** có raw alias, unchecked alias, iterator/container view hoặc tham chiếu tới backing collection nào có thể phá invariant không?
6. **Null policy:** generic `T` thường có thể nhận `null`; API có cho phép null không và đã thực thi nhất quán chưa?
7. **Concurrent modification:** nếu nhiều thread thao tác đồng thời, có đồng bộ để giữ atomicity/visibility cho trạng thái cần thiết không?
8. **Phạm vi cam kết:** proof giả định caller tuân thủ type system thông thường, hay API phải chống dữ liệu từ mã legacy không đáng tin?
9. **Thời điểm xác minh:** dữ liệu sau khi kiểm tra có còn có thể bị sửa qua alias khác không?
10. **Đầu ra:** return type có hứa thứ mà runtime object không có, ví dụ `String[]` nhưng object thật là `Object[]), không?

Chỉ khi biết rõ **điều kiện đầu vào**, **cơ chế giữ invariant** và **những đường có thể phá invariant**, mới có thể đánh giá một cast không kiểm tra có đáng chấp nhận hay không.

## 3. Khoanh vùng `@SuppressWarnings("unchecked")` tại nơi thật sự cần

So sánh ba phạm vi:

```java
// Không nên mặc định dùng phạm vi quá rộng:
@SuppressWarnings("unchecked")
class BroadSuppression<T> {
    // Các cast mới trong cả class cũng có thể mất cảnh báo.
}
```

```java
// Nhỏ hơn, nhưng có thể vẫn che các unchecked operations khác:
@SuppressWarnings("unchecked")
T get(int index) {
    return (T) elements[index];
}
```

```java
// Nên ưu tiên khi có invariant bảo đảm:
T get(int index) {
    java.util.Objects.checkIndex(index, size);

    @SuppressWarnings("unchecked")
    T value = (T) elements[index];

    return value;
}
```

Annotation ở biến local làm ý định hiển thị ngay cạnh cast và cho các warning phát sinh nơi khác **tiếp tục được compiler báo**.

**Nguyên tắc review:** mỗi lần dùng `@SuppressWarnings("unchecked")` nên có một **lý do có thể kiểm tra**, không chỉ “compiler cứ cảnh báo”. Nếu có thể thay cast bằng generic API chuẩn, wildcard `?` hoặc `Class<T>.cast` mà không làm sai contract, thường nên loại bỏ cast ngay từ đầu.

### `rawtypes` khác `unchecked`

```java
java.util.List raw = new java.util.ArrayList(); // raw type
raw.add("An");                                  // unchecked call
```

Khi bật `-Xlint:rawtypes -Xlint:unchecked`, bạn có thể thấy cảnh báo thuộc hai loại khác nhau:

- **`rawtypes`**: đang dùng raw `List` thay vì parameterized type khi có thể.
- **`unchecked`**: lời gọi hoặc phép chuyển không được compiler xác minh đầy đủ.

Không nên gắn suppression `unchecked` trên cả class để che các raw-type warnings; nên **sửa thiết kế nhập/xuất của API trước**, rồi khoanh vùng thật hẹp chỗ không thể tránh.

## 4. Bất biến điển hình cho `Object[]` làm backing storage

Trong một cấu trúc dữ liệu generic, Java cấm `new T[n]` vì `T` là non-reifiable. Nhưng `Object[]` nội bộ được phép:

```java
class SimpleStore<T> {
    private Object[] slots = new Object[4];
    private int size;

    void add(T value) {
        slots[size++] = value;
    }

    T get(int index) {
        @SuppressWarnings("unchecked")
        T item = (T) slots[index];
        return item;
    }
}
```

Đoạn trên **chưa đầy đủ**: có thể bị tràn mảng hoặc đọc vị trí chưa dùng. Muốn giải thích cast an toàn, invariant phải gồm cả ràng buộc cấu trúc:

- `0 <= size <= slots.length`.
- Các vị trí `slots[0]` đến `slots[size-1]` là phần tử logic đã được ghi qua API type-safe của `Store<T>`.
- `slots` không bị trả ra ngoài; không có method nhận `Object` rồi ghi không kiểm tra.
- `get(i)` chỉ chấp nhận `0 <= i < size`.
- Thao tác resize giữ nguyên dữ liệu, kiểu và thứ tự các vị trí logic.
- Nếu `null` bị cấm, mọi thao tác ghi phải kiểm tra; nếu `null` hợp lệ, method đọc phải giải thích cách phân biệt phần tử null với vị trí chưa dùng.
- Code sử dụng không cố ý phá generic contract bằng raw/unchecked operations.

Có thể kiểm soát các điều kiện này bằng việc **giữ storage private**, chỉ công khai phương thức typed, kiểm tra chỉ số và không để alias có quyền sửa lộ ra ngoài.

### Tại sao cast ở `get` hợp lý hơn cast cả array?

Khi invariant đúng, một **phần tử** trong array thực tế là object phù hợp với `T`. Nhưng object array được tạo bởi `new Object[n]` có runtime class **`Object[]`**, không biến thành `String[]` khi `T = String`.

```java
// Trong một lớp generic lưu private Object[]:
@SuppressWarnings("unchecked")
T[] wrong() {
    return (T[]) slots; // Không phải lời hứa runtime T[] an toàn
}
```

Nếu caller cần `String[]` mà `slots` là `Object[]`, runtime có thể ném `ClassCastException` tại điểm nhận giá trị. Cast từng phần tử và trả một **`T`** dựa trên invariant khác hoàn toàn với việc gắn một **runtime component class không có thật** cho mảng.

## 5. Giới hạn của bất biến typed API: raw alias vẫn có thể phá

Một `Buffer<String>` có phương thức `add(T)` được compiler kiểm tra khi caller dùng parameterized type:

```java
Buffer<String> strings = new Buffer<>();
strings.add("Java");
// strings.add(42); // Không biên dịch
```

Nhưng nếu caller cố ý sử dụng raw type:

```java
Buffer raw = strings;
raw.add(42); // Unchecked call warning
```

Thao tác này có thể đưa `Integer` vào bộ lưu trữ `Object[]` của `Buffer<String>`. Khi caller đọc thành `String`, compiler có thể chèn cast và gây `ClassCastException`.

Điều phải ghi trong lập luận an toàn là: **bất biến được bảo vệ cho những lần sử dụng typed bình thường**. Nó không bảo đảm chống mọi mã cố ý phá hệ thống kiểu bằng raw/unchecked alias, reflection tùy quyền hoặc unsafe native operations.

Nếu thư viện phải dùng trong môi trường có dữ liệu từ plugin/mã legacy không đáng tin, có thể cần **runtime validation khi nhập** bằng `Class<T>` token, validator hoặc định dạng/schema cụ thể, thay vì chỉ dựa vào generic `T` ở compile time.

Đừng viết “private field nên hoàn toàn không thể bị heap pollution” khi `add(T)` vẫn có thể bị gọi qua raw alias: private chỉ bảo vệ **truy cập trực tiếp vào field**, không chặn mọi đường gọi vào API bị làm mất tham số kiểu.

## 6. Biên dữ liệu chưa tin cậy: kiểm tra, không chỉ cast

Giả sử API nhận `Object input` từ reflection hoặc một hệ thống cũ. Cách làm sai:

```java
@SuppressWarnings("unchecked")
static <T> java.util.List<T> assume(Object input) {
    return (java.util.List<T>) input;
}
```

Phương thức này thực chất hứa trả `List<T>` dù không kiểm tra **bất kỳ phần tử nào**. Ngay cả khi object bên ngoài là `List`, một phần tử sai có thể gây lỗi ở caller sau đó. Nó cũng chưa phân biệt đúng lỗi nếu `input` không phải list.

Cách nên dùng khi cần một list có phần tử thuộc class runtime cụ thể:

```java
static <T> java.util.List<T> checkedCopy(
        Object input, Class<T> elementType) {
    java.util.Objects.requireNonNull(elementType, "elementType");

    if (!(input instanceof java.util.List<?> source)) {
        throw new IllegalArgumentException("not a List");
    }

    java.util.List<T> result =
            new java.util.ArrayList<>(source.size());

    for (Object item : source) {
        result.add(elementType.cast(
                java.util.Objects.requireNonNull(item, "element")));
    }

    return java.util.List.copyOf(result);
}
```

Từng bước đều có ý nghĩa:

1. **Kiểm tra cấu trúc ngoài**: `instanceof List<?>` xác nhận object là một `List`, không xác nhận kiểu phần tử.
2. **Yêu cầu token**: `Class<T> elementType` cho biết runtime class cần chấp nhận, ví dụ `String.class`.
3. **Kiểm tra mỗi phần tử**: `elementType.cast(item)` kiểm tra object tương thích với runtime class mong muốn. Giá trị sai bị báo ngay tại biên.
4. **Quy định null policy**: `requireNonNull(item)` khiến dữ liệu `null` bị từ chối rõ ràng, không trông chờ lỗi ở `List.copyOf` cuối phương thức.
5. **Sao chép**: trả một list mới không thể sửa cấu trúc để không tiếp tục phụ thuộc vào cấu trúc mutable của nguồn sau lần xác thực.
6. **Không có unchecked cast**: type safety đến từ thao tác `Class<T>.cast` trả về `T` cho từng item đã được kiểm tra.

Đây là một lựa chọn **hợp đồng API**, không bắt buộc với mọi nguồn. Nếu cho phép `null`, hãy chọn chính sách và cách xây kết quả khác, vì `List.copyOf` không chấp nhận null.

### `Class<T>` không đủ cho generics lồng nhau

`String.class` cho phép kiểm tra một phần tử kiểu `String`; nhưng không có `List<String>.class`. Vì vậy, nếu cần kiểm tra `List<List<String>>` hoặc `Map<String, List<Integer>>`, phải dùng validator/schema biết duyệt từng tầng; một `Class<List>` kiểu raw chỉ kiểm tra cấu trúc ngoài.

**Không giả vờ rằng `Type` hay `ParameterizedType` tự kiểm tra dữ liệu.** Reflection metadata mô tả khai báo/type schema; thư viện muốn xác minh dữ liệu phải thực thi validation phù hợp với schema đó.

## 7. Giữ hợp đồng an toàn khi dùng `List.copyOf` hoặc immutable snapshot

Nếu vừa nhận một `List<?> source` có thể được sửa qua alias khác, thao tác “validate toàn bộ rồi cast tham chiếu gốc thành `List<T>`” vẫn có rủi ro:

```java
// KHÔNG NÊN làm để công khai dữ liệu không đáng tin:
boolean allStrings = source.stream().allMatch(String.class::isInstance);

// Ngay sau đó source có thể bị sửa qua alias khác.
// @SuppressWarnings("unchecked")
// List<String> result = (List<String>) source;
```

Dù ở một thời điểm tất cả phần tử đều là `String`, **sự thật này không tự chuyển `List<?>` thành `List<String>` ở compile time**. Hơn nữa, nếu list mutable vẫn được chia sẻ, invariant đã kiểm tra có thể trở nên sai sau đó.

Sao chép từng phần tử **sau khi kiểm tra** sang container mới có kiểu `T` là một hướng tốt hơn. `List.copyOf` trong `checkedCopy` giúp tách cấu trúc kết quả khỏi nguồn, nhưng lưu ý:

- `List.copyOf` cho list không thể sửa cấu trúc, **không deep-copy các object phần tử mutable**.
- Nếu list nguồn bị sửa **trong lúc duyệt**, có thể xảy ra lỗi, đọc không nhất quán hoặc behavior khác tùy implementation. Không tuyên bố rằng riêng việc dùng `List.copyOf` tạo ra snapshot atomic trước mọi concurrent modification.
- Nếu muốn hỗ trợ dữ liệu đến từ nhiều thread, cần quy định concurrency policy và lấy snapshot nhất quán theo cơ chế đồng bộ phù hợp.

Một API an toàn cần nêu rõ **thời điểm kiểm tra**, **đối tượng sở hữu kết quả** và **những ràng buộc của dữ liệu trong tương lai**.

## 8. Wrapper `Collections.checkedList` hữu ích ở đâu?

Java cung cấp những wrapper kiểm tra runtime class của phần tử lúc có thao tác ghi qua wrapper:

```java
java.util.List<String> backing = new java.util.ArrayList<>();
java.util.List<String> checked =
        java.util.Collections.checkedList(backing, String.class);

checked.add("ok");

java.util.List raw = checked;
// raw.add(42); // Có unchecked warning; wrapper ném ClassCastException
```

Ưu điểm: nếu mã cũ gọi `add` qua **chính checked wrapper**, một phần tử runtime không tương thích với `String` sẽ bị chặn gần biên ghi.

Nhưng invariant cần nêu rõ:

- Wrapper **không sửa chữa** nội dung sai kiểu đã có trong backing list từ trước khi bọc.
- Một **alias khác** còn trỏ trực tiếp tới `backing` có thể bỏ qua wrapper và gây heap pollution.
- Wrapper cũng không tự biến generics của Java thành reified generics; `String.class` chỉ kiểm tra một runtime class cụ thể.
- Wrapper không mặc định làm backing list thread-safe hoặc immutable.

Do đó, `checkedList` là một **runtime guard tại các thao tác mà nó kiểm soát**, không thay cho việc kiểm soát quyền sở hữu backing collection hoặc xác thực nguồn dữ liệu không đáng tin.

## 9. Array factory: khi cast phần tử khác cast cả mảng

Nếu cần trả mảng typed thật cho caller, đừng viết:

```java
// Sai mục tiêu runtime nếu slots thực tế là new Object[n]:
@SuppressWarnings("unchecked")
T[] expose() {
    return (T[]) slots;
}
```

Thay vào đó, yêu cầu caller cung cấp một array factory:

```java
T[] toArray(java.util.function.IntFunction<T[]> factory) {
    java.util.Objects.requireNonNull(factory, "factory");

    T[] output = java.util.Objects.requireNonNull(
            factory.apply(size), "array");

    if (output.length != size) {
        throw new IllegalArgumentException("length");
    }

    System.arraycopy(slots, 0, output, 0, size);
    return output;
}
```

Khi `T = String`, caller dùng:

```java
String[] names = buffer.toArray(String[]::new);
```

Kết quả là `String[]` **thật**, vì `String[]::new` tạo mảng có runtime component type `String`. `System.arraycopy` sẽ áp dụng array store checks với mảng đích; nó không đổi runtime type của backing `Object[]`.

Tuy nhiên, array factory cũng có một **hợp đồng**: nó phải cấp phát mảng phù hợp `T[]` với kích thước được yêu cầu. Trong code trên, `null` và sai độ dài bị từ chối. Nếu caller dùng raw/unchecked để đưa factory tạo mảng sai kiểu, runtime có thể phát hiện mismatch; không nên tuyên bố method chống được mọi factory không đáng tin.

## 10. Chương trình Java 17+ hoàn chỉnh: an toàn, biên kiểm tra và phản ví dụ

Lưu thành **`UncheckedBoundaryDemo.java`**:

```java
import java.util.*;
import java.util.function.IntFunction;

public class UncheckedBoundaryDemo {
    static final class Buffer<T> {
        private Object[] slots = new Object[2];
        private int size;

        void add(T value) {
            if (size == slots.length) {
                slots = Arrays.copyOf(slots, slots.length * 2);
            }
            slots[size++] = value;
        }

        T get(int index) {
            Objects.checkIndex(index, size);

            // Invariant: slots[0..size) are written via add(T)
            // under parameterized use; storage does not escape.
            @SuppressWarnings("unchecked")
            T value = (T) slots[index];

            return value;
        }

        T[] toArray(IntFunction<T[]> arrayFactory) {
            Objects.requireNonNull(arrayFactory, "arrayFactory");

            T[] result = Objects.requireNonNull(
                    arrayFactory.apply(size), "array");

            if (result.length != size) {
                throw new IllegalArgumentException("length");
            }

            System.arraycopy(slots, 0, result, 0, size);
            return result;
        }
    }

    static <T> List<T> checkedCopy(
            Object candidate, Class<T> elementType) {
        Objects.requireNonNull(elementType, "elementType");

        if (!(candidate instanceof List<?> source)) {
            throw new IllegalArgumentException("not a List");
        }

        List<T> result = new ArrayList<>(source.size());

        for (Object value : source) {
            result.add(elementType.cast(
                    Objects.requireNonNull(value, "element")));
        }

        return List.copyOf(result);
    }

    // Cố ý sai: cast này không bảo đảm phần tử thuộc T.
    static <T> List<T> assumedList(Object candidate) {
        if (!(candidate instanceof List<?>)) {
            throw new IllegalArgumentException("not a List");
        }

        @SuppressWarnings("unchecked")
        List<T> result = (List<T>) candidate;

        return result;
    }

    public static void main(String[] args) {
        Buffer<String> buffer = new Buffer<>();
        buffer.add("An");
        buffer.add("Binh");

        System.out.println("Buffer read: " + buffer.get(0));

        String[] copy = buffer.toArray(String[]::new);
        System.out.println("Typed array: " + Arrays.toString(copy));
        System.out.println("Array type: "
                + copy.getClass().getTypeName());

        List<String> safe =
                checkedCopy(List.of("A", "B"), String.class);
        System.out.println("Validated list: " + safe);

        List<?> mixed = List.of("A", 42);

        try {
            checkedCopy(mixed, String.class);
        } catch (ClassCastException ex) {
            System.out.println("Mixed input: ClassCastException");
        }

        try {
            checkedCopy(List.of("ok"), Integer.class);
        } catch (ClassCastException ex) {
            System.out.println("Wrong token: ClassCastException");
        }

        try {
            checkedCopy(List.of("A"), null);
        } catch (NullPointerException ex) {
            System.out.println("Missing token: NullPointerException");
        }

        List<String> wronglyAssumed =
                UncheckedBoundaryDemo.<String>assumedList(mixed);

        try {
            String value = wronglyAssumed.get(1);
            System.out.println(value);
        } catch (ClassCastException ex) {
            System.out.println(
                    "Unchecked assumption: ClassCastException");
        }

        // Cố ý phá invariant bằng raw alias:
        Buffer<String> polluted = new Buffer<>();
        Buffer raw = polluted;
        raw.add(42);

        try {
            String value = polluted.get(0);
            System.out.println(value);
        } catch (ClassCastException ex) {
            System.out.println("Raw alias: ClassCastException");
        }

        List<String> backing = new ArrayList<>(List.of("ok"));
        List<String> checked =
                Collections.checkedList(backing, String.class);

        List rawChecked = checked;

        try {
            rawChecked.add(42);
        } catch (ClassCastException ex) {
            System.out.println("Checked wrapper: ClassCastException");
        }

        System.out.println("Backing after blocked add: " + backing);
    }
}
```

Biên dịch và chạy trên JDK 17 trở lên:

```bash
javac -encoding UTF-8 -Xlint:rawtypes -Xlint:unchecked UncheckedBoundaryDemo.java
java UncheckedBoundaryDemo
```

**Standard output** của chương trình (không gồm compiler warnings):

```text
Buffer read: An
Typed array: [An, Binh]
Array type: java.lang.String[]
Validated list: [A, B]
Mixed input: ClassCastException
Wrong token: ClassCastException
Missing token: NullPointerException
Unchecked assumption: ClassCastException
Raw alias: ClassCastException
Checked wrapper: ClassCastException
Backing after blocked add: [ok]
```

**Đọc kết quả theo ranh giới niềm tin:**

1. `Buffer<String>` ban đầu chỉ nhận chuỗi qua `add(T)`, không để lộ `slots`, và `get` đọc đúng vùng `size`. Unchecked cast nội bộ được gắn với invariant cụ thể.
2. `toArray(String[]::new)` tạo mảng typed thật; backing array vẫn là `Object[]` và không bị trả trực tiếp.
3. `checkedCopy` xác minh từng phần tử từ nguồn `Object` theo `String.class` hoặc `Integer.class`; dữ liệu sai được báo **ngay tại biên chuyển đổi**.
4. `assumedList` chỉ xác nhận object bên ngoài là `List` và cast unchecked, **không kiểm tra phần tử**; lỗi xuất hiện khi bên gọi đọc `Integer` như `String`.
5. Raw `Buffer` bypass generic compile-time check để ghi `Integer` vào buffer vốn được dùng dưới kiểu `Buffer<String>`. Cast nội bộ không thể cứu invariant đã bị phá.
6. `Collections.checkedList` phát hiện `Integer` khi raw alias cố gọi `add` qua wrapper; backing list giữ nguyên `[ok]`.

Chương trình có hai raw aliases **cố ý dùng để minh họa lỗi**, nên `-Xlint:rawtypes -Xlint:unchecked` được kỳ vọng báo warnings ở các dòng đó. Việc ghi rõ chỗ nào không an toàn quan trọng hơn việc cố làm cho build không còn bất cứ warning nào bằng suppression rộng.

## 11. `Class<T>` không phải phép “khôi phục” toàn bộ generics

Một API `Class<T>` rất hữu ích khi kiểu mong muốn là runtime class cụ thể:

```java
static <T> T verified(Object candidate, Class<T> type) {
    return type.cast(candidate);
}

String name = verified("Java", String.class);
```

Nếu truyền một `Integer` cho `String.class`, `ClassCastException` xảy ra tại `cast`. Không cần unchecked cast.

Tuy nhiên, không tồn tại `List<String>.class`. Với một danh sách generic:

```java
Object input = List.of("A", 42);

boolean outer = input instanceof List<?>; // true
```

`outer` chỉ chứng minh kiểu tổng thể. Muốn khôi phục một hợp đồng dữ liệu phức tạp, cần validator có khả năng đi qua các lớp lồng nhau và giải thích chính sách null/covariance/mutability tương ứng.

Nếu hệ thống dùng reflection `Type`/`ParameterizedType` hoặc một type token để mô tả `List<String>`, metadata đó giúp xác định **schema** muốn kiểm tra, nhưng nó vẫn không tự duyệt và xác nhận object theo schema. Phải tách **đặc tả kiểu** khỏi **thực thi validation**.

## 12. Với legacy API, đặt trust boundary ở đâu?

Một ứng dụng có thể phải gọi một thư viện cũ trả raw `List`:

```java
// Chữ ký legacy minh họa:
static java.util.List loadLegacy() {
    return java.util.List.of("An", "Binh");
}
```

Sai lầm thường gặp là rải cast `(List<String>) loadLegacy()` ở nhiều tầng, rồi cho toàn bộ project sử dụng `List<String>` như thể dữ liệu đã được kiểm tra.

Một hướng là tạo **adapter duy nhất**:

```java
static List<String> loadNames() {
    return checkedCopy(loadLegacy(), String.class);
}
```

Adapter này là ranh giới rõ ràng: sau khi nó trả kết quả, caller nhận một snapshot typed do method tạo, và không cần biết nguồn legacy ban đầu raw.

Tài liệu hóa adapter:

- Nguồn raw có thể không phải list hoặc có phần tử sai kiểu.
- Phần tử sai kiểu gây `ClassCastException` ở bước `Class.cast`.
- Phần tử null gây `NullPointerException` theo chính sách đã chọn.
- Danh sách kết quả không thể sửa cấu trúc, nhưng các object phần tử mutable không tự được deep-copy.
- Nếu dữ liệu nguồn bị sửa đồng thời trong quá trình duyệt, adapter không cam kết snapshot atomic nếu chưa có chính sách đồng bộ.

Với nguồn legacy đã được tin cậy bởi một hợp đồng **được chứng minh riêng**, có thể có chỗ dùng cast unchecked nội bộ; nhưng nên ghi cụ thể **ai là producer, dữ liệu được bảo vệ bằng gì và điều kiện nào khiến giả định mất hiệu lực**.

## 13. Các bất biến dễ tưởng đúng nhưng thực tế chưa đủ

### 13.1. “Danh sách hiện tại chứa toàn String”

Bất biến này nói về **một thời điểm**, không đủ nếu có raw alias hoặc thread khác có thể thêm `Integer` ngay sau khi kiểm tra.

Cần thêm điều kiện **không còn đường mutation ngoài kiểm soát** hoặc phải tạo snapshot với cơ chế đồng bộ thích hợp.

### 13.2. “Mọi lần gọi `add` nhận `T`”

Đúng với caller dùng `Buffer<String>` typed, nhưng raw alias `Buffer raw` vẫn có thể gọi `add(42)` với warning. Nếu ranh giới an toàn buộc phải chống cả raw callers, nên dùng runtime token để xác thực ở chỗ ghi.

### 13.3. “Mảng có các phần tử String nên là String[]”

Sai. Mảng tạo bằng `new Object[n]` vẫn có runtime component type `Object`, kể cả khi mọi phần tử đều là `String`. Cast cả mảng sang `String[]` có thể gây `ClassCastException`.

### 13.4. “Annotation suppression chỉ dùng ở một dòng nên cast chắc chắn an toàn”

Sai. **Phạm vi nhỏ giúp audit** và tránh che các warning khác; nó không chứng minh nội dung dữ liệu. Cần kiểm tra invariant trước khi suppress.

### 13.5. “Wrapper checked bảo vệ cả backing list”

Không nếu vẫn còn một alias trực tiếp của backing list có thể sửa. `Collections.checkedList` kiểm tra các thao tác **qua wrapper**, không tự giám sát mọi tham chiếu khác tới cùng object.

### 13.6. “Sao chép dữ liệu là đủ cho thread safety”

Sao chép từng phần tử tạo một kết quả riêng về cấu trúc, nhưng nếu nguồn bị ghi đồng thời khi đang duyệt thì snapshot có thể không nhất quán. Cần quy định đồng bộ hoặc cấu trúc immutable phù hợp khi yêu cầu đa luồng.

## 14. Generic arrays và varargs: trường hợp suppression cần đánh giá riêng

Một lớp generic không được phép viết `new T[n]` vì `T` non-reifiable. Cách dùng `Object[]` nội bộ cộng cast từng phần tử không tương đương việc tạo `T[]` thật.

Một generic varargs method cũng có thể tạo cảnh báo:

```java
static <T> void visit(T... values) {
    for (T value : values) {
        System.out.println(value);
    }
}
```

Varargs được truyền dưới dạng array. Khi element type non-reifiable, compiler có thể cảnh báo về khả năng **heap pollution** và generic array creation.

Nếu nội dung thực sự an toàn và declaration thuộc loại được phép, `@SafeVarargs` có thể được dùng cho constructor hoặc method `static`, `final`, `private` (với điều kiện phiên bản Java tương ứng):

```java
@SafeVarargs
static <T> void printAll(T... values) {
    for (T value : values) {
        System.out.println(value);
    }
}
```

`@SafeVarargs` là **lời cam kết của tác giả**, không khiến `T` reifiable và không sửa được thao tác ghi sai vào varargs array qua `Object[]` alias.

Khi audit cast hoặc generic arrays, hãy xét riêng:

- Kiểu mảng được tạo thực tế (`Object[]`, `String[]` hay `List[]`).
- Mảng có bị lộ/giữ alias hay không.
- Có phép ghi sai kiểu qua raw hoặc `Object[]` hay không.
- Có đủ điều kiện để cam kết `@SafeVarargs` hay không.

Không thêm `@SafeVarargs` hoặc `@SuppressWarnings` chỉ vì muốn tắt warnings mà không phân tích phần thân method.

## 15. Bounded generics không tự tạo một runtime class cho `T`

Ví dụ:

```java
static <T extends Number> T identity(T value) {
    return value;
}
```

Bound `Number` giúp compiler biết có thể dùng các thao tác được khai báo ở `Number`, và erasure của `T` dựa trên bound đó. Nó **không** làm `T` thành một runtime class cụ thể như `Integer` ở mọi lần gọi.

Nếu một generic adapter viết:

```java
@SuppressWarnings("unchecked")
static <T extends Number> T assumeNumber(Object value) {
    return (T) value;
}
```

Cast có thể chỉ kiểm tra đối tượng tương thích `Number`. Nếu caller kỳ vọng `Integer` nhưng dữ liệu thực là `Double`, lỗi có thể xuất hiện khi caller tiêu thụ kết quả.

Tương tự, một type parameter `T extends Comparable<? super T>` cho phép gọi `compareTo` theo quan hệ generic ở compile time, nhưng không xác minh mọi object chưa đáng tin thuộc đúng type argument của một container.

Khi biên nhận dữ liệu cần kiểm tra subtype cụ thể, dùng `Class<T>` hoặc validator phù hợp thay vì suy luận rằng có bound là đủ.

## 16. Review checklist: một unchecked cast đủ điều kiện merge chưa?

Hãy kiểm tra những câu sau trước khi phê duyệt code:

1. **Vì sao compiler không thể tự chứng minh?** Do erasure, raw legacy boundary, generic backing array hay một điểm khác?
2. **Có tránh được cast không?** Dùng `List<?>`, `Class<T>.cast`, typed copy, factory, wildcard PECS hoặc generic API chuẩn.
3. **Nếu không tránh được, cast nằm ở đâu?** Một biến local/phương thức adapter nhỏ có phải vị trí hợp lý nhất không?
4. **Bất biến viết thành lời có cụ thể không?** Ví dụ “mọi `slots[0..size)` được ghi qua `add(T)`, không có alias sửa storage”.
5. **Mọi đường ghi đã được liệt kê?** `set`, `addAll`, deserialize, listener/callback, raw API?
6. **Có nguy cơ raw alias/unchecked cast từ caller không?** Điều kiện tin cậy đã được giới hạn rõ chưa?
7. **Null được xử lý thế nào?** Cấm và báo ngay, hay cho phép nhất quán ở tất cả đường ghi?
8. **Storage có bị lộ không?** Return `Object[]`, array view, backing collection hoặc internal iterator mutable?
9. **Đọc đúng vùng logic không?** `size` và index check chính xác?
10. **Có concurrency policy không?** Nếu public API dự định thread-safe, proof về type safety phải tương thích với proof về đồng bộ.
11. **Cast được suppress ở scope nào?** Có che warnings khác ngoài ý muốn không?
12. **Tests có tình huống sai kiểu không?** Dữ liệu hỗn hợp, null, raw alias, array runtime mismatch, empty list?
13. **Có test API result không?** Kết quả là snapshot hay view; mutable hay unmodifiable; có ghi rõ trong Javadoc?
14. **Có bằng chứng runtime khi cần không?** `Class<T>.cast` hoặc validator thực sự kiểm tra dữ liệu ở biên?
15. **Có nên bật linter trong CI?** Ví dụ `javac -Xlint:unchecked -Xlint:rawtypes`, rồi xem xét cảnh báo ở từng vị trí.

Nếu không thể trả lời các câu về đường ghi và invariant, chưa nên coi một unchecked cast là đã được chứng minh an toàn.

## 17. Những lỗi phổ biến

**Lỗi 1 — “Unchecked cast đồng nghĩa code chắc chắn ném exception.”** Không. Compiler cảnh báo vì không thể chứng minh; cast có thể đúng hoặc sai tùy invariant/dữ liệu.

**Lỗi 2 — “Suppress warning sẽ khiến JVM kiểm tra generic type argument.”** Không. Annotation chỉ ảnh hưởng diagnostics của compiler.

**Lỗi 3 — “Một `List<String>` lấy bằng cast từ `Object` đã được xác thực.”** Không. Chỉ có thể đã kiểm tra kiểu ngoài `List`; từng phần tử chưa được kiểm chứng.

**Lỗi 4 — “`private Object[]` thì bất biến luôn được bảo đảm.”** Không. API raw `add` vẫn có thể làm hỏng nội dung; public getter để lộ alias cũng phá đóng gói.

**Lỗi 5 — “Cast `(T[]) new Object[n]` là cách tạo mảng đúng runtime type.”** Không. Với `T = String`, object vẫn là `Object[]`.

**Lỗi 6 — “Đã validate toàn bộ list thì cast tham chiếu gốc mãi mãi an toàn.”** Không nếu list mutable và có alias khác. Bản sao có kiểm tra giải quyết rủi ro cấu trúc tốt hơn, nhưng còn yêu cầu snapshot nhất quán khi concurrent mutation.

**Lỗi 7 — “Mọi cast trong class generic đều nên bị suppress ở cấp class.”** Không. Đó là cách dễ che các unsafe operations mới nhất.

**Lỗi 8 — “`Class<T>` kiểm tra được cả `List<Map<K,V>>`.”** Không. Token class đơn lẻ không biểu diễn toàn bộ type arguments lồng nhau.

**Lỗi 9 — “`Collections.checkedList` kiểm tra cả các đường ghi qua backing alias.”** Không. Chỉ thao tác qua wrapper được bảo vệ bằng check của wrapper.

**Lỗi 10 — “Bound `T extends Number` chứng minh object là `Integer` khi caller cần `Integer`.”** Không. Bound nói về quan hệ subtype, không khóa T thành một subtype cụ thể tại runtime.

**Lỗi 11 — “Nếu không có ClassCastException tại chỗ cast thì dữ liệu chắc chắn an toàn.”** Không. Lỗi có thể chỉ xuất hiện ở một lần đọc/truy xuất xa hơn.

**Lỗi 12 — “JUnit test với một `List<String>` đủ chứng minh cast.”** Không. Test cho ví dụ cụ thể không thay proof về tất cả đường ghi, aliases, lifetime và contract.

## 18. Bài tập tự kiểm tra

**Câu 1.** Vì sao `(List<String>) input` từ một `Object` có thể là unchecked cast?

Vì JVM không có runtime class chuyên biệt chứa type argument `String` cho list để kiểm tra đầy đủ; cast có thể xác nhận kiểu ngoài mà không xác minh mọi phần tử.

**Câu 2.** Có nên đặt `@SuppressWarnings("unchecked")` trên toàn bộ class để build sạch không?

Thông thường không. Nên khoanh vùng ở đúng local variable/method đã có invariant rõ, để các cảnh báo khác còn được báo.

**Câu 3.** Điều kiện cốt lõi để cast `(T) slots[i]` có thể biện minh trong `Buffer<T>` là gì?

Các phần tử thuộc vùng hợp lệ được ghi qua đường typed bảo toàn `T`; storage không rò rỉ và không có đường ghi không kiểm tra trong giả định sử dụng; index/size được kiểm soát.

**Câu 4.** Vì sao `(T[]) slots` khác với cast từng phần tử?

Mảng được tạo `new Object[n]` có runtime component type `Object`; cast không đổi class của mảng thành `String[]` khi `T = String`.

**Câu 5.** `Class<String>.cast(42)` giúp gì?

Nó ném `ClassCastException` ngay tại biên kiểm tra vì `Integer` không tương thích với runtime class `String`. Đây là checked runtime cast của một object.

**Câu 6.** `Collections.checkedList(backing, String.class)` có sửa các giá trị sai kiểu đã có trước không?

Không. Wrapper kiểm tra các thao tác phù hợp đi **qua chính wrapper**, không tự sửa backing hoặc ngăn alias bên ngoài ghi trực tiếp.

**Câu 7.** Nếu một API đã kiểm tra list rồi tiếp tục trả chính list gốc mutable, rủi ro còn lại?

Một alias khác có thể sửa list sau khi kiểm tra, khiến lời hứa kiểu không còn đúng. Cần quy định ownership và validation/snapshot thích hợp.

**Câu 8.** Bật `-Xlint:rawtypes -Xlint:unchecked` giúp gì?

Nó phát hiện các raw types và unchecked operations để reviewer tập trung phân tích, không tự chứng minh code có hoặc không có heap pollution.

**Câu 9.** `T extends Number` có thay được `Integer.class` khi cần kiểm tra runtime chính xác `Integer` không?

Không. `Number` là upper bound của nhiều subtype; cần token hoặc validator phù hợp với yêu cầu thực tế.

## 19. Kết luận: một nguyên tắc nhỏ, một chứng minh rõ

Một unchecked cast có thể là lựa chọn hợp lý trong **nội bộ một generic API được đóng gói chặt**, nhưng chỉ khi ta nêu rõ và duy trì được invariant chứng minh nội dung phù hợp. Hãy đặt cast tại **một trust boundary** dễ thấy; để `@SuppressWarnings("unchecked")` ở **phạm vi nhỏ nhất**; giữ raw types và backing arrays khỏi các phần API thông thường; kiểm tra dữ liệu chưa đáng tin bằng `Class<T>.cast` hoặc validator thay vì gắn nhãn qua unchecked cast.

**Không đánh đồng bốn việc:** type parameters ở compile time, runtime class của object, metadata generic của reflection và nội dung dữ liệu thực tế. Khi những quan hệ này được diễn đạt đúng trong chữ ký/Javadoc và được bảo vệ bằng kiểm tra hoặc invariant cụ thể, một API generic mới có thể xứng đáng được gọi là type-safe, dù ở bên trong còn một cast mà compiler không tự chứng minh được.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="generic-api-design-008">Ảnh hưởng của đổi giới hạn và thứ tự giới hạn đến tương thích nhị phân</a>

<details>
<summary>Xem chi tiết</summary>

# Ảnh hưởng của đổi giới hạn và thứ tự giới hạn đến tương thích nhị phân trong Java Generics

## 1. Vấn đề: thay một chữ ký generic có thể phá client đã biên dịch

Xét một generic method công khai:

~~~java
public static <T extends java.io.Serializable
        & Comparable<T>> T echo(T value) {
    return value;
}
~~~

Một thư viện có thể muốn “dọn đẹp” chữ ký bằng cách đổi thứ tự giới hạn:

~~~java
public static <T extends Comparable<T>
        & java.io.Serializable> T echo(T value) {
    return value;
}
~~~

Hai chữ ký trên đều chấp nhận `String` vì String thỏa cả `Serializable` và `Comparable<String>`. Ở Java source, chúng trông gần như tương đương về tập kiểu được phép. Nhưng **thứ tự bounds ảnh hưởng đến type erasure**: kiểu đầu tiên trong danh sách bounds xác định erasure của type variable.

- Bản cũ: erasure(T) là `java.io.Serializable`.
- Bản mới: erasure(T) là `java.lang.Comparable`.
- JVM liên kết method dựa trên **tên + bytecode descriptor**, không dựa trên một ý tưởng trừu tượng “hai generic bounds là cùng tập giao”.
- Vì T vừa là tham số vừa là giá trị trả về, **cả parameter descriptor và return descriptor đều đổi**.
- Một client đã compile với method cũ có thể tìm đúng descriptor cũ khi chạy, nhưng class thư viện mới không còn method đó: lỗi `NoSuchMethodError`.

Đây là một **binary compatibility break** (*phá tương thích nhị phân*), ngay cả khi client source có thể biên dịch lại với bản thư viện mới và chạy bình thường.

Chủ đề này tập trung vào kỹ năng **thiết kế và tiến hóa generic API**:
1. Erasure của type variable phụ thuộc bounds nào?
2. Thứ tự bounds có ảnh hưởng tới bytecode descriptor và JVM linkage không?
3. Thay bound mà giữ bound đầu tiên có khác gì thay bound đầu tiên?
4. Binary compatibility khác source compatibility và behavioral compatibility ra sao?
5. Cách kiểm tra bằng `javac`, `javap`, Reflection và phép chạy client cũ với thư viện mới.
6. Cách thiết kế, version, kiểm thử API generic công khai để tránh vô tình phá người dùng.

## 2. Ba lớp tương thích cần phân biệt

Khi nói “đổi generic signature vẫn tương thích”, phải hỏi rõ loại nào:

| Loại tương thích | Câu hỏi đúng | Ví dụ kiểm tra |
| --- | --- | --- |
| **Source compatibility** | Code Java cũ có thể **biên dịch lại** với API mới không? | Biên dịch lại `Client.java` sau khi nâng phiên bản dependency. |
| **Binary compatibility** | Bytecode client **đã biên dịch** có thể liên kết/chạy với binary thư viện mới không? | Giữ nguyên `Client.class` cũ; thay riêng `Api.class`. |
| **Behavioral compatibility** | Sau khi chạy được, API có còn hành vi/ngữ nghĩa mong đợi không? | Kiểm tra exception, side effects, kết quả, contract. |

Một đổi thứ tự bounds có thể:

- **Vẫn source-compatible** đối với một lời gọi cụ thể, ví dụ dùng String.
- **Không binary-compatible** nếu method descriptor bị đổi và thư viện không duy trì descriptor cũ.
- **Thay đổi hành vi** nếu implementation mới thực hiện cast hoặc gọi method khác theo giới hạn mới, dù descriptor vẫn giữ.

Trong tình huống nâng thư viện, các ứng dụng lớn thường có module đã compile ở những thời điểm khác nhau. Chính vì vậy **“source của tôi compile được” không đủ chứng minh “binary cũ vẫn chạy”**.

### 2.1. Vì sao JVM không dựa trên Generic Signature để liên kết method?

Compiler Java tạo **descriptor** cho field và method bằng các JVM types sau erasure. Class file còn có thể lưu **Signature attribute** mang type parameters và actual generic types để phục vụ các công cụ và compiler sau này.

Hai thứ có vai trò khác nhau:

~~~text
Method descriptor:
    (Ljava/io/Serializable;)Ljava/io/Serializable;

Generic Signature:
    mô tả <T extends Serializable & Comparable<T>> T echo(T)
~~~

Descriptor là thông tin quan trọng cho JVM method resolution. Generic Signature là metadata bổ sung; nó không tạo một JVM method riêng cho mỗi type argument. JVM không có hai runtime classes `List<String>` và `List<Integer>` chỉ vì generics ở source.

Vì thế thay metadata generic **có thể** chỉ làm thay đổi source/reflection contracts nếu descriptor giữ nguyên; nhưng đổi erasure thành kiểu khác thì thường **đổi descriptor**, tạo nguy cơ mất liên kết với client cũ.

## 3. Quy tắc erasure của type variable

Java Generics dùng **type erasure**. Đối với type variable có upper bounds, erasure gắn với **leftmost bound**, tức giới hạn đầu tiên, sau khi áp dụng các quy tắc erasure liên quan.

Ví dụ:

| Khai báo type parameter | Erasure của T |
| --- | --- |
| `<T>` | `Object` |
| `<T extends Number>` | `Number` |
| `<T extends java.io.Serializable>` | `Serializable` |
| `<T extends java.io.Serializable & Comparable<T>>` | `Serializable` |
| `<T extends Comparable<T> & java.io.Serializable>` | `Comparable` |
| `<T extends Number & java.io.Serializable & Comparable<T>>` | `Number` |
| `<T extends Number & Comparable<T> & java.io.Serializable>` | `Number` |

**Cực kỳ quan trọng:** Java yêu cầu nếu có **class bound** như Number, nó phải đứng **đầu** trước các interface bounds. Bạn không thể hoán đổi Number xuống sau một interface rồi vẫn giữ cú pháp hợp lệ.

~~~java
// Hợp lệ: class trước, sau đó các interfaces.
<T extends Number & java.io.Serializable & Comparable<T>>

// Không hợp lệ về ngôn ngữ:
// <T extends java.io.Serializable & Number & Comparable<T>>
// Class Number không được đứng sau interface bound.
~~~

Nếu không có class bound tường minh, các giới hạn là interface bounds, và **interface đứng đầu quyết định erasure**.

### 3.1. Intersection type không phải “một JVM class giao”

`T extends Serializable & Comparable<T>` nghĩa là T phải thỏa cả hai interface trong type checking ở compile time.

Nó không tạo ra một runtime class đặc biệt `SerializableAndComparable`. JVM dùng erased type đầu tiên trong descriptors; khi code cần gọi method từ một secondary bound, compiler có thể chèn các `checkcast` phù hợp.

Do đó câu “cả hai interface đều được yêu cầu nên thứ tự không quan trọng” **sai đối với erasure và tương thích nhị phân**.

### 3.2. Bounds cũng quyết định thành viên nào được phép sử dụng

~~~java
static <T extends java.io.Serializable & Comparable<T>>
int compare(T a, T b) {
    return a.compareTo(b);
}
~~~

Compiler chấp nhận `compareTo` vì `Comparable<T>` xuất hiện trong bounds, dù không phải bound đầu tiên.

Nếu **bỏ Comparable<T>** khỏi tập giới hạn, body không còn được bảo đảm có `compareTo`. Nếu chỉ **đổi thứ tự** các bounds nhưng giữ cả hai, về mặt tập các member được phép từ những interfaces này, những khả năng source-level tương ứng nhìn chung vẫn có; tuy nhiên erasure và bytecode signatures có thể đổi.

## 4. Tham số và giá trị trả về T bị xóa thành gì?

Ví dụ:

~~~java
class Library {
    static <T extends Number> T identity(T value) {
        return value;
    }
}
~~~

Ở source:

~~~text
<T extends Number> T identity(T value)
~~~

Sau erasure, JVM descriptor tương ứng:

~~~text
(Ljava/lang/Number;)Ljava/lang/Number;
~~~

Với:

~~~java
static <T extends java.io.Serializable & Comparable<T>>
T echo(T value) {
    return value;
}
~~~

JVM descriptor:

~~~text
(Ljava/io/Serializable;)Ljava/io/Serializable;
~~~

Sau khi đảo interface đầu tiên:

~~~java
static <T extends Comparable<T> & java.io.Serializable>
T echo(T value) {
    return value;
}
~~~

JVM descriptor đổi thành:

~~~text
(Ljava/lang/Comparable;)Ljava/lang/Comparable;
~~~

**Descriptor chứa cả tham số lẫn kiểu trả về**. Với field có kiểu type variable, field descriptor cũng dựa trên erasure của variable tương ứng.

Đừng cho rằng thay bounds chỉ đổi thông tin “mang tính documentation”. Trong ví dụ trên, nó đã thay **binary identity** của phương thức đối với việc liên kết mã đã compile.

## 5. Thực nghiệm 1: giữ cùng hai bounds nhưng đảo vị trí

### 5.1. API cũ

Tạo thư mục chứa source file `api/Api.java`:

~~~java
package api;

public class Api {
    public static <T extends java.io.Serializable & Comparable<T>>
    T echo(T value) {
        return value;
    }
}
~~~

Với Java 17, `javap -s -p api.Api` sau khi compile sẽ cho descriptor của `echo` chứa:

~~~text
descriptor: (Ljava/io/Serializable;)Ljava/io/Serializable;
~~~

### 5.2. Client biên dịch với API cũ

Tệp `Client.java`:

~~~java
import api.Api;

public class Client {
    public static void main(String[] args) {
        System.out.println("result=" + Api.echo("ok"));
    }
}
~~~

`String` thực hiện cả Serializable lẫn Comparable<String>, vì vậy lời gọi hợp lệ.

Ví dụ lệnh dùng **Bash** từ thư mục có `api/Api.java` và `Client.java`:

~~~bash
mkdir -p out/api out/client
javac --release 17 -d out/api api/Api.java
javac --release 17 -cp out/api -d out/client Client.java
java -cp out/client:out/api Client
~~~

Stdout:

~~~text
result=ok
~~~

Trên Windows, dấu phân tách classpath là `;` thay vì `:`:

~~~text
java -cp "out/client;out/api" Client
~~~

**Cần giữ nguyên file `out/client/Client.class`** cho bước sau. Không compile lại Client khi thay thư viện.

### 5.3. API mới: chỉ đổi thứ tự bounds

Thay nội dung `api/Api.java` thành:

~~~java
package api;

public class Api {
    public static <T extends Comparable<T> & java.io.Serializable>
    T echo(T value) {
        return value;
    }
}
~~~

Hai type bounds vẫn giống nhau, chỉ vị trí đổi. Biên dịch lại **riêng thư viện**:

~~~bash
javac --release 17 -d out/api api/Api.java
javap -classpath out/api -p -s api.Api
~~~

Descriptor mới:

~~~text
descriptor: (Ljava/lang/Comparable;)Ljava/lang/Comparable;
~~~

Chạy **Client.class cũ**:

~~~bash
java -cp out/client:out/api Client
~~~

Kết quả là chương trình thất bại với lỗi liên kết:

~~~text
java.lang.NoSuchMethodError:
'java.io.Serializable api.Api.echo(java.io.Serializable)'
~~~

Dòng thực tế có thể có prefix `Exception in thread "main"` và stack trace, vị trí file/line tùy môi trường. Bài này chỉ trích phần chẩn đoán chính, không giả vờ các dòng stack trace luôn giống nhau.

**Nguyên nhân**: Client.class được compile để gọi descriptor cũ nhận/trả Serializable. Class Api mới chỉ còn descriptor nhận/trả Comparable. JVM không nhận ra một method có descriptor cũ, dù source client vẫn viết `Api.echo("ok")` và có thể compile lại được.

### 5.4. Biên dịch lại client khác hoàn toàn giữ binary cũ

Nếu biên dịch lại Client.java với Api mới:

~~~bash
javac --release 17 -cp out/api -d out/client Client.java
java -cp out/client:out/api Client
~~~

Output lại là:

~~~text
result=ok
~~~

Vì compiler mới tạo lời gọi đến descriptor Comparable mới. Điều này chứng minh:
- Client **source** cụ thể có thể vẫn hợp lệ.
- Client **binary** cũ không tương thích với bản Api mới.
- Việc “clean build” toàn bộ project đôi khi che một breaking change vẫn gây lỗi ở plugin, module hoặc thư viện đã phát hành mà người dùng không recompile.

Đây không chỉ là giả định: khi thử bằng JDK thực tế, biên dịch v1/v2 thành công, chạy client cũ với v1 in `result=ok`, thay riêng Api.class v2 gây `NoSuchMethodError` đúng như mô tả.

## 6. Thực nghiệm 2: đổi thứ tự **secondary bounds** nhưng giữ bound đầu tiên

Giả sử class bound đứng đầu:

~~~java
static <T extends Number
        & java.io.Serializable
        & Comparable<T>> T echo(T value) {
    return value;
}
~~~

Nếu chỉ đảo hai interfaces đứng sau Number:

~~~java
static <T extends Number
        & Comparable<T>
        & java.io.Serializable> T echo(T value) {
    return value;
}
~~~

Hai chữ ký này đều có **erasure(T) = Number**, vì leftmost bound vẫn là Number.

Vì vậy descriptor của method `echo(T) : T` ở cả hai trường hợp vẫn là:

~~~text
(Ljava/lang/Number;)Ljava/lang/Number;
~~~

**Kết luận giới hạn:** chỉ đảo secondary bounds và không làm đổi erasure **không tự làm đổi descriptor của method đơn giản này**. Client đã compile có thể vẫn liên kết được nếu method còn nguyên descriptor và các điều kiện binary linkage khác được giữ.

Tuy vậy, thay đổi vẫn quan sát được qua:
- Generic `Signature` metadata.
- Thứ tự `TypeVariable.getBounds()` của Java Reflection.
- Công cụ tạo schema, API documentation hoặc framework có logic phụ thuộc metadata/order.
- Source contract ở các thay đổi khác liên quan secondary bounds, đặc biệt thêm/xóa bound thay vì chỉ đảo.

Không kết luận “vì descriptor giống nhau nên chắc chắn mọi hành vi đều giống nhau”. Hãy kiểm tra implementation, các members kế thừa, code generation và những thành phần dựa vào generic metadata.

### 6.1. Vì sao bound Number phải đứng đầu?

Java bắt buộc nếu một type variable có bound là class và interface, class xuất hiện trước:

~~~java
// Hợp lệ:
<T extends Number & java.io.Serializable & Comparable<T>>

// Không hợp lệ:
// <T extends java.io.Serializable & Number & Comparable<T>>
~~~

Điều này giới hạn những cách sắp xếp hợp lệ. Khi type parameter có class bound, **đổi thứ tự các interface ở phía sau** thường giữ class erasure; nhưng khi **đổi chính class bound**, erasure vẫn có thể thay đổi.

Nếu chỉ có interface bounds, như `Serializable & Comparable<T>`, đảo interfaces có thể **đổi erasure ngay lập tức**.

## 7. Chương trình Java 17+ đọc erasure và bounds bằng Reflection

Lưu thành **BoundOrderDemo.java**:

~~~java
import java.io.Serializable;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Arrays;

public class BoundOrderDemo {
    static class SerializableFirst {
        static <T extends Serializable & Comparable<T>>
        T echo(T value) {
            return value;
        }
    }

    static class ComparableFirst {
        static <T extends Comparable<T> & Serializable>
        T echo(T value) {
            return value;
        }
    }

    static class NumberFirstA {
        static <T extends Number & Serializable & Comparable<T>>
        T echo(T value) {
            return value;
        }
    }

    static class NumberFirstB {
        static <T extends Number & Comparable<T> & Serializable>
        T echo(T value) {
            return value;
        }
    }

    private static String bounds(Method method) {
        return Arrays.stream(
                method.getTypeParameters()[0].getBounds()
        ).map(Type::getTypeName)
         .reduce((a, b) -> a + " | " + b)
         .orElse("");
    }

    public static void main(String[] args)
            throws ReflectiveOperationException {

        Method s = SerializableFirst.class
                .getDeclaredMethod("echo", Serializable.class);

        Method c = ComparableFirst.class
                .getDeclaredMethod("echo", Comparable.class);

        Method n1 = NumberFirstA.class
                .getDeclaredMethod("echo", Number.class);

        Method n2 = NumberFirstB.class
                .getDeclaredMethod("echo", Number.class);

        System.out.println(
                "firstA=" + s.getParameterTypes()[0].getTypeName()
        );
        System.out.println(
                "firstB=" + c.getParameterTypes()[0].getTypeName()
        );
        System.out.println(
                "returnA=" + s.getReturnType().getTypeName()
        );
        System.out.println(
                "returnB=" + c.getReturnType().getTypeName()
        );

        System.out.println("boundsA=" + bounds(s));
        System.out.println("boundsB=" + bounds(c));

        System.out.println(
                "stableA=" + n1.getParameterTypes()[0].getTypeName()
        );
        System.out.println(
                "stableB=" + n2.getParameterTypes()[0].getTypeName()
        );
        System.out.println("stableBoundsA=" + bounds(n1));
        System.out.println("stableBoundsB=" + bounds(n2));

        System.out.println(
                "values=" + SerializableFirst.echo("ok")
                        + "/" + ComparableFirst.echo("ok")
        );
    }
}
~~~

Biên dịch/chạy bằng JDK 17 trở lên:

~~~bash
javac --release 17 -Xlint:all BoundOrderDemo.java
java BoundOrderDemo
~~~

Stdout chính xác:

~~~text
firstA=java.io.Serializable
firstB=java.lang.Comparable
returnA=java.io.Serializable
returnB=java.lang.Comparable
boundsA=java.io.Serializable | java.lang.Comparable<T>
boundsB=java.lang.Comparable<T> | java.io.Serializable
stableA=java.lang.Number
stableB=java.lang.Number
stableBoundsA=java.lang.Number | java.io.Serializable | java.lang.Comparable<T>
stableBoundsB=java.lang.Number | java.lang.Comparable<T> | java.io.Serializable
values=ok/ok
~~~

Các dòng đầu dùng `Method.getParameterTypes()` và `getReturnType()` để quan sát **erased classes**: Serializable hoặc Comparable tương ứng bound đứng đầu.

Các dòng `boundsA/boundsB` dùng `TypeVariable.getBounds()` để đọc **generic declarations** đúng thứ tự và đầy đủ. Chúng không phải những types mà JVM bắt buộc phân biệt bằng một class runtime riêng cho mỗi intersection.

Hai dòng `stableA/stableB` cho thấy khi Number vẫn ở vị trí đầu thì erased parameter types đều là Number. Trong khi đó `stableBoundsA/stableBoundsB` vẫn khác nhau vì Reflection giữ thứ tự các secondary bounds.

Cuối cùng, `values=ok/ok` minh họa **cả hai phiên bản có thể chạy tốt nếu client biên dịch phù hợp với từng version**; nó không chứng minh client binary cũ có thể liên kết với version descriptor khác.

## 8. Phân biệt Method descriptor với Generic Signature attribute

Một generic declaration mang nhiều thông tin hơn bytecode descriptor.

Ví dụ method:

~~~java
static <T extends Serializable & Comparable<T>> T echo(T value)
~~~

Có các khái niệm:

| Tầng | Ý nghĩa |
| --- | --- |
| **Source declaration** | T và các bounds tạo quy tắc kiểm tra kiểu cho javac. |
| **Erasure** | T biến thành leftmost bound để tạo erased types. |
| **Method descriptor** | Mô tả parameter/return types mà JVM dùng trong liên kết, ở đây Serializable→Serializable. |
| **Signature attribute** | Giữ lại mô tả type parameters/bounds và những generic constructs phục vụ reflection và compiler. |

Một class file có thể vừa có descriptor đã erasure vừa có `Signature` thể hiện đủ cả hai bounds. Khi đổi thứ tự bounds sau bound đầu giữ nguyên, descriptor có thể không đổi nhưng metadata `Signature` sẽ khác.

Để xem:

~~~bash
javap -v -p BoundOrderDemo
~~~

Nếu method nằm trong nested class:

~~~bash
javap -v -p 'BoundOrderDemo$SerializableFirst'
~~~

Dấu `$` trong binary name chỉ nested class. Ví dụ trên dùng shell có single quotes phù hợp Bash/PowerShell để không nội suy ký tự đặc biệt.

Hãy tìm:
- `descriptor:` — JVM types cho method.
- `Signature:` — generic signature khi có.
- `Code:` — instructions của implementation.
- `ACC_BRIDGE`/`ACC_SYNTHETIC` nếu compiler tạo bridge methods trong một hệ phân cấp lớp có overriding.

**Đừng đọc Signature như thể nó là descriptor dùng để liên kết method.** Điều gây `NoSuchMethodError` ở thực nghiệm chính là sự khác biệt giữa descriptor client đang gọi và descriptor thư viện thực sự cung cấp.

## 9. Đổi bound đầu tiên ảnh hưởng tới method, field và class generic

Không chỉ generic methods riêng lẻ mới bị ảnh hưởng. Một **generic class** có type variable T cũng tạo các member signatures dựa trên erasure(T).

Giả sử thư viện v1:

~~~java
public class Box<T extends java.io.Serializable> {
    public T value;

    public T get() {
        return value;
    }

    public void set(T replacement) {
        value = replacement;
    }
}
~~~

Các kiểu erasure liên quan:
- `value` field descriptor là Serializable.
- `get()` return descriptor là Serializable.
- `set(T)` parameter descriptor là Serializable.

Nếu đổi thành:

~~~java
public class Box<T extends Comparable<T> & java.io.Serializable> {
    public T value;

    public T get() {
        return value;
    }

    public void set(T replacement) {
        value = replacement;
    }
}
~~~

Leftmost bound trở thành Comparable. **Các erased descriptors của field và methods sử dụng T sẽ đổi**. Một client cũ có thể thất bại với:
- `NoSuchFieldError` khi truy cập field theo descriptor cũ.
- `NoSuchMethodError` khi gọi `get` hoặc `set` theo descriptor cũ.

Điểm cần nhớ: `T` xuất hiện ở nhiều nơi trong public/protected bytecode API, nên thay bound đầu tiên có thể tạo **hàng loạt thay đổi binary signatures** chứ không chỉ một generic annotation.

### 9.1. Nếu T chỉ xuất hiện trong metadata thì sao?

Một thay đổi generic bound không luôn phá mọi binary client:
- Nếu descriptor public methods/fields thực sự **không đổi**, những lời gọi tới các descriptors đó không mất liên kết **chỉ vì bound order**.
- Nếu thay đổi chỉ tác động metadata `Signature`, một JVM call thông thường có thể vẫn chạy.
- Dù vậy compiler mới, reflective tools, method selection hoặc implementation behavior vẫn có thể bị ảnh hưởng.

Vì thế **không** nên áp dụng quy tắc tuyệt đối “hễ sửa bounds là binary-incompatible” hoặc “hễ không sửa tên method là compatible”. Phải kiểm tra **erased ABI thực tế**.

## 10. Thay secondary bounds: source compatibility có thể đổi dù binary descriptor giữ

Xét v1:

~~~java
static <T extends Number & java.io.Serializable>
T pass(T value) {
    return value;
}
~~~

Và v2 thêm interface constraint:

~~~java
static <T extends Number & java.io.Serializable & Comparable<T>>
T pass(T value) {
    return value;
}
~~~

Bound đầu vẫn là Number, nên nếu method body/chữ ký khác không đổi, erased descriptor của `pass(T):T` vẫn là Number→Number.

Nhưng **tập các kiểu source được phép truyền có thể thu hẹp**. Một subtype Number implements Serializable nhưng không Comparable<T> có thể được chấp nhận với v1 và bị javac từ chối với v2.

Ví dụ một class Number do ứng dụng định nghĩa implement Serializable nhưng không Comparable:

~~~java
final class Metric extends Number
        implements java.io.Serializable {

    private final int value;

    Metric(int value) {
        this.value = value;
    }

    public int intValue()    { return value; }
    public long longValue()  { return value; }
    public float floatValue(){ return value; }
    public double doubleValue(){ return value; }
}
~~~

`pass(new Metric(1))` hợp lệ với v1 nhưng **không biên dịch với v2** do thiếu `Comparable<Metric>`. Source compatibility của caller đó bị phá dù erasure của T vẫn là Number.

Nếu client đã compile với v1, việc JVM có còn liên kết được với v2 phụ thuộc descriptors và những thay đổi triển khai khác. Trong ví dụ đơn giản `return value`, descriptor Number→Number giống nhau và client cũ có thể vẫn gọi được. Nhưng nếu v2 bắt đầu gọi `compareTo` thông qua bound mới, một object Metric do client cũ gửi tới có thể gây lỗi runtime khi implementation cast/dùng Comparable.

**Không nhầm “link được” với “hành vi vẫn đúng”.** Source contract đã chặt hơn nhưng bytecode cũ có thể tiếp tục gọi method, do JVM không kiểm tra generic bounds như một hợp đồng runtime đầy đủ ở mỗi invocation.

## 11. Đổi từ unbounded T sang bounded T có thể đổi erasure

Ví dụ API ban đầu:

~~~java
static <T> T same(T value) {
    return value;
}
~~~

Erasure của T là Object, nên descriptor:

~~~text
(Ljava/lang/Object;)Ljava/lang/Object;
~~~

Đổi thành:

~~~java
static <T extends CharSequence> T same(T value) {
    return value;
}
~~~

Erasure của T thành CharSequence và descriptor đổi:

~~~text
(Ljava/lang/CharSequence;)Ljava/lang/CharSequence;
~~~

Dù method tên `same` không đổi, và rất nhiều đối số như String vẫn thỏa constraint mới, binary client đã biên dịch tìm Object descriptor có thể bị `NoSuchMethodError` nếu không có phương thức tương thích cũ.

Ngược lại, **thêm một secondary bound** sau một first bound ổn định không tự đổi erasure, nên cần đánh giá riêng.

### 11.1. Thay bound class đầu

~~~java
<T extends Number & java.io.Serializable>
~~~

thành:

~~~java
<T extends Integer & java.io.Serializable>
~~~

**Không hợp lệ** vì Integer là final class, không thể dùng `Integer` làm một superclass bound của một subtype mới? Java type parameter `<T extends Integer>` có thể là khai báo hợp lệ dù class Integer final: giá trị T chỉ có thể là Integer, compiler có thể chấp nhận. Nhưng việc chọn thay giới hạn quá hẹp là vấn đề thiết kế, không phải ví dụ nên dựa vào để minh họa runtime behavior tổng quát.

Một ví dụ rõ ràng hơn:

~~~java
<T extends Number>
~~~

đổi sang:

~~~java
<T extends java.lang.Comparable<T>>
~~~

thay erasure từ Number thành Comparable; nếu method signature sử dụng T, erased ABI tương ứng có thể thay đổi.

**Nguyên tắc:** đổi bound **đầu tiên** hoặc thêm một class bound trước các interface hiện có có thể làm thay đổi erasure, kể cả khi các ràng buộc khác vẫn giữ nguyên.

## 12. Chữ ký throws T và exception bounds

Java cho phép một type variable có bound phù hợp trong `throws`:

~~~java
static <E extends Exception> void propagate(E ex) throws E {
    throw ex;
}
~~~

Ở đây E là một type variable có erasure liên quan bound Exception. Nếu method dùng E làm tham số hoặc kiểu trả về, đổi bound đầu tiên có thể đổi **descriptor của chính vị trí E đó**.

Một điểm khác phải tách biệt: mệnh đề `throws` của Java **không thuộc JVM method descriptor theo nghĩa parameter/return types**. Chỉ đổi danh sách checked exceptions trong `throws` mà không thay erased parameter/return descriptors không nhất thiết làm mất method ở bước resolution.

Tuy nhiên thay `throws` có thể:
- Phá **source compatibility** vì compiler áp dụng checked-exception rules.
- Đổi `Exceptions` attribute hoặc `Signature` metadata.
- Ảnh hưởng Reflection (`getExceptionTypes` / `getGenericExceptionTypes`).
- Ảnh hưởng hành vi thực tế nếu implementation ném exception khác.

Đừng khái quát “mọi thay đổi generic exception bound đều là NoSuchMethodError”. Hãy nhìn xem E có xuất hiện ở **parameter/return descriptor** hay không, rồi kiểm tra các hợp đồng khác.

## 13. Interface methods, overriding và bridge methods

Đổi erasure của type variables trong generic interfaces hoặc superclass không chỉ ảnh hưởng một method độc lập. Nó có thể tương tác với **overriding** và những bridge methods do javac sinh.

Ví dụ:

~~~java
interface Store<T> {
    T get();
    void put(T value);
}

final class StringStore implements Store<String> {
    private String value;

    public String get() {
        return value;
    }

    public void put(String value) {
        this.value = value;
    }
}
~~~

Sau erasure của Store<T> unbounded, interface methods có descriptors tương ứng với Object. Compiler có thể tạo **bridge methods** trong StringStore để một lời gọi qua Store vẫn tới implementation nhận/trả String.

Nếu sau một phiên bản thư viện, interface Store thay thành `Store<T extends CharSequence>`, những method declarations dùng T có thể có descriptors tương ứng với CharSequence thay vì Object.

Rủi ro:
- Một client cũ dùng `invokeinterface Store.get:()Ljava/lang/Object;` có thể không thấy phương thức với descriptor cũ trong phiên bản interface mới.
- Một implementation đã compile từ trước có thể không thỏa mới các contracts/bridge expectations.
- Tùy cách thay đổi interface/class và bytecode cụ thể, có thể xuất hiện `NoSuchMethodError`, `AbstractMethodError` hoặc lỗi liên kết/hành vi khác.

**Không tự đoán lỗi chính xác chỉ từ source snippet** khi có nhiều inheritance/bridge relationships. Cách chắc chắn là kiểm tra `javap -p -s -v` của cả thư viện và class implementation trước/sau, rồi chạy **client và plugin binaries cũ** với bản thư viện mới.

### 13.1. Bridge methods không phải phương thuốc tự động

Compiler tạo bridge methods trong những tình huống cần bảo toàn dispatch theo quy tắc overriding và erasure; không có cam kết nó sẽ tự giữ **mọi descriptor công khai của API version trước** sau khi developer đổi bounds.

Ví dụ static method `Api.echo` ở phần 5 **không được cứu bởi bridge method** chỉ vì đổi thứ tự generic bounds. Khi cần tương thích binary, phải có chiến lược API/compatibility rõ ràng chứ không trông chờ compiler tự sinh adapter cho mọi signature cũ.

## 14. Generic overloads, method lookup và erasure clashes

Generic overload resolution diễn ra ở **compile time** dựa trên các phương thức hiện có và ràng buộc kiểu. Sau erasure, JVM method names/descriptors phải thỏa các quy tắc không va chạm.

Ví dụ:

~~~java
static <T extends java.io.Serializable> void use(T value) {
    System.out.println("serializable");
}
~~~

Nếu đổi bound đầu của T sang Comparable, erased parameter descriptor sẽ đổi. Điều đó không chỉ ảnh hưởng client cũ mà còn có thể tác động cách compiler nhìn tập overloads ở source.

Hai declarations khác nhau có thể gặp **name clash** khi erasure giống nhau, ví dụ:

~~~java
// Các phương thức sau không thể overload chỉ nhờ type argument:
// void add(List<String> values) { }
// void add(List<Integer> values) { }
~~~

Cả hai bị xóa tham số List<String>/List<Integer> về raw List trong bytecode, gây cùng erased signature.

Khi thay bounds, hãy kiểm tra:
- Các overloads có còn hợp lệ ở source không?
- Erased parameter types có đổi không?
- Bytecode method descriptors nào biến mất hoặc xuất hiện?
- Client đã compile có invoke đúng descriptor tồn tại không?
- Các method bridge/synthetic có thay đổi không?

**Cùng tên method** không đủ chứng minh binary compatibility. Một phương thức “trông tương đương” ở Java source có thể không phải binary method mà client cũ đang tham chiếu.

## 15. Reflection đọc bounds theo thứ tự nhưng không tự chứng minh tương thích nhị phân

Các API Reflection hữu ích:

~~~java
Method m = SomeClass.class.getDeclaredMethod(
        "echo", java.io.Serializable.class
);

TypeVariable<Method> t = m.getTypeParameters()[0];

Type[] bounds = t.getBounds();
Class<?> erasedReturn = m.getReturnType();
Class<?>[] erasedParams = m.getParameterTypes();
Type genericReturn = m.getGenericReturnType();
~~~

Nếu bound đầu đã đổi thành Comparable, lookup `getDeclaredMethod("echo", Serializable.class)` có thể thất bại vì erased parameter type mới không còn là Serializable. Phải dùng kiểu parameter thực tế của method.

Trong chương trình `BoundOrderDemo`:
- SerializableFirst có erased parameter/return Serializable.
- ComparableFirst có erased parameter/return Comparable.
- NumberFirstA và NumberFirstB đều có erased parameter/return Number, dù `getBounds()` cho thứ tự secondary bounds khác nhau.

**Reflection rất có ích để kiểm tra metadata**, nhưng để khẳng định backward binary compatibility nên dùng **javap descriptors và chạy binary client cũ**. Các framework dựa trên TypeVariable.getBounds() có thể thay đổi hành vi sau khi secondary bounds bị đảo dù JVM descriptors không đổi.

### 15.1. TypeVariable.getBounds không nên bị sort tùy tiện

Thứ tự bounds trong generic declaration là thông tin có ý nghĩa đối với erasure. Một schema tool tự sort alphabetically rồi dùng phần tử đầu như erasure sẽ tính sai nếu original first bound khác phần tử đầu sau sort.

Khi chỉ muốn hiển thị tập giao các interface cho người đọc, có thể trình bày ở dạng dễ hiểu. Nhưng khi cần tính erasure, **giữ nguyên bound order được khai báo** và không tự tráo thứ tự trước khi xác định leftmost bound.

## 16. Cách kiểm tra ABI trước và sau thay đổi thư viện

Một quy trình thực dụng để review thay đổi:

### 16.1. Chốt phiên bản cũ

Build thư viện cũ thành jar hoặc thư mục class files, và giữ một client sample đã compile:

~~~bash
javac --release 17 -d old-classes api/Api.java
javac --release 17 -cp old-classes -d client-classes Client.java
~~~

Không chỉ giữ source. Điểm khác biệt của binary compatibility chính là **giữ nguyên client .class file**.

### 16.2. Kiểm tra erased descriptors

~~~bash
javap -p -s -classpath old-classes api.Api
javap -p -s -classpath new-classes api.Api
~~~

So sánh:
- Tên class/interface.
- Tên methods và erased parameter/return descriptors.
- Fields và descriptors.
- Visibility/access modifiers, static/instance.
- Những members có thay đổi do inheritance và compiler bridges.

Để xem generic metadata:

~~~bash
javap -p -v -classpath old-classes api.Api
javap -p -v -classpath new-classes api.Api
~~~

So sánh các `Signature` attributes, type parameters, bounds và những attributes khác liên quan.

### 16.3. Chạy binary cũ với thư viện mới

Không recompile Client.class trong bài test này:

~~~bash
java -cp client-classes:new-classes Client
~~~

Trên Windows thay `:` bằng `;` trong classpath. Phải đảm bảo classpath không vô tình lấy Api.class cũ trước Api.class mới, nếu không bài test sẽ cho **false pass**.

### 16.4. Biên dịch lại client để kiểm tra source compatibility

Sau đó mới chạy một bài test riêng:

~~~bash
javac --release 17 -cp new-classes -d recompiled-client Client.java
~~~

Có thể client binary cũ thất bại nhưng source recompile thành công (như thực nghiệm thay bound đầu), hoặc ngược lại có những thay đổi generic signature làm source bị từ chối dù erased descriptor không đổi.

### 16.5. Test hành vi và reflection

Nếu ABI ổn, vẫn cần:
- Tests với các subtype thực sự được APIs chấp nhận.
- Tests dùng raw/legacy clients nếu thư viện có hợp đồng tương thích.
- Reflection/schema/serialization tests nếu sản phẩm đọc `Signature`.
- Integration tests với subclass, implementors, proxies, plugins và method handles khi hệ thống dùng chúng.

Công cụ kiểm tra binary API diff có thể hỗ trợ kiểm tra tự động. Nhưng điều kiện triển khai cụ thể như dynamic classloading, modules, bridges và bytecode generation vẫn cần test phù hợp.

## 17. Bảng đánh giá nhanh từng loại thay đổi bounds

| Thay đổi | Erasure T | Nguy cơ binary | Nguy cơ source/metadata |
| --- | --- | --- | --- |
| `<T>` → `<T extends Number>` | Object → Number | Cao nếu T xuất hiện trong public erased descriptors. | Nhiều caller không còn thỏa bound. |
| `Serializable & Comparable<T>` → `Comparable<T> & Serializable` | Serializable → Comparable | Cao nếu T nằm ở parameters/return/fields. | Có thể vẫn nhận cùng String; order metadata đổi. |
| `Number & Serializable & Comparable<T>` → `Number & Comparable<T> & Serializable` | Number → Number | **Không tự đổi descriptor** của ví dụ dùng T. | `Signature`/Reflection order đổi; cần kiểm tra contract khác. |
| `Number & Serializable` → `Number & Serializable & Comparable<T>` | Number → Number | Descriptor không tự đổi, nhưng implementation/bridge có thể đổi. | Tập type arguments hợp lệ thu hẹp; client recompilation có thể lỗi. |
| `Number & Serializable` → `Number` | Number → Number | Descriptor đơn giản ổn nếu mọi thứ khác giữ nguyên. | Tập type arguments mở rộng; implementation có thể thay đổi. |
| `Number` → `CharSequence` | Number → CharSequence | Có thể làm đổi ABI của members dùng T. | Các callers theo Number có thể bị từ chối. |
| Chỉ đổi `throws` checked exception types | Tham số/trả về không nhất thiết đổi | Không tự đổi method descriptor chỉ vì throws. | Source checked-exception contract và Reflection đổi. |
| Đổi member visibility, static hoặc inheritance | Không chỉ phụ thuộc bounds | Rủi ro riêng theo JVM/JLS binary rules. | Cần đánh giá riêng. |

“Nguy cơ binary” không có nghĩa tất cả clients đều thất bại. Client chỉ gọi những members không thay đổi descriptor có thể không bị tác động. Nhưng nếu public ABI bị mất descriptor đã công bố, **đó là breaking change đối với binary clients phụ thuộc descriptor đó**.

## 18. Thiết kế API để tránh ràng buộc không cần thiết vào erasure

### 18.1. Không dùng intersection bounds chỉ vì trông đẹp

Nếu method chỉ lưu/chuyển tiếp value và không thực sự cần Comparable, thêm Comparable chỉ làm API chặt hơn và có thể ảnh hưởng tính tương thích mà không đem lại khả năng thực sự được dùng.

~~~java
static <T extends java.io.Serializable> T keep(T value) {
    return value;
}
~~~

Nếu chỉ cần truyền object mà không sử dụng Serializable members, thậm chí một `<T> T keep(T)` hoặc API non-generic khác có thể thích hợp hơn; nhưng **không tùy tiện đổi API đã phát hành**, vì chính việc bỏ Serializable bound cũng làm erasure đổi thành Object.

### 18.2. Chọn bound đầu như một quyết định ABI

Khi thiết kế một API **mới** dùng multiple interface bounds, hãy ý thức rằng chọn interface nào đứng đầu không chỉ là thẩm mỹ. Nếu T được dùng trong method parameter/return/field signatures, interface đầu sẽ xuất hiện trong erased descriptors.

Đừng mong sau khi phát hành có thể đảo tùy ý mà không ảnh hưởng người dùng. Chọn thứ tự có lý do thiết kế rồi giữ ổn định trừ khi chấp nhận breaking change và versioning tương ứng.

### 18.3. Tách yêu cầu thao tác khỏi chữ ký công khai khi hợp lý

Có trường hợp một method chỉ cần tiếp nhận type rộng, rồi kiểm tra một khả năng tùy chọn bên trong:

~~~java
static String describe(Object value) {
    return String.valueOf(value);
}
~~~

Nếu API thật sự không cần giả định Comparable hoặc Serializable ở type contract, đừng thêm các bounds đó “để phòng xa”. Tuy nhiên nếu thuật toán bắt buộc cần `compareTo` với compile-time type safety, một bound Comparable có thể là yêu cầu đúng. Mục tiêu là **bounds phản ánh nhu cầu thật** và không được thay đổi vô tội vạ trong released ABI.

### 18.4. Không ép raw/unchecked để “duy trì compatibility”

Một client không compile với bounds mới không nên được sửa bằng raw casts tùy tiện:

~~~java
// Bỏ type checking bằng raw type hoặc unchecked cast
// không khiến JVM descriptor cũ xuất hiện trở lại.
~~~

Raw type/cast làm mất type safety nhưng không sửa một `NoSuchMethodError` do binary descriptor không còn. Phải giải quyết **API linkage**, không phải che compiler diagnostics.

## 19. Chiến lược migration khi buộc phải thay đổi bound đầu

Nếu cần thay bounds và erasure của một public API đã phát hành, các phương án cần cân nhắc:

1. **Giữ ABI cũ nếu có thể:** tránh đổi bound đầu; đưa yêu cầu mới qua helper/overload rõ ràng mà không trùng erasure.
2. **Thêm API mới có tên khác:** ví dụ `echoComparable` thay vì thay tại chỗ `echo`, giúp clients cũ tiếp tục gọi `echo`.
3. **Giữ một adapter với descriptor cũ khi phù hợp:** phương thức cũ chuyển tiếp đến logic mới, nhưng phải kiểm tra type contracts; không thể tự tạo overload chỉ khác return type trong Java source.
4. **Version như breaking change** nếu không thể giữ descriptor cũ, và thông báo yêu cầu rebuild/relink các modules.
5. **Đánh giá class inheritance và bridge methods**: một adapter thủ công có thể không đủ trong generic interfaces/overrides.
6. **Kiểm tra client binary cũ**, không chỉ build lại tất cả code.
7. **Kiểm tra Source API** sau khi thay: những callers dùng type arguments không thỏa bounds mới phải nhận lỗi compile rõ.
8. **Không giả định một JVM compatibility shim luôn hợp lệ**: có thể gặp erased signature clashes hoặc thay đổi dispatch/overload resolution.

Với method đơn giản ở phần 5, có thể giữ method cũ tên `echo` và đưa method mới tên `echoComparable`, tránh mất descriptor cũ. Nếu vừa muốn tên cũ vừa muốn kiểu mới, cần thiết kế cẩn thận để không xung đột giữa erased signatures và hành vi của các overloads.

### 19.1. Adapter có thể giữ descriptor cũ bằng một overload hợp pháp

Ví dụ nếu signatures khác nhau ở erased parameter types, thư viện có thể cung cấp **hai overloads** với tên giống nhau khi Java cho phép và không bị xung đột method resolution:

~~~java
public static <T extends java.io.Serializable & Comparable<T>>
T echo(T value) {
    return value;
}

public static <T extends Comparable<T> & java.io.Serializable>
T echoComparable(T value) {
    return value;
}
~~~

Đây là một lựa chọn giữ **tên cũ + erased signature cũ** và thêm **tên mới**, không phải thủ thuật tự động làm method mới có cả hai descriptors. Tên method mới khiến binary behavior rõ ràng, tránh các vấn đề overload ambiguity khi input String thực hiện nhiều interfaces.

Không nên khẳng định một overload mới của cùng tên với Serializable và Comparable luôn lựa chọn đúng theo lời gọi Java: String là subtype của cả hai nhưng hai interface không nhất thiết có quan hệ subtype, nên việc overload có thể gây **ambiguous method call** khi recompile. Versioning/API mới có tên riêng thường dễ kiểm soát hơn.

## 20. Thứ tự bounds và reflection/schema frameworks

Một số công cụ đọc generic declarations bằng:

~~~java
TypeVariable<?> typeVar =
        SomeClass.class.getTypeParameters()[0];

Type[] bounds = typeVar.getBounds();
~~~

Dù secondary bounds đổi thứ tự và JVM descriptor của member vẫn giữ, công cụ:
- ghi log hoặc hiển thị bounds theo thứ tự khai báo;
- chọn bound đầu như đại diện cho erased runtime class;
- xây schema hoặc mapping constraints;
- so sánh generic signatures khi tạo cache keys;
- sinh mã hoặc proxy dựa trên generic information;

có thể quan sát sự khác biệt.

Khi thay API, **không chỉ kiểm tra liên kết JVM mà bỏ qua public metadata contract** nếu ecosystem của thư viện dùng reflection. Các framework có thể dựa trên metadata theo cách khác nhau; không phải mọi cách dựa trên thứ tự secondary bounds đều hợp lý, nhưng việc thay thứ tự vẫn có thể làm kết quả của chúng khác đi.

### 20.1. Không parse getTypeName để suy erasure theo thứ tự alphabet

`TypeVariable.getBounds()` trả Type[] theo thứ tự, nhưng các bound có thể là ParameterizedType, Class hoặc type variables khác. Muốn giải đúng erasure cần dùng quy tắc JLS và cấu trúc Type, không sort tên String theo alphabet hay đơn giản lấy tên class runtime của generic object.

`java.lang.reflect.Type` trong generics là metadata về chữ ký, không phải type checking của mọi dữ liệu runtime. Không nhầm reflection với một runtime reification đầy đủ của type arguments.

## 21. Những sai lầm thường gặp

### “Intersection bounds chỉ là một tập hợp; đổi thứ tự không thể gây lỗi”

Sai. Với nhiều interface bounds, **leftmost bound quyết định erasure**. Đảo Serializable và Comparable có thể đổi erased descriptors và phá client binary cũ.

### “Đổi bounds luôn gây NoSuchMethodError”

Sai. Nếu erasure/erased descriptor giữ nguyên và những binary contracts khác không đổi, một đổi secondary bound đơn giản có thể không tạo lỗi linkage ở method call. Source/behavior/metadata vẫn phải kiểm tra.

### “Source client vẫn compile nghĩa là binary compatible”

Sai. Client Java source được compile lại có thể gọi descriptor mới, trong khi Client.class cũ vẫn tham chiếu descriptor cũ.

### “Method có cùng tên và cùng T là cùng method đối với JVM”

Sai. JVM phân biệt method bằng name/descriptor; erasure(T) có thể đổi parameter hoặc return descriptors dù source dùng cùng ký hiệu T.

### “Đổi kiểu trong throws chắc chắn đổi JVM descriptor”

Sai. Throws clause không phải parameter/return descriptor. Nhưng nếu type variable cũng nằm ở parameter/return, thay erasure của nó có thể đổi descriptor tại những vị trí đó.

### “Thêm secondary bound không ảnh hưởng source”

Sai. Nó có thể thu hẹp tập các type arguments hợp lệ, ví dụ Number & Serializable trước đây chấp nhận type không Comparable, còn version thêm Comparable<T> thì không.

### “Bridge methods luôn cứu binary compatibility”

Sai. Bridge methods phục vụ những quy tắc polymorphism/erasure nhất định, không tự giữ mọi descriptor cũ sau arbitrary API redesign.

### “Chỉ cần build sạch toàn hệ thống là đã chứng minh API non-breaking”

Sai. Build sạch **recompile clients** và có thể che mất lỗi binary compatibility với plugin/jar đã phát hành. Cần test riêng binary cũ trên thư viện mới.

### “Reflection thấy hai bounds tương tự nên class runtime cũng giống nhau”

Không đủ. Intersection constraints và Generic Signature metadata không đồng nghĩa JVM có một runtime class mới. JVM method descriptor vẫn bị chi phối bởi erasure.

### “Chỉ cần suppress warnings là fix được linkage”

Sai. `@SuppressWarnings` không thay class file descriptors mà Client.class gọi. Muốn giải NoSuchMethodError phải cung cấp method với signature tương thích hoặc phát hành breaking version/recompile đúng phạm vi.

## 22. Checklist khi review một thay đổi generic bound trong thư viện

1. **Ghi lại chữ ký cũ và mới**, bao gồm type parameter bounds và thứ tự.
2. **Xác định leftmost bound** của từng type variable.
3. **Tính erasure trước/sau**: Object, class bound, interface đầu tiên.
4. **Liệt kê chỗ T xuất hiện** trong public/protected method parameters, return types và field declarations.
5. **So sánh JVM descriptors** bằng `javap -p -s` trên binaries thật.
6. **So sánh generic Signatures** bằng `javap -v` và Reflection khi thư viện có public metadata consumers.
7. **Xem throws**: checked exceptions đổi source contract hay chỉ metadata; đừng lẫn với method descriptor.
8. **Kiểm tra interface/superclass hierarchies**, overriding và synthetic/bridge methods.
9. **Biên dịch lại nguồn client** để đánh giá source compatibility, đặc biệt khi thêm bounds.
10. **Giữ Client.class cũ** và chạy với jar mới để kiểm tra binary compatibility.
11. **Đảm bảo classpath không chứa jar cũ** che khuất class mới.
12. **Đánh giá behavior**: cast, invokes, logic dùng secondary bounds và các objects cũ có thể không đáp ứng constraint mới.
13. **Kiểm thử overloads** vì thay erasure có thể ảnh hưởng overload resolution và name clashes.
14. **Kiểm thử reflection/frameworks** nếu schema/proxies/serialization dùng generic bounds.
15. **Cập nhật versioning và release notes** nếu thay đổi không tương thích; thông báo yêu cầu recompilation hoặc API migration.
16. **Giữ adapter tên và descriptors cũ nếu khả thi**, tránh phá clients mà không có lợi ích cần thiết.
17. **Không đánh đồng compiler errors với JVM linkage errors**: lỗi ở giai đoạn nào quyết định phương án khắc phục.

## 23. Câu hỏi tự kiểm tra

**1.** `<T extends Serializable & Comparable<T>>` có erasure T là gì?

**Đáp án:** Serializable, bởi Serializable là leftmost bound.

**2.** Nếu đảo thành `<T extends Comparable<T> & Serializable>`, erasure thành gì?

**Đáp án:** Comparable. Nếu T xuất hiện trong parameters/return của public method, descriptor có thể đổi tương ứng.

**3.** Java có cho viết `<T extends Serializable & Number>` để đưa Serializable lên đầu không?

**Đáp án:** Không. Nếu có class bound Number, class phải đứng đầu trước interface bounds.

**4.** Đảo `Number & Serializable & Comparable<T>` thành `Number & Comparable<T> & Serializable` có đổi erasure T không?

**Đáp án:** Không. Number vẫn là bound đầu tiên, nên erasure T vẫn là Number. Generic Signature/Reflection order vẫn đổi.

**5.** Vì sao String vẫn source-compatible với hai thứ tự Serializable/Comparable?

**Đáp án:** String thỏa cả Serializable và Comparable<String>. Cùng một tập constraints được yêu cầu, dù order thay đổi.

**6.** Vì sao client bytecode cũ có thể nhận NoSuchMethodError khi thư viện đảo hai interface bounds?

**Đáp án:** Client cũ gọi descriptor nhận/trả Serializable; method mới chỉ có descriptor nhận/trả Comparable. JVM không tìm được old method descriptor.

**7.** Nếu biên dịch lại Client.java với thư viện mới mà chạy được thì đã chứng minh binary compatibility chưa?

**Đáp án:** Chưa. Đó là source recompilation; phải chạy chính Client.class cũ với thư viện mới để kiểm tra binary linkage.

**8.** Khi thêm Comparable<T> sau Number mà giữ Number đầu, descriptor của method dùng T có bắt buộc đổi không?

**Đáp án:** Không; erasure T vẫn là Number. Nhưng caller dùng type không Comparable<T> có thể không compile được nữa, và implementation mới có thể gây lỗi khi xử lý binary cũ.

**9.** Field có kiểu T trong `Box<T extends Serializable>` thay sang `Box<T extends Comparable<T>>` có thể ảnh hưởng gì?

**Đáp án:** Field descriptor theo erasure đổi từ Serializable sang Comparable; một client cũ truy cập field theo descriptor cũ có thể gặp NoSuchFieldError.

**10.** Chỉ thay throws clause có tự đổi JVM method descriptor không?

**Đáp án:** Không, throws không phải một phần của JVM parameter/return descriptor. Nhưng source exception analysis, attributes và runtime behavior có thể bị ảnh hưởng.

**11.** Bản thư viện mới có bridge methods thì chắc chắn mọi binary caller cũ sẽ chạy không?

**Đáp án:** Không. Bridge methods chỉ xuất hiện ở các tình huống cụ thể; cần kiểm tra thực tế descriptors và running compatibility.

**12.** Reflection có thể cho thấy đổi secondary bounds không?

**Đáp án:** Có. `TypeVariable.getBounds()` trả các bounds theo thứ tự generic declaration; dù erasure không đổi, thứ tự mảng có thể khác.

**13.** Vì sao tools nên so sánh cả descriptor và Signature attribute?

**Đáp án:** Descriptor ảnh hưởng JVM linkage; Signature ảnh hưởng thông tin generic cho compiler, Reflection và công cụ metadata. Hai tầng có thể thay đổi độc lập.

**14.** Một API library đã phát hành có nên tự ý “sắp xếp bounds theo alphabet” không?

**Đáp án:** Không. Thứ tự bound đầu có thể quyết định erasure. Nếu muốn cải thiện readability, cần xem đó là API change, kiểm chứng ABI và áp dụng chính sách versioning.

**15.** Khi thay bound đầu, cách kiểm tra đáng tin nhất là gì?

**Đáp án:** Giữ bản client class đã compile trước thay đổi, thay riêng library binary, kiểm tra `javap` descriptors và chạy lại client cũ, đồng thời biên dịch lại source client để đánh giá source compatibility riêng.

## 24. Ghi nhớ

**Thứ tự bounds là một phần có ý nghĩa của generic API design, không chỉ là cách trình bày.**

~~~text
<T extends Serializable & Comparable<T>>
            │ bound đầu
            ▼
   erasure(T) = Serializable
            │
            ▼
   erased descriptor của T dùng Serializable

Đảo hai interface bounds:

<T extends Comparable<T> & Serializable>
            │ bound đầu mới
            ▼
   erasure(T) = Comparable
            │
            ▼
   erased descriptor của T dùng Comparable

→ Binary client cũ có thể tìm sai descriptor
→ NoSuchMethodError nếu method cũ không còn
~~~

Khi **bound đầu giữ nguyên** (như `Number & Serializable & Comparable<T>` đổi thành `Number & Comparable<T> & Serializable`), erasure của T vẫn Number: không phải mọi đổi thứ tự đều đổi descriptor. Tuy nhiên generic Signature/Reflection metadata đổi, và thêm/bớt secondary constraints còn có thể ảnh hưởng source compatibility, runtime behavior và code generation.

Để bảo vệ API đã công bố, hãy làm **cả ba bài test**: biên dịch lại source client, chạy client binary cũ với library mới, và kiểm thử hành vi. Kiểm tra `javap -s -p` cho erased descriptors, `javap -v` cho generic Signatures, và đánh giá bridge/inheritance/fields nếu type variable xuất hiện ở nhiều members.

**Nguyên tắc thực tiễn:** chọn bounds dựa trên nhu cầu thật, xem leftmost bound là một phần của thiết kế ABI, tránh đổi thứ tự tùy hứng trong public APIs và không đánh đồng “source vẫn hợp lệ” với “binary đã phát hành vẫn tương thích”.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="generic-api-design-009">Cân bằng tính linh hoạt, độ dễ hiểu và khả năng suy luận kiểu</a>

<details>
<summary>Xem chi tiết</summary>

# Cân bằng tính linh hoạt, độ dễ hiểu và khả năng suy luận kiểu trong Java Generic API

Một API có thể đúng về mặt hệ kiểu mà **khó gọi, khó đọc và khó nâng cấp**. Ngược lại, một chữ ký quá đơn giản có thể buộc caller phải sao chép dữ liệu hoặc ép kiểu không an toàn. Khi phát hành một API generic, mục tiêu là cân bằng ba yếu tố:

1. **Linh hoạt đúng nhu cầu:** nhận những nguồn/đích khác type arguments nhưng vẫn giữ ràng buộc cần thiết.
2. **Rõ hợp đồng:** chỉ nhìn tên method, parameters và kết quả là người đọc hiểu luồng dữ liệu, mutability và các điều kiện cần.
3. **Compiler dễ suy luận:** phần lớn call sites không phải ép kiểu, gõ explicit type witness hay cast lambda để javac chọn một lời gọi.

Bài này là **bài tổng hợp theo tình huống review API**, không lặp lại toàn bộ chứng minh invariance, PECS hoặc wildcard capture. Ta sẽ cùng thiết kế một pipeline xử lý events, đánh giá nhiều chữ ký thay thế, thử inference tại nơi gọi, kiểm tra ví dụ Java 17 và tìm rủi ro khi đổi public API.

## 1. Bắt đầu từ nơi gọi: mỗi quyết định phục vụ một yêu cầu

Giả sử ứng dụng có hai loại sự kiện:

~~~java
interface Tagged {
    String tag();
}
record Alert(String tag) implements Tagged {}
record Note(String tag) implements Tagged {}
~~~

Cần:
- Lấy những `Alert` có tag bắt đầu bằng `"a"`.
- Biến chúng thành danh sách String để hiển thị.
- Đưa kết quả đã chọn vào một collection theo dõi chung.
- Chỉnh sửa nội dung của một danh sách Alert mutable mà **giữ nguyên type argument Alert**.
- Gộp Alert và Note thành một danh sách Tagged mà không làm sai collection gốc.

Trước khi viết `<T>`, hãy phác thảo call sites mong muốn:

~~~java
List<Alert> alerts = List.of(
        new Alert("alpha"),
        new Alert("beta"),
        new Alert("atlas"));

Predicate<Tagged> beginsA =
        event -> event.tag().startsWith("a");

List<Tagged> chosen = select(alerts, beginsA);
List<String> labels = mapAll(chosen, Tagged::tag);

List<Object> audit = new ArrayList<>();
appendAll(chosen, audit);
~~~

Mục tiêu là viết được những lời gọi ấy **không unchecked cast và không copy chỉ để vượt incompatibility do type arguments**. Chữ ký API được thiết kế ngược từ nhu cầu đã xác định.

### Ba câu hỏi phải trả lời cho từng phương thức

- **Dữ liệu chảy về đâu?** Chỉ đọc nguồn, ghi vào đích, hay vừa đọc vừa ghi cùng nơi?
- **Kiểu được liên kết ở đâu?** Cần cùng T giữa source và output, hay chỉ một interface chung như Tagged?
- **Giá trị trả ra thuộc hợp đồng nào?** Một List<T> mới, Optional<T>, số lượng, hay side effect trên collection cũ?

Không cần type variable ở mọi method. Ví dụ chỉ lấy số lượng nên đủ dùng:

~~~java
static int count(Collection<?> values) {
    return values.size();
}
~~~

Chữ ký này không kém an toàn hơn một `<T> int count(Collection<T>)` trong trường hợp chỉ gọi `size()`; ngược lại, nó cho người đọc biết element type không liên quan.

## 2. Chấm một chữ ký theo ba chiều

| Tiêu chí | Câu hỏi review | Dấu hiệu chưa tốt |
| --- | --- | --- |
| **Linh hoạt** | Có nhận được List<Alert>, List<Note>, List<Tagged> khi thuật toán chỉ cần Tagged? | Caller phải copy sang List<Tagged> chỉ để gọi hàm đọc. |
| **Rõ nghĩa** | Tham số nào bị sửa? Kết quả có alias nguồn không? | Tên `copy` nhưng lại append vào đích, hoặc tên `select` nhưng trả view mutable không báo. |
| **Suy luận kiểu** | Java tự biết T/R từ arguments, callback và expected type? | Hầu hết callers phải viết `.<Tagged>` hoặc ép lambda vì chữ ký/overloads mơ hồ. |
| **Hạn chế runtime** | Null, empty, mutability, order, exception và partial failure có rõ? | Generic signature đúng nhưng implementation làm sai kỳ vọng dữ liệu. |
| **Tương thích** | Thay chữ ký có đổi erased descriptor, nguồn gọi hay overload resolution? | Một cải tiến PECS làm client binary cũ mất method. |

Không cần API “tổng quát nhất”: một method chỉ phục vụ domain cụ thể thường nên nhận interface domain cụ thể. Type parameters và wildcards là công cụ diễn đạt hợp đồng, không phải thành tích cần tối đa hóa.

## 3. Thiết kế nguồn đọc: `? extends T` và callback nhận supertype

Đối với phép lọc, chúng ta muốn caller truyền danh sách Alert **và** một Predicate biết làm việc với interface Tagged.

~~~java
static <T> List<T> select(
        Iterable<? extends T> source,
        Predicate<? super T> predicate) {

    List<T> result = new ArrayList<>();

    for (T value : source) {
        if (predicate.test(value)) {
            result.add(value);
        }
    }

    return result;
}
~~~

Cấu trúc có chủ đích:
- `Iterable<? extends T>`: chỉ cần duyệt phần tử từ producer, không cần index hoặc mutation.
- `Predicate<? super T>`: predicate tiêu thụ T, nên có thể là một predicate nhận supertype như Tagged.
- `List<T>`: caller nhận **collection mới** với element type được suy luận; không phải wildcard result làm hạn chế thao tác không cần thiết.

Ở call site:

~~~java
List<Tagged> chosen = select(alerts, beginsA);
~~~

Compiler có thể chọn T phù hợp với Alert ở nguồn, Tagged ở predicate và **target type List<Tagged>**. Do đó callers không cần cast List<Alert> thành List<Tagged> — phép cast đó vốn không hợp lệ dưới invariance.

Một biến thể:

~~~java
List<Alert> chosenAlerts = select(alerts, beginsA);
~~~

cũng có thể hợp lệ. Trong trường hợp này T được suy luận phù hợp với Alert. Cùng một generic method hỗ trợ cả hai cách yêu cầu output mà không ghi sai type arguments của nguồn.

### Biến `var` và target typing

~~~java
var selected = select(alerts, beginsA);
~~~

`var` không viết rõ expected type List<Tagged> ở vế trái. Compiler suy luận theo những ràng buộc còn lại, và kết quả có thể khác với lời gọi có target type tường minh. Không nên dạy rằng `var` luôn suy ra **ý định nghiệp vụ** của caller.

Nếu downstream cần chính xác `List<Tagged>`, khai báo kiểu đích tường minh thường tốt hơn việc bắt caller thêm unchecked cast sau đó.

## 4. Biến đổi kết quả: đặt tên T/R theo chiều dữ liệu

Một mapper đọc dữ liệu thuộc T và **sản xuất loại kết quả R mới**:

~~~java
static <T, R> List<R> mapAll(
        Iterable<? extends T> source,
        Function<? super T, ? extends R> mapper) {

    List<R> result = new ArrayList<>();

    for (T value : source) {
        result.add(mapper.apply(value));
    }

    return result;
}
~~~

Có hai ràng buộc đáng được đặt tên:
- `T` liên kết elements nguồn với input của mapper.
- `R` liên kết output mapper với elements của list kết quả.

Gọi:

~~~java
List<String> names = mapAll(chosen, Tagged::tag);
~~~

`Tagged::tag` là method reference có target functional interface được suy luận từ lời gọi. Không cần `Function<Object,Object>`, cũng không cần ép kiểu toàn bộ list xuống Object.

**Nhưng** nếu API chỉ có nhiệm vụ cụ thể “lấy tag từ Tagged”, một method `List<String> tags(Iterable<? extends Tagged>)` có thể dễ hiểu hơn generic `mapAll`. Chỉ tạo một helper tổng quát khi nó thật sự giảm lặp và có nhiều use cases hợp lý.

### Đừng lạm dụng interface rộng

Nếu method cần `get(index)` hoặc `replaceAll`, nhận `List<T>` thay vì `Iterable<T>` vì cần hành vi của List. Ngược lại, một method chỉ lặp không cần bắt caller cấp random-access list.

Một interface quá rộng **về quyền thao tác** làm implementation thiếu contract; một interface quá hẹp **về dạng collection** buộc caller làm adapter vô ích.

## 5. Đích nhận dữ liệu: dùng PECS nhưng không che side effects

Khi muốn append các events vào collection theo dõi:

~~~java
static <T> void appendAll(
        Iterable<? extends T> source,
        Collection<? super T> destination) {

    for (T value : source) {
        destination.add(value);
    }
}
~~~

Chữ ký mô tả một chiều:
- Source đọc ra T.
- Destination nhận T hoặc subtype của T thích hợp.
- Một `<T>` liên kết hai phía; hai dấu `?` hoàn toàn độc lập **không chứng minh** chúng có thể trao đổi elements.

Ví dụ:

~~~java
List<Object> audit = new ArrayList<>();
appendAll(chosen, audit);
~~~

Không có ý định đọc giá trị từ audit rồi ghi ngược về source. Nếu cần trao đổi hai chiều giữa danh sách, phải thiết kế một hợp đồng mạnh hơn, có thể yêu cầu `List<T>` ở cả hai phía.

Đặt tên `appendAll` rõ rằng destination **tăng thêm phần tử**. Phương thức JDK `Collections.copy(dest,src)` cũng dùng PECS nhưng **thay theo index** và đòi destination có đủ size; đừng bắt chước tên `copy` mà thay hành vi.

Cần mô tả thêm nếu public:
- Destination phải hỗ trợ `add`, không phải mọi Collection đều mutable.
- Nếu source/destination có alias và thực hiện append khi đang iterate, hành vi có thể lỗi hoặc không phù hợp; có thể yêu cầu không alias hoặc snapshot.
- Nếu `add` ném exception ở giữa, một số phần tử có thể đã được thêm; không tự hứa atomic operation.
- Collection có thể không giữ duplicates hoặc iteration order theo cách người dùng kỳ vọng.

Generics chỉ giải quyết **tính tương thích của phần tử**, không giải quyết hết các điều kiện runtime này.

## 6. Khi cần đọc rồi ghi lại cùng loại, giữ `List<T>`

Một phép sửa tại chỗ:

~~~java
static <T> void rewrite(
        List<T> values,
        UnaryOperator<T> editor) {

    values.replaceAll(editor);
}
~~~

Chữ ký này có chủ đích dùng **exact named T**: đọc T, biến đổi thành T, ghi T về **cùng list**. Nó không phải bài toán “source producer, target consumer” độc lập.

Ví dụ:

~~~java
List<Alert> editable = new ArrayList<>(alerts);

rewrite(editable,
        alert -> new Alert(alert.tag().toUpperCase()));
~~~

Nếu thay toàn bộ thành `List<? extends Tagged>` thì compiler không thể bảo đảm một Tagged bất kỳ có thể ghi vào một list thực tế đang là List<Alert>. Nếu dùng `List<? super Alert>`, phía đọc chỉ được bảo đảm như Object. Quan hệ một T có tên là phần hợp đồng cần giữ.

Chọn `List<T>` hay `Map<K,V>` khi method cần đọc và cập nhật cùng type contract; dùng wildcard khi có một chiều linh hoạt. **Đừng dùng raw cast để đánh lừa hệ kiểu.**

## 7. Một ví dụ Java 17 hoàn chỉnh để đánh giá cả pipeline

Lưu tệp **GenericApiDecisionDemo.java**:

~~~java
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

public class GenericApiDecisionDemo {
    interface Tagged {
        String tag();
    }

    record Alert(String tag) implements Tagged {}
    record Note(String tag) implements Tagged {}

    static int count(Collection<?> values) {
        return values.size();
    }

    static <T> List<T> select(
            Iterable<? extends T> source,
            Predicate<? super T> predicate) {

        List<T> result = new ArrayList<>();
        for (T value : source) {
            if (predicate.test(value)) {
                result.add(value);
            }
        }
        return result;
    }

    static <T, R> List<R> mapAll(
            Iterable<? extends T> source,
            Function<? super T, ? extends R> mapper) {

        List<R> result = new ArrayList<>();
        for (T value : source) {
            result.add(mapper.apply(value));
        }
        return result;
    }

    static <T> void appendAll(
            Iterable<? extends T> source,
            Collection<? super T> destination) {

        for (T value : source) {
            destination.add(value);
        }
    }

    static <T> void rewrite(
            List<T> values, UnaryOperator<T> mapper) {

        values.replaceAll(mapper);
    }

    public static void main(String[] args) {
        List<Alert> alerts = List.of(
                new Alert("alpha"),
                new Alert("beta"),
                new Alert("atlas")
        );

        Predicate<Tagged> beginsA =
                event -> event.tag().startsWith("a");

        List<Tagged> chosen = select(alerts, beginsA);
        System.out.println(
                "chosen=" + mapAll(chosen, Tagged::tag)
        );
        System.out.println("count=" + count(chosen));

        List<Object> audit = new ArrayList<>();
        appendAll(chosen, audit);
        System.out.println("audit=" + audit.size());

        List<Alert> editable = new ArrayList<>(alerts);
        rewrite(editable,
                alert -> new Alert(
                        alert.tag().toUpperCase()));
        System.out.println(
                "edited=" + mapAll(editable, Alert::tag)
        );

        List<Note> notes = List.of(new Note("gamma"));
        List<Tagged> all = new ArrayList<>();
        appendAll(alerts, all);
        appendAll(notes, all);
        System.out.println(
                "all=" + mapAll(all, Tagged::tag)
        );
        System.out.println(
                "source=" + mapAll(alerts, Alert::tag)
        );
    }
}
~~~

Biên dịch/chạy:

~~~bash
javac --release 17 -Xlint:all GenericApiDecisionDemo.java
java GenericApiDecisionDemo
~~~

**Stdout đã kiểm chứng:**

~~~text
chosen=[alpha, atlas]
count=2
audit=2
edited=[ALPHA, BETA, ATLAS]
all=[alpha, beta, atlas, gamma]
source=[alpha, beta, atlas]
~~~

**Tại sao đây là ví dụ về quyết định thiết kế, không chỉ demo syntax?**

- `chosen`: kết quả List<Tagged> được suy luận theo type target, dù nguồn là List<Alert>.
- `mapAll`: trả List<String> từ callback Tagged::tag mà caller không cast.
- `audit`: source đọc như Tagged, đích List<Object> nhận được nhờ `? super T`; phương thức có side effect rõ.
- `editable`: phép sửa tại chỗ giữ chính loại Alert; nguồn List.of không bị sửa.
- `all`: ghép Alert và Note vào danh sách Tagged mà không ép kiểu một List<Alert> thành List<Tagged>.
- `source`: nguồn ban đầu còn nguyên, cho thấy những thao tác nào tạo list mới và thao tác nào thay đổi nơi khác.

Thử thay `Predicate<Tagged>` bằng một predicate chỉ nhận `Note`. Compiler phải từ chối lời gọi `select(alerts, predicateOfNote)` nếu không có kiểu T thỏa ràng buộc; không dùng unchecked cast để làm cho lời gọi sai ý nghĩa chạy được.

## 8. Target typing, lambda và type inference: lỗi thường nằm ở call site

Một generic method có thể dùng thông tin từ:
- Argument types.
- Target type ở assignment, return hoặc invocation contexts theo quy tắc Java.
- Functional interface target của lambda/method reference.
- Bounds và wildcard constraints của các type variables.

Bởi vậy, **cùng tên method không bảo đảm mọi lời gọi chọn cùng một T**:

~~~java
List<Alert> onlyAlerts = select(alerts, beginsA);
List<Tagged> anyTagged = select(alerts, beginsA);
~~~

Cả hai cách dùng giữ quan hệ được compiler kiểm tra; chúng không làm ArrayList/List mang runtime class riêng cho từng type parameter.

### Khi inference không rõ, sửa hợp đồng trước

Một mẹo debug được phép là tách lời gọi thành các biến trung gian có type rõ:

~~~java
Predicate<Tagged> selectedByTag =
        element -> element.tag().startsWith("a");

List<Tagged> filtered =
        select(alerts, selectedByTag);

List<String> labels =
        mapAll(filtered, Tagged::tag);
~~~

Đừng bắt caller lặp đi lặp lại `(Function<...>)`, raw `List` hoặc `MyApi.<Object>...` chỉ để chữa một API thiết kế quá khó suy luận.

Dù vậy, **một số explicit type witness** là hợp lý trong trường hợp hiếm gặp, đặc biệt khi mục đích lựa chọn T không thể hiện rõ từ đối số. Đánh giá API dựa trên các use cases thường nhật, không đòi inference thần kỳ ở mọi expression.

## 9. Overload dễ nhập nhằng: đừng tạo hai functional targets ngang nhau

Hai overload sau có erased parameters khác nhau nên có thể cùng tồn tại:

~~~java
static void load(
        java.util.function.Supplier<String> source) {}

static void load(
        java.util.concurrent.Callable<String> source) {}
~~~

Nhưng lời gọi:

~~~java
// CỐ Ý KHÔNG BIÊN DỊCH:
// load(() -> "ready");
~~~

có thể **ambiguous** vì lambda không tự mang một functional interface class cụ thể. Compiler không nên phải đoán xem tác giả muốn Supplier hay Callable chỉ từ body trả String.

Cách giải quyết:
- Đặt hai tên diễn đạt nghiệp vụ (`loadFromSupplier`, `loadFromCallable`) nếu hợp đồng xử lý khác nhau.
- Chọn một functional interface làm public contract nếu hai overload chỉ gây trùng nghĩa.
- Khi thực sự cần hai overloads, caller có thể cung cấp một biến typed, chẳng hạn:

~~~java
java.util.function.Supplier<String> supplier =
        () -> "ready";

// load(supplier); // Sẽ chọn overload Supplier.
~~~

Nếu hai overload đều nhận `List<String>` và `List<Integer>`, vấn đề còn sớm hơn: do erasure chúng **cùng erased signature** và không thể khai báo cạnh nhau trong một class. Không cố dùng runtime type arguments để dispatch, vì Java overload resolution dựa trên compile-time types.

## 10. Public API: linh hoạt hơn chưa chắc tương thích với bản cũ

Giả sử bản v1 công bố:

~~~java
void inspect(List<Tagged> events)
~~~

Bản v2 muốn chấp nhận nhiều nguồn và đổi thành:

~~~java
void inspect(Collection<? extends Tagged> events)
~~~

Bản mới có thể tốt hơn về **source flexibility**, nhưng raw parameter class đã đổi **List → Collection**. JVM method descriptor vì thế thay đổi. Một client `.class` cũ có thể vẫn tìm phương thức nhận List, và gặp lỗi liên kết như NoSuchMethodError nếu descriptor cũ không được giữ lại.

Trong khi đó, đổi một type argument/wildcard trên cùng raw List đôi khi giữ descriptor tương đương, **nhưng** source overload resolution, generic signature/reflection và type inference có thể thay đổi.

Đừng suy rằng “chỉ sửa phần chữ trong dấu `<...>` thì chắc chắn tương thích”. Cần phân biệt:
1. **Source compatibility:** caller có biên dịch lại với v2?
2. **Binary compatibility:** caller `.class` từ v1 còn liên kết với v2?
3. **Behavioral compatibility:** null, order, mutation, exceptions và semantics có đổi?
4. **Reflection/tool compatibility:** generic Signature metadata hoặc bounds có khác?

### Cách kiểm chứng một thay đổi public API

~~~bash
# Biên dịch các phiên bản v1/v2 ở thư mục class riêng.
javap -p -s YourLibraryClass
javap -v YourLibraryClass

# Chạy client đã biên dịch bằng v1 cùng library v2,
# KHÔNG biên dịch lại client ở phép thử binary compatibility.
~~~

Nếu đổi bounds của type variable, đặc biệt leftmost bound, erased return/parameter descriptors có thể đổi dù tên method vẫn vậy. Khi thư viện đã phát hành, không sửa API dựa trên cảm giác một chữ ký “dễ đọc hơn”; đối chiếu class files và thử binary client cũ.

## 11. Những yếu tố hệ kiểu không bảo đảm

| Điều hệ kiểu thể hiện | Điều vẫn phải ghi trong hợp đồng |
| --- | --- |
| Source cho phép đọc T, destination cho phép nhận T. | Destination có mutable không, append có thể partial failure không. |
| Return type là List<T>. | List mới hay view, mutable hay unmodifiable, có alias dữ liệu không. |
| Callback nhận T và trả R. | Callback có thể ném exception, thay đổi trạng thái, trả null không. |
| `Optional<T>` chứa giá trị T khi có. | Không tìm thấy khác lỗi dữ liệu, Optional.empty() được dùng khi nào. |
| `Comparator<? super T>` có thể so sánh T. | Null ordering, tính nhất quán, sort stability theo contract. |
| `Class<T>` đại diện một runtime class. | Không phải type token tự xác thực nested type arguments. |
| Type parameter có bounds. | Bounds không mô tả domain semantics, limits hoặc authorization. |

Ví dụ `List.copyOf(source)` không cho sửa cấu trúc list qua API thông thường và không cho null elements, nhưng nó không deep-copy các element objects mutable. Nếu user đồng thời thay đổi source không đồng bộ, việc lấy snapshot cũng cần chính sách thread safety riêng.

Một API dễ dùng cần tài liệu hóa các điều kiện runtime **ngắn và chính xác** thay vì giả định generics tự giải quyết mọi thứ.

## 12. Ma trận kiểm thử trước khi phát hành

Đừng chỉ viết một happy path với List<String>. Hãy dùng những call sites đại diện:

| Trường hợp | Điều cần chứng minh |
| --- | --- |
| Producer subtype | `List<Alert>` có thể dùng ở method chỉ đọc Tagged. |
| Supertype consumer | `Collection<Object>` có thể nhận Tagged. |
| Same-type mutation | `List<Alert>` không cho ghi Note khi API giữ T=Alert. |
| Callback supertype | `Predicate<Tagged>` dùng được trên nguồn Alert. |
| Callback result type | Mapper `Tagged::tag` cho kết quả `List<String>`. |
| Target typing | Cả `List<Alert>` và `List<Tagged>` ở assignment contexts thích hợp. |
| Return-only type contract | Đầu ra giữ được kiểu cần thiết, không phải Object gây cast muộn. |
| Overload ambiguity | Lambda trần không bị giao hai functional target không phân biệt nổi. |
| Immutable source | Không thử sửa trực tiếp danh sách `List.of`. |
| Mutable destination | `appendAll` xử lý đúng ngữ nghĩa thêm vào cuối; alias/partial failure có chính sách. |
| Binary compatibility | Giữ client `.class` cũ khi test đổi descriptors. |
| Invalid generic calls | Source code sai bị compiler từ chối; không suppress để test qua. |

### Negative compilation: ba ví dụ phải bị từ chối

Để từng đoạn vào **file thử nghiệm riêng**, không ghép vào chương trình runnable.

**A — Invariance:**

~~~java
List<Alert> alerts = new ArrayList<>();
// List<Tagged> notAllowed = alerts; // Compile error.
~~~

**B — Sai chiều dữ liệu:**

~~~java
List<? extends Tagged> source = new ArrayList<Alert>();
// source.add(new Note("bad")); // Compile error.
~~~

**C — Ghi nhầm loại vào một list chính xác:**

~~~java
List<Alert> alerts = new ArrayList<>();
// rewrite(alerts, alert -> new Note(alert.tag()));
// Compile error: UnaryOperator<Alert> phải trả Alert.
~~~

Bởi các dòng sai đã được comment, file chứa chúng có thể được làm **runnable test harness** nếu đặt trong class phù hợp. Để thực sự làm *negative compilation test*, bỏ comment đúng dòng đang kiểm tra và xác nhận `javac` trả exit code khác 0. Không nhầm một comment có diễn giải lỗi với bằng chứng compiler đã thực sự bị test.

## 13. Trình tự sửa một chữ ký API đang khó dùng

Một quy trình review tiết kiệm công sức:

1. **Viết hai call sites hợp lệ điển hình và một call site phải bị từ chối**. Nếu không diễn đạt được, có thể contract chưa rõ.
2. **Chọn mức abstraction**: Iterable để duyệt, Collection để thêm, List để dùng index/mutation, domain interface khi thuật toán phụ thuộc hành vi nghiệp vụ.
3. **Vẽ luồng dữ liệu**: producer → mapper → consumer, hoặc read/modify/write cùng T.
4. **Đặt type variables chỉ ở những quan hệ cần giữ**, tránh `<T>` thừa trong hàm chỉ `size()`.
5. **Áp dụng wildcard ở ranh giới dữ liệu một chiều**, không dùng wildcard để che quan hệ hai chiều hoặc thay đổi kiểu phần tử bằng cast.
6. **Cho output một type hữu ích**, thường `List<R>`/`Optional<T>` thay vì `Object`/wildcard result không cần thiết.
7. **Kiểm tra Java inference** với lambda/method reference, target type rõ và `var`.
8. **Rà overloads**: xung đột erasure, ambiguous lambda, null hoặc compiler chọn overload khác ý định.
9. **Viết contract runtime**: null, empty, mutability, aliasing, ordering, error behavior.
10. **Nếu API đã public, kiểm tra source/binary compatibility** bằng javac, javap và client đã compile với phiên bản cũ.
11. **Ghi Javadoc ngắn về quan hệ type parameters và ràng buộc ngoài hệ kiểu**, không chép lại chữ ký mà thiếu hành vi.
12. **Chỉ tối ưu complexity khi có ích thực tế**; một generic framework mới không tốt hơn một method domain ngắn nếu chỉ có một use case.

## 14. Câu hỏi đánh giá cuối chương

**1.** Method chỉ đếm số phần tử của bất kỳ Collection. Có cần `<T>` không?

**Đáp án:** Không cần nếu không có ràng buộc với các vị trí khác; `Collection<?>` rõ hơn.

**2.** Tại sao `List<Alert>` không gán trực tiếp vào `List<Tagged>`?

**Đáp án:** Generic type arguments invariant. List<Tagged> có thể nhận Note, trong khi List<Alert> không thể.

**3.** Vì sao method `select` trả `List<T>` nhưng nhận `Iterable<? extends T>`?

**Đáp án:** Input chỉ cấp các giá trị có thể dùng như T; output được tạo mới với element type T liên quan tới caller/target type.

**4.** Tại sao callback trong `select` là `Predicate<? super T>`?

**Đáp án:** Predicate tiêu thụ T; một predicate nhận supertypes của T vẫn xử lý được mọi T.

**5.** Tại sao `rewrite` dùng `List<T>` và `UnaryOperator<T>`, không `List<? extends T>`?

**Đáp án:** Nó phải đọc rồi ghi lại cùng element type trên cùng list; wildcard producer không cho ghi tùy ý một T mới.

**6.** Tại sao `load(() -> "ready")` có thể ambiguous nếu overload Supplier<String> và Callable<String>?

**Đáp án:** Lambda trần tương thích với hai functional target ngang hàng, javac không có đủ khác biệt để chọn.

**7.** Khi nào một generic helper `mapAll` thực sự đáng tạo?

**Đáp án:** Khi có nhiều phép biến đổi cùng cấu trúc và việc giữ quan hệ T→R giúp caller tránh duplicate code/casts; không phải vì cú pháp phức tạp hơn trông tốt hơn.

**8.** `List<Tagged> chosen = select(alerts, beginsA)` có làm alerts thành List<Tagged> không?

**Đáp án:** Không. select xây kết quả mới theo hợp đồng T, nguồn vẫn là List<Alert> và không bị ép thành một parameterized type khác.

**9.** Thay tham số public từ List sang Collection có thể phá client đã compile không?

**Đáp án:** Có. Erased method descriptor đổi từ List sang Collection, nên binary client cũ có thể không tìm được method descriptor nó đã liên kết.

**10.** Generic signature có đủ hứa `appendAll` atomic/thread-safe không?

**Đáp án:** Không. Type safety là một tầng contract; atomicity, mutability, concurrent modification và partial failures cần quy định/kiểm chứng riêng.

**Kết luận:** Chữ ký generic tốt nhất không phải chữ ký dài nhất hay ngắn nhất. Nó là **chữ ký đơn giản nhất vẫn đủ quan hệ kiểu**, nhận đúng các call sites an toàn, để compiler suy luận tự nhiên và giải thích rõ hành vi mà hệ kiểu không mô tả. Khi API được phát hành rộng rãi, **tính tương thích** là chiều đánh giá bắt buộc bên cạnh độ linh hoạt, tính dễ hiểu và suy luận kiểu.


</details>

- [Quay lại đầu trang](#back-to-top)

<a id="back-to-top"></a>

# Cú pháp cơ bản

## Menu
- [Khai báo một hoặc nhiều tham số kiểu](#generics-syntax-001)
- [Quy ước tên T, E, K, V và cách đặt tên có nghĩa](#generics-syntax-002)
- [Truyền đối số kiểu và tạo đối tượng kiểu tham số hóa](#generics-syntax-003)
- [Toán tử diamond &lt;&gt; và khác biệt với raw type](#generics-syntax-004)
- [Kiểu tham số hóa lồng nhau như Map&lt;K,List&lt;V&gt;&gt;](#generics-syntax-005)
- [Chỉ dùng kiểu tham chiếu: primitive, kiểu bao và autoboxing](#generics-syntax-006)
- [Giá trị null, giới hạn thao tác trên T và kiểu Object mặc định](#generics-syntax-007)
- [Đọc chữ ký generic và theo dõi nơi ràng buộc từng biến kiểu](#generics-syntax-008)

## <a id="generics-syntax-001">Khai báo một hoặc nhiều tham số kiểu</a>

<details>
<summary>Xem chi tiết</summary>

# Khai báo một hoặc nhiều tham số kiểu trong Java Generics

## 1. Mục tiêu

Java Generics cho phép khai báo một lớp, interface, record, phương thức hoặc constructor có **một hay nhiều tham số kiểu** (*type parameters*). Các tham số kiểu là tên đại diện cho kiểu dữ liệu sẽ được lựa chọn khi dùng API. Chúng giúp trình biên dịch kiểm tra mối quan hệ giữa giá trị đầu vào, giá trị lưu trữ và kết quả trả về mà không cần ép kiểu thủ công.

Sau bài này, bạn có thể:

- Viết đúng cú pháp **<T>**, **<K, V>**, **<I, O>** cho từng loại khai báo.
- Nhận ra đâu là vị trí **khai báo** tham số kiểu và đâu là vị trí **truyền đối số kiểu**.
- Phân biệt tham số kiểu thuộc class với tham số kiểu riêng của method/constructor.
- Hiểu tính độc lập, thứ tự, phạm vi và ràng buộc cơ bản của nhiều tham số kiểu.
- Áp dụng cú pháp Java 17+ trong một chương trình biên dịch, chạy được và tránh các lỗi thường gặp.

Đầu tiên, so sánh hai khai báo:

~~~java
class Box<T> {
    private T value;
}

class Pair<K, V> {
    private K key;
    private V value;
}
~~~

**Box<T>** khai báo **một** tham số kiểu T. **Pair<K, V>** khai báo **hai** tham số kiểu K và V. Một chữ như T, K hoặc V chỉ là *tên* của type parameter: nó không tự đại diện cho String, Integer hay một class nào cho đến khi xuất hiện ở ngữ cảnh sử dụng cụ thể.

## 2. Ngữ pháp khai báo: dấu ngoặc nhọn góc

Dạng cơ bản:

~~~text
class TênLớp<T> { ... }
class TênLớp<K, V> { ... }
interface TênInterface<I, O> { ... }
record TênRecord<A, B>(A first, B second) { ... }

<T> KiểuTrảVề tênPhươngThức(... ) { ... }
<A, B> KiểuTrảVề tênPhươngThức(... ) { ... }
~~~

Các nguyên tắc cú pháp:

1. Danh sách type parameters nằm trong **<...>** và được ngăn cách bằng **dấu phẩy**. Không có dấu phẩy sau tham số cuối: **<K, V>** hợp lệ; **<K, V,>** không hợp lệ.
2. Mỗi tên type parameter phải là định danh Java hợp lệ, phân biệt chữ hoa/chữ thường. Ví dụ T và t là hai tên khác nhau, dù quy ước thường viết T, E, K, V bằng chữ hoa.
3. Trong một danh sách, các tên phải **khác nhau**: **<T, T>** không hợp lệ vì khai báo trùng tên.
4. Dấu ngoặc trong khai báo class/interface/record đi **ngay sau tên** loại; danh sách của generic method đi **trước kiểu trả về**; danh sách của generic constructor đi **trước tên constructor**.
5. Có thể khai báo một, hai, ba hoặc nhiều tham số kiểu, không có giới hạn cú pháp cố định là chỉ một T; hãy dùng đúng số lượng cần thiết để biểu diễn các quan hệ kiểu có ý nghĩa.
6. Danh sách type parameters là phần khai báo **kiểu**, không phải danh sách tham số nhận giá trị. Ví dụ constructor **Pair(K key, V value)** có hai tham số giá trị tên key và value, còn **class Pair<K, V>** có hai tham số kiểu K và V.

Một ví dụ dễ đọc:

~~~java
class Triple<A, B, C> {
    private final A first;
    private final B second;
    private final C third;

    Triple(A first, B second, C third) {
        this.first = first;
        this.second = second;
        this.third = third;
    }

    A first() { return first; }
    B second() { return second; }
    C third() { return third; }
}
~~~

A, B và C là ba tên kiểu độc lập; chúng có thể được gán cùng kiểu hoặc các kiểu khác nhau tại từng cách sử dụng Triple. Độ phức tạp không tự động tăng chất lượng API: nếu tất cả thuộc tính luôn cùng kiểu và có vai trò giống nhau, một tham số kiểu T có thể biểu diễn tốt hơn ba tên độc lập.

## 3. Khai báo một tham số kiểu cho class

Khai báo phổ biến nhất là **class Box<T>**: một kiểu T được dùng nhất quán cho giá trị đưa vào, lưu trữ và lấy ra.

~~~java
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
}
~~~

Trong phần **class Box<T>**, T được **khai báo**. Sau đó:

- **private T value**: field thuộc kiểu T.
- **Box(T value)**: tham số giá trị của constructor phải có kiểu T.
- **T get()**: phương thức trả về T.
- **void set(T value)**: phương thức nhận giá trị thuộc T.

Khi sử dụng, ta truyền *type argument* vào chỗ đã khai báo:

~~~java
// Minh họa trong thân phương thức hoặc khối lệnh:
Box<String> label = new Box<>("Java");
Box<Integer> counter = new Box<>(10);

String text = label.get();
Integer number = counter.get();
~~~

**String** và **Integer** ở đây là *đối số kiểu* (*type arguments*), còn **Box<String>** và **Box<Integer>** là những *kiểu tham số hóa* (*parameterized types*). Việc khai báo class chỉ viết một lần; compiler áp dụng quan hệ kiểu của nó cho nhiều cách sử dụng.

**new Box<>()** dùng *diamond operator*: đối số kiểu của constructor được suy luận từ ngữ cảnh. Phía khai báo **class Box<T>** vẫn cần nêu rõ tên T; không thể khai báo class bằng **class Box<>**.

Trong Java, tham số kiểu mặc định dùng một kiểu tham chiếu. **Box<int>** là lỗi, cần **Box<Integer>**; **Box<String>**, **Box<int[]>** và **Box<List<String>>** đều có thể hợp lệ nếu những kiểu được sử dụng đã được khai báo hoặc import đúng.

## 4. Khai báo nhiều tham số kiểu cho class

Khai báo **<K, V>** thường mô tả hai kiểu có vai trò khác nhau, ví dụ khóa và giá trị. Mỗi tên được phân tách bằng dấu phẩy, và mỗi vị trí có ý nghĩa riêng.

~~~java
class Entry<K, V> {
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
~~~

Khi sử dụng:

~~~java
// Minh họa trong thân phương thức hoặc khối lệnh:
Entry<String, Integer> age = new Entry<>("Lan", 20);
Entry<Long, Long> interval = new Entry<>(100L, 200L);

String name = age.key();    // K là String
Integer years = age.value(); // V là Integer
~~~

**Thứ tự theo vị trí** rất quan trọng. Với **Entry<String, Integer>**, K tương ứng String và V tương ứng Integer; đảo thành **Entry<Integer, String>** tạo một parameterized type khác, trong đó vai trò hai kiểu cũng đảo ngược.

**K và V không bắt buộc phải khác nhau**. Entry<Long, Long> hoàn toàn hợp lệ vì hai *tham số* K, V là hai vị trí khác nhau, dù hai *đối số* truyền vào trùng Long.

Nhiều tham số kiểu có thể lồng nhau:

~~~java
class Table<K, V> {
    private java.util.Map<K, java.util.List<V>> rows =
            new java.util.HashMap<>();
}
~~~

Trong **class Table<K, V>**, chỉ **K và V** được khai báo mới. Biểu thức **Map<K, List<V>>** đang dùng K và V làm type arguments cho hai generic types đã có là Map và List; không có khai báo K/V bổ sung bên trong Map hay List.

### Khi nào dùng một T, khi nào dùng nhiều tên?

- **Box<T>** phù hợp khi mọi thao tác gắn với cùng một kiểu giá trị.
- **Pair<A, B>** phù hợp khi muốn mang theo hai giá trị mà mỗi bên có thể có kiểu riêng.
- **Mapper<I, O>** phù hợp khi kiểu nhận vào có thể khác kiểu trả ra.
- **Repository<ID, E>** phù hợp khi kiểu khóa định danh có vai trò khác kiểu thực thể.
- **Triple<A, B, C>** chỉ nên xuất hiện khi thật sự có ba vai trò kiểu cần được biểu diễn.

Một thiết kế với nhiều tham số kiểu sẽ khó đọc nếu người dùng không hiểu vai trò của từng tên. Ưu tiên tên hoặc tài liệu API rõ nghĩa và giữ số lượng tham số kiểu vừa đủ.

## 5. Khai báo type parameters trên interface và record

Generics không chỉ dành cho class. Interface có thể khai báo một hoặc nhiều tham số kiểu:

~~~java
@FunctionalInterface
interface Converter<I, O> {
    O convert(I input);
}
~~~

I biểu diễn kiểu dữ liệu đi vào; O biểu diễn kiểu kết quả. Một triển khai sử dụng cụ thể:

~~~java
// Minh họa trong thân phương thức hoặc khối lệnh:
Converter<String, Integer> parser = Integer::parseInt;
Integer value = parser.convert("42");
~~~

Tại khai báo interface, I và O chưa được xác định thành kiểu cụ thể. Khi gán Converter<String, Integer>, mối quan hệ trở thành “nhận String, trả Integer”. Phương thức convert không cần tự viết **<I, O>**, vì đã có hai type variables từ interface.

Java 17 hỗ trợ generic record với cú pháp tương tự:

~~~java
record Point<X, Y>(X x, Y y) {}
~~~

**Point<X, Y>** khai báo hai tham số kiểu cho record; các thành phần x và y dùng các tên đó làm kiểu. Ví dụ **Point<Integer, Integer> point = new Point<>(3, 5)** là hợp lệ. Record tự cung cấp constructor, accessor, equals, hashCode và toString theo đặc tả record. Generic record không làm thay đổi cách khai báo danh sách **<X, Y>**.

Khai báo có thể thêm ràng buộc cho từng tham số (xem mục 9), và một interface generic cũng có thể được triển khai với đối số cụ thể hoặc truyền tiếp các type variables:

~~~java
class StringLength implements Converter<String, Integer> {
    @Override
    public Integer convert(String input) {
        return input.length();
    }
}

class DelegatingConverter<I, O> implements Converter<I, O> {
    private final Converter<I, O> delegate;

    DelegatingConverter(Converter<I, O> delegate) {
        this.delegate = delegate;
    }

    @Override
    public O convert(I input) {
        return delegate.convert(input);
    }
}
~~~

Trong StringLength, String/Integer là type arguments cụ thể. Trong DelegatingConverter, **<I, O>** là khai báo mới của class, và **implements Converter<I, O>** truyền hai type variables ấy làm đối số cho interface. Tên I/O ở hai khai báo không phải một “biến toàn cục”; chúng liên kết với nhau thông qua chữ ký implements.

## 6. Khai báo generic method: dấu <...> nằm trước kiểu trả về

Generic method tự khai báo type parameters của nó, không phụ thuộc vào việc lớp chứa nó có generic hay không. Cú pháp đúng:

~~~java
static <T> T identity(T value) {
    return value;
}

static <A, B> String format(A first, B second) {
    return String.valueOf(first) + " / " + String.valueOf(second);
}

static <T> void show(T value) {
    System.out.println(value);
}
~~~

Trật tự thành phần khi khai báo phương thức static là **static → <T> → kiểu trả về → tên phương thức → (tham số giá trị)**. Với instance method, bỏ static hoặc đặt modifiers như public ở trước **<T>**:

~~~java
public <E> E choose(E first, E second) {
    return first;
}
~~~

**<E>** mới là danh sách tham số kiểu. **E** kế tiếp là kiểu trả về; **E first** và **E second** là các tham số giá trị có kiểu E.

Đây là lỗi rất thường gặp:

~~~java
// Mã minh họa sai cú pháp/ngữ cảnh:
static T identity(T value) {
    return value;
}
~~~

Nếu T chưa được khai báo ở phạm vi hợp lệ thì compiler không thể tìm thấy kiểu T. Chỉnh thành **static <T> T identity(T value)**. Phần khai báo **<T>** cần có ngay cả khi compiler có thể suy luận T khi gọi phương thức.

Với nhiều tham số kiểu, mỗi tên có thể gắn vào kiểu trả về hoặc tham số khác nhau:

~~~java
static <K, V> java.util.Map<K, V> singletonMap(K key, V value) {
    return java.util.Map.of(key, value);
}
~~~

**K** và **V** được khai báo cho phương thức; Map<K, V> là kiểu trả về dùng hai tên đã khai báo; K key và V value là hai tham số giá trị. Khi gọi **singletonMap("ready", true)**, compiler thường suy luận K là String và V là Boolean.

Đối số kiểu của method có thể **được suy luận** hoặc **ghi tường minh**:

~~~java
// Giả sử đang ở cùng class với phương thức static identity:
String a = identity("abc");
String b = GenericMethods.<String>identity("abc");
~~~

Lời gọi có **<String>** sau tên class/dấu chấm truyền *method type argument* String; nó không phải khai báo type parameter mới. Để lời gọi ở ví dụ này biên dịch, phương thức identity phải được khai báo là static trong class tên GenericMethods và có thể truy cập tại vị trí gọi.

## 7. Phạm vi khai báo: tham số kiểu của class và của method

Quan sát một class vừa có type parameter ở cấp class, vừa có generic method riêng:

~~~java
class Holder<T> {
    private final T stored;

    Holder(T stored) {
        this.stored = stored;
    }

    T read() {
        return stored;
    }

    <U> U echo(U input) {
        return input;
    }

    static <R> R same(R input) {
        return input;
    }
}
~~~

Có ba type variables giữ ba vai trò:

- **T** được khai báo ở **Holder<T>**; dùng trong instance field stored, constructor và instance method read().
- **U** được khai báo bởi **<U> U echo(...)**; U chỉ có phạm vi tại method echo và có thể khác hoàn toàn T.
- **R** được khai báo bởi **static <R> R same(...)**; đây là type parameter của static method, không phải T của Holder.

Không thể dùng trực tiếp T của class generic trong **static field** hoặc **static method** như sau:

~~~java
class Wrong<T> {
    // static T cached;          // Compile-time error
    // static T reuse(T input) { // Compile-time error
    //     return input;
    // }
}
~~~

Lý do: thành viên static gắn với class Wrong chung, trong khi các cách sử dụng Wrong<String> và Wrong<Integer> đều là cùng một class ở runtime. Nếu cần generic static method, tự khai báo **static <U> U reuse(U input)**. Nếu cần sử dụng T của class, dùng instance method.

Một class generic chứa instance method **T read()** không có nghĩa read là *generic method* tự khai báo tham số mới: read chỉ **sử dụng** T đã được class khai báo. Generic method đích thực có danh sách type parameters của **chính phương thức**.

Phạm vi tên cũng giải thích tại sao không nên đặt trùng tên gây rối. Ví dụ phương thức **<T> T convert(T x)** bên trong class **Wrapper<T>** có một T riêng, có thể che tên T của class trong phạm vi phương thức. Phần mềm vẫn có thể biên dịch tùy cách dùng, nhưng nên dùng tên khác như U để tránh hiểu lầm về quan hệ kiểu.

## 8. Generic constructor với một hoặc nhiều tham số kiểu

Constructor có thể khai báo một danh sách type parameters **riêng**, ngay trước tên constructor, kể cả khi class không generic:

~~~java
class Receipt {
    private final String description;

    <T> Receipt(T source) {
        description = String.valueOf(source);
    }

    String description() {
        return description;
    }
}
~~~

Ở đây **<T> Receipt(T source)** là generic constructor của một class không generic. T chỉ được dùng trong constructor; **Receipt** không trở thành parameterized type chỉ vì constructor generic. Cách dùng **new Receipt(123)** hoặc **new Receipt("invoice")** đều hợp lệ.

Một class generic vẫn có thể có generic constructor với một type parameter **khác** của class:

~~~java
class Parsed<T> {
    private final T value;

    <S> Parsed(S raw, java.util.function.Function<S, T> parser) {
        value = parser.apply(raw);
    }

    T get() {
        return value;
    }
}
~~~

Ví dụ **Parsed<Integer> n = new Parsed<>("12", Integer::parseInt)**: **T** của class là Integer; **S** của constructor được suy luận thành String từ đối số raw và method reference. Đây là minh họa đặt trong một phương thức hợp lệ, với class Parsed hiện diện trong phạm vi.

Với nhiều tham số của constructor:

~~~java
class Summary {
    private final String text;

    <A, B> Summary(A left, B right) {
        text = left + " | " + right;
    }

    String text() {
        return text;
    }
}
~~~

Trong **<A, B> Summary(A left, B right)**, A và B là type parameters *của constructor*, độc lập với việc Summary không phải generic class. Phương thức text() trả về String cố định.

Khi cần giữ quan hệ kiểu trong **toàn bộ trạng thái và API** của đối tượng, khai báo tham số ở cấp class; khi chỉ cần linh hoạt đầu vào của **một lần tạo đối tượng**, generic constructor có thể phù hợp. Việc đặt type parameter ở đâu quyết định phạm vi sử dụng được tên đó.

## 9. Ràng buộc từng tham số kiểu bằng extends

Theo mặc định, một type parameter không khai báo bound tường minh có cận trên là **Object**. Ta có thể giới hạn tập đối số kiểu bằng **extends**.

Một tham số có cận:

~~~java
class Metric<T extends Number> {
    private final T number;

    Metric(T number) {
        this.number = number;
    }

    double asDouble() {
        return number.doubleValue();
    }
}
~~~

**T extends Number** yêu cầu T là Number hoặc subtype của Number. Do vậy Metric<Integer> và Metric<Double> hợp lệ, Metric<String> không hợp lệ. Compiler biết T có các phương thức của Number, nên gọi **number.doubleValue()** được kiểm tra an toàn.

Hai tham số có cận riêng:

~~~java
class IndexedValue<K extends Comparable<K>, V extends Number> {
    private final K key;
    private final V value;

    IndexedValue(K key, V value) {
        this.key = key;
        this.value = value;
    }

    K key() { return key; }
    V value() { return value; }
}
~~~

**K extends Comparable<K>** là một cận interface cho K; **V extends Number** là cận class cho V. Mỗi vị trí phải thỏa bound tương ứng, không dùng cùng một bound cho cả hai tham số.

Một tham số có **nhiều cận trên**:

~~~java
static <T extends Number & Comparable<T>> T maximum(T a, T b) {
    return a.compareTo(b) >= 0 ? a : b;
}
~~~

Cú pháp dùng **&** để nối các cận của *cùng một T*, còn dùng **dấu phẩy** để phân tách *những tham số kiểu khác nhau*. Đây là sự khác biệt cần nhớ:

| Cú pháp | Ý nghĩa |
| --- | --- |
| <A, B> | Khai báo hai tham số kiểu độc lập A và B |
| <T extends Number> | Một tham số T có cận Number |
| <K extends Comparable<K>, V> | Hai tham số; chỉ K có cận tường minh |
| <T extends Number & Comparable<T>> | Một T phải thỏa cả Number và Comparable<T> |
| <A extends Number, B extends Number> | Hai tham số, mỗi tham số có cận Number |

Khi có cận class và interface, cận class nếu hiện diện phải đứng trước các cận interface; không thể khai báo hai class bounds khác nhau. Dùng **extends** cho cả class bound lẫn interface bound, không viết **<T implements Comparable<T>>**. Không được khai báo lower bound bằng **<T super Number>**; **super** xuất hiện trong *wildcard type argument*, ví dụ List<? super Integer>, chứ không phải khai báo type parameter.

### Quan hệ giữa nhiều tham số kiểu

Một tham số có thể tham chiếu đến tham số khác trong bound. Ví dụ:

~~~java
class Registry<K, V extends java.io.Serializable> {
    // K không có cận tường minh; V phải thực thi Serializable.
}

class DirectedPair<A, B extends A> {
    private final A source;
    private final B detail;

    DirectedPair(A source, B detail) {
        this.source = source;
        this.detail = detail;
    }
}
~~~

Ở DirectedPair, B phải là A hoặc subtype của A. Bound như vậy thể hiện quan hệ kiểu mà compiler kiểm tra; nếu không có ràng buộc thật sự cần thiết, dùng <A, B> đơn giản sẽ rõ ràng hơn.

## 10. Ví dụ chạy hoàn chỉnh: một, hai và nhiều type parameters

Lưu thành tệp **GenericDeclarationsDemo.java**. Đây là một chương trình độc lập trên Java 17+:

~~~java
public class GenericDeclarationsDemo {
    static final class Box<T> {
        private final T value;

        Box(T value) {
            this.value = value;
        }

        T get() {
            return value;
        }
    }

    static final class Pair<K, V> {
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

    @FunctionalInterface
    interface Converter<I, O> {
        O convert(I input);
    }

    static <T> T first(T left, T right) {
        return left;
    }

    static <A, B> Pair<B, A> swap(Pair<A, B> original) {
        return new Pair<>(original.value(), original.key());
    }

    public static void main(String[] args) {
        Box<String> language = new Box<>("Java");
        Pair<String, Integer> release = new Pair<>("version", 21);

        Pair<Integer, String> swapped = swap(release);
        Converter<String, Integer> parser = Integer::parseInt;

        System.out.println(language.get());
        System.out.println(release.key() + ": " + release.value());
        System.out.println(swapped.key() + " -> " + swapped.value());
        System.out.println(first("left", "right"));
        System.out.println(parser.convert("17") + 4);
    }
}
~~~

Biên dịch và chạy ở thư mục chứa file:

~~~bash
javac -encoding UTF-8 GenericDeclarationsDemo.java
java GenericDeclarationsDemo
~~~

Stdout chính xác:

~~~text
Java
version: 21
21 -> version
left
21
~~~

Các điểm cần quan sát:

1. **Box<T>** khai báo **một** tham số kiểu. Trong Box<String>, String là đối số kiểu gắn cho T.
2. **Pair<K, V>** khai báo **hai** tham số kiểu. Pair<String, Integer> thiết lập K = String, V = Integer.
3. **Converter<I, O>** là interface với hai tham số kiểu khác vai trò. Converter<String, Integer> nhận chuỗi và trả Integer.
4. **static <T> T first(...)** tự khai báo T; không phụ thuộc T của Box.
5. **static <A, B> Pair<B, A> swap(Pair<A, B> original)** khai báo A, B để diễn tả quan hệ giữa *kiểu đầu vào* và *kiểu đầu ra*. Trong lời gọi swap(release), compiler suy luận A = String, B = Integer; kiểu trả về là Pair<Integer, String>.
6. Toán tử kim cương **new Pair<>(...)** nhờ ngữ cảnh constructor/kiểu đích mà không cần lặp danh sách đối số kiểu.
7. **Integer::parseInt** được chọn làm cách triển khai Converter<String, Integer>; expression **parser.convert("17") + 4** dùng phép unboxing từ Integer ra int để cộng 4.

Chú ý: trường hợp **Pair<String, Integer>** không tạo ra một class mới ở runtime cho mỗi tổ hợp kiểu; generics kiểm tra an toàn kiểu chủ yếu lúc biên dịch.

## 11. Ví dụ chạy hoàn chỉnh: giới hạn nhiều tham số

Lưu thành **BoundedParametersDemo.java**:

~~~java
public class BoundedParametersDemo {
    static final class NumberPair<A extends Number, B extends Number> {
        private final A left;
        private final B right;

        NumberPair(A left, B right) {
            this.left = left;
            this.right = right;
        }

        double sum() {
            return left.doubleValue() + right.doubleValue();
        }
    }

    static <T extends Number & Comparable<T>> T larger(T a, T b) {
        return a.compareTo(b) >= 0 ? a : b;
    }

    public static void main(String[] args) {
        NumberPair<Integer, Double> pair = new NumberPair<>(3, 2.5);
        Integer best = larger(7, 12);

        System.out.println(pair.sum());
        System.out.println(best);
    }
}
~~~

Biên dịch/chạy:

~~~bash
javac -encoding UTF-8 BoundedParametersDemo.java
java BoundedParametersDemo
~~~

Stdout chính xác:

~~~text
5.5
12
~~~

Tại **NumberPair<A extends Number, B extends Number>**, hai type parameters có hai bound được khai báo riêng. Integer và Double đều là subtype của Number nên cách sử dụng **NumberPair<Integer, Double>** hợp lệ.

Trong **<T extends Number & Comparable<T>>**, chỉ có **một** T nhưng nó thỏa hai cận. Tại lời gọi larger(7, 12), hai giá trị int được boxing thành Integer; kiểu được suy luận là Integer, vốn vừa là Number vừa hiện thực Comparable<Integer>. Vì thế lời gọi compareTo hợp lệ.

## 12. Type parameter khác type argument và wildcard ở đâu?

Khi đọc dấu ngoặc góc, luôn xét **vị trí xuất hiện**:

~~~java
class Repository<ID, E> {                  // Khai báo type parameters
    java.util.List<E> items;                // E là type argument của List

    void save(E entity) {}                  // E là kiểu tham số giá trị
}

// Trong thân phương thức hoặc khối lệnh:
// Repository<Long, String> repo;             // Long/String là type arguments
// java.util.List<?> unknown;                // ? là wildcard type argument
~~~

- **<ID, E>** ngay sau Repository là danh sách **type parameters** được khai báo.
- **List<E>** là một **parameterized type** sử dụng E đã khai báo. Tại vị trí List<E>, E giữ vai trò *type argument* cho List.
- **Repository<Long, String>** là một parameterized type khác: Long và String là hai type arguments thay cho ID và E.
- **List<?>** biểu thị danh sách có kiểu phần tử chưa biết. Dấu **?** không khai báo tên mới, không thể đem **?** dùng như T và không thể khai báo **class Repository<?>**.

**Tên tham số kiểu** có thể dùng như kiểu trong field, parameter, return type hoặc các generic type lồng nhau trong phạm vi hợp lệ. **Đối số kiểu** cho biết cách một generic type cụ thể được sử dụng.

Một ví dụ đối chiếu thêm:

~~~java
static <T> java.util.List<T> singleton(T value) {
    return java.util.List.of(value);
}
~~~

Trong chữ ký trên, **<T>** là khai báo; **List<T>** là kiểu tham số hóa mà đối số là type variable T; **T value** dùng T làm kiểu của tham số giá trị. Việc T xuất hiện nhiều lần chính là cách biểu diễn rằng kiểu phần tử đầu ra phải liên hệ với kiểu của giá trị đầu vào.

## 13. Tên và thứ tự tham số kiểu: ghi nhớ để đọc cú pháp

Tên tham số kiểu là **quy ước giúp đọc vai trò**, không phải từ khóa của Java. Trong một danh sách `<...>`, mỗi tên được khai báo một lần; ở nơi sử dụng, các type arguments được gán **theo vị trí**. Ví dụ `Pair<String, Integer>` khác `Pair<Integer, String>` dù cùng sử dụng hai kiểu, và `Pair<String, String>` vẫn hợp lệ.

| Tên thường gặp | Vai trò gợi nhớ |
| --- | --- |
| `T` | Kiểu tổng quát |
| `E` | Phần tử của collection |
| `K`, `V` | Khóa và giá trị |
| `I`, `O` hoặc `R` | Đầu vào, đầu ra hoặc kết quả |
| `U`, `S` | Một kiểu khác với `T` khi cần phân biệt |

Tên `K`, `V` không tự áp đặt bound hay bắt hai kiểu phải khác nhau. Dùng **cùng một biến kiểu** khi cần ràng buộc những vị trí phải có cùng quan hệ kiểu; dùng nhiều tên khi các vị trí thực sự độc lập. Bài về quy ước tên phân tích chi tiết việc chọn ký hiệu ngắn hay tên mô tả, phạm vi và Javadoc; ở đây chỉ cần nắm cú pháp khai báo và thứ tự type arguments.

## 14. Những lỗi biên dịch thường gặp

### Quên dấu phẩy hoặc khai báo trùng tên

~~~java
// Ví dụ sai, chỉ minh họa:
class Bad<A B> {}       // Thiếu dấu phẩy
class Bad2<T, T> {}     // Khai báo trùng tên T
class Bad3<K, V,> {}    // Dấu phẩy thừa
~~~

Sửa thành **class Good<A, B> {}** hoặc những danh sách tên duy nhất khác.

### Viết tham số kiểu sai chỗ trong generic method

~~~java
// Sai: compiler chưa có khai báo T:
class Broken {
    // static T copy(T value) { return value; }
}

// Đúng:
class Correct {
    static <T> T copy(T value) {
        return value;
    }
}
~~~

Dấu **<T>** của method phải nằm trước kiểu trả về.

### Cho rằng type parameters là các giá trị runtime

~~~java
class Example<T> {
    // T = String;       // Sai: T không phải biến giá trị
    // T item = new T(); // Sai: không thể new trực tiếp một type variable
}
~~~

Type variable mô tả kiểu tĩnh, không giữ đối tượng Class<T> tại runtime. Khi cần tạo T, có thể truyền một Supplier<T> hoặc factory phù hợp; khi cần biết Class<T>, phải có tham chiếu Class<T> từ nguồn khác.

### Dùng raw type thay cho khai báo đầy đủ

~~~java
java.util.List<String> safe = new java.util.ArrayList<>();
java.util.List raw = safe; // Raw type: mất thông tin type argument
~~~

Dùng **List<String>** khi biết kiểu phần tử hoặc **List<?>** khi chưa biết. Raw type có thể dẫn đến unchecked operation và lỗi cast ở runtime.

### Truyền sai số lượng type arguments

~~~java
class Pair<A, B> {}

// Sai: Pair<String> chỉ cung cấp một đối số cho hai tham số.
// Đúng: Pair<String, Integer> hoặc Pair<String, String>.
~~~

Số đối số kiểu thông thường phải tương ứng số tham số được khai báo. **new Pair<>()** là cú pháp suy luận đối số kiểu ở lời gọi constructor; nó không biến Pair thành generic class chỉ có một tham số.

### Dùng primitive trực tiếp

~~~java
// List<int> bad; // Sai: int là primitive type
java.util.List<Integer> good = java.util.List.of(1, 2);
~~~

Generic type arguments cho kiểu tham chiếu; primitive thường dùng wrapper tương ứng (int → Integer, long → Long, boolean → Boolean). Mảng của primitive như **int[]** là reference type và có thể làm đối số kiểu, ví dụ List<int[]>.

### Nhầm extends và implements trong cận

~~~java
// Sai: class Bad<T implements Runnable> {}
// Đúng:
class Task<T extends Runnable> {
    private final T action;

    Task(T action) {
        this.action = action;
    }

    void run() {
        action.run();
    }
}
~~~

Dù Runnable là interface, generic bound vẫn dùng từ khóa **extends**.

### Cho rằng tham số kiểu hỗ trợ phép toán bất kỳ

~~~java
class Numbers<T extends Number> {
    T add(T a, T b) {
        // return a + b; // Sai: không có toán tử + tổng quát cho T
        throw new UnsupportedOperationException("Chưa định nghĩa phép cộng cho T");
    }
}
~~~

Cận **Number** bảo đảm các phương thức như doubleValue(), không làm toán tử **+** hoạt động trực tiếp trên type variable T. Nếu bài toán cần số học, phải lựa chọn phép chuyển đổi hoặc giao diện phép toán phù hợp.

### Nhầm thứ tự hai kiểu và kỳ vọng compiler tự đảo

~~~java
// Giả sử class Pair<K,V> và các accessor đã có:
Pair<String, Integer> source = new Pair<>("x", 1);
// Pair<Integer, String> wrong = source; // Sai: khác parameterized type.
~~~

Kiểu Pair<String, Integer> không tự động trở thành Pair<Integer, String> bởi tên K/V linh hoạt. Muốn đảo hai vị trí, cần tạo một giá trị mới với hai type arguments ở thứ tự tương ứng.

## 15. Kiểm tra nhanh kiến thức

**Câu 1:** Phần nào khai báo hai type parameters trong **class Pair<K, V>**? **Pair<String, Integer>** giữ vai trò gì?

**Đáp án:** **<K, V>** là danh sách khai báo hai tham số kiểu. Pair<String, Integer> là parameterized type, với String làm type argument cho K và Integer cho V.

**Câu 2:** Vì sao **class Pair<T, T>** không hợp lệ, nhưng **Pair<String, String>** lại hợp lệ?

**Đáp án:** Không được khai báo hai tham số kiểu trùng tên trong cùng một danh sách; hai vị trí type arguments của cách sử dụng thì có thể cùng là String.

**Câu 3:** Khác nhau giữa **class Holder<T> { T get() {...} }** và **static <T> T identity(T value)** là gì?

**Đáp án:** T của get() thuộc phạm vi class Holder<T>; method get không khai báo type parameter mới. Ngược lại identity là generic method tự khai báo T nên dùng được cả trong class không generic.

**Câu 4:** Trong **<T extends Number & Comparable<T>>**, có bao nhiêu tham số kiểu và bao nhiêu bound?

**Đáp án:** Một tham số kiểu T, có hai cận Number và Comparable<T>. Dấu **&** nối các cận, khác dấu phẩy giữa nhiều tham số kiểu.

**Câu 5:** Gọi phương thức **static <A, B> Pair<B, A> swap(Pair<A, B> input)** với Pair<String, Integer> thì kiểu kết quả là gì?

**Đáp án:** Pair<Integer, String>. Compiler suy luận A = String, B = Integer từ input, sau đó thay vị trí trong kiểu trả về Pair<B, A>.

**Câu 6:** **Converter<I, O>** nhận String và trả Integer thì khai báo biến ở vị trí sử dụng thế nào?

**Đáp án:** **Converter<String, Integer>**. I tương ứng String và O tương ứng Integer, theo đúng thứ tự khai báo.

## 16. Checklist trước khi hoàn thành một khai báo generic

Trước khi viết hoặc review một khai báo có dấu ngoặc góc, hãy tự hỏi:

1. Các kiểu có thực sự cần tùy biến không? Một T có đủ hay các vai trò K, V hoặc I, O cần độc lập?
2. Danh sách **<...>** đã đặt đúng vị trí theo class, interface, record, method hay constructor chưa?
3. Tên tham số có duy nhất trong danh sách và dễ hiểu không?
4. Type variable có được dùng trong phạm vi hợp lệ và thể hiện đúng quan hệ kiểu của các field, tham số, kết quả không?
5. Các bounds bằng extends đã đúng thứ tự và có thực sự cần không?
6. Tại nơi sử dụng, type arguments có đủ số lượng, đúng thứ tự và thỏa bound không?
7. Cú pháp dùng type argument, diamond, wildcard hay raw type đã được phân biệt với cú pháp **khai báo** chưa?

Điểm cốt lõi của cú pháp là **<T>** giới thiệu một tên kiểu dùng lại ở nhiều vị trí; **<K, V>** giới thiệu hai tên kiểu độc lập. Chính phạm vi và cách tái sử dụng các tên này quyết định compiler kiểm tra mối quan hệ giữa các giá trị trong chương trình.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="generics-syntax-002">Quy ước tên T, E, K, V và cách đặt tên có nghĩa</a>

<details>
<summary>Xem chi tiết</summary>

# Quy ước tên T, E, K, V và cách đặt tên có nghĩa trong Java Generics

Trong Java Generics, `T`, `E`, `K`, `V` là **tên tham số kiểu (type parameters)**, không phải từ khóa của Java và không phải tên những kiểu có sẵn. Chúng là các tên thay thế cho kiểu mà người viết định nghĩa generic class, interface hoặc method chưa biết trước. Đặt tên rõ ràng giúp người đọc nhận ra **vai trò của từng kiểu** ngay tại chữ ký API.

Bài học này tập trung vào cách đọc, khai báo và chọn tên tham số kiểu. Cách đặt tên không đổi cơ chế hoạt động của generics, nhưng ảnh hưởng mạnh đến khả năng hiểu và sử dụng API.

## 1. Phân biệt tên tham số kiểu với đối số kiểu

Xét lớp và cách dùng sau:

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

// Khi dùng trong ngữ cảnh có thể khai báo biến:
Box<String> textBox = new Box<>("Hello");
```

- `T` trong `class Box<T>` là **tham số kiểu**: tên được khai báo bởi tác giả lớp, có phạm vi sử dụng trong các phần hợp lệ của định nghĩa lớp.
- `String` trong `Box<String>` là **đối số kiểu (type argument)**: kiểu cụ thể do người dùng lớp cung cấp tại điểm sử dụng.
- Hai chỗ `T` tại trường `value` và phương thức `get()` cùng chỉ một kiểu được chọn cho mỗi cách sử dụng `Box<T>`. Vì thế `Box<String>.get()` có kiểu kết quả `String` tại thời điểm biên dịch.
- Dấu `<>` trong `new Box<>("Hello")` là **diamond operator**: trình biên dịch suy luận đối số kiểu phù hợp từ ngữ cảnh. Nó không phải tên tham số kiểu.

`T` có thể đổi thành `ELEMENT` hoặc một định danh hợp lệ khác mà không thay đổi thuật toán:

```java
class Box<VALUE> {
    private final VALUE value;

    Box(VALUE value) {
        this.value = value;
    }

    VALUE get() {
        return value;
    }
}
```

Hai phiên bản `Box` trên là **hai cách viết thay thế**, không đặt cả hai cùng tên trong cùng một tệp/package để biên dịch. Sự khác biệt nằm ở tên dùng cho người đọc; Java vẫn kiểm tra quan hệ kiểu theo cùng nguyên tắc.

## 2. Ý nghĩa quy ước của T, E, K, V

| Tên | Gợi nghĩa thường gặp | Ngữ cảnh phù hợp | Cách đọc |
|---|---|---|---|
| `T` | **Type** – một kiểu bất kỳ | `Box<T>`, `Optional<T>`, `Comparable<T>` | “kiểu T” |
| `E` | **Element** – kiểu phần tử | `List<E>`, `Set<E>`, `Queue<E>` | “phần tử E” |
| `K` | **Key** – kiểu khóa | `Map<K, V>`, cấu trúc tìm kiếm theo khóa | “khóa K” |
| `V` | **Value** – kiểu giá trị | `Map<K, V>`, giá trị ghép với khóa | “giá trị V” |

Đây là **quy ước đọc hiểu**, không phải luật ngữ pháp hoặc ràng buộc của trình biên dịch. Java không yêu cầu tên `E` chỉ xuất hiện trong collection, `K` chỉ có trong map, hay `V` chỉ chứa giá trị được ghi. Ví dụ `class Container<E>` và `class Container<T>` đều hợp lệ nếu dùng nhất quán. Tuy nhiên, đổi `K` thành `T` và `V` thành `U` ở một map sẽ khiến vai trò khóa/giá trị khó nhìn ra.

Các dạng quen thuộc trong thư viện Java:

```java
List<String> names;          // List<E>: E = String
Set<Integer> uniqueNumbers;  // Set<E>: E = Integer
Map<String, Integer> ages;   // Map<K, V>: K = String, V = Integer
Optional<Double> price;      // Optional<T>: T = Double
```

Đây là những **khai báo biến minh họa**; để chạy riêng cần đặt trong lớp/phương thức và import các kiểu tương ứng. Tên `names`, `ages` là tên biến chứa đối tượng; `E`, `K`, `V` là tên kiểu trong **định nghĩa** của collection.

Một hiểu nhầm phổ biến là `E` viết tắt của *Exception*. Với các collection như `List<E>`, `E` có nghĩa là *Element*. Nếu API đang biểu đạt kiểu ngoại lệ, hãy cân nhắc tên giúp tránh nhầm lẫn với phần tử.

## 3. Những ký hiệu khác và cách mở rộng nhất quán

Bên cạnh bộ bốn cơ bản, một số tên thường thấy:

| Tên | Gợi nghĩa theo ngữ cảnh | Ví dụ |
|---|---|---|
| `S`, `U` | Kiểu thứ hai/thứ ba khi `T` đã được dùng | `<T, U>` cho phép hai kiểu độc lập |
| `R` | **Result/Return** – kiểu kết quả | `Function<T, R>` nhận `T`, trả `R` |
| `L`, `R` | **Left/Right** – thành phần trái/phải | `Pair<L, R>` |
| `N` | **Number** – kiểu số, khi thích hợp | `<N extends Number>` |
| `X` | Một kiểu đặc biệt như kiểu lỗi, tùy API | `<X extends Exception>` trong một số thiết kế |

`R` có thể là *Result* trong `Function<T, R>` nhưng là *Right* trong `Pair<L, R>`. **Ý nghĩa của ký hiệu đến từ hợp đồng API**, không từ một “từ điển bắt buộc” của Java.

Ví dụ đọc `Function<T, R>`: một hàm nhận đầu vào thuộc kiểu `T` và trả đầu ra thuộc kiểu `R`. Với `Function<String, Integer>`, `T = String`, `R = Integer`. `BiFunction<T, U, R>` dùng hai kiểu đầu vào `T` và `U`, kết quả `R`. Còn `Map<K, V>` diễn đạt cặp vai trò *khóa → giá trị*, nên ký hiệu `K` và `V` dễ hiểu hơn `T`, `U`.

Nếu nhiều tham số có **cùng vai trò**, có thể dùng `T`, `U`, `S` một cách nhất quán; nếu chúng có **vai trò khác nhau rõ rệt**, tên theo vai trò sẽ giúp người gọi hiểu nhanh hơn. Không có yêu cầu phải dùng một thứ tự mở rộng duy nhất cho tất cả thư viện.

## 4. Ví dụ Java 17 chạy độc lập: tên thể hiện vai trò

Lưu toàn bộ chương trình sau thành `GenericNamingDemo.java`:

```java
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class GenericNamingDemo {
    static final class Box<T> {
        private final T value;

        Box(T value) {
            this.value = value;
        }

        T get() {
            return value;
        }
    }

    static final class Pair<L, R> {
        private final L left;
        private final R right;

        Pair(L left, R right) {
            this.left = left;
            this.right = right;
        }

        L left() {
            return left;
        }

        R right() {
            return right;
        }
    }

    @FunctionalInterface
    interface Converter<INPUT, OUTPUT> {
        OUTPUT convert(INPUT input);
    }

    static <E> E first(List<E> elements) {
        return elements.get(0);
    }

    static <T, R> List<R> transform(List<T> inputs, Function<T, R> mapper) {
        return inputs.stream().map(mapper).toList();
    }

    static <K, V> Map<K, V> oneEntry(K key, V value) {
        Map<K, V> result = new LinkedHashMap<>();
        result.put(key, value);
        return result;
    }

    public static void main(String[] args) {
        Box<String> box = new Box<>("Java");
        System.out.println("box: " + box.get());

        System.out.println("first: " + first(List.of(7, 8, 9)));

        List<Integer> lengths = transform(
            List.of("a", "java"),
            String::length
        );
        System.out.println("lengths: " + lengths);

        Map<String, Integer> ages = oneEntry("Ada", 36);
        System.out.println("map: " + ages);

        Pair<String, Integer> pair = new Pair<>("year", 2026);
        System.out.println("pair: " + pair.left() + "=" + pair.right());

        Converter<String, Integer> converter = String::length;
        System.out.println("converter: " + converter.convert("Generics"));
    }
}
```

Chạy:

```bash
javac GenericNamingDemo.java
java GenericNamingDemo
```

Kết quả chuẩn:

```text
box: Java
first: 7
lengths: [1, 4]
map: {Ada=36}
pair: year=2026
converter: 8
```

Phân tích ý nghĩa tên trong từng chữ ký:

1. `Box<T>`: lớp là hộp cho **một kiểu bất kỳ**. Tên `T` ngắn, đủ thông tin vì chỉ có một tham số kiểu và không có vai trò cạnh tranh.
2. `<E> E first(List<E> elements)`: `E` là **kiểu phần tử**. Truyền `List<Integer>` thì kết quả được suy luận là `Integer`. Phương thức giả định danh sách không rỗng; nếu rỗng, `get(0)` ném `IndexOutOfBoundsException`.
3. `<T, R> List<R> transform(List<T> inputs, Function<T, R> mapper)`: `T` là **kiểu đầu vào**, `R` là **kiểu kết quả**. Chuỗi `"java"` được chuyển thành số độ dài `4`; `T` và `R` có thể là hai kiểu khác nhau.
4. `<K, V> Map<K, V> oneEntry(K key, V value)`: `K` luôn nhất quán ở khóa và `V` ở giá trị. Hàm có thể được gọi với `K = String`, `V = Integer`. `LinkedHashMap` bảo toàn thứ tự chèn; kết quả chỉ có một mục nên hiển thị `{Ada=36}`.
5. `Pair<L, R>`: hai thành phần có vai trò **trái** và **phải**, dù không có ý nghĩa khóa–giá trị; ở đây `L, R` rõ hơn `K, V`.
6. `Converter<INPUT, OUTPUT>`: minh họa cách dùng **tên có nghĩa dài hơn** khi hai vai trò đầu vào và đầu ra cần đặc biệt dễ đọc. Cú pháp này hợp lệ; chữ `INPUT` và `OUTPUT` không có nghĩa đặc biệt với trình biên dịch. Trong API nhỏ, `Converter<T, R>` cũng hoàn toàn hợp lý.

Ví dụ trên dùng một `@FunctionalInterface` để minh họa kiểu đầu vào/đầu ra. `String::length` là method reference hợp lệ vì `convert(String)` trả `Integer` nhờ autoboxing từ `int`.

## 5. Khi nào chọn tên một chữ cái, khi nào tên đầy đủ?

**Ưu tiên một chữ cái viết hoa** khi vai trò phổ biến và nhìn là hiểu ngay. Các API kiểu `List<E>`, `Map<K, V>`, `Box<T>`, `Function<T, R>` thường dễ đọc hơn một danh sách tên dài mà không có ích thêm. Đây là phong cách rất phổ biến trong Java Standard Library, đặc biệt ở generic class và method nhỏ.

**Cân nhắc tên mô tả** khi một chữ cái khiến hợp đồng trở nên mơ hồ, nhất là API công khai có nhiều loại dữ liệu mang vai trò khác nhau. Ví dụ một repository có thể được diễn đạt như sau:

```java
interface Repository<ENTITY, ID> {
    ENTITY findById(ID id);
    void save(ENTITY entity);
}
```

Ở đây hai tham số có hai vai trò khác nhau: `ENTITY` là kiểu thực thể mà repository thao tác; `ID` là kiểu định danh để tra cứu. Tên `Repository<T, U>` hợp lệ nhưng chữ ký `U findById(T id)` sẽ khó hiểu và dễ viết nhầm vai trò hơn. Nếu nhóm ưu tiên kiểu tên `EntityT` và `IdT` thì cũng hợp lệ; chọn một quy ước nhất quán và tài liệu hóa API rõ ràng.

**Lưu ý về phong cách:** Java không bắt buộc type parameter phải viết hoa một chữ cái. Những tên như `INPUT`, `OUTPUT`, `ENTITY` là ví dụ hợp lệ để đặt tên có nghĩa; không phải quy ước chuẩn bắt buộc ở mọi codebase. Đừng kéo dài tên chỉ để “mô tả cho đủ”: `<VeryGenericElementType>` hiếm khi rõ hơn `<E>` ở `List`.

Cách chọn thực dụng:

- Nếu chỉ một kiểu bất kỳ: bắt đầu bằng `T`.
- Nếu đang mô tả phần tử một tập hợp: dùng `E`.
- Nếu là khóa và giá trị có vai trò bất đối xứng: dùng `K, V`.
- Nếu đầu vào và đầu ra cần giữ quan hệ nhưng khác vai trò: cân nhắc `T, R` hoặc tên diễn đạt rõ như `INPUT, OUTPUT`.
- Nếu hai mặt trái/phải của cặp không nhất thiết là key/value: dùng `L, R` hoặc tên theo domain.
- Nếu ký hiệu không đủ truyền tải quan hệ ở một API phức tạp: dùng tên dài hơn với quy ước nhất quán của dự án.

## 6. Tên phải nhất quán trong phạm vi khai báo

Một tham số kiểu **được khai báo** trong phần `<...>` và chỉ được dùng ở những vị trí thuộc phạm vi khai báo đó. Ví dụ:

```java
class Holder<T> {
    private final T value;

    Holder(T value) {
        this.value = value;
    }

    T read() {
        return value;
    }

    static <U> U echo(U input) {
        return input;
    }
}
```

Trong `Holder<T>`, các vị trí `T` ở trường, constructor và `read()` cùng nói về **một tham số kiểu của lớp**. Trong `static <U> U echo(U input)`, `U` là **tham số kiểu riêng của phương thức static**: tên `U` được khai báo ngay trước kiểu trả về và không phụ thuộc vào `T` của một instance `Holder<T>`.

Đoạn sau **không biên dịch** nếu đặt trong một generic class:

```java
static T invalid(T input) {
    return input;
}
```

Lý do: phương thức `static` không được sử dụng trực tiếp tham số kiểu của lớp, vì tham số đó gắn với các cách dùng instance của kiểu generic. Có thể sửa thành `static <T> T valid(T input)`, khai báo một tham số kiểu thuộc chính phương thức. Dù tên trùng `T`, đó là **tham số khác**, nên trong tài liệu giảng dạy nên cân nhắc tên `U` để tránh hiểu nhầm.

Với chữ ký `static <K, V> Map<K, V> oneEntry(K key, V value)`, cả hai tên được khai báo trước kiểu trả về, sau đó dùng nhất quán ở `Map<K, V>` và các tham số. Đổi `V` thành `R` ở một vị trí nhưng không cập nhật chỗ khai báo sẽ gây lỗi biên dịch (không tìm thấy ký hiệu kiểu).

Tên biến thông thường khác tên tham số kiểu. Ví dụ `<K, V> Map<K, V> oneEntry(K key, V value)` dùng `K` và `V` cho **kiểu**, còn `key` và `value` cho **biến dữ liệu**. Tên biến viết camelCase và tên tham số kiểu thường viết chữ hoa để dễ phân biệt.

## 7. Bound không đổi ý nghĩa đặt tên

Generic bound giúp giới hạn kiểu được chấp nhận. Tên thể hiện vai trò, còn `extends` quy định giới hạn:

```java
static <N extends Number> double doubled(N value) {
    return value.doubleValue() * 2;
}

static <T extends Comparable<T>> T larger(T a, T b) {
    return a.compareTo(b) >= 0 ? a : b;
}
```

Đây là hai **đoạn phương thức minh họa**, cần đặt vào trong một class trước khi biên dịch độc lập.

- `N` gợi **number**; bound `extends Number` mới là cơ chế thực sự cho phép `doubleValue()`. Đổi tên thành `T` mà giữ nguyên bound không làm phương thức mất tính an toàn kiểu.
- `T` trong `larger` biểu diễn một kiểu có khả năng so sánh với chính kiểu đó; cùng `T` xuất hiện tại bound, tham số và kết quả để giữ quan hệ.
- `extends` trong generic bound không có nghĩa `N` là một lớp con mới được tạo. Nó chấp nhận đối số kiểu thỏa ràng buộc. `Number` có các subtype như `Integer`, `Double`, không chấp nhận kiểu không phải subtype phù hợp.

Một số API dùng ràng buộc linh hoạt hơn như `<T extends Comparable<? super T>>` khi cần hỗ trợ kiểu so sánh được với một supertype. Cú pháp có thể phức tạp, nhưng tên `T` vẫn giữ vai trò: **kiểu của giá trị cần so sánh**. Không đổi tên sang `K` hoặc `E` nếu chúng không thật sự diễn đạt vai trò.

## 8. Các lỗi đặt tên phổ biến

**Lỗi 1: Lạm dụng `T, U, V` dù có quan hệ khóa–giá trị.** Ví dụ `interface Storage<T, U> { U get(T key); }` hợp lệ, nhưng `Storage<K, V>` rõ hơn cho cấu trúc tra cứu theo khóa. Có thể giữ tên ngắn mà vẫn có ý nghĩa.

**Lỗi 2: Dùng `E` cho cả phần tử lẫn ngoại lệ mà không phân biệt.** `E` trong `List<E>` là element; nếu API có cả kiểu kết quả và kiểu exception, hãy chọn tên không xung đột và giải thích rõ trong Javadoc. Đừng giả định tất cả ký hiệu đều có ý nghĩa cố định ở mọi ngữ cảnh.

**Lỗi 3: Nhầm ký hiệu kiểu với một lớp Java tên `T`.** `T` trong `class Box<T>` là tên được khai báo tại lớp generic, không phải class đã có trong package. Trong một vùng code, đặt tham số kiểu trùng tên với lớp domain có thể gây che khuất tên và làm người đọc khó hiểu; nên tránh.

**Lỗi 4: Viết thường `<t>` theo thói quen đặt tên biến.** Java có thể chấp nhận định danh kiểu như `t`, nhưng quy ước dùng chữ cái in hoa (`T`, `E`...) giúp phân biệt tham số kiểu với biến thường. Tên kiểu và tên biến tuân theo các quy ước đọc khác nhau.

**Lỗi 5: Cùng một ký hiệu nhưng dùng cho hai vai trò lẫn lộn.** Ví dụ `<T> Map<T, T> convert(T key, T value)` ép cả khóa lẫn giá trị thuộc cùng một kiểu dù thiết kế muốn hai kiểu độc lập. Nên dùng `<K, V> Map<K, V> convert(K key, V value)` khi cần cho phép khác kiểu. Đây là **lỗi thiết kế quan hệ kiểu**, không chỉ lỗi thẩm mỹ.

**Lỗi 6: Dùng tên dài làm mờ cấu trúc.** `<ElementTypeOfTheInputList>` có thể khiến method một dòng khó đọc. Với một generic method nhỏ, `<E> E first(List<E> elements)` rõ ràng hơn. Tên dài chỉ có ích khi diễn đạt một ý nghĩa mà tên ngắn không thể hiện được.

**Lỗi 7: Tưởng tên `K`, `V` tự giới hạn kiểu.** `<K, V>` không ngăn truyền `String`, `Integer`, hoặc cùng một kiểu cho cả hai. Chỉ type bounds (nếu có) và các chữ ký API mới giới hạn quan hệ kiểu. `K` không mặc nhiên phải hashable hay `V` phải mutable.

**Lỗi 8: Tưởng đổi tên `T` thành `R` sẽ thay đổi giá trị trả về.** Tên ký hiệu không tạo chuyển đổi kiểu. Một method `<T, R> R convert(T x)` chỉ biên dịch khi thân phương thức thật sự tạo/nhận được `R` phù hợp; không thể đơn giản `return x;` vì không có bảo đảm `T` là `R`.

**Lỗi 9: Quên viết Javadoc cho API generic công khai.** Nếu vai trò khó hiểu, chú thích `@param <T>` là nơi giải thích tham số kiểu. Ví dụ:

```java
/**
 * Lấy phần tử đầu tiên của danh sách không rỗng.
 *
 * @param <E> kiểu phần tử trong danh sách
 * @param items danh sách đầu vào
 * @return phần tử tại vị trí 0
 * @throws IndexOutOfBoundsException nếu danh sách rỗng
 */
static <E> E first(List<E> items) {
    return items.get(0);
}
```

Đoạn Javadoc minh họa cho phương thức có thể đặt trong class và cần import `java.util.List`. Khác với `@param items` mô tả biến phương thức, cú pháp **`@param <E>`** mô tả *tham số kiểu*.

## 9. Câu hỏi tự kiểm tra

1. Trong `Map<String, Long>`, `K` và `V` ở định nghĩa `Map<K, V>` ứng với gì? **Đáp án:** `K = String` cho khóa và `V = Long` cho giá trị.
2. `List<E>` có bắt buộc dùng chữ `E` không? **Không.** Đó là quy ước của Java Collections; tên khác vẫn hợp lệ nếu khai báo và dùng nhất quán.
3. Trong `<T, R> R transform(T input, Function<T, R> f)`, vì sao có hai tham số kiểu? **Đáp án:** `T` mô tả kiểu đầu vào, `R` mô tả kiểu đầu ra; chúng có thể khác nhau.
4. Trong `Pair<L, R>`, có nên mặc định giải thích `R` là Result không? **Không.** Với cặp trái–phải, `R` thường là Right; cần đọc hợp đồng API.
5. Tên `<INPUT, OUTPUT>` có hợp lệ không? **Có.** Compiler không yêu cầu đúng một chữ cái, nhưng nhóm dự án nên thống nhất phong cách và chỉ dùng tên dài khi cần rõ nghĩa.
6. Đổi `static <E> E first(List<E> xs)` thành `static <T> T first(List<T> xs)` có đổi hành vi không? **Không**, nếu chỉ thay tên tham số kiểu nhất quán; cả hai mô tả cùng quan hệ kiểu.
7. `<K, V>` có tự buộc `K` và `V` khác nhau không? **Không.** `Map<String, String>` vẫn hợp lệ.
8. `class Box<T>` có thể viết `static T current;` không? **Không.** Thành viên static không được tham chiếu trực tiếp tham số kiểu của lớp; khai báo static generic method với tham số kiểu riêng nếu cần.

Khi đọc hoặc viết generic API, luôn hỏi ba điều: **Ký hiệu này đại diện cho đối tượng nào hoặc vai trò nào? Nó được khai báo ở phạm vi nào? Các vị trí lặp lại của ký hiệu có đang mô tả đúng cùng một kiểu không?** Nếu trả lời rõ cả ba, cách đặt tên thường đủ tốt; nếu còn mơ hồ, hãy chọn ký hiệu phù hợp hơn hoặc diễn giải rõ bằng tên và Javadoc.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="generics-syntax-003">Truyền đối số kiểu và tạo đối tượng kiểu tham số hóa</a>

<details>
<summary>Xem chi tiết</summary>

# Truyền đối số kiểu và tạo đối tượng kiểu tham số hóa trong Java Generics

Khi sử dụng **Java Generics**, ta có thể viết một lớp hay interface dùng chung cho nhiều kiểu dữ liệu, sau đó **truyền đối số kiểu** (`type argument`) vào nơi sử dụng. Ví dụ `Box<String>` biểu thị một `Box` mang giá trị kiểu `String`, còn `Box<Integer>` biểu thị một `Box` mang giá trị kiểu `Integer`. Trình biên dịch dựa vào đối số kiểu để kiểm tra phép gán, lời gọi phương thức và kiểu dữ liệu được trả về.

Bài này hướng dẫn cú pháp truyền đối số kiểu, cách tạo đối tượng với hoặc không có **diamond operator** (`<>`), cách Java suy luận kiểu dựa trên ngữ cảnh và các lỗi thường gặp khi khởi tạo generic.

## 1. Phân biệt tham số kiểu và đối số kiểu

Xét khai báo lớp:

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

Ở đây:

- `Box` là tên lớp generic.
- `T` là **tham số kiểu** (`type parameter`), được khai báo bên trong `<T>`; nó là tên đại diện cho một kiểu tham chiếu mà nơi sử dụng sẽ chỉ định.
- `Box<String>` là một **kiểu được tham số hóa** (`parameterized type`), trong đó `String` là **đối số kiểu** (`type argument`) cụ thể truyền vào vị trí `T`.
- `Box<Integer>` là cách sử dụng khác của cùng khai báo `Box<T>`, với `T` tương ứng `Integer`.

Cú pháp tổng quát:

```java
class Container<T> { /* khai báo sử dụng T */ }

// Ví dụ đặt trong một phương thức:
Container<String> textContainer;    // Truyền đối số kiểu String
Container<Integer> numberContainer; // Truyền đối số kiểu Integer
```

Phần `<T>` xuất hiện **khi khai báo** generic; `<String>` hoặc `<Integer>` xuất hiện **khi sử dụng** generic. Đây là hai vai trò khác nhau, dù cùng dùng dấu ngoặc nhọn `<...>`.

Có thể khai báo nhiều tham số kiểu, phân biệt bằng dấu phẩy:

```java
class Pair<K, V> {
    private final K key;
    private final V value;

    Pair(K key, V value) {
        this.key = key;
        this.value = value;
    }

    K key() { return key; }
    V value() { return value; }
}
```

Khi dùng `Pair<String, Integer>`, tham số `K` được gắn với `String`, còn `V` với `Integer`. **Thứ tự đối số kiểu phải tương ứng thứ tự tham số kiểu** trong khai báo. Tên `T`, `K`, `V` là quy ước dễ đọc; trình biên dịch không yêu cầu dùng đúng các chữ cái này.

## 2. Cú pháp đầy đủ khi tạo đối tượng generic

Một đối tượng `Box<String>` có thể được tạo bằng cách nêu đối số kiểu trong cả khai báo biến lẫn biểu thức `new`:

```java
Box<String> title = new Box<String>("java");
```

Cách đọc:

1. `Box<String>` ở bên trái khai báo **kiểu của biến tham chiếu** `title`.
2. `new Box<String>("java")` ở bên phải gọi constructor của `Box` và chỉ định đối số kiểu `String` cho đối tượng được tạo.
3. `"java"` là giá trị constructor nhận; vì `T` được chỉ định là `String`, nó phù hợp với tham số constructor kiểu `T`.
4. `title.get()` có kiểu biểu thức `String` theo kiểm tra kiểu lúc biên dịch, nên gọi được `toUpperCase()` trực tiếp.

Đối số kiểu được truyền vào **kiểu generic**; nó không phải tham số giá trị của constructor. Ví dụ `new Box<String>("java")` có **đối số kiểu** `String` và **đối số constructor** là chuỗi `"java"`.

Nếu lớp định nghĩa constructor không nhận đối số, ta có thể dùng `new ClassName<Type>()`; nếu constructor yêu cầu tham số, phải cung cấp các đối số giá trị tương ứng. Generics không tự tạo constructor hay bỏ qua các ràng buộc constructor.

### Ví dụ Java 17 chạy đầy đủ

Lưu chương trình sau thành `TypeArgumentsDemo.java`:

```java
public class TypeArgumentsDemo {
    static final class Box<T> {
        private final T value;

        Box(T value) {
            this.value = value;
        }

        T get() {
            return value;
        }
    }

    static final class Pair<K, V> {
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

    public static void main(String[] args) {
        Box<String> title = new Box<String>("java");
        Box<Integer> quantity = new Box<Integer>(3);
        Pair<String, Integer> version = new Pair<String, Integer>("JDK", 17);

        System.out.println(title.get().toUpperCase());
        System.out.println(quantity.get() + 2);
        System.out.println(version.key() + " " + version.value());
    }
}
```

Biên dịch và chạy:

```bash
javac -encoding UTF-8 TypeArgumentsDemo.java
java TypeArgumentsDemo
```

Kết quả chuẩn (`stdout`):

```text
JAVA
5
JDK 17
```

Trong `Box<Integer>`, tham số kiểu dùng `Integer` (lớp bao của `int`). Java tự đóng hộp số `3` thành `Integer` khi truyền constructor và tự mở hộp khi thực hiện phép cộng trong `quantity.get() + 2`. Kiểu `Pair<String, Integer>` giúp trình biên dịch kiểm tra lần lượt hai đối số của constructor.

## 3. Dùng diamond operator `<>` để Java tự suy luận đối số kiểu

Kể từ Java 7, khi tạo đối tượng generic, ta thường có thể thay phần đối số kiểu phía sau `new` bằng **diamond** `<>`:

```java
Box<String> title = new Box<>("java");
Box<Integer> quantity = new Box<>(3);
Pair<String, Integer> version = new Pair<>("JDK", 17);
```

Ba biểu thức trên mang cùng ý định kiểu với cách viết tường minh trong ví dụ trước. Diamond yêu cầu trình biên dịch **suy luận** đối số kiểu dựa trên thông tin có sẵn, bao gồm kiểu đích (`target type`) và kiểu đối số constructor.

So sánh:

| Viết tường minh | Dùng diamond | Kết quả cần diễn đạt |
|---|---|---|
| `new Box<String>("java")` | `new Box<>("java")` | Khởi tạo `Box<String>` trong ngữ cảnh phù hợp |
| `new Box<Integer>(3)` | `new Box<>(3)` | Khởi tạo `Box<Integer>` trong ngữ cảnh phù hợp |
| `new Pair<String, Integer>("JDK", 17)` | `new Pair<>("JDK", 17)` | Khởi tạo `Pair<String, Integer>` trong ngữ cảnh phù hợp |

**Diamond chỉ viết `<>`, không phải `<T>`.** Trong `new Box<>()`, Java có trách nhiệm suy luận đối số kiểu; trong `new Box<String>()`, lập trình viên chỉ định `String` trực tiếp.

Diamond giảm lặp thông tin và thường được ưu tiên khi kết quả suy luận đúng với hợp đồng mong muốn. Có những ngữ cảnh thiếu thông tin hoặc đối số constructor phức tạp làm kiểu suy luận rộng hơn mong đợi; khi đó khai báo kiểu rõ ràng giúp kiểm soát tốt hơn.

## 4. Kiểu biến ở bên trái có thể quyết định kiểu suy luận ở bên phải

Xem khai báo collection:

```java
import java.util.ArrayList;
import java.util.List;

List<String> names = new ArrayList<>();
names.add("An");
String firstName = names.get(0);
```

Đây là **đoạn mã minh họa nằm trong thân một phương thức**; cần các import như trên. `List<String>` là kiểu được tham số hóa của **interface** `List`; `ArrayList` là lớp triển khai được khởi tạo. Trình biên dịch suy luận `String` cho `new ArrayList<>()` nhờ kiểu đích `List<String>`.

Lợi ích thực tế:

- API của biến `names` thể hiện danh sách chỉ nhận phần tử `String` qua các thao tác được kiểm tra kiểu.
- Khi gọi `names.add("An")`, giá trị hợp lệ vì là `String`.
- Khi gọi `names.get(0)`, kiểu biểu thức là `String`; không cần viết `(String)`.
- Nếu thêm `names.add(42)`, trình biên dịch sẽ báo lỗi vì `int` được đóng hộp thành `Integer`, không phù hợp tham số kiểu `String`.

Vì `List` là interface nên không thể viết `new List<String>()` trực tiếp; phải chọn một lớp triển khai, ví dụ `ArrayList`. Cú pháp tham số hóa của biến và cú pháp khởi tạo thuộc hai vị trí độc lập: biến có thể khai báo bằng interface, còn đối tượng được tạo từ lớp triển khai.

### Ví dụ với kiểu lồng nhau

```java
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Đặt bên trong một phương thức:
Map<String, List<Integer>> scores = new HashMap<>();
scores.put("Java", new ArrayList<>());
scores.get("Java").add(10);
```

Ở đây `Map<String, List<Integer>>` có hai đối số kiểu ở lớp ngoài: khóa `String` và giá trị `List<Integer>`. `List<Integer>` lại là một kiểu được tham số hóa. Dấu `>>` cuối khai báo đóng hai cặp ngoặc góc lồng nhau; trong Java hiện đại, không cần thêm khoảng trắng để phân tách chúng.

Đừng nhầm các loại ngoặc:

- `<String, Integer>`: đối số **kiểu**.
- `("Java", 17)`: đối số **giá trị** của constructor/phương thức.
- `<>`: diamond để **suy luận** đối số kiểu khi khởi tạo.

## 5. `var` kết hợp với diamond: nhận diện kiểu suy luận

`var` để compiler suy luận **kiểu tĩnh của biến cục bộ** từ initializer. Nếu initializer là một constructor generic rỗng với diamond và không có target type cụ thể, suy luận có thể cho kiểu phần tử `Object`:

```java
List<String> a = new ArrayList<>();     // Target type cho phép suy luận String.
var b = new ArrayList<>();              // b có kiểu ArrayList<Object>.
var c = new ArrayList<String>();        // c có kiểu ArrayList<String>.
```

Khác biệt quan trọng: `ArrayList<Object>` vẫn là **parameterized type**, không phải raw `ArrayList`. `var` không khiến biến trở thành kiểu động. Khi lời gọi cần phần tử String, ưu tiên viết `List<String> names = new ArrayList<>();` hoặc `var names = new ArrayList<String>();`. Phân tích sâu về diamond/var, cảnh báo raw và ví dụ heap pollution thuộc bài chuyên về toán tử diamond.

## 6. Truyền đối số kiểu cho phương thức generic

Đối số kiểu cũng có thể được truyền khi gọi **phương thức generic**. Trong khai báo phương thức, danh sách tham số kiểu `<T>` đứng **trước kiểu trả về**:

```java
static <T> T echo(T value) {
    return value;
}
```

Khi gọi, Java thường suy luận `T` từ đối số giá trị và kiểu đích:

```java
String result = echo("Java");
```

Cũng có thể chỉ định đối số kiểu **tường minh** bằng cú pháp `ClassName.<Type>method(...)`:

```java
String result = TypeMethodDemo.<String>echo("Java");
```

Trong lời gọi phương thức generic, `<String>` xuất hiện **ngay trước tên phương thức**, không phải sau `new`. Đây là cú pháp khác diamond của biểu thức khởi tạo constructor.

Ví dụ chạy đầy đủ, lưu thành `TypeMethodDemo.java`:

```java
public class TypeMethodDemo {
    static <T> T echo(T value) {
        return value;
    }

    public static void main(String[] args) {
        String inferred = echo("Java");
        String explicit = TypeMethodDemo.<String>echo("JDK");

        System.out.println(inferred);
        System.out.println(explicit);
    }
}
```

Lệnh:

```bash
javac -encoding UTF-8 TypeMethodDemo.java
java TypeMethodDemo
```

`stdout`:

```text
Java
JDK
```

Thông thường nên để trình biên dịch suy luận đối số kiểu cho phương thức khi ý định đã rõ. Cú pháp chỉ định tường minh hữu ích khi muốn làm rõ một kiểu cụ thể hoặc xử lý một tình huống suy luận khó.

**Một lưu ý quan trọng:** tham số kiểu của lớp và tham số kiểu của phương thức là hai khai báo riêng biệt. Một phương thức `static` không thể trực tiếp sử dụng `T` chỉ khai báo trên `class Box<T>`; nó phải tự khai báo tham số kiểu, ví dụ `static <U> U identity(U value)`.

## 7. Những điều kiện đối số kiểu phải đáp ứng

### 7.1. Đối số kiểu phải là kiểu tham chiếu

Java Generics không chấp nhận kiểu nguyên thủy (`primitive type`) làm đối số kiểu:

```java
Box<int> counter;       // Sai: int không phải kiểu tham chiếu
Box<Integer> counter2;  // Đúng: Integer là kiểu tham chiếu
```

Các cặp thường dùng gồm `int`/`Integer`, `long`/`Long`, `double`/`Double` và `boolean`/`Boolean`. Autoboxing/unboxing cho phép mã tiện dụng, nhưng không làm `Box<int>` trở nên hợp lệ.

### 7.2. Đối số kiểu phải phù hợp với giới hạn (`bound`) nếu có

Giả sử một lớp khai báo:

```java
class NumberBox<T extends Number> {
    private final T value;

    NumberBox(T value) {
        this.value = value;
    }

    T get() {
        return value;
    }
}
```

`T extends Number` yêu cầu đối số kiểu là `Number` hoặc kiểu con của `Number`. Bởi vậy:

```java
NumberBox<Integer> integers = new NumberBox<>(5); // Đúng
NumberBox<Double> decimals = new NumberBox<>(1.5); // Đúng
// NumberBox<String> text = new NumberBox<>("x"); // Sai: String không extends Number
```

Đây là **đoạn mã minh họa**, cần đặt trong thân phương thức cùng khai báo `NumberBox` ở trên. Kiểu được suy luận qua diamond vẫn phải đáp ứng giới hạn đã khai báo.

### 7.3. Generic mặc định bất biến (`invariant`)

Ngay cả khi `Integer` là kiểu con của `Number`, hai kiểu tham số hóa sau **không** có quan hệ kiểu con theo cùng chiều:

```java
// List<Number> numbers = new ArrayList<Integer>(); // Sai: không tương thích
```

Nếu lập trình viên cần làm việc với một nhóm kiểu có quan hệ cha–con, wildcard như `? extends Number` hoặc `? super Integer` có thể phù hợp, nhưng có quy tắc đọc/ghi riêng. Không nên thay `<Integer>` bằng `<Number>` hay giả định có thể gán tùy ý chỉ vì hai kiểu đối số liên quan kế thừa.

### 7.4. Không thể suy luận đúng một kiểu từ mọi ngữ cảnh

Diamond dùng dữ kiện của chương trình, không biết ý định nghiệp vụ. Nếu các dữ kiện chỉ cho phép suy luận `Object`, chương trình có thể hợp lệ nhưng mất ràng buộc mong muốn. Tốt nhất là viết kiểu đích đủ rõ ở ranh giới API và kiểm tra cảnh báo trình biên dịch.

## 8. Phân biệt kiểu tham số hóa với raw type khi tạo object

`Box<String> value = new Box<>("Java")` dùng generic type argument `String` và diamond suy luận kiểu; còn `Box raw = new Box("Java")` cố ý bỏ type argument để tương thích mã cũ. Raw type có thể khiến compiler phát `rawtypes` hoặc `unchecked` warnings và mất kiểm tra phần tử ở những thao tác tiếp theo. Đừng đánh đồng **raw type** với `Box<?>`: wildcard vẫn là cách biểu diễn an toàn kiểu rằng type argument chưa biết.

```java
Box<String> safe = new Box<>("Java");
Box<?> unknown = safe;
// Box<?> không cho phép tự đặt một giá trị String tùy ý
// nếu phương thức Box có setter nhận chính type parameter T.
```

Dù khai báo `Box<String>`, JVM thông thường không tạo riêng một runtime class `Box<String>`; đây là hiệu ứng **type erasure**, không phải lý do để dùng raw types. Chi tiết diamond vs raw, cảnh báo và cách chuyển dữ liệu legacy được học sâu ở bài chuyên tiếp theo.

## 9. Các lỗi cú pháp và thiết kế phổ biến

| Viết sai hoặc dễ gây hiểu nhầm | Vấn đề | Cách sửa |
|---|---|---|
| `new Box<T>("x")` trong mã không hề khai báo `T` | `T` không tự động đại diện cho bất kỳ kiểu nào trong mọi phạm vi. | Dùng `new Box<String>("x")` hoặc `new Box<>("x")` khi ngữ cảnh đủ rõ. |
| `Box<int> b` | Kiểu primitive không được làm đối số generic. | `Box<Integer> b`. |
| `Box<String> b = new Box<>(123)` | Đối số constructor không tương thích với kiểu đích `String`. | Truyền một `String` hoặc đổi hợp đồng thành kiểu thực sự cần. |
| `Box<String> b = new Box<Integer>(123)` | Hai kiểu tham số hóa không tương thích. | Dùng đối số kiểu thống nhất ở hai phía. |
| `List<String> x = new List<>()` | `List` là interface, không thể tạo trực tiếp. | `List<String> x = new ArrayList<>();`. |
| `var x = new ArrayList<>()` nhưng mong chỉ chứa chuỗi | Kiểu suy luận là `ArrayList<Object>`, cho phép nhiều loại phần tử. | Viết `var x = new ArrayList<String>()` hoặc `List<String> x = new ArrayList<>();`. |
| `Box b = new Box("x")` | Raw type bỏ kiểm tra tham số kiểu; có thể xuất hiện cảnh báo. | `Box<String> b = new Box<>("x");`. |
| Cố dùng `Box<String>` làm kiểu con của `Box<Object>` | Generic bất biến. | Thiết kế API đúng kiểu; cân nhắc wildcard nếu cần. |

Các dòng minh họa lỗi biên dịch trong bảng dùng để **nhận diện và sửa lỗi**, không phải mã nguồn để chạy nguyên trạng.

## 10. Quy trình áp dụng trong code thực tế

Khi cần tạo một đối tượng generic, hãy kiểm tra lần lượt:

1. **Xác định hợp đồng kiểu:** dữ liệu phải là `String`, `Integer`, một kiểu domain như `Order`, hay một cặp `K, V`?
2. **Đọc khai báo generic:** lớp/interface có bao nhiêu tham số kiểu, theo thứ tự nào và có giới hạn `extends` gì không?
3. **Đặt kiểu đích dễ đọc:** ví dụ `List<Order> orders` hoặc `Box<String> label`. Ưu tiên interface làm kiểu biến khi thiết kế API phù hợp.
4. **Khởi tạo bằng constructor đúng:** `new ArrayList<>()`, `new Box<>("Java")` hay `new Pair<>("key", 10)`. Diamond nên được chọn khi trình biên dịch có thể suy luận kiểu mong muốn.
5. **Tránh raw type và ép kiểu không được kiểm tra:** xem cảnh báo `-Xlint:rawtypes,unchecked` khi tích hợp mã cũ; đừng dùng raw type để né lỗi generic.
6. **Xem lại suy luận khi dùng `var`:** với constructor rỗng và diamond, viết rõ đối số kiểu phía sau `new` nếu cần kiểu hẹp hơn `Object`.

Một mẫu dễ đọc, thường gặp trong dự án Java:

```java
// Trong một phương thức, đã import List và ArrayList:
List<String> tags = new ArrayList<>();
tags.add("generics");

Box<String> label = new Box<>("Java");
String text = label.get();
```

Trong ví dụ cuối, `tags` dùng kiểu interface `List<String>` nhưng đối tượng là `ArrayList<String>`; `label` và đối tượng `Box` có cùng đối số kiểu `String`. Các kiểu được kiểm tra ngay khi biên dịch, giúp phát hiện sớm việc truyền sai dữ liệu.

## 11. Tự kiểm tra

**Câu 1.** Trong `Pair<String, Integer> pair = new Pair<>("A", 1);`, đâu là tham số kiểu, đâu là đối số kiểu, đâu là đối số constructor?

**Câu 2.** Tại sao `List<String> words = new ArrayList<>();` đúng nhưng `List<String> words = new List<>();` sai?

**Câu 3.** Hai biến dưới đây có cùng kiểu không?

```java
var a = new ArrayList<>();
var b = new ArrayList<String>();
```

**Câu 4.** Cách khai báo nào hợp lệ cho số nguyên: `Box<int>` hay `Box<Integer>`? Tại sao?

**Gợi ý đáp án:** (1) `K` và `V` là tham số kiểu ở khai báo `Pair<K,V>`; `String` và `Integer` là đối số kiểu; `"A"` và `1` là đối số constructor. (2) `List` là interface, `ArrayList` là lớp có thể khởi tạo. (3) Không; `a` thường là `ArrayList<Object>` còn `b` là `ArrayList<String>`. (4) `Box<Integer>` vì generic nhận kiểu tham chiếu, không nhận primitive `int`.

**Điểm cần nhớ:** Hãy khai báo đối số kiểu để diễn đạt rõ hợp đồng dữ liệu; dùng `new GenericClass<Type>(...)` khi muốn chỉ định tường minh và `new GenericClass<>(...)` khi suy luận kiểu đủ rõ. Khi dùng `var`, constructor rỗng, raw type hoặc generic có giới hạn, cần kiểm tra kỹ kiểu mà trình biên dịch thực sự xác định.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="generics-syntax-004">Toán tử diamond &lt;&gt; và khác biệt với raw type</a>

<details>
<summary>Xem chi tiết</summary>

# Toán tử diamond `<>` và khác biệt với raw type trong Java

## 1. Vấn đề cần giải quyết

Generics giúp trình biên dịch kiểm tra kiểu phần tử trong collection hoặc lớp tổng quát. Khi khởi tạo đối tượng generic, Java có hai cách viết có vẻ giống nhau nhưng **khác nhau về độ an toàn kiểu**:

```java
List<String> names = new ArrayList<>(); // Diamond: trình biên dịch suy luận String.
List<String> old   = new ArrayList();   // Raw type: bỏ tham số kiểu, cảnh báo unchecked.
```

Ở dòng thứ nhất, `<>` là **toán tử diamond**: danh sách được khởi tạo với tham số kiểu suy luận từ ngữ cảnh (`String`). Ở dòng thứ hai, `new ArrayList()` là **raw type**: constructor được gọi thông qua kiểu `ArrayList` không tham số hóa. Dòng đó vẫn có thể biên dịch để hỗ trợ tương thích mã Java cũ, nhưng mất một phần kiểm tra kiểu và sinh cảnh báo về phép chuyển đổi không kiểm tra được.

Mục tiêu sau bài học:

- Nhận diện rõ `List<String>`, `new ArrayList<String>()`, `new ArrayList<>()`, `new ArrayList()` và `List<?>`.
- Hiểu `<>` suy luận kiểu **khi biên dịch** từ ngữ cảnh như khai báo biến, tham số phương thức hoặc kiểu trả về.
- Nhìn ra lý do raw type tạo cảnh báo `rawtypes`, `unchecked` và nguy cơ **heap pollution / ClassCastException**.
- Biết dùng diamond trong code Java 17+, phân biệt trường hợp có target type với khai báo bằng `var`, và chuyển dần mã legacy sang API an toàn kiểu.

`<>` được đưa vào Java 7 để giảm việc lặp lại tham số kiểu ở biểu thức `new`. Diamond vẫn tuân theo toàn bộ quy tắc của generics, không biến Java thành ngôn ngữ có kiểu động.

## 2. Năm cách viết dễ nhầm lẫn

| Cú pháp | Ý nghĩa | Có kiểm tra kiểu generic? |
| --- | --- | --- |
| `List<String> a` | Biến có kiểu phần tử `String` | Có |
| `new ArrayList<String>()` | Khởi tạo và chỉ định tường minh `String` | Có |
| `new ArrayList<>()` | Khởi tạo, để trình biên dịch **suy luận** tham số kiểu | Có |
| `new ArrayList()` | Sử dụng **raw type**, bỏ hẳn tham số kiểu | Không đầy đủ; có thể sinh cảnh báo |
| `List<?> any` | List của một kiểu chưa biết, nhưng vẫn là **parameterized type** | Có; giới hạn thao tác ghi để bảo vệ an toàn kiểu |

Hai dòng đầu dưới đây tương đương về hợp đồng generic mà biến `names` nhìn thấy:

```java
// Các câu lệnh này có thể đặt trong một phương thức Java.
List<String> names1 = new ArrayList<String>();
List<String> names2 = new ArrayList<>();
names1.add("Lan");
names2.add("Minh");
// names2.add(42); // Lỗi biên dịch vì List<String> không nhận Integer.
```

Diamond **không phải** chuỗi ký hiệu bỏ tham số kiểu như raw type. `new ArrayList<>()` yêu cầu trình biên dịch giải bài toán suy luận tham số kiểu cho lớp `ArrayList<E>`; kết quả vẫn được kiểm tra như một biểu thức generic. Trong khi đó `new ArrayList()` chọn cách dùng raw `ArrayList`, với các chữ ký generic được nhìn dưới dạng đã xóa tham số kiểu.

Một điểm đáng nhớ: type ở **vế trái** và type ở **vế phải** đều quan trọng.

```java
List<String> safe = new ArrayList<>(); // Hai phía giữ ràng buộc String.
List<String> weak = new ArrayList();   // Phép gán unchecked từ raw sang List<String>.
List raw = new ArrayList<>();          // Biến raw vẫn cho phép gọi add không kiểm tra.
```

Ở dòng cuối, diamond của biểu thức `new` **không thể làm biến `raw` trở nên an toàn**: nơi sử dụng biến vẫn chỉ biết nó là `List` raw. Đây là lý do phải kiểm tra chữ ký biến, tham số và giá trị trả về trong cả luồng dữ liệu.

## 3. Diamond hoạt động như thế nào?

Một kiểu tổng quát khai báo các **tham số kiểu**, ví dụ `ArrayList<E>` hay `Map<K,V>`. Với biểu thức tạo đối tượng `new ArrayList<>()`, dấu `<>` báo cho compiler suy luận `E` từ các ràng buộc có sẵn.

### 3.1. Suy luận từ kiểu đích của biến

```java
List<String> letters = new ArrayList<>();
Set<Long> ids = new HashSet<>();
Map<String, Integer> ages = new HashMap<>();
```

Compiler kiểm tra xem constructor có thể tạo biểu thức tương thích với `List<String>`, `Set<Long>` và `Map<String,Integer>` hay không. Các tham số kiểu ở phía phải thường không cần lặp lại vì phía trái đã cung cấp ràng buộc.

Lưu ý: diamond không có nghĩa compiler luôn lấy đúng ký tự ở vế trái rồi thay thế máy móc. Suy luận kiểu còn phụ thuộc constructor, kiểu tham số truyền vào, cách sử dụng và các ràng buộc tương thích của Java.

### 3.2. Suy luận từ tham số phương thức

```java
// Đoạn trong một lớp Java, giả sử đã import java.util.*.
static void acceptNames(List<String> names) {
    System.out.println(names.size());
}

// Trong một phương thức khác:
acceptNames(new ArrayList<>());
```

Lời gọi có ngữ cảnh đích `List<String>` từ chữ ký `acceptNames`, nên `new ArrayList<>()` có thể được suy luận phù hợp. Không nhất thiết phải gán vào biến trung gian.

### 3.3. Suy luận từ kiểu trả về

```java
static Map<String, List<Integer>> emptyGrades() {
    return new HashMap<>();
}
```

Ngữ cảnh `return` cung cấp kiểu đích `Map<String,List<Integer>>`; compiler kiểm tra được biểu thức trả về. Với phương thức generic, suy luận của lời gọi phương thức và của constructor có thể tương tác, nhưng đó là hai quy tắc cú pháp khác nhau.

### 3.4. Generic lồng nhau: viết gọn mà vẫn rõ kiểu

```java
Map<String, List<Integer>> grades = new LinkedHashMap<>();
grades.put("Lan", new ArrayList<>());
grades.get("Lan").add(8);
grades.get("Lan").add(9);
// grades có dữ liệu tương ứng "Lan" -> [8, 9].
```

Compiler suy luận `Map<String,List<Integer>>` cho `LinkedHashMap<>` và có thể suy luận `ArrayList<Integer>` cho lời gọi `put` vì tham số thứ hai được yêu cầu tương thích với `List<Integer>`. Hệ thống generics vẫn kiểm tra chiều sâu của collection lồng nhau.

### 3.5. Đưa dữ liệu constructor vào suy luận

```java
List<String> names = new ArrayList<>(List.of("Lan", "Mai"));
Map<String, Integer> ages =
        new HashMap<>(Map.of("Lan", 20, "Mai", 21));
```

Compiler kết hợp kiểu đích và đối số constructor. `List.of`/`Map.of` trong ví dụ là các **generic static factory method**; chúng tự suy luận tham số kiểu của phương thức. Chúng **không dùng diamond**; diamond là cú pháp liên quan đến khởi tạo đối tượng với `new`.

## 4. Diamond và `var`: khi không còn kiểu đích tường minh

Từ Java 10, `var` cho phép compiler suy luận kiểu **biến cục bộ** từ biểu thức khởi tạo. Điều này tạo tình huống khác với `List<String> names = ...`:

```java
var a = new ArrayList<>();         // a là ArrayList<Object>.
a.add("Java");                     // Hợp lệ.
a.add(2026);                       // Cũng hợp lệ vì kiểu phần tử là Object.

var b = new ArrayList<String>();   // b là ArrayList<String>.
b.add("Java");
// b.add(2026);                    // Lỗi biên dịch.
```

Khi viết `var a = new ArrayList<>()` và không có dữ liệu/kiểu đích hữu ích, compiler suy luận đối số kiểu không bị ràng buộc thành `Object`. Đây vẫn là một **parameterized type** (`ArrayList<Object>`), **không phải raw type** (`ArrayList`). Sự khác biệt nằm ở chỗ danh sách `Object` có thể chứa nhiều kiểu tham chiếu theo đúng hợp đồng, còn raw type bỏ qua thông tin tham số kiểu và có thể phá vỡ hợp đồng của một danh sách vốn được khai báo là `List<String>`.

Cách viết nên chọn nếu cần một list chuỗi với `var`:

```java
var names = new ArrayList<String>();
names.add("Lan");
```

Nếu muốn ẩn implementation bằng giao diện, dùng khai báo tường minh:

```java
List<String> names = new ArrayList<>();
```

Cách thứ hai thường giúp API dễ đổi từ `ArrayList` sang implementation khác và thể hiện yêu cầu chỉ lưu `String` ngay tại khai báo.

**Không suy đoán `var` tương đương `Object` ở mọi trường hợp.** Ví dụ `var names = List.of("Lan")` thường suy luận `List<String>` nhờ kiểu trả về và đối số của generic factory `List.of`. Vấn đề cụ thể là `new ArrayList<>()` không có ràng buộc đủ cụ thể khi đặt ngay sau `var`.

## 5. Raw type: di sản tương thích với Java trước generics

Trước Java 5, các collection như `List` và `ArrayList` chưa có tham số kiểu. Khi generics xuất hiện, Java vẫn cho phép dạng raw để mã cũ tiếp tục làm việc:

```java
List legacy = new ArrayList();
legacy.add("hello");
legacy.add(42);
Object item = legacy.get(0);
```

Raw type thường xóa kiểm tra compile-time rằng một phần tử phải là `String`, `Integer` hay một kiểu cụ thể. Sau đó người dùng có thể phải cast từ `Object`, với lỗi runtime nếu giá trị thực không tương thích.

Ngược lại, phiên bản generic sau cung cấp kiểm tra trước khi chạy:

```java
List<String> messages = new ArrayList<>();
messages.add("hello");
// messages.add(42);       // Lỗi ngay lúc biên dịch.
String item = messages.get(0); // Không cần cast thủ công.
```

### 5.1. Heap pollution: một biến nhìn thấy kiểu sai về nội dung

*Heap pollution* là tình huống một biến của kiểu có tham số hóa trỏ đến dữ liệu không phù hợp với tham số kiểu đó. Raw type là một con đường phổ biến gây ra vấn đề này:

```java
List<String> words = new ArrayList<>();
words.add("Java");

List raw = words;       // Truy cập cùng đối tượng qua biến raw.
raw.add(99);            // Cảnh báo unchecked, nhưng có thể chạy.

String second = words.get(1); // Ném ClassCastException lúc chạy.
```

Điểm nguy hiểm là `words` vẫn có kiểu khai báo `List<String>`, nhưng biến raw đã lén thêm `Integer` vào cùng một đối tượng danh sách. Biểu thức đọc phần tử thông qua `words.get(1)` khiến compiler chèn cast thích hợp; cast đó thất bại khi gặp `Integer`.

Khi chạy `javac -Xlint:rawtypes -Xlint:unchecked`, ta có thể thấy hai nhóm cảnh báo:

- `[rawtypes]`: khai báo hoặc sử dụng một kiểu generic ở dạng raw, chẳng hạn `List raw`.
- `[unchecked]`: thao tác mà compiler không thể xác minh tính đúng đắn của tham số kiểu, chẳng hạn `raw.add(99)` hoặc gán raw list cho `List<String>`.

Cảnh báo không phải sự bảo đảm rằng code sẽ tự động sửa dữ liệu. Nó đánh dấu một vị trí người phát triển phải kiểm tra.

### 5.2. Raw type trong vế phải cũng nguy hiểm

```java
List<String> names = new ArrayList();  // Unchecked conversion.
names.add("Lan");
```

Compiler có thể cho phép phép gán nhằm tương thích với mã cũ, nhưng constructor ở đây không truyền thông tin generic qua biểu thức. Việc loại bỏ cảnh báo đúng cách là dùng `new ArrayList<>()` hoặc `new ArrayList<String>()`. Không nên chỉ thêm `@SuppressWarnings("unchecked")` vì annotation đó không phục hồi kiểm tra kiểu.

### 5.3. Raw type không mang lại lợi ích tốc độ hay bộ nhớ

Generics Java chủ yếu được triển khai bằng **type erasure**: biểu diễn runtime thông thường dùng chung lớp `ArrayList` cho `ArrayList<String>` và `ArrayList<Integer>`. Do đó raw type không phải một lựa chọn tối ưu hiệu năng; chọn raw thường chỉ làm mất thông tin phục vụ kiểm tra lúc biên dịch.

```java
boolean sameClass =
    new ArrayList<String>().getClass() == new ArrayList<Integer>().getClass();
// sameClass là true.
```

Type erasure cũng giải thích vì sao không thể dùng `instanceof List<String>` để xác minh tham số `String` ở runtime. Khi cần kiểm tra có phải một loại list, `instanceof List<?>` là phép kiểm tra hợp lệ; việc kiểm tra các phần tử thực tế phải thực hiện riêng khi cần.

## 6. Đừng nhầm `<>`, `<?>` và kiểu raw

Ba cách viết có ngoặc nhọn góc nhưng ý nghĩa khác nhau:

```java
List<String> strings = new ArrayList<>(); // Diamond: suy luận kiểu khi new.
List<?> unknown = strings;               // Wildcard: có kiểu phần tử chưa biết.
List raw = strings;                      // Raw: loại bỏ thông tin tham số kiểu.
```

Với `List<?>`, compiler vẫn bảo vệ an toàn kiểu:

```java
Object value = unknown.get(0);  // Hợp lệ: đọc như Object.
// unknown.add("more");         // Lỗi biên dịch: không biết kiểu phần tử.
// unknown.add(42);             // Lỗi biên dịch.
unknown.add(null);              // Hợp lệ với chữ ký add, nếu implementation cho phép null.
```

Lưu ý: `List<?>` không đồng nghĩa `List<Object>`. Với `List<Object>`, bạn có thể thêm bất kỳ giá trị tham chiếu tương thích `Object`, nhưng không thể gán trực tiếp `List<String>` cho `List<Object>` vì generics Java là **invariant**:

```java
List<String> names = new ArrayList<>();
List<?> readable = names;       // Hợp lệ.
// List<Object> writable = names; // Lỗi biên dịch.
List<Object> objects = new ArrayList<>();
objects.add("Lan");
objects.add(42);
```

Khi API chỉ cần xem/đọc dữ liệu chưa biết kiểu, `List<?>` thường tốt hơn raw `List`. Khi API cần nhận đối tượng có kiểu cụ thể, hãy mô tả đúng kiểu hoặc dùng wildcard có bound (`? extends T`, `? super T`) tùy thao tác.

| Loại | Đọc phần tử ra | Thêm phần tử | Điều phải nhớ |
| --- | --- | --- | --- |
| `List<String>` | `String` | `String` | Kiểm tra kiểu đầy đủ. |
| `List<Object>` | `Object` | Mọi đối tượng tham chiếu | Vẫn là generic, không phải raw. |
| `List<?>` | `Object` | Không thêm đối tượng cụ thể; `null` được phép về mặt kiểu | An toàn cho truy cập kiểu chưa biết. |
| `List` raw | `Object` | Gọi `add` không kiểm tra generic, thường có unchecked warning | Có thể làm hỏng hợp đồng của biến generic khác. |

## 7. Diamond hợp lệ ở đâu và không hợp lệ ở đâu?

Diamond là **danh sách tham số kiểu rỗng tại biểu thức khởi tạo lớp generic**:

```java
List<String> a = new ArrayList<>(); // Hợp lệ.
Map<String, Integer> b = new HashMap<>(); // Hợp lệ.
// List<> c = new ArrayList<>();      // Sai: phía khai báo phải có kiểu.
// List<String> d = new List<>();     // Sai: List là interface, không thể new.
```

`<>` không viết ở mọi vị trí dùng generics. Khi khai báo lớp/phương thức, phải đặt **tên tham số kiểu**:

```java
class Holder<T> {
    private final T value;
    Holder(T value) { this.value = value; }
    T get() { return value; }
}

static <T> List<T> singleton(T item) {
    return new ArrayList<>(List.of(item));
}
```

Khi gọi phương thức generic, trình biên dịch thường suy luận kiểu từ đối số và ngữ cảnh, không dùng cú pháp `method<>()`:

```java
List<String> one = singleton("Java");  // Suy luận T = String.
// List<String> wrong = singleton<> ("Java"); // Không có diamond ở đây.
List<String> empty = Collections.<String>emptyList(); // Chỉ định type witness.
```

Điều kiện và giới hạn bổ sung:

1. Diamond dùng khi **constructor tạo lớp phù hợp**, không thể tạo trực tiếp interface hay abstract class.
2. Kiểu tham số generic chỉ nhận **reference type**: `new ArrayList<Integer>()` hợp lệ, `new ArrayList<int>()` không hợp lệ. Boxing và unboxing hỗ trợ khi đưa số nguyên vào `List<Integer>`.
3. Diamond không vượt qua giới hạn bound: ví dụ lớp `NumericBox<T extends Number>` không thể được tạo thành `NumericBox<String>` bằng cách dùng `<>`.
4. Java 7 cho phép diamond với constructor không tạo anonymous class; từ **Java 9** có thể dùng diamond cùng anonymous class với các ràng buộc về kiểu suy luận. Với code hiện đại, chỉ dùng biến thể anonymous class nếu thực sự cần và hiểu giới hạn; lambda hoặc implementation có tên thường dễ đọc hơn.
5. Đừng dựa vào diamond để che một kiểu khó hiểu: khi inference gây lỗi hoặc làm code mất rõ ràng, khai báo kiểu tường minh ở vế trái, thêm đối số kiểu rõ ràng ở `new`, hoặc đơn giản hóa các lớp generic lồng nhau.

## 8. Bài toán thực tế: cải thiện API legacy mà không mất an toàn

Giả sử một hàm cũ trả về `List` raw từ thư viện trước Java 5:

```java
// Chỉ minh họa API legacy.
static List loadLegacy() {
    List data = new ArrayList();
    data.add("Lan");
    data.add("Mai");
    return data;
}
```

Cách viết sau có thể biên dịch với cảnh báo, nhưng **không chứng minh** mọi phần tử đều là `String`:

```java
List<String> names = loadLegacy(); // Unchecked conversion.
```

Cách chuyển đổi được kiểm tra ở ranh giới an toàn hơn là nhận `Collection<?>`, duyệt từng phần tử, xác nhận kiểu, rồi đưa vào danh sách mới:

```java
// Đặt bên trong một lớp đã import java.util.*.
static List<String> copyStrings(Collection<?> input) {
    List<String> out = new ArrayList<>();
    for (Object value : input) {
        if (!(value instanceof String s)) {
            throw new IllegalArgumentException("Expected String element");
        }
        out.add(s);
    }
    return out;
}

// List<String> names = copyStrings(loadLegacy());
```

Đoạn này cố ý từ chối cả `null` vì `null instanceof String` là `false`. Nếu hợp đồng dữ liệu cho phép `null`, cần xử lý nó một cách tường minh thay vì vô tình chấp nhận hoặc bỏ qua. Tạo danh sách mới tránh để một alias raw tiếp tục chèn phần tử sai kiểu vào chính danh sách đã kiểm tra.

Khi kiểm soát được mã nguồn legacy, cách sửa tối ưu là cập nhật chữ ký ngay tại nơi khai báo:

```java
static List<String> loadNames() {
    List<String> names = new ArrayList<>();
    names.add("Lan");
    names.add("Mai");
    return names;
}
```

Không cần xóa sạch mọi lời gọi cũ trong một bước; có thể tạo adapter kiểm tra dữ liệu ở ranh giới, sau đó chuyển dần tham số, kiểu trả về và các biến nội bộ sang kiểu generic.

## 9. Chương trình chạy được: diamond, `var` và lỗi raw type

Tạo tệp **`DiamondVsRaw.java`** như sau. Phần raw type được giữ có chủ đích để minh họa rủi ro; các đoạn mới nên ưu tiên generic an toàn.

```java
import java.util.*;

public class DiamondVsRaw {
    static final class Box<T> {
        private final T value;
        Box(T value) { this.value = value; }
        T get() { return value; }
    }

    public static void main(String[] args) {
        List<String> names = new ArrayList<>();
        names.add("Lan");
        names.add("Minh");
        System.out.println("typed: " + names);

        Box<Integer> score = new Box<>(9);
        System.out.println("box: " + score.get());

        Map<String, List<Integer>> grades = new LinkedHashMap<>();
        grades.put("Lan", new ArrayList<>());
        grades.get("Lan").add(9);
        System.out.println("map: " + grades);

        var noTarget = new ArrayList<>();
        noTarget.add("text");
        noTarget.add(42);
        System.out.println("inferred: " + noTarget);

        List<String> legacy = new ArrayList<>();
        legacy.add("OK");
        List raw = legacy;
        raw.add(123);
        try {
            String value = legacy.get(1);
            System.out.println(value);
        } catch (ClassCastException ex) {
            System.out.println("raw: ClassCastException");
        }
    }
}
```

Biên dịch trên **JDK 17+**; tùy chọn `-Xlint` yêu cầu compiler chỉ rõ vị trí cảnh báo:

```bash
javac -Xlint:rawtypes -Xlint:unchecked DiamondVsRaw.java
java DiamondVsRaw
```

Khi chạy `javac`, compiler sẽ báo `[rawtypes]` tại `List raw` và `[unchecked]` tại `raw.add(123)`. Diễn đạt đầy đủ/cách đánh số dòng trong cảnh báo phụ thuộc phiên bản compiler; đó là **diagnostics**, không phải stdout của chương trình.

Kết quả stdout của lệnh `java DiamondVsRaw` (đã biên dịch và chạy thử với JDK 21):

```text
typed: [Lan, Minh]
box: 9
map: {Lan=[9]}
inferred: [text, 42]
raw: ClassCastException
```

Diễn giải:

- `names` và `score` nhận ràng buộc generic chính xác. Diamond của `ArrayList<>` và `Box<>` giúp bỏ phần khai báo lặp lại.
- `grades` minh họa diamond cho `Map<String,List<Integer>>` và value là một `ArrayList<Integer>` suy luận từ tham số `put`.
- `var noTarget = new ArrayList<>()` tạo `ArrayList<Object>` vì không có ràng buộc cụ thể; cả chuỗi và số được boxing thành `Integer` đều thêm hợp lệ.
- `raw` trỏ tới **cùng đối tượng** với `legacy`, nên `raw.add(123)` đưa `Integer` vào `List<String>` và tạo vấn đề tại thời điểm đọc dữ liệu. `try/catch` làm ví dụ in ra tên loại lỗi để việc học không bị dừng ở stack trace thay đổi theo môi trường.

Trong code production, **loại bỏ alias raw**, đừng giữ cơ chế bắt `ClassCastException` như một giải pháp xử lý dữ liệu thường xuyên.

## 10. Những lỗi thường gặp

| Hiện tượng | Nguyên nhân | Cách xử lý |
| --- | --- | --- |
| `List<String> a = new ArrayList();` xuất hiện `unchecked conversion` | Constructor dùng raw type | Đổi thành `new ArrayList<>()`. |
| `List raw = new ArrayList<>();` vẫn có `rawtypes` | Kiểu khai báo của biến vẫn raw | Viết `List<String>`, `List<?>` hoặc một kiểu thích hợp. |
| `var xs = new ArrayList<>();` nhận cả `String` và `Integer` | Compiler suy luận `ArrayList<Object>` khi không có target type cụ thể | Dùng `var xs = new ArrayList<String>()` hoặc `List<String> xs = new ArrayList<>()`. |
| `List<> xs` không biên dịch | Diamond không dùng thay cho danh sách tham số trong khai báo kiểu | Viết `List<String>`, `List<?>` hoặc `List<T>`. |
| `new List<>()` không biên dịch | `List` là interface | Tạo `new ArrayList<>()` hoặc một implementation phù hợp. |
| `List<Integer> x = new ArrayList<String>();` không biên dịch | Hai tham số kiểu không tương thích | Thống nhất kiểu cần lưu ở hai vế. |
| `List<Number> x = new ArrayList<Integer>();` không biên dịch | Generics bất biến | Chọn `List<Number>` ở hai phía hoặc wildcard phù hợp để chỉ đọc. |
| `List<?> xs` không `add("a")` được | Kiểu phần tử chưa biết | Dùng kiểu cụ thể nếu cần ghi, hoặc thiết kế tham số `? super T` thích hợp. |
| `@SuppressWarnings("unchecked")` làm hết cảnh báo | Chỉ ẩn diagnostic; không xác thực dữ liệu | Sửa khai báo generic hoặc kiểm tra/chuyển đổi ở boundary. |
| `ClassCastException` xuất hiện khi `get` dù list có kiểu generic | Raw alias hoặc unchecked cast đưa sai phần tử vào list | Truy ngược nguồn heap pollution; bỏ raw, thêm kiểm tra tại nơi nhận dữ liệu. |

### Quy tắc chọn nhanh khi viết code mới

- Đã có kiểu đích rõ như `List<User>`, `Map<String, Long>`: ưu tiên `new ArrayList<>()`, `new HashMap<>()` để tránh lặp lại.
- Dùng `var` với constructor generic rỗng: cân nhắc viết tường minh tham số kiểu ở biểu thức `new`; đừng mặc định `<>` suy luận đúng kiểu mong muốn.
- Đang xử lý dữ liệu chưa biết kiểu: dùng `List<?>`/`Collection<?>` để đọc an toàn; kiểm tra phần tử trước khi chuyển thành typed collection.
- Gặp raw type trong code legacy: bật `-Xlint:rawtypes -Xlint:unchecked`, lần theo cảnh báo và sửa từ chữ ký API nếu có thể.
- Không thêm raw type nhằm né các lỗi generic; lỗi compile-time thường đang giúp phát hiện mâu thuẫn hợp đồng dữ liệu.

## 11. Bài tập tự kiểm tra và lời giải định hướng

**Bài 1 — Hai constructor:** Tại sao `List<String> s = new ArrayList<>()` tốt hơn `List<String> s = new ArrayList()`? Hãy chỉ ra nơi compiler có đủ thông tin để kiểm tra `String` và nơi xuất hiện cảnh báo.

**Định hướng:** Diamond suy luận `String` qua kiểu đích; raw constructor bỏ tham số kiểu và phép gán sang `List<String>` không được kiểm tra đầy đủ.

**Bài 2 — Có hay không là raw?** Xem hai khai báo:

```java
var a = new ArrayList<>();
var b = new ArrayList();
```

**Định hướng:** `a` là `ArrayList<Object>` (parameterized type do inference); `b` là raw `ArrayList`. Khi gọi `add` với `b`, compiler có thể cảnh báo unchecked. Cả hai có thể nhận nhiều kiểu phần tử, nhưng cơ chế an toàn kiểu khác nhau.

**Bài 3 — Sửa API:** Cho đoạn legacy:

```java
Map cache = new HashMap();
cache.put("answer", 42);
Integer n = (Integer) cache.get("answer");
```

Hãy đổi sang `Map<String,Integer> cache = new HashMap<>()`, bỏ cast và thử gọi `cache.put(10, "wrong")`. **Định hướng:** dòng `put` sai kiểu mới bị từ chối ngay khi biên dịch.

**Bài 4 — Một alias raw:** Giải thích vì sao `List<String>` vẫn có thể ném `ClassCastException` khi đọc, mặc dù `add(123)` qua chính biến này là lỗi biên dịch. **Định hướng:** một tham chiếu khác ở dạng raw có thể chèn phần tử không đúng hợp đồng vào cùng đối tượng; chính xác hơn, generic chỉ bảo vệ được những lối truy cập mà compiler có đủ thông tin kiểu.

Khi tự kiểm tra, bạn cần phân biệt được ba ý cốt lõi: **diamond là suy luận tham số kiểu**, **raw type bỏ tham số kiểu để tương thích mã cũ**, và **wildcard vẫn là generic an toàn kiểu dành cho trường hợp chưa biết tham số cụ thể**. Hiểu ba điểm này giúp đọc cảnh báo compiler và chỉnh sửa collection API trong dự án Java mà không tạo lỗi runtime khó truy vết.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="generics-syntax-005">Kiểu tham số hóa lồng nhau như Map&lt;K,List&lt;V&gt;&gt;</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểu tham số hóa lồng nhau trong Java: `Map<K, List<V>>`

Khi một cấu trúc dữ liệu chứa **một cấu trúc dữ liệu khác**, Java cho phép ghép các kiểu generic để diễn đạt đầy đủ kiểu ở từng tầng. Ví dụ `Map<String, List<Integer>>` là ánh xạ từ một `String` sang một danh sách `Integer`: mỗi khóa có thể gắn với nhiều số, trong khi trình biên dịch vẫn kiểm tra kiểu khóa, kiểu danh sách và kiểu phần tử.

Bài học tập trung vào cách **đọc, khai báo, khởi tạo và thao tác với kiểu tham số hóa lồng nhau**, đặc biệt mẫu `Map<K, List<V>>`. Sau bài này, bạn có thể viết phương thức generic cho cấu trúc ấy, chọn cách cập nhật an toàn, duyệt dữ liệu, xử lý khóa vắng mặt, nhận biết tính bất biến về kiểu và tránh nhầm lẫn giữa sao chép map với sao chép các danh sách bên trong.

## 1. Đọc đúng `Map<K, List<V>>` từ ngoài vào trong

Trước hết, `Map<K, V>` định nghĩa ánh xạ từ **một khóa kiểu `K`** tới **một giá trị kiểu `V`**. Khi giá trị đó là một danh sách, thay đối số kiểu thứ hai bằng `List<V>`:

```java
Map<K, List<V>>
```

Hãy đọc theo ba bước:

1. **Tầng ngoài — `Map<..., ...>`:** đây là cấu trúc ánh xạ khóa sang giá trị, với mỗi khóa duy nhất trong map.
2. **Đối số thứ nhất — `K`:** kiểu của khóa; `put` và `computeIfAbsent` cần khóa tương ứng với kiểu này.
3. **Đối số thứ hai — `List<V>`:** giá trị gắn với mỗi khóa là một danh sách; mỗi phần tử của danh sách có kiểu `V`.

Ở đây **`K` và `V` chỉ là tên tham số kiểu**, được khai báo bởi lớp/phương thức chứa chúng. Cấu trúc `Map<K, List<V>>` không mặc nhiên khai báo được `K` hoặc `V` trong mọi ngữ cảnh.

Ví dụ cụ thể:

```java
Map<String, List<Integer>> scores;
```

- `K` đã được thay bằng `String`: mỗi khóa là tên hoặc mã dưới dạng chuỗi.
- `V` đã được thay bằng `Integer`: mỗi danh sách chứa các số nguyên kiểu tham chiếu.
- Kiểu **giá trị của map** là `List<Integer>`, không phải `Integer`.
- Vì vậy, `scores.get("An")` có kiểu trả về `List<Integer>`; sau đó `scores.get("An").get(0)` có kiểu `Integer` nếu khóa tồn tại và danh sách không rỗng.

Mô hình dữ liệu:

```text
Map<String, List<Integer>>
    "An"    -> [8, 9, 10]
    "Bình"  -> [7, 9]
    "Chi"   -> []
```

Một map có thể có khóa trỏ tới danh sách rỗng, không có khóa đó, hoặc (nếu cách xây dựng cho phép) ánh xạ khóa tới `null`. Ba trạng thái này không nên mặc định được coi là giống nhau.

### Các ví dụ lồng kiểu khác

| Kiểu | Ý nghĩa |
| --- | --- |
| `List<List<String>>` | Danh sách mà mỗi phần tử là một danh sách chuỗi, ví dụ các hàng dữ liệu |
| `Map<String, List<Order>>` | Mỗi mã khách hàng liên kết với nhiều đơn hàng |
| `Map<Integer, Set<String>>` | Mỗi mã số liên kết với tập chuỗi không trùng |
| `Map<String, Map<String, Integer>>` | Ánh xạ hai cấp, ví dụ vùng → mặt hàng → số lượng |
| `List<Map<String, Object>>` | Danh sách các bản ghi có tên trường dạng chuỗi và giá trị đa kiểu |
| `Map<String, Optional<Long>>` | Mỗi khóa liên kết với một giá trị có thể có hoặc không, được biểu diễn bằng `Optional` |

Các kiểu như `Order` trong bảng là kiểu nghiệp vụ minh họa. `Map<String, List<V>>`, `Map<String, Set<V>>` hay `List<List<T>>` đều là **cú pháp kết hợp các kiểu generic**, không tạo nên loại cú pháp Java hoàn toàn mới.

## 2. Cú pháp khai báo, đóng ngoặc và toán tử kim cương

### 2.1. Khởi tạo với đối số kiểu cụ thể

```java
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

Map<String, List<Integer>> scores = new HashMap<>();
scores.put("An", new ArrayList<Integer>());
```

Đây là **đoạn minh họa nằm trong phạm vi có thể khai báo biến cục bộ**, không phải tệp Java độc lập. Trong `new HashMap<>()`, dấu `<>` là *diamond operator*: trình biên dịch suy luận các đối số kiểu của đối tượng được khởi tạo từ ngữ cảnh biến bên trái. Đối tượng vẫn được dùng theo hợp đồng `Map<String, List<Integer>>`.

Đối với Java 7 trở lên, hai dấu `>` liên tiếp trong `Map<K, List<V>>` được nhận diện đúng để đóng hai danh sách tham số kiểu. Không cần chèn khoảng trắng giữa chúng.

```java
Map<String, List<Integer>> a = new HashMap<>();
Map<String, Map<Integer, List<Long>>> b = new HashMap<>();
```

Ví dụ thứ hai có ba dấu `>` liên tiếp vì có ba tầng generic cần đóng. Khi đọc cú pháp nhiều tầng, hãy ghép từng dấu `>` với dấu `<` tương ứng từ trong ra ngoài.

### 2.2. `Map<K, List<V>>` phải nằm trong phạm vi khai báo `K`, `V`

**Trong lớp generic:**

```java
final class Groups<K, V> {
    private final Map<K, List<V>> data = new HashMap<>();
}
```

**Trong phương thức generic:**

```java
static <K, V> void add(
        Map<K, List<V>> groups,
        K key,
        V value) {
    groups.computeIfAbsent(key, unused -> new ArrayList<>()).add(value);
}
```

Phần `<K, V>` **đứng trước kiểu trả về `void`** là khai báo tham số kiểu của phương thức. Tham số kiểu `K` xuất hiện ở khóa, `V` xuất hiện ở danh sách giá trị và đối số `value`, nhờ đó trình biên dịch kiểm tra các lời gọi cùng một hợp đồng.

Nếu viết phương thức sử dụng `K`, `V` bên trong một lớp không khai báo `K`, `V` và cũng không khai báo `<K, V>` tại phương thức, mã sẽ không biên dịch.

### 2.3. Tự suy luận kiểu không đồng nghĩa với raw type

```java
Map<String, List<Integer>> typed = new HashMap<>();
Map raw = new HashMap(); // Raw type: mất thông tin kiểu của khóa và giá trị.
```

`new HashMap<>()` ở dòng đầu **vẫn an toàn kiểu** theo biến `typed`; dòng thứ hai sử dụng kiểu thô (*raw type*), vốn có thể gây cảnh báo và lỗi khi chạy. Không nhầm hai trường hợp chỉ vì cả hai cùng viết ngắn ở phía khởi tạo.

## 3. Chương trình hoàn chỉnh: thêm, đọc, nhóm dữ liệu và tạo bản chụp

Lưu mã sau vào tệp **`Main.java`**. Chương trình dùng `Map<String, List<Integer>>` để lưu nhiều điểm theo tên; đồng thời có phương thức generic dùng lại cho nhiều kiểu khóa và giá trị. Một `record` được nhóm theo khóa để cho thấy cùng thuật toán hỗ trợ kiểu dữ liệu khác.

```java
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class Main {
    record Enrollment(String course, String student) {}

    static <K, V> void append(
            Map<K, List<V>> groups,
            K key,
            V value) {
        groups.computeIfAbsent(key, unused -> new ArrayList<>()).add(value);
    }

    static <K, V> List<V> readSnapshot(
            Map<K, List<V>> groups,
            K key) {
        List<V> values = groups.get(key);
        return values == null ? List.of() : List.copyOf(values);
    }

    static <K, V> Map<K, List<V>> snapshot(
            Map<K, List<V>> groups) {
        Map<K, List<V>> copied = new LinkedHashMap<>();
        for (Map.Entry<K, List<V>> entry : groups.entrySet()) {
            copied.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return Collections.unmodifiableMap(copied);
    }

    static <K, V> Map<K, List<V>> groupBy(
            List<V> items,
            Function<? super V, ? extends K> keySelector) {
        Map<K, List<V>> result = new LinkedHashMap<>();
        for (V item : items) {
            K key = keySelector.apply(item);
            append(result, key, item);
        }
        return result;
    }

    public static void main(String[] args) {
        Map<String, List<Integer>> scores = new LinkedHashMap<>();
        append(scores, "An", 8);
        append(scores, "An", 9);
        append(scores, "Bình", 7);

        System.out.println("Scores: " + scores);
        System.out.println("First An: " + readSnapshot(scores, "An").get(0));
        System.out.println("Missing: " + readSnapshot(scores, "Chi"));

        Map<String, List<Integer>> frozen = snapshot(scores);
        scores.get("An").add(10);

        System.out.println("Live An: " + scores.get("An"));
        System.out.println("Snapshot An: " + frozen.get("An"));

        List<Enrollment> registrations = List.of(
                new Enrollment("Java", "An"),
                new Enrollment("SQL", "Bình"),
                new Enrollment("Java", "Chi"));

        Map<String, List<Enrollment>> byCourse =
                groupBy(registrations, Enrollment::course);

        List<String> javaStudents = byCourse.get("Java").stream()
                .map(Enrollment::student)
                .toList();

        System.out.println("Java students: " + javaStudents);
        // append(scores, "An", "wrong"); // Sai kiểu: V của scores là Integer.
    }
}
```

Chạy bằng JDK 17 trở lên:

```bash
javac -encoding UTF-8 Main.java
java Main
```

**Stdout:**

```text
Scores: {An=[8, 9], Bình=[7]}
First An: 8
Missing: []
Live An: [8, 9, 10]
Snapshot An: [8, 9]
Java students: [An, Chi]
```

Giải thích kết quả:

1. `append(scores, "An", 8)` suy luận `K = String` và `V = Integer` từ đối số `Map<String, List<Integer>>` và tham số truyền vào. Không thể thêm một `String` vào danh sách điểm qua lời gọi cùng kiểu này.
2. `computeIfAbsent` chỉ tạo `ArrayList<V>` khi khóa chưa có ánh xạ giá trị khác `null`. Nếu đã có danh sách, nó trả danh sách đang lưu để `add` tiếp phần tử.
3. `readSnapshot` trả danh sách rỗng khi không có danh sách cho khóa, tránh việc gọi phương thức trên `null`. Khi có dữ liệu, nó dùng `List.copyOf` tạo danh sách không sửa được từ bên ngoài.
4. `snapshot` sao chép từng danh sách trước khi tạo map không sửa được. Vì thế thêm điểm `10` vào `scores` không làm thay đổi danh sách của `frozen`.
5. `groupBy` nhận `Enrollment::course` làm hàm tạo khóa, nhưng giữ chính `Enrollment` làm kiểu phần tử của danh sách. Kết quả `Map<String, List<Enrollment>>` thể hiện chính xác cả hai tầng dữ liệu.

**Phạm vi giả định của các hàm minh họa:** `snapshot` và `readSnapshot` yêu cầu danh sách/phần tử sao chép không phải `null`, vì `List.copyOf` ném `NullPointerException` với phần tử `null`. `snapshot` không sao chép sâu bản thân từng đối tượng `V` nếu chúng có trạng thái có thể thay đổi. `readSnapshot` chủ ý gộp hai trường hợp khóa vắng mặt và giá trị ánh xạ `null` thành danh sách rỗng; một API cần phân biệt trạng thái phải kiểm tra thêm bằng `containsKey`.

## 4. Truy cập và duyệt từng tầng dữ liệu

Với biến:

```java
Map<String, List<Integer>> scores = new LinkedHashMap<>();
```

các biểu thức sau có ý nghĩa kiểu khác nhau:

| Biểu thức | Kiểu tại thời điểm biên dịch | Lưu ý |
| --- | --- | --- |
| `scores` | `Map<String, List<Integer>>` | Toàn bộ ánh xạ |
| `scores.keySet()` | `Set<String>` | Tập các khóa |
| `scores.values()` | `Collection<List<Integer>>` | Tập hợp các danh sách; **không phải** `List<Integer>` |
| `scores.get("An")` | `List<Integer>` | Có thể là `null` nếu khóa không tồn tại hoặc được ánh xạ tới `null` |
| `scores.get("An").get(0)` | `Integer` | Chỉ an toàn nếu danh sách tồn tại và có phần tử đầu |
| `scores.entrySet()` | `Set<Map.Entry<String, List<Integer>>>` | Duyệt đồng thời khóa và danh sách |
| `scores.getOrDefault("Chi", List.of())` | `List<Integer>` | Dùng default nếu khóa **không có ánh xạ**; không thay thế giá trị `null` đang gắn với khóa |

Có ba cách duyệt hay gặp:

**Duyệt từng cặp khóa và danh sách:**

```java
for (Map.Entry<String, List<Integer>> entry : scores.entrySet()) {
    String name = entry.getKey();
    List<Integer> grades = entry.getValue();

    for (Integer grade : grades) {
        System.out.println(name + ": " + grade);
    }
}
```

Đây là mã minh họa dùng biến `scores` đã được khai báo và giả định các danh sách không `null`. Mỗi vòng `for` tương ứng một tầng tham số hóa. Với `HashMap`, thứ tự dòng in **không được bảo đảm**; chọn `LinkedHashMap` nếu cần giữ thứ tự chèn.

**Duyệt toàn bộ giá trị lồng nhau:**

```java
for (List<Integer> grades : scores.values()) {
    for (Integer grade : grades) {
        // Xử lý từng điểm grade.
    }
}
```

**Gộp phần tử từ nhiều danh sách bằng Stream:**

```java
List<Integer> allScores = scores.values().stream()
        .flatMap(List::stream)
        .toList();
```

Với Java 17, `Stream.toList()` tạo danh sách không thể sửa bằng các thao tác thêm/xóa trực tiếp. Nó không yêu cầu sắp xếp lại phần tử; thứ tự kết quả phụ thuộc thứ tự gặp phần tử của nguồn và các stream được tạo. Khi các danh sách đầu vào hoặc thứ tự lặp của map chưa xác định, không nên khẳng định một thứ tự đầu ra cụ thể.

## 5. Thao tác thêm phần tử: hiểu rõ danh sách bên trong có mutable không

Có hai cách viết thường gặp.

### 5.1. Tự tạo danh sách nếu thiếu khóa

```java
List<Integer> scoresOfAn = scores.get("An");
if (scoresOfAn == null) {
    scoresOfAn = new ArrayList<>();
    scores.put("An", scoresOfAn);
}
scoresOfAn.add(10);
```

Cách này phù hợp với map có thể thay đổi và các giá trị danh sách cũng có thể thay đổi. Nó coi khóa chưa tồn tại và khóa đang ánh xạ tới `null` như nhau.

### 5.2. Sử dụng `computeIfAbsent`

```java
scores.computeIfAbsent("An", key -> new ArrayList<>()).add(10);
```

`computeIfAbsent` hoạt động theo ý tưởng: nếu chưa có giá trị khác `null` cho khóa, tính giá trị mới qua lambda và gắn vào map; rồi trả về giá trị đó để dùng tiếp. Đây là cách gọn và đúng cho mẫu **một khóa → nhiều phần tử** trong ngữ cảnh map/danh sách mutable phù hợp.

**Không nhầm với `put`:**

```java
scores.put("An", new ArrayList<>(List.of(10)));
```

`put` trên khóa đã tồn tại sẽ **thay cả danh sách trước đó**, không chỉ thêm `10` vào danh sách hiện tại. Nếu muốn tích lũy phần tử, dùng `computeIfAbsent(...).add(...)` hoặc thao tác tương đương.

### Hai lỗi mutability phổ biến

**Map không sửa được:**

```java
Map<String, List<Integer>> immutableMap =
        Map.of("An", List.of(8, 9));
// immutableMap.put("Bình", List.of(7)); // UnsupportedOperationException.
```

**Danh sách không sửa được dù map có thể sửa:**

```java
Map<String, List<Integer>> mixed = new HashMap<>();
mixed.put("An", List.of(8, 9));
// mixed.get("An").add(10); // UnsupportedOperationException.
```

Không có quy tắc rằng `Map<K,List<V>>` luôn mutable hoặc luôn immutable. Khả năng sửa của **map ngoài** và **danh sách trong** là hai thuộc tính độc lập, phụ thuộc đối tượng triển khai. Một map mutable vẫn có thể chứa danh sách immutable.

Nếu API yêu cầu bất biến, hãy xác định nó có cho phép thay cả danh sách, thêm phần tử bên trong, hay không thao tác nào; sau đó thiết kế rõ hợp đồng đọc/ghi thay vì chỉ dựa vào tên kiểu generic.

## 6. Khóa vắng mặt, danh sách rỗng, `null` và kiểu trả về

Đọc:

```java
List<Integer> grades = scores.get("An");
```

Cần phân biệt:

- **Khóa không tồn tại:** `Map.get` thường trả về `null`.
- **Khóa tồn tại, giá trị `null`:** một số triển khai map cho phép; `get` cũng trả `null`, nhưng `containsKey("An")` là `true`.
- **Khóa tồn tại, danh sách rỗng:** `get` trả danh sách có `isEmpty() == true`, khác `null`.
- **Khóa tồn tại, danh sách có phần tử:** có thể duyệt và truy cập theo chỉ số hợp lệ.

Nếu luôn muốn danh sách rỗng cho khóa không tồn tại:

```java
List<Integer> grades = scores.getOrDefault("An", List.of());
```

Chú ý `getOrDefault` **không** thay thế giá trị `null` được ánh xạ rõ ràng bởi khóa. Nếu cần coi `null` giống vắng mặt:

```java
List<Integer> grades = scores.get("An");
if (grades == null) {
    grades = List.of();
}
```

Nếu cần phân biệt **vắng mặt** với **danh sách rỗng**, giữ nguyên hợp đồng `get` và dùng `containsKey` khi cần. Lạm dụng `List.of()` làm mặc định có thể làm mất thông tin nghiệp vụ, chẳng hạn “khách hàng chưa được tải lịch sử” khác với “lịch sử đã tải và rỗng”.

`List.of()` phù hợp làm **giá trị đọc mặc định** nhưng không thể `.add(...)`; muốn cập nhật phải tạo `new ArrayList<>()` hoặc chọn cấu trúc mutable thích hợp.

## 7. Bất biến kiểu ở nhiều tầng: lỗi biên dịch cần hiểu

Generics trong Java mặc định **bất biến (invariant)**. Dù `Integer` là subtype của `Number`, các kiểu `List<Integer>` và `List<Number>` không có quan hệ subtype theo chiều đó. Mức lồng nhau cũng không làm quy tắc này biến mất:

```java
Map<String, List<Integer>> grades = new HashMap<>();

// Không biên dịch:
// Map<String, List<Number>> asNumbers = grades;

// Không biên dịch:
// Map<String, List<? extends Number>> flexible = grades;
```

Tại sao phép gán thứ hai vẫn sai? `List<Integer>` có thể sử dụng ở nơi yêu cầu `List<? extends Number>`, nhưng **`Map<String, List<Integer>>` không phải subtype của `Map<String, List<? extends Number>>`** vì đối số kiểu thứ hai của `Map` cũng bất biến. Nếu cho gán, chỗ nhận có thể thay danh sách trong map bằng một danh sách `Double`, phá vỡ hợp đồng ban đầu.

Để viết API **chỉ đọc** các giá trị số từ nhiều loại danh sách, có thể mở rộng bằng wildcard đúng tầng:

```java
static double sumAll(
        Map<String, ? extends List<? extends Number>> source) {
    double total = 0;
    for (List<? extends Number> numbers : source.values()) {
        for (Number number : numbers) {
            total += number.doubleValue();
        }
    }
    return total;
}
```

Đây là phương thức minh họa, không phải chương trình độc lập. Kiểu `Map<String, ? extends List<? extends Number>>` cho phép nhận một map có giá trị là các danh sách với phần tử số phù hợp, bao gồm `Map<String, List<Integer>>`. Tuy vậy, qua tham chiếu `source` ta không thể thay tùy ý danh sách trong map bằng một `List<Number>` hay thêm một `Number` tùy ý vào danh sách nhận được. Cơ chế wildcard rộng hơn cho việc đọc nhưng **giới hạn một số thao tác ghi** để giữ an toàn kiểu.

Điểm cần nhớ: wildcard đặt ở **tầng nào** sẽ quyết định sự linh hoạt ở tầng đó. Ba chữ ký dưới đây khác nhau:

```java
Map<String, List<Number>>
Map<String, List<? extends Number>>
Map<String, ? extends List<? extends Number>>
```

Một API nhận chính xác kiểu thứ nhất là hợp đồng khác với API nhận kiểu thứ hai và kiểu thứ ba. Hãy bắt đầu từ nhu cầu sử dụng: cần đọc giá trị số, cần thêm loại số bất kỳ, hay cần thay cả danh sách?

### Lỗi thường gặp: gán map sang `Map<Object, ...>`

```java
Map<String, List<Integer>> scores = new HashMap<>();
// Map<Object, List<Object>> other = scores; // Không biên dịch.
```

Việc `String` là subtype của `Object` và `Integer` cũng là subtype của `Object` **không** làm các kiểu map liên quan trở thành subtype theo cách trên. Khi thực sự muốn chứa nhiều loại khóa và nhiều loại phần tử, cần thiết kế kiểu dữ liệu từ đầu, ví dụ `Map<Object, List<Object>>`, chấp nhận giảm độ chính xác của kiểu và kiểm tra dữ liệu ở ranh giới phù hợp.

## 8. Bài toán sao chép: map ngoài và danh sách trong

Một cấu trúc lồng nhau có thể chia sẻ tham chiếu giữa nhiều nơi:

```java
Map<String, List<Integer>> original = new HashMap<>();
original.put("An", new ArrayList<>(List.of(8, 9)));

Map<String, List<Integer>> shallow = new HashMap<>(original);
shallow.get("An").add(10);
```

`shallow` là **map mới**, nhưng `original.get("An")` và `shallow.get("An")` ban đầu trỏ tới **cùng một đối tượng danh sách**. Vì vậy, thêm `10` qua `shallow` cũng làm thay đổi danh sách nhìn từ `original`. Sao chép map **không tự động sao chép** các giá trị tham chiếu bên trong.

Tương tự, `Collections.unmodifiableMap(original)` chỉ tạo lớp xem không sửa được đối với map: bản thân các danh sách tham chiếu bên trong vẫn có thể mutable. `Map.copyOf(original)` tạo map không sửa được nhưng cũng không sao chép sâu từng danh sách; đồng thời không chấp nhận khóa/giá trị `null`.

Nếu muốn **bản chụp không sửa được ở hai tầng**, có thể sao chép danh sách trước:

```java
Map<String, List<Integer>> copied = new LinkedHashMap<>();
for (Map.Entry<String, List<Integer>> entry : original.entrySet()) {
    copied.put(entry.getKey(), List.copyOf(entry.getValue()));
}
Map<String, List<Integer>> frozen =
        Collections.unmodifiableMap(copied);
```

Cần hiểu chính xác giới hạn: `List.copyOf` chỉ sao chép cấu trúc danh sách và bảo đảm danh sách trả về không sửa được qua API đó; nó **không clone từng phần tử**. Nếu `V` là đối tượng có state mutable, muốn cách ly hoàn toàn state cần cơ chế tạo bản sao thích hợp cho chính kiểu `V`. Nếu dữ liệu đầu vào chứa `null`, `List.copyOf` ném `NullPointerException`.

Khi ngữ cảnh yêu cầu **cập nhật liên tục**, một cấu trúc mutable đơn giản như `LinkedHashMap<K, ArrayList<V>>` ở bên trong và chỉ phơi ra `Map<K,List<V>>`/danh sách sao chép ở API đọc có thể là lựa chọn hợp lý, nhưng cần nhất quán về quyền sửa và đồng bộ trong hệ thống nhiều luồng.

## 9. Khi nào nên dùng `Map<K,List<V>>`, khi nào cần cấu trúc khác?

`Map<K,List<V>>` phù hợp khi:

- Một khóa có **nhiều giá trị** và **thứ tự** hoặc khả năng lặp lại phần tử là quan trọng.
- Bạn cần nhóm dữ liệu theo thuộc tính, ví dụ khóa môn học → danh sách người đăng ký.
- Bạn cần tra nhanh nhóm theo khóa rồi duyệt các phần tử trong nhóm.

Nhưng không phải mọi quan hệ một-nhiều đều nên dùng `List<V>`:

| Nhu cầu | Lựa chọn tham khảo | Vì sao |
| --- | --- | --- |
| Cho phép giá trị lặp và lưu thứ tự trong mỗi nhóm | `Map<K, List<V>>` | List biểu diễn thứ tự và giữ bản sao các giá trị lặp |
| Không muốn phần tử lặp trong từng nhóm | `Map<K, Set<V>>` | Set diễn đạt tính duy nhất trong mỗi nhóm |
| Chỉ cần một giá trị trên mỗi khóa | `Map<K, V>` | Không cần thêm một tầng `List` |
| Cần nhóm hai cấp khóa độc lập | `Map<K1, Map<K2, V>>` | Mỗi cấp map mang một vai trò khóa |
| Cần giữ thứ tự toàn cục giữa mọi bản ghi | `List<Record>` hoặc model phù hợp | Nhóm theo khóa có thể che đi thứ tự xen kẽ ban đầu |
| Có nhiều quy tắc nghiệp vụ quanh nhóm | Lớp/domain object chuyên biệt | Đặt validation và thao tác nhất quán vào API có tên |

`HashMap` không bảo đảm thứ tự duyệt, còn `LinkedHashMap` giữ thứ tự chèn của khóa. Kiểu `List<V>` mô tả danh sách ở tầng giá trị nhưng không nói map ngoài là loại nào. Nếu thứ tự, tính duy nhất hay quyền thay đổi có ý nghĩa nghiệp vụ, hãy nêu rõ trong API hoặc chọn cấu trúc phù hợp.

### Nhóm dữ liệu bằng Stream

Một cách tạo kiểu lồng là `Collectors.groupingBy`:

```java
Map<String, List<Enrollment>> byCourse =
        registrations.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        Enrollment::course));
```

`Enrollment` và `registrations` lấy từ chương trình hoàn chỉnh ở phần 3. Kết quả có dạng `Map<String, List<Enrollment>>`. Không nên giả định thứ tự khóa hoặc loại/mutability chính xác của map và list do collector mặc định trả về; nếu những thuộc tính này là yêu cầu của API, hãy chọn overload với `mapFactory`/collector phù hợp hoặc chủ động tạo cấu trúc được kiểm soát.

Nếu muốn **giữ thứ tự gặp khóa** khi nhóm trong stream tuần tự, có thể dùng map factory:

```java
Map<String, List<Enrollment>> ordered =
        registrations.stream().collect(
                java.util.stream.Collectors.groupingBy(
                        Enrollment::course,
                        LinkedHashMap::new,
                        java.util.stream.Collectors.toList()));
```

Đây là đoạn dùng trong ngữ cảnh tệp `Main.java` ở trên, không phải chương trình độc lập. Không nhầm "LinkedHashMap giữ thứ tự chèn khóa" với "mọi collector bảo đảm tính mutable và thứ tự trong mọi chế độ chạy". Hành vi thứ tự cần đọc từ hợp đồng của collector và đặc điểm stream đầu vào.

## 10. Type erasure và kiểm tra dữ liệu runtime

Sự an toàn kiểu của `Map<String, List<Integer>>` được kiểm tra chủ yếu **lúc biên dịch**. Java dùng cơ chế *type erasure*: tại runtime, phần lớn đối số kiểu như `String`, `Integer` trong cách tham số hóa không còn được reify trong một đối tượng map thông thường.

Vì vậy cú pháp sau không hợp lệ:

```java
// if (value instanceof Map<String, List<Integer>>) { ... }
// Không biên dịch: không kiểm tra được toàn bộ kiểu tham số hóa này.
```

Bạn có thể kiểm tra cấu trúc thô:

```java
if (value instanceof Map<?, ?>) {
    // Chỉ biết value là một Map, chưa biết kiểu khóa và giá trị bên trong.
}
```

Đây là đoạn minh họa dùng biến `value` kiểu `Object` đã tồn tại trong phạm vi. Khi dữ liệu đến từ JSON, phản hồi API, reflection hoặc mã raw, hãy **xác thực từng tầng**: kiểu khóa, kiểu giá trị có phải danh sách, và kiểu từng phần tử trước khi xem dữ liệu là `Map<String,List<Integer>>`.

Không nên viết một phép ép kiểu `((Map<String, List<Integer>>) value)` rồi xem đó là kiểm tra runtime đầy đủ. Phép ép không kiểm chứng có thể chấp nhận ở biên dịch kèm cảnh báo, nhưng dữ liệu sai bên trong vẫn dẫn tới lỗi khi truy cập sau đó. Với dữ liệu ngoài chương trình, dùng thư viện chuyển đổi có mô tả đầy đủ kiểu lồng nhau hoặc kiểm tra thủ công theo schema.

Về kỹ thuật, `Class<?>` đơn lẻ chỉ biểu diễn được lớp runtime, chẳng hạn `Map.class` hoặc `List.class`, chứ không biểu diễn đầy đủ `Map<String,List<Integer>>`. Một số thư viện JSON có cơ chế **type reference** cho kiểu generic lồng; cách sử dụng cụ thể tùy API của thư viện, không phải tính năng tự có của cú pháp Java.

## 11. Những lỗi điển hình và cách sửa

**Lỗi 1 — quên tầng `List`:**

```java
Map<String, List<Integer>> scores = new HashMap<>();
// scores.put("An", 8); // Sai: giá trị map phải là List<Integer>.
scores.put("An", new ArrayList<>(List.of(8))); // Hợp lệ.
```

**Lỗi 2 — đọc map như thể trả một `Integer`:**

```java
// Integer first = scores.get("An"); // Sai: get trả List<Integer>.
List<Integer> an = scores.get("An");
```

Muốn lấy phần tử đầu, cần `an.get(0)`, sau khi đảm bảo `an != null` và `!an.isEmpty()`.

**Lỗi 3 — chia sẻ một danh sách cho nhiều khóa ngoài ý muốn:**

```java
List<Integer> shared = new ArrayList<>();
scores.put("An", shared);
scores.put("Bình", shared);
```

Khi thêm phần tử qua một khóa, khóa còn lại nhìn thấy cùng thay đổi. Nếu muốn các nhóm độc lập, hãy tạo `new ArrayList<>()` riêng cho mỗi nhóm.

**Lỗi 4 — giả định `Map.of`/`List.of` có thể thêm phần tử:**

```java
Map<String, List<Integer>> fixed = Map.of("An", List.of(8));
// fixed.get("An").add(9); // UnsupportedOperationException.
```

Dùng các triển khai mutable khi thao tác cập nhật là yêu cầu, hoặc xây bản kết quả mới theo kiểu bất biến.

**Lỗi 5 — hiểu sai wildcard ở tầng trong:** `Map<String,List<Integer>>` không thể gán thẳng cho `Map<String,List<? extends Number>>`. Nếu một phương thức chỉ đọc nhiều loại danh sách số, hãy đánh giá `Map<String, ? extends List<? extends Number>>` cùng các giới hạn thao tác ghi.

**Lỗi 6 — trả trực tiếp danh sách nội bộ cho bên gọi:** bên nhận có thể `add`, `clear` hoặc thay đổi thứ tự, làm hỏng trạng thái của map. Dùng `List.copyOf`/view thích hợp và mô tả rõ đó là bản chụp hay một view sống.

**Lỗi 7 — coi `getOrDefault` như thay thế cả giá trị `null`:** phương thức chỉ lấy default khi khóa không có ánh xạ; kiểm tra `null` riêng nếu hợp đồng cần gộp hai trạng thái.

**Lỗi 8 — ép kiểu raw/unchecked khi dữ liệu không rõ nguồn:** thông tin kiểu generic có thể bị phá vỡ lúc chạy. Hãy kiểm tra cấu trúc ở biên dữ liệu, hạn chế raw type và xử lý cảnh báo `unchecked` có chủ đích.

## 12. Tự luyện tập

**Bài 1.** Cho:

```java
Map<String, List<Long>> ids = new HashMap<>();
```

Kiểu của `ids.get("A")` và `ids.values()` là gì?

**Đáp án:** `ids.get("A")` có kiểu `List<Long>`; `ids.values()` có kiểu `Collection<List<Long>>`. Không được kết luận rằng hai biểu thức tương đương với `Long` hoặc `Collection<Long>`.

**Bài 2.** Khi gọi:

```java
append(scores, "An", 9);
```

ở chương trình hoàn chỉnh, hai tham số kiểu được suy luận ra sao?

**Đáp án:** `K = String`, `V = Integer` vì `scores` là `Map<String,List<Integer>>`. Dòng `append(scores, "An", "9")` dùng chuỗi thay cho điểm số nguyên và bị từ chối khi biên dịch.

**Bài 3.** Vì sao `new HashMap<>(scores)` chưa đủ để tránh danh sách bên trong bị thay đổi từ một tham chiếu khác?

**Đáp án:** Hàm tạo ấy tạo một map mới nhưng sao chép các **tham chiếu giá trị**; những danh sách chứa trong cả hai map vẫn có thể là cùng đối tượng. Cần sao chép các danh sách khi muốn cách ly cấu trúc bên trong.

**Bài 4.** Một khóa chưa có điểm và một khóa có danh sách điểm rỗng có cùng ngữ nghĩa không?

**Đáp án:** Không nhất thiết. `get` trên khóa vắng mặt có thể trả `null`; khóa ánh xạ tới `List.of()` trả danh sách rỗng. Nghiệp vụ quyết định liệu có cần phân biệt và có nên sử dụng `containsKey` hay không.

**Bài 5.** Chọn chữ ký thích hợp cho một hàm **chỉ đọc** tất cả số trong các danh sách giá trị:

```java
Map<String, List<Number>>
Map<String, ? extends List<? extends Number>>
```

**Đáp án:** Chữ ký thứ hai linh hoạt hơn khi nhận `Map<String,List<Integer>>` hoặc các danh sách thuộc những loại con khác của `Number`. Đổi lại, API không thể tự do thay danh sách hay thêm các phần tử tùy ý qua tham chiếu wildcard.

## Tổng kết

Với `Map<K, List<V>>`, hãy đọc từ ngoài vào trong: map có khóa kiểu `K`, mỗi giá trị là danh sách phần tử kiểu `V`. Điểm mạnh của cú pháp lồng nhau là trình biên dịch có thể theo dõi **kiểu ở mọi tầng** và buộc `put`, `get`, `entrySet`, `computeIfAbsent` hay các phương thức generic tuân theo quan hệ đó. Để dùng đúng trong ứng dụng thực tế, hãy đồng thời quyết định **cấu trúc có mutable không, cần giữ thứ tự không, khóa vắng mặt xử lý ra sao và liệu bản sao map có đủ cách ly dữ liệu bên trong không**. Cuối cùng, nhớ rằng bất biến kiểu áp dụng ở từng tầng và type erasure không thay thế việc xác thực dữ liệu từ nguồn bên ngoài.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="generics-syntax-006">Chỉ dùng kiểu tham chiếu: primitive, kiểu bao và autoboxing</a>

<details>
<summary>Xem chi tiết</summary>

# Chỉ dùng kiểu tham chiếu trong Java Generics: primitive, kiểu bao và autoboxing

Trong Java, **đối số kiểu (type argument) truyền cho generics phải là kiểu tham chiếu**. Vì vậy, `List<Integer>` hợp lệ còn `List<int>` không hợp lệ. Java cung cấp **wrapper class (lớp bao)** như `Integer`, `Double` và cơ chế **autoboxing/unboxing** để làm việc thuận tiện với số, boolean và ký tự khi API yêu cầu kiểu tham chiếu.

Quy tắc tưởng đơn giản này tác động trực tiếp đến cú pháp generic class và method, thao tác `List`/`Map`, lỗi `NullPointerException`, so sánh giá trị, cũng như chi phí bộ nhớ và hiệu năng.

## 1. Phân biệt primitive và reference type

Java có tám kiểu nguyên thủy (*primitive types*): `boolean`, `byte`, `short`, `char`, `int`, `long`, `float` và `double`. Biến primitive giữ một giá trị thuộc kiểu đó, chẳng hạn `int count = 10;` hoặc `boolean active = true;`. Primitive không phải đối tượng và không có phương thức instance.

**Kiểu tham chiếu (reference type)** bao gồm class, interface, array và các loại kiểu tham chiếu khác của Java; biến tham chiếu có thể trỏ đến một đối tượng/mảng hoặc nhận `null`. Ví dụ `String`, `Integer`, `List<String>` và `int[]` đều là kiểu tham chiếu, dù các phần tử của `int[]` là primitive.

Bảng tương ứng thường dùng:

| Primitive | Wrapper class | Ví dụ giá trị |
|---|---|---|
| `boolean` | `Boolean` | `Boolean flag = true;` |
| `byte` | `Byte` | `Byte size = (byte) 7;` |
| `short` | `Short` | `Short code = (short) 12;` |
| `char` | `Character` | `Character letter = 'A';` |
| `int` | `Integer` | `Integer count = 42;` |
| `long` | `Long` | `Long total = 42L;` |
| `float` | `Float` | `Float rate = 1.5f;` |
| `double` | `Double` | `Double amount = 1.5;` |

Wrapper class nằm trong `java.lang`, được import tự động. Chúng là những đối tượng **bất biến (immutable)**: thao tác số học không sửa giá trị bên trong một đối tượng `Integer` đã tồn tại. Cả primitive và wrapper đều có ích; chọn loại phù hợp với hợp đồng API và yêu cầu dữ liệu.

`void` không phải một primitive chứa giá trị để lưu trong collection; `Void` là một lớp tham chiếu đặc biệt, đôi khi dùng làm tham số kiểu để biểu đạt việc không có kết quả giá trị trong API như `Callable<Void>`. Không nên coi `Void` là “kiểu số bao” hoặc một cách lưu giá trị `void`.

## 2. Vì sao `List<int>` sai mà `List<Integer>` đúng?

Generic class hoặc generic interface dùng kiểu tham số hóa, ví dụ `List<E>`, `Map<K, V>` và `Box<T>`. Với Java 17, **đối số kiểu phải là reference type** (hoặc wildcard hợp lệ ở vị trí cho phép); tên kiểu primitive như `int` và `double` không được viết trong ngoặc `<...>` dưới dạng đối số kiểu.

Các ví dụ **không biên dịch**, được đặt riêng để nhận biết lỗi:

```java
List<int> numbers;            // Sai: int không phải reference type
Map<String, double> prices;   // Sai: double không phải reference type
Box<boolean> enabled;         // Sai: boolean không phải reference type
```

Các phiên bản **hợp lệ**:

```java
List<Integer> numbers;
Map<String, Double> prices;
Box<Boolean> enabled;
```

Những đoạn trên là **minh họa khai báo**, không phải các chương trình chạy độc lập: cần import `List`, `Map` và định nghĩa lớp `Box<T>` thích hợp.

Tương tự, phương thức generic:

```java
static <T> T identity(T value) {
    return value;
}
```

có thể được gọi bằng `identity(123)` nhưng không có nghĩa `T` trở thành primitive `int`. Trình biên dịch áp dụng **boxing** để biểu diễn đối số bằng `Integer`, sau đó suy luận và kiểm tra kiểu theo các quy tắc áp dụng cho reference type. Lệnh `Integer result = identity(123);` hợp lệ; khi gán kết quả vào `int` có thể phát sinh **unboxing**.

**Type erasure:** phần lớn tham số kiểu của Java bị xóa khi biên dịch: tham số `T` không có bound thường xóa về `Object`, tham số có bound xóa về bound tương ứng. Vì `Object` và các bound là kiểu tham chiếu, wrapper giúp truyền primitive qua ranh giới của các API generic thông thường. Không nên hiểu `List<Integer>` là `List<int>` bí mật được tối ưu thành mảng số nguyên thủy; nó vẫn là danh sách các tham chiếu `Integer`.

## 3. Boxing, autoboxing, unboxing: chính xác là gì?

**Boxing** chuyển giá trị primitive thành giá trị kiểu wrapper tương ứng. **Autoboxing** là boxing được trình biên dịch tự chèn trong các ngữ cảnh Java hỗ trợ. Ngược lại, **unboxing** lấy giá trị primitive từ wrapper; trình biên dịch cũng có thể chèn unboxing tự động.

```java
int primitive = 42;

Integer boxedExplicitly = Integer.valueOf(primitive); // Boxing chủ động
Integer boxedAutomatically = primitive;               // Autoboxing

int unboxedExplicitly = boxedExplicitly.intValue();   // Unboxing chủ động
int unboxedAutomatically = boxedAutomatically;         // Auto-unboxing
```

Đây là đoạn minh họa cần đặt trong method hoặc vùng khai báo hợp lệ. Hai dòng với `Integer.valueOf(...)` và `.intValue()` giúp nhìn rõ cơ chế; trong code thường ngày, autoboxing/unboxing giúp cú pháp ngắn hơn.

Các bối cảnh hay xuất hiện chuyển đổi tự động:

- **Gán và truyền tham số:** `Integer x = 10;`, hoặc truyền `10` vào method đòi `Integer`, nếu ngữ cảnh cho phép boxing.
- **Đưa primitive vào collection:** `List<Integer> values = new ArrayList<>(); values.add(10);` — đối số `10` được boxing thành `Integer`.
- **Lấy wrapper ra để dùng như primitive:** `int x = values.get(0);` — phần tử `Integer` được unbox thành `int`.
- **Toán tử số học và so sánh số:** `Integer x = 10; int y = x + 5;` thường đòi unboxing của `x` trước phép cộng.
- **Generic method:** khi gọi `<T> T echo(T value)` với một primitive, boxing tạo giá trị kiểu wrapper để đi vào vị trí `T`.

**Autoboxing không phải phép chuyển đổi tùy ý giữa mọi wrapper.** Ví dụ để tạo một `Long` từ số nguyên, viết `Long n = 10L;` hoặc `Long.valueOf(10L)`; đừng cho rằng `Integer` và `Long` thay thế trực tiếp cho nhau chỉ vì `int` có thể mở rộng thành `long`. Phép gán `Integer i = 10; Long l = i;` không hợp lệ.

Cũng không có autoboxing từ một kiểu chuỗi tùy ý thành số: `Integer.parseInt("42")` trả `int`, còn `Integer.valueOf("42")` trả `Integer`. Chuyển đổi chữ số trong chuỗi thành số là **parsing**, tách biệt với boxing.

## 4. Ví dụ Java 17 chạy độc lập: từ primitive đến generic collection

Lưu nguyên chương trình dưới tên `PrimitiveGenericsDemo.java`:

```java
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;

public class PrimitiveGenericsDemo {
    static <T> T echo(T input) {
        return input;
    }

    public static void main(String[] args) {
        List<Integer> scores = new ArrayList<>();
        scores.add(10); // int -> Integer (autoboxing)
        scores.add(20);

        int first = scores.get(0); // Integer -> int (unboxing)
        System.out.println("first: " + first);

        Integer echoed = echo(42);
        System.out.println("echo: " + echoed);

        int sum = scores.stream().mapToInt(Integer::intValue).sum();
        System.out.println("sum: " + sum);

        int[] raw = {2, 4, 6};
        List<int[]> batches = new ArrayList<>();
        batches.add(raw);
        System.out.println("array value: " + batches.get(0)[1]);

        List<int[]> oneArray = Arrays.asList(raw);
        System.out.println("asList size: " + oneArray.size());

        List<Integer> boxed = Arrays.stream(raw).boxed().toList();
        System.out.println("boxed stream: " + boxed);

        int primitiveSum = IntStream.of(raw).sum();
        System.out.println("primitive sum: " + primitiveSum);

        Integer a = Integer.valueOf(1000);
        Integer b = Integer.valueOf(1000);
        System.out.println("value equality: " + Objects.equals(a, b));

        Integer missing = null;
        System.out.println("missing is null: " + (missing == null));
        try {
            int unavailable = missing;
            System.out.println(unavailable);
        } catch (NullPointerException exception) {
            System.out.println("unboxing null: NullPointerException");
        }

        List<Integer> selected = new ArrayList<>(List.of(10, 20, 30));
        selected.remove(1); // remove(int index): xóa 20
        selected.remove(Integer.valueOf(10)); // remove(Object): xóa 10
        System.out.println("after remove: " + selected);
    }
}
```

Biên dịch/chạy trong thư mục chứa tệp (JDK 17 trở lên):

```bash
javac PrimitiveGenericsDemo.java
java PrimitiveGenericsDemo
```

Stdout theo đúng thứ tự:

```text
first: 10
echo: 42
sum: 30
array value: 4
asList size: 1
boxed stream: [2, 4, 6]
primitive sum: 12
value equality: true
missing is null: true
unboxing null: NullPointerException
after remove: [30]
```

Giải thích những chi tiết dễ bỏ qua:

1. `scores.add(10)` gọi phương thức có tham số kiểu `Integer`; trình biên dịch chèn boxing. `int first = scores.get(0)` tự unbox kết quả `Integer`. Danh sách vẫn chứa wrapper reference, không biến thành `int[]`.
2. `echo(42)` là method generic `<T> T echo(T input)`; `42` được boxing thành `Integer`, và kết quả có thể giữ dưới kiểu `Integer`. `T` không phải kiểu primitive.
3. `mapToInt(Integer::intValue)` chuyển luồng các `Integer` thành `IntStream` để cộng với `sum()`; phương thức này cho kết quả `int`. Vì tất cả phần tử không-null nên việc chuyển đổi an toàn.
4. `int[]` **là reference type**, dù phần tử là `int`. Vì vậy `List<int[]>` hợp lệ, và `batches.get(0)[1]` đọc số `4` trong phần tử mảng.
5. `Arrays.asList(raw)` nhận **một mảng `int[]` làm một phần tử**, tạo `List<int[]>` có kích thước `1`, không phải `List<Integer>` có ba số. Đây là lỗi rất thường gặp khi đưa mảng primitive vào API generic nhận varargs.
6. `Arrays.stream(raw).boxed().toList()` chuyển từng `int` thành `Integer` để tạo `List<Integer>`. `Stream.toList()` trong Java 17 trả danh sách **không hỗ trợ thay đổi cấu trúc/nội dung bằng các method sửa**; nếu cần sửa, có thể bọc `new ArrayList<>(...)`.
7. `IntStream.of(raw)` tính tổng trực tiếp trên luồng `int`, tránh phải tạo danh sách wrapper chỉ để cộng.
8. `Objects.equals(a, b)` so sánh giá trị an toàn với `null`. Không sử dụng `a == b` để kiểm tra số bằng nhau khi cả hai là `Integer`.
9. `missing == null` kiểm tra null an toàn; nhưng `int unavailable = missing;` cố gắng unbox `null` nên ném `NullPointerException`.
10. `List<Integer>.remove(1)` gọi **`remove(int index)`**, còn `remove(Integer.valueOf(10))` gọi **`remove(Object value)`**. Hai phương thức overload có ý nghĩa khác nhau và autoboxing có thể khiến người mới nhầm.

## 5. Mảng primitive là kiểu tham chiếu: ngoại lệ tưởng mâu thuẫn

Quy tắc cấm primitive làm type argument **không có nghĩa mọi thứ chứa primitive đều bị cấm**. Bản thân mảng trong Java là một đối tượng, nên các kiểu `int[]`, `double[]` và `boolean[]` đều là reference type hợp lệ khi làm đối số kiểu:

```java
List<int[]> chunks;
List<double[]> measurements;
Map<String, boolean[]> flags;
```

Đây là khai báo hợp lệ khi đặt trong ngữ cảnh và import `List`, `Map`. Tuy nhiên, `List<int[]>` là **danh sách các mảng số nguyên**, không phải danh sách số nguyên theo từng phần tử. Ví dụ `chunks.get(0)` có kiểu `int[]`, và `chunks.get(0)[0]` mới có kiểu `int`.

So sánh ba cấu trúc:

| Cấu trúc | Chứa gì? | Cách dùng điển hình |
|---|---|---|
| `int[]` | Dãy phần tử primitive `int` | Kích thước biết khi tạo, truy cập chỉ số, tính toán số |
| `List<Integer>` | Dãy tham chiếu tới wrapper `Integer` | Collection API, có khả năng thêm/xóa nếu triển khai hỗ trợ |
| `List<int[]>` | Dãy các đối tượng mảng `int[]` | Nhiều đợt dữ liệu, lô/batch số nguyên |

Khi cần đổi `int[]` thành `List<Integer>`, dùng `Arrays.stream(array).boxed().toList()` nếu chỉ cần đọc; hoặc `Arrays.stream(array).boxed().collect(java.util.stream.Collectors.toCollection(ArrayList::new))` nếu cần danh sách có thể thêm/xóa. Việc chuyển đổi này phải boxing từng phần tử; không thể ép kiểu trực tiếp `(List<Integer>) array`.

Ngược lại, từ `List<Integer>` có thể tạo `int[]` bằng `list.stream().mapToInt(Integer::intValue).toArray()`, nhưng **phần tử null sẽ gây lỗi khi chuyển thành primitive**.

## 6. Null: điểm khác biệt quan trọng giữa primitive và wrapper

Primitive `int` không thể chứa `null`; wrapper `Integer` có thể chứa `null`. Đây vừa là lợi thế biểu đạt “chưa có giá trị”, vừa là nguồn phát sinh lỗi nếu vô tình unbox.

Ví dụ minh họa, cần đặt trong method:

```java
Integer quantity = null;

// int q = quantity;         // Biên dịch được nhưng khi chạy sẽ ném NPE
boolean absent = quantity == null; // An toàn, true
boolean zero = java.util.Objects.equals(quantity, 0); // An toàn, false
```

Những biểu thức như `quantity + 1`, `quantity < 5` hoặc `int q = quantity` yêu cầu unboxing, nên khi `quantity == null` có thể ném `NullPointerException`. Việc chỉ khai báo wrapper và giữ `null` chưa ném lỗi; lỗi phát sinh khi sử dụng theo cách yêu cầu giá trị primitive.

Tình huống `Map<String, Integer>` thường gây bất ngờ:

```java
Map<String, Integer> counts = new java.util.HashMap<>();
// int count = counts.get("missing"); // get trả null -> auto-unboxing gây NPE
int count = counts.getOrDefault("missing", 0);
```

`getOrDefault` dùng giá trị mặc định nếu khóa không có mặt; với ví dụ map rỗng thì `count = 0`. **Ngoại lệ cần nhớ:** nếu map thực sự chứa ánh xạ từ khóa đó tới giá trị `null`, `getOrDefault` trả `null`, và lệnh gán vào `int` vẫn có thể ném NPE. Khi null là một trạng thái hợp lệ, hãy xử lý tường minh:

```java
Integer maybeCount = counts.get("key");
int safeCount = maybeCount == null ? 0 : maybeCount;
```

Hai ví dụ map là các đoạn minh họa cần import `Map` và đặt trong method. Thiết kế API nên mô tả rõ **null nghĩa là gì**: không có bản ghi, chưa được gán, hay là dữ liệu lỗi. Không xem `0` và `null` tự động là tương đương trong mọi miền nghiệp vụ.

## 7. So sánh wrapper: `==`, `equals()` và unboxing

Với primitive, `int a = 5; int b = 5; a == b` so sánh **giá trị số**. Với hai biến cùng kiểu wrapper reference `Integer`, `a == b` thường là **so sánh tham chiếu**, không phải kiểm tra số có bằng nhau hay không.

Java có cache cho một số giá trị boxed phổ biến (chẳng hạn `Integer.valueOf` phải tái sử dụng instance cho dải giá trị nhỏ theo quy định); cách triển khai có thể cache thêm. Do vậy mã thử `Integer a = 127; Integer b = 127; a == b` có thể cho `true` nhưng không chứng minh `==` là cách so sánh đúng cho mọi số. **Không phụ thuộc vào định danh object của wrapper để so sánh nội dung.**

Ưu tiên:

- `Objects.equals(a, b)` nếu biến wrapper có thể null và cần so sánh giá trị.
- `a.equals(b)` nếu đã chắc `a` không null.
- `a.intValue() == b.intValue()` nếu cần so sánh primitive và đã chắc cả hai không null.

**Thận trọng:** trong một biểu thức gồm wrapper và primitive, ví dụ `Integer a = 5; boolean same = a == 5;`, quy tắc chuyển đổi có thể unbox `a` để so sánh giá trị primitive. Kết quả của `==` phụ thuộc vào **kiểu của hai toán hạng**, nên không có một phát biểu “`==` với Integer luôn so sánh địa chỉ” cho mọi trường hợp. Nếu `a` là null, biểu thức có unboxing này sẽ ném NPE.

Wrapper là immutable. Ví dụ `Integer count = 1; count++;` được biên dịch thành logic gần với “unbox, cộng 1, rồi boxing/gán lại”; phép tăng không sửa đối tượng `Integer` cũ.

## 8. Autoboxing có chi phí: chọn cấu trúc dữ liệu theo nhu cầu

Wrapper hữu ích vì `List<E>`, `Map<K, V>` và các generic API chuẩn làm việc với reference type; đổi lại, một collection số thường cần **tham chiếu tới các đối tượng wrapper**, cùng với boxing/unboxing trong nhiều thao tác. Điều này có thể tốn bộ nhớ, cấp phát và CPU, đặc biệt trong vòng lặp lớn hoặc đường xử lý dữ liệu nóng. JVM có các tối ưu hóa như caching và escape analysis nên **không thể kết luận mỗi lần boxing luôn tạo một đối tượng mới**, cũng không thể suy ra mức chi phí chính xác chỉ từ cú pháp.

Chọn công cụ dựa trên tình huống:

| Nhu cầu | Lựa chọn | Ý nghĩa |
|---|---|---|
| Tích hợp `List`, `Map` hay API generic tiêu chuẩn | `Integer`, `Long`, `Double`... | Tuân theo type argument là reference type |
| Lưu dãy số nguyên kích thước cố định và thao tác theo chỉ số | `int[]` | Dữ liệu phần tử primitive, không cần wrapper cho từng phần tử |
| Tính toán chuỗi số từ mảng/nguồn số | `IntStream`, `LongStream`, `DoubleStream` | API stream chuyên biệt cho primitive |
| Một giá trị số có thể có hoặc không có | `OptionalInt`, `OptionalLong`, `OptionalDouble` nếu phù hợp | Biểu diễn giá trị tùy chọn mà không cần `Optional<Integer>` |
| Cần danh sách có thể thêm/xóa hoặc API collection rộng | `ArrayList<Integer>` hoặc collection phù hợp | Linh hoạt, nhưng sử dụng wrapper |
| Truyền nhiều mảng số theo lô | `List<int[]>` | Mỗi phần tử danh sách là một mảng primitive |

`IntStream` và `OptionalInt` là **API chuyên biệt**, không biến `List<int>` thành cú pháp hợp lệ. Ví dụ `IntStream.range(1, 5).sum()` tính tổng `1 + 2 + 3 + 4 = 10` bằng luồng số primitive. Nếu cuối cùng cần `List<Integer>`, dùng `.boxed()` để chuyển sang luồng wrapper.

Một số kiểu generic có bounded type parameter:

```java
static <N extends Number> double twice(N value) {
    return value.doubleValue() * 2;
}
```

Đây là phương thức minh họa cần đặt trong class. `twice(3)` hợp lệ do `3` được boxing thành `Integer`, vốn là subtype của `Number`; `twice(2.5)` dùng `Double`. Bound `Number` **không cho phép thay `N` bằng primitive `int`**; nó chỉ chấp nhận reference type thích hợp.

Trong tính toán cần hiệu năng cao, đo đạc với dữ liệu và JVM thực tế trước khi tối ưu. Đừng thay mọi `List<Integer>` bằng mảng nếu thiết kế cần những tính năng của collection.

## 9. Các lỗi thường gặp và cách sửa

**Lỗi 1 — Truyền primitive trực tiếp làm type argument.**

```java
List<int> values;       // Sai cú pháp tham số kiểu
List<Integer> values;   // Đúng (viết như phương án thay thế, không khai báo hai lần cùng scope)
```

Cách sửa: dùng wrapper `Integer`, hoặc cân nhắc `int[]`/`IntStream` nếu thao tác thuần số. Tương tự, `Map<String, boolean>` phải đổi thành `Map<String, Boolean>`.

**Lỗi 2 — Nghĩ rằng `List<Integer>` chứa trực tiếp các ô `int`.** Danh sách lưu các tham chiếu theo kiểu `Integer`; boxing/unboxing được áp dụng khi biên dịch tại các ngữ cảnh cần thiết. Sự thuận tiện cú pháp không xóa khác biệt runtime.

**Lỗi 3 — Unbox `null` mà không kiểm tra.** `Integer count = null; int n = count;` biên dịch được nhưng ném `NullPointerException` khi chạy. Kiểm tra null hoặc thiết kế API tránh giá trị null không mong muốn.

**Lỗi 4 — Dùng `==` để so sánh hai wrapper.** Có thể nhận kết quả phụ thuộc cache và identity của object. Dùng `Objects.equals(left, right)` cho so sánh null-safe hoặc `.equals()` khi đối tượng chắc chắn khác null.

**Lỗi 5 — Chuyển nhầm `Arrays.asList(intArray)` thành danh sách số.** Với `int[] array = {1, 2, 3};`, `Arrays.asList(array)` tạo **một phần tử kiểu `int[]`**, không phải ba `Integer`. Dùng `Arrays.stream(array).boxed().toList()` khi muốn một wrapper cho từng giá trị.

**Lỗi 6 — Nhầm `List<Integer>.remove(1)` là xóa số `1`.** Lời gọi tham số primitive `int` chọn `remove(int index)`; nếu muốn xóa giá trị bằng `1`, dùng `remove(Integer.valueOf(1))` (overload `remove(Object)`). Cẩn thận chỉ số ngoài phạm vi.

**Lỗi 7 — Cho rằng collection wrapper nhận mọi kiểu số tương thích nhau.** `List<Integer>` không phải `List<Long>`, và `Integer` không tự chuyển thành `Long` qua boxing. Nếu cần nhiều loại số, chọn API cho `Number` hoặc generic bound phù hợp; nếu cần chuyển kiểu, thực hiện chuyển đổi có chủ đích.

**Lỗi 8 — Quên tính không sửa được của `Stream.toList()`.** Danh sách trả về từ `Arrays.stream(raw).boxed().toList()` không cho `add`, `remove`, `set`. Nếu cần sửa, khởi tạo `new ArrayList<>(result)` hoặc dùng collector tạo `ArrayList`.

**Lỗi 9 — Dùng `Optional<Integer>` cho mọi phép toán số dù không cần API generic.** Trong những nơi cần một số tùy chọn, `OptionalInt` có thể diễn đạt phù hợp hơn và tránh bọc phần tử bằng `Integer`. Tuy nhiên `OptionalInt` không thay thế mọi nơi yêu cầu `Optional<T>` hay `List<T>`: mỗi kiểu có API khác nhau.

**Lỗi 10 — Cho rằng `int[]` bị cấm trong generics vì các phần tử của nó là primitive.** Bản thân mảng là reference type, nên `List<int[]>` hợp lệ. Nhưng đó là danh sách *mảng*, không phải danh sách *số*.

## 10. Câu hỏi tự kiểm tra có đáp án

1. **Vì sao `List<int>` không hợp lệ?** Vì `int` là primitive, còn đối số kiểu của generic API Java 17 phải là reference type. Dùng `List<Integer>` khi cần collection.
2. **`Integer x = 7;` có yêu cầu `new Integer(7)` không?** Không. Compiler tự áp dụng boxing thích hợp; trong code Java mới ưu tiên cú pháp đơn giản hoặc `Integer.valueOf`, tránh gọi constructor wrapper cũ.
3. **`List<Integer> list = new ArrayList<>(); list.add(5);` xảy ra chuyển đổi gì?** Giá trị `5` được autobox thành `Integer` để gọi `add`.
4. **`int number = list.get(0);` xảy ra gì?** Kết quả `Integer` được unbox thành `int`. Nếu phần tử là `null`, có nguy cơ NPE.
5. **`List<int[]>` hợp lệ không?** Có. `int[]` là kiểu tham chiếu đến một mảng, dù từng phần tử trong mảng là primitive.
6. **`Arrays.asList(new int[]{1, 2})` có bao nhiêu phần tử?** Một: phần tử duy nhất là đối tượng `int[]`; không tự boxing từng giá trị của mảng.
7. **Muốn biến `int[]` thành `List<Integer>` cần gì?** `Arrays.stream(array).boxed().toList()` là cách ngắn trong Java 17. Nếu cần sửa danh sách, tạo `new ArrayList<>(...)`.
8. **`Integer maybe = null; boolean b = maybe == null;` có lỗi không?** Không; phép so sánh với `null` ở đây không unbox. Nhưng `maybe == 0` có thể ném NPE vì phải unbox.
9. **Tại sao `Integer a = 1000; Integer b = 1000; a == b` không nên dùng để kiểm tra giá trị?** Hai wrapper có thể là hai tham chiếu khác nhau. Dùng `Objects.equals(a, b)`.
10. **Khi xử lý dữ liệu số lớn, có lựa chọn nào ngoài `List<Integer>`?** Có: `int[]`, `IntStream`, hoặc cấu trúc chuyên biệt phù hợp; đo hiệu năng thực tế trước khi quyết định.

Ghi nhớ: **primitive cung cấp giá trị số/logic cơ bản; wrapper đưa giá trị đó vào thế giới đối tượng và generic API; autoboxing/unboxing kết nối hai thế giới nhưng có thể phát sinh null lỗi và chi phí.** Nhìn thấy `List<Integer>`, hãy nhớ đó là generic của một **reference type**, dù code truyền vào thường viết gọn như số primitive.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="generics-syntax-007">Giá trị null, giới hạn thao tác trên T và kiểu Object mặc định</a>

<details>
<summary>Xem chi tiết</summary>

# Giá trị null, giới hạn thao tác trên T và kiểu Object mặc định trong Java Generics

## 1. Mục tiêu

Khi viết **class Box<T>** hoặc **static <T> T identity(T value)**, ta chưa chỉ rõ T là String, Integer, Number hay kiểu nào khác. Đây là lý do Java chỉ cho phép một số thao tác chắc chắn hợp lệ với mọi kiểu mà T có thể đại diện.

Ba câu hỏi nền tảng của bài học:

1. **T có thể nhận null không?** Có. Các type arguments của generics Java thông thường là kiểu tham chiếu, và null có thể được gán cho biến kiểu tham chiếu tương ứng; không có cam kết “không null” mặc định.
2. **Với T không có ràng buộc tường minh, được gọi những phương thức nào?** Chủ yếu là các phương thức khả dụng từ Object, vì T có cận trên ngầm định là Object. Không thể giả định T có API của String, Number hoặc một interface riêng.
3. **T có phải chính là Object không?** Không. T là một **biến kiểu** (*type variable*) giữ quan hệ kiểu giữa nhiều vị trí trong khai báo. Cận trên Object cho biết các thao tác tối thiểu được phép; nó không thay T bằng Object trong hệ thống kiểm tra kiểu ở mã nguồn.

Bài dùng Java 17+; các chương trình có nhãn tệp và lệnh chạy là ví dụ độc lập, còn đoạn được ghi “minh họa lỗi biên dịch” cố ý không biên dịch.

## 2. T không ràng buộc có cận trên Object mặc định

Xét khai báo:

~~~java
class Box<T> {
    private final T value;

    Box(T value) {
        this.value = value;
    }

    T get() {
        return value;
    }
}
~~~

Trong **class Box<T>**, ký hiệu T khai báo một type parameter **không có bound tường minh** (*unbounded type parameter*). Về cận trên, Java hiểu tương đương với:

~~~java
class Box<T extends Object> {
    private final T value;

    Box(T value) {
        this.value = value;
    }

    T get() {
        return value;
    }
}
~~~

Ở đây **extends Object** là cận trên tường minh; viết ra cũng không làm T trở thành Object. Java cho phép đối số kiểu thuộc một kiểu tham chiếu bất kỳ tương thích với cận, ví dụ String, Integer, StringBuilder, một interface type, một array type hoặc một parameterized type. **Box<String>** và **Box<Integer>** là hai cách dùng hợp lệ của cùng khai báo.

Ví dụ sử dụng trong một phương thức:

~~~java
Box<String> text = new Box<>("hello");
Box<Integer> count = new Box<>(42);
Box<int[]> numbers = new Box<>(new int[] { 1, 2, 3 });

String s = text.get();
Integer n = count.get();
int[] a = numbers.get();
~~~

Ngược lại, **Box<int>** không hợp lệ: int là primitive type, không thể trực tiếp làm type argument của generic class. Dùng kiểu wrapper **Integer** hoặc một kiểu tham chiếu khác.

**Cận trên Object mặc định** có hai ý nghĩa cần tách:

- **Ở nguồn Java:** compiler chỉ có thể tin rằng mọi giá trị T đều tương thích với Object và có những API bảo đảm bởi cận trên đó. Trong một cách sử dụng cụ thể Box<String>, compiler vẫn biết get() có kiểu tĩnh String.
- **Trong việc hiện thực generics:** unbounded T thường bị *type erasure* thành Object trong bytecode. Việc xóa kiểu phục vụ khả năng tương thích runtime; nó không xóa kiểm tra kiểu của source code trước khi biên dịch.

Một generic declaration không tự cung cấp hành vi chuyên biệt cho T. Muốn compiler cho phép gọi một phương thức của Number hoặc CharSequence, phải khai báo bound thích hợp hoặc cung cấp một thao tác bên ngoài cho T.

## 3. Null với type parameter T

Trong Java, **null** là giá trị tham chiếu đặc biệt biểu thị không trỏ đến đối tượng. Null có thể được chuyển đổi sang một kiểu tham chiếu, trong đó có kiểu biểu diễn bởi T trong generics thông thường.

~~~java
class Holder<T> {
    T value;

    Holder(T value) {
        this.value = value;
    }

    void clear() {
        value = null; // Hợp lệ với T là kiểu tham chiếu
    }

    boolean isNull() {
        return value == null;
    }
}
~~~

Sau đây đều có thể biên dịch khi class Holder đã được khai báo:

~~~java
Holder<String> name = new Holder<>(null);
Holder<Integer> size = new Holder<>(null);
Holder<Number> amount = new Holder<>(null);

String text = name.value;   // text nhận null
Integer number = size.value; // number nhận null
~~~

Dù type argument là Integer, giá trị **null** không phải là một số nguyên 0. Integer là wrapper/reference type, và mọi tham chiếu Integer vẫn có thể null.

Giới hạn bằng **extends** không mặc nhiên loại trừ null:

~~~java
class NumberHolder<T extends Number> {
    T value;

    NumberHolder(T value) {
        this.value = value;
    }
}

// Trong thân phương thức:
// NumberHolder<Integer> n = new NumberHolder<>(null); // Hợp lệ
~~~

Bound Number chỉ giới hạn **kiểu** mà T được phép biểu diễn. Nó không cam kết **giá trị thực tế** luôn tồn tại. Tương tự, **<T extends Comparable<T>>**, **<T extends CharSequence>** hay **<T extends Runnable>** không tự tạo ra một ràng buộc non-null.

### Truyền null vào generic method

Một generic method có thể nhận null làm đối số:

~~~java
static <T> T echo(T input) {
    return input;
}
~~~

Lời gọi **String result = echo(null)** có thể hợp lệ; compiler dùng ngữ cảnh kiểu đích để suy luận T phù hợp và kết quả nhận được là null. Cũng có thể chỉ rõ kiểu để diễn đạt ý định:

~~~java
String name = GenericNullDemo.<String>echo(null);
~~~

Dòng trên minh họa lời gọi tường minh khi **echo** là static method có thể truy cập trong class tên GenericNullDemo. Dấu **<String>** ở lời gọi là method type argument; **<T>** trước kiểu trả về trong khai báo là type parameter.

Không nên giả định null tự cho biết một kiểu cụ thể duy nhất. Trong biểu thức có overload hoặc type inference phức tạp, null có thể khiến việc chọn phương thức trở nên mơ hồ hoặc khiến compiler cần thông tin kiểu bổ sung. Khi đó, kiểu biến đích, type witness hoặc cast có thể giúp làm rõ; giải pháp tùy chữ ký đang dùng.

## 4. Kiểm tra null an toàn trên T

Do T là kiểu tham chiếu, ta có thể so sánh với null bằng **==** hoặc **!=**:

~~~java
static <T> boolean missing(T value) {
    return value == null;
}

static <T> boolean present(T value) {
    return value != null;
}
~~~

Hai phép so sánh này không cần bound. Khi biến T không null, **==** giữa hai tham chiếu kiểm tra chúng có trỏ đến cùng đối tượng hay không; nó **không** tương đương với so sánh nội dung bằng equals.

Kiểu sử dụng phổ biến khi cần đọc thuộc tính của T:

~~~java
static <T> String describe(T value) {
    if (value == null) {
        return "(không có giá trị)";
    }
    return value.getClass().getSimpleName() + ": " + value;
}
~~~

Nhánh if tránh gọi **getClass()** trên null. Biểu thức nối String với value dùng chuyển đổi sang chuỗi theo ngữ nghĩa string concatenation; không chứng minh T có bất kỳ phép toán số học nào.

Nếu cần so sánh nội dung và cả hai giá trị có thể null, dùng **Objects.equals(a, b)**:

~~~java
import java.util.Objects;

class Equality {
    static <T> boolean same(T left, T right) {
        return Objects.equals(left, right);
    }
}
~~~

**Objects.equals** trả true khi cả hai đều null, false khi chỉ một bên null, còn khi cả hai khác null thì gọi equals theo hợp đồng của đối tượng bên trái. Nó tránh NullPointerException do trực tiếp gọi **left.equals(right)** khi left có thể null.

Lưu ý rằng equals(Object) không có chữ ký generic bảo đảm hai đối số thuộc cùng kiểu; type parameter T trong ví dụ same thể hiện mối quan hệ của hai tham số ở mức suy luận của compiler, nhưng Object.equals(Object) vẫn chấp nhận bất kỳ Object nào.

### Null khác với “không có phần tử”

Một generic container tự thiết kế có thể dùng null như tín hiệu “chưa có dữ liệu”, nhưng đây là **quy ước API**, không phải quy tắc của generics.

~~~java
final class Slot<T> {
    private T item;

    void put(T value) {
        item = value;
    }

    boolean hasNonNullValue() {
        return item != null;
    }
}
~~~

Nếu API cần phân biệt **“chưa được gán”** với **“đã gán giá trị null”**, chỉ nhìn item == null là không đủ. Phải thêm cờ trạng thái hoặc dùng một mô hình biểu diễn có/không có giá trị phù hợp. Ví dụ:

~~~java
final class NullableSlot<T> {
    private boolean assigned;
    private T value;

    void put(T value) {
        this.value = value;
        this.assigned = true;
    }

    boolean isAssigned() {
        return assigned;
    }

    T get() {
        if (!assigned) {
            throw new IllegalStateException("Chưa gán");
        }
        return value;
    }
}
~~~

Một NullableSlot đã gọi put(null) vẫn có isAssigned() == true và get() trả null; nó khác trạng thái chưa từng gọi put. Đừng nhầm khả năng lưu null của generic type với chính sách biểu diễn dữ liệu của một container.

## 5. Những thao tác Object mặc định cho T

Với **T không bound tường minh**, các thành viên public của Object có thể được gọi qua biến T nếu thỏa điều kiện truy cập. Những phương thức thường hữu ích nhất là **toString()**, **equals(Object)**, **hashCode()** và **getClass()**.

~~~java
static <T> String asText(T value) {
    return value.toString();
}

static <T> boolean matches(T value, Object other) {
    return value.equals(other);
}

static <T> int hash(T value) {
    return value.hashCode();
}

static <T> Class<?> runtimeClass(T value) {
    return value.getClass();
}
~~~

Những phương thức này đều **có thể biên dịch** với T không bound. Tuy nhiên, nếu truyền null thì từng phương thức trên sẽ ném **NullPointerException** tại thời điểm gọi. “Phương thức được phép gọi trên T” là vấn đề **kiểu tĩnh**; “receiver có null không” là vấn đề **giá trị runtime**.

Object còn có các phương thức public liên quan tới đồng bộ như wait() và notify(), nhưng đây không phải API nên dùng chỉ vì T có cận Object. Các phương thức protected như clone() không tự trở thành callable công khai ở mọi ngữ cảnh; giới hạn truy cập Java vẫn áp dụng.

Bảng thao tác mẫu:

| Biểu thức trong nơi có T không bound | Biên dịch? | Điều kiện/ý nghĩa |
| --- | --- | --- |
| value == null | Có | Kiểm tra tham chiếu |
| value != null | Có | Kiểm tra tham chiếu |
| Object o = value | Có | T có cận trên Object |
| String s = value.toString() | Có | Object có toString(); null gây NPE |
| int h = value.hashCode() | Có | Object có hashCode(); null gây NPE |
| boolean b = value.equals(other) | Có | Object có equals(Object); null gây NPE |
| Class<?> c = value.getClass() | Có | Object có getClass(); null gây NPE |
| value.length() | Không | Object không khai báo length() |
| value.intValue() | Không | Object không khai báo intValue() |
| value.compareTo(other) | Không | Object không khai báo compareTo() |
| value + 1 | Không với phép cộng số tổng quát | Không biết T là kiểu số |
| new T() | Không | Không được khởi tạo trực tiếp type variable |
| T.class | Không | Type variable không dùng trong class literal |
| new T[3] | Không | Generic array creation |
| value instanceof T | Không | T thông thường không reifiable ở runtime |

**getClass()** trả về class thật của đối tượng tại runtime (khi khác null), không phải thông tin chắc chắn về type argument T của cách khai báo biến. Ví dụ khi một T thực tế chứa ArrayList<String>, getClass() trả class ArrayList; không thể suy ra từ đó riêng type argument String đã dùng tại nguồn.

### toString và String.valueOf khác nhau khi null

**value.toString()** sẽ ném NullPointerException nếu value là null. Trong khi đó **String.valueOf(value)** là lựa chọn null-safe: nhận một tham chiếu Object và trả chuỗi **"null"** khi đối số null.

~~~java
static <T> String safeToText(T value) {
    return String.valueOf(value);
}
~~~

Nếu cần nhãn riêng khi vắng dữ liệu, hãy kiểm tra:

~~~java
static <T> String display(T value) {
    return value == null ? "(trống)" : value.toString();
}
~~~

Đừng lạm dụng toString() cho logic nghiệp vụ hay phép so sánh: nó chủ yếu phục vụ biểu diễn chuỗi và có thể được override với định dạng không ổn định.

## 6. Vì sao compiler không cho gọi API của String hoặc Number trên T?

Xem mã cố ý lỗi:

~~~java
// Minh họa lỗi biên dịch; không lưu thành một chương trình để chạy.
class InvalidOperations<T> {
    int size(T value) {
        return value.length();    // Lỗi: T có thể không phải CharSequence
    }

    double convert(T value) {
        return value.doubleValue(); // Lỗi: T có thể không phải Number
    }

    T plus(T a, T b) {
        return a + b;             // Lỗi: không có phép cộng số học tổng quát cho T
    }
}
~~~

Ngay cả khi **chỉ định dùng InvalidOperations<String>**, class phải tự hợp lệ với **mọi type argument** đáp ứng bound của T. Không thể biên dịch một thân generic class chỉ dựa vào ý định sẽ dùng với một subtype duy nhất.

Tương tự, trình biên dịch không suy ra phương thức từ cách đặt tên T. **T** không phải viết tắt của “Text”, **N** không tự mang Number, và **V** không cho phép phép cộng. Chỉ có khai báo kiểu/bound và các phương thức được bảo đảm bởi chúng mới quyết định thao tác hợp lệ.

Một trường hợp đặc biệt cần đọc đúng: phép nối chuỗi **value + "!"** có thể biên dịch với T vì đây là **string concatenation** có toán hạng String, không phải phép cộng số học trên T. Nó không chứng minh value hỗ trợ toán tử cộng.

### Cách 1: thêm bound phù hợp

Muốn gọi **length()** trên một chuỗi ký tự tổng quát:

~~~java
static <T extends CharSequence> int lengthOf(T value) {
    return value.length();
}
~~~

CharSequence khai báo length(), nên compiler biết T có phương thức đó. Chẳng hạn String và StringBuilder đều implement CharSequence.

Muốn đọc biểu diễn số:

~~~java
static <T extends Number> double doubled(T value) {
    return value.doubleValue() * 2.0;
}
~~~

Number cung cấp doubleValue(); lấy về double rồi nhân 2.0 là hợp lệ. Nhưng **value + value** vẫn không phải toán tử generic hợp lệ: bound Number không biến T thành primitive type và không định nghĩa overload toán tử +.

Muốn so sánh theo thứ tự với cùng kiểu T:

~~~java
static <T extends Comparable<T>> T max(T left, T right) {
    return left.compareTo(right) >= 0 ? left : right;
}
~~~

T phải implement Comparable<T>, vì vậy compiler cho phép gọi compareTo(T). **max("a", "z")** hợp lệ với String, còn một class không implement Comparable phù hợp sẽ không thể làm type argument.

### Cách 2: đưa hành vi vào như một tham số

Không phải kiểu nào cũng có thứ tự “tự nhiên”. Nếu không muốn ràng buộc T implement Comparable, hãy nhận **Comparator<? super T>**:

~~~java
static <T> T maxBy(T left, T right,
                   java.util.Comparator<? super T> comparator) {
    return comparator.compare(left, right) >= 0 ? left : right;
}
~~~

Comparator được truyền vào cung cấp hành vi so sánh; bản thân T vẫn không bound. Đây là một cách thiết kế generic API thường linh hoạt hơn khi cùng một kiểu có nhiều tiêu chí so sánh (độ dài, tên, thứ tự ưu tiên).

Cũng có thể nhận **Function<T, R>**, **Predicate<T>**, **Supplier<T>**, hoặc interface do chính bạn định nghĩa để cung cấp những thao tác mà Object không có. Sự lựa chọn giữa bound và truyền strategy phụ thuộc quan hệ thật của API.

### Cách 3: nhiều bound khi cần kết hợp API

~~~java
static <T extends Number & Comparable<T>> T largerNumber(T first, T second) {
    return first.compareTo(second) >= 0 ? first : second;
}
~~~

T phải là subtype của Number **và** implement Comparable<T>. Number là class bound, đứng trước interface bound. Trong thân có thể dùng cả **doubleValue()** và **compareTo()**. **&** nối bounds; khác dấu phẩy giữa nhiều type parameters.

Khi cận là interface duy nhất:

~~~java
static <T extends Runnable> void runTask(T action) {
    action.run();
}
~~~

Java vẫn dùng từ khóa **extends**, không dùng **implements** trong khai báo type parameter. Dù có bound, **action có thể null** và lời gọi action.run() sẽ NPE.

## 7. T không phải Object: quan hệ kiểu mạnh hơn Object

Phân biệt ba chữ ký:

~~~java
static Object passObject(Object value) {
    return value;
}

static <T> T passGeneric(T value) {
    return value;
}

static <T> Object passGenericAsObject(T value) {
    return value;
}
~~~

- **passObject(Object)** nhận Object và chỉ bảo đảm kết quả tĩnh Object. Khi gọi với String, code gọi sau thường cần cast nếu muốn nhận String.
- **passGeneric(T)** biểu diễn **cùng một T** ở đầu vào và đầu ra. Khi compiler suy luận T là String, giá trị trả về có kiểu tĩnh String mà không cần cast thủ công tại nơi gọi.
- **passGenericAsObject(T)** dùng T ở tham số nhưng **đã làm mất quan hệ kiểu ở kiểu trả về** vì khai báo Object. Đó thường là thiết kế dư thừa nếu T không đóng góp vào quan hệ nào khác.

Ví dụ ở nơi gọi:

~~~java
// Những phương thức bên trên nằm trong class có thể gọi được.
Object a = passObject("Java");
String b = passGeneric("Java");
// String c = passObject("Java"); // Compile-time error: Object không tự gán cho String
~~~

Đặc biệt, từ cận trên Object **không** được suy ra có thể gán mọi Object vào một biến T bất kỳ:

~~~java
// Minh họa compile-time error:
static <T> T wrong() {
    Object any = new Object();
    return any; // Lỗi: Object không bảo đảm là T
}
~~~

Với một lời gọi mà T được chọn là String, Object tùy ý không nhất thiết là String. Gán **T vào Object** là an toàn; gán **Object vào T** đòi hỏi kiểm tra/chuyển kiểu phù hợp. Nếu buộc dùng cast không kiểm tra từ Object sang T, có thể nhận cảnh báo unchecked và phát sinh lỗi ở runtime. Tránh dùng cast để che lấp sự thiếu vắng quan hệ kiểu.

Một nhầm lẫn tương tự là gọi **List<T>** giống **List<Object>**. Khi T là String, List<String> không phải subtype của List<Object> vì các generic types như List là **invariant** theo đối số kiểu. Dùng **List<?>** hoặc bounded wildcard khi muốn thể hiện linh hoạt nơi sử dụng, thay vì tự ý thay T bằng Object.

## 8. Giá trị null và các phép gọi dẫn đến NullPointerException

Giả sử T là Integer:

~~~java
static <T extends Number> double asDouble(T input) {
    return input.doubleValue();
}
~~~

Gọi **asDouble(null)** vẫn có thể biên dịch nhưng sẽ NPE tại **input.doubleValue()**. Việc thêm extends Number cung cấp API, không kiểm tra giá trị runtime.

Với giá trị wrapper có auto-unboxing, lỗi còn có thể xảy ra ngay cả khi code không trực tiếp gọi phương thức:

~~~java
Integer wrapped = null;
// int primitive = wrapped; // Biên dịch được nhưng runtime NPE khi unboxing
~~~

Tương tự, **Box<Integer>** có thể chứa null; lấy Integer ra rồi gán cho int có thể NPE do unboxing. Không nên đánh đồng “generic type argument là Integer” với “luôn có số nguyên hợp lệ”.

**Một số cách bảo vệ khi sử dụng T:**

- Kiểm tra **if (value == null)** trước khi truy cập thành viên hoặc unboxing.
- Dùng **Objects.requireNonNull(value, "value")** tại ranh giới API nếu contract yêu cầu có giá trị. Hàm này trả lại chính tham chiếu khi không null, còn khi null ném NullPointerException với thông điệp.
- Nếu null là một trạng thái hợp lệ, mô tả rõ contract và dùng điều kiện để xử lý nó. Không nên âm thầm áp dụng quy tắc non-null cho mọi generic API.
- Có thể dùng **Optional<T>** ở ngữ cảnh biểu diễn “có/không có giá trị” phù hợp, nhưng Optional không cho phép chính giá trị bên trong là null. **Optional.ofNullable(value)** biến null thành Optional.empty().
- Nếu yêu cầu null-safety ở thời gian biên dịch, Java 17 chuẩn không tự áp đặt điều đó cho mọi T; có thể dùng các annotation/tool phân tích nullness của dự án và tuân theo hợp đồng của framework, nhưng chúng không phải phép bảo đảm tự động chỉ nhờ cú pháp <T>.

### Hàm trả về T và chính sách non-null

~~~java
static <T> T required(T value) {
    return java.util.Objects.requireNonNull(value, "value");
}

static <T> T orElse(T value, T fallback) {
    if (value != null) {
        return value;
    }
    return java.util.Objects.requireNonNull(fallback, "fallback");
}
~~~

**required** giữ kiểu kết quả T mà đồng thời kiểm tra đầu vào khác null. **orElse** trả value nếu có, nếu không thì trả fallback và yêu cầu fallback khác null. Hai phương thức thể hiện chính sách rõ ràng trong runtime; type parameter T vẫn giữ quan hệ kiểu tĩnh giữa input và output.

Với tham số kiểu không bound, **Objects.requireNonNull** là một công cụ đặc biệt thuận tiện: nó được khai báo dạng generic và giữ nguyên kiểu tĩnh của biểu thức sau kiểm tra, không cần ép kiểu về T.

## 9. Ví dụ chạy độc lập: null, Object và quan hệ kiểu T

Lưu thành **NullAndObjectDemo.java**:

~~~java
import java.util.Objects;

public class NullAndObjectDemo {
    static final class Box<T> {
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

        boolean isEmpty() {
            return value == null;
        }
    }

    static <T> String describe(T value) {
        if (value == null) {
            return "(null)";
        }
        return value.getClass().getSimpleName() + ":" + value.toString();
    }

    static <T> boolean same(T left, T right) {
        return Objects.equals(left, right);
    }

    static <T> T echo(T value) {
        return value;
    }

    public static void main(String[] args) {
        Box<String> greeting = new Box<>(null);
        System.out.println(greeting.isEmpty());
        System.out.println(describe(greeting.get()));

        greeting.set("Java");
        String message = echo(greeting.get());
        System.out.println(describe(message));

        System.out.println(same("ok", "ok"));
        System.out.println(same(null, "ok"));
        System.out.println(describe(21));
    }
}
~~~

Biên dịch/chạy bằng JDK 17+:

~~~bash
javac -encoding UTF-8 NullAndObjectDemo.java
java NullAndObjectDemo
~~~

Stdout:

~~~text
true
(null)
String:Java
true
false
Integer:21
~~~

Giải thích:

1. Box<T> không yêu cầu có giá trị ban đầu. Box<String>(null) hợp lệ; isEmpty kiểm tra chính xác **value == null**.
2. describe được gọi với null và trả chuỗi **"(null)"**, tránh getClass()/toString() trên receiver null.
3. Sau set("Java"), echo là generic method trả cùng kiểu T đã nhận, nên compiler cho phép gán kết quả cho String.
4. Các lời gọi same dùng Objects.equals, có thể xử lý null ở một hoặc cả hai phía.
5. Với literal 21, kiểu generic được suy luận phù hợp với Integer do autoboxing; describe nhận một đối tượng Integer, gọi Object.getClass() và Object.toString() (được Integer override), cho kết quả **Integer:21**.

**Tính runnable:** tệp chỉ có một public class trùng tên file; các nested classes và methods đều được khai báo đầy đủ. Stdout không phụ thuộc hashCode hay định dạng ngẫu nhiên.

## 10. Ví dụ chạy độc lập: cần bound để gọi phương thức chuyên biệt

Lưu thành **BoundedOperationsDemo.java**:

~~~java
import java.util.Comparator;

public class BoundedOperationsDemo {
    static <T extends CharSequence> int characters(T value) {
        return value.length();
    }

    static <T extends Number> double twice(T value) {
        return value.doubleValue() * 2.0;
    }

    static <T extends Number & Comparable<T>> T maximum(T a, T b) {
        return a.compareTo(b) >= 0 ? a : b;
    }

    static <T> T chooseLarger(T first, T second,
                              Comparator<? super T> comparator) {
        return comparator.compare(first, second) >= 0 ? first : second;
    }

    public static void main(String[] args) {
        System.out.println(characters("Java"));
        System.out.println(characters(new StringBuilder("generic")));
        System.out.println(twice(3));
        System.out.println(maximum(5, 9));
        System.out.println(chooseLarger("ant", "tiger",
                Comparator.comparingInt(String::length)));
    }
}
~~~

Biên dịch/chạy:

~~~bash
javac -encoding UTF-8 BoundedOperationsDemo.java
java BoundedOperationsDemo
~~~

Stdout:

~~~text
4
7
6.0
9
tiger
~~~

Phân tích:

- **T extends CharSequence** cho phép gọi length() trên cả String và StringBuilder.
- **T extends Number** cho phép gọi doubleValue() và phép nhân trên giá trị double thu được; bản thân T vẫn không dùng được trực tiếp trong phép nhân generic.
- **T extends Number & Comparable<T>** cho phép compareTo; Integer đáp ứng cả hai cận.
- **chooseLarger** không ràng buộc T phải Comparable vì Comparator được đưa vào như một tham số; Comparator so sánh chuỗi theo độ dài và chọn tiger.
- Cả bốn phương thức vẫn có thể gặp NPE nếu nhận null tại nơi gọi phương thức receiver chưa kiểm tra. Trong ví dụ chạy, mọi đối số được sử dụng đều khác null.

## 11. Ví dụ chạy độc lập: bắt buộc có giá trị

Lưu thành **NullPolicyDemo.java**:

~~~java
import java.util.Objects;
import java.util.Optional;

public class NullPolicyDemo {
    static <T> T requireValue(T value) {
        return Objects.requireNonNull(value, "value");
    }

    static <T> T withFallback(T value, T fallback) {
        return value != null
                ? value
                : Objects.requireNonNull(fallback, "fallback");
    }

    static <T> Optional<T> optionalOf(T value) {
        return Optional.ofNullable(value);
    }

    public static void main(String[] args) {
        System.out.println(requireValue("ready"));
        System.out.println(withFallback(null, "guest"));
        System.out.println(optionalOf(null).isEmpty());
        System.out.println(optionalOf("Java").orElse("other"));

        try {
            requireValue(null);
        } catch (NullPointerException ex) {
            System.out.println(ex.getMessage());
        }
    }
}
~~~

Lệnh:

~~~bash
javac -encoding UTF-8 NullPolicyDemo.java
java NullPolicyDemo
~~~

Stdout:

~~~text
ready
guest
true
Java
value
~~~

**requireValue** và **withFallback** giữ lại T ở kiểu trả về; Objects.requireNonNull từ chối null thay vì giả định T đã được kiểm tra bởi hệ thống generic. **Optional.ofNullable(null).isEmpty()** trả true, còn Optional.ofNullable("Java") giữ chuỗi "Java". Thông báo exception được xác định rõ trong source qua **Objects.requireNonNull(value, "value")**.

## 12. Type erasure: Object mặc định không có nghĩa runtime biết T

Với generic type không bound, compiler hiện thực nhiều thao tác theo erasure; cận ngầm định Object là một nguyên nhân khiến bytecode thường xử lý tham chiếu kiểu Object. Tuy nhiên, Java compiler cũng chèn cast cần thiết tại các nơi sử dụng có thông tin type arguments.

Ví dụ:

~~~java
class Container<T> {
    T item;

    Container(T item) {
        this.item = item;
    }

    T get() {
        return item;
    }
}

// Ở nơi gọi:
// Container<String> x = new Container<>("hello");
// String s = x.get();
~~~

Về ý tưởng, compiler kiểm tra ở nguồn rằng get() trả đúng String trong cách sử dụng Container<String>, dù mã sau erasure có thể cần cast về String khi lấy kết quả. Người lập trình không phải thêm cast trong source đúng kiểu.

Không thể dựa vào T để tự khởi tạo đối tượng hoặc tạo mảng của T:

~~~java
// Minh họa lỗi biên dịch, không chạy:
class Factory<T> {
    // T create() { return new T(); } // Lỗi
    // T[] createArray() { return new T[4]; } // Lỗi
    // Class<T> type() { return T.class; } // Lỗi
}
~~~

Nguyên nhân là T không phải một class token cụ thể có thể dùng trực tiếp trong các biểu thức trên ở runtime. Nếu cần tạo T, hãy nhận một factory hoặc Supplier<T>. Nếu cần kiểm tra kiểu runtime, có thể nhận Class<T> từ người gọi khi thiết kế API phù hợp.

Ví dụ hợp lệ với Supplier:

~~~java
import java.util.function.Supplier;

class GenericFactory<T> {
    private final Supplier<? extends T> supplier;

    GenericFactory(Supplier<? extends T> supplier) {
        this.supplier = supplier;
    }

    T create() {
        return supplier.get();
    }
}

// Trong một phương thức:
// GenericFactory<StringBuilder> factory =
//         new GenericFactory<>(StringBuilder::new);
// StringBuilder builder = factory.create();
~~~

Compiler cho phép supplier.get() được dùng làm T vì **? extends T** thể hiện kiểu kết quả có thể gán cho T. Tạo object thực tế là trách nhiệm của Supplier. Một Supplier vẫn có thể trả null nếu hợp đồng của nó cho phép; generics tự nó không cấm.

Một lỗi khác là **value instanceof T**, do kiểu T nhìn chung không có biểu diễn runtime cần thiết để kiểm tra đúng type argument. Thay vào đó, có thể kiểm tra **value instanceof String** trong những ngữ cảnh hợp lệ vì String là class cụ thể; hoặc dùng **Class<T>.isInstance(value)** nếu caller cung cấp Class<T>. Kiểm tra runtime chỉ dựa trên class cũng không thể kiểm tra đầy đủ List<String> hay các type arguments lồng nhau.

## 13. Những lỗi và ngộ nhận thường gặp

### Sai lầm 1: “T mặc định là Object nên bất kỳ Object nào cũng gán được vào T”

~~~java
// Mã minh họa lỗi:
static <T> T badReturn() {
    Object value = "text";
    return value; // Compile-time error
}
~~~

Đúng là **T có thể chuyển thành Object** (T là subtype của bound Object). Chiều ngược lại không được đảm bảo. Một lời gọi có thể yêu cầu T = Integer, trong khi Object đang chứa String.

### Sai lầm 2: “T extends Number thì T không thể null”

~~~java
static <T extends Number> double magnitude(T n) {
    return n.doubleValue();
}
~~~

**magnitude(null)** có thể biên dịch và NPE lúc chạy. Bound kiểm soát kiểu, không xác nhận receiver khác null.

### Sai lầm 3: “T có Object methods nên gọi toString trên null vẫn an toàn”

~~~java
static <T> String unsafeText(T value) {
    return value.toString();
}
~~~

Mã biên dịch, nhưng truyền null gây NPE. Dùng **String.valueOf(value)** hoặc kiểm tra trước tùy mục đích.

### Sai lầm 4: “Có thể gọi phương thức của String vì thường truyền String”

~~~java
class NotAlwaysString<T> {
    // int length(T value) { return value.length(); } // Compile-time error
}
~~~

Không có bound nên value có thể thuộc kiểu không có length(). Cần **<T extends CharSequence>** nếu muốn tính độ dài của CharSequence, hoặc khai báo phương thức nhận String nếu API thật sự chỉ thao tác với String.

### Sai lầm 5: “T có thể cộng trực tiếp như số”

~~~java
class Numeric<T extends Number> {
    // T add(T a, T b) { return a + b; } // Compile-time error
}
~~~

Number không khai báo toán tử + cho T. Phải chuyển sang kiểu số cụ thể hoặc nhận một phép cộng dưới dạng hàm; lưu ý chuyển sang double có thể mất độ chính xác với số lớn.

### Sai lầm 6: “Objects.equals(a, b) và a == b giống nhau”

Hai biểu thức khác semantics: **==** kiểm tra cùng tham chiếu hoặc null; **Objects.equals** kiểm tra bằng equals nếu thích hợp và null-safe. Với những object bằng nội dung nhưng khác instance, Objects.equals có thể trả true còn == trả false.

### Sai lầm 7: “Null và generic có thể thay nhau biểu diễn tính tùy chọn”

Một Slot<T> cho phép null chỉ chứng tỏ có thể lưu null. Nó không cho biết “chưa có dữ liệu” có phải cùng nghĩa với “giá trị được chọn là null” hay không. API phải quy định rõ trạng thái và lỗi/giá trị trả về.

### Sai lầm 8: “Giữ T là đủ để biết chính xác type arguments ở runtime”

Erasure khiến thông tin kiểu thành phần không tồn tại như một class chuyên biệt cho mỗi List<String> hay List<Integer>. Một Object chứa List không cho phép dùng getClass() để xác minh trực tiếp đối số kiểu String. Nếu cần kiểm tra phần tử, duyệt và xác nhận chúng theo chính sách phù hợp.

### Sai lầm 9: “Muốn bỏ NPE chỉ cần cast T”

Cast không làm một giá trị null biến thành object; cast null sang một reference type vẫn cho null. Nó cũng không tạo ra method mới cho unbounded T trong chữ ký generic. Dùng kiểm tra null và bound/strategy thích hợp.

### Sai lầm 10: “Object có toString nên có thể dùng toString để ép API generic làm mọi thứ”

toString chỉ tạo một dạng biểu diễn; parse, so sánh, tính toán và truy cập thuộc tính domain cần API/contract riêng. Khi thiết kế generic method, hãy khai báo giới hạn kiểu hoặc truyền hàm thao tác, thay vì dựa trên định dạng chuỗi.

## 14. Quy tắc thiết kế generic API có liên quan đến null

Không có một chính sách null duy nhất cho mọi generic API. Điều quan trọng là đưa chính sách vào contract và thực thi nhất quán:

| Yêu cầu | Cách biểu diễn |
| --- | --- |
| Cho phép không có giá trị | Chấp nhận null và xử lý rõ ràng, hoặc dùng một kiểu trạng thái thích hợp |
| Tuyệt đối yêu cầu đầu vào có giá trị | Objects.requireNonNull tại ranh giới API |
| Muốn biểu diễn có/không có giá trị rõ ràng | Optional<T> nếu hợp với kiểu API; không lưu null bên trong Optional |
| Muốn dùng phương thức của một interface | <T extends InterfaceName> |
| Muốn dùng các phương thức số | <T extends Number> hoặc API toán học cụ thể |
| Muốn so sánh nhưng T không có Comparable | Truyền Comparator<? super T> |
| Muốn tạo object T | Truyền Supplier<? extends T> hoặc factory |
| Muốn kiểm tra class cụ thể của T | Truyền Class<T> khi cần; chú ý giới hạn của type erasure |

Ví dụ về một API từ chối null ngay khi khởi tạo:

~~~java
import java.util.Objects;

class NonNullBox<T> {
    private final T value;

    NonNullBox(T value) {
        this.value = Objects.requireNonNull(value, "value");
    }

    T get() {
        return value;
    }
}
~~~

Dù T là unbounded type variable, class này xây dựng invariant riêng: sau khi constructor thành công, field value không null. Đây là bảo đảm từ **logic constructor và field final** (trong phạm vi code mẫu), không phải từ cú pháp **<T>**.

Nếu API cho phép null, cần kiểm tra trước khi gọi bất cứ instance method nào trên receiver đó. Đặc biệt, một bound như Comparable, Runnable hoặc Number cho phép compiler gọi phương thức tương ứng, nhưng receiver null vẫn dẫn đến NPE.

## 15. Tự kiểm tra

**Câu 1.** Với **class Box<T>**, T có cận trên mặc định là gì? Có được tự viết **Box<int>** không?

**Đáp án:** T có cận trên Object nếu không khai báo bound tường minh. **Box<int>** không hợp lệ; dùng Box<Integer> hoặc một kiểu tham chiếu khác.

**Câu 2.** Trong **static <T> boolean empty(T x) { return x == null; }**, phương thức có cần **T extends Object** không?

**Đáp án:** Không. T đã có cận Object ngầm định; so sánh với null hợp lệ vì T được dùng như reference type.

**Câu 3.** Vì sao **static <T> int length(T value) { return value.length(); }** không biên dịch? Cách sửa tối thiểu nếu muốn chấp nhận String và StringBuilder?

**Đáp án:** Object không có length(). Thêm bound: **static <T extends CharSequence> int length(T value) { return value.length(); }**. Vẫn phải xử lý null nếu đầu vào có thể null.

**Câu 4.** **T extends Number** có cho phép biểu thức **a + b** với hai biến T không?

**Đáp án:** Không. T là reference type variable, còn Number không định nghĩa toán tử cộng generic. Có thể dùng doubleValue() rồi cộng dưới kiểu double nếu yêu cầu cho phép.

**Câu 5.** Có thể gán **Object x = t** nếu t có kiểu T không bound? Có thể gán ngược **T t = x** một cách tổng quát không?

**Đáp án:** Có thể gán T sang Object. Không thể gán tùy ý Object sang T vì T có thể là subtype cụ thể của Object.

**Câu 6.** Tại sao **String.valueOf(value)** thường an toàn hơn **value.toString()** nếu value có thể null?

**Đáp án:** String.valueOf(Object) chuyển null thành chuỗi "null"; gọi trực tiếp toString trên receiver null ném NullPointerException.

**Câu 7.** So sánh **value == null**, **value.equals(other)** và **Objects.equals(value, other)**.

**Đáp án:** Phép == null kiểm tra thiếu tham chiếu; value.equals dùng logic equals của object nhưng gây NPE khi value null; Objects.equals null-safe và so sánh nội dung dựa trên equals khi các đối tượng khác null.

**Câu 8.** Tại sao **new T()** không hợp lệ? Nên làm gì nếu hàm generic cần tạo đối tượng mới?

**Đáp án:** Type erasure không cung cấp constructor cụ thể của T ở runtime. Nhận Supplier<T> hoặc một factory thích hợp để caller cung cấp cách tạo.

**Câu 9.** Đoạn **class Box<T extends Number> { T n; }** có thể có n == null không?

**Đáp án:** Có. Giới hạn kiểu bằng Number không biến thành bảo đảm non-null.

**Câu 10.** Khi nào nên dùng **<T extends Comparable<T>>**, khi nào nên truyền Comparator?

**Đáp án:** Dùng bound nếu API yêu cầu T có thứ tự tự nhiên qua Comparable<T>; truyền Comparator<? super T> nếu muốn cho phép so sánh tùy chiến lược hoặc với kiểu không implement Comparable phù hợp.

## 16. Ghi nhớ để áp dụng

Với type parameter **T không bound**, compiler biết tối thiểu rằng T phù hợp với **Object**. Điều này đủ cho kiểm tra null, gán từ T sang Object và gọi các phương thức Object khả dụng trên receiver không null. Nó **không** đủ để gọi length(), compareTo(), doubleValue(), tạo new T() hoặc thực hiện phép cộng số học tổng quát.

Null là một vấn đề độc lập với bounds: **T**, **T extends Number** và **T extends Comparable<T>** đều có thể nhận null khi ngữ cảnh cho phép. Nếu cần một invariant khác null, hãy thể hiện nó trong hợp đồng API và kiểm tra ở đúng ranh giới. Khi cần thao tác chuyên biệt, thêm **extends** cho đúng interface/class hoặc cung cấp một hàm/strategy làm việc với T. Giữ được sự phân biệt này giúp khai báo generics vừa linh hoạt, vừa an toàn kiểu và dễ suy luận.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="generics-syntax-008">Đọc chữ ký generic và theo dõi nơi ràng buộc từng biến kiểu</a>

<details>
<summary>Xem chi tiết</summary>

# Đọc chữ ký generic và theo dõi nơi ràng buộc từng biến kiểu trong Java

Một chữ ký generic có thể ngắn như `T get()` hoặc nhiều lớp như `<T, R> List<R> map(List<? extends T> source, Function<? super T, ? extends R> mapper)`. Điểm quyết định khi đọc không nằm ở việc thuộc tên `T`, `E`, `K`, `V`: đó chỉ là tên các **biến kiểu**. Quan trọng là xác định **nơi biến kiểu được khai báo, những vị trí nào dùng chung biến đó, giới hạn kiểu là gì và khi gọi API thì kiểu cụ thể được gán cho nó ra sao**.

Bài này hướng dẫn một phương pháp đọc chữ ký từng bước, từ generic class và generic method đến bounded type, wildcard, kiểu trả về, constructor và API Java Core. Các đoạn chương trình hoàn chỉnh dùng Java 17+; các chữ ký trích riêng được đánh dấu là mã minh họa.

## 1. Ba câu hỏi bắt buộc khi gặp một chữ ký

Với mỗi tên biến kiểu như `T`, hãy lần theo ba điểm:

1. **Khai báo ở đâu?** Tìm cặp dấu `<...>` khai báo biến kiểu: `class Box<T>`, `interface Converter<I, O>`, `static <T> T first(...)` hoặc constructor `<U> Box(U input)`. Một chữ cái `T` chỉ là type variable nếu nằm trong phạm vi khai báo hợp lệ.
2. **Ràng buộc ở những vị trí nào?** Ghi lại mọi vị trí `T` xuất hiện: trường, tham số, kiểu trả về, type argument lồng bên trong, cận của biến khác. Cùng một `T` trong cùng phạm vi đại diện cho **một lựa chọn kiểu liên kết**, không phải những kiểu được lựa chọn độc lập.
3. **Lựa chọn kiểu tại nơi sử dụng là gì?** Với `Box<String>` thì type parameter của `Box` được áp dụng như `String`. Với generic method, compiler suy luận từ đối số thực, ngữ cảnh gán và các ràng buộc, hoặc người gọi truyền type witness tường minh như `Utils.<String>identity("Java")`.

Một chữ ký thể hiện hợp đồng **compile time**. Kiểu thực tế của giá trị vẫn phải tuân thủ hợp đồng đó khi chương trình chạy; các đường đi raw/unchecked có thể phá vỡ bảo đảm tĩnh. Không cần tìm một “biến runtime tên T” trong đối tượng.

### Ví dụ mở đầu

```java
static <T> T identity(T value) {
    return value;
}
```

Chữ ký được tách thành:

- `static`: thành viên thuộc class.
- `<T>`: **khai báo biến kiểu T** dành riêng cho phương thức.
- `T` đứng trước tên `identity`: **kiểu trả về**, dùng biến kiểu đã khai báo.
- `T value`: **kiểu của tham số**, dùng chính biến kiểu đó.
- Khi gọi `String text = identity("Java");`, compiler có thể suy luận `T = String`. Khi gọi `Integer n = identity(7);`, compiler có thể suy luận `T = Integer` (giá trị `7` được boxing).

Chữ ký này nói: “phương thức nhận một `T` và trả một `T`”. Nó không bảo đảm giá trị trả về là cùng **đối tượng** với đầu vào đối với mọi cách cài đặt có chữ ký tương tự; tính đồng nhất đối tượng chỉ đúng nếu thân hàm thực hiện như ví dụ.

## 2. Phân biệt khai báo biến kiểu và truyền đối số kiểu

Hai dạng dấu ngoặc nhọn `<...>` trông tương tự nhưng có vai trò khác nhau:

| Cú pháp | Vai trò | Điều cần theo dõi |
| --- | --- | --- |
| `class Box<T>` | Khai báo type parameter của class | `T` có phạm vi trong các thành viên instance phù hợp của `Box` |
| `interface Pair<K, V>` | Khai báo hai type parameters | `K` và `V` độc lập cho đến khi chữ ký liên kết chúng |
| `Box<String>` | Sử dụng type argument cụ thể | Những vị trí `T` của `Box` được xem như `String` tại điểm dùng |
| `Map<String, Integer>` | Cung cấp hai type arguments | `K = String`, `V = Integer` |
| `<T> T read(...)` | Khai báo type parameter của method | `T` được suy luận/áp dụng riêng mỗi invocation |
| `Class<T>` | Dùng `T` làm type argument cho `Class` | `T` phải được khai báo ở phạm vi bao quanh |
| `List<?>` | Wildcard, nghĩa là một kiểu phần tử chưa biết | Không tạo một biến tên `T` cho người viết tự dùng |
| `new Box<>("a")` | Diamond inference ở constructor call | Compiler suy luận type argument từ ngữ cảnh/đối số |

Tên `T` không đặc biệt với JVM. Bạn có thể đặt tên `ITEM`, `ID`, `SOURCE` nếu rõ nghĩa và hợp cú pháp Java identifier. Quy ước thông dụng là `T` (type), `E` (element), `K` (key), `V` (value), `R` (result), `U` (kiểu bổ sung).

Nguyên tắc quan trọng: **hai khai báo có cùng tên chữ `T` nhưng ở các phạm vi khác nhau không tự ràng buộc với nhau**. Ngược lại, những lần xuất hiện của `T` trong **cùng một phạm vi khai báo** cùng chịu một tập ràng buộc. Khi tìm chỗ khai báo, đọc từ vị trí sử dụng ngược về cú pháp của phương thức, class hoặc interface bao quanh.

## 3. Cách đọc chữ ký generic class từ trái sang phải

Xét chương trình `SignatureClassDemo.java`:

```java
public class SignatureClassDemo {
    static final class Entry<K, V> {
        private final K key;
        private V value;

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

        void replace(V newValue) {
            value = newValue;
        }
    }

    public static void main(String[] args) {
        Entry<String, Integer> score = new Entry<>("Lan", 8);
        score.replace(9);

        String name = score.key();
        Integer points = score.value();

        System.out.println(name + ": " + points);
    }
}
```

Biên dịch và chạy:

```bash
javac SignatureClassDemo.java
java SignatureClassDemo
```

Kết quả `stdout`:

```text
Lan: 9
```

### Bước 1: xác định “bảng ký hiệu kiểu”

`Entry<K,V>` khai báo hai type parameters thuộc class:

| Biến kiểu | Khai báo | Những nơi ràng buộc trong class |
| --- | --- | --- |
| `K` | `Entry<K,V>` | `K key`, tham số `K key` của constructor, kiểu trả về `K` của `key()` |
| `V` | `Entry<K,V>` | `V value`, constructor `V value`, kiểu trả về `V` của `value()`, tham số `V newValue` của `replace(...)` |

### Bước 2: thay vào lời gọi cụ thể

`Entry<String, Integer>` khiến compiler kiểm tra các thành viên *như thể* chúng có kiểu:

```java
// Chỉ là hình dung về kiểu nguồn ở điểm sử dụng, không phải class mới.
Entry(String key, Integer value);
String key();
Integer value();
void replace(Integer newValue);
```

Không có bản sao mã class `EntryStringInteger` riêng. `score.replace("nine")` bị từ chối khi biên dịch. `score.replace(9)` được phép vì boxing chuyển `int` sang `Integer`.

### Bước 3: phân biệt thông tin kiểu và giá trị

`K` không phải biến lưu khóa, `V` không phải giá trị dữ liệu. `K`/`V` định nghĩa các **mối quan hệ kiểu**; `key`, `value` mới là các trường chứa dữ liệu. Một đối tượng `Entry<String,Integer>` có thể giữ các giá trị `"Lan"` và `9`, nhưng type arguments chỉ mô tả kiểu cho việc kiểm tra tại compile time.

### Bước 4: đọc constructor

`Entry(K key, V value)` dùng `K` và `V` đã khai báo ở class. Không có dấu `<K,V>` đặt trước constructor nên constructor này **không tự khai báo** cặp type parameters mới. Compiler kiểm tra constructor tương ứng với `Entry<String,Integer>` được tạo ở điểm gọi.

## 4. Cách đọc chữ ký generic method

Một generic method có type-parameter list riêng đặt **trước kiểu trả về**, sau các modifier (nếu có):

```java
public static <T> T first(java.util.List<T> values)
```

Phân tích: `public static` là modifier; `<T>` là khai báo type parameter; `T` trước `first` là kiểu trả về; `List<T>` dùng `T` để buộc phần tử đầu vào và kết quả cùng quan hệ kiểu.

### Chương trình hoàn chỉnh

Tạo `MethodSignatureDemo.java`:

```java
import java.util.List;

public class MethodSignatureDemo {
    static <T> T first(List<T> elements) {
        if (elements.isEmpty()) {
            throw new IllegalArgumentException("Empty");
        }
        return elements.get(0);
    }

    static <T> boolean contains(List<T> elements, T wanted) {
        return elements.contains(wanted);
    }

    public static void main(String[] args) {
        String a = first(List.of("A", "B"));
        Integer n = first(List.of(10, 20));

        System.out.println(a);
        System.out.println(n);
        System.out.println(contains(List.of("A", "B"), "B"));
    }
}
```

Chạy:

```bash
javac MethodSignatureDemo.java
java MethodSignatureDemo
```

`stdout`:

```text
A
10
true
```

Với `first(List.of("A", "B"))`, compiler suy luận `T` là `String`; với `first(List.of(10, 20))`, là `Integer`. Hai lời gọi không chia sẻ một “T chung toàn chương trình”. Sự ràng buộc diễn ra **riêng cho từng lần áp dụng generic method**.

Với `contains(List<T>, T)`, cùng biến `T` xuất hiện hai lần; compiler phải tìm cách suy luận type argument thỏa các ràng buộc ở lời gọi. **Không suy luận rằng compiler luôn bắt buộc cả hai biểu thức đầu vào có chính xác cùng một runtime class**: thuật toán inference có thể tìm ra supertype chung nếu chữ ký và ngữ cảnh cho phép. Nếu mục tiêu nghiệp vụ yêu cầu loại phần tử phải đồng nhất chặt chẽ, cần thiết kế API và validation phù hợp; generic inference không phải công cụ so sánh lớp runtime.

### Lỗi hay gặp: thiếu phần khai báo `<T>`

```java
// Sai nếu trong scope chưa hề có type parameter T:
// static T missing(T value) { return value; }

// Đúng:
static <T> T identity(T value) { return value; }
```

Trong generic class `Box<T>`, một **instance method** có thể dùng `T` của class mà không phải khai báo thêm. Nhưng `static` method không được sử dụng trực tiếp class-level `T` vì thành viên static thuộc class dùng chung, không thuộc một instantiation `Box<String>` nào.

## 5. Phân biệt type parameter của class và của method

Tạo `ScopeDemo.java`:

```java
public class ScopeDemo {
    static final class Holder<T> {
        private final T value;

        Holder(T value) {
            this.value = value;
        }

        T get() {
            return value;
        }

        <R> R convert(java.util.function.Function<? super T, ? extends R> f) {
            return f.apply(value);
        }

        static <U> U echo(U value) {
            return value;
        }
    }

    public static void main(String[] args) {
        Holder<Integer> holder = new Holder<>(42);

        Integer original = holder.get();
        String label = holder.convert(n -> "ID-" + n);
        Boolean yes = Holder.echo(true);

        System.out.println(original);
        System.out.println(label);
        System.out.println(yes);
    }
}
```

Chạy:

```bash
javac ScopeDemo.java
java ScopeDemo
```

`stdout`:

```text
42
ID-42
true
```

Lần theo:

- `Holder<T>` khai báo `T` của **class**.
- Trường `value`, constructor `Holder(T value)` và instance method `T get()` dùng `T` thuộc class. Với `Holder<Integer>`, compiler dùng `Integer` ở những vị trí đó.
- `<R> R convert(...)` khai báo thêm `R` thuộc **method**. Trong `convert`, `T` xuất phát từ class (với receiver này là `Integer`), còn `R` được suy luận khi gọi method (ở ví dụ là `String`).
- `Function<? super T, ? extends R>` nhận hàm có khả năng xử lý giá trị `T` và tạo giá trị tương thích với `R`. Wildcard có ngữ nghĩa riêng; `?` không phải một biến kiểu có tên.
- `static <U> U echo(U value)` khai báo `U` riêng cho static method; phương thức này không phụ thuộc type argument `Integer` của `holder`. Có thể gọi `Holder.echo(true)` và compiler suy luận `U=Boolean`.

**Câu hỏi nhanh:** `get()` và `convert(...)` dùng chung biến kiểu nào? `echo(...)` có đọc `T` của class không? Nếu có hai lần gọi `convert` lần lượt trả `String` và `Long`, `R` được chọn theo từng lời gọi hay cố định trên object? (Đáp án: chung `T` của class; `echo` không dùng `T`; `R` thuộc từng lời gọi.)

### Generic constructor cũng có biến kiểu riêng

Một constructor có thể khai báo `<U>`, khác với biến kiểu của class. Ví dụ độc lập ở mức khai báo:

```java
class Container<T> {
    final T value;

    <U> Container(U source,
                  java.util.function.Function<? super U, ? extends T> convert) {
        this.value = convert.apply(source);
    }
}
```

Với `Container<Integer> x = new Container<>("123", Integer::parseInt);`, type parameter **class** là `T=Integer`, còn type parameter **constructor** `U` có thể suy luận là `String`. Constructor chuyển `U` sang `T`, nhờ đó giữ được quan hệ kiểu giữa dữ liệu đầu vào và trường kết quả.

Không đặt `<U>` là type argument cho class nếu nó đang được khai báo trước constructor. Chỗ đứng của phần khai báo là chìa khóa để đọc đúng scope.

## 6. Một phương pháp chú giải chữ ký dài

Xét chữ ký tự thiết kế:

```java
static <S, R> java.util.List<R> map(
        java.util.List<? extends S> source,
        java.util.function.Function<? super S, ? extends R> converter)
```

Đây là **chữ ký minh họa**, chưa có thân phương thức. Có thể lập bảng trước khi đọc logic:

| Ký hiệu | Được khai báo tại | Ràng buộc xuất hiện | Vai trò |
| --- | --- | --- | --- |
| `S` | `<S,R>` của method | `? extends S`, `? super S` | Kiểu đầu vào của phép biến đổi |
| `R` | `<S,R>` của method | `? extends R`, `List<R>` | Kiểu kết quả |
| `? extends S` | Wildcard trong `List` | Phụ thuộc `S` | Danh sách nguồn cung cấp phần tử tương thích `S` |
| `? super S` | Wildcard trong `Function` | Phụ thuộc `S` | Function nhận được giá trị `S` |
| `? extends R` | Wildcard trong `Function` | Phụ thuộc `R` | Function tạo giá trị tương thích `R` |

Cách đọc bằng tiếng Việt: “Phương thức nhận danh sách chứa các giá trị có thể đọc như `S`, cùng một phép biến đổi nhận được `S` và tạo ra `R`; kết quả là danh sách `R`.”

Bắt đầu từ **kết quả và mối quan hệ** rồi mới đọc sâu wildcard. Đừng cố gán cho từng ký hiệu `?` một loại cụ thể trong đầu; wildcard là một giới hạn khả năng, còn type parameter có tên là điểm nối các vị trí liên quan.

### Đặt các loại cụ thể vào bảng

Nếu lời gọi dùng `List<Integer>` và một hàm đổi số thành chuỗi, một cách suy luận hợp lệ là `S=Integer` và `R=String`. Khi đó:

- `List<Integer>` phù hợp với `List<? extends Integer>`.
- Hàm nhận `Integer` phù hợp với `Function<? super Integer, ? extends String>`.
- Kết quả `List<R>` có kiểu `List<String>`.

Phần type inference thật có thể có nhiều ứng viên rồi chọn theo các ràng buộc và ngữ cảnh. Mô hình thay `S=Integer`, `R=String` là cách đọc một invocation cụ thể, không phải thuật toán đầy đủ của `javac`.

## 7. Bounded type parameter: ràng buộc bắt đầu từ khai báo

Type parameter có thể mang **bound**. Nhìn ví dụ:

```java
static <T extends Number> double twice(T value)
```

Đọc `<T extends Number>` trước: chỉ nhận kiểu phù hợp với `Number`, giúp thân phương thức gọi `value.doubleValue()`.

### Chương trình hoàn chỉnh

Tạo `BoundSignatureDemo.java`:

```java
public class BoundSignatureDemo {
    static <T extends Number> double twice(T value) {
        return value.doubleValue() * 2;
    }

    static <T extends Comparable<? super T>> T larger(T a, T b) {
        return a.compareTo(b) >= 0 ? a : b;
    }

    public static void main(String[] args) {
        System.out.println(twice(3));
        System.out.println(twice(1.25));
        System.out.println(larger("cat", "dog"));
    }
}
```

Chạy:

```bash
javac BoundSignatureDemo.java
java BoundSignatureDemo
```

`stdout`:

```text
6.0
2.5
dog
```

#### Đọc `<T extends Number>`

- `T` được khai báo tại method và bị **giới hạn** bởi `Number`.
- `T value` nhận giá trị có kiểu phù hợp bound.
- `double` là kiểu trả về cụ thể, **không** phải `T`. Do đó gọi `twice(Integer)` hay `twice(Double)` đều trả primitive `double`.
- `twice("3")` không thỏa bound và không biên dịch.

#### Đọc `<T extends Comparable<? super T>>`

- `T` được khai báo tại method, đồng thời xuất hiện ngay trong **bound của chính nó** (recursive bound).
- `Comparable<? super T>` nghĩa là giá trị kiểu `T` có thể so sánh với đối tượng `T` qua một interface `Comparable` chấp nhận `T` hoặc supertype phù hợp của `T`.
- `T a` và `T b` liên kết hai đối số với kiểu trả về `T`.
- `a.compareTo(b)` hợp lệ vì bound cho compiler chứng minh khả năng so sánh.

Điểm cần nắm ở bước đầu là “bound cung cấp những thao tác mà compiler cho phép trên `T`”. Nếu phần `Comparable<? super T>` còn khó, trước tiên tự viết biến thể đơn giản `<T extends Comparable<T>>` và thử với `String`; sau đó trở lại lower-bound wildcard để hiểu vì sao mẫu linh hoạt hơn tồn tại.

### Multiple bounds

Ví dụ cú pháp:

```java
<T extends Number & Comparable<T>>
```

`T` phải thỏa cả hai cận. Nếu có **cận class**, nó phải đứng đầu; các interface theo sau dấu `&`. `extends` xuất hiện ngay cả khi cận là interface. Một biến kiểu không có `extends` tường minh mặc định có bound `Object` theo mô hình type parameter thông thường.

Không đồng nhất `<T extends Number>` với `List<? extends Number>`:

- `<T extends Number>` **khai báo một biến kiểu có tên** `T` cùng cận trên.
- `List<? extends Number>` **sử dụng wildcard** tại vị trí type argument của `List`; nó không tạo tên `T` để người viết dùng ở nơi khác.

## 8. Type parameter và wildcard: hai loại “liên kết” khác nhau

Trong chữ ký, `T` có thể xuất hiện ở nhiều nơi để khẳng định một quan hệ; hai wildcard `?` không mặc nhiên cùng một loại.

### Ví dụ so sánh

```java
static <T> T chooseFirst(java.util.List<T> values)

static Object chooseFirstUnknown(java.util.List<?> values)
```

Đây là hai chữ ký minh họa, chưa có thân. Với `List<String>`:

- `chooseFirst` có thể trả kiểu tĩnh `String` vì `T` liên kết phần tử đầu vào và kết quả.
- `chooseFirstUnknown` chỉ hứa trả `Object` vì không liên kết kiểu trả về với wildcard.
- `List<?>` vẫn giữ kiểu an toàn khi **đọc**: lấy ra `Object` là an toàn; cố thêm phần tử khác `null` bằng `add` bị hạn chế ở compile time.

### Vì sao không đổi mọi `T` thành `?`?

Cú pháp như `? first(List<?> values)` là **không hợp lệ**: wildcard chỉ được dùng trong những vị trí type argument thích hợp, không dùng thay cho một return type độc lập. Nếu kiểu trả về phải phụ thuộc kiểu đầu vào, hãy khai báo type parameter và dùng cùng biến ở hai vị trí.

### Cùng một chữ ký có cả wildcard và T

Ví dụ:

```java
static <T> void copy(
        java.util.List<? super T> destination,
        java.util.List<? extends T> source)
```

Phân tích:

- `T` được khai báo tại method.
- `source` là nơi **cung cấp** giá trị đọc được như `T`: `? extends T`.
- `destination` là nơi **nhận** giá trị `T`: `? super T`.
- Dù hai tham số là hai danh sách có type arguments khác nhau, chúng cùng **tham chiếu vào một `T`** để bảo đảm thao tác chuyển phần tử an toàn.
- Wildcard riêng của `source` và `destination` không phải hai lần định nghĩa `T` mới.

Mẫu này thể hiện quy tắc **PECS** (Producer Extends, Consumer Super). Đây là công cụ đọc chữ ký; không phải mệnh lệnh phải dùng wildcard trong mọi API. Nếu vừa đọc vừa ghi một danh sách với kiểu chính xác cần liên kết, `List<T>` có thể đơn giản hơn.

### Wildcard là kiểu chưa biết, không phải Object

`List<?>` không đồng nghĩa `List<Object>`. `List<Object>` chấp nhận thêm mọi đối tượng, còn `List<?>` giữ an toàn trước một loại phần tử cụ thể nhưng không được người gọi biết. Bất kể compiler có thể đọc phần tử `List<?>` dưới dạng `Object`, nó không cho phép thêm tùy ý một `Object`.

## 9. Đọc type arguments lồng nhau

Đừng chỉ nhìn một tầng `<...>`. Với:

```java
Map<String, List<Integer>>
```

Hãy đi từ ngoài vào trong:

1. `Map<K,V>` có hai type parameters; ở đây `K=String` và `V=List<Integer>`.
2. `List<E>` nằm bên trong `V`; ở đây `E=Integer`.
3. Phương thức `Map.put(K,V)`, sau khi áp dụng type arguments, yêu cầu khóa `String` và giá trị `List<Integer>`.
4. `Map.get(Object)` nhận `Object` làm key argument theo chữ ký thư viện, nhưng kiểu trả về của `get` vẫn là `V` (tức `List<Integer>`) đối với biến Map này; có thể trả `null` khi không có ánh xạ.

Tạo `NestedSignatureDemo.java`:

```java
import java.util.List;
import java.util.Map;

public class NestedSignatureDemo {
    public static void main(String[] args) {
        Map<String, List<Integer>> marks = Map.of(
                "Lan", List.of(8, 9),
                "Mai", List.of(7));

        List<Integer> lanMarks = marks.get("Lan");
        Integer first = lanMarks.get(0);

        System.out.println(lanMarks);
        System.out.println(first);
    }
}
```

Chạy `javac NestedSignatureDemo.java`, `java NestedSignatureDemo`:

```text
[8, 9]
8
```

Hãy lần ngược kiểu của `first`: `Map<String,List<Integer>>` → `get("Lan")` trả `List<Integer>` → `get(0)` trên List trả `Integer`. Nếu gọi `marks.put(...)` ở ví dụ, `Map.of` tạo immutable map nên việc sửa đổi sẽ ném `UnsupportedOperationException` tại runtime ngay cả khi kiểu của cặp khóa–giá trị hợp lệ. **Hợp lệ về generic type không đồng nghĩa object cho phép mutation.**

### Generic type không có nghĩa mọi tham số method đều là type argument

Trong `Map<K,V>`, phương thức `get(Object key)` cố ý nhận `Object` theo API. Người đọc phải tra chữ ký thực, không suy đoán thành `get(K key)`. Tương tự, `Collection<E>.contains(Object o)` nhận `Object`. Generic signature xác định những gì compiler bảo đảm; đừng suy diễn dựa trên một tên biến.

## 10. Trường hợp T có trong kiểu của lớp cha hoặc interface

Một class có thể **đóng cố định** type argument của interface cha:

```java
interface Reader<T> {
    T read();
}

class NameReader implements Reader<String> {
    @Override
    public String read() {
        return "Lan";
    }
}
```

Đây là mã khai báo minh họa. `Reader<T>` khai báo biến `T`; `NameReader implements Reader<String>` cung cấp type argument `String`, do đó method triển khai phải đáp ứng hợp đồng `String read()` tại mức mã nguồn.

Một class con cũng có thể **chuyển tiếp** type parameter:

```java
class IdentityReader<T> implements Reader<T> {
    private final T value;

    IdentityReader(T value) {
        this.value = value;
    }

    @Override
    public T read() {
        return value;
    }
}
```

Lần theo từ `IdentityReader<T>` (khai báo) đến `Reader<T>` (sử dụng) rồi tới `T read()`. Chữ `T` trong `Reader<T>` ở vế `implements` là **type argument** truyền cho interface, dù cùng tên với type parameter của class đang khai báo.

Ví dụ mở rộng: `class Result<T> extends Container<List<T>>` có nghĩa type parameter `T` của `Result` được lồng trong `List<T>` và cả `List<T>` làm type argument cấp cha `Container`. Muốn hiểu method thừa kế, phải lần theo từng ánh xạ type argument như vậy.

### Phân biệt override và overload khi đọc generic method

Nếu interface yêu cầu `T read()` và class triển khai `Reader<String>`, compiler kiểm tra method `String read()` theo hợp đồng đã chuyên biệt hóa. Sau type erasure, compiler có thể sinh bridge method để giữ đa hình; bạn không cần tự viết bridge. Trong khi đó hai overload `void use(List<String>)` và `void use(List<Integer>)` trong cùng class không phân biệt được sau erasure và bị lỗi name clash.

Điểm thực hành: khi một phương thức kế thừa “bỗng trả String”, đừng tìm `<String>` ngay trên method. Hãy lần từ interface/class cha qua `implements`/`extends` để biết biến kiểu được ràng buộc ở đâu.

## 11. Đọc một số chữ ký Java Core theo quy trình

Các chữ ký dưới đây được trình bày để đọc quan hệ kiểu. Một vài khai báo được rút gọn chỉ bỏ modifier/annotation không quyết định quan hệ kiểu; nếu cần tài liệu API chính xác của một phiên bản JDK, kiểm tra JavaDoc tương ứng.

### 11.1. `List<E>`: type parameter ở interface

Chữ ký liên quan:

```java
interface List<E> {
    boolean add(E element);
    E get(int index);
}
```

Đây là trích mô hình để đọc, không phải toàn bộ interface JDK.

Ánh xạ `List<String>` → `E=String`:

| Thành viên | Kiểu sau khi áp dụng `E=String` |
| --- | --- |
| `add(E element)` | Tham số được compiler xem là `String` |
| `E get(int index)` | Kết quả được compiler xem là `String` |
| `int index` | Vẫn là `int`, không phụ thuộc `E` |

`List<E>` chỉ khai báo **một** type parameter; nó không khai báo lại `E` trên từng method.

### 11.2. `Map<K,V>`: hai type parameters độc lập

Một số thành viên:

```java
V put(K key, V value);
V get(Object key);
Set<Map.Entry<K, V>> entrySet();
```

Với `Map<String, Integer>`:

- `put` nhận `String` và `Integer`, trả `Integer` (có thể `null` theo hành vi map).
- `get` nhận `Object`, trả `Integer` (cũng có thể `null`).
- `entrySet` trả `Set<Map.Entry<String, Integer>>`, gồm **hai tầng generic lồng**.

Nếu chưa rõ `Map.Entry<K,V>`, hãy lập bảng ánh xạ tiếp: `Entry.getKey()` có kiểu `K`, `Entry.getValue()` có kiểu `V`. Không thêm một type parameter mới ở `entrySet`; nó dùng cặp đã khai báo tại `Map`.

### 11.3. `Collections.copy`: PECS và một T nối hai list

Chữ ký method JDK:

```java
public static <T> void copy(
        List<? super T> dest,
        List<? extends T> src)
```

- `<T>` khai báo một type parameter của **method static**.
- `void`: method không trả phần tử nào; `T` chỉ liên kết hai tham số.
- `src` cung cấp phần tử như `T`; `dest` nhận `T`.
- Không giả định hành vi “append”: `Collections.copy` **thay** các vị trí hiện có của danh sách đích; danh sách đích phải có ít nhất số phần tử bằng source, nếu không ném `IndexOutOfBoundsException`.

Tạo `CopySignatureDemo.java`:

```java
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class CopySignatureDemo {
    public static void main(String[] args) {
        List<Integer> source = List.of(2, 4);
        List<Number> target = new ArrayList<>(
                Arrays.asList(0, 0, 0));

        Collections.copy(target, source);
        System.out.println(target);
    }
}
```

Chạy:

```bash
javac CopySignatureDemo.java
java CopySignatureDemo
```

`stdout`:

```text
[2, 4, 0]
```

Tại invocation này, `T=Integer` là một lựa chọn hợp lệ: `List<Integer>` phù hợp `List<? extends Integer>`; `List<Number>` phù hợp `List<? super Integer>`. `Collections.copy` thay hai phần tử đầu, giữ phần tử thứ ba. Nếu dùng `List.of(0,0,0)` làm đích thì dù kích thước đủ, danh sách không hỗ trợ `set` và sẽ gây lỗi runtime; chữ ký generic không cam kết tính mutable.

### 11.4. `Comparator.comparing`: một method, hai biến kiểu

Chữ ký overload thường dùng trong JDK:

```java
public static <T, U extends Comparable<? super U>>
Comparator<T> comparing(
        Function<? super T, ? extends U> keyExtractor)
```

Tách theo thứ tự:

1. `<T, U extends Comparable<? super U>>` khai báo hai type parameters thuộc **method**.
2. `U` có bound yêu cầu giá trị trích ra có thể so sánh; `T` không có bound tường minh.
3. `Function<? super T, ? extends U>` nhận một đối tượng kiểu `T` và cho ra key tương thích `U`.
4. `Comparator<T>` là kiểu trả về, nghĩa là một comparator dùng cho các đối tượng `T`; **không** phải `Comparator<U>`.
5. Với `Comparator.comparing(String::length)`, kiểu key có thể là `Integer` (`U=Integer`), còn loại đối tượng đem so sánh là `String` (`T=String`) khi ngữ cảnh đủ để inference xác định.

Để tránh các vấn đề target typing với method reference khi thử độc lập, có thể viết `Comparator.<String, Integer>comparing(String::length)` hoặc `Comparator.comparing((String s) -> s.length())`. Cách viết explicit type witness không tạo một generic class mới, chỉ làm rõ type arguments của **lời gọi method**.

Tạo `ComparatorSignatureDemo.java`:

```java
import java.util.Comparator;
import java.util.List;

public class ComparatorSignatureDemo {
    public static void main(String[] args) {
        Comparator<String> byLength =
                Comparator.comparing((String s) -> s.length());

        String longest = List.of("go", "java", "api")
                .stream()
                .max(byLength)
                .orElseThrow();

        System.out.println(longest);
    }
}
```

Chạy `javac ComparatorSignatureDemo.java` và `java ComparatorSignatureDemo`:

```text
java
```

Hãy ghi hai cột riêng: `T=String` đại diện đối tượng được so sánh; `U=Integer` đại diện khóa trích xuất để so sánh. Không đồng nhất `T` và `U` chỉ vì chúng cùng một method.

### 11.5. `Optional.map`: type parameter của class cộng type parameter của method

Chữ ký JDK:

```java
public <U> Optional<U> map(
        Function<? super T, ? extends U> mapper)
```

Chữ ký này là một **instance method bên trong `Optional<T>`**:

- `T` đến từ khai báo **class** `Optional<T>`; không phải do `map` khai báo.
- `<U>` là khai báo biến kiểu **của method `map`**.
- `mapper` nhận được kiểu `T` và tạo giá trị tương thích `U`.
- Kết quả `Optional<U>` giữ kiểu của giá trị được biến đổi, khác với `Optional<T>` đầu vào.

Tạo `OptionalSignatureDemo.java`:

```java
import java.util.Optional;

public class OptionalSignatureDemo {
    public static void main(String[] args) {
        Optional<Integer> length = Optional.of("Java")
                .map(String::length);

        System.out.println(length.orElse(0));
    }
}
```

Chạy `javac OptionalSignatureDemo.java`, `java OptionalSignatureDemo`:

```text
4
```

`Optional.of("Java")` tạo `Optional<String>`; tại invocation của `map`, `T=String` và `U=Integer`. Phương thức `map` trả `Optional<Integer>`; `orElse(0)` trả `Integer` rồi được in ra. Trường hợp mapper trả `null`, `Optional.map` tạo optional rỗng theo hợp đồng JDK, không tự chuyển thành `Optional<null>`.

## 12. Method type inference: ràng buộc tại nơi gọi

Tới đây cần tách **khai báo** và **áp dụng**. Với:

```java
static <T> T pick(T first, T second) {
    return first;
}
```

Chữ ký có một `T`, nhưng compiler không luôn suy luận kiểu chỉ dựa vào “lớp runtime của hai đối số phải bằng nhau”. Ví dụ `pick(10, 20)` có thể dùng `T=Integer`; một lời gọi với các giá trị khác loại có thể suy luận một supertype hoặc intersection type phù hợp, tùy ngữ cảnh. Vì vậy “cùng T” có nghĩa các biểu thức phải **cùng thỏa nghiệm ràng buộc cho type variable**, không nhất thiết phải cùng exact class.

### Ngữ cảnh mục tiêu có thể ảnh hưởng suy luận

```java
import java.util.Collections;
import java.util.List;

public class InferenceDemo {
    static <T> T identity(T value) {
        return value;
    }

    public static void main(String[] args) {
        List<String> empty = Collections.emptyList();
        String word = InferenceDemo.<String>identity("typed");
        System.out.println(empty.size());
        System.out.println(word);
    }
}
```

Chạy `javac InferenceDemo.java` và `java InferenceDemo`:

```text
0
typed
```

`Collections.emptyList()` khai báo generic method `<T> List<T> emptyList()`; dù không có đối số truyền vào, kiểu đích `List<String>` giúp suy luận `T=String`. `InferenceDemo.<String>identity("typed")` truyền type witness minh thị. Nếu truyền kiểu không thỏa tham số method hoặc bound, compiler từ chối.

### Diamond và `var` không giống nhau

`List<String> words = new ArrayList<>();` suy luận type arguments cho constructor/class từ mục tiêu. `var words = new ArrayList<String>();` để compiler suy ra **kiểu biến cục bộ** từ initializer. `var` không khai báo type parameter mới; nó chỉ yêu cầu compiler suy ra một kiểu tĩnh tại điểm khai báo. Những biểu thức thiếu ngữ cảnh có thể khiến kiểu suy ra rộng hơn dự định; hãy xem kiểu mà IDE/compiler suy ra thay vì mặc định đoán là `String`.

### Overload resolution và generic

Khi có overload, compiler dùng kiểu tĩnh, inference, quy tắc applicability và specificity để chọn method. Không nên nhìn một `<T>` đơn lẻ rồi khẳng định overload được chọn trước khi xem các lời gọi và overload khác. Trong cùng class, hai method chỉ khác `List<String>`/`List<Integer>` sẽ xung đột do type erasure; generic không cho tạo overload kiểu đó.

## 13. Phạm vi (scope) và điểm ràng buộc: đừng nhầm nơi xuất hiện tên

Những vị trí khai báo thường gặp:

| Vị trí | Cú pháp | Phạm vi sử dụng tiêu biểu |
| --- | --- | --- |
| Class | `class Box<T> { ... }` | Thành viên instance và khai báo bên trong vùng scope phù hợp |
| Interface | `interface Repository<ID,E> { ... }` | Phương thức/kiểu trả về của interface và subtype áp dụng |
| Record | `record Pair<K,V>(K key, V value) {}` | Components, accessor và hành vi instance |
| Method | `<T> T method(T x)` | Tham số, return type, throws/implementation phù hợp |
| Constructor | `<U> Box(U x)` | Constructor đó |
| Generic supertype usage | `implements Reader<String>` | Ràng buộc `T` của `Reader<T>` thành `String` cho subtype |
| Type argument usage | `Class<T> token` | Dùng `T` đã có trong scope làm argument cho một generic khác |

Cách theo dõi thực tế bằng “sổ cái biến kiểu”:

```text
Chữ ký:
class Cache<K, V> {
    V put(K key, V value)
    <R> R convert(V value, Function<? super V, ? extends R> f)
}

Biến   Nơi khai báo    Ràng buộc trong chữ ký
K      Cache<K,V>      key của put
V      Cache<K,V>      value/return của put;
                      parameter value và input f của convert
R      <R> convert     output f và return của convert
```

Mã này là **sơ đồ chữ ký**, không phải class Java chạy độc lập. Khi đọc `Cache<String,Integer>`, điền `K=String`, `V=Integer`. Một invocation `convert` có thể chọn `R=String`, invocation khác có thể chọn `R=Long` trên cùng đối tượng `Cache<String,Integer>`. Đó là lý do phải ghi cột **nơi khai báo**, thay vì chỉ ghi “T là kiểu dữ liệu”.

### Cảnh báo về shadowing

Tên type parameter có thể gây hiểu lầm khi nhiều cấp scope dùng ký hiệu ngắn giống nhau. Trong code sản xuất nên chọn tên khác (`T` của class, `R` của method) để tránh đọc nhầm. Một biến kiểu khác tên **không** tự liên hệ với biến kiểu thứ nhất chỉ vì cả hai đều có bound `Object` hay cùng xuất hiện trong chữ ký.

Một lời gọi method có thể suy luận hai type parameters thành cùng một kiểu cụ thể, nhưng đó là **nghiệm của invocation**, không phải bằng chứng hai parameters được khai báo đồng nhất. Nếu người viết muốn một quan hệ kiểu đồng nhất cố định, dùng cùng một named type parameter ở các vị trí cần thiết.

## 14. Type erasure: theo dõi ràng buộc ở compile time, không suy ra “kiểu runtime T”

Trong Java, type variables và type arguments đa phần được xử lý bằng **type erasure**:

- `T` không có bound thường được xóa thành `Object`.
- `T extends Number` xóa về `Number`.
- Với multiple bounds, erasure của `T` dựa vào **cận đầu tiên**.
- Compiler có thể chèn cast và bridge method.
- `List<String>` và `List<Integer>` dùng cùng runtime class `ArrayList` nếu đều khởi tạo bằng `ArrayList`.

Tạo `SignatureRuntimeDemo.java`:

```java
import java.util.ArrayList;
import java.util.List;

public class SignatureRuntimeDemo {
    public static void main(String[] args) {
        List<String> strings = new ArrayList<>();
        List<Integer> integers = new ArrayList<>();

        System.out.println(strings.getClass() == integers.getClass());
        System.out.println(strings instanceof List<?>);
    }
}
```

Chạy:

```bash
javac SignatureRuntimeDemo.java
java SignatureRuntimeDemo
```

`stdout`:

```text
true
true
```

Điều này không phủ nhận quan hệ kiểu mà compiler vừa kiểm tra. Nó chỉ cho thấy runtime object thông thường không lưu `String`/`Integer` làm type argument riêng để JVM kiểm tra trên từng collection.

Cần phân biệt metadata của **khai báo** với đối số kiểu của **một đối tượng**. `Field.getGenericType()` có thể đọc generic signature còn lưu trong class file của một trường; `new ArrayList<String>().getClass()` không tự trả về “ArrayList of String”. Trong API có liên quan reflection hoặc serialize dữ liệu, nhà phát triển đôi khi cung cấp `Class<T>`, `Type` hoặc type token để mang thêm mô tả kiểu vào runtime.

### Một ví dụ lỗi ràng buộc bị vượt qua

```java
List<String> labels = new ArrayList<>();
List raw = labels;
raw.add(17);
String label = labels.get(0);
```

Đoạn chỉ nhằm minh họa, cần import và đặt vào phương thức trước khi chạy. `raw.add(17)` gây unchecked warning nhưng có thể qua compiler; `labels.get(0)` khi dùng thành `String` có thể gây `ClassCastException` bởi cast runtime. Lần ngược từ nơi cast thất bại tới raw alias để tìm chỗ hợp đồng `List<String>` bị phá. Dùng `javac -Xlint:rawtypes -Xlint:unchecked` giúp phát hiện các đường đi nguy hiểm.

## 15. Những lỗi suy luận khi đọc chữ ký

| Cách đọc sai | Vì sao sai | Cách đọc đúng |
| --- | --- | --- |
| “`T` là một class cụ thể.” | `T` là biến kiểu khai báo tại source. | Tìm `<T>` và nơi truyền/suy luận type argument. |
| “Hai method cùng viết `<T>` thì cùng một T.” | Scope method độc lập. | Lập bảng riêng cho từng method. |
| “`List<?>` là `List<Object>`.” | Wildcard bảo vệ kiểu phần tử chưa biết. | Đọc ra `Object` an toàn; không được thêm tùy ý. |
| “`<T extends Number>` và `<? extends Number>` giống nhau.” | Một bên khai báo named type parameter, một bên dùng wildcard. | Xác định dấu `<...>` nằm ở vị trí khai báo hay argument. |
| “`List<Integer>` là `List<Number>`.” | Generic type arguments mặc định invariant. | Dùng `? extends Number` khi chỉ đọc nguồn. |
| “`? super Integer` cho đọc trực tiếp Integer.” | Loại danh sách có thể là `List<Object>`. | Kiểu đọc an toàn chung là `Object`. |
| “Method generic phải dùng `T` trong kiểu trả về.” | `<T>` có thể chỉ ràng buộc tham số, hoặc return là `void`. | Theo dõi tất cả vị trí `T` thực sự xuất hiện. |
| “`Map<K,V>.get` nhất định nhận `K`.” | API cụ thể có thể khai báo `get(Object)`. | Tra chữ ký thành viên; không suy đoán từ kiểu container. |
| “`static` method trong `Box<T>` tự dùng T của class.” | Class-level `T` thuộc ngữ cảnh instance; static không có instantiation riêng. | Khai báo riêng `static <U> U ...` nếu cần. |
| “Các giá trị truyền vào cùng T phải có cùng exact class.” | Type inference có thể chọn supertype phù hợp. | Đọc ràng buộc compile time; đừng đồng nhất với so sánh `getClass()`. |
| “`Collections.copy` nối danh sách.” | Chữ ký kiểu không mô tả đầy đủ hành vi. | Đọc thêm hợp đồng API về size/mutation/index. |
| “Không cần kiểm tra runtime vì đã có generic.” | Raw/unchecked/reflection/dữ liệu ngoài có thể phá hợp đồng. | Kiểm tra dữ liệu tại ranh giới và theo dõi warning. |

## 16. Quy trình đọc một chữ ký lạ trong sáu bước

Khi gặp một signature trong tài liệu hoặc IDE, hãy xử lý theo thứ tự sau:

1. **Chép nguyên chữ ký, giữ đủ các dấu `<...>`.** Tách modifier, tên class/method, kiểu trả về và danh sách tham số. Đừng bỏ cận `extends`/`super`.
2. **Đánh dấu mọi nơi khai báo.** Ghi `class/interface/record <...>`, `method <...>`, `constructor <...>`; phân biệt đó là type-parameter list hay type-argument list.
3. **Vẽ sổ cái cho từng biến kiểu có tên.** Với `K`, `V`, `T`, `U`, ghi bound (nếu có), nơi xuất hiện ở tham số, kiểu trả về, supertype và nested generic.
4. **Khoanh wildcard riêng.** Đọc `? extends X` là phần tử được cung cấp dưới cận `X`; `? super X` là nơi có thể tiêu thụ `X`; `?` là loại chưa biết. Sau đó nối wildcard về named type parameter `X` nếu có.
5. **Áp dụng một lời gọi có kiểu cụ thể.** Ghi rõ `K=String`, `V=Integer` hoặc nghiệm suy luận `T=Integer`, `R=String`. Dịch chữ ký thành kiểu mà caller nhìn thấy; tránh diễn đạt như JVM tạo class generic mới.
6. **Phân biệt giới hạn kiểu với hành vi runtime.** Chữ ký cho biết các phép gọi hợp lệ về kiểu; Javadoc hoặc thân implementation mới cho biết mutability, xử lý `null`, exception, copying và thứ tự.

Chỉ sau khi làm xong sáu bước mới bắt đầu tối ưu hoặc rút gọn API. Nhiều lỗi do đọc lướt `T` mà bỏ qua nơi khai báo ở class so với method.

## 17. Bài thực hành tự kiểm tra

### Bài 1 — Gạch chân nơi khai báo

Cho:

```java
class Store<K, V> {
    V get(K key) { return null; }
    <R> R convert(V value, java.util.function.Function<V, R> f) {
        return f.apply(value);
    }
}
```

Câu hỏi:

1. `K`, `V` và `R` được khai báo ở đâu?
2. Với `Store<String,Integer>`, `get` nhận gì và trả gì?
3. `convert` trên Store này có thể trả `String` không? Điều kiện gì cần cho `Function`?

Đáp án: `K,V` của class; `R` của method `convert`; `get` nhận `String` trả `Integer`; `convert` có thể trả `String` khi nhận function phù hợp kiểu `Function<Integer,String>`. Code minh họa method `get` hiện trả `null`; chữ ký không tự cung cấp lưu trữ thực tế.

### Bài 2 — Tìm nơi type inference hoạt động

Cho:

```java
static <T> java.util.List<T> one(T value) {
    return java.util.List.of(value);
}
```

Dự đoán kiểu biểu thức:

```java
java.util.List<String> a = one("X");
java.util.List<Integer> b = one(7);
```

Đáp án: `T=String` và `T=Integer` ở hai invocation. `List.of` không chấp nhận phần tử `null` theo hợp đồng runtime; kiểu generic `T` không phải một null-check tự động.

### Bài 3 — Phân biệt named type parameter và wildcard

Cho ba chữ ký:

```java
static <T> T first(java.util.List<T> list)
static Object any(java.util.List<?> list)
static void receive(java.util.List<? super Integer> list)
```

Câu hỏi: chữ ký nào liên kết kiểu trả về với phần tử; chữ ký nào chỉ đọc an toàn như `Object`; chữ ký nào cho thêm `Integer`? Đáp án: lần lượt `first`, `any`, `receive`. Đây là các **chữ ký trích**, không có thân nên không tự biên dịch như chương trình độc lập.

### Bài 4 — Lần theo kiểu lồng

Với `Map<String, List<Long>>`, hãy tự suy ra:

- `K=String`, `V=List<Long>`.
- `map.entrySet()` có kiểu `Set<Map.Entry<String,List<Long>>>`.
- `entry.getValue()` có kiểu `List<Long>`.
- `entry.getValue().get(0)` có kiểu `Long`.

Nếu nhầm `V=Long`, quay lại bước đọc từ ngoài vào trong.

### Bài 5 — Đọc method có hai biến kiểu và bound

Cho:

```java
static <T, U extends Comparable<? super U>>
java.util.Comparator<T> by(
        java.util.function.Function<? super T, ? extends U> extractor)
```

Hỏi biến nào là đối tượng cần so sánh, biến nào là khóa so sánh, bound nằm ở đâu, kiểu trả về là gì? Đáp án: `T` là kiểu đối tượng; `U` là key type; `U` có bound `Comparable<? super U>`; kết quả `Comparator<T>`. Chữ ký này mô phỏng quan hệ của `Comparator.comparing`, chưa có implementation.

## 18. Tóm tắt để áp dụng ngay

Mỗi khi đọc generic signature, hãy tạo một bản đồ ngắn theo mẫu:

```text
Định nghĩa:    <T, R extends SomeInterface> ...
Nguồn T:       class hay method? T xuất hiện ở đâu?
Nguồn R:       class hay method? bound của R là gì?
Wildcard:      ? extends T / ? super T / ?
Lời gọi:       T = kiểu nào, R = kiểu nào?
Kết quả:       caller nhận kiểu biểu thức nào?
Runtime:       chữ ký có hứa mutation/null/order/validation không?
```

Đọc `T` như **một quan hệ phải lần theo**, không như tên một kiểu bất kỳ. Đọc `<T>` như vị trí khai báo, `<String>` như một đối số kiểu khi dùng generic, và `<?>` như một type argument chưa biết. Với class-level và method-level type parameter, luôn đánh dấu rõ phạm vi; với chữ ký dài, lập bảng ràng buộc trước khi suy luận. Làm được điều đó, bạn có thể đi từ `List<E>` đến `Optional.map` hay `Comparator.comparing` mà vẫn hiểu đúng compiler đang bảo vệ điều gì.

</details>

- [Quay lại đầu trang](#back-to-top)

<a id="back-to-top"></a>

# Giới hạn ngôn ngữ

## Menu
- [Không khởi tạo new T() và cách truyền Supplier&lt;T&gt;](#generics-language-limits-001)
- [Không dùng primitive làm đối số kiểu](#generics-language-limits-002)
- [Không dùng tham số kiểu của lớp trong ngữ cảnh static](#generics-language-limits-003)
- [Không khai báo lớp generic kế thừa Throwable](#generics-language-limits-004)
- [Phân biệt kiểu trong catch với tham số kiểu được dùng trong throws](#generics-language-limits-005)
- [Enum và kiểu annotation không được khai báo tham số kiểu](#generics-language-limits-006)
- [Không cung cấp giá trị mặc định cho tham số kiểu](#generics-language-limits-007)
- [Các giới hạn overload, instanceof và tạo mảng liên quan xóa kiểu](#generics-language-limits-008)
- [Phân biệt giới hạn ngôn ngữ với giới hạn của một API cụ thể](#generics-language-limits-009)

## <a id="generics-language-limits-001">Không khởi tạo new T() và cách truyền Supplier&lt;T&gt;</a>

<details>
<summary>Xem chi tiết</summary>

# Không khởi tạo `new T()` và cách truyền `Supplier<T>` trong Java Generics

## 1. Tại sao bài toán này xuất hiện?

Bạn đang viết một class generic dùng để quản lý hoặc khởi tạo nhiều kiểu dữ liệu khác nhau:

```java
class Box<T> {
    T create() {
        // Mong muốn tạo một object mới có đúng kiểu T.
        return new T(); // Không biên dịch.
    }
}
```

Ý định có vẻ hợp lý: caller dùng `Box<Customer>` thì `create()` tạo `Customer`, còn `Box<Order>` thì tạo `Order`. Tuy nhiên Java **không cho phép `new T()`** khi `T` là type variable. Thông báo từ `javac` thường có dạng:

```text
error: unexpected type
  required: class
  found:    type parameter T
```

Ngay cả khi gắn bound cho biến kiểu, ví dụ `<T extends Number>`, trình biên dịch vẫn không thể cấp phát trực tiếp bằng `new T()`. Java không có cú pháp bound kiểu “`T` bắt buộc có public no-argument constructor” như một số ngôn ngữ khác.

Giải pháp thường gọn và dễ kiểm thử nhất là **để caller cung cấp chiến lược tạo object** thông qua `Supplier<T>`:

```java
import java.util.function.Supplier;

class Box<T> {
    private final Supplier<T> factory;

    Box(Supplier<T> factory) {
        this.factory = factory;
    }

    T create() {
        return factory.get();
    }
}
```

Caller quyết định cách tạo instance:

```java
Box<StringBuilder> box = new Box<>(StringBuilder::new);
StringBuilder builder = box.create();
```

Ở đây `StringBuilder::new` là **constructor reference**, được chuyển thành một implementation của `Supplier<StringBuilder>`. Khi `create()` gọi `factory.get()`, logic tạo object chạy ở đúng nơi đã biết class cụ thể.

Bài này giải thích nguyên nhân từ type erasure, cách dùng `Supplier<T>` và `Supplier<? extends T>`, constructor reference, lambda có tham số, thiết kế factory, kiểm soát null/freshness, checked exceptions và các lỗi thường gặp. Ví dụ chính dùng Java 17 và đã được biên dịch, chạy kiểm tra.

## 2. `T` là type variable, không phải tên một class cụ thể

Một class generic:

```java
class Repository<T> {
    T value;
}
```

định nghĩa **một type variable `T`** để compiler kiểm tra các phép gán và quan hệ kiểu tại source. Chẳng hạn:

```java
Repository<String> strings = new Repository<>();
Repository<Integer> numbers = new Repository<>();
```

`String` và `Integer` là type arguments khác nhau khi dùng `Repository<T>`. Nhưng chúng không khiến JVM tạo các bản class `Repository<String>` và `Repository<Integer>` riêng biệt theo mỗi argument.

Phần lớn thông tin type argument generic bị loại khỏi những method/field descriptors ở bytecode theo cơ chế **type erasure** (*xóa kiểu*). Với type variable không có bound tường minh, erasure thường trở thành `Object`.

Điều đó tạo ra một giới hạn: bên trong class generic, compiler không có một biểu thức `new` tự động xác định runtime class nào sẽ được tạo khi caller chọn `T`.

### 2.1. Phân biệt dùng `T` và tạo `T`

Bạn **được** viết:

```java
class Holder<T> {
    private T value;

    void put(T value) {
        this.value = value;
    }

    T get() {
        return value;
    }
}
```

Bởi vì code chỉ lưu và trả lại các object đã được bên ngoài tạo, compiler kiểm tra kiểu `T` qua hợp đồng generic.

Bạn **không được** viết:

```java
class Holder<T> {
    T make() {
        return new T(); // Lỗi biên dịch.
    }
}
```

`new` cần biết kiểu class có thể khởi tạo và constructor tương ứng ở source. `T` chỉ là type variable: nó không xác định một constructor cụ thể mà method này có quyền gọi.

**Ghi nhớ:** `T` hữu ích để liên kết *kiểu của giá trị* giữa tham số, field và kết quả. Nó **không phải một “constructor động”**.

### 2.2. Bound không làm `new T()` hợp lệ

Xét:

```java
class Factory<T extends Number> {
    T make() {
        return new T(); // Vẫn sai.
    }
}
```

`T extends Number` chỉ yêu cầu type argument là `Number` hoặc một subtype thích hợp. Caller có thể chọn `Integer`, `Double`, một `Number` tự định nghĩa, v.v. Các lớp đó không có chung một constructor public không tham số.

Ngay cả khi bound là một class có constructor public không tham số:

```java
class Base {
    public Base() {
    }
}

class Generic<T extends Base> {
    // T make() { return new T(); } // Vẫn sai.
}
```

Compiler không thể suy luận rằng **mọi subtype `T`** sẽ có constructor phù hợp. Constructor không phải một member polymorphic được thừa kế như instance method; subclass có thể chỉ có các constructor nhận tham số hoặc có quyền truy cập khác.

### 2.3. Vì sao không dùng `T.class`?

```java
class Holder<T> {
    // Class<T> type() { return T.class; } // Không hợp lệ.
}
```

`T.class` không phải class literal hợp lệ vì `T` là type variable, không phải tên một class cụ thể hiện diện ở runtime. Khi thực sự cần class token, caller phải cung cấp `Class<T>` như một giá trị riêng.

```java
static <T> String className(Class<T> type) {
    return type.getName();
}

// className(String.class) -> "java.lang.String"
```

`Class<T>` là một công cụ khác với `Supplier<T>`: class token mô tả một runtime class, còn Supplier mô tả **hành động cung cấp giá trị**. Supplier thường phù hợp hơn khi mục tiêu là tạo object, đặc biệt với constructor có tham số, dependency injection hoặc factory tùy chỉnh.

## 3. `Supplier<T>` là gì?

`java.util.function.Supplier<T>` là một functional interface tiêu chuẩn của Java:

```java
@FunctionalInterface
public interface Supplier<T> {
    T get();
}
```

Nó có một abstract method **không nhận tham số** và trả một giá trị kiểu `T`.

Có thể xem Supplier như một lời hứa:

> “Khi được gọi `get()`, tôi sẽ cung cấp một giá trị tương thích với `T`.”

Nó **không hứa**:

- Mỗi lần gọi trả một object mới.
- Kết quả luôn khác null.
- Việc tính toán luôn nhanh hoặc không có tác dụng phụ.
- Có thể gọi đồng thời an toàn trên nhiều thread.
- Phương thức `get()` không bao giờ ném unchecked exception.

Những thuộc tính đó phải được quy định thêm trong hợp đồng của ứng dụng hoặc được đảm bảo bởi implementation cụ thể.

### 3.1. Supplier khác `Consumer`, `Function` và `Callable`

| Interface | Abstract method | Trường hợp thường dùng |
| --- | --- | --- |
| `Supplier<T>` | `T get()` | Cung cấp/tạo giá trị không có input tại thời điểm gọi. |
| `Function<A,R>` | `R apply(A input)` | Tạo/chuyển đổi kết quả có một input. |
| `BiFunction<A,B,R>` | `R apply(A a, B b)` | Tạo/chuyển đổi với hai input. |
| `Consumer<T>` | `void accept(T value)` | Tiêu thụ hoặc xử lý một giá trị; không trả kết quả. |
| `Callable<T>` | `T call() throws Exception` | Tác vụ trả giá trị, có chữ ký cho checked exception. |

`Supplier<T>` phù hợp nhất khi object có thể được tạo mà **không cần đối số được truyền cho `get()`**. Điều đó không có nghĩa constructor thật phải không có tham số: lambda có thể **capture** các giá trị cấu hình được cung cấp từ trước.

## 4. Giải pháp cơ bản: truyền Supplier vào generic class

Thiết kế:

```java
import java.util.Objects;
import java.util.function.Supplier;

final class Box<T> {
    private final Supplier<? extends T> factory;

    Box(Supplier<? extends T> factory) {
        this.factory = Objects.requireNonNull(factory, "factory");
    }

    T newValue() {
        return Objects.requireNonNull(
                factory.get(), "factory returned null");
    }
}
```

Lợi ích:

1. Class `Box<T>` **không cần biết** `T` là class nào.
2. Caller cung cấp cách tạo object, có thể thay bằng constructor reference, lambda hoặc method reference.
3. `newValue()` trả về `T`, nên caller không cần cast.
4. Có thể thay Supplier bằng factory giả trong unit test.
5. Có thể kiểm tra rõ ràng việc factory được truyền vào là null và factory trả null.

Cách dùng:

```java
Box<StringBuilder> box = new Box<>(StringBuilder::new);

StringBuilder first = box.newValue();
StringBuilder second = box.newValue();

System.out.println(first != second); // true với constructor reference này
```

`StringBuilder::new` tạo builder mới mỗi lần được gọi, vì constructor không có tham số được thực thi mỗi lần Supplier chạy.

### 4.1. Vì sao dùng `Supplier<? extends T>`?

Một khai báo đơn giản:

```java
Supplier<T> factory;
```

đủ dùng cho nhiều trường hợp. Nhưng khi factory có thể trả subtype của `T`, khai báo:

```java
Supplier<? extends T> factory;
```

thường linh hoạt hơn. Ví dụ một API muốn trả kiểu tổng quát `Number`, nhưng factory thực tế tạo `Integer`:

```java
Supplier<Integer> integers = () -> Integer.valueOf(7);
Supplier<? extends Number> numbers = integers;

Number value = numbers.get();
```

`Supplier<? extends T>` phù hợp vì `Supplier` là **producer**: bạn đọc một giá trị từ factory. Đây là ứng dụng trực tiếp của quy tắc PECS (*Producer Extends, Consumer Super*).

Không nên đổi thành `Supplier<? super T>` khi mục tiêu là lấy một `T`, vì factory kiểu `Supplier<? super T>` có thể trả một supertype rộng hơn `T`, compiler không thể hứa kết quả là `T`.

### 4.2. Vì sao kiểm tra `Objects.requireNonNull`?

```java
Objects.requireNonNull(factory, "factory");
```

giúp lỗi xuất hiện tại lúc tạo `Box`, thay vì đợi đến `newValue()` rồi vô tình gọi `.get()` trên tham chiếu null.

```java
Objects.requireNonNull(factory.get(), "factory returned null");
```

thể hiện hợp đồng riêng: method `newValue()` **không chấp nhận kết quả null**. Nếu ứng dụng cho phép null như một giá trị có nghĩa, bỏ hoặc thay kiểm tra này bằng một API mô tả rõ ràng, ví dụ `Optional<T>` khi thích hợp.

Đừng thêm ràng buộc không-null mà bỏ qua contract: đôi khi Supplier được thiết kế có thể trả null để biểu thị “không có dữ liệu”.

## 5. Cung cấp Supplier bằng constructor reference

Một constructor không tham số có thể khớp `Supplier<T>`:

```java
class Customer {
    Customer() {
    }
}

Supplier<Customer> factory = Customer::new;
Customer customer = factory.get();
```

`Customer::new` không khởi tạo object ngay lúc viết biểu thức. Nó tạo một function object có hành vi gọi constructor khi `get()` được gọi.

### 5.1. Constructor phải truy cập được tại nơi viết reference

Một constructor reference như:

```java
Supplier<Customer> factory = Customer::new;
```

chỉ hợp lệ nếu compiler có thể truy cập constructor tương ứng từ vị trí expression và overload resolution chọn được constructor phù hợp với functional interface.

Một class có constructor `private` không thể được reference tùy ý từ code bên ngoài. Nhưng bạn có thể tạo Supplier **ở bên trong class** hoặc một factory method có quyền truy cập, rồi truyền nó ra ngoài mà không tiết lộ constructor.

### 5.2. Constructor không tham số có thể là implicit hoặc explicit

Ví dụ:

```java
class Empty {
}
```

Nếu không khai báo bất cứ constructor nào, compiler có thể tạo default constructor theo quy tắc của Java; `Empty::new` hợp lệ khi constructor ấy truy cập được từ nơi sử dụng.

Nếu class đã khai báo một constructor nhận tham số:

```java
class User {
    User(String name) {
    }
}
```

Java **không tự thêm** constructor không tham số. Khi đó `User::new` không thể khớp `Supplier<User>` nếu chỉ có `User(String)`; cần dùng lambda capture hoặc `Function<String,User>`.

### 5.3. `Class<T>` không phải constructor reference

Không thể viết một `T::new` tổng quát bên trong class generic:

```java
class Box<T> {
    // Supplier<T> factory = T::new; // Không hợp lệ.
}
```

`T` chưa phải một tên class có constructor xác định. Caller cần cung cấp `ActualClass::new` ở nơi biết class cụ thể.

## 6. Constructor có tham số: lambda capture

Với class:

```java
class Config {
    private final String region;

    Config(String region) {
        this.region = region;
    }

    String region() {
        return region;
    }
}
```

Không dùng được `Config::new` làm `Supplier<Config>` nếu chỉ có constructor `Config(String)`:

```java
// Không hợp lệ cho Supplier<Config>:
// Supplier<Config> factory = Config::new;
```

Nhưng có thể **capture giá trị region**:

```java
String region = "ap-southeast";

Supplier<Config> factory = () -> new Config(region);
Config first = factory.get();
```

Lambda ở đây không nhận tham số khi `.get()` được gọi; nó đã “giữ” giá trị `region` từ môi trường nơi Supplier được tạo.

Trong Java, local variable được capture phải là `final` hoặc **effectively final** — không bị gán lại sau khi được lambda capture theo quy tắc ngôn ngữ.

### 6.1. Capturing khác việc luôn tạo object ngay

```java
Config existing = new Config("ap-southeast");

Supplier<Config> reused = () -> existing;
Supplier<Config> fresh = () -> new Config("ap-southeast");
```

Hai Supplier cùng trả kiểu `Config`, nhưng có hành vi khác:

- `reused.get()` trả cùng `existing` qua mỗi lần gọi.
- `fresh.get()` thực hiện `new Config(...)` mỗi lần gọi.

Đừng suy luận từ **kiểu `Supplier<Config>`** rằng mỗi lần `.get()` chắc chắn cấp phát một object mới. Sự khác biệt phải được định nghĩa trong hợp đồng.

### 6.2. Dependency injection qua lambda

Một object có thể cần nhiều dependency:

```java
class Service {
    Service(String endpoint, int timeoutMillis) {
    }
}
```

Caller có thể cung cấp:

```java
String endpoint = "https://example.invalid";
int timeoutMillis = 1000;

Supplier<Service> serviceFactory =
        () -> new Service(endpoint, timeoutMillis);
```

Ví dụ URL là chuỗi minh họa cấu hình, không có truy cập mạng. Điều quan trọng là các dependency có sẵn tại **nơi dựng Supplier**. Class generic nhận `Supplier<Service>` không phải biết constructor đang nhận hai tham số hay cách cấu hình chúng.

Khi dependency thay đổi theo từng lời gọi, `Function`/`BiFunction` thường thích hợp hơn Supplier đã capture tham số cố định.

## 7. Khi nào dùng `Function` hoặc `BiFunction` thay Supplier?

`Supplier<T>` có method `.get()` không nhận argument. Nếu quá trình khởi tạo cần input **ở mỗi lần gọi**, bạn có thể dùng `Function<A,T>` hoặc `BiFunction<A,B,T>`.

Ví dụ `record` với hai thành phần:

```java
record Ticket(int id, String title) {
}
```

Có thể tạo một vé từ title trong một hàm:

```java
import java.util.function.Function;

Function<String, Ticket> createTicket =
        title -> new Ticket(7, title);

Ticket t = createTicket.apply("Java");
```

Nếu cả `id` và `title` là input:

```java
import java.util.function.BiFunction;

BiFunction<Integer, String, Ticket> ticketFactory =
        Ticket::new;

Ticket t = ticketFactory.apply(7, "Java");
```

`Ticket::new` ở đây khớp `BiFunction<Integer,String,Ticket>` vì record constructor có hai tham số `int` và `String`; compiler có thể sử dụng unboxing từ `Integer` khi cần theo quy tắc tương thích.

### 7.1. Generic factory function

```java
static <A, T> T createFrom(
        A input,
        java.util.function.Function<? super A, ? extends T> factory) {
    return java.util.Objects.requireNonNull(
            factory, "factory").apply(input);
}
```

`? super A` ở đầu vào của Function cho phép factory nhận kiểu rộng hơn `A`. `? extends T` ở đầu ra cho phép trả subtype của `T`. Đây là thiết kế linh hoạt khi factory đóng vai trò nhận input và tạo output.

Bản trên chưa kiểm tra kết quả null; nếu contract yêu cầu không-null, thêm `Objects.requireNonNull(factory.apply(input), ...)` tương tự ví dụ Box.

### 7.2. Khi constructor có checked exception

`Supplier<T>.get()` **không khai báo `throws` checked exception**. Nếu constructor bạn muốn gọi ném `IOException` hoặc checked exception khác, bạn không thể trực tiếp viết method reference/lambda ném checked exception ấy để gán cho Supplier thông thường, trừ khi xử lý exception bên trong lambda.

Ví dụ minh họa, cần import thích hợp:

```java
Supplier<String> fromFile = () -> {
    try {
        return java.nio.file.Files.readString(
                java.nio.file.Path.of("input.txt"));
    } catch (java.io.IOException ex) {
        throw new java.io.UncheckedIOException(ex);
    }
};
```

Đây là **đoạn minh họa**, không đọc file thực trong chương trình chạy chính. Nó wrap `IOException` thành unchecked exception; đây là lựa chọn API, không phải phương pháp luôn luôn nên dùng.

Nếu checked exception là thành phần quan trọng của hợp đồng, định nghĩa functional interface riêng có `throws`, hoặc cân nhắc `Callable<T>` (`call() throws Exception`), tùy mục tiêu. Không nên âm thầm nuốt exception trong Supplier rồi trả null nếu caller cần biết thất bại.

## 8. Ví dụ Java 17 hoàn chỉnh

Tạo file **`SupplierFactoryDemo.java`**:

```java
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

public class SupplierFactoryDemo {
    static final class Resource {
        private static int nextId;
        private final int id = ++nextId;

        int id() {
            return id;
        }
    }

    static final class Config {
        private final String region;

        Config(String region) {
            this.region = region;
        }

        String region() {
            return region;
        }
    }

    record Ticket(int id, String title) {}

    static final class Box<T> {
        private final Supplier<? extends T> factory;

        Box(Supplier<? extends T> factory) {
            this.factory = Objects.requireNonNull(factory, "factory");
        }

        T newValue() {
            return Objects.requireNonNull(
                    factory.get(), "factory returned null");
        }
    }

    static <T> List<T> createMany(
            int count, Supplier<? extends T> factory) {
        if (count < 0) {
            throw new IllegalArgumentException("negative count");
        }
        Objects.requireNonNull(factory, "factory");

        List<T> values = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            values.add(Objects.requireNonNull(
                    factory.get(), "factory returned null"));
        }
        return values;
    }

    static <A, T> T createFrom(
            A input, Function<? super A, ? extends T> factory) {
        return Objects.requireNonNull(factory, "factory").apply(input);
    }

    public static void main(String[] args) {
        Box<Resource> box = new Box<>(Resource::new);

        Resource a = box.newValue();
        Resource b = box.newValue();
        System.out.println("ids=" + a.id() + "," + b.id());
        System.out.println("distinct=" + (a != b));

        List<Resource> resources = createMany(2, Resource::new);
        System.out.println("batch=" + resources.size());
        System.out.println("batch-ids=" + resources.get(0).id()
                + "," + resources.get(1).id());

        Box<Config> config = new Box<>(
                () -> new Config("ap-southeast"));
        System.out.println("region=" + config.newValue().region());

        Ticket ticket = createFrom(
                "Java", title -> new Ticket(7, title));
        System.out.println("ticket=" + ticket.id()
                + ":" + ticket.title());

        Resource singleton = new Resource();
        Box<Resource> reused = new Box<>(() -> singleton);
        System.out.println("reused="
                + (reused.newValue() == reused.newValue()));

        try {
            new Box<String>(() -> null).newValue();
        } catch (NullPointerException e) {
            System.out.println("null-error=" + e.getMessage());
        }
    }
}
```

Biên dịch và chạy trong thư mục chứa file:

```bash
javac --release 17 -Xlint:all SupplierFactoryDemo.java
java SupplierFactoryDemo
```

Ví dụ đã biên dịch thành công không có cảnh báo và cho stdout:

```text
ids=1,2
distinct=true
batch=2
batch-ids=3,4
region=ap-southeast
ticket=7:Java
reused=true
null-error=factory returned null
```

### 8.1. Diễn giải `ids=1,2` và `distinct=true`

`Resource::new` là factory gọi constructor của `Resource`. Trong chương trình chạy tuần tự này, mỗi invocation tạo một object mới và tăng `nextId`, nên có hai ID `1` và `2` và tham chiếu `a != b`.

**Không phải mọi Supplier đều bảo đảm điều này.** Bằng chứng về object mới đến từ implementation cụ thể `Resource::new`, không đến từ type `Supplier<Resource>`.

Biến đếm tĩnh `nextId` được dùng đơn giản để minh họa và **không thread-safe** nếu code được gọi đồng thời. Ứng dụng thực muốn cấp ID duy nhất trong môi trường đa luồng phải có cơ chế đồng bộ hoặc ID generator phù hợp.

### 8.2. Diễn giải `batch=2` và `batch-ids=3,4`

`createMany` gọi `factory.get()` trong một vòng lặp. Vì caller truyền `Resource::new`, hai lần gọi tiếp theo tạo resource mang ID `3` và `4`.

Đối với `count = 0`, method trả danh sách rỗng; nó vẫn kiểm tra factory khác null. Đối với `count < 0`, method ném `IllegalArgumentException`. Nếu factory ném exception ở lần thứ `k`, method không hoàn tất và exception lan ra ngoài — đoạn ví dụ này **không** tự rollback những object đã được tạo trước đó.

### 8.3. Diễn giải `region=ap-southeast`

`Config` chỉ có constructor nhận một `String`. Caller dùng:

```java
() -> new Config("ap-southeast")
```

để tạo một `Supplier<Config>` không có tham số `get()`. Factory capture cấu hình, và mỗi invocation gọi constructor `Config(String)`.

### 8.4. Diễn giải `ticket=7:Java`

`createFrom` nhận một input `String` và một `Function` dùng nó để tạo `Ticket`. Nếu `id` cũng cần được cung cấp cho mỗi lần gọi, `BiFunction<Integer,String,Ticket>` và `Ticket::new` là lựa chọn trực tiếp hơn.

### 8.5. Diễn giải `reused=true`

`new Box<>(() -> singleton)` trả **cùng object** mỗi lần gọi `.get()`. Điều này chứng minh `Supplier<T>` là interface *cung cấp giá trị*, không phải hợp đồng *luôn tạo instance mới*. Nếu tên method `newValue()` của API yêu cầu object mới, cần tài liệu hóa invariant và thiết kế factory/test bảo đảm điều đó.

### 8.6. Diễn giải `null-error=...`

Ví dụ chủ động gọi `Objects.requireNonNull(factory.get(), "factory returned null")`. Một Supplier có quyền trả null theo hợp đồng chung của `Supplier`, nhưng `Box` đã bổ sung hợp đồng **không chấp nhận null** và báo lỗi ngay tại boundary.

Nếu trường hợp “không tạo được object” là một trạng thái nghiệp vụ bình thường, cân nhắc `Supplier<Optional<T>>` hoặc một kiểu kết quả riêng, thay vì ném `NullPointerException` nếu contract không mong muốn.

## 9. Ví dụ cố tình không biên dịch: `new T()` và `T.class`

Tạo file riêng **`InvalidNewT.java`**:

```java
class InvalidNewT<T extends Number> {
    T create() {
        return new T();
    }

    Class<T> token() {
        return T.class;
    }
}
```

Biên dịch:

```bash
javac --release 17 InvalidNewT.java
```

Đây là chương trình **cố tình sai**. `javac --release 17` đã trả về hai diagnostic:

```text
error: unexpected type
  required: class
  found:    type parameter T
```

và:

```text
error: cannot select from a type variable
```

Lỗi đầu tiên đến từ `new T()`. Lỗi thứ hai đến từ `T.class`.

Sửa đúng bằng cách thay thiết kế:

```java
final class GenericCreator<T> {
    private final Supplier<? extends T> factory;

    GenericCreator(Supplier<? extends T> factory) {
        this.factory = Objects.requireNonNull(factory);
    }

    T create() {
        return factory.get();
    }
}
```

Đây là class minh họa cần import `java.util.function.Supplier` và `java.util.Objects` nếu đặt trong file riêng. Caller phải cung cấp `SomeConcreteType::new` hoặc lambda khi tạo `GenericCreator<SomeConcreteType>`.

Đừng cố bỏ bound `<T extends Number>` để sửa lỗi `new T()`; bỏ bound không làm compiler có được constructor của `T`.

## 10. Vì sao không tự dùng Reflection để gọi constructor?

Một cách khác là nhận **class token**:

```java
static <T> T createWithNoArgs(Class<T> type)
        throws ReflectiveOperationException {
    return type.getDeclaredConstructor().newInstance();
}
```

Trong ví dụ này, `Class<T>` mang runtime class cụ thể, để Reflection tra cứu constructor không tham số. Nó có khác biệt lớn với Supplier:

| Tiêu chí | `Supplier<T>` | `Class<T>` + Reflection |
| --- | --- | --- |
| Ai quyết định cách tạo object? | Caller cung cấp hành vi bất kỳ | Generic code cố tìm constructor từ class token |
| Có cần constructor không tham số? | Không; lambda có thể capture đối số | Có nếu code gọi `getDeclaredConstructor()` không tham số |
| Có thể dùng static factory/singleton/pool? | Có | Không tự động, trừ khi thiết kế thêm |
| Checked exception từ reflection | Không có ở `Supplier.get()` | Có thể có `NoSuchMethodException`, `IllegalAccessException`, `InvocationTargetException`, `InstantiationException` |
| Phạm vi truy cập | Compiler kiểm tra method reference/lambda ở nơi tạo | Reflection chịu quy tắc access/module; `getDeclaredConstructor()` không có nghĩa luôn được phép invoke |
| Unit test bằng stub/fake | Dễ truyền Supplier giả | Cần class thực hoặc thiết kế thêm abstraction |
| Độ phức tạp API | Thường ít boilerplate | Cần xử lý lỗi reflection và chính sách access |

Reflection hữu ích nếu bạn đang viết container, serializer hoặc framework cần tạo class được xác định động. Nhưng đối với việc tạo `T` mà caller đã biết constructor hoặc chiến lược xây dựng, `Supplier<T>` thường đơn giản và minh bạch hơn.

### 10.1. `Class<T>` không bảo đảm constructor tồn tại

```java
Class<T> type;
```

không chứng minh có constructor public không tham số, cũng không chứng minh type là concrete class thay vì interface/abstract class. Reflection có thể thất bại do thiếu constructor, constructor không truy cập được, class abstract hoặc exception được constructor ném.

Không nên dùng `Class.newInstance()` như giải pháp mặc định: API này đã bị deprecated; thường nên dùng `getDeclaredConstructor().newInstance()` khi thực sự chọn hướng reflection.

### 10.2. Constructor parameterized khó được suy ra hoàn toàn bằng reflection

Nếu `T` chỉ có constructor nhận `String`, generic method cần biết tên, số lượng, kiểu và giá trị tham số, cùng chính sách chọn overload. Đây là lý do truyền factory/lambda thường tốt hơn:

```java
Supplier<Config> creator = () -> new Config("ap-southeast");
```

Constructor được compiler kiểm tra tại source; generic class không cần tự giải quyết reflective overload hay access.

## 11. Đưa Supplier vào generic method thay vì giữ trong class

Không phải mọi thiết kế đều cần lưu factory thành field. Bạn có thể yêu cầu factory trực tiếp khi gọi:

```java
static <T> T make(Supplier<? extends T> supplier) {
    return Objects.requireNonNull(supplier, "supplier").get();
}
```

Dùng:

```java
StringBuilder value = make(StringBuilder::new);
```

Hoặc method tạo nhiều phần tử:

```java
static <T> List<T> createMany(
        int count, Supplier<? extends T> supplier) {
    if (count < 0) {
        throw new IllegalArgumentException("negative count");
    }
    Objects.requireNonNull(supplier, "supplier");

    List<T> values = new ArrayList<>();
    for (int i = 0; i < count; i++) {
        values.add(supplier.get());
    }
    return values;
}
```

Đoạn trên là method minh họa, cần import `java.util.List`, `java.util.ArrayList`, `java.util.Objects` và `java.util.function.Supplier`. Nếu muốn cấm null, thêm kiểm tra kết quả `get()` như chương trình hoàn chỉnh.

### 11.1. Khi nào nên injection qua constructor?

Giữ `Supplier<? extends T>` trong field khi class cần tạo nhiều `T` qua nhiều method hoặc nhiều lần sử dụng. Ưu điểm là:

- Một lần thiết lập factory, nhiều lần dùng.
- Dễ thay implementation trong unit test.
- Generic class ít phụ thuộc vào class cụ thể.
- Phù hợp inversion of control hoặc dependency injection đơn giản.

Nếu factory chỉ cần đúng một lần, truyền trực tiếp vào method có thể gọn hơn.

### 11.2. Có nên lưu `Supplier<T>` tĩnh trong generic class?

Một trường `static` không thể đơn giản “mang T của instance”:

```java
class Registry<T> {
    // static Supplier<T> factory; // Không hợp lệ:
    // cannot reference non-static type variable T.
}
```

`T` thuộc về type parameter của **class instance**, không phải một tham số riêng của static field. Nên lưu factory ở **instance field**, hoặc thiết kế static generic method có type parameter riêng:

```java
static <T> T create(Supplier<T> factory) {
    return factory.get();
}
```

Điều này tránh nhầm lẫn giữa type variable ở cấp class và cấp method.

## 12. Thiết kế hợp đồng Supplier thật rõ

Khi bạn viết một API như:

```java
T create();
```

hãy làm rõ mục đích “create” bao gồm điều gì. Một Supplier chung có thể hợp lệ nhưng không thỏa mong đợi ứng dụng.

### 12.1. Có phải trả một object mới mỗi lần?

```java
Supplier<StringBuilder> fresh = StringBuilder::new;

StringBuilder shared = new StringBuilder();
Supplier<StringBuilder> cached = () -> shared;
```

`fresh.get()` tạo builder mới; `cached.get()` trả lại cùng builder. Cả hai cùng thuộc kiểu `Supplier<StringBuilder>`.

Nếu API yêu cầu *fresh instance per call*, hãy ghi cụ thể trong JavaDoc/hợp đồng và kiểm thử. Chỉ truyền một Supplier không chứng minh freshness.

### 12.2. Supplier có thể có side effect

```java
AtomicInteger sequence = new AtomicInteger();

Supplier<Integer> nextId = sequence::incrementAndGet;
```

Ví dụ này cần import `java.util.concurrent.atomic.AtomicInteger`. Mỗi lần `get()` thay đổi state bên ngoài Supplier. Kết quả phụ thuộc số lần gọi và thứ tự gọi.

Không nên tự cho rằng gọi `.get()` hai lần sẽ cho kết quả bằng nhau hoặc không thay đổi state.

### 12.3. Supplier có thể đắt hoặc thất bại

Factory có thể mở file, thiết lập socket, gọi một service, hoặc khởi tạo resource đắt. Nếu method `createMany(1000, factory)` gọi `get()` 1000 lần, đó có thể là thao tác tốn kém.

Nếu Supplier gặp exception ở lần gọi thứ năm, những object đã được tạo trong bốn lần trước có thể cần cleanup. Generic factory code không tự có transaction/rollback chỉ vì trả `List<T>`.

### 12.4. Supplier không mặc nhiên thread-safe

Một Supplier giữ mutable state không đồng bộ có thể xảy ra data race khi nhiều thread gọi `.get()` cùng lúc. Thread safety phụ thuộc implementation của factory và các object mà nó capture.

Nếu dùng nhiều thread, hãy xem:

- Các dependency được capture có mutable không?
- Counter hoặc cache có đồng bộ không?
- Có dùng singleton mutable được chia sẻ giữa nhiều caller không?
- Logic tạo object có được phép chạy đồng thời không?

Một constructor reference đơn giản không tự động bảo đảm toàn bộ quy trình tạo object an toàn trong mọi ứng dụng.

### 12.5. Nullable factory result

`Supplier<T>` được phép trả null. Nếu API cấm null, validate tại boundary:

```java
T value = Objects.requireNonNull(
        supplier.get(), "factory returned null");
```

Nếu null đại diện cho trạng thái bình thường, có thể dùng kết quả `Optional<T>` hoặc một `Result<T,Error>` do ứng dụng định nghĩa. Đừng dùng `Optional` bừa bãi cho mọi field; cần phân biệt “không có kết quả” với “factory lỗi”.

## 13. Constructor reference, lambda và method reference khác nhau thế nào?

Ba Supplier sau đều có thể cung cấp một object kiểu `T` trong những hoàn cảnh thích hợp:

```java
Supplier<StringBuilder> a = StringBuilder::new;
Supplier<StringBuilder> b = () -> new StringBuilder();
Supplier<StringBuilder> c = FactoryMethods::newBuilder;
```

Với một class hỗ trợ:

```java
class FactoryMethods {
    static StringBuilder newBuilder() {
        return new StringBuilder();
    }
}
```

- `StringBuilder::new` là **constructor reference**.
- `() -> new StringBuilder()` là **lambda** gọi constructor.
- `FactoryMethods::newBuilder` là **method reference** tới một static factory method.

Tất cả có thể khớp `Supplier<StringBuilder>` vì method đích không cần input và trả `StringBuilder`.

### 13.1. Ưu tiên cách nào?

Dùng `SomeClass::new` khi constructor có chữ ký phù hợp và không cần thêm xử lý.

Dùng lambda khi:

- Constructor cần các tham số được capture.
- Cần validate/thay đổi logic trước hoặc sau khi tạo.
- Cần chọn một trong nhiều implementation theo cấu hình.
- Cần chuyển checked exception sang hợp đồng lỗi khác một cách có chủ đích.

Dùng method reference như `Factories::create` khi đã có static factory rõ nghĩa hoặc logic tạo object phức tạp được đóng gói ở một phương thức độc lập.

### 13.2. Method reference được giải quyết tại compile time

`SomeClass::new` không tìm constructor bằng reflection lúc gọi. Compiler dùng **target type** của functional interface để xác định constructor overload thích hợp. Vì thế:

```java
Supplier<StringBuilder> supplier = StringBuilder::new;
Function<Integer, StringBuilder> sized = StringBuilder::new;
```

có thể chọn những constructor khác nhau của `StringBuilder`, lần lượt không tham số hoặc nhận capacity.

Nếu chỉ viết:

```java
// var creator = StringBuilder::new; // Không có target type phù hợp.
```

một method/constructor reference không tự có kiểu chức năng độc lập để suy ra chỉ từ `var`; nó cần một **target functional interface** phù hợp. Hãy gán cho `Supplier<StringBuilder>` hoặc truyền vào một method có tham số nhận Supplier.

## 14. Các lựa chọn thay thế trong thiết kế hướng đối tượng

### 14.1. Truyền sẵn object thay vì factory

Nếu code chỉ cần một object để lưu hoặc xử lý:

```java
class Holder<T> {
    private final T value;

    Holder(T value) {
        this.value = value;
    }

    T get() {
        return value;
    }
}
```

Không cần Supplier khi không có nhu cầu tạo object trong tương lai.

### 14.2. Dùng factory interface domain

Nếu logic khởi tạo có nhiều thao tác hoặc checked exception riêng:

```java
interface ConnectionFactory<T> {
    T open(String endpoint) throws Exception;
}
```

Một interface có tên domain và method nhận tham số có thể rõ nghĩa hơn `Supplier<T>`. Không nên ép mọi factory vào `Supplier<T>` khi contract thật đòi endpoint, timeout, validation, resource cleanup hoặc xử lý lỗi phức tạp.

### 14.3. Template method qua abstract class

Một class generic có thể giao quyền tạo object cho subclass:

```java
abstract class Creator<T> {
    protected abstract T create();

    T make() {
        return create();
    }
}
```

Subclass concrete biết `T`:

```java
class StringCreator extends Creator<String> {
    @Override
    protected String create() {
        return "Java";
    }
}
```

Điều này hợp lệ vì subclass triển khai việc tạo `String` cụ thể. Tuy nhiên nó yêu cầu kế thừa để thay đổi hành vi. `Supplier<T>` thường linh hoạt hơn vì dùng **composition** và lambda/constructor reference.

## 15. Sai lầm phổ biến và cách sửa

### Sai lầm 1 — “Đã có `<T>` thì dùng `new T()` được”

**Sai.** `<T>` không có nghĩa compiler biết runtime class hoặc constructor cần gọi. Truyền `Supplier<T>`/`Supplier<? extends T>` từ caller hoặc thiết kế factory khác.

### Sai lầm 2 — “`T extends SomeClass` bảo đảm mọi subtype có constructor không tham số”

**Sai.** Constructor không phải member instance được thừa kế; subclass có thể chỉ có constructor nhận tham số hoặc không thể được tạo. Bound không giải quyết bài toán này.

### Sai lầm 3 — “`Supplier<T>` luôn tạo object mới”

**Sai.** `() -> existing` trả cùng object. Nếu cần một object mới mỗi lần, document và test điều kiện đó; dùng constructor reference hoặc factory có invariant tương ứng.

### Sai lầm 4 — “Constructor reference tự động dùng được với mọi constructor”

**Sai.** `Supplier<T>` không nhận tham số. `User(String)` không khớp trực tiếp `Supplier<User>`; dùng `() -> new User("name")` hoặc `Function<String,User>`.

### Sai lầm 5 — “Lưu `Class<T>` là đủ để tạo mọi loại `T`”

**Sai.** `Class<T>` không bảo đảm class concrete, không bảo đảm constructor truy cập được hoặc không tham số, và không quyết định dependency cần inject. Reflection cần xử lý thêm lỗi và quyền truy cập.

### Sai lầm 6 — “Không thể dùng Supplier với interface hoặc abstract type”

**Sai.** Supplier có thể trả bất kỳ subtype phù hợp với `T`. Ví dụ `Supplier<? extends Animal>` có thể tạo `Dog`, dù bạn không thể `new Animal()` khi `Animal` abstract. Chính factory concrete quyết định implementation.

### Sai lầm 7 — “Chỉ cần `Supplier<T>` là không bao giờ có null”

**Sai.** Supplier có thể trả null. Cần hợp đồng và kiểm tra tại boundary nếu yêu cầu non-null.

### Sai lầm 8 — “Supplier không có tham số nên không dùng được constructor có tham số”

**Sai.** Lambda có thể capture tham số đã có tại nơi tạo Supplier:

```java
Supplier<Config> factory = () -> new Config("ap-southeast");
```

Nếu giá trị phải thay đổi mỗi lần gọi, chọn `Function` hoặc `BiFunction` phù hợp.

### Sai lầm 9 — “`Supplier.get()` có thể khai báo mọi checked exception”

**Sai.** `get()` không có checked `throws`. Một lambda gán cho Supplier phải xử lý checked exception hoặc dùng API chức năng khác có `throws`.

### Sai lầm 10 — “Factory injection tự động an toàn cho đa luồng”

**Sai.** Supplier có thể capture state mutable, giữ cache hoặc dùng bộ đếm không đồng bộ. Thread safety phải được thiết kế trong factory thực tế.

### Sai lầm 11 — “`Supplier<? super T>` vẫn trả `T`”

**Sai.** Khi producer trả supertype, compiler không thể khẳng định kết quả là `T`. Nếu chỉ **đọc giá trị T**, dùng `Supplier<? extends T>` hoặc `Supplier<T>`.

### Sai lầm 12 — “Suppress lỗi `new T()` bằng annotation”

**Không thể.** Đây là **lỗi biên dịch**, không phải unchecked warning. `@SuppressWarnings` không làm `new T()` trở nên hợp lệ. Phải đổi API để cung cấp chiến lược tạo object.

## 16. Checklist triển khai trong dự án

| Câu hỏi | Thiết kế phù hợp |
| --- | --- |
| Có thật cần tạo object bên trong generic class không? | Nếu chỉ lưu giá trị có sẵn, nhận `T` trực tiếp thay vì Supplier. |
| Caller biết class concrete và constructor không tham số không? | Truyền `Concrete::new`. |
| Constructor cần cấu hình cố định từ caller không? | Truyền `() -> new Concrete(config)`. |
| Tham số khởi tạo thay đổi theo mỗi lần gọi không? | Dùng `Function`/`BiFunction` hoặc factory domain-specific. |
| Có cần factory trả subtype của `T` không? | Cân nhắc `Supplier<? extends T>`. |
| `get()` được phép trả null không? | Viết contract rõ và validate/encode absence. |
| Phải trả object mới mỗi lần không? | Document và test freshness, đừng suy từ chữ `Supplier`. |
| Có checked exception trong logic tạo không? | Wrap có chủ đích hoặc chọn functional interface có `throws`. |
| Resource tạo ra có cần close/rollback không? | Thiết kế ownership, cleanup và xử lý partial failure. |
| Gọi factory ở nhiều thread không? | Kiểm tra state capture, singleton, synchronization và thread safety. |
| Có cần dùng Reflection thực sự không? | Chỉ khi class được chọn động và chấp nhận xử lý constructor/access/exception. |
| Unit test sẽ cung cấp object thế nào? | Truyền `() -> fake`, `FakeType::new` hoặc factory có đếm số lần gọi. |

Một quy trình sửa từ code `new T()` có thể là:

1. Xác định lý do generic code muốn tạo `T` và thời điểm tạo.
2. Xác định caller nào biết concrete type và dependencies.
3. Thiết kế constructor/method nhận `Supplier<? extends T>` hoặc factory có input tương ứng.
4. Chuyển logic `new Concrete(...)` về phía caller bằng constructor reference/lambda.
5. Định nghĩa hợp đồng null, freshness, exception, ownership và concurrency.
6. Viết test với cả factory bình thường và factory lỗi/null/reuse.
7. Biên dịch với `javac --release 17 -Xlint:all` và thử luồng runtime.
8. Đảm bảo không còn cast unchecked hoặc reflection không cần thiết chỉ để khởi tạo `T`.

## 17. Câu hỏi tự kiểm tra

**Câu 1.** Vì sao class generic sau không compile?

```java
class Maker<T> {
    T make() {
        return new T();
    }
}
```

<details>
<summary>Đáp án</summary>

`T` là type variable, không phải một concrete class có constructor xác định ở source. Java không hỗ trợ generic constructor bound yêu cầu mọi subtype có constructor không tham số. Truyền một factory như `Supplier<T>` từ caller.

</details>

**Câu 2.** Nếu caller có constructor không tham số của `Invoice`, làm sao cung cấp factory cho `Box<Invoice>`?

<details>
<summary>Đáp án</summary>

Có thể viết `new Box<>(Invoice::new)` khi constructor truy cập được và type inference/target type thích hợp. `Invoice::new` tương ứng Supplier tạo `Invoice` khi `.get()` được gọi.

</details>

**Câu 3.** Một class chỉ có constructor `User(String name)`. Cần factory như thế nào?

<details>
<summary>Đáp án</summary>

Nếu tên được cố định từ đầu, dùng `() -> new User("Lan")` làm `Supplier<User>`. Nếu tên thay đổi ở mỗi lần tạo, dùng `Function<String,User>` với `User::new` và `.apply(name)`.

</details>

**Câu 4.** `Supplier<Resource>` có bảo đảm mỗi `get()` trả object mới không?

<details>
<summary>Đáp án</summary>

Không. `Resource::new` sẽ gọi constructor mỗi lần, nhưng `() -> existingResource` có thể luôn trả cùng object. Freshness là invariant bổ sung của implementation, không nằm trong interface `Supplier`.

</details>

**Câu 5.** Vì sao có thể lưu `Supplier<? extends Number>` nhận một `Supplier<Integer>`?

<details>
<summary>Đáp án</summary>

Supplier là producer: mỗi `get()` trả `Integer` và `Integer` là subtype của `Number`, nên có thể đọc kết quả như `Number`. `? extends Number` diễn đạt sự tương thích đó mà không yêu cầu `Supplier<Integer>` là subtype của `Supplier<Number>`.

</details>

**Câu 6.** Factory trả null có bị `Supplier<T>` tự từ chối không?

<details>
<summary>Đáp án</summary>

Không. Interface không cấm null. API dùng Supplier phải tự quyết định xử lý: chấp nhận null, ném lỗi rõ ràng bằng `Objects.requireNonNull`, hoặc biểu diễn trường hợp không có giá trị bằng kiểu kết quả phù hợp.

</details>

**Câu 7.** `Class<T>` và `Supplier<T>` khác nhau ở đâu?

<details>
<summary>Đáp án</summary>

`Class<T>` đại diện một class runtime để introspection/cast và có thể dùng reflection tìm constructor; nó không tự bảo đảm constructor khả dụng. `Supplier<T>` là hành vi cung cấp giá trị, có thể gọi constructor, static factory, cache hoặc logic khác. Nếu chỉ cần tạo giá trị từ caller, Supplier thường dễ dùng và dễ test hơn.

</details>

## 18. Kết luận

**Java không hỗ trợ `new T()` vì `T` là biến kiểu, không phải một tên class có constructor xác định tại điểm biên dịch.** Type erasure giải thích vì sao một class generic không có runtime specialization riêng cho mọi type argument, còn quy tắc constructor của Java không bảo đảm rằng mọi subtype `T` đều có constructor giống nhau.

Cách thiết kế thông dụng là **đưa hành vi tạo object từ caller vào generic code bằng `Supplier<T>` hoặc `Supplier<? extends T>`**:

```java
Box<StringBuilder> box = new Box<>(StringBuilder::new);
StringBuilder item = box.newValue();
```

Với constructor cần tham số cố định, dùng lambda capture; khi tham số thay đổi mỗi lần, dùng `Function`/`BiFunction` hoặc factory domain-specific. Khi thiết kế API, đừng quên rằng `Supplier.get()` có thể trả null, trả cùng object, có side effect hoặc ném unchecked exception. Generic factory chỉ thực sự hữu ích khi hợp đồng tạo object, kiểm soát dữ liệu và vòng đời object được mô tả rõ.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="generics-language-limits-002">Không dùng primitive làm đối số kiểu</a>

<details>
<summary>Xem chi tiết</summary>

# Không dùng primitive làm đối số kiểu trong Java Generics

## 1. Quy tắc cốt lõi và vấn đề thường gặp

Trong Java Generics, **đối số kiểu (*type argument*) phải là kiểu tham chiếu (*reference type*) thích hợp, không được là kiểu nguyên thủy (*primitive type*)**. Vì vậy, các khai báo này **không hợp lệ**:

```java
// CỐ Ý KHÔNG BIÊN DỊCH – minh họa các trường hợp sai:
List<int> numbers;
Set<boolean> flags;
Map<int, double> prices;
Optional<long> count;
```

`int`, `boolean`, `double`, `long` là các primitive types. Chúng không thể đứng trực tiếp trong dấu `<...>` với vai trò type argument của generic class/interface.

Thay vào đó, Java cung cấp các **wrapper classes** như `Integer`, `Boolean`, `Double`, `Long`:

```java
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

class ValidDeclarations {
    List<Integer> numbers;
    Set<Boolean> flags;
    Map<Integer, Double> prices;
    Optional<Long> count;
}
```

Đoạn này là một class hợp lệ để minh họa khai báo; nó không có `main`.

Cần ghi nhớ ngay ba ý:

1. **`List<int>` sai**, **`List<Integer>` đúng**: `Integer` là một class, không phải primitive `int`.
2. **Autoboxing/unboxing** giúp viết code với `Integer` gần giống `int`, nhưng đây là các phép chuyển đổi thật, có hệ quả về `null`, so sánh và hiệu năng.
3. **Không phải mọi thứ chứa `int` đều bị cấm trong generic**: `int[]` là **kiểu mảng tham chiếu**, nên `List<int[]>` hoàn toàn hợp lệ.

Bài học này không chỉ yêu cầu nhớ tên wrapper: nó giúp chọn đúng loại lưu trữ và hiểu những lỗi tinh vi có thể phát sinh khi dùng Generics với dữ liệu số.

## 2. Các primitive types và wrapper classes tương ứng

Java có tám primitive types. Mỗi primitive có một wrapper class trong `java.lang`:

| Primitive | Wrapper | Ví dụ dùng làm type argument |
|---|---|---|
| `byte` | `Byte` | `List<Byte>` |
| `short` | `Short` | `List<Short>` |
| `int` | `Integer` | `List<Integer>` |
| `long` | `Long` | `List<Long>` |
| `float` | `Float` | `List<Float>` |
| `double` | `Double` | `List<Double>` |
| `char` | `Character` | `List<Character>` |
| `boolean` | `Boolean` | `List<Boolean>` |

Các wrapper này là **class tham chiếu** và có thể là type argument:

```java
java.util.List<Integer> ids = new java.util.ArrayList<>();
java.util.List<Character> letters = new java.util.ArrayList<>();
java.util.List<Boolean> enabled = new java.util.ArrayList<>();
```

Trong khi các dạng sau không hợp lệ:

```java
// CỐ Ý KHÔNG BIÊN DỊCH:
java.util.List<int> ids;
java.util.List<char> letters;
java.util.List<boolean> enabled;
```

Hai nhóm kiểu phục vụ những vai trò khác nhau:

- Primitive lưu và thực hiện phép toán với giá trị theo ngữ nghĩa primitive.
- Wrapper là đối tượng tham chiếu (hoặc `null`) có phương thức, tham gia hệ thống kế thừa class/interface và làm type argument hợp lệ.

Các wrapper không làm `int` trở thành subclass của `Object`. `Integer` mới là class kế thừa từ `Number` và gián tiếp từ `Object`.

## 3. Tại sao ngôn ngữ đặt ra giới hạn này?

Java Generics thông thường dựa trên **type erasure** (*xóa kiểu*) để duy trì một mô hình class/bytecode dùng chung cho nhiều type arguments. Một `ArrayList<String>` và một `ArrayList<Integer>` sử dụng cùng generic class ở runtime, thay vì JVM tự sinh một class chuyên biệt cho từng type argument.

Với:

```java
class Box<T> {
    T value;

    Box(T value) {
        this.value = value;
    }

    T get() {
        return value;
    }
}
```

Nếu type variable `T` không có bound tường minh, erasure của `T` là `Object`. Mã bytecode đại diện cho field và method có thể làm việc qua kiểu tham chiếu `Object`, sau đó compiler đặt các phép cast cần thiết tại nơi sử dụng kiểu cụ thể.

Primitive `int` **không phải reference type** và không thể được dùng như một instance của `Object` mà không chuyển sang wrapper. Do đó, một generic API được thiết kế theo type variables tham chiếu không cho phép trực tiếp đưa `int` vào `T`.

Nói cách khác, không thể suy luận rằng:

```java
Box<int> counter; // CỐ Ý KHÔNG HỢP LỆ.
```

sẽ tạo một `Box` lưu `int` nguyên thủy hiệu quả như một field kiểu `int`. Java Generics thông thường **không chuyên biệt hóa (*specialize*)** class `Box` cho primitive bằng cú pháp đó.

Thay vào đó:

```java
Box<Integer> counter = new Box<>(10);
```

được compiler chấp nhận: `10` được chuyển thành `Integer` nếu ngữ cảnh đòi wrapper.

**Giới hạn đang nói tới nằm ở vị trí *type argument*.** Nó không cấm bạn dùng primitive làm tham số phương thức bình thường, trường dữ liệu của class không generic, kiểu trả về, hay phần tử của mảng primitive.

## 4. Đối chiếu: primitive trong method hợp lệ, primitive trong `<...>` không hợp lệ

Hai tình huống này rất dễ bị nhầm:

```java
class Arithmetic {
    int doubleIt(int value) {
        return value * 2;
    }

    java.util.List<Integer> asList(int value) {
        return java.util.List.of(value);
    }
}
```

Class trên hợp lệ:

- `doubleIt` nhận và trả `int` trực tiếp, không có type argument nào.
- `asList` nhận `int` nhưng trả `List<Integer>`. Generic method `List.of` suy luận wrapper `Integer` trong ngữ cảnh phù hợp và boxing diễn ra ở bước truyền phần tử.

Ngược lại:

```java
// CỐ Ý KHÔNG BIÊN DỊCH:
class Broken<T extends int> { }
```

`T extends int` không hợp lệ vì `int` không phải reference type dùng được làm bound. Nếu cần giới hạn các wrapper số, có thể viết `<T extends Number>`:

```java
class NumericBox<T extends Number> {
    T value;
}
```

Sau đó `NumericBox<Integer>` và `NumericBox<Double>` hợp lệ; `NumericBox<int>` vẫn không hợp lệ.

## 5. Ví dụ Java 17 chạy được: `List<Integer>` thay `List<int>`

Tạo tệp **`WrapperListDemo.java`**:

```java
import java.util.ArrayList;
import java.util.List;

public class WrapperListDemo {
    public static void main(String[] args) {
        List<Integer> scores = new ArrayList<>();

        scores.add(8);
        scores.add(9);
        scores.add(10);

        int total = 0;

        for (int score : scores) {
            total += score;
        }

        System.out.println(scores);
        System.out.println(total);
        System.out.println(scores.get(0) + 2);
    }
}
```

Biên dịch và chạy bằng JDK 17 trở lên:

```bash
javac WrapperListDemo.java
java WrapperListDemo
```

Stdout:

```text
[8, 9, 10]
27
10
```

Giải thích:

1. `scores` khai báo `List<Integer>`, **không** phải `List<int>`.
2. `scores.add(8)` hợp lệ nhờ **boxing**: một giá trị `int` được chuyển sang wrapper `Integer` ở nơi cần.
3. Vòng `for (int score : scores)` đọc mỗi `Integer` và **unbox** thành `int`.
4. `total` là `int`, nên các phép cộng dùng phép toán số nguyên thông thường.
5. `scores.get(0) + 2` yêu cầu giá trị số; `Integer` có thể được unbox để tính ra `10`.

Code này được kiểm tra generic ở compile time. Nếu thử `scores.add("text")`, compiler từ chối vì `String` không tương thích với `Integer`.

## 6. Autoboxing và unboxing hoạt động thế nào?

**Boxing conversion** chuyển một giá trị primitive sang kiểu wrapper tương ứng trong các ngữ cảnh mà Java cho phép.

**Unboxing conversion** chuyển một wrapper sang giá trị primitive tương ứng.

Ví dụ:

```java
Integer boxed = 42; // Boxing từ int sang Integer.
int primitive = boxed; // Unboxing từ Integer sang int.
```

Có thể xem biểu diễn minh họa gần tương đương:

```java
Integer boxed = Integer.valueOf(42);
int primitive = boxed.intValue();
```

Compiler có thể tạo lời gọi method hỗ trợ tương ứng và tối ưu hóa chúng theo ngữ cảnh. Ví dụ trên nhằm diễn giải ngữ nghĩa, **không phải cam kết chính xác mọi lệnh bytecode trong mọi bản JDK/JIT**.

### Chương trình quan sát chuyển đổi

Tạo **`BoxingConversionDemo.java`**:

```java
public class BoxingConversionDemo {
    public static void main(String[] args) {
        int original = 21;

        Integer boxed = original;
        int restored = boxed;

        Double pi = 3.5;
        double doubled = pi * 2;

        Boolean enabled = true;
        boolean active = enabled;

        System.out.println(boxed);
        System.out.println(restored);
        System.out.println(doubled);
        System.out.println(active);
    }
}
```

Lệnh:

```bash
javac BoxingConversionDemo.java
java BoxingConversionDemo
```

Stdout:

```text
21
21
7.0
true
```

Với `Double pi = 3.5`, compiler chọn wrapper `Double` từ literal `double`. Khi tính `pi * 2`, phép toán cần giá trị số primitive, nên có unboxing; kiểu kết quả là `double`.

Những chuyển đổi này giúp API Generic dễ dùng, nhưng **không miễn phí về mặt ngữ nghĩa**: wrapper có thể là `null`, có identity của reference và có thể có chi phí lưu trữ khác primitive.

## 7. `null` là nguy cơ đặc biệt khi unboxing

Primitive `int` không thể có giá trị `null`. `Integer` là reference type, nên có thể là `null`.

Ví dụ:

```java
Integer count = null;
// int total = count; // Có thể ném NullPointerException.
```

Đây không phải lỗi compiler; phép gán sang `int` có thể biên dịch thành công, rồi thất bại khi chạy nếu `count == null`.

Tạo tệp **`UnboxingNullDemo.java`**:

```java
import java.util.ArrayList;
import java.util.List;

public class UnboxingNullDemo {
    public static void main(String[] args) {
        List<Integer> values = new ArrayList<>();
        values.add(10);
        values.add(null);

        System.out.println(values.size());
        System.out.println(values.get(0));

        try {
            int number = values.get(1);
            System.out.println(number);
        } catch (NullPointerException ex) {
            System.out.println("NullPointerException");
        }
    }
}
```

Biên dịch/chạy:

```bash
javac UnboxingNullDemo.java
java UnboxingNullDemo
```

Stdout:

```text
2
10
NullPointerException
```

`ArrayList<Integer>` có thể chứa `null`. Khi `values.get(1)` được dùng ở vị trí `int`, compiler thực hiện unboxing, và giá trị `null` không thể được giải tham chiếu để lấy primitive.

Không phải mọi collection cho phép `null`:

- `new ArrayList<Integer>()` cho phép `null`.
- `List.of(1, null)` ném `NullPointerException` ngay khi tạo danh sách.
- `List.copyOf` từ chối `null` trong nguồn.

**Thực hành tốt:** Quyết định rõ `null` có phải giá trị hợp lệ trong hợp đồng dữ liệu hay không. Nếu không, kiểm tra hoặc dùng collection/factory có chính sách ngăn `null`. Nếu có, phải xử lý `null` trước unboxing.

## 8. Wrapper không phải primitive: so sánh bằng `==` và `equals`

Một khác biệt đáng chú ý là `Integer` là object tham chiếu.

```java
Integer a = Integer.valueOf(1000);
Integer b = Integer.valueOf(1000);

System.out.println(a.equals(b));
```

Dòng in `true`, vì hai đối tượng biểu diễn cùng giá trị số.

Nhưng `a == b` trong trường hợp **cả hai biến đều là wrapper tham chiếu** có ý nghĩa kiểm tra **cùng object hay không**, không phải so sánh số học. Không nên dựa vào việc JVM cache hay không cache từng giá trị wrapper để đưa ra một kết luận phổ quát.

Đặc biệt, với `Integer` nằm trong khoảng nhỏ, Java có quy tắc và cách triển khai boxing/cache khiến một số phép `==` tình cờ trả `true`; ngoài khoảng đó, hành vi identity không nên dùng làm hợp đồng. Dùng `.equals(...)` hoặc so sánh primitive sau khi xử lý `null`.

Tệp **`WrapperEqualityDemo.java`**:

```java
import java.util.Objects;

public class WrapperEqualityDemo {
    public static void main(String[] args) {
        Integer first = 1000;
        Integer second = 1000;

        System.out.println(first.equals(second));
        System.out.println(Objects.equals(first, second));
        System.out.println(first.intValue() == second.intValue());

        Integer absent = null;
        System.out.println(Objects.equals(absent, null));
    }
}
```

Biên dịch/chạy:

```bash
javac WrapperEqualityDemo.java
java WrapperEqualityDemo
```

Stdout:

```text
true
true
true
true
```

Ví dụ cố tình **không in `first == second`** để tránh khiến người học dựa vào wrapper identity. Nếu quan tâm giá trị, hãy dùng `.equals` hoặc `Objects.equals` khi có thể có `null`; nếu quan tâm giá trị nguyên thủy và đã chắc chắn không null, unbox rồi so sánh primitive.

Cũng nên tránh trộn `Integer` nullable vào biểu thức với literal `int` mà không cân nhắc unboxing. Ví dụ `Integer x = null; boolean zero = (x == 0);` có thể ném `NullPointerException`.

## 9. Type inference với primitive: compiler thường chọn wrapper

Một generic method khai báo tham số kiểu `T`:

```java
static <T> T identity(T value) {
    return value;
}
```

Khi gọi:

```java
Integer result = identity(123);
```

compiler không suy luận `T = int` như một primitive type argument bất hợp lệ. Trong ngữ cảnh thích hợp, nó sử dụng boxing và suy luận một reference type, ở đây là `Integer`.

Tạo tệp **`GenericInferencePrimitiveDemo.java`**:

```java
import java.util.List;

public class GenericInferencePrimitiveDemo {

    static <T> T identity(T value) {
        return value;
    }

    static <T> List<T> singleton(T value) {
        return List.of(value);
    }

    public static void main(String[] args) {
        Integer boxed = identity(7);
        int primitive = identity(7);

        List<Integer> numbers = singleton(9);

        System.out.println(boxed);
        System.out.println(primitive);
        System.out.println(numbers);

        System.out.println(numbers.get(0) + 1);
    }
}
```

Lệnh:

```bash
javac GenericInferencePrimitiveDemo.java
java GenericInferencePrimitiveDemo
```

Stdout:

```text
7
7
[9]
10
```

Diễn giải:

1. Generic method `<T> T identity(T value)` đòi `T` thuộc hệ kiểu tham chiếu.
2. Literal `7` là `int`, được boxing phù hợp với suy luận generic.
3. Kết quả có thể gán cho `Integer`; nếu gán cho `int`, unboxing cung cấp giá trị primitive.
4. `singleton(9)` suy luận `T = Integer`, tạo `List<Integer>`.

**Không có chuyện compiler âm thầm tạo `List<int>` rồi che nó đi.** Các type arguments được biểu diễn theo hệ thống kiểu tham chiếu.

## 10. `List.of`, `Arrays.asList` và bẫy mảng primitive

Khi muốn biến nhiều số nguyên thành `List<Integer>`, hãy cẩn thận nếu nguồn ban đầu là `int[]`.

Xét:

```java
int[] raw = {1, 2, 3};

var fromArray = java.util.Arrays.asList(raw);
```

`Arrays.asList` là generic varargs method nhận **đối tượng** làm phần tử. `int[]` là một object mảng; các giá trị primitive **bên trong** mảng không được tự động boxing thành từng `Integer` nhờ truyền toàn bộ `int[]` vào varargs.

Kết quả của `Arrays.asList(raw)` là `List<int[]>` với **một phần tử**, chính là object mảng `raw`, chứ không phải `List<Integer>` có ba phần tử.

Tạo tệp **`PrimitiveArrayListTrapDemo.java`**:

```java
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

public class PrimitiveArrayListTrapDemo {
    public static void main(String[] args) {
        int[] raw = {1, 2, 3};

        List<int[]> nested = Arrays.asList(raw);
        List<Integer> boxed = IntStream.of(raw)
                .boxed()
                .toList();

        System.out.println(nested.size());
        System.out.println(nested.get(0).length);
        System.out.println(boxed);
        System.out.println(boxed.size());
    }
}
```

Biên dịch/chạy:

```bash
javac PrimitiveArrayListTrapDemo.java
java PrimitiveArrayListTrapDemo
```

Stdout:

```text
1
3
[1, 2, 3]
3
```

Điểm cần nhớ:

- `int[]` là **reference type**, vì mảng là object; do đó `List<int[]>` hợp lệ.
- `Arrays.asList(raw)` đưa nguyên object `int[]` vào list.
- `IntStream.of(raw).boxed()` tạo stream wrapper `Integer` từ các giá trị `int`.
- `.toList()` (Java 16+) tạo một list không thể chỉnh sửa qua API chuẩn; không nên gọi `add` lên kết quả.

Nếu cần `ArrayList<Integer>` mutable, dùng `collect(Collectors.toCollection(ArrayList::new))` sau `.boxed()` hoặc tạo `new ArrayList<>(boxed)`.

## 11. Khi `List<int[]>` là thiết kế đúng

Giới hạn generic primitive không cấm dùng **mảng primitive như một phần tử tham chiếu**.

Ví dụ:

```java
java.util.List<int[]> rows = new java.util.ArrayList<>();
rows.add(new int[] {1, 2});
rows.add(new int[] {3, 4});
```

List trên có hai phần tử kiểu `int[]`, không phải bốn phần tử kiểu `Integer`. Nó phù hợp khi mỗi phần tử là một khối dữ liệu số nguyên, ví dụ một hàng ma trận hoặc một mẫu dữ liệu.

Ví dụ độc lập **`PrimitiveArrayElementDemo.java`**:

```java
import java.util.ArrayList;
import java.util.List;

public class PrimitiveArrayElementDemo {
    public static void main(String[] args) {
        List<int[]> rows = new ArrayList<>();

        rows.add(new int[] {1, 2});
        rows.add(new int[] {3, 4, 5});

        for (int[] row : rows) {
            int sum = 0;

            for (int value : row) {
                sum += value;
            }

            System.out.println(sum);
        }
    }
}
```

Lệnh:

```bash
javac PrimitiveArrayElementDemo.java
java PrimitiveArrayElementDemo
```

Stdout:

```text
3
12
```

Mỗi `row` là object mảng chứa primitive `int`. Vì `int[]` là reference type, nó có thể điền vào type argument của `List`. Các giá trị `int` bên trong vẫn nằm trong array primitive, không cần boxing từng phần tử ở lớp lưu trữ mảng.

Cần nhớ thêm rằng list **không sao chép sâu** các mảng được thêm vào. Nếu caller thay `rows.get(0)[0]`, nội dung mảng cũng thay đổi.

## 12. Giới hạn với nhiều kiểu generic khác nhau

Không chỉ `List` mới cấm primitive type arguments.

Các khai báo sau đều sai:

```java
// CỐ Ý KHÔNG HỢP LỆ:
java.util.Set<char> letters;
java.util.Map<int, String> namesById;
java.util.function.Supplier<double> supplier;
java.util.Optional<boolean> present;
```

Các phiên bản hợp lệ:

```java
java.util.Set<Character> letters;
java.util.Map<Integer, String> namesById;
java.util.function.Supplier<Double> supplier;
java.util.Optional<Boolean> present;
```

Tương tự với class tự viết:

```java
class Pair<A, B> {
    A left;
    B right;
}
```

Ta có thể dùng `Pair<Integer, Double>`, nhưng không được dùng `Pair<int, double>`.

Đây là **quy tắc của hệ thống type arguments**, không phải quy tắc riêng của Java Collections Framework.

Ngoài ra, generic method declaration:

```java
static <T> T first(T a, T b) {
    return a;
}
```

không thể được gọi bằng type witness primitive:

```java
// CỐ Ý KHÔNG HỢP LỆ:
Utils.<int>first(1, 2);
```

Nếu cần viết rõ type witness, dùng reference type như `<Integer>`, với điều kiện method được truy cập hợp lệ trong code thật.

## 13. Numeric wrappers và kiểu chung `Number`

Các wrapper số như `Byte`, `Short`, `Integer`, `Long`, `Float`, `Double` đều kế thừa từ `Number`, do đó có thể dùng một danh sách phần tử kiểu `Number` để chứa nhiều wrapper.

Tệp **`NumberCollectionDemo.java`**:

```java
import java.util.ArrayList;
import java.util.List;

public class NumberCollectionDemo {

    static double total(List<? extends Number> values) {
        double result = 0.0;

        for (Number number : values) {
            result += number.doubleValue();
        }

        return result;
    }

    public static void main(String[] args) {
        List<Number> mixed = new ArrayList<>();

        mixed.add(2);
        mixed.add(3.5);
        mixed.add(4L);

        System.out.println(mixed);
        System.out.println(total(mixed));
        System.out.println(total(List.of(1, 2, 3)));
    }
}
```

Biên dịch/chạy:

```bash
javac NumberCollectionDemo.java
java NumberCollectionDemo
```

Stdout:

```text
[2, 3.5, 4]
9.5
6.0
```

Các literal `2`, `3.5`, `4L` có thể được boxing thành `Integer`, `Double`, `Long`; tất cả phù hợp với `Number`.

Với `List<? extends Number>`, phương thức `total` chỉ cần **đọc** phần tử dưới dạng `Number`, nên nó có thể nhận `List<Integer>`, `List<Double>` hoặc `List<Number>`. Đây là ứng dụng PECS (*Producer Extends*) với wrapper numbers.

**Không nhầm `List<Integer>` với subtype của `List<Number>`.** Generic Java mặc định invariant:

```java
List<Integer> integers = List.of(1, 2);
// List<Number> numbers = integers; // CỐ Ý KHÔNG HỢP LỆ.
List<? extends Number> readable = integers; // Hợp lệ.
```

Biến `readable` có thể đọc `Number`, nhưng không thể tùy tiện thêm một `Double` vào danh sách vốn có thể là `List<Integer>`.

## 14. Bẫy conversion: không có mọi kiểu chuyển đổi trong một bước boxing

Một số người kỳ vọng một wrapper generic tự hỗ trợ tất cả các numeric widenings như primitive.

Ví dụ `Long` và `Integer` là **hai wrapper classes khác nhau**:

```java
java.util.List<Long> ids = new java.util.ArrayList<>();
ids.add(1L); // Hợp lệ.

// ids.add(1); // Không hợp lệ: int không tự boxing thành Long.
```

Với lời gọi `ids.add(1)`, compiler không tự động thực hiện chuỗi “`int` -> `long` -> `Long`” chỉ vì đích là `Long`; Java không cung cấp mọi kết hợp widening-then-boxing như một conversion mặc định tại lời gọi này.

Sửa bằng:

```java
ids.add(1L);
ids.add(Long.valueOf(1));
```

Đối với hàm nhận `Number`, boxing một `int` thành `Integer` rồi widening reference sang `Number` là một trường hợp được phép:

```java
java.util.List<Number> numbers = new java.util.ArrayList<>();
numbers.add(1); // Integer là Number.
```

Khi gặp lỗi compiler với wrapper, hãy phân biệt **primitive widening**, **boxing**, **reference widening**, và các tổ hợp conversion được JLS thực sự cho phép. Đừng giả định mọi cú pháp nhìn giống numeric conversion đều hợp lệ.

## 15. Wrapper có ảnh hưởng hiệu năng và bộ nhớ không?

Khi cần lưu hàng triệu giá trị số:

- `int[]` lưu primitive `int` trong một object mảng.
- `List<Integer>` lưu các **reference** tới wrapper objects thông qua một implementation thích hợp, điển hình là `ArrayList` có mảng nội bộ chứa references.
- Boxing có thể tạo object wrapper hoặc tận dụng object được cache theo quy tắc/triển khai; không nên tự suy ra số lượng cấp phát tuyệt đối từ một đoạn code.
- Unboxing có thể thêm thao tác chuyển đổi, và nếu dữ liệu có `null` sẽ phát sinh `NullPointerException`.
- Trong các vòng lặp tính toán lớn, chi phí nhớ/cache/GC/boxing có thể ảnh hưởng đáng kể; nhưng các tối ưu hóa của JIT có thể khiến thực tế phụ thuộc workload.

Vì vậy `List<Integer>` không nhất thiết là cách tốt nhất để lưu một lượng lớn primitive `int` nếu mục tiêu là hiệu năng/mật độ dữ liệu. Nhưng nó thường là lựa chọn phù hợp khi cần các API Collections chuẩn: thay đổi kích thước, `contains`, `Map`, tương tác với code generic, v.v.

**Không kết luận “List luôn chậm”** chỉ từ việc có boxing. Muốn tối ưu một đường xử lý số, hãy dùng benchmark đáng tin như JMH trên workload đại diện.

## 16. Khi cần xử lý primitive hàng loạt, dùng `IntStream`, `LongStream`, `DoubleStream`

Java Stream API có các phiên bản **chuyên cho primitive**:

- `IntStream` xử lý `int`.
- `LongStream` xử lý `long`.
- `DoubleStream` xử lý `double`.

Chúng giúp thực hiện nhiều thao tác số học mà không cần trình bày dữ liệu theo `Stream<Integer>` hoặc `List<Integer>` ở mọi bước.

Tạo tệp **`PrimitiveStreamDemo.java`**:

```java
import java.util.List;
import java.util.stream.IntStream;

public class PrimitiveStreamDemo {
    public static void main(String[] args) {
        int total = IntStream.rangeClosed(1, 4).sum();
        double average = IntStream.of(2, 4, 6)
                .average()
                .orElse(0.0);

        List<Integer> boxed =
                IntStream.of(3, 5, 7)
                        .boxed()
                        .toList();

        System.out.println(total);
        System.out.println(average);
        System.out.println(boxed);
    }
}
```

Biên dịch/chạy:

```bash
javac PrimitiveStreamDemo.java
java PrimitiveStreamDemo
```

Stdout:

```text
10
4.0
[3, 5, 7]
```

`IntStream.sum()` trả `int`, còn `average()` trả `OptionalDouble` vì trung bình có thể vắng mặt khi stream rỗng.

`.boxed()` chuyển dòng primitive `int` thành `Stream<Integer>`; `.toList()` tạo danh sách wrapper không sửa được qua API chuẩn trong Java 17. Nếu cần tiếp tục xử lý primitive, nên giữ primitive stream đến khi thật sự cần chuyển sang object API.

**Không có `Stream<int>`**. Cú pháp đúng là `IntStream`, hoặc `Stream<Integer>` khi muốn làm việc với wrappers.

## 17. Primitive-specialized Optional và functional interfaces

Giới hạn type arguments cũng giải thích vì sao Java cung cấp nhiều API chuyên biệt cho primitive.

Ví dụ:

| Mục tiêu | Generic reference API | Primitive-specialized API |
|---|---|---|
| Giá trị số nguyên có thể vắng mặt | `Optional<Integer>` | `OptionalInt` |
| Giá trị số thực double có thể vắng mặt | `Optional<Double>` | `OptionalDouble` |
| Giá trị long có thể vắng mặt | `Optional<Long>` | `OptionalLong` |
| Hàm trả `int` không tham số | `Supplier<Integer>` | `IntSupplier` |
| Hàm nhận `int` | `Consumer<Integer>` | `IntConsumer` |
| Predicate nhận `int` | `Predicate<Integer>` | `IntPredicate` |
| Ánh xạ `int` sang `int` | `Function<Integer,Integer>` | `IntUnaryOperator` |
| Hàm nhận `int`, trả object `R` | `Function<Integer,R>` | `IntFunction<R>` |
| Hàm nhận object `T`, trả `int` | `Function<T,Integer>` | `ToIntFunction<T>` |

Các kiểu chuyên biệt mô tả primitive ngay trong chữ ký API và có thể tránh boxing/unboxing trong một số thao tác.

Tệp **`PrimitiveFunctionalDemo.java`**:

```java
import java.util.OptionalInt;
import java.util.function.IntPredicate;
import java.util.function.IntUnaryOperator;

public class PrimitiveFunctionalDemo {
    public static void main(String[] args) {
        IntUnaryOperator twice = n -> n * 2;
        IntPredicate positive = n -> n > 0;

        OptionalInt result = OptionalInt.of(twice.applyAsInt(6));

        System.out.println(result.getAsInt());
        System.out.println(positive.test(10));
        System.out.println(positive.test(-1));
    }
}
```

Biên dịch/chạy:

```bash
javac PrimitiveFunctionalDemo.java
java PrimitiveFunctionalDemo
```

Stdout:

```text
12
true
false
```

`IntUnaryOperator` và `IntPredicate` nhận/trả primitive `int` theo chữ ký; chúng **không** phải generic interface được tham số hóa bằng `<int>`.

`OptionalInt` không cần type argument; nó đã được thiết kế chuyên biệt cho `int`. Nếu cần giá trị vắng mặt, `OptionalInt.empty()` biểu diễn điều đó mà không dùng `null` như một giá trị `int`.

## 18. Khi nào nên chọn `Integer[]`, `int[]` và `List<Integer>`?

Cả ba đều hợp lệ, nhưng ý nghĩa khác nhau.

| Loại | Phần tử | Kích thước | `null` ở cấp phần tử | Hợp đồng chính |
|---|---|---|---|---|
| `int[]` | Primitive `int` | Cố định | Không thể | Mảng primitive, runtime biết component `int` |
| `Integer[]` | Reference `Integer` | Cố định | Có thể | Mảng reference wrapper |
| `List<Integer>` | Reference `Integer` | Tùy implementation | Tùy implementation | Collection generic, nhiều tiện ích |
| `List<int[]>` | Reference tới object `int[]` | Tùy implementation | Tùy implementation | Mỗi phần tử là một mảng primitive |
| `IntStream` | Dòng giá trị `int` | Không biểu diễn trực tiếp số slot có thể sửa | Không có phần tử null | Pipeline thao tác số nguyên |

**Cần số slot cố định và lưu primitive trực tiếp?** `int[]` thường là lựa chọn tự nhiên.

**Cần nhiều tiện ích Collections, thay đổi số phần tử, làm key/value hoặc dữ liệu generic?** `List<Integer>` hoặc các container wrapper phù hợp.

**Cần các dãy primitive theo từng nhóm?** `List<int[]>` có thể đúng, nhưng phải cân nhắc mutation của mảng con.

**Cần pipeline tính toán?** `IntStream` giúp mô tả thao tác số mà không phải chuyển tất cả giá trị sang wrappers.

`Integer[]` khác `int[]` ở runtime component type, ngữ nghĩa null và nơi áp dụng boxing/unboxing.

## 19. Ví dụ xử lý dữ liệu: chọn primitive array hoặc generic list

Tệp **`PrimitiveVsWrapperStorageDemo.java`**:

```java
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PrimitiveVsWrapperStorageDemo {

    static int sumArray(int[] values) {
        int sum = 0;
        for (int value : values) {
            sum += value;
        }
        return sum;
    }

    static int sumList(List<Integer> values) {
        int sum = 0;
        for (Integer value : values) {
            sum += value;
        }
        return sum;
    }

    public static void main(String[] args) {
        int[] primitive = {4, 5, 6};
        List<Integer> wrappers =
                new ArrayList<>(List.of(4, 5, 6));

        System.out.println(Arrays.toString(primitive));
        System.out.println(wrappers);
        System.out.println(sumArray(primitive));
        System.out.println(sumList(wrappers));

        wrappers.add(7);
        System.out.println(wrappers.size());
    }
}
```

Lệnh:

```bash
javac PrimitiveVsWrapperStorageDemo.java
java PrimitiveVsWrapperStorageDemo
```

Stdout:

```text
[4, 5, 6]
[4, 5, 6]
15
15
4
```

Ví dụ này không định đo tốc độ. Nó chỉ cho thấy:

- `sumArray` đọc primitive trực tiếp.
- `sumList` unbox `Integer` mỗi lần đưa vào phép cộng, và cần có chính sách rõ với `null`.
- `ArrayList` có thể thêm phần tử, còn kích thước mảng `int[]` được cố định khi tạo.

Nếu phần tử `null` xuất hiện trong `wrappers`, `sumList` có thể ném `NullPointerException`. Ngữ nghĩa này quan trọng hơn việc hai ví dụ đều in tổng `15` với dữ liệu hợp lệ.

## 20. Không được nhầm wrapper và class runtime của primitive

`Integer.class` là class token cho wrapper reference type `Integer`. Primitive type `int` có class literal hợp lệ là `int.class`, nhưng điều đó **không** khiến `int` trở thành reference type dùng được cho Generics.

Một class token có thể đại diện cho primitive type nhằm phục vụ reflection, trong khi generic type arguments vẫn chịu điều kiện là kiểu tham chiếu.

Ví dụ:

```java
System.out.println(int.class.isPrimitive());
System.out.println(Integer.class.isPrimitive());
```

Nếu đặt trong method Java, hai dòng trên lần lượt in:

```text
true
false
```

**Điểm cần nhớ:** Khả năng runtime/reflection có đối tượng `Class` đại diện cho primitive không thay đổi cú pháp `List<int>` thành hợp lệ.

Khi viết một API cần runtime class token:

```java
static <T> T checkedCast(Object value, Class<T> type) {
    return type.cast(value);
}
```

`T` vẫn là type variable generic tham chiếu. `Class<T>` không phải một cơ chế cho phép khai báo `T = int` bằng type argument primitive. Nếu API cần xử lý `int` trực tiếp, nên dùng primitive-specific signature hoặc wrapper `Integer` theo thiết kế.

## 21. Những lỗi phổ biến

**Lỗi 1 — “`List<int>` viết ngắn hơn `List<Integer>`.”** Không phải cú pháp hợp lệ. Type argument phải là reference type phù hợp.

**Lỗi 2 — “`int` kế thừa `Number`.”** Sai. `Integer` là subclass của `Number`; primitive `int` không phải class.

**Lỗi 3 — “`List<Integer>` lưu `int` trực tiếp như `int[]`.”** Không. Collection generic làm việc với wrappers ở lớp API, trong khi mảng primitive có component `int`.

**Lỗi 4 — “Boxing và unboxing hoàn toàn miễn phí.”** Không thể khẳng định. Có thể có chi phí object/conversion, tùy code và tối ưu hóa runtime.

**Lỗi 5 — “`Integer` không thể `null` vì biểu diễn `int`.”** Sai. Wrapper là reference và có thể `null`.

**Lỗi 6 — “Có thể dùng `==` an toàn để so giá trị hai `Integer`.”** Nếu cả hai toán hạng là references, `==` thường kiểm tra identity, không phải numeric equality. Dùng `.equals` hoặc primitive sau khi kiểm tra null.

**Lỗi 7 — “`List<Integer>` là `List<Number>` vì `Integer` là `Number`.”** Sai. Generic Java invariant; dùng wildcard phù hợp nếu API chỉ cần đọc kiểu cha.

**Lỗi 8 — “`Arrays.asList(int[])` trả list các `Integer`.”** Sai. `int[]` được coi là một phần tử reference, nên thường cho `List<int[]>` một phần tử.

**Lỗi 9 — “`List<int[]>` cũng bị cấm vì có chữ `int`.”** Sai. `int[]` là **array reference type**, hợp lệ làm type argument.

**Lỗi 10 — “`List<Long>` tự nhận literal `1` vì số nào cũng ép được.”** Không. Một `int` không tự chuyển qua mọi chuỗi widening-plus-boxing thành `Long`; dùng `1L` khi muốn `Long`.

**Lỗi 11 — “`Optional<int>` hợp lệ vì `Optional` dành cho giá trị có thể vắng mặt.”** Sai. Có `OptionalInt` chuyên biệt hoặc `Optional<Integer>`.

**Lỗi 12 — “`Stream<int>` giúp tránh boxing.”** Không hợp lệ. Dùng `IntStream`, không phải parameterized `Stream<int>`.

**Lỗi 13 — “Generics bị xóa nghĩa là compiler không kiểm tra được wrapper.”** Sai. `List<Integer>` có compile-time type checking mạnh; vấn đề riêng là primitive type arguments không được hỗ trợ.

**Lỗi 14 — “Primitive không được dùng ở bất kỳ vị trí nào trong generic class.”** Sai. Một generic class vẫn có thể khai báo `int count`, nhận tham số `double rate`, hoặc dùng `int[]`; điều bị giới hạn ở đây là primitive làm *type argument* hoặc type-variable bound không hợp lệ.

## 22. Bài tập tự kiểm tra có đáp án

**Câu 1.** Trong các khai báo sau, dòng nào hợp lệ?

```java
List<int> a;          // A
List<Integer> b;      // B
List<int[]> c;        // C
```

**Đáp án:** B và C hợp lệ. A không hợp lệ. Ví dụ cần import `java.util.List` và đặt trong class/method.

**Câu 2.** `Integer x = 5; int y = x;` thực hiện những chuyển đổi gì?

**Đáp án:** Boxing `int` thành `Integer` ở phép gán đầu và unboxing `Integer` thành `int` ở phép gán sau.

**Câu 3.** `Integer x = null; int y = x;` lỗi ở giai đoạn nào?

**Đáp án:** Có thể biên dịch, nhưng ném `NullPointerException` khi unboxing tại runtime.

**Câu 4.** `List<Integer>` có tự động gán cho `List<Number>` không?

**Đáp án:** Không; Java Generics invariant. Nếu chỉ đọc số, có thể dùng `List<? extends Number>`.

**Câu 5.** `Arrays.asList(new int[] {1,2,3}).size()` là bao nhiêu?

**Đáp án:** `1`, vì một mảng `int[]` được xem như một object phần tử của generic varargs.

**Câu 6.** Cần `List<Integer>` từ một `int[]`, có thể dùng gì trên Java 17?

**Đáp án:** `Arrays.stream(source).boxed().toList()` hoặc `IntStream.of(source).boxed().toList()`.

**Câu 7.** `List<Long> ids = new ArrayList<>(); ids.add(1);` hợp lệ không?

**Đáp án:** Không. Literal `1` có kiểu `int`; dùng `1L` hoặc `Long.valueOf(1)`.

**Câu 8.** `List<int[]>` lưu mỗi giá trị như thế nào?

**Đáp án:** Mỗi **phần tử của List** là một object mảng `int[]`, và các `int` nằm bên trong từng mảng ấy.

**Câu 9.** Nếu cần tổng các số nguyên lớn, tại sao nên cân nhắc `int[]` hoặc `IntStream`?

**Đáp án:** Vì chúng hỗ trợ thao tác primitive trực tiếp, có thể tránh một phần chi phí boxing/unboxing. Cần đo thực tế trước khi kết luận hiệu năng.

**Câu 10.** Vì sao `Integer.class` hoặc `int.class` không làm `List<int>` hợp lệ?

**Đáp án:** Reflection class tokens không thay đổi quy tắc type argument của ngôn ngữ; `int` vẫn là primitive type.

**Câu 11.** Cần optional value kiểu primitive `int`, có các lựa chọn nào?

**Đáp án:** `OptionalInt` cho API chuyên primitive hoặc `Optional<Integer>` khi cần generic wrapper API.

**Câu 12.** Có nên so sánh hai wrapper bằng `==` khi nghiệp vụ cần giá trị số?

**Đáp án:** Không nên. Dùng `Objects.equals(a,b)` để xử lý nullable references hoặc kiểm tra null rồi so sánh primitive.

## 23. Checklist thiết kế API liên quan primitive

Khi thiết kế một class/method Generic, hãy xét:

1. **Type argument có phải primitive không?** Nếu có, thay bằng wrapper hoặc chọn API chuyên primitive.
2. **Có cần tập hợp kích thước động không?** `List<Integer>`, `Set<Long>`, `Map<Integer,Double>` có thể phù hợp.
3. **Có cần dữ liệu primitive dày đặc hoặc thao tác số học nhiều không?** Xem xét `int[]`, `double[]`, `IntStream`, `DoubleStream`.
4. **Có thể nhận `null` không?** Nếu wrapper nullable, thiết kế chính sách trước khi unbox.
5. **Có đang so sánh wrapper bằng `==` không?** Nếu mục tiêu là value equality, sửa thành `.equals` hoặc `Objects.equals`.
6. **Có chuyển `int[]` sang `List<Integer>` không?** Đừng dùng nhầm `Arrays.asList(int[])`; cần boxing từng phần tử.
7. **Có đang giả định `List<Integer>` là `List<Number>` không?** Sử dụng variance/wildcards phù hợp.
8. **Có cần loại bỏ giá trị vắng mặt mà không dùng wrapper `null`?** Dùng `OptionalInt` hoặc một hợp đồng return primitive phù hợp.
9. **Có dùng type witness primitive như `<int>` không?** Sửa thành wrapper type hoặc để compiler boxing/infer hợp lệ.
10. **Có cần tương thích API bên ngoài không?** Nếu API nhận primitive array, đừng ép dùng generic collection chỉ vì đang học Generics.

Thiết kế rõ ràng thường tốt hơn việc ẩn boxing và casts sau các abstraction phức tạp. Mọi lựa chọn nên dựa trên semantics của dữ liệu và hình thức API cần hỗ trợ.

## 24. Tổng kết và tài liệu chuẩn

**Java Generics không cho primitive types như `int`, `double`, `boolean` làm type arguments.** Muốn dùng API generic, chọn wrapper class tương ứng: `Integer`, `Double`, `Boolean`, v.v. Java có autoboxing/unboxing để kết nối primitive và wrapper trong code thường ngày, nhưng các chuyển đổi này có hệ quả về `null`, equality và có thể cả hiệu năng.

Đừng nhầm lẫn giữa **primitive type** và **array of primitive**: `int` không phải reference type, còn `int[]` là object mảng tham chiếu, nên `List<int[]>` hợp lệ. Với nhiều giá trị primitive, cân nhắc `int[]`, `IntStream`, `OptionalInt` hoặc các primitive-specialized functional interfaces thay vì bắt buộc chuyển mọi thứ thành `List<Integer>`.

Chọn cấu trúc phù hợp với bài toán:

- Dùng `List<Integer>` cho Collection API khi cần generic reference elements.
- Dùng `int[]` khi cần lưu primitive có độ dài cố định.
- Dùng `IntStream` khi pipeline phép toán có ích.
- Dùng `OptionalInt` khi cần một giá trị `int` có thể vắng mặt.
- Dùng wrapper/reference generics khi cần API tổng quát và an toàn kiểu ở compile time.

Tài liệu đáng tin cậy để đối chiếu:

- [Java Language Specification §4.5 — Parameterized Types](https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html#jls-4.5): quy tắc type arguments.
- [Java Language Specification §4.3 — Reference Types and Values](https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html#jls-4.3): reference type so với primitive type.
- [Java Language Specification §4.6 — Type Erasure](https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html#jls-4.6): erasure của type variables và parameterized types.
- [Java Language Specification §5.1.7 — Boxing Conversion](https://docs.oracle.com/javase/specs/jls/se17/html/jls-5.html#jls-5.1.7): chuyển primitive sang wrapper.
- [Java Language Specification §5.1.8 — Unboxing Conversion](https://docs.oracle.com/javase/specs/jls/se17/html/jls-5.html#jls-5.1.8): chuyển wrapper sang primitive và tình huống `null`.
- [Java SE 17 `java.lang.Integer`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Integer.html): wrapper `Integer` và các API liên quan.
- [Java SE 17 `java.util.stream.IntStream`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/stream/IntStream.html): stream chuyên primitive int.
- [Java SE 17 `java.util.OptionalInt`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/OptionalInt.html): optional cho primitive `int`.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="generics-language-limits-003">Không dùng tham số kiểu của lớp trong ngữ cảnh static</a>

<details>
<summary>Xem chi tiết</summary>

# Không dùng tham số kiểu của lớp trong ngữ cảnh `static` — Java Generics

Khi một lớp Java được khai báo `class Box<T>`, tham số kiểu `T` thuộc về **khai báo lớp generic và cách sử dụng lớp dưới kiểu tham số hóa**, ví dụ `Box<String>` hoặc `Box<Integer>`. Nhưng thành viên `static` gắn với **lớp**, không gắn riêng với từng cách chọn đối số kiểu. Vì vậy, Java **không cho phép sử dụng trực tiếp tham số kiểu `T` của lớp trong các khai báo và ngữ cảnh static**.

```java
// Mã CỐ Ý KHÔNG BIÊN DỊCH: minh họa lỗi cần hiểu.
class Box<T> {
    T value;                   // Hợp lệ: instance field
    void set(T value) { }      // Hợp lệ: instance method

    // static T cached;                   // Lỗi
    // static T getCached() { return null; } // Lỗi
    // static void save(T value) { }       // Lỗi
}
```

Tuy nhiên, điều này **không có nghĩa phương thức static bị cấm dùng generics**. Phương thức static có thể **tự khai báo type parameter riêng**:

```java
class Box<T> {
    private final T value;

    private Box(T value) {
        this.value = value;
    }

    static <U> Box<U> of(U value) { // Hợp lệ: U thuộc phương thức of
        return new Box<>(value);
    }
}
```

Sự phân biệt giữa **`T` của lớp** và **`U` của phương thức static** là trọng tâm của bài. Tính đúng/sai không phụ thuộc việc tên biến kiểu là chữ `T` hay `U`, mà phụ thuộc **scope của nơi khai báo type parameter**.

## 1. Tách biệt tham số kiểu của lớp và biến/thành viên static

Hãy xét cùng một generic class:

```java
class Holder<T> {
    private T value;             // Mỗi instance có field value của nó
    static int created;          // Field chung của runtime class Holder

    Holder(T value) {
        this.value = value;
        created++;
    }

    T get() {
        return value;
    }
}
```

Sử dụng:

```java
Holder<String> a = new Holder<>("An");
Holder<Integer> b = new Holder<>(42);
System.out.println(Holder.created); // 2
```

Hai kiểu tĩnh `Holder<String>` và `Holder<Integer>` **khác nhau về cách compiler kiểm tra generic**, nhưng thường dùng **cùng một runtime class `Holder`** do type erasure. Vì vậy, biến `static int created` chỉ có **một bản cho runtime class được nạp bởi class loader tương ứng**, không được sao chép riêng thành `created` cho `String` và `Integer`.

Nếu Java cho viết `static T lastValue`, hãy thử hỏi:

- Khi gán `Holder<String>.lastValue = "Java"`, trường đó phải nhận `String`?
- Khi gán `Holder<Integer>.lastValue = 7`, cùng trường đó phải nhận `Integer`?
- Nếu chỉ có một static field chung, kiểu nào là hợp đồng an toàn cho cả hai?

Không có câu trả lời nhất quán nếu coi `T` của lớp là một kiểu riêng biệt cho mỗi phiên bản static field trong mô hình Java Generics thông thường. Java giải quyết từ **hệ thống kiểu ở compile time** bằng cách cấm tham chiếu trực tiếp `T` của lớp trong static context.

**Chú ý:** `static` không đồng nghĩa “chỉ tạo một instance object của lớp”. Mỗi `new Holder<...>()` vẫn tạo đối tượng riêng với instance field riêng; chỉ **thành viên static** không được nhân theo từng đối số kiểu.

## 2. Những ngữ cảnh static không được dùng `T` của lớp

Với:

```java
class Cache<T> {
    // Các ví dụ khác nhau bên dưới được xét độc lập.
}
```

Những vị trí sau đều sai nếu sử dụng chính `T` của `Cache<T>`:

| Vị trí | Mã minh họa bị cấm | Lý do |
| --- | --- | --- |
| Kiểu của static field | `static T item;` | Field static không có đối số kiểu của lớp riêng để chọn `T` |
| Tham số static method | `static void put(T item)` | Method static không nằm trong ngữ cảnh dùng type parameter của lớp |
| Kiểu trả về static method | `static T get()` | Không có `T` lớp nào được method static dùng |
| Kiểu local trong static method | `static void run() { T t; }` | Thân static method không được tham chiếu `T` của lớp |
| Static initialization block | `static { T x = null; }` | Static initializer là static context |
| Kiểu trong static member class | `static class Entry { T value; }` | Static nested class không mang type parameter của enclosing class |
| Dùng `T` như đối số generic trong static field | `static List<T> list;` | Dù `List` hợp lệ, `T` của lớp vẫn không nằm trong scope này |

Một số snippet ở bảng bỏ qua dấu ngoặc đóng và được viết để biểu thị **vị trí sai**, không phải chương trình hoàn chỉnh.

### Lỗi `non-static type variable T cannot be referenced from a static context`

Tùy trình biên dịch, khi viết:

```java
class Wrong<T> {
    static T data;
}
```

bạn có thể gặp thông báo dạng:

```text
non-static type variable T cannot be referenced from a static context
```

Chẩn đoán nói rằng biến kiểu `T` được khai báo tại lớp generic không được tham chiếu ở static context. Đừng nhầm với thông báo `non-static variable ... cannot be referenced from a static context` khi cố gọi **instance field** từ static method: hai lỗi có liên quan ý tưởng tách biệt static/instance nhưng là những quy tắc khác nhau.

### Static method không có implicit `this`

Một static method không có đối tượng `this` gắn với lời gọi. Nó không thể viết `this.value` hoặc tham chiếu trực tiếp instance field không qua một object. Tương tự, nó không thể sử dụng trực tiếp type parameter `T` của class để tuyên bố tham số/trả về vì static method không được gắn với một cách tham số hóa lớp cụ thể.

Tuy nhiên, static method hoàn toàn có thể **nhận một đối tượng `Holder<U>` làm tham số**, nhờ type parameter tự khai báo `U`; điều này sẽ được trình bày ở phần factory và generic static method.

## 3. Instance field, instance method và constructor dùng `T` thế nào?

Ở **ngữ cảnh instance**, type parameter của lớp có thể được sử dụng:

```java
class Parcel<T> {
    private T content;

    Parcel(T content) {      // Constructor nhận T của lớp
        this.content = content;
    }

    T get() {                // Instance method trả T
        return content;
    }

    void replace(T next) {   // Instance method nhận T
        content = next;
    }
}
```

Khi gọi:

```java
Parcel<String> label = new Parcel<>("A");
label.replace("B");
String text = label.get();

// label.replace(42); // Không biên dịch: Integer không phù hợp String
```

Compiler xem `content` và phương thức `replace` qua kiểu `Parcel<String>`. Với `Parcel<Integer>`, compiler kiểm tra một bộ quy tắc tương ứng với `Integer` cho instance API. Không có static field nào phải đồng thời đóng vai trò `String` và `Integer`.

Constructor của generic class có thể tham chiếu type parameter lớp vì nó **xây dựng instance** có cách tham số hóa cụ thể ở mã nguồn. Đừng suy ra mọi constructor đều là static chỉ vì cú pháp `new` tạo object.

### Một instance method cũng có thể khai báo type parameter riêng

```java
class PairBox<T> {
    T first;

    <U> U takeSecond(U other) {
        return other;
    }
}
```

Trong `PairBox<T>`, `T` thuộc class, còn `U` thuộc method `takeSecond`. Đây là hai scope độc lập, tương tự nguyên tắc áp dụng cho static generic method.

## 4. Cú pháp đúng của static generic method: `static <U> ...`

Định nghĩa một static factory:

```java
class Box<T> {
    private final T item;

    private Box(T item) {
        this.item = item;
    }

    T get() {
        return item;
    }

    static <U> Box<U> of(U item) {
        return new Box<>(item);
    }
}
```

Dòng quan trọng:

```java
static <U> Box<U> of(U item)
```

- `static`: method thuộc class, không cần instance để gọi.
- `<U>`: **khai báo** type parameter mới của phương thức `of`.
- `Box<U>`: method trả một đối tượng `Box` dùng type argument `U`.
- `U item`: kiểu tham số của chính method là `U`.
- `new Box<>(item)`: compiler suy luận type argument phù hợp để tạo object.

Caller:

```java
Box<String> name = Box.of("An");
Box<Integer> age = Box.of(20);
```

Dù `of` là static, compiler có thể suy luận `U = String` ở lời gọi thứ nhất và `U = Integer` ở lời gọi thứ hai. Điều này **không có nghĩa `of` đã sử dụng `T` của class**. `U` là type variable **được khai báo riêng cho từng lời gọi generic method** ở mức kiểu nguồn, còn runtime vẫn dùng erasure tương ứng.

### Thiếu `<U>` là sai

```java
// KHÔNG BIÊN DỊCH trong Box<T>:
static Box<U> of(U item) {
    return new Box<>(item);
}
```

Tên `U` chưa được khai báo nên không thể dùng làm type variable. Nếu đổi `U` thành `T` của class mà không khai báo type parameter của method, vẫn bị lỗi static context.

### Đặt `<U>` ở đúng vị trí

Với method generic, phần khai báo type parameter nằm **sau các modifier**, **trước return type**:

```java
public static <U> U identity(U value) {
    return value;
}
```

Không đặt `<U>` sau kiểu trả về như cách ghi type argument khi **gọi** generic method.

## 5. Dùng cùng tên `T` cho class và static method: hợp lệ nhưng dễ hiểu nhầm

Java cho phép một static method khai báo type parameter riêng có tên trùng `T` của class:

```java
class Example<T> {
    static <T> T identity(T value) {
        return value;
    }
}
```

Đây là **hai type variables khác nhau** dù cùng có tên `T`. Trong phạm vi method, `T` của `identity` **che khuất (shadows)** tên `T` của class:

```java
Example<String> text = new Example<>();
Integer value = Example.identity(42); // Hợp lệ: T của method là Integer
```

Lệnh này không hề yêu cầu `T` của class là `Integer`; `identity` có type parameter độc lập.

**Khuyến nghị:** trong code dễ gây nhầm, dùng tên `U`, `R`, `V` hoặc tên type parameter có ý nghĩa để phân biệt static method type parameter với class type parameter. Việc dùng `static <T>` không sai, nhưng có thể khiến người mới tưởng method static đang tham chiếu `T` của class.

## 6. Static generic method có thể nhận instance của class generic

Một cách hợp lệ để làm việc với `Box<T>` trong static code là **truyền object đã mang type argument cụ thể ở mã nguồn vào tham số**:

```java
class Holder<T> {
    private final T data;

    Holder(T data) {
        this.data = data;
    }

    T get() {
        return data;
    }

    static <U> U extract(Holder<U> source) {
        return source.get();
    }

    static void printUnknown(Holder<?> source) {
        Object value = source.get();
        System.out.println(value);
    }
}
```

Ở đây:

- `extract` tự khai báo `U`, sau đó ràng buộc kiểu `Holder<U>` của tham số và kiểu trả về `U`.
- `printUnknown` không cần khai báo type parameter: `Holder<?>` thể hiện một `Holder` có kiểu phần tử chưa biết, nên đọc được giá trị như `Object`.
- Không phương thức nào dùng `T` của khai báo `Holder<T>` trong static context.

Với caller:

```java
Holder<String> source = new Holder<>("Java");
String value = Holder.extract(source); // U suy luận là String
Holder.printUnknown(source);           // In Java
```

Đây là cách thông thường để các utility static xử lý generic instance **mà vẫn giữ type safety**, thay vì cố truy cập trực tiếp `T` lớp bên ngoài static method.

### `static` không cấm kiểu tham số hóa trong chữ ký

Hai khai báo sau hoàn toàn có thể hợp lệ:

```java
static void show(Holder<String> value) { /* ... */ }

static <U> void showGeneric(Holder<U> value) { /* ... */ }
```

Dòng đầu dùng `String` là **một kiểu cụ thể** trong tham số. Dòng sau tự khai báo `U`. Chúng không dùng type variable `T` từ khai báo class. Vì vậy đừng khái quát sai rằng “static method không được viết `Box<...>`”.

## 7. Static field nào hợp lệ trong generic class?

Không được viết `static T item;`, nhưng Java cho phép static field có kiểu **không phụ thuộc `T` của class**:

```java
class Registry<T> {
    static int count;
    static String name = "Shared registry";
    static java.util.List<?> snapshot = java.util.List.of();

    T value;
}
```

Cả `int`, `String` và `List<?>` đều không phụ thuộc type variable `T`. Field `snapshot` dùng wildcard, không cam kết một type argument cụ thể của `Registry<T>`.

Tuy nhiên, cần phân biệt:

```java
// KHÔNG HỢP LỆ trong Registry<T>:
// static java.util.List<T> values;
```

với:

```java
// HỢP LỆ: kiểu phần tử được cố định là String, không phải T của class.
static java.util.List<String> names = new java.util.ArrayList<>();
```

Một static field `List<String>` **không được phân chia riêng theo `Registry<String>` và `Registry<Integer>`**; nó là field chung thuộc runtime class `Registry`. Nếu nó mutable, mọi caller có thể tác động tới một nguồn trạng thái chung. Chỉ sử dụng kiểu cố định khi thiết kế thật sự muốn điều đó.

### Static counter có thể phục vụ thống kê chung

```java
class Counter<T> {
    static int created;

    Counter() {
        created++;
    }
}
```

Hai lệnh `new Counter<String>()` và `new Counter<Integer>()` đều tăng cùng field `Counter.created`. Đây là một đặc tính của thành viên static, **không phải một counter riêng cho mỗi tham số kiểu**. Nếu nhiều thread tăng counter đồng thời, `created++` còn có vấn đề atomicity; muốn đếm thread-safe cần cơ chế đồng bộ hoặc atomic thích hợp, nhưng đó là vấn đề khác với generics.

## 8. Static initializer và static context: không có `T` lớp

Static initializer chạy gắn với việc khởi tạo class, không phụ thuộc một instance `Box<String>` hay `Box<Integer>` nào:

```java
class Settings<T> {
    static java.util.List<String> defaults;

    static {
        defaults = java.util.List.of("A", "B"); // Hợp lệ
        // T initial = null;                    // Không hợp lệ
    }
}
```

Bất kể sau này code dùng `Settings<Integer>` hay `Settings<String>`, khối static initializer không được phép sử dụng chính `T` của class. Việc tồn tại dấu `<T>` trên tên lớp **không làm một static initializer trở thành generic**.

Không có cú pháp khai báo một **static generic initializer block** kiểu `static <U> { ... }`. Nếu cần logic generic, tạo static generic method riêng và gọi method đó với đối số kiểu được suy luận từ các giá trị/tham số thích hợp.

## 9. Static nested class cũng không thấy `T` của enclosing class

Một generic outer class có thể chứa **static nested class**:

```java
class Outer<T> {
    static class Nested<U> {
        U data;

        Nested(U data) {
            this.data = data;
        }
    }
}
```

`Nested<U>` là một type generic **riêng**, khai báo type parameter `U` của chính nó. Nó không tự động “nhận” `T` của `Outer<T>`.

Cách dùng:

```java
Outer.Nested<String> text = new Outer.Nested<>("A");
Outer.Nested<Integer> number = new Outer.Nested<>(7);
```

Để thấy điều không hợp lệ:

```java
class Outer<T> {
    static class Wrong {
        // T item; // Lỗi: không dùng T của Outer trong static nested class
    }
}
```

Do nested class có modifier `static`, nó không mang implicit enclosing instance; cũng không mang type argument `T` của một đối tượng `Outer<T>` nào.

### So với non-static inner class

```java
class Outer<T> {
    private final T value;

    Outer(T value) {
        this.value = value;
    }

    class Inner {
        T getOuterValue() {
            return value;
        }
    }
}
```

`Inner` ở đây là **non-static inner class**. Nó gắn với một đối tượng `Outer<T>`, nên có thể dùng `T` của outer.

```java
Outer<String> outer = new Outer<>("Java");
Outer<String>.Inner inner = outer.new Inner();
String s = inner.getOuterValue();
```

Hai đoạn `Outer` ở trên là **hai phương án khai báo độc lập**, không chép đồng thời hai class cùng tên vào một file. Ví dụ runnable ở mục 11 sử dụng cả nested static và inner non-static với tên khác nhau để bạn kiểm chứng.

## 10. Phân biệt lỗi `static T` với những giới hạn khác của type variable

Không phải mọi chỗ Java từ chối `T` đều do static context. So sánh bốn ví dụ **cố ý không biên dịch** trong một class generic:

```java
class GenericTools<T> {
    // static T shared;       // Sai: T của lớp trong static context.
    void example(Object x) {
        // T item = new T();   // Sai: không có constructor cho T.
        // Class<T> c = T.class; // Sai: type variable không có class literal.
        // T[] arr = new T[2]; // Sai: generic array creation.
        // boolean b = x instanceof T; // Sai: T không reifiable.
    }
}
```

Dù `example` là **instance method**, bốn dòng cuối vẫn không hợp lệ. Chúng liên quan tới type erasure/reifiability, không phải việc chia sẻ static member. Tùy mục đích: dùng `Supplier<? extends T>` để tạo object, `Class<T>` để kiểm tra runtime class, `IntFunction<T[]>` hoặc array factory để tạo mảng, và `List<T>` khi không cần mảng.

**Câu hỏi chẩn đoán:** nếu xóa từ khóa `static` mà code vẫn sai, đó không phải lỗi chỉ do scope static. Phân biệt hai nguyên nhân giúp chọn factory, class token hoặc API instance thích hợp, thay vì dùng một unchecked cast để che lỗi.

## 11. Chương trình Java 17+ chạy được: static và instance cùng tồn tại

Lưu mã vào tệp **`StaticTypeParameterDemo.java`**:

```java
public class StaticTypeParameterDemo {
    static final class Box<T> {
        private final T value;

        private Box(T value) {
            this.value = value;
        }

        T get() {
            return value;
        }

        static int created;

        // U do phương thức static tự khai báo.
        static <U> Box<U> of(U value) {
            created++;
            return new Box<>(value);
        }

        static <U extends Number> double twice(U number) {
            return number.doubleValue() * 2;
        }

        // T dưới đây thuộc method identity, không phải Box<T>.
        static <T> T identity(T input) {
            return input;
        }

        // Static nested class có type parameter V riêng.
        static final class Tag<V> {
            private final V value;

            Tag(V value) {
                this.value = value;
            }

            V get() {
                return value;
            }
        }

        // Non-static inner class nhìn thấy T của Box<T>.
        final class Inner {
            T outerValue() {
                return value;
            }
        }
    }

    public static void main(String[] args) {
        Box<String> text = Box.of("Java");
        Box<Integer> number = Box.of(42);

        System.out.println("String box: " + text.get());
        System.out.println("Integer box: " + number.get());
        System.out.println("Shared static count: " + Box.created);
        System.out.println("Static generic identity: "
                + Box.identity("hello"));
        System.out.println("Bounded static method: " + Box.twice(3));

        Box.Tag<Long> tag = new Box.Tag<>(99L);
        System.out.println("Static nested tag: " + tag.get());

        Box<String>.Inner inner = text.new Inner();
        System.out.println("Non-static inner sees T: "
                + inner.outerValue());
    }
}
```

Biên dịch và chạy trên JDK 17+:

```bash
javac -encoding UTF-8 StaticTypeParameterDemo.java
java StaticTypeParameterDemo
```

Standard output:

```text
String box: Java
Integer box: 42
Shared static count: 2
Static generic identity: hello
Bounded static method: 6.0
Static nested tag: 99
Non-static inner sees T: Java
```

Ý nghĩa từng dòng:

1. `Box<String>` và `Box<Integer>` dùng instance field `value` riêng; compiler kiểm tra `get()` theo `String` hoặc `Integer` ở từng caller.
2. Cả hai lệnh `Box.of(...)` tăng **cùng** static field `Box.created`, nên giá trị là 2.
3. `Box.identity("hello")` hợp lệ dù method dùng tên `T`: tên đó là **type parameter do method tự khai báo**, được suy luận từ đối số `"hello"`.
4. `Box.twice(3)` suy luận `U = Integer`; ràng buộc `U extends Number` cho phép gọi `doubleValue()`.
5. `Box.Tag<Long>` có `V = Long` của static nested class, **không phụ thuộc `T` của `Box<T>`**.
6. `Box<String>.Inner` là inner class không static gắn với đối tượng `text`, nên đọc `value` của outer instance và biết đó là `String` ở mã nguồn.

Đây là chương trình độc lập có thể chạy; các đoạn có nhãn **KHÔNG BIÊN DỊCH** trong các mục khác chỉ dùng để phân tích lỗi, không cần chép vào file này.

## 12. Cách sửa các lỗi static/generic thường gặp

### Lỗi A — Static field dùng `T` lớp

Trước:

```java
// Sai:
class Registry<T> {
    // static T latest;
}
```

Nếu mục tiêu là **lưu dữ liệu theo từng object**, bỏ `static`:

```java
class Registry<T> {
    private T latest;

    void set(T value) {
        latest = value;
    }
}
```

Nếu mục tiêu là **trạng thái static dùng chung**, hãy chọn một kiểu không phụ thuộc `T`, ví dụ `static int count` hoặc một kiểu cụ thể phù hợp. Nếu cần kho dữ liệu đa kiểu, cân nhắc thiết kế registry có token và các bất biến runtime rõ ràng; không phải cứ dùng `static Map<String, Object>` là đã có type safety tự động.

### Lỗi B — Static method trả `T` lớp

Trước:

```java
// Sai trong class Box<T>:
// static T find() { return null; }
```

Nếu xử lý dữ liệu của **một instance cụ thể**, viết instance method:

```java
T get() {
    return value;
}
```

Nếu thao tác là **factory**, khai báo type parameter method:

```java
static <U> Box<U> of(U value) {
    return new Box<>(value);
}
```

Nếu thao tác là utility nhận object generic, khai báo `static <U> U extract(Box<U> box)`.

### Lỗi C — Static nested class muốn dùng `T` của outer

Trước:

```java
// Sai trong Outer<T>:
// static class Entry { T item; }
```

Nếu entry cần độc lập khỏi outer instance, cho nó type parameter riêng:

```java
static class Entry<U> {
    U item;
}
```

Nếu entry phải gắn với outer instance và dùng chính `T`, cân nhắc **non-static inner class** hoặc truyền `T` qua constructor cùng kiểu cụ thể, tùy quan hệ sở hữu và vòng đời.

### Lỗi D — Ghi `static T` và tưởng compiler hiểu `T` từ người gọi

Static method không chọn “`T` của lớp đang được gọi” vì không có bản static riêng cho `Box<String>` và `Box<Integer>`. Nếu method không cần đối số, một generic method `static <U> U empty()` tuy có thể viết `return null` về cú pháp, nhưng **không thể tự suy ra một giá trị `U` hợp lệ bất kỳ** từ không khí. Thiết kế tốt thường nhận dữ liệu, token, supplier hoặc factory thay vì dựa vào một `U` ảo.

## 13. Vấn đề thực hành khi gọi static method qua kiểu tham số hóa

Thông thường, hãy gọi static method bằng **tên class**:

```java
Box<String> box = Box.of("Java");
```

Đối với static factory có type parameter riêng, compiler có thể dùng target typing và type inference để khớp kiểu trả về. Nếu cần, generic method còn cho phép type witness:

```java
Box<String> one = Box.<String>of("Java");
```

Không nên tưởng rằng cú pháp `Box<String>.of(...)` có nghĩa “chọn static method gắn với `T = String` của class”. Java không cung cấp hệ thành viên static riêng cho mỗi `Box<T>` kiểu đó; trong code bình thường hãy viết `Box.of(...)` hoặc `Box.<String>of(...)` cho static generic method.

Khi generic class có phương thức instance `get()`, caller phải có object:

```java
Box<String> value = Box.of("x");
String result = value.get();
```

Không gọi `Box.get()` như static method chỉ vì `Box` là generic class. Hãy phân biệt **loại thành viên** với **cách tham số hóa kiểu**.

## 14. Bài tập tự kiểm tra

**Câu 1.** `class Repo<T> { static T data; }` có hợp lệ không?

Không. `T` của lớp không thể được dùng làm kiểu của static field.

**Câu 2.** `class Repo<T> { static int count; }` có hợp lệ không?

Có. `int` không phụ thuộc `T`; field static `count` được dùng chung giữa các cách tham số hóa lớp trong cùng runtime class được nạp.

**Câu 3.** `class Repo<T> { static <U> U identity(U value) { return value; } }` có hợp lệ không?

Có. `U` là type parameter **do static method tự khai báo**, không phải `T` của class.

**Câu 4.** `class Repo<T> { static <T> T identity(T value) { return value; } }` có hợp lệ không?

Có. `T` ở method là type parameter mới, che khuất tên `T` của class trong phạm vi method; không phải một cách truy cập type parameter của class từ static context.

**Câu 5.** Một `static class Nested { T value; }` nằm trong `Outer<T>` có hợp lệ không?

Không. Static nested class không có type parameter `T` của enclosing class trong scope. Để sửa, khai báo `Nested<U>` hoặc dùng non-static inner class nếu quan hệ sở hữu cần outer instance.

**Câu 6.** `class Box<T> { T get() { ... } }` dùng `T` trong instance method có hợp lệ không?

Có. `T` thuộc generic instance API, và compiler áp dụng theo kiểu tĩnh `Box<String>`, `Box<Integer>`, v.v.

**Câu 7.** `static <U> U extract(Box<U> value)` dùng `Box<U>` trong static context có hợp lệ không?

Có. Type parameter `U` được khai báo bởi chính static method và ràng buộc cách dùng `Box<U>` của đối số.

**Câu 8.** Đổi một static method thành instance method có khiến `T.class` và `new T[10]` hợp lệ không?

Không. Đó là những giới hạn khác do type variable `T` non-reifiable và type erasure, không chỉ do static context.

## 15. Checklist trước khi viết generic API có static members

Khi gặp chữ `T` ở một khai báo, hãy tự kiểm tra:

1. **Ai khai báo `T`?** `class Box<T>` hay `static <T> ...`?
2. **Bạn đang ở ngữ cảnh nào?** Field instance, method instance, constructor, static field, static initializer, static method hay static nested class?
3. **Static member có thật sự cần kiểu của một instance không?** Nếu có, có thể nên chuyển logic vào instance method hoặc nhận instance làm tham số.
4. **Có thể khai báo type parameter riêng cho static method không?** Ví dụ `static <U> Box<U> of(U value)`.
5. **Cần trạng thái riêng cho từng đối tượng hay trạng thái chung?** Trường instance và trường static có vòng đời khác nhau.
6. **Nested type có cần enclosing instance không?** Dùng non-static inner nếu cần tham chiếu outer instance và outer `T`; dùng static nested generic class với `<U>` riêng nếu muốn độc lập.
7. **Có đang nhầm lỗi static với type erasure không?** `T.class`, `new T()`, `new T[]` không trở nên hợp lệ chỉ bằng cách bỏ `static`.
8. **Có đang dùng raw type hoặc cast để lách compiler không?** Những cách này có thể phá hợp đồng generic và làm lỗi xuất hiện muộn ở runtime.

**Kết luận:** tham số kiểu `T` của lớp generic chỉ được dùng ở các ngữ cảnh phù hợp với **instance/type của lớp**, không được tham chiếu trực tiếp trong static field, static initializer, static method hoặc static nested class. Nhưng static code **được phép dùng generic theo cách riêng**: khai báo `static <U>`, nhận `Box<U>` làm tham số, tạo `Box<U>` qua factory, hoặc dùng static nested class có type parameter của chính nó. Hiểu đúng **scope và ownership của type variables** là cách chắc chắn nhất để sửa lỗi “non-static type variable cannot be referenced from a static context”.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="generics-language-limits-004">Không khai báo lớp generic kế thừa Throwable</a>

<details>
<summary>Xem chi tiết</summary>

# Không khai báo lớp generic kế thừa `Throwable` trong Java

## Mục tiêu học tập

Trong Java, một lớp có thể kế thừa `Exception`, `RuntimeException` hoặc `Error` để biểu diễn lỗi. Nhưng **lớp generic không được là lớp con trực tiếp hoặc gián tiếp của `java.lang.Throwable`**. Điều đó có nghĩa những ý tưởng như `class ProcessingException<T> extends Exception` hay `class Failure<T> extends RuntimeException` đều bị Java compiler từ chối.

Đây là một giới hạn của ngôn ngữ, không phải chuyện “chưa biết viết đúng cú pháp”. Type erasure khiến đối số kiểu generic không được runtime phân biệt như những loại exception khác nhau để thực hiện cơ chế `catch`.

Sau bài này, bạn có thể:

- Nhận ra những khai báo exception generic **không hợp lệ**, kể cả đường kế thừa gián tiếp từ `Throwable`.
- Hiểu vai trò của **type erasure** và cơ chế nhận diện ngoại lệ tại runtime.
- Phân biệt **generic exception class bị cấm** với **generic method khai báo `throws E` vẫn hợp lệ**.
- Viết `<E extends Exception> void propagate(E error) throws E` một cách đúng đắn.
- Giải thích vì sao không thể dùng tham số kiểu `E` làm kiểu của biến `catch (E ex)`, dù `E extends Exception`.
- Thiết kế phương án thay thế: exception **không generic** có thuộc tính typed, dùng **generic data holder** độc lập với `Throwable`, hoặc method/functional interface generic có exception type parameter.
- Đọc cảnh báo/lỗi từ `javac`, phân biệt checked và unchecked exception, và tránh suy luận sai về JVM.

Các chương trình Java 17+ trong bài đã được biên dịch và chạy thử. Các đoạn được đánh dấu **không hợp lệ** là mẫu cố ý gây lỗi compiler, không phải tệp phải chạy thành công.

## 1. Quy tắc chính xác của Java

Quy tắc quan trọng là:

> Một lớp generic không thể là lớp con của `Throwable`, dù kế thừa trực tiếp hay qua những lớp ở giữa.

Những kiểu sau đều không hợp lệ:

```java
// KHÔNG BIÊN DỊCH:
class GenericChecked<T> extends Exception {}

class GenericUnchecked<T> extends RuntimeException {}

class GenericError<T> extends Error {}

class PlainProblem extends Exception {}
class GenericIndirect<T> extends PlainProblem {}
```

Lớp `PlainProblem` ở ví dụ là một exception bình thường và **được phép**. Lỗi nằm ở `GenericIndirect<T>`: vì nó có tham số kiểu `T` và kế thừa từ `PlainProblem`, vốn là subclass của `Exception` → `Throwable`.

Java cũng không cho phép biến cách viết thành hợp lệ bằng cách thay từ `Exception` sang `RuntimeException`:

- `Exception` là subclass của `Throwable`.
- `RuntimeException` là subclass của `Exception`.
- `Error` là subclass của `Throwable`.
- Mọi subclass của các kiểu trên cuối cùng đều là subclass của `Throwable`.

Lệnh `javac` trên những trường hợp này báo lỗi **`a generic class may not extend java.lang.Throwable`**. Java không cung cấp ngoại lệ đặc biệt cho “unchecked exception generic”; cả checked, runtime exception và error đều chịu cùng giới hạn lớp generic.

## 2. Ba khái niệm dễ bị nhầm

| Cách viết | Hợp lệ? | Giải thích |
| --- | --- | --- |
| `class ParseException extends Exception` | Có | Exception class không generic |
| `class ParseException<T> extends Exception` | **Không** | Generic class là subclass của `Throwable` |
| `class ParseException<T> extends RuntimeException` | **Không** | `RuntimeException` vẫn nằm dưới `Throwable` |
| `class Child<T> extends ParseException` | **Không** | Generic subclass kế thừa `Throwable` gián tiếp |
| `record ErrorData<T>(T value) {}` | Có | Record này **không** kế thừa `Throwable` |
| `interface Result<T> {}` | Có | Interface generic bình thường, không phải exception class |
| `<E extends Exception> void run(E e) throws E` | Có | Generic **method** dùng type variable trong `throws` |
| `<E extends Exception> void run(E e) { catch (E x) {} }` | **Không** | Type variable không được làm kiểu catch parameter |
| `class ParseException extends Exception` có generic method | Có | **Class** không generic; chỉ có method generic |
| Constructor `<T> ParseException(T data)` trong exception không generic | Có | Generic constructor không biến lớp thành generic class |

Tóm lại: **cái bị cấm là sự kết hợp “lớp có type parameter” và “là subclass của Throwable”**. Java vẫn cho phép generics xuất hiện ở nhiều nơi liên quan đến xử lý lỗi, miễn không vi phạm quy tắc đó.

## 3. Tại sao Java cấm generic exception class?

Java generics chủ yếu được triển khai bằng **type erasure (xóa kiểu)**: những cách tham số hóa của cùng một generic class, chẳng hạn `Box<String>` và `Box<Integer>`, không tạo ra hai runtime class riêng theo từng đối số kiểu.

Trong khi đó, exception handling của JVM dựa vào **runtime class của đối tượng được ném** và khả năng khớp với loại exception trong bảng exception handler. Hãy tưởng tượng cú pháp sau được cho phép:

```java
// GIẢ ĐỊNH: Java thực tế KHÔNG cho khai báo kiểu này.
class Problem<T> extends Exception {}

// GIẢ ĐỊNH tiếp, nếu hai biến thể có thể được ném:
// catch (Problem<String> ex) { ... }
// catch (Problem<Integer> ex) { ... }
```

Ở mã nguồn, `Problem<String>` và `Problem<Integer>` có type arguments khác nhau. Nhưng nếu Java xử lý chúng theo cơ chế erasure thông thường, runtime class vẫn chỉ là `Problem`, không phải hai class khác nhau với type arguments có thể phân biệt ở JVM.

Vì vậy một cơ chế `catch` phân nhánh theo `<String>` và `<Integer>` **không thể dựa vào generic arguments bị xóa**. Ngôn ngữ giải quyết triệt để vấn đề ở cấp khai báo: **không cho định nghĩa generic class kế thừa `Throwable`**.

### Giới hạn của cách giải thích

Đừng hiểu sai thành “JVM không thể chứa một đối tượng exception có dữ liệu generic”. Một exception **không generic** vẫn có thể giữ các đối tượng kiểu khác nhau trong field, hoặc tham chiếu tới một **generic data holder**. Điều Java cấm là chính **lớp Throwable-subclass** có danh sách type parameters.

Đồng thời, đây là **quy tắc biên dịch của Java**; việc đọc bytecode thủ công hoặc nhìn metadata runtime không biến những khai báo source không hợp lệ thành hợp lệ. Không nên tìm một “mẹo cast” để cố qua `javac`: phương án đúng là thiết kế API khác.

## 4. Thử compiler: năm lỗi được tạo có chủ đích

Tạo tệp **`InvalidGenericExceptions.java`** sau để **quan sát lỗi**, không phải chạy:

```java
class PlainProblem extends Exception {}

class GenericCheckedProblem<T> extends Exception {}

class GenericRuntimeProblem<T> extends RuntimeException {}

class GenericIndirectProblem<T> extends PlainProblem {}

class GenericErrorProblem<T> extends Error {}

class CatchProblem {
    static <E extends Exception> void catchTypeVariable(E error) {
        try {
            throw error;
        } catch (E caught) {
            System.out.println(caught);
        }
    }
}
```

Chạy:

```bash
javac InvalidGenericExceptions.java
```

**Các chẩn đoán chính đã được kiểm chứng trên Java compiler:**

```text
error: a generic class may not extend java.lang.Throwable
error: a generic class may not extend java.lang.Throwable
error: a generic class may not extend java.lang.Throwable
error: a generic class may not extend java.lang.Throwable
error: unexpected type
  required: class
  found:    type parameter E
5 errors
```

Thứ tự ví dụ tương ứng bốn khai báo lớp generic và một `catch (E caught)` ở cuối. Đường tệp, số dòng và chú thích chi tiết của compiler có thể khác tùy phiên bản JDK; phần bản chất là:

1. `GenericCheckedProblem<T>`: generic + `Exception`.
2. `GenericRuntimeProblem<T>`: generic + `RuntimeException`.
3. `GenericIndirectProblem<T>`: generic + `PlainProblem` → `Exception`.
4. `GenericErrorProblem<T>`: generic + `Error`.
5. `catch (E caught)`: `E` là type parameter, không phải một class được phép dùng trong catch clause.

**Quan trọng:** `PlainProblem extends Exception` là khai báo hợp lệ, bản thân nó không phải nguyên nhân của một trong năm lỗi.

### Vì sao `catch (E e)` bị từ chối?

`E extends Exception` nói rằng `E` được giới hạn bởi `Exception`; nó **không** biến `E` thành một class cụ thể như `IOException.class` mà runtime catch handler có thể dùng theo ngữ nghĩa Java source.

Một catch clause yêu cầu **kiểu exception class thích hợp**, không cho phép viết một type variable làm kiểu catch parameter. Nếu cần bắt ngoại lệ bên trong generic method, có thể bắt một exception class phù hợp, hoặc thiết kế generic method để **chuyển tiếp** exception qua `throws E`, như phần tiếp theo.

## 5. Generic `throws E` là hợp lệ

Đây là điểm dễ gây nhầm lẫn nhất của bài.

**Không được viết generic exception class:**

```java
// Không hợp lệ:
class MyException<T> extends Exception {}
```

**Nhưng được viết generic method có type variable trong `throws`:**

```java
static <E extends Exception> void propagate(E error) throws E {
    throw error;
}
```

`E` ở đây là **type parameter của phương thức**, được ràng buộc bởi `Exception`. Method nhận một object exception có runtime class thật, rồi ném **chính object exception đó** bằng `throw error`. Nó không tạo một lớp exception generic mới.

Ví dụ ý tưởng sử dụng:

```java
try {
    propagate(new java.io.IOException("disk offline"));
} catch (java.io.IOException ex) {
    System.out.println(ex.getMessage());
}
```

Khi gọi `propagate` với `IOException`, compiler có thể duy trì thông tin về checked exception đó cho caller. `catch (IOException ex)` sử dụng một **exception class thật**, hợp lệ; nó không phải `catch (E ex)`.

Quy tắc áp dụng rộng hơn: tham số kiểu trong `throws E` có thể dùng khi `E` được ràng buộc thích hợp bởi `Throwable` hoặc subclass phù hợp, ví dụ `<E extends Exception>`. Việc ném và bắt các object thực ở runtime vẫn tuân thủ phân cấp `Throwable`.

## 6. Chương trình Java 17: generic method chuyển tiếp checked exception

Tạo tệp **`ExceptionGenericsDemo.java`**:

```java
import java.io.IOException;

public class ExceptionGenericsDemo {
    static final class DataException extends Exception {
        private static final long serialVersionUID = 1L;
        private final String field;

        DataException(String field, String message, Throwable cause) {
            super(message, cause);
            this.field = field;
        }

        String field() { return field; }
    }

    @FunctionalInterface
    interface ThrowingSupplier<T, E extends Exception> {
        T get() throws E;
    }

    static <T, E extends Exception> T execute(ThrowingSupplier<T, E> work)
            throws E {
        return work.get();
    }

    static <E extends Exception> void propagate(E error) throws E {
        throw error;
    }

    static int parseAge(String input) throws DataException {
        try {
            int age = Integer.parseInt(input);
            if (age < 0) throw new NumberFormatException("negative");
            return age;
        } catch (NumberFormatException ex) {
            throw new DataException("age", "Invalid age: " + input, ex);
        }
    }

    public static void main(String[] args) {
        try {
            int age = execute(() -> parseAge("21"));
            System.out.println("age: " + age);
        } catch (DataException ex) {
            System.out.println("unexpected: " + ex.getMessage());
        }

        try {
            execute(() -> parseAge("bad"));
        } catch (DataException ex) {
            System.out.println("checked: " + ex.field()
                    + " / " + ex.getMessage());
            System.out.println("cause: "
                    + ex.getCause().getClass().getSimpleName());
        }

        try {
            propagate(new IOException("disk offline"));
        } catch (IOException ex) {
            System.out.println("propagated: " + ex.getMessage());
        }
    }
}
```

Lưu file theo đúng tên class public rồi chạy:

```bash
javac -Xlint:all ExceptionGenericsDemo.java
java ExceptionGenericsDemo
```

**Stdout đã kiểm chứng:**

```text
age: 21
checked: age / Invalid age: bad
cause: NumberFormatException
propagated: disk offline
```

Chương trình này đã biên dịch thành công với `-Xlint:all` **không phát cảnh báo** trong môi trường Java 17 được dùng kiểm thử.

### Giải thích cấu trúc

**`DataException extends Exception` không generic.** Đây là checked exception class bình thường, có field `String field` để mô tả vị trí dữ liệu lỗi. `serialVersionUID` được khai báo vì exception có thể tuần tự hóa theo `Serializable`, giúp ví dụ không phát cảnh báo `[serial]` khi bật `-Xlint:all`.

**`ThrowingSupplier<T, E extends Exception>` là interface generic hợp lệ.** Nó không kế thừa `Throwable`. Tham số kiểu `T` biểu diễn kết quả và `E` biểu diễn loại checked exception mà lời gọi có thể ném.

**`execute(ThrowingSupplier<T,E>) throws E` là method generic hợp lệ.** Compiler có thể suy luận `T` từ kiểu kết quả và `E` từ hợp đồng checked exception của callback. Thân method gọi `work.get()` rồi chuyển tiếp lỗi, không bọc exception trong một generic subclass.

**`propagate(E error) throws E` ném một object exception được cung cấp.** Với `IOException`, caller có thể bắt loại exception đó theo class thật. Không có constructor của một exception generic tại đây.

**`parseAge` chuyển một lỗi parse thành `DataException`.** Nó bảo toàn nguyên nhân `NumberFormatException` qua constructor `super(message, cause)`. Đây là một mẫu checked exception thông thường, không cần khai báo `DataException<T>`.

### Checked và unchecked trong ví dụ

`DataException` và `IOException` là checked exceptions: caller phải bắt hoặc khai báo `throws` phù hợp. `NumberFormatException` là unchecked exception (con của `RuntimeException`); ở đây nó được bắt và bọc thành `DataException` để cung cấp hợp đồng rõ ràng cho API parse.

Giới hạn generic class kế thừa `Throwable` áp dụng cho **cả** `RuntimeException` và `Error`; không có chuyện unchecked thì được khai báo generic exception class.

## 7. Vì sao generic interface mang exception type argument là phương án tốt?

Một chức năng xử lý nhiều loại lỗi thường cần:

- **Kiểu kết quả**: `T`.
- **Kiểu checked exception có thể ném**: `E`.
- Một cách tái sử dụng logic mà không phải bắt mọi exception thành `Exception` hoặc `RuntimeException`.

Bạn có thể định nghĩa:

```java
@FunctionalInterface
interface ThrowingTask<T, E extends Exception> {
    T run() throws E;
}
```

Đây là **interface generic**, không phải generic exception class; hoàn toàn hợp lệ. Khi viết:

```java
static <T, E extends Exception> T runTask(ThrowingTask<T, E> task)
        throws E {
    return task.run();
}
```

thông tin generic giúp compiler áp dụng checked-exception contract cho caller. Không cần tạo `TaskException<T>`.

**Nhưng hãy hiểu giới hạn:** đây không phải cơ chế catch theo type argument. Một method nhận `ThrowingTask<String, IOException>` vẫn ném object runtime `IOException`; caller xử lý bằng `catch(IOException ex)` theo class thật, không có handler cho “`E` của callback”.

Mô hình này hữu ích khi viết những API hỗ trợ callback I/O, parsing, file operations hoặc checked exception adapters. Nếu API cần thêm context nghiệp vụ, hãy giữ context ở một **non-generic exception class** hoặc trong một **generic result/data class** riêng.

## 8. Thiết kế thay thế: exception không generic, dữ liệu có thể generic

Bài toán: bạn muốn lỗi xử lý một bản ghi chứa thông tin có kiểu riêng — chẳng hạn số trạng thái HTTP, mã lỗi, đối tượng payload. Không nên tạo `OperationException<T> extends Exception`.

Thay vào đó:

1. Lớp exception **không generic** mô tả danh tính lỗi và những field ổn định.
2. Một record/class **generic độc lập với Throwable** có thể biểu diễn dữ liệu thuộc nghiệp vụ.
3. Generic method có thể nhận `Class<T>` để kiểm tra object khi thực sự cần lấy dữ liệu kiểu `T`.

Tạo tệp **`ExceptionContextDemo.java`**:

```java
public class ExceptionContextDemo {
    record FailureData<T>(String code, T payload) {}

    static final class OperationException extends Exception {
        private static final long serialVersionUID = 1L;
        private final String code;
        private final int status;

        OperationException(String message, String code, int status) {
            super(message);
            this.code = code;
            this.status = status;
        }

        FailureData<Integer> detail() {
            return new FailureData<>(code, status);
        }

        <T> T statusAs(Class<T> expected) {
            return expected.cast(status);
        }
    }

    static void load() throws OperationException {
        throw new OperationException("Not found", "NOT_FOUND", 404);
    }

    public static void main(String[] args) {
        try {
            load();
        } catch (OperationException ex) {
            System.out.println("error type: "
                    + ex.getClass().getSimpleName());
            System.out.println("detail: " + ex.detail());
            System.out.println("status: " + ex.statusAs(Integer.class));

            try {
                ex.statusAs(String.class);
            } catch (ClassCastException cast) {
                System.out.println("wrong status type: "
                        + cast.getClass().getSimpleName());
            }
        }
    }
}
```

Biên dịch và chạy:

```bash
javac -Xlint:all ExceptionContextDemo.java
java ExceptionContextDemo
```

**Stdout đã kiểm chứng:**

```text
error type: OperationException
detail: FailureData[code=NOT_FOUND, payload=404]
status: 404
wrong status type: ClassCastException
```

Chương trình này cũng đã qua `javac -Xlint:all` **không cảnh báo**.

### Vì sao hợp lệ?

`OperationException` không có type parameter trên class declaration, vì thế được phép `extends Exception`.

`FailureData<T>` là **generic record thông thường**. Nó không là subclass của `Throwable`; việc record chứa một số trạng thái và mã lỗi là dữ liệu, không phải một exception có kiểu generic.

`detail(): FailureData<Integer>` chỉ là **kiểu trả về của method** trong non-generic exception class. Method trả một generic object thông thường; Java không cấm một Throwable-subclass có phương thức trả generic type.

`<T> T statusAs(Class<T> expected)` là **generic instance method** của một exception class không generic. Nó được phép sử dụng type parameter `T`; `Class.cast` kiểm tra việc biến số `status` (được boxing thành `Integer`) sang kiểu runtime do caller yêu cầu. Khi gọi `String.class`, cast thất bại và `ClassCastException`, chứng tỏ generic method không có quyền làm sai kiểu biến thành hợp lệ.

### Thiết kế ở mức sản phẩm

Nếu context cần lưu object bất kỳ trong exception, bạn phải quyết định vấn đề **serialization**, giới hạn kiểu, quyền sở hữu và bảo mật thông tin. Mọi `Throwable` đều có liên quan tới serialization; một field tham chiếu tới payload không serializable có thể khiến quá trình serialize exception gặp vấn đề. Ví dụ ở đây lưu riêng `String` và `int` trong exception, còn `FailureData<Integer>` được **tạo khi cần** thay vì lưu một object generic không rõ khả năng serialize.

Trong thực tế, nên ưu tiên một exception class có error code, message, cause và các thuộc tính domain cần thiết; dùng một generic result/data type riêng nếu cần mang kết quả hoặc lỗi theo kiểu tường minh mà không dựa vào exception dispatch.

## 9. Generic constructor trong exception không generic vẫn hợp lệ

Một trường hợp nữa chứng minh giới hạn chỉ áp dụng ở **class declaration**.

Tạo tệp **`GenericConstructorExample.java`**:

```java
public class GenericConstructorExample {
    static final class ReportException extends Exception {
        private static final long serialVersionUID = 1L;

        <T> ReportException(T value) {
            super("failure: " + value);
        }
    }

    public static void main(String[] args) {
        try {
            throw new ReportException(42);
        } catch (ReportException ex) {
            System.out.println(ex.getMessage());
        }
    }
}
```

Chạy:

```bash
javac -Xlint:all GenericConstructorExample.java
java GenericConstructorExample
```

Stdout đã xác minh:

```text
failure: 42
```

Ở đây `<T>` thuộc **constructor**, không phải lớp `ReportException`. Một instance `new ReportException(42)` vẫn thuộc class runtime `ReportException` bình thường. Constructor chỉ dùng `T` để nhận giá trị một cách tổng quát và tạo message, không giữ “`ReportException<Integer>`” như kiểu exception tại runtime.

Có thể chọn cách viết constructor đơn giản nhận `Object` nếu không cần generic-specific inference/constraints; generic constructor không tự mang lại type safety mạnh hơn khi đối số chỉ được chuyển thành chuỗi.

## 10. Ranh giới của `throw`, `throws E` và `catch` khi exception class không generic

Lớp exception **không generic** vẫn có thể xuất hiện trong một API dùng Generics:

```java
static <E extends Exception> void relay(E failure) throws E {
    throw failure;
}
```

Đây là **method minh họa** cần đặt trong class. `throw failure` ném một **object exception hiện có**; `throws E` mô tả checked-exception contract qua type variable của **method**, hoàn toàn không khai báo subclass generic của `Throwable`. Caller có thể bắt một class thật, ví dụ `catch (IOException ex)` trong ngữ cảnh khai báo/call phù hợp. Không thể đặt `catch (E ex)`, vì catch parameter phải có exception class hợp lệ chứ không phải type variable.

Tóm tắt chính xác:

| Thiết kế | Hợp lệ? | Vì sao |
|---|---|---|
| `class Failure extends Exception` | Có | Lớp exception không generic |
| `class Failure<T> extends Exception` | Không | Generic subclass của Throwable |
| Method `<E extends Exception> ... throws E` | Có | Generic contract ở method, không ở exception class |
| `catch (E ex)` | Không | Handler không thể định danh bằng type variable |
| `new E()` | Không | Type variable không cung cấp constructor |

**Ranh giới bài này:** giới hạn quyết định **class exception được khai báo thế nào**. Những vấn đề nâng cao như cách JVM chọn catch handler, `Class<E>.isInstance`, precise rethrow và type inference trong `throws E` thuộc hướng phân tích riêng, không thể “sửa” bằng cách làm generic exception class.

## 11. Đọc bytecode với `javap`: generic method vẫn erased

Sau khi biên dịch `ExceptionGenericsDemo.java`, chạy:

```bash
javap -s -p ExceptionGenericsDemo
javap -s -p 'ExceptionGenericsDemo$ThrowingSupplier'
```

Trích kết quả **đã kiểm chứng**:

```text
static <T, E extends java.lang.Exception> T execute(
    ExceptionGenericsDemo$ThrowingSupplier<T, E>) throws E;
  descriptor: (LExceptionGenericsDemo$ThrowingSupplier;)Ljava/lang/Object;

static <E extends java.lang.Exception> void propagate(E) throws E;
  descriptor: (Ljava/lang/Exception;)V
```

Và từ interface:

```text
interface ExceptionGenericsDemo$ThrowingSupplier<T, E extends java.lang.Exception> {
  public abstract T get() throws E;
    descriptor: ()Ljava/lang/Object;
}
```

Những dòng hiển thị `<T, E extends Exception>` đến từ generic metadata mà `javap` có thể trình bày. Nhưng JVM descriptors dùng erasure:

- `T` không bound được xóa thành `Object`.
- `E extends Exception` trong **vị trí tham số kiểu của phương thức** được xóa thành `Exception`.
- `ThrowingSupplier<T,E>` được biểu diễn theo runtime interface `ThrowingSupplier`, không có biến thể class riêng theo type arguments.

**Điểm cần phân biệt:** descriptor không “mang lại” generic exception class. `throws E` vẫn là khai báo checked-exception contract tại source; runtime object được ném là một exception thực như `IOException` hoặc `DataException`.

Với `javap -v`, bạn có thể xem thêm generic `Signature` và các thuộc tính liên quan tới declared exceptions. Nhưng đừng dùng descriptor như bằng chứng rằng JVM có catch handler phân biệt `Exception<String>` và `Exception<Integer>`; những kiểu exception generic đó không tồn tại hợp lệ trong Java source.

## 12. Checked vs unchecked exception không tạo ngoại lệ cho quy tắc

| Nhóm | Ví dụ | Compile-time handling | Có được generic subclass không? |
| --- | --- | --- | --- |
| Checked exception | `Exception`, `IOException`, `DataException` | Cần catch/throws phù hợp | **Không** |
| Unchecked exception | `RuntimeException`, `IllegalArgumentException` | Không buộc catch/throws | **Không** |
| Error | `Error`, `AssertionError` | Không buộc catch/throws | **Không** |

Việc unchecked exception có thể tự lan truyền mà không phải khai báo trong `throws` **không liên quan** tới luật cấm subclass generic của `Throwable`.

Ví dụ:

```java
// Cả hai đều KHÔNG BIÊN DỊCH:
class RetryException<T> extends RuntimeException {}
class BrokenSystemError<T> extends Error {}
```

Chúng vẫn là generic classes nằm dưới cây kế thừa `Throwable`.

Thay thế đúng:

```java
class RetryException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    RetryException(String message) { super(message); }
}
```

Nếu muốn mang thêm thông tin typed về tác vụ đang retry, có thể đưa nó vào một generic result wrapper không kế thừa `Throwable`, hoặc cung cấp field/method thích hợp với một hợp đồng runtime rõ.

## 13. Những sai lầm phổ biến

**Sai lầm 1 — “Không được generic hóa bất cứ thứ gì liên quan exception.”** Không đúng. Generic methods có `throws E`, generic functional interfaces và generic data holders đều hợp lệ. Chỉ **generic class là Throwable-subclass** bị cấm.

**Sai lầm 2 — “Thay `Exception` bằng `RuntimeException` sẽ giải quyết lỗi.”** Không đúng. `RuntimeException` vẫn là subclass của `Throwable`, nên `class X<T> extends RuntimeException` vẫn bị từ chối.

**Sai lầm 3 — “Kế thừa gián tiếp sẽ tránh được giới hạn.”** Không đúng. `class X<T> extends NonGenericException` cũng sai nếu `NonGenericException` nằm dưới `Throwable`.

**Sai lầm 4 — “`throws E` bị cấm giống `catch (E)`.”** Không đúng. Đây là hai vị trí cú pháp và hai quy tắc khác nhau. `throws E` hợp lệ khi bound phù hợp; `catch (E)` không hợp lệ.

**Sai lầm 5 — “`E extends Exception` làm E thành một class thực tại runtime.”** Không đúng. `E` vẫn là type variable. Khi gọi với `IOException`, object thật là `IOException`, nhưng `E` không trở thành class literal dùng được trong catch clause.

**Sai lầm 6 — “Cứ có `<T>` trong constructor là toàn bộ exception class generic.”** Không đúng. Constructor generic có thể nằm trong một class exception **không generic**, như `ReportException` ở mục 9.

**Sai lầm 7 — “Cast hoặc reflection có thể ép `GenericException<T>` thành hợp lệ.”** Không đúng ở Java source. Runtime reflection không thay đổi quy tắc khai báo class do compiler áp dụng.

**Sai lầm 8 — “Một generic result record là exception.”** `FailureData<T>` chỉ là object dữ liệu. Nó không tự làm thay `try/catch`; muốn dùng exception flow thì ném một non-generic Throwable object hoặc dùng một API trả result rõ ràng.

**Sai lầm 9 — “Exception có field Object chứa gì cũng an toàn.”** Nếu dữ liệu đến từ nguồn không kiểm soát, vẫn cần validation/type token. Ngoài ra, `Throwable` liên quan tới serialization, nên field chứa object không serializable phải được thiết kế cẩn thận.

**Sai lầm 10 — “Vì type erasure, generic method có `throws E` không còn ích gì.”** Không đúng. Compiler vẫn kiểm tra contract generic của callback/caller, nên generic throws rất hữu ích dù bytecode dùng kiểu erased.

## 14. Khi thiết kế API thực tế, chọn cách nào?

### Bạn muốn phân biệt các loại lỗi nghiệp vụ bằng `catch`

Dùng **non-generic exception classes** thích hợp như `ValidationException`, `DataException`, `NotFoundException`. Mỗi loại có runtime class riêng, phù hợp với cách JVM dispatch exception handlers.

### Bạn muốn một exception có mã lỗi và context

Thêm field/method vào **non-generic exception class**. Giữ nguyên nhân bằng `Throwable cause`, ví dụ `super(message, cause)`. Nếu payload nhiều kiểu, xem xét serialized form, `Class<T>` token hoặc validator rõ ràng.

### Bạn muốn hàm hỗ trợ nhiều loại checked exception

Dùng method `<T, E extends Exception> T execute(ThrowingSupplier<T,E> action) throws E`. Điều này giữ được thông tin checked exception ở source mà không cần tạo generic Throwable subclass.

### Bạn muốn kiểu dữ liệu kết quả/lỗi ở compile time

Dùng **generic result wrapper** độc lập với Throwable, ví dụ một `record Result<T>(T value, String errorCode)`, hoặc một sealed hierarchy riêng để biểu diễn trạng thái thành công/thất bại. Cách này phù hợp khi lỗi là dữ liệu do caller kiểm tra thay vì exception được ném.

### Bạn muốn truyền exception vào một utility

Chấp nhận một biến `E extends Exception` hoặc một `Supplier<? extends E>` phù hợp; ném exception object thực. Không cố tự tạo `new E()` hoặc `catch (E)`.

## 15. Bài tập có đáp án

### Bài 1 — Bốn khai báo

Xét:

```java
class A extends Exception {}
class B<T> extends Exception {}
class C<T> extends RuntimeException {}
class D<T> extends A {}
```

**Câu hỏi:** những lớp nào hợp lệ?

**Đáp án:** chỉ `A` hợp lệ. `B`, `C` generic trực tiếp dưới `Throwable`; `D` generic gián tiếp dưới `Throwable`.

### Bài 2 — Generic method và generic class

```java
static <E extends Exception> void send(E error) throws E {
    throw error;
}
```

**Câu hỏi:** vì sao method này hợp lệ mà `class EBox<E> extends Exception` không hợp lệ?

**Đáp án:** type variable `E` thuộc generic method để mô tả kiểu object exception được truyền và ném; method không định nghĩa một generic class nằm dưới `Throwable`. `EBox<E>` thì định nghĩa trực tiếp một generic exception class, bị cấm.

### Bài 3 — Catch theo `E`

Cho `<E extends IOException> void handle(E e)`, có được dùng `catch(E ex)` không?

**Đáp án:** không. Type variable dù có bound cụ thể vẫn không là một catchable exception class trong catch parameter. Có thể bắt `IOException` nếu phù hợp với vùng try và các quy tắc checked exception; hoặc chuyển tiếp bằng `throws E`.

### Bài 4 — Chọn thiết kế

Một ứng dụng muốn báo lỗi `NotFound` kèm mã trạng thái `404` và metadata chi tiết kiểu riêng.

**Đáp án định hướng:** tạo `NotFoundException extends Exception`/ `RuntimeException` **không generic**, lưu `code`, `status`, `message`, `cause` phù hợp; nếu cần một cấu trúc dữ liệu typed, tạo `FailureData<Integer>` riêng. Không viết `NotFoundException<Integer> extends Exception`.

### Bài 5 — Đọc bytecode

Method `<E extends Exception> void propagate(E error) throws E` có erased parameter type nào?

**Đáp án:** `Exception`, vì giới hạn trên của `E` là `Exception`; JVM descriptor tiêu biểu `(Ljava/lang/Exception;)V`.

### Bài 6 — Constructor có `<T>`

```java
class ReportException extends Exception {
    <T> ReportException(T value) { super(String.valueOf(value)); }
}
```

**Câu hỏi:** có bị cấm bởi quy tắc generic Throwable subclass không?

**Đáp án:** không. Class `ReportException` không generic; constructor mới là thành viên generic. Đừng hiểu việc constructor có `T` là JVM tạo `ReportException<String>` class riêng.

### Bài 7 — Checked exception contract

Vì sao `ThrowingSupplier<T,E extends Exception>` có ích?

**Đáp án:** nó cho phép mô tả đồng thời kiểu kết quả `T` và loại checked exception `E` trong lời gọi, giúp caller bắt hoặc khai báo loại lỗi cụ thể khi compiler suy luận được `E`.

### Bài 8 — Chuyển exception thành dữ liệu

Nếu API không cần `throw/catch` mà muốn caller kiểm tra có lỗi hay không, cách nào phù hợp?

**Đáp án:** một generic result type hoặc sealed result hierarchy **không** kế thừa `Throwable`. Generic classes như `Result<T>` có thể giữ typed payload mà không vi phạm quy tắc exception hierarchy.

## 16. Checklist ghi nhớ

Khi thiết kế exception hoặc generic utility trong Java:

- [ ] Nếu class có `<T>`, nó **không** phải subclass trực tiếp/gián tiếp của `Throwable`.
- [ ] Không viết `class X<T> extends Exception`, `RuntimeException` hay `Error`.
- [ ] Nếu cần catch theo loại lỗi, dùng exception **class cụ thể không generic**.
- [ ] Phân biệt `catch(E)` bị cấm với `throws E` có thể hợp lệ.
- [ ] Nếu `throw error` dùng type variable, bound và khai báo `throws` phù hợp.
- [ ] Nếu cần typed callback, dùng generic interface `ThrowingSupplier<T,E>`.
- [ ] Nếu cần typed context, dùng generic data class/record độc lập với `Throwable`.
- [ ] Nếu lưu dữ liệu trong exception, cân nhắc `cause`, serialization và kiểm tra kiểu runtime.
- [ ] Nếu dùng generic method bên trong exception class, bảo đảm **class vẫn không generic**.
- [ ] Kiểm tra ví dụ bằng `javac`, đối chiếu output đúng và không cố né lỗi compiler bằng unchecked cast.

## Kết luận

**Java không cho khai báo một generic class kế thừa `Throwable` dù trực tiếp hay gián tiếp**, nên `class MyException<T> extends Exception` và `class MyRuntimeException<T> extends RuntimeException` đều sai. Đây là giới hạn của ngôn ngữ liên quan type erasure và cơ chế nhận diện exception theo runtime class.

Tuy vậy, **generics vẫn dùng được trong thiết kế xử lý lỗi**: generic methods với `throws E`, generic callback interfaces, generic constructors trong non-generic exceptions và các generic data holders đều có chỗ đứng. Hãy giữ exception classes không generic để `throw/catch` hoạt động rõ ràng, và đặt type parameters ở các API/kiểu dữ liệu thực sự cần chúng.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="generics-language-limits-005">Phân biệt kiểu trong catch với tham số kiểu được dùng trong throws</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt kiểu trong catch với tham số kiểu được dùng trong throws của Java Generics

## 1. Câu hỏi cốt lõi: vì sao throws E hợp lệ nhưng catch (E e) không hợp lệ?

Một phương thức generic trong Java có thể khai báo **tham số kiểu ngoại lệ** trong mệnh đề `throws`:

~~~java
static <E extends Exception> void relay(E problem) throws E {
    throw problem;
}
~~~

Khai báo này **hợp lệ**: compiler biết `E` là subtype của `Exception` và dùng `throws E` để mô tả hợp đồng về loại ngoại lệ mà caller phải xử lý hoặc khai báo tiếp.

Nhưng phương thức sau **không hợp lệ**:

~~~java
static <E extends Exception> void incorrect(E problem) throws E {
    try {
        throw problem;
    } catch (E caught) {   // Lỗi biên dịch: E là type variable.
        System.out.println(caught);
    }
}
~~~

Đây không phải hai cách dùng tương đương của `E`:

- `throws E` thuộc **hệ thống phân tích checked exceptions ở compile time**. Nó diễn đạt một loại ngoại lệ ràng buộc bởi tham số kiểu; compiler có thể kiểm tra các lời gọi theo kiểu suy luận được.
- `catch (E caught)` đòi **một exception type có thể dùng trực tiếp để chọn handler tại runtime**. Java không chấp nhận type variable làm loại ngoại lệ trong tham số của `catch`, vì thông tin type argument thường bị xóa bởi **type erasure**.
- `catch (IOException caught)` hay `catch (Exception caught)` hợp lệ, bởi các tên này là **class exception cụ thể**, có class identity để runtime xác định handler phù hợp.
- Các class exception không được tự khai báo generic type parameter: `class ErrorData<T> extends Exception` cũng không hợp lệ.

Mấu chốt của bài: **`throws` và `catch` có nhiệm vụ khác nhau trong ngôn ngữ**. `throws E` không hứa rằng JVM có thể tạo một nhánh catch hoạt động theo E sau erasure.

## 2. Quy tắc đối với throws và catch

### 2.1. `throws` mô tả ngoại lệ có thể thoát khỏi phương thức

Ví dụ quen thuộc:

~~~java
static void readFile() throws java.io.IOException {
    // Có thể ném IOException.
}
~~~

Một method declaration có thể chứa:
- Tên exception class `IOException`.
- Nhiều kiểu exception class phân tách bằng dấu phẩy.
- **Type variable** nếu giới hạn trên phù hợp với Throwable, ví dụ `<E extends Exception> ... throws E`.

Java phân biệt **checked exceptions** như `IOException`, `SQLException` với **unchecked exceptions** (ví dụ `RuntimeException`, `Error` và các subtype của chúng). Đối với checked exception, caller thường phải catch hoặc tiếp tục khai báo `throws`.

### 2.2. `catch` lựa chọn handler theo class ngoại lệ

~~~java
try {
    doWork();
} catch (java.io.IOException problem) {
    // Xử lý IOException và các subclass.
}
~~~

Runtime so khớp đối tượng ngoại lệ đã ném với các lớp exception trong các catch clauses theo thứ tự; nhánh đầu tiên phù hợp được chọn. Điều đó dựa trên quan hệ **class/subclass runtime**, không phải type argument generic của caller.

Ví dụ:

~~~java
try {
    doWork();
} catch (java.io.IOException | java.sql.SQLException problem) {
    // Multi-catch: xử lý một trong hai exception classes.
}
~~~

Đây là **multi-catch**. Dấu `|` liệt kê các class ngoại lệ được phép theo quy tắc Java, không phải một cú pháp tổng quát để catch mọi `E` tùy ý.

### 2.3. Khác biệt tổng hợp

| Đặc điểm | `throws E` | `catch (E e)` |
| --- | --- | --- |
| Vai trò | Hợp đồng checked-exception ở compile time. | Chọn handler xử lý ngoại lệ tại runtime. |
| E là type variable? | Được phép khi bounded đúng với Throwable. | Không được phép. |
| Có cần class exception cụ thể làm token bắt không? | Không nhất thiết tại source; erasure áp dụng khi sinh descriptor. | Có: catch parameter phải dùng exception class hợp lệ, không dùng type variable. |
| Liên quan type inference? | Có, caller có thể suy luận E và compiler xác định nghĩa vụ xử lý checked exception. | Không có catch theo type argument được suy luận. |
| Ví dụ hợp lệ | `<E extends Exception> void f() throws E`. | `catch (IOException ex)` hoặc `catch (Exception ex)`. |

**Lưu ý:** `throws E` không phải cam kết rằng “mỗi invocation chỉ có một object exception class được lưu sẵn trong E ở runtime”. Nó là chữ ký generic phục vụ kiểm tra kiểu, trong khi exception object thật vẫn có runtime class của chính nó.

## 3. Vì sao Java cấm catch (E e)?

Xét:

~~~java
static <E extends Exception> void handle(E value) throws E {
    try {
        throw value;
    } catch (E problem) {
        System.out.println(problem.getMessage());
    }
}
~~~

Với `E extends Exception`, erasure của `E` là `Exception`. Nếu compiler cho phép `catch(E)` và đơn giản biến nó thành `catch(Exception)`, nó sẽ bắt **mọi Exception phù hợp với bound**, không chỉ lớp cụ thể mà caller đã gắn cho E.

Ví dụ, cùng method về mặt source có thể được gọi với `E = IOException` hoặc `E = SQLException`, nhưng JVM không có một handler table phân biệt hai type arguments cho từng invocation generic. Biến kiểu không biến thành một class runtime chuyên biệt cho mỗi lần gọi.

Do đó, Java yêu cầu kiểu catch là **exception class cụ thể**. Ví dụ biên dịch được:

~~~java
static <E extends Exception> void handle(E value) throws E {
    try {
        throw value;
    } catch (Exception problem) {
        System.out.println(problem.getMessage());
    }
}
~~~

Đoạn này hợp lệ nhưng **`catch(Exception)` bắt theo Exception**, không bắt theo E. Nếu mục tiêu là xử lý riêng IOException, có thể viết `catch(IOException)` khi compiler xác định loại ngoại lệ đó có thể được ném và đoạn code hợp lệ theo exception analysis.

**Lỗi cụ thể của javac 17** với `catch(E problem)` có dạng:

~~~text
error: unexpected type
required: class
found:    type parameter E
~~~

Điều quan trọng ở chẩn đoán: compiler yêu cầu một **class**, không chấp nhận một **type parameter** ở vị trí đó.

## 4. Điều kiện hợp lệ của E trong throws

Một type variable muốn xuất hiện trong mệnh đề `throws` phải có giới hạn thích hợp: nó đại diện cho subtype của `Throwable`.

Ví dụ:

~~~java
static <E extends Exception> void pass(E problem) throws E {
    throw problem;
}
~~~

Hợp lệ; `E` là một subtype của `Exception` nên cũng là subtype của `Throwable`.

Một biến thể tổng quát hơn:

~~~java
static <E extends Throwable> void passAnything(E problem) throws E {
    throw problem;
}
~~~

Hợp lệ; nó có thể đại diện cho checked exceptions, unchecked exceptions hoặc Error tùy invocation. Tuy nhiên, dùng `Throwable` ở API công khai thường tạo hợp đồng quá rộng; đa số ứng dụng nên ưu tiên `Exception` hoặc một checked exception cụ thể, trừ khi có lý do rõ ràng.

Ngược lại, câu sau không hợp lệ:

~~~java
// static <E> void bad() throws E { }
// E mặc định chỉ có bound Object, không được bảo đảm là Throwable.
~~~

Tương tự, không phải mọi type variable có bound là `Object`, `Serializable` hay một interface nghiệp vụ đều có thể đứng trong `throws` nếu compiler không chứng minh được chúng là subtype của Throwable.

### Khi type variable bị xóa thì throws còn tác dụng gì?

Java thường xóa tham số kiểu `E extends Exception` theo bound `Exception` ở những nơi cần type erasure. Nhưng compiler sử dụng generic signature và type inference ở **mã nguồn caller** để quyết định checked exception tương ứng.

Kết quả là:
- Caller của method generic có thể được kiểm tra với IOException hoặc một exception type cụ thể.
- Runtime vẫn nhận object IOException thật nếu nó được ném.
- Không có phép `catch(E)` động nào được sinh ra.

## 5. Ví dụ đơn giản: một method generic “ném lại đúng kiểu”

Đây là method hợp lệ:

~~~java
static <E extends Exception> void relay(E problem) throws E {
    throw problem;
}
~~~

Cách gọi:

~~~java
try {
    relay(new java.io.IOException("network"));
} catch (java.io.IOException problem) {
    System.out.println(problem.getMessage());
}
~~~

Ở vị trí invocation, compiler có thể suy luận `E = IOException`, nên caller xử lý bằng `catch(IOException)`. Runtime nhận IOException đúng như object được ném.

Đây **không** phải một ví dụ dùng `catch(E)`. Caller bắt **IOException**, một class exception thật, trong khi `E` chỉ xuất hiện trong chữ ký của method generic.

Nếu caller không bắt hoặc tiếp tục khai báo checked exception thích hợp, compiler có thể báo:

~~~text
unreported exception IOException; must be caught or declared to be thrown
~~~

Đó là tác dụng của hợp đồng `throws E` ở compile time.

## 6. Generic functional interface có exception type riêng

Một tình huống áp dụng thực tế hơn là truyền một hành động có checked exception mà nó được phép ném:

~~~java
@FunctionalInterface
interface CheckedAction<E extends Exception> {
    void run() throws E;
}
~~~

Ví dụ adapter:

~~~java
static <E extends Exception> void execute(CheckedAction<E> action) throws E {
    action.run();
}
~~~

Đặc điểm:

- `CheckedAction<E>` giữ E như một tham số generic của hợp đồng hàm.
- `run() throws E` cho phép implementation ném một checked exception thuộc E.
- `execute(...) throws E` chuyển tiếp hợp đồng mà không cần bắt ngoại lệ.
- Caller có thể gán `CheckedAction<IOException>` và bắt IOException cụ thể.

Đoạn sử dụng:

~~~java
CheckedAction<java.io.IOException> action =
        () -> { throw new java.io.IOException("read failed"); };

try {
    execute(action);
} catch (java.io.IOException problem) {
    System.out.println(problem.getMessage()); // read failed.
}
~~~

Điều này rất hữu ích khi viết callback API, transaction wrapper, job runner hoặc utility xử lý checked exceptions mà không muốn ép mọi caller về `throws Exception` rộng hơn mức cần thiết.

Nếu có nhiều loại checked exception độc lập, một tham số `E` không nhất thiết diễn đạt toàn bộ các loại ngoại lệ chính xác như một chữ ký liệt kê nhiều class. Khi thiết kế API cần cân nhắc mức trừu tượng, bounds và cách xử lý.

## 7. Muốn bắt ngoại lệ tại runtime theo E thì làm thế nào?

Java không chấp nhận:

~~~java
// catch (E e) { ... } // Không hợp lệ.
~~~

Nhưng một method generic có thể dùng **runtime class token** `Class<E>` cùng một catch class cụ thể:

~~~java
static <E extends Exception> void inspectThenRethrow(
        CheckedAction<E> action, Class<E> type) throws E {

    try {
        action.run();
    } catch (Exception actual) {
        if (type.isInstance(actual)) {
            System.out.println(
                    "caughtRuntime=" +
                    actual.getClass().getSimpleName()
            );
            throw type.cast(actual);
        }

        throw new IllegalStateException(
                "Unexpected exception", actual
        );
    }
}
~~~

Ở đây:

1. `catch (Exception actual)` là một handler hợp lệ cho class Exception.
2. `type` là một **Class<E> token** được caller cung cấp, ví dụ `IOException.class`.
3. `type.isInstance(actual)` hỏi runtime object exception có tương thích với class trong token không.
4. `type.cast(actual)` thực hiện checked cast với class token rồi cho giá trị E ở mức source.
5. `throw type.cast(actual)` thỏa `throws E` ở chữ ký method.
6. Nếu exception không tương thích, ví dụ này bọc nó trong một `IllegalStateException` thay vì gán sai thành E.

**Không đồng nhất điều này với catch(E):** JVM vẫn chọn handler `Exception` trước; sau khi vào nhánh, code chủ động kiểm tra runtime class. Cách làm có token phù hợp khi thật sự cần phân biệt một exception type động do caller cung cấp.

### Vì sao nên xử lý trường hợp ngoại lệ không tương thích?

Nếu method tự ý ép mọi Exception thành E bằng unchecked cast, runtime có thể tạo một hợp đồng giả và phát `ClassCastException` tại nơi khác. Với class token, method có thể **kiểm tra chính xác class runtime** hoặc quyết định bọc/đưa ngoại lệ khác đi theo một đường rõ ràng.

Trong ví dụ, `IllegalStateException` là unchecked exception. API sản phẩm có thể chọn quy tắc khác, ví dụ thêm type parameter hoặc kiểu trả về kết quả, nhưng phải mô tả hợp đồng cụ thể. Đừng giả định mọi Exception bắt được đều là E chỉ vì hành động có chữ ký `throws E`: một exception unchecked khác vẫn có thể phát sinh trong implementation.

## 8. Chương trình Java 17+ hoàn chỉnh và chạy được

Lưu tệp sau với tên **GenericExceptionDemo.java**:

~~~java
import java.io.IOException;
import java.sql.SQLException;

public class GenericExceptionDemo {
    @FunctionalInterface
    interface CheckedAction<E extends Exception> {
        void run() throws E;
    }

    static <E extends Exception> void execute(
            CheckedAction<E> action) throws E {
        action.run();
    }

    static <E extends Exception> void relay(E problem) throws E {
        throw problem;
    }

    static <E extends Exception> void inspectThenRethrow(
            CheckedAction<E> action, Class<E> type) throws E {

        try {
            action.run();
        } catch (Exception actual) {
            if (type.isInstance(actual)) {
                System.out.println(
                        "caughtRuntime=" +
                        actual.getClass().getSimpleName()
                );
                throw type.cast(actual);
            }

            throw new IllegalStateException(
                    "Unexpected exception", actual
            );
        }
    }

    // Java 7+ precise rethrow; xem phần 11.
    static void precise(boolean io) throws IOException, SQLException {
        try {
            if (io) {
                throw new IOException("disk");
            }
            throw new SQLException("database");
        } catch (Exception exception) {
            throw exception;
        }
    }

    public static void main(String[] args) {
        CheckedAction<IOException> ioAction = () -> {
            throw new IOException("read failed");
        };

        try {
            execute(ioAction);
        } catch (IOException problem) {
            System.out.println(
                    "execute=" + problem.getMessage()
            );
        }

        try {
            relay(new IOException("network"));
        } catch (IOException problem) {
            System.out.println(
                    "relay=" + problem.getMessage()
            );
        }

        try {
            inspectThenRethrow(
                    ioAction, IOException.class
            );
        } catch (IOException problem) {
            System.out.println(
                    "typedRethrow=" + problem.getMessage()
            );
        }

        try {
            precise(false);
        } catch (IOException | SQLException problem) {
            System.out.println(
                    "precise=" +
                    problem.getClass().getSimpleName()
            );
        }
    }
}
~~~

Biên dịch và chạy bằng JDK hỗ trợ Java 17:

~~~bash
javac --release 17 -Xlint:all GenericExceptionDemo.java
java GenericExceptionDemo
~~~

**Stdout chính xác:**

~~~text
execute=read failed
relay=network
caughtRuntime=IOException
typedRethrow=read failed
precise=SQLException
~~~

Chương trình này được thiết kế để không cần raw types hoặc unchecked casts. Nó minh họa kiểu ngoại lệ ở `throws E` vẫn có ý nghĩa với caller trong khi handler runtime được viết bằng **class exception hợp lệ**.

### Giải thích từng dòng kết quả

- `execute=read failed`: `CheckedAction<IOException>` giữ hợp đồng ngoại lệ IOException; caller catch IOException như bình thường.
- `relay=network`: method `relay` ném chính object IOException, caller bắt bằng exception class IOException.
- `caughtRuntime=IOException`: bên trong `inspectThenRethrow`, `catch(Exception)` bắt object, token `IOException.class` xác định loại runtime phù hợp và in ra tên class.
- `typedRethrow=read failed`: method tiếp tục ném cùng exception theo chữ ký `throws E`; caller xử lý IOException.
- `precise=SQLException`: nhánh `precise(false)` ném SQLException, handler multi-catch bắt đúng class và in tên class.

Mỗi dòng minh họa cách **static checked-exception contract**, **runtime exception class** và **catch handler** phối hợp mà không phải dùng `catch(E)`.

## 9. Các trường hợp cố ý sai: kiểm chứng qua javac

Đoạn sau **không phải** mã chạy được. Lưu riêng thành **InvalidGenericExceptions.java** nếu muốn xem compiler diagnostics:

~~~java
class InvalidGenericExceptions {
    static <E extends Exception> void bad(E input) throws E {
        try {
            throw input;
        } catch (E exception) {  // Không hợp lệ: E là type variable.
            System.out.println(exception);
        }
    }
}

class GenericProblem<T> extends Exception {
    // Không hợp lệ: generic class kế thừa Throwable.
}

class GenericRuntimeProblem<T> extends RuntimeException {
    // Cũng không hợp lệ.
}
~~~

Biên dịch để thấy lỗi:

~~~bash
javac --release 17 InvalidGenericExceptions.java
~~~

Những chẩn đoán quan trọng:

~~~text
error: unexpected type
required: class
found:    type parameter E

error: a generic class may not extend java.lang.Throwable
~~~

Compiler thường phát một lỗi riêng cho `catch(E)` và mỗi generic class kế thừa Throwable. Lý do không phụ thuộc class đó kế thừa trực tiếp Throwable hay gián tiếp qua Exception/RuntimeException: **không thể khai báo generic subclass của Throwable**.

Đừng trộn file chứa mã cố ý sai với `GenericExceptionDemo.java` khi mục đích là biên dịch/running chương trình hoàn chỉnh.

## 10. Vì sao catch theo type argument không khả thi dù method biết `E`?

Điểm dễ nhầm là **một object exception có runtime class cụ thể** không đồng nghĩa compiler có thể chuyển **type variable `E`** thành một exception handler riêng. Với `<E extends Exception>`, thông tin `E` thuộc hợp đồng generic tại compile time; cùng method có thể được gọi ở ngữ cảnh suy luận `E = IOException` hoặc `E = SQLException`. JVM không tạo một handler mới cho mỗi type argument đó.

Vì thế đoạn sau **cố ý không biên dịch**:

~~~java
static <E extends Exception> void bad(E failure) throws E {
    try {
        throw failure;
    } catch (E caught) { // Type variable không phải catch parameter type hợp lệ.
        System.out.println(caught);
    }
}
~~~

Dùng `catch (Exception actual)` hoặc một exception class cụ thể nếu muốn **bắt lỗi**, sau đó có thể kiểm tra `Class<E> token` bằng `token.isInstance(actual)` hoặc `token.cast(actual)` theo hợp đồng rõ ràng. Nhưng runtime token **không làm** cú pháp `catch(E)` trở nên hợp lệ.

Java cũng cấm **generic subclass của `Throwable`** (kể cả dưới `RuntimeException`), nên không thể “vòng qua” bằng một `GenericError<E>` để phân biệt các handler theo type argument. Một lớp exception không generic vẫn có thể có generic methods, constructor hoặc payload được giữ bằng wrapper.

**Nội dung riêng cần nhớ ở đây:** `throws E` được compiler dùng để theo dõi checked exception từ lời gọi; `catch` của JVM dùng runtime exception class. Hai cơ chế phục vụ hai nhiệm vụ khác nhau, dù cùng nằm trong xử lý lỗi.

## 11. Precise rethrow: catch(Exception) nhưng vẫn giữ loại checked exception hẹp

Một trường hợp dễ nhầm với `throws E` là **precise rethrow analysis** từ Java 7.

~~~java
static void precise(boolean io)
        throws java.io.IOException, java.sql.SQLException {

    try {
        if (io) {
            throw new java.io.IOException("disk");
        }
        throw new java.sql.SQLException("database");
    } catch (Exception exception) {
        throw exception;
    }
}
~~~

Ở đây catch parameter có kiểu **Exception**, không phải type variable E. Tuy nhiên compiler phân tích:
- Trong try có thể phát sinh IOException hoặc SQLException.
- Catch parameter `exception` không bị gán lại (effectively final).
- `throw exception` trong catch có thể được phân tích chính xác theo các exception types từ try.

Do đó method vẫn có thể khai báo `throws IOException, SQLException` mà không cần khai báo một `throws Exception` rộng.

Điều này hoàn toàn khác với việc cho phép `catch(E)`. Precise rethrow là một **quy tắc phân tích loại ngoại lệ ở compile time**, không phải catch runtime theo generic type argument.

### Điều kiện effectively final rất quan trọng

Nếu bạn gán lại catch parameter rồi ném nó, compiler có thể không còn suy luận hẹp theo cách precise rethrow và phải xem xét kiểu khai báo `Exception` rộng hơn. Khi đó, một method chỉ `throws IOException, SQLException` có thể không còn biên dịch.

Vì vậy, khi cần bắt để log rồi ném lại, giữ catch parameter không bị gán lại:

~~~java
try {
    riskyOperation();
} catch (Exception problem) {
    log(problem);
    throw problem;
}
~~~

Đoạn trên là **mảnh mã minh họa**, cần phương thức `riskyOperation()` và `log(...)` trong chương trình thực tế; quy tắc ngoại lệ cụ thể vẫn phụ thuộc các checked exceptions được biết từ khối try.

## 12. Multi-catch có thay thế được catch(E) không?

Multi-catch:

~~~java
try {
    work();
} catch (java.io.IOException | java.sql.SQLException problem) {
    System.out.println(problem.getMessage());
}
~~~

có thể xử lý nhiều class exception cụ thể trong một handler.

Nhưng nó **không** có nghĩa “catch một generic type union E”. Ví dụ sau vẫn sai:

~~~java
// catch (E | IOException problem) { }
// E là type variable, không được dùng làm catch type.
~~~

Trong multi-catch, các exception alternatives phải là các class exception hợp lệ; không được có một alternative là subtype của alternative khác trong cùng multi-catch, và biến multi-catch là implicitly final.

Nếu cần logic chung cho nhiều class ngoại lệ, có thể:
- Dùng multi-catch khi danh sách class hữu hạn và đã biết.
- Catch một superclass thích hợp và phân nhánh bằng `instanceof`.
- Dùng class token nếu exception class mục tiêu được truyền vào runtime.
- Để exception đi tiếp qua `throws E` nếu method không cần xử lý.

Chọn giải pháp theo mục tiêu xử lý runtime và hợp đồng compile-time, không theo việc có type parameter trong chữ ký hay không.

## 13. Overriding, checked exceptions và generic throws

Khi ghi đè một method, Java không cho subclass tùy ý mở rộng tập **checked exceptions** được phép thoát ra ngoài so với hợp đồng supertype.

Ví dụ:

~~~java
class BaseJob {
    void run() throws java.io.IOException {
    }
}

class SpecificJob extends BaseJob {
    @Override
    void run() throws java.io.FileNotFoundException {
    }
}
~~~

Hợp lệ vì FileNotFoundException là subtype của IOException.

Ngược lại, một override chỉ muốn thêm `SQLException` vào method có hợp đồng `throws IOException` sẽ không hợp lệ nếu checked exception đó không được cho phép bởi quy tắc override.

Với generic methods có `throws E`, compiler cũng kiểm tra mối quan hệ giữa chữ ký được kế thừa và method override sau các phép thay thế kiểu phù hợp. `throws E` không cho phép một implementation phớt lờ contract exception của interface/base class.

**Ý nghĩa thực hành:** `throws E` là cách mô hình hóa checked-exception contract, không phải một escape hatch để mọi subclass ném thêm bất kỳ checked exception nào.

## 14. Type inference ở throws E: đừng nhầm với catch runtime

Khi gọi:

~~~java
static <E extends Exception> void relay(E problem) throws E {
    throw problem;
}

relay(new java.io.IOException("network"));
~~~

type inference có thể xác định E theo biểu thức đối số. Với checked exception IOException, caller phải xử lý phù hợp.

Nhưng nếu truyền một biến có kiểu tĩnh rộng hơn:

~~~java
Exception general = new java.io.IOException("network");
relay(general);
~~~

compiler có thể suy luận E là `Exception` theo kiểu tĩnh biến `general`, dù object thực tế là IOException. Khi đó **hợp đồng compile time rộng hơn**; caller cần catch Exception hoặc khai báo tương ứng.

Runtime vẫn ném đúng đối tượng IOException gốc. Đây là ví dụ rõ nhất để tách:
- **Kiểu tĩnh và type inference của E** trong `throws`.
- **Class runtime thực sự của exception object**.
- **Catch handler** mà caller dùng để xử lý.

Không được giả định compiler suy luận E dựa trên runtime class của dữ liệu trong biến `Exception general`.

### Vì sao generic throws không tự là một “sneaky throw” API đáng dùng?

Có thể bắt gặp các kỹ thuật dùng generic type inference và unchecked casts để lách quy tắc checked exceptions. Chúng không cần thiết cho việc hiểu khác biệt giữa `throws E` và `catch(E)`, và dễ làm hợp đồng API trở nên khó đoán.

Trong code nghiệp vụ, ưu tiên khai báo đúng checked exceptions hoặc dùng abstraction rõ như `CheckedAction<E>`. Đừng sử dụng thủ thuật exception typing để che checked exception khỏi caller khi họ cần được biết và xử lý nó.

## 15. Class<E> token giải quyết được và không giải quyết được gì?

Với:

~~~java
static <E extends Exception> boolean matches(
        Exception problem, Class<E> type) {
    return type.isInstance(problem);
}
~~~

Caller có thể hỏi:

~~~java
boolean io = matches(
        new java.io.IOException("disk"),
        java.io.IOException.class
); // true.
~~~

Đây là **runtime class matching hợp lệ**. Nó kiểm tra trực tiếp object ngoại lệ theo class token.

Nhưng class token **không thay đổi cú pháp catch**:
- Không thể viết `catch(type)`.
- Không thể viết `catch(E problem)` chỉ vì đang giữ `Class<E>`.
- Handler runtime vẫn được khai báo bằng exception class như `Exception` hoặc `IOException`.
- Khi đã catch một class rộng, bên trong mới dùng `type.isInstance(...)` để phân biệt.

Nếu cần xử lý một số loại checked exceptions cụ thể mà không muốn catch `Exception` quá rộng, có thể thiết kế API với nhiều catch clauses cụ thể hoặc phân tách logic tại caller. Token động không thay thế mọi hình thức checked-exception analysis.

## 16. Khi nào một ngoại lệ unchecked vẫn lọt qua E?

Method:

~~~java
static <E extends Exception> void execute(
        CheckedAction<E> action) throws E {
    action.run();
}
~~~

bảo toàn checked exception contract của E. Nhưng body callback vẫn có thể ném một `NullPointerException`, `IllegalArgumentException` hoặc subclass khác của RuntimeException.

Những ngoại lệ unchecked đó **không bắt buộc phải khai báo trong throws**. Vì vậy, nói rằng `execute(... ) throws E` “chỉ có thể ném chính xác E và không có exception nào khác” là sai.

Đó là lý do phương thức `inspectThenRethrow` ở ví dụ phải quyết định xử lý thế nào khi `catch(Exception)` bắt một exception không đúng class token. Nó không nên ép mù quáng thành E.

Tương tự, `Error` không phải subtype của Exception nên `catch(Exception)` không bắt `Error`. Nếu API đặc biệt cần xử lý Throwable rộng, đó là một quyết định khác với tác động đáng kể đến cách xử lý lỗi nghiêm trọng; tránh catch Throwable một cách máy móc.

## 17. Lỗi thường gặp khi review Java Generics và exceptions

### Lỗi A — thấy throws E rồi viết catch(E)

~~~java
// <E extends Exception> ... throws E
// catch (E e) { } // Bị compiler từ chối.
~~~

**Sửa:** Catch một exception class thật; hoặc để ngoại lệ propagate theo `throws E`.

### Lỗi B — biến exception subclass thành generic class

~~~java
// class Failure<T> extends RuntimeException {} // Không hợp lệ.
~~~

**Sửa:** Non-generic exception class, gắn payload qua Object hoặc wrapper được kiểm soát, giữ generic typing ở tầng API phù hợp.

### Lỗi C — nghĩ bound E extends Exception làm E reifiable

Type variable không tự trở thành class runtime có thể catch. Runtime object có class cụ thể, nhưng E ở chữ ký generic vẫn chịu erasure.

### Lỗi D — dùng catch(Exception) rồi giả định mọi thứ bắt được là E

Có thể có unchecked exceptions không tương thích với token E. Dùng `Class<E>.isInstance` hoặc chính sách khác thay vì unchecked cast.

### Lỗi E — nhầm multi-catch và generic catch

`catch(IOException | SQLException ex)` sử dụng hai classes cụ thể. Nó không nhận một type parameter đại diện cho union.

### Lỗi F — khai báo throws Exception dù method chỉ ném IOException

Một hợp đồng `throws Exception` rộng hơn cần thiết làm tăng gánh nặng cho caller. Dùng `throws IOException` hoặc `throws E` khi E thực sự có vai trò mô hình hóa typed callback.

### Lỗi G — gán lại catch parameter rồi mong precise rethrow vẫn thu hẹp

Precise rethrow dựa vào exception analysis và điều kiện catch parameter effectively final. Đừng phá điều kiện đó nếu muốn giữ chữ ký throws hẹp.

### Lỗi H — gắn @SuppressWarnings lên unchecked cast ngoại lệ mà không hiểu nguồn gốc

Suppress không thay runtime exception class hoặc đảm bảo dữ liệu thực sự là E. Nếu cần kiểm tra động, hãy dùng runtime class token phù hợp.

## 18. Quy trình chọn giải pháp

Khi thiết kế method xử lý ngoại lệ trong generic API:

1. **Bạn cần xử lý ngoại lệ hay chỉ chuyển tiếp?** Nếu chỉ chuyển tiếp, cân nhắc `throws E` để giữ hợp đồng checked exception của callback.
2. **Kiểu checked exception có cố định không?** Nếu là IOException/SQLException đã biết, khai báo trực tiếp thường rõ hơn generic E.
3. **Bạn có định viết catch(E) không?** Hãy thay đổi thiết kế: Java không chấp nhận type variable trong catch.
4. **Runtime cần phân biệt exception class động không?** Nhận thêm `Class<E>`, bắt một class thích hợp rồi dùng `isInstance/cast`.
5. **Một generic exception class có cần thiết không?** Không thể dùng generic class làm subclass Throwable; chuyển generic information ra class wrapper/API khác.
6. **Bạn catch rộng để log rồi rethrow?** Kiểm tra precise rethrow và catch parameter effectively final.
7. **Bạn có bắt RuntimeException/Error ngoài ý muốn không?** Viết rõ chính sách ứng với checked, unchecked và fatal errors.
8. **Có override method không?** Giữ hợp đồng checked exception compatible với supertype.
9. **Có dùng generic inference cho E không?** Nhớ rằng inference dựa trên kiểu tĩnh tại call site, không theo runtime class của exception đang cất trong biến rộng.
10. **Kiểm chứng javac với source release mục tiêu**, ví dụ `javac --release 17`, và thử cả ví dụ đúng lẫn sai để thấy nơi compiler yêu cầu class exception thật.

## 19. Câu hỏi tự kiểm tra

**Câu 1.** Vì sao `static <E extends Exception> void f() throws E` được phép nhưng `catch(E e)` không được phép?

**Đáp án:** `throws E` là hợp đồng checked exception tại compile time; `catch` cần một exception class runtime. E là type variable bị erasure, không dùng trực tiếp để xác định handler.

**Câu 2.** `E extends Throwable` có dùng ở `throws E` được không?

**Đáp án:** Có. E được đảm bảo là subtype của Throwable. Tuy nhiên contract rộng như vậy có thể bao gồm các Error/unchecked exception nên nên dùng theo nhu cầu cụ thể.

**Câu 3.** Có thể viết `class Failure<T> extends Exception` không?

**Đáp án:** Không. Java cấm generic class là subclass của Throwable, dù trực tiếp hay gián tiếp.

**Câu 4.** Một method nhận `CheckedAction<IOException>` và khai báo `throws E` có buộc caller dùng `catch(E)` không?

**Đáp án:** Không. Caller có thể xử lý bằng `catch(IOException)` hoặc khai báo tiếp IOException. E được thay bằng type argument ở compile time, còn catch vẫn dùng exception class thật.

**Câu 5.** Runtime muốn biết exception có tương thích class token `IOException.class` thì làm thế nào?

**Đáp án:** Catch bằng một exception class hợp lệ trước, sau đó dùng `IOException.class.isInstance(actual)` và nếu cần `IOException.class.cast(actual)`. Điều này không biến `catch(E)` thành hợp lệ.

**Câu 6.** Precise rethrow có liên quan thế nào đến `catch(Exception)`?

**Đáp án:** Nếu try chỉ có các checked exceptions được compiler phân tích và catch parameter không bị gán lại, `throw exception` có thể giữ các loại checked exception hẹp hơn Exception trong mệnh đề throws. Không phải generic catch.

**Câu 7.** Với `Exception e = new IOException("disk"); relay(e);`, compiler nhìn E là kiểu nào?

**Đáp án:** Có thể suy luận E theo kiểu tĩnh Exception của biểu thức `e`, dù object runtime là IOException. Caller cần xử lý checked exception theo hợp đồng kiểu tĩnh rộng tương ứng.

**Câu 8.** `throws E` có bảo đảm không xảy ra NullPointerException không?

**Đáp án:** Không. RuntimeException và nhiều unchecked exceptions vẫn có thể phát sinh mà không cần khai báo trong throws.

**Câu 9.** Vì sao multi-catch `catch(IOException | SQLException ex)` hợp lệ nhưng `catch(E | IOException ex)` sai?

**Đáp án:** Multi-catch yêu cầu các alternatives là exception classes hợp lệ; E là type variable không được dùng làm catch type.

**Câu 10.** Nếu chỉ cần truyền callback có checked exception IOException qua utility generic, nên chọn gì?

**Đáp án:** Một interface như `CheckedAction<E extends Exception>` và utility `<E extends Exception> void execute(CheckedAction<E>) throws E` có thể bảo toàn checked-exception typing mà không cần catch(E) hay ép về throws Exception rộng.

## 20. Ghi nhớ

**`throws E` và `catch(E)` không đối xứng trong Java Generics.**

- `throws E` hợp lệ nếu E bị ràng buộc là một subtype của Throwable, vì compiler có thể phân tích checked-exception contract bằng generic type inference.
- `catch(E e)` không hợp lệ: handler cần exception class runtime cụ thể, không phải type variable sau erasure.
- Class ngoại lệ generic như `class Failure<T> extends Exception` cũng không hợp lệ.
- Nếu cần bắt rồi kiểm tra ngoại lệ theo kiểu động, hãy dùng một catch class hợp lệ kết hợp `Class<E>` token và `isInstance/cast`.
- Precise rethrow, multi-catch và generic throws là những tính năng **khác nhau**; chúng giải quyết những vấn đề khác nhau.

Nguyên tắc thiết kế là phân biệt rõ **kiểu tĩnh của checked exception tại lời gọi**, **class runtime của đối tượng ngoại lệ** và **loại class được ghi trong catch handler**. Hiểu được ba tầng này giúp viết generic APIs linh hoạt mà vẫn đúng quy tắc xử lý ngoại lệ của Java.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="generics-language-limits-006">Enum và kiểu annotation không được khai báo tham số kiểu</a>

<details>
<summary>Xem chi tiết</summary>

# Enum và kiểu annotation không được khai báo tham số kiểu trong Java Generics

## 1. Vấn đề và mục tiêu

Java Generics cho phép định nghĩa class hoặc interface có **tham số kiểu**:

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

interface Converter<I, O> {
    O convert(I input);
}
```

Caller có thể dùng `Box<String>`, `Box<Integer>` hoặc `Converter<String, Integer>`. Nhưng **không phải mọi dạng khai báo kiểu trong Java đều được phép đặt `<T>` ngay sau tên**. Hai trường hợp quan trọng:

```java
// Không hợp lệ:
enum Choice<T> {
    FIRST, SECOND
}
```

```java
// Không hợp lệ:
@interface Info<T> {
    T value();
}
```

Với Java 17, `javac` báo lần lượt những lỗi có nội dung chính:

```text
error: enums cannot be generic
```

```text
error: annotation interface Info cannot be generic
```

Điều này không có nghĩa enum hoặc annotation “không thể liên quan đến Generics”. Một enum vẫn **có thể triển khai generic interface, có method generic, có field sử dụng parameterized type**. Annotation vẫn có thể sử dụng **một số kiểu `Class` có type argument** làm kiểu trả về của element, và có thể được gắn lên generic class, type parameter hoặc type argument theo `@Target`.

Bài này giúp phân biệt:

- Những khai báo generic **bị cấm** ở cấp `enum` và `@interface`.
- Những **cách sử dụng Generics vẫn hợp lệ** bên trong enum hoặc khi định nghĩa/sử dụng annotation.
- Vì sao `Enum<E extends Enum<E>>` trong thư viện chuẩn **không** có nghĩa chúng ta được viết `enum E<T>`.
- Vì sao annotation elements bị giới hạn kiểu trả về và không phải method generic thông thường.
- Cách mô hình hóa enum gắn với generic API và annotation metadata một cách type-safe.
- Lỗi compiler, ví dụ Java 17 chạy được, bài tập và hướng khắc phục.

## 2. Bắt đầu từ bốn khái niệm không được nhầm

| Khái niệm | Ví dụ | Ý nghĩa |
| --- | --- | --- |
| Generic **class** | `class Holder<T> {}` | Class tự khai báo một hoặc nhiều type parameter. |
| Generic **interface** | `interface Codec<T> {}` | Interface tự khai báo type parameter. |
| **Parameterized type** | `Codec<Integer>`, `List<String>` | Một kiểu generic được **sử dụng** với type argument cụ thể. |
| Generic **method** | `<T> T echo(T x)` | Phương thức khai báo type parameter riêng, độc lập với việc class có generic hay không. |

Một `enum` có thể sử dụng **parameterized type** hoặc khai báo **generic method**, nhưng không thể là một **generic enum declaration** với `<T>` sau tên.

Một `@interface` không thể là **generic annotation declaration**; hơn nữa các method tạo thành **annotation elements** cũng không được generic như `<T> T value()`.

**Quy tắc đọc source:** trước khi kết luận “Java cấm Generics ở đây”, hãy tìm xem `<T>` là tham số kiểu **của chính enum/annotation**, của **một method trong enum**, hay là type argument của một interface/class mà enum đang dùng. Các vị trí khác nhau tuân theo quy tắc khác nhau.

## 3. Vì sao Java không cho khai báo `enum E<T>`?

Ví dụ cố ý sai:

```java
enum State<T> {
    NEW, DONE
}
```

Biên dịch với Java 17 sẽ gặp lỗi:

```text
error: enums cannot be generic
```

Enum declaration chỉ có thể khai báo một tập hữu hạn các **enum constants** thuộc kiểu enum đó. Java không hỗ trợ cú pháp khai báo type parameters trên chính enum như đối với class hoặc interface thông thường.

Điều đáng chú ý: ngay cả một enum chỉ có một kiểu giá trị liên quan đến `T` cũng không được viết:

```java
// Cố tình sai:
enum Token<T> {
    TEXT, NUMBER
}
```

Không có cách hợp lệ để “cung cấp” `T` khi sử dụng từng enum constant bằng cú pháp `Token<String>.TEXT`. Nếu hệ thống cần các giá trị biến thiên mạnh theo type parameter, hãy dùng generic class, record, interface hoặc mô hình domain phù hợp.

### 3.1. Enum là một kiểu riêng, không phải class generic do người dùng khai báo

Một enum vẫn là một kiểu tham chiếu có các instance cố định, có thể:

- Khai báo constructor, field và instance method.
- Khai báo static method và generic method.
- Triển khai interface.
- Chứa enum constant-specific class body để ghi đè hành vi.
- Sử dụng generic collections như `List<String>` và `Map<String,Integer>` bên trong code.

Nhưng **không được** đặt danh sách type parameter sau tên enum:

```java
// Sai: khai báo enum generic.
enum Status<T> { READY; }

// Đúng: enum không có tham số kiểu.
enum Status { READY; }
```

### 3.2. `Enum<E extends Enum<E>>` là trường hợp khác

Trong Java API có class trừu tượng chuẩn:

```java
public abstract class Enum<E extends Enum<E>>
        implements Comparable<E>, java.io.Serializable {
    // Chỉ là trích đoạn mô tả quan hệ kiểu.
}
```

Đây là **một generic class có sẵn trong JDK** để làm lớp cha của enum. Một enum cụ thể như `Status` được Java cho quan hệ kiểu tương ứng với `Enum<Status>` theo các quy tắc chuyên biệt của enum.

Vì vậy:

```java
enum Status {
    READY, DONE
}
```

không hề tự khai báo `<T>`. Việc superclass `Enum` **được thiết kế generic** không đồng nghĩa declaration `enum` cũng được phép generic.

Không thể viết:

```java
// Cố tình sai: enum không được tự khai báo type parameter.
// enum Status<T> extends Enum<Status<T>> { ... }
```

Thực tế, enum có quan hệ lớp cha chuyên biệt do Java quy định; lập trình viên cũng không được tự chỉ định một `extends` clause cho enum như class thông thường.

## 4. Enum vẫn triển khai được generic interface

Một giải pháp thiết kế hợp lệ:

```java
interface Decoder<T> {
    T decode(String source);
}

enum NumberLevel implements Decoder<Integer> {
    LOW(1), HIGH(3);

    private final int multiplier;

    NumberLevel(int multiplier) {
        this.multiplier = multiplier;
    }

    @Override
    public Integer decode(String source) {
        return Integer.valueOf(source) * multiplier;
    }
}
```

`NumberLevel` **không** phải một enum generic: nó không khai báo `<T>`. Thay vào đó, nó triển khai **parameterized interface** `Decoder<Integer>`.

Caller:

```java
Decoder<Integer> decoder = NumberLevel.HIGH;
Integer result = decoder.decode("7"); // 21
```

Compiler kiểm tra kiểu trả về và tham số qua `Decoder<Integer>`. Mỗi enum constant vẫn là giá trị thuộc enum `NumberLevel`.

### 4.1. Bound/type argument được chốt tại enum declaration

Toàn bộ enum `NumberLevel` triển khai `Decoder<Integer>`, không phải một constant triển khai `Decoder<String>` còn constant khác triển khai `Decoder<Double>` theo những specialization tùy chọn của chính enum.

Ví dụ mong muốn nhưng không được tạo bằng `<T>` của enum:

```java
// Không có cú pháp kiểu riêng cho từng constant:
// NumberLevel<String>.LOW
// NumberLevel<Integer>.HIGH
```

Nếu mỗi loại giá trị có hợp đồng generic khác nhau, cân nhắc một generic sealed interface và nhiều concrete implementations, hoặc các typed descriptor objects. Dùng một enum chung khi các constant thật sự chia sẻ cùng một API và kiểu dữ liệu.

### 4.2. Enum có thể lưu dữ liệu mang kiểu generic

Ví dụ field sử dụng parameterized type:

```java
enum MessageType {
    INFO, ERROR;

    private final java.util.List<String> tags =
            new java.util.ArrayList<>();

    java.util.List<String> tags() {
        return java.util.List.copyOf(tags);
    }
}
```

`List<String>` là **kiểu của field/return**, không phải type parameter của enum. Cấu trúc này hợp lệ.

Lưu ý enum constants là các instance được chia sẻ trong phạm vi chương trình, tương tự singleton cho từng constant. Nếu field là mutable, cần suy nghĩ tới ownership, thread safety và cách xuất dữ liệu. Phương thức `List.copyOf` tạo snapshot không thể sửa qua API cập nhật thông thường, nhưng bản thân enum vẫn cần chính sách mutation phù hợp.

## 5. Generic method bên trong enum là hợp lệ

Một phương thức có thể tự khai báo `<T>` dù enum chứa nó không generic:

```java
enum Utility {
    INSTANCE;

    public <T> T echo(T value) {
        return value;
    }
}
```

Cách dùng:

```java
String text = Utility.INSTANCE.echo("Java");
Integer number = Utility.INSTANCE.echo(42);
```

Type variable `T` ở đây thuộc **method `echo`**. Compiler có thể suy luận kiểu cho từng lời gọi độc lập; không cần class/enum có một tham số kiểu cố định.

Đừng nhầm:

```java
enum Utility<T> { INSTANCE; } // Sai.
```

với:

```java
enum Utility {
    INSTANCE;

    <T> T echo(T value) { return value; } // Đúng.
}
```

### 5.1. Generic static method với enum-bound

Một trường hợp thực tế là thao tác tổng quát với **bất kỳ enum nào**:

```java
static <E extends Enum<E>> E parse(
        Class<E> enumClass, String name) {
    return Enum.valueOf(enumClass, name);
}
```

Cách gọi:

```java
Level level = parse(Level.class, "HIGH");
```

`E extends Enum<E>` là **F-bound** thường gặp khi viết helper cho enum. `E` là type parameter **của method**, không phải type parameter được khai báo trên `enum Level`.

`Class<E>` được caller truyền vào giúp method biết enum class cụ thể ở runtime. Đây không phải phép thay thế cho một generic enum declaration; nó là cách xây generic API **xử lý enum**.

### 5.2. `EnumSet<E>` và `EnumMap<K,V>` không phải generic enum

JDK có những class generic được thiết kế chuyên cho enum:

```java
java.util.EnumSet<Level> levels =
        java.util.EnumSet.of(Level.LOW, Level.HIGH);

java.util.EnumMap<Level, String> labels =
        new java.util.EnumMap<>(Level.class);

labels.put(Level.LOW, "Low");
```

`EnumSet` và `EnumMap` là **generic collection classes** sử dụng enum làm type argument/key. Điều đó hoàn toàn khác với khai báo `enum Level<T>`.

`EnumMap<K,V>` yêu cầu `K` là kiểu enum phù hợp; `EnumSet<E>` cũng yêu cầu type argument là enum type. Chúng giúp bạn gắn dữ liệu có kiểu vào enum constants mà không cần generic enum declaration.

## 6. Enum constant có class body riêng: được nhưng không tạo generic enum

Enum có thể khai báo hành vi khác nhau theo từng constant:

```java
enum Action {
    START {
        @Override
        public String execute() {
            return "starting";
        }
    },
    STOP {
        @Override
        public String execute() {
            return "stopping";
        }
    };

    public abstract String execute();
}
```

Cả hai constant vẫn thuộc enum `Action`. Constant-specific class body cho phép override các instance methods, nhưng **không khiến từng constant có một tham số kiểu riêng**.

Ví dụ trên không liên quan tới việc `Action` generic. Đây là hình thức đa hình đặc trưng của enum, khác với việc tạo nhiều subtype generic độc lập.

Nếu cố viết:

```java
// Cố tình sai:
// enum Action { <T> START; }
```

compiler từ chối do cú pháp enum constant không cho khai báo `<T>` như vậy.

## 7. `@interface` không được khai báo type parameters

Annotation interface được khai báo theo cú pháp:

```java
@interface Label {
    String value();
}
```

Không thể thêm `<T>` vào tên:

```java
// Cố tình sai:
@interface Label<T> {
    T value();
}
```

Trên Java 17, compiler báo:

```text
error: annotation interface Label cannot be generic
```

Và còn có thể báo thêm rằng `T` không phải kiểu hợp lệ cho một annotation element.

Lý do không nên hiểu đơn giản là “annotation không chứa object”: Java annotations là **metadata có cấu trúc và bộ kiểu giá trị được ngôn ngữ quy định**, phục vụ compiler, class file và Reflection. Java áp đặt một mô hình khai báo cố định, không phải một interface generic tùy ý.

### 7.1. Annotation interface là một dạng khai báo đặc biệt

Dù tên gọi có chứa từ “interface”, annotation interface **không tương đương một interface thông thường với toàn bộ cú pháp tự do của interface**. Nó có các element declarations với những ràng buộc riêng về:

- Kiểu kết quả.
- Việc không nhận tham số.
- Việc không khai báo type parameter cho element method.
- Default value.
- Khả năng biểu diễn annotation value trong class file và sử dụng ở vị trí annotation.

Bạn không thể khắc phục việc `@interface X<T>` không hợp lệ bằng cách gán một bound `<T extends SomeClass>`. Giới hạn nằm ở **dạng khai báo annotation**.

## 8. Annotation element có thể có kiểu nào?

Một annotation element như:

```java
@interface Label {
    String value();
}
```

trông giống method signature, nhưng kiểu trả về của nó bị giới hạn. Các nhóm hợp lệ về bản chất gồm:

1. Primitive (`int`, `boolean`, `long`, v.v.).
2. `String`.
3. `Class` hoặc một **parameterized invocation của `Class`** thích hợp như `Class<?>`, `Class<? extends Number>`.
4. Một enum type.
5. Một annotation type khác.
6. **Mảng một chiều** của một trong các loại trên.

Ví dụ:

```java
@interface Rule {
    int priority() default 1;
    String name() default "default";
    Class<?> type();
    Class<? extends Number> numberType() default Integer.class;
    String[] tags() default {};
}
```

Việc `Class<? extends Number>` xuất hiện **không làm `Rule` trở thành annotation generic**. Nó chỉ là **kiểu kết quả hợp lệ của một annotation element**.

### 8.1. Bảng phân biệt kiểu element

| Kiểu trả về element | Được phép? | Lưu ý |
| --- | --- | --- |
| `int`, `boolean`, `double` | Có | Primitive. |
| `String` | Có | Một chuỗi; khi dùng annotation phải có giá trị hợp lệ theo ngữ cảnh. |
| `Class<?>` | Có | Có thể lưu class literal phù hợp. |
| `Class<? extends Number>` | Có | Class literal phải đáp ứng bound. |
| `Class<String>` | Có | Một dạng parameterized invocation của `Class`. |
| `SomeEnum` | Có | Một enum type. |
| `NestedAnnotation` | Có | Một annotation type. |
| `String[]`, `Class<?>[]`, `SomeEnum[]` | Có | Chỉ một chiều của loại element hợp lệ. |
| `Object` | Không | Kiểu trả về không thuộc nhóm được phép. |
| `List<String>` | Không | Không được dùng collection generic tùy ý làm annotation element. |
| `Map<String, Object>` | Không | Không thuộc nhóm element type hợp lệ. |
| `T` trong một annotation generic tưởng tượng | Không | Annotation không có type parameter `T`. |
| `String[][]` | Không | Mảng nhiều chiều không phải annotation element type hợp lệ. |

**Điểm tinh tế:** `Class<?>` có generic wildcard và hoàn toàn hợp lệ làm annotation element; nhưng điều đó không đồng nghĩa mọi parameterized type như `List<String>` đều được phép.

### 8.2. `Class<? extends Number>` lưu loại class, không lưu instance

Ví dụ annotation:

```java
@interface NumberRule {
    Class<? extends Number> numberType()
            default Integer.class;
}
```

Cách dùng:

```java
@NumberRule(numberType = Double.class)
class Report {
}
```

`Double.class` là class literal có quan hệ kiểu phù hợp. Annotation không giữ một `Double` instance hay một `List<Double>` runtime được validate. Nó lưu **metadata là một class**.

Trường hợp như `Class<? extends Number>` hữu ích để chỉ định loại implementation, validator hay adapter trong frameworks, nhưng khi framework muốn khởi tạo class đó, nó vẫn phải xử lý constructor/dependency/access rules. Class literal **không bảo đảm** có constructor không tham số.

### 8.3. Giá trị annotation phải thuộc các dạng cho phép

Khi sử dụng annotation, giá trị element thường là literal/constant expression của kiểu phù hợp, class literal, enum constant, annotation hoặc array initializer gồm các dạng hợp lệ. Không thể truyền một arbitrary object vừa được tạo:

```java
// Sai: annotation không nhận List<String> làm element.
// @Tag(values = new ArrayList<String>())
```

Ngay cả khi một kiểu cụ thể có constructor public, điều đó không cho phép dùng `new SomeObject()` làm annotation value tùy ý.

### 8.4. `value()` có cú pháp rút gọn

```java
@interface Title {
    String value();
}

@Title("Generics")
class Guide {
}
```

Element tên `value` có cú pháp gán ngắn gọn. Đây là tiện ích của annotation syntax, **không phải** một generic method, và không thêm type parameter vào annotation.

## 9. Method generic trong annotation interface cũng không hợp lệ

Một annotation element không thể khai báo type parameter:

```java
// Cố tình sai:
@interface InvalidElement {
    <T> T value();
}
```

Trên Java 17, compiler báo:

```text
error: elements in annotation interface declarations cannot be generic methods
```

Thêm `<T>` trước kiểu trả về không thể biến annotation element thành factory hoặc generic accessor tùy ý. Đây là khác biệt lớn giữa generic method được phép trong enum và element declarations của annotation.

Bảng ghi nhớ:

| Vị trí khai báo | Có thể đặt `<T>` không? |
| --- | --- |
| `class Holder<T>` | Có |
| `interface Codec<T>` | Có |
| `enum State<T>` | **Không** |
| `@interface Label<T>` | **Không** |
| `<T> T echo(T x)` bên trong enum | Có |
| `<T> T value()` là annotation element | **Không** |

Một annotation type có thể chứa constants/nested declarations theo luật ngôn ngữ, nhưng các method element không có độ tự do tương đương những generic methods thông thường.

## 10. Annotation dùng enum làm element: hợp lệ và type-safe

Enum thường là lựa chọn tốt để định nghĩa các giá trị metadata cố định:

```java
enum Severity {
    INFO, WARNING, ERROR
}

@interface Audit {
    Severity severity() default Severity.INFO;
    String message() default "";
}
```

Cách dùng:

```java
@Audit(severity = Severity.ERROR, message = "failed")
class Service {
}
```

Compiler kiểm tra `Severity.ERROR` có đúng enum type của element hay không. Nếu truyền enum constant từ enum **khác**, compiler từ chối do sai kiểu.

Annotation `Audit` vẫn **không generic**. Nó chỉ có một element kiểu `Severity`, là enum type hợp lệ.

### 10.1. Có thể dùng enum trong mảng annotation element

```java
@interface Allowed {
    Severity[] levels() default {
        Severity.INFO, Severity.WARNING
    };
}
```

Mảng một chiều của enum là loại element hợp lệ. Khi đọc annotation bằng Reflection, caller nhận array tương ứng. Với annotation runtime, API thường trả bản sao khi cần bảo vệ tính bất biến của annotation instance; không nên xem array nhận được là API sửa annotation metadata.

## 11. Annotation lồng annotation: thay cho object tùy ý

Nếu cần mô tả metadata phức tạp hơn một chuỗi hoặc enum đơn lẻ:

```java
@interface Range {
    int min();
    int max();
}

@interface Check {
    Range range();
    String label() default "";
}
```

Áp dụng:

```java
@Check(range = @Range(min = 1, max = 10), label = "age")
class ValidatedField {
}
```

Ví dụ minh họa này dùng annotation type `Range` làm kết quả của element `range`. Cách thiết kế này tận dụng đúng bộ kiểu được annotation hỗ trợ, thay vì cố khai báo `List<Range>` hoặc một object dữ liệu tự do làm element.

Nếu metadata thực sự cần một tập hợp nhiều bản ghi phức tạp, bạn có thể dùng **mảng một chiều của annotation**:

```java
@interface RuleItem {
    String name();
    int weight();
}

@interface RuleSet {
    RuleItem[] value();
}
```

Khi thiết kế schema annotation, hãy ưu tiên sự rõ ràng và tương thích với compiler/class-file rules. Không biến một annotation thành cơ chế serialization đầy đủ cho cấu trúc đối tượng không bị giới hạn.

## 12. Ví dụ Java 17 hoàn chỉnh: enum + generic interface + annotation

Tạo file **`EnumAnnotationGenericsDemo.java`**:

```java
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;

public class EnumAnnotationGenericsDemo {
    interface Codec<T> {
        T decode(String text);
    }

    enum Level implements Codec<Integer> {
        LOW(1), HIGH(3);

        private final int weight;

        Level(int weight) {
            this.weight = weight;
        }

        @Override
        public Integer decode(String text) {
            return Integer.valueOf(text) * weight;
        }

        public <T> T echo(T value) {
            return value;
        }
    }

    enum Action {
        START {
            @Override
            public String execute() {
                return "starting";
            }
        },
        STOP {
            @Override
            public String execute() {
                return "stopping";
            }
        };

        public abstract String execute();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    @interface Rule {
        Class<?> type();
        Class<? extends Number> numberType()
                default Integer.class;
        Level level() default Level.LOW;
        String[] tags() default {};
    }

    @Rule(
            type = String.class,
            numberType = Double.class,
            level = Level.HIGH,
            tags = {"core", "generics"}
    )
    static class Example {
    }

    static <E extends Enum<E>> E parse(
            Class<E> kind, String name) {
        return Enum.valueOf(kind, name);
    }

    public static void main(String[] args) {
        System.out.println("enum=" + Level.HIGH.name());
        System.out.println("interface=" + Level.HIGH.decode("7"));
        System.out.println("method=" + Level.LOW.echo("ok"));
        System.out.println("action=" + Action.START.execute());
        System.out.println("parse=" + parse(Level.class, "LOW"));

        Rule rule = Example.class.getAnnotation(Rule.class);
        System.out.println("annotation="
                + rule.type().getSimpleName() + ":"
                + rule.numberType().getSimpleName() + ":"
                + rule.level());
        System.out.println("tags=" + List.of(rule.tags()));
    }
}
```

Chương trình sử dụng Java 17 hoặc JDK mới hơn có hỗ trợ `--release 17`. Chạy:

```bash
javac --release 17 -Xlint:all EnumAnnotationGenericsDemo.java
java EnumAnnotationGenericsDemo
```

Đã kiểm chứng: biên dịch thành công **không có warning**, stdout:

```text
enum=HIGH
interface=21
method=ok
action=starting
parse=LOW
annotation=String:Double:HIGH
tags=[core, generics]
```

### 12.1. Phân tích `enum=HIGH` và `interface=21`

`Level.HIGH` là một enum constant thuộc enum `Level` không generic. `Level` triển khai `Codec<Integer>`:

```java
enum Level implements Codec<Integer> { ... }
```

`decode("7")` trả `Integer` có giá trị `7 * 3 = 21`. Compiler biết `Codec<Integer>` yêu cầu kiểu kết quả phù hợp với `Integer`. Không cần `enum Level<T>`.

### 12.2. Phân tích `method=ok`

Method:

```java
public <T> T echo(T value) {
    return value;
}
```

được khai báo bên trong enum. `T` chỉ thuộc phạm vi method, nên compiler có thể suy luận `T = String` khi gọi `echo("ok")`.

Điều này chứng minh **enum không generic** vẫn có thể có **generic method**.

### 12.3. Phân tích `action=starting`

Enum `Action` dùng constant-specific class body để override `execute()`. Không có generic parameter ở cấp enum. Đây là đa hình theo enum constant, không phải chuyên biệt hóa enum theo `T`.

### 12.4. Phân tích `parse=LOW`

Generic helper:

```java
static <E extends Enum<E>> E parse(
        Class<E> kind, String name) {
    return Enum.valueOf(kind, name);
}
```

dùng enum-bound để nhận một enum type cụ thể; `Level.class` là class token mà caller cung cấp. Mọi thông tin cần thiết được truyền vào helper rõ ràng, không yêu cầu enum declaration có type parameter.

Nếu tên không tồn tại, `Enum.valueOf` ném `IllegalArgumentException`; không tự trả null. Hãy xử lý chính sách báo lỗi theo ứng dụng.

### 12.5. Phân tích `annotation=String:Double:HIGH`

`@Rule` chứa các element:

- `Class<?> type()`: lưu `String.class`.
- `Class<? extends Number> numberType()`: lưu `Double.class`.
- `Level level()`: lưu `Level.HIGH`.
- `String[] tags()`: lưu mảng chuỗi.

Các element này **hợp lệ** dù annotation interface không generic. `@Retention(RUNTIME)` cho phép Reflection đọc annotation từ class `Example`. Nếu bỏ retention runtime, `getAnnotation` có thể không thấy annotation trong chương trình chạy mặc dù annotation hợp lệ ở source.

### 12.6. `tags=[core, generics]` đến từ đâu?

`rule.tags()` trả một array `String[]`. `List.of(rule.tags())` tạo một danh sách chứa các phần tử chuỗi đó theo overload varargs phù hợp. Output đã được kiểm chứng bằng chương trình thực.

Không có `List<String>` nào được sử dụng làm **kiểu trả về của annotation element**: đó là `String[]`, một kiểu được phép. Việc chuyển mảng sang List diễn ra **sau khi đọc annotation**, trong Java code thông thường.

## 13. Ví dụ cố ý không biên dịch: enum generic

Tệp độc lập **`GenericEnum.java`**:

```java
enum GenericEnum<T> {
    A;
}
```

Biên dịch:

```bash
javac --release 17 GenericEnum.java
```

Compiler trả:

```text
error: enums cannot be generic
```

Đừng “sửa” bằng cách đổi thành:

```java
// Vẫn sai:
// enum GenericEnum<T extends Number> { A; }
```

Bound không thay đổi quy tắc: enum declaration không khai báo type parameters.

Nếu ứng dụng muốn một đối tượng chứa giá trị generic đồng thời có một nhãn enum cố định, hãy tách nhãn và payload:

```java
enum Kind {
    TEXT, NUMBER
}

record TaggedValue<T>(Kind kind, T value) {
}
```

Ví dụ:

```java
TaggedValue<String> text =
        new TaggedValue<>(Kind.TEXT, "hello");
```

`TaggedValue<T>` là generic record, còn `Kind` vẫn là enum không generic. Lưu ý mô hình này **chưa tự bảo đảm** `Kind.TEXT` luôn đi với `String`, vì caller có thể tạo `new TaggedValue<Integer>(Kind.TEXT, 7)`. Nếu cần invariant gắn loại nhãn với kiểu payload, thiết kế validator hoặc các subtype/constructors cụ thể để compiler hay runtime bảo vệ quy tắc đó.

## 14. Ví dụ cố ý không biên dịch: annotation generic

Tệp **`GenericAnnotation.java`**:

```java
@interface GenericAnnotation<T> {
    T value();
}
```

Lệnh:

```bash
javac --release 17 GenericAnnotation.java
```

`javac` báo lỗi chính:

```text
error: annotation interface GenericAnnotation cannot be generic
```

Và lỗi tại kiểu element:

```text
error: invalid type for annotation interface element
```

Việc đổi return thành `String` vẫn không làm `<T>` trên `@interface` hợp lệ. Cần bỏ type parameter của annotation declaration:

```java
@interface ValidAnnotation {
    String value();
}
```

Nếu muốn metadata mô tả class được chọn, dùng element `Class<?>` hoặc bound thích hợp thay vì generic type parameter ở cấp annotation.

## 15. Ví dụ cố ý không biên dịch: element `List<String>`

Tệp **`ListElement.java`**:

```java
import java.util.List;

@interface ListElement {
    List<String> value();
}
```

Lệnh:

```bash
javac --release 17 ListElement.java
```

Compiler trả:

```text
error: invalid type for annotation interface element
```

`List<String>` là một kiểu Java hoàn toàn hợp lệ khi dùng cho biến, field hoặc return type của method thông thường. Nhưng nó **không nằm trong tập kiểu return được phép của annotation element**.

Nếu cần nhiều chuỗi, dùng mảng:

```java
@interface Tags {
    String[] value();
}

@Tags({"java", "generics"})
class Article {
}
```

Nếu cần nhiều metadata phức tạp, dùng mảng annotation lồng nhau. Nếu cần dữ liệu runtime động với collection semantics, dùng một config object hoặc registry riêng — annotation không phải nơi lưu đối tượng tùy ý.

## 16. Ví dụ cố ý không biên dịch: method generic trong annotation

Tệp **`GenericAnnotationMethod.java`**:

```java
@interface GenericAnnotationMethod {
    <T> T value();
}
```

Biên dịch:

```bash
javac --release 17 GenericAnnotationMethod.java
```

Một diagnostic quan trọng:

```text
error: elements in annotation interface declarations
cannot be generic methods
```

Compiler còn có thể cảnh báo kiểu kết quả `T` không hợp lệ cho annotation element. Điều cần sửa là **thiết kế kiểu element**, không phải thêm bound.

Nếu element chỉ cần biểu diễn một class:

```java
@interface ClassHint {
    Class<?> value();
}
```

Nếu nghiệp vụ cần một generic method xử lý dữ liệu, viết method đó trong class/interface/service bình thường, có thể đọc metadata annotation làm input.

## 17. Ví dụ cố ý không biên dịch: mảng nhiều chiều hoặc `Object` element

Annotation element không chấp nhận một kiểu return tùy ý chỉ vì compiler hiểu kiểu đó ở Java thông thường.

Tệp **`InvalidArrayElement.java`**:

```java
@interface InvalidArrayElement {
    String[][] value();
}
```

Compiler trả:

```text
error: invalid type for annotation interface element
```

Tương tự:

```java
@interface InvalidObjectElement {
    Object value();
}
```

cũng sai. Phải chọn primitive, `String`, `Class`/parameterized invocation của `Class`, enum, annotation hoặc **mảng một chiều** của những loại đó.

### 17.1. Vì sao `Class<?>[]` lại hợp lệ?

Ví dụ:

```java
@interface Types {
    Class<?>[] value();
}
```

Đây là **mảng một chiều** của một `Class<?>` element hợp lệ. Không mâu thuẫn với việc `String[][]` bị cấm: `String[][]` là hai chiều.

Một trường hợp khác đã được biên dịch thành công với Java 17:

```java
@interface Pick {
    Class<String> exact() default String.class;

    Class<? extends Number>[] types()
            default {Integer.class, Double.class};
}
```

`Class<String>` và `Class<? extends Number>[]` đều hợp lệ; các class literals ở default value phải có quan hệ kiểu phù hợp.

## 18. Annotation có thể xuất hiện trên generic declarations và type arguments

Một annotation **không cần generic** để đánh dấu chỗ khai báo type parameter hoặc type argument. Ví dụ:

```java
import java.lang.annotation.ElementType;
import java.lang.annotation.Target;
import java.util.List;

@Target({
    ElementType.TYPE_USE,
    ElementType.TYPE_PARAMETER
})
@interface TypeMarker {
}

class AnnotatedGeneric<@TypeMarker T> {
    List<@TypeMarker String> names;
    @TypeMarker T value;
}
```

Đoạn này đã được biên dịch bằng `javac --release 17 -Xlint:all` mà không có lỗi hoặc warning.

Điểm cần phân biệt:

- `TypeMarker` là annotation interface không khai báo `<T>`.
- `AnnotatedGeneric<T>` là một **generic class bình thường**.
- Annotation được **gắn lên type parameter hoặc type use** vì `@Target` cho phép.
- `List<@TypeMarker String>` có một **type annotation** trên chỗ dùng kiểu `String`; nó không làm `TypeMarker` trở thành generic.

### 18.1. `TYPE_PARAMETER` và `TYPE_USE` khác nhau

`ElementType.TYPE_PARAMETER` dành cho chỗ **khai báo** một type parameter, ví dụ `<@Marker T>`.

`ElementType.TYPE_USE` dành cho các vị trí dùng type trong ngữ cảnh ngôn ngữ cho phép, ví dụ `List<@Marker String>` hoặc một chỗ khai báo có kiểu phù hợp.

Một annotation chỉ có `@Target(TYPE)` có thể dùng cho class/interface declaration, nhưng không mặc nhiên dùng ở mọi type argument. Nếu mục tiêu là static analysis hoặc nullness/type checking trên Generics, hãy thiết kế `@Target` chính xác.

### 18.2. Không nhầm type-use annotation với annotation có tham số kiểu

`@TypeMarker` được gắn **trên** `String` trong `List<@TypeMarker String>`, nhưng bản thân `@TypeMarker` vẫn là metadata annotation với các element theo luật riêng. Bạn không thể viết `@TypeMarker<String>` để “tham số hóa annotation”.

Nhiều công cụ như annotation processors hoặc type checkers có thể sử dụng type-use annotations để thực thi quy tắc tĩnh. Điều đó **không** làm Java runtime tự kiểm tra generic type arguments vốn bị erase.

## 19. Cách tổ chức mô hình khi cần “enum mang kiểu dữ liệu”

Một bài toán phổ biến là định nghĩa danh sách các loại dữ liệu:

```java
enum DataKind {
    TEXT, NUMBER
}
```

và muốn mỗi loại “mang” một kiểu kết quả khác nhau. Không thể giải quyết bằng:

```java
// Không hợp lệ:
// enum DataKind<T> { TEXT, NUMBER }
```

Một số thiết kế phù hợp hơn:

### 19.1. Enum làm nhãn, generic record làm payload

```java
enum DataKind {
    TEXT, NUMBER
}

record Value<T>(DataKind kind, T payload) {
}
```

Ưu điểm là giữ được một enum hữu hạn để hiển thị/lưu nhãn, trong khi `Value<T>` có generic contract cho payload.

Giới hạn: quan hệ giữa `DataKind.TEXT` và `T=String` cần invariant bổ sung nếu ứng dụng đòi nó. Không coi `Value<Integer>(TEXT, 123)` mặc nhiên là lỗi compiler.

### 19.2. Interface generic với implementations cụ thể

```java
interface TypedKey<T> {
    T read(String source);
}

final class StringKey implements TypedKey<String> {
    @Override
    public String read(String source) {
        return source;
    }
}

final class IntKey implements TypedKey<Integer> {
    @Override
    public Integer read(String source) {
        return Integer.valueOf(source);
    }
}
```

Nếu mỗi loại thực sự có generic behavior riêng, các implementation như vậy cho phép compiler gắn kiểu kết quả với instance type. Bạn có thể tổ chức registry của chúng trong collection hoặc enum labels khi cần.

### 19.3. Dùng enum triển khai một generic interface cố định

Nếu mọi constant trả cùng một kiểu và chỉ khác logic:

```java
interface Rule<T> {
    T apply(String input);
}

enum TextRule implements Rule<String> {
    UPPER {
        @Override
        public String apply(String input) {
            return input.toUpperCase();
        }
    },
    LOWER {
        @Override
        public String apply(String input) {
            return input.toLowerCase();
        }
    };
}
```

Đây là cách dùng enum đúng mục đích: tập constant cố định, cùng một kiểu giao diện `Rule<String>`, có hành vi đa hình. Nó **không** làm enum generic.

## 20. Cách tổ chức metadata khi cần “annotation mang một kiểu”

Một bài toán khác là muốn đánh dấu class được xử lý cùng kiểu dữ liệu:

```java
@interface Handles {
    Class<?> type();
}
```

Cách dùng:

```java
@Handles(type = String.class)
class StringHandler {
}
```

Annotation này chứa một class token, không phải một `T` được suy luận cho handler.

Nếu cần giới hạn lựa chọn:

```java
@interface HandlesNumber {
    Class<? extends Number> type();
}
```

Compiler có thể kiểm tra class literal theo bound. Tuy nhiên annotation không kiểm tra rằng implementation class `StringHandler` thật sự có method handle nhận đúng kiểu tương ứng.

Để gắn chặt metadata với behavior, có thể kết hợp:

- Generic interface `Handler<T>` cho compile-time contract.
- Annotation `@Handles(type = ...)` cho đăng ký/phát hiện.
- Validation tại startup hoặc annotation processor để kiểm tra các mối quan hệ mà Java type system không tự gắn giữa annotation element và generic argument.

Không nên hứa rằng gắn annotation sẽ tự động reify toàn bộ generic type của class instance.

## 21. Đọc Annotation bằng Reflection đúng cách

Một annotation có `@Retention(RetentionPolicy.RUNTIME)` mới được Reflection truy xuất tại runtime theo cơ chế annotation runtime thông thường.

```java
@Retention(RetentionPolicy.RUNTIME)
@interface Label {
    String value();
}
```

```java
@Label("hello")
class Example {
}
```

Một method sử dụng Reflection:

```java
Label label = Example.class.getAnnotation(Label.class);
```

Nếu annotation có mặt và retention cho phép, `label.value()` trả `"hello"`.

Với ví dụ hoàn chỉnh ở mục 12, `Rule` có `@Retention(RUNTIME)`, vì thế:

```java
Rule rule = Example.class.getAnnotation(Rule.class);
```

đọc được các element `type`, `numberType`, `level`, `tags`.

### 21.1. Retention là vấn đề khác Generics

`SOURCE`, `CLASS`, `RUNTIME` quy định annotation được lưu/đọc ở mức nào; chúng không quyết định annotation được phép generic hay không. Kể cả annotation retention runtime, khai báo `@interface Info<T>` vẫn không hợp lệ.

### 21.2. Annotation metadata không phải đối tượng config tự do

Các method element của annotation trả những loại giá trị được phép theo JLS, không phải arbitrary object graph. Nếu bạn cần danh sách runtime thay đổi liên tục hoặc dữ liệu nested phức tạp, nên có config class/record và parser/validator riêng. Annotation phù hợp để mô tả metadata hữu hạn, đọc được bằng compiler hoặc Reflection.

## 22. Checklist phân tích lỗi biên dịch

Khi gặp compiler error liên quan enum, annotation và Generics:

1. **Tìm chính xác vị trí `<T>`**: nằm ngay sau tên `enum`/`@interface`, hay ở method/class/interface khác?
2. Nếu là `enum Name<T>`, đây là **lỗi cấu trúc khai báo**; đổi sang enum không generic và triển khai generic interface/method hoặc tách generic holder.
3. Nếu là `@interface Name<T>`, bỏ type parameters khỏi annotation declaration; xác định metadata thực sự cần trả dưới dạng element được phép.
4. Nếu annotation element là `<T> T value()` hoặc `List<String> value()`, đó là **kiểu/method element không hợp lệ**, không phải vấn đề erasure có thể sửa bằng cast.
5. Với annotation muốn mô tả class, cân nhắc `Class<?>` hoặc `Class<? extends Base>`; nếu cần nhiều class, dùng mảng một chiều.
6. Nếu annotation muốn chứa dữ liệu phức tạp, thử enum, annotation lồng nhau hoặc một-dimensional array của kiểu được hỗ trợ; nếu không phù hợp, chuyển logic sang config object.
7. Với enum implement generic interface, xác nhận type argument và implementation method thực sự khớp hợp đồng.
8. Với generic helper làm việc trên enum, ưu tiên `<E extends Enum<E>>` kết hợp `Class<E>` theo API sử dụng.
9. Nếu dùng Reflection để đọc annotation, xác nhận `@Retention(RUNTIME)` và vị trí target thích hợp.
10. Biên dịch lại với `javac --release 17 -Xlint:all` và viết test cả khai báo hợp lệ lẫn những trường hợp cần bị từ chối.

### 22.1. Bảng nguyên nhân và cách khắc phục

| Thông báo | Nguyên nhân | Hướng xử lý |
| --- | --- | --- |
| `enums cannot be generic` | Đặt type parameter sau tên enum. | Bỏ `<T>`; dùng generic class/interface/method quanh enum. |
| `annotation interface ... cannot be generic` | Khai báo `@interface A<T>`. | Bỏ `<T>`; dùng element hợp lệ để mô tả metadata. |
| `elements in annotation interface declarations cannot be generic methods` | Element khai báo `<T>` riêng. | Dùng kiểu element cố định; chuyển xử lý generic sang class/service. |
| `invalid type for annotation interface element` | Return type là `Object`, `List<String>`, `String[][]`, v.v. | Dùng primitive, `String`, `Class`, enum, annotation hoặc array một chiều hợp lệ. |
| Enum `implements Codec<Integer>` báo thiếu method | Chưa override đúng chữ ký generic của interface. | Viết implementation đúng kiểu sau khi thay `T = Integer`. |
| Reflection trả `null` khi đọc annotation | Annotation không hiện diện hoặc retention không cho truy xuất runtime. | Kiểm tra khai báo, `@Retention`, `@Target` và nơi gắn. |
| Muốn mỗi enum constant có type argument khác nhau | Enum declaration không generic, constants dùng chung enum type. | Chuyển sang typed hierarchy/descriptors hoặc chia các enum với hợp đồng cố định. |

## 23. Các ngộ nhận thường gặp

**Ngộ nhận 1 — “Enum không generic nên không được dùng `List<String>` trong enum.”** Sai. Enum không khai báo type parameters, nhưng field, parameter và return type bên trong nó vẫn có thể là parameterized types.

**Ngộ nhận 2 — “`Enum<E>` trong JDK chứng minh có thể khai báo `enum X<T>`.”** Sai. `Enum<E>` là generic superclass đặc biệt của enum, không phải cú pháp generic enum declaration.

**Ngộ nhận 3 — “Enum không generic nên không được có `<T> T method(T)`.”** Sai. Method có type parameter riêng và được phép trong enum.

**Ngộ nhận 4 — “Enum triển khai `Codec<Integer>` thì enum trở thành `Enum<Integer>`.”** Sai. Parameterized type ở đây là **interface** được triển khai, không phải type parameter của enum.

**Ngộ nhận 5 — “Annotation không generic nên element không thể dùng `Class<?>`.”** Sai. `Class<?>`, `Class<? extends Number>` và nhiều dạng invocation hợp lệ của `Class` được annotation hỗ trợ.

**Ngộ nhận 6 — “Một element `List<String>` cũng hợp lệ vì `List<String>` là Java type hợp lệ.”** Sai. Annotation element types bị giới hạn chặt hơn return type của method thông thường.

**Ngộ nhận 7 — “`String[][]` chỉ là array nên làm annotation element được.”** Sai. Annotation chỉ hỗ trợ một-dimensional array của kiểu element hợp lệ.

**Ngộ nhận 8 — “Annotation có thể khai báo method generic vì nó là interface.”** Sai. Các element declarations của annotation interface không được generic methods.

**Ngộ nhận 9 — “`@TypeMarker` gắn vào `List<@TypeMarker String>` nghĩa là `TypeMarker` có tham số kiểu.”** Sai. Annotation đang được **áp dụng tại type use**, không phải được khai báo generic.

**Ngộ nhận 10 — “Chỉ cần annotation có `Class<? extends Foo>` là framework tự xác minh mọi generic behavior.”** Sai. Annotation metadata chỉ mô tả một class token hoặc giá trị cố định; quan hệ giữa metadata và implementation cần được kiểm tra riêng.

**Ngộ nhận 11 — “`@Retention(RUNTIME)` sửa được giới hạn generic annotation.”** Sai. Retention chỉ quyết định visibility của annotation ở runtime, không cho thêm type parameters vào `@interface`.

**Ngộ nhận 12 — “Mọi constant của một enum có thể độc lập chọn type argument cho cùng enum generic.”** Sai. Enum không generic; các constants cùng thuộc một enum type, còn việc có class body riêng không tạo specialization theo `T`.

## 24. Bài thực hành

### Bài 1 — Phân loại khai báo

Xét:

```java
enum Mode<T> {
    A;
}

interface Provider<T> {
    T get();
}

enum DefaultMode implements Provider<String> {
    A;

    public String get() {
        return "ok";
    }
}
```

Dòng khai báo nào hợp lệ?

<details>
<summary>Đáp án</summary>

`enum Mode<T>` không hợp lệ vì enum không được khai báo type parameters. `Provider<T>` là generic interface hợp lệ. `DefaultMode implements Provider<String>` hợp lệ vì enum không generic nhưng được phép implement một parameterized interface và override method trả `String`.

</details>

### Bài 2 — Generic method trong enum

Có thể thêm method này trong một enum không generic không?

```java
<T> T identity(T value) {
    return value;
}
```

<details>
<summary>Đáp án</summary>

Có. `<T>` thuộc khai báo generic method, không thuộc khai báo enum. Java cho phép enum có generic methods theo những quy tắc method thông thường.

</details>

### Bài 3 — Kiểu trả về annotation element

Những kiểu nào hợp lệ: `String`, `Object`, `List<String>`, `Class<?>`, `Class<? extends Number>`, `String[]`, `String[][]`?

<details>
<summary>Đáp án</summary>

Hợp lệ: `String`, `Class<?>`, `Class<? extends Number>`, `String[]`. Không hợp lệ: `Object`, `List<String>`, `String[][]`. Annotation element chỉ chấp nhận những nhóm kiểu được ngôn ngữ quy định; mảng chỉ một chiều.

</details>

### Bài 4 — Annotation có method generic?

```java
@interface Pick {
    <T> T get();
}
```

Vì sao bị compiler từ chối?

<details>
<summary>Đáp án</summary>

Annotation element declarations không được generic methods. Việc annotation là một dạng interface không khiến method element có cùng quyền tự do khai báo type parameters như method của class/interface thông thường. Cần chọn một element type hợp lệ hoặc chuyển logic generic sang method ở một class khác.

</details>

### Bài 5 — Vì sao có thể dùng `EnumSet<Level>`?

<details>
<summary>Đáp án</summary>

`EnumSet` là một **generic collection class** của Java. Type argument `Level` là một enum type cụ thể, nhưng chính enum `Level` không khai báo `<T>`. Không có mâu thuẫn với quy tắc enum không generic.

</details>

### Bài 6 — Gắn annotation lên type argument có làm annotation generic không?

```java
List<@TypeMarker String> names;
```

<details>
<summary>Đáp án</summary>

Không. `TypeMarker` được sử dụng tại vị trí TYPE_USE nếu khai báo `@Target` cho phép. Nó không tự có type parameter. Generic type ở đây là `List<String>`; annotation chỉ mô tả metadata trên chỗ dùng type `String`.

</details>

### Bài 7 — Lựa chọn thiết kế cho enum trả dữ liệu nhiều kiểu

Ứng dụng có nhãn `TEXT`, `NUMBER` nhưng mỗi nhãn cần một loại payload riêng. Có nên viết `enum Item<T>` không?

<details>
<summary>Đáp án</summary>

Không thể viết generic enum declaration đó trong Java. Có thể dùng enum làm nhãn kết hợp generic record/class cho payload; nếu cần compiler bắt buộc nhãn nào đi với loại payload nào, cân nhắc typed descriptor hoặc các implementations cụ thể qua generic interface, bổ sung invariant phù hợp.

</details>

### Bài 8 — Annotation có thể lưu danh sách chuỗi?

Nếu muốn metadata chứa ba nhãn `"a"`, `"b"`, `"c"`, hãy định nghĩa element như thế nào?

<details>
<summary>Đáp án</summary>

Dùng `String[] value()`:

```java
@interface Tags {
    String[] value();
}

@Tags({"a", "b", "c"})
class Tagged {
}
```

Không dùng `List<String> value()`, vì `List<String>` không phải annotation element type hợp lệ.

</details>

## 25. Ghi nhớ trọng tâm

**Java cấm khai báo type parameters trực tiếp trên `enum` và `@interface`**, nhưng phạm vi cấm khác nhau:

- Enum không được viết `enum E<T>`, **vẫn có thể** implement `GenericInterface<Concrete>`, có generic methods, dùng generic fields và kết hợp với generic collections như `EnumSet<E>`, `EnumMap<E,V>`.
- Annotation không được viết `@interface A<T>`, và các **annotation element methods cũng không được generic**. Kiểu trả về element chỉ nằm trong tập mà Java quy định: primitive, `String`, `Class` (kể cả invocation hợp lệ của `Class`), enum, annotation và mảng một chiều của chúng.
- Annotation vẫn có thể được **gắn tại generic type declarations/usages** thông qua `TYPE_PARAMETER`, `TYPE_USE` mà không trở thành generic annotation.

Khi gặp nhu cầu “enum có tham số kiểu”, hãy hỏi liệu generic contract nên thuộc **interface**, **method** hay **object chứa enum**. Khi gặp nhu cầu “annotation generic”, hãy hỏi liệu metadata có thể được biểu diễn bằng **Class token, enum, array hoặc nested annotation** hay cần chuyển sang một config object và generic service. Cách chia trách nhiệm này giữ được type safety mà không cố dùng những khai báo Java không hỗ trợ.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="generics-language-limits-007">Không cung cấp giá trị mặc định cho tham số kiểu</a>

<details>
<summary>Xem chi tiết</summary>

# Không cung cấp giá trị mặc định cho tham số kiểu trong Java Generics

## 1. Giới hạn ngôn ngữ cần nhớ

Trong Java, khi khai báo một generic class, interface, record hoặc generic method, ta có thể khai báo **tham số kiểu** (*type parameter*) và đặt **giới hạn** (*bound*) cho tham số đó. Nhưng Java **không có cú pháp khai báo giá trị mặc định cho type parameter**, chẳng hạn “nếu caller không viết đối số kiểu thì tự coi `T` là `String`”.

Các đoạn sau **cố ý không hợp lệ** trong Java 17:

```java
// CỐ Ý KHÔNG BIÊN DỊCH — Java không hỗ trợ default type argument.
class Box<T = String> { }

interface Repository<T = Object> { }

record Pair<K, V = String>(K key, V value) { }

class Utility {
    static <T = String> T make() {
        return null;
    }
}
```

`T = String`, `V = String` hay `<T = String>` không thuộc cú pháp hợp lệ của khai báo generic Java. Không thể sửa bằng cách thay `String` thành `Object`, dùng một wrapper khác hoặc thêm annotation.

Dạng hợp lệ là:

```java
class Box<T> { }

interface Repository<T> { }

record Pair<K, V>(K key, V value) { }

class Utility {
    static <T> T make() {
        return null;
    }
}
```

Nếu muốn giới hạn các kiểu được chấp nhận, Java cung cấp `extends`, ví dụ `<T extends Number>`. Nếu muốn bỏ bớt việc viết đối số kiểu ở **vị trí gọi**, Java cung cấp **type inference** và **diamond operator `<>`**. Nếu muốn một lựa chọn mặc định có **ngữ nghĩa nghiệp vụ** như “dùng `String`”, hãy tạo factory method hoặc subtype chuyên biệt.

> **Điểm phân biệt cốt lõi:** *Default type argument* là quy tắc gắn ngay trong khai báo tham số kiểu; *type inference* là quá trình compiler tìm kiểu thích hợp cho **mỗi biểu thức/lời gọi** dựa trên ngữ cảnh và các ràng buộc. Java hỗ trợ cơ chế thứ hai, không hỗ trợ cơ chế thứ nhất.

## 2. Thuật ngữ: type parameter, type argument, bound và inference

Xét:

```java
class Holder<T> {
    T value;
}
```

- `T` trong `class Holder<T>` là **type parameter** được khai báo.
- `String` trong `Holder<String>` là **type argument** được cung cấp khi sử dụng.
- Trong `Holder<T extends Number>`, `Number` là **upper bound**, ràng buộc những kiểu có thể dùng thay `T`.
- Trong `new Holder<>()`, `<>` yêu cầu compiler **suy luận type arguments** tại biểu thức tạo đối tượng.
- Trong `Holder<?>`, `?` là **wildcard**, biểu diễn type argument chưa xác định, không phải default `Object`.

Các cách sử dụng có ý nghĩa khác nhau:

| Cú pháp | Hợp lệ? | Ý nghĩa |
|---|---|---|
| `class Holder<T> {}` | Có | Khai báo một type parameter `T` |
| `class Holder<T = String> {}` | Không | Java không hỗ trợ default type argument |
| `class Holder<T extends Number> {}` | Có | Giới hạn `T` là subtype của `Number` |
| `Holder<String> h;` | Có | Dùng type argument tường minh `String` |
| `Holder<?> h;` | Có | Dùng wildcard cho đối số kiểu chưa biết |
| `Holder h;` | Có, không khuyến khích | Dùng **raw type** và bỏ kiểm tra generic tại nhiều chỗ |
| `Holder<> h;` | Không | Diamond không dùng trong tên kiểu ở khai báo biến |
| `Holder<String> h = new Holder<>();` | Có | Diamond suy luận từ target type |
| `var h = new Holder<>();` | Có nếu constructor phù hợp | `var` lấy kiểu từ initializer, không đặt default `String` |

Đừng đánh đồng việc compiler “đoán” được một kiểu với việc class đã khai báo kiểu mặc định.

## 3. Vì sao thiếu đối số kiểu không có nghĩa “dùng mặc định”?

Giả sử class:

```java
class Container<T> {
    T value;
}
```

Có ba cách nhìn:

```java
Container<String> typed = new Container<>();
Container raw = new Container();
var inferred = new Container<>();
```

Trong ví dụ này, constructor mặc định do Java tạo ra có thể được gọi không đối số vì class không khai báo constructor riêng.

- `typed` có kiểu tĩnh `Container<String>` nhờ khai báo bên trái.
- `raw` có kiểu thô `Container`; compiler không biết đối số `T`, và một số thao tác có thể tạo cảnh báo `rawtypes`/`unchecked`.
- `inferred` không có `String` làm ngữ cảnh đích và constructor cũng không nhận đối số. Với một `T` không bị ràng buộc như trên, compiler suy luận `Object`; biến có kiểu tĩnh `Container<Object>`.

Kết quả `Object` trong tình huống cuối là **kết quả của suy luận kiểu không có ràng buộc cụ thể hơn**, không phải default type argument do `Container<T>` khai báo. Nếu constructor có tham số hoặc biểu thức có target type khác, kết quả có thể khác.

Câu “Java tự mặc định mọi `T` thành `Object`” cũng **không đúng**. Type variables có thể có bounds; type inference dùng ngữ cảnh và quy tắc lựa chọn kiểu. `Object` chỉ là một trường hợp thường gặp với type parameter không bound và biểu thức thiếu ràng buộc cụ thể.

## 4. Ví dụ chạy được: diamond không đồng nghĩa default type

Tạo tệp **`NoDefaultTypeArgumentDemo.java`**:

```java
public class NoDefaultTypeArgumentDemo {

    static class Box<T> {
        private T value;

        Box() {
        }

        Box(T value) {
            this.value = value;
        }

        void set(T value) {
            this.value = value;
        }

        T get() {
            return value;
        }
    }

    public static void main(String[] args) {
        Box<String> targeted = new Box<>();
        targeted.set("Java");

        var inferred = new Box<>(42);
        Integer n = inferred.get();

        var noTarget = new Box<>();
        noTarget.set("first");
        noTarget.set(3);
        Object last = noTarget.get();

        System.out.println(targeted.get());
        System.out.println(n);
        System.out.println(last);
    }
}
```

Biên dịch/chạy trên JDK 17 trở lên:

```bash
javac NoDefaultTypeArgumentDemo.java
java NoDefaultTypeArgumentDemo
```

Stdout:

```text
Java
42
3
```

Phân tích:

1. `Box<String> targeted = new Box<>()` có ngữ cảnh đích `Box<String>`; constructor không nhận tham số vẫn suy luận được `T = String`.
2. `var inferred = new Box<>(42)` không có target type do `var`, nhưng **đối số constructor** `42` góp phần suy luận `T = Integer` (qua boxing).
3. `var noTarget = new Box<>()` không có ràng buộc cho type variable `T` không giới hạn, nên suy luận kiểu phần tử là `Object`.
4. `noTarget.set("first")` và `noTarget.set(3)` đều hợp lệ vì `String` và `Integer` đều là các reference types gán được cho `Object`.
5. Các lệnh `set(...)` **sau** khai báo không khiến kiểu `noTarget` “học” từ `String` rồi đổi sang `Integer`; kiểu được quyết định ngay tại initializer.

Để tự kiểm tra compiler, thêm thử dòng dưới vào cuối `main`:

```java
// CỐ Ý LỖI, không thêm vào phiên bản chạy được:
// targeted.set(10); // Compiler từ chối vì T của targeted là String.
```

Diamond chỉ **lược bỏ việc viết lại** type argument khi compiler có thể suy luận được; nó không thêm cú pháp mặc định vào class `Box`.

## 5. `var` không cung cấp type argument mặc định

`var` là cơ chế **suy luận kiểu của biến cục bộ** (Java 10+), không phải ký hiệu cho một generic type argument tùy chọn.

So sánh:

```java
java.util.List<String> a = new java.util.ArrayList<>();
var b = new java.util.ArrayList<String>();
var c = new java.util.ArrayList<>();
```

Các kiểu tĩnh sau suy luận:

| Biến | Kiểu tĩnh |
|---|---|
| `a` | `List<String>` |
| `b` | `ArrayList<String>` |
| `c` | `ArrayList<Object>` khi không có thông tin cụ thể hơn |

Điều khác biệt giữa `a` và `b` không chỉ là cú pháp ngắn/dài: `a` khai báo qua interface `List`, còn `b` suy ra class triển khai cụ thể `ArrayList`.

Một hiểu nhầm phổ biến là:

> “Tôi viết `var list = new ArrayList<>()`, sau đó `list.add("A")`, vậy compiler sẽ mặc định kiểu của list là String.”

Sai: `list` đã được xác định là `ArrayList<Object>` trong trường hợp constructor không đối số và không có target type. Việc `add("A")` không sửa kiểu tĩnh của biến.

Nếu yêu cầu là một danh sách chuỗi, nên ghi rõ ý định bằng một trong hai cách:

```java
java.util.List<String> list = new java.util.ArrayList<>();
var another = new java.util.ArrayList<String>();
```

Hai dạng giúp compiler từ chối việc thêm `Integer` vào một danh sách dự định chỉ chứa `String`.

## 6. Type argument suy luận từ constructor khác với default

Một constructor có thể cung cấp thông tin cho compiler qua **kiểu các đối số được truyền vào**.

Tạo tệp **`ConstructorInferenceDemo.java`**:

```java
public class ConstructorInferenceDemo {

    record Pair<L, R>(L left, R right) {
    }

    public static void main(String[] args) {
        Pair<String, Integer> a = new Pair<>("age", 21);

        var b = new Pair<>("score", 99);

        String label = b.left();
        Integer score = b.right();

        System.out.println(a.left() + "=" + a.right());
        System.out.println(label + "=" + score);
    }
}
```

Lệnh:

```bash
javac ConstructorInferenceDemo.java
java ConstructorInferenceDemo
```

Stdout:

```text
age=21
score=99
```

Ở `new Pair<>("score", 99)`, compiler thấy các constructor arguments là `String` và `int` (được boxing thành `Integer` trong quá trình suy luận), vì vậy có thể suy ra type arguments `L = String`, `R = Integer` ngay cả khi vế trái là `var`.

Ở `new Pair<>("age", 21)`, target type `Pair<String, Integer>` cũng đóng góp ngữ cảnh suy luận.

Đây là hai nguồn ràng buộc khác nhau nhưng đều **thuộc lời gọi cụ thể**. Chúng không làm record `Pair<L,R>` nhận được hai default type arguments trong khai báo.

## 7. Upper bound không phải giá trị mặc định

Java hỗ trợ:

```java
class NumericBox<T extends Number> {
    T value;
}
```

Dòng `T extends Number` có nghĩa **`T` phải là `Number` hoặc subtype hợp lệ của `Number`**, không có nghĩa “`T` mặc định là `Number` mỗi khi bỏ `<...>`”.

Các cách dùng hợp lệ:

```java
NumericBox<Integer> a = new NumericBox<>();
NumericBox<Double> b = new NumericBox<>();
NumericBox<Number> c = new NumericBox<>();
```

Còn:

```java
// CỐ Ý KHÔNG HỢP LỆ:
NumericBox<String> bad = new NumericBox<>();
```

bị compiler từ chối vì `String` không kế thừa `Number`.

### Bound ảnh hưởng kết quả suy luận khi thiếu ràng buộc

Với một class như `NumericBox<T extends Number>`, nếu viết `var x = new NumericBox<>()` và không có dữ liệu để ràng buộc `T` cụ thể, compiler có thể chọn `Number` làm kiểu thích hợp theo các bounds. Điều này là **quy tắc type inference dựa trên upper bound**, không phải generic default được khai báo bằng cú pháp kiểu `T = Number`.

### Ví dụ chạy được với bound

Tệp **`BoundIsNotDefaultDemo.java`**:

```java
public class BoundIsNotDefaultDemo {

    static class NumericBox<T extends Number> {
        private T value;

        void set(T value) {
            this.value = value;
        }

        T get() {
            return value;
        }
    }

    public static void main(String[] args) {
        NumericBox<Integer> integers = new NumericBox<>();
        integers.set(8);

        var inferred = new NumericBox<>();
        inferred.set(2.5);

        Number asNumber = inferred.get();

        System.out.println(integers.get());
        System.out.println(asNumber);
    }
}
```

Chạy:

```bash
javac BoundIsNotDefaultDemo.java
java BoundIsNotDefaultDemo
```

Stdout:

```text
8
2.5
```

Bất kể ví dụ dùng `<Integer>` tường minh hay suy luận từ `<>`, compiler vẫn kiểm tra giới hạn `T extends Number`. Upper bound làm **hẹp tập kiểu được phép**, còn default type argument (không tồn tại trong Java) sẽ là quy tắc **chọn một kiểu khi thiếu thông tin**.

Nếu có nhiều bounds, chẳng hạn `<T extends Number & Comparable<T>>`, cũng không có tham số kiểu mặc định. Thứ tự bounds ảnh hưởng erasure và những phương thức được phép gọi qua `T`, chứ không bổ sung cú pháp `= SomeType`.

## 8. Generic method cũng không có default type parameter

Giới hạn này không chỉ áp dụng cho class hoặc record. Phương thức generic được khai báo dạng:

```java
static <T> T identity(T value) {
    return value;
}
```

Không thể khai báo:

```java
// CỐ Ý KHÔNG BIÊN DỊCH:
static <T = String> T identity(T value) {
    return value;
}
```

Khi gọi `identity("hello")`, compiler có thể suy luận `T = String` từ đối số. Khi một lời gọi không có đủ thông tin, compiler tiếp tục áp dụng quy tắc inference, chứ không đọc một default type argument từ khai báo.

Một generic method có thể được gọi bằng **explicit type witness**:

```java
Utility.<String>identity("hello")
```

Nếu `identity` là static method khả kiến của `Utility` theo ngữ cảnh, `<String>` ở lời gọi **đặt type argument của method**. Nó không thay đổi cách method được khai báo và không phải phép cast.

## 9. Ví dụ chạy được: target typing và type witness của method

Tạo tệp **`GenericMethodInferenceDemo.java`**:

```java
import java.util.ArrayList;
import java.util.List;

public class GenericMethodInferenceDemo {

    static <T> List<T> empty() {
        return new ArrayList<>();
    }

    static <T> T echo(T value) {
        return value;
    }

    public static void main(String[] args) {
        List<String> targetTyped = empty();
        targetTyped.add("A");

        var inferredWithoutTarget = empty();
        inferredWithoutTarget.add(10);
        inferredWithoutTarget.add("B");

        var explicitlyTyped =
                GenericMethodInferenceDemo.<String>empty();
        explicitlyTyped.add("C");

        String echoed = echo("Java");

        System.out.println(targetTyped);
        System.out.println(inferredWithoutTarget);
        System.out.println(explicitlyTyped);
        System.out.println(echoed);
    }
}
```

Biên dịch/chạy:

```bash
javac GenericMethodInferenceDemo.java
java GenericMethodInferenceDemo
```

Stdout:

```text
[A]
[10, B]
[C]
Java
```

Giải thích:

- `List<String> targetTyped = empty()` cung cấp target type để compiler suy luận `T` phù hợp với `String`.
- `var inferredWithoutTarget = empty()` không có target type `List<String>` và `empty()` không nhận tham số. Với `T` không bound trong ví dụ, `T` được suy luận thành `Object`.
- `GenericMethodInferenceDemo.<String>empty()` đặt type witness tường minh, khiến kết quả có kiểu `List<String>`.
- `echo("Java")` suy luận từ đối số `String` của method; không cần type witness.

`<String>` ở lời gọi static method chính là một cách **người gọi chọn type argument**, không phải một default được định nghĩa bởi `<T>`.

## 10. Không thể “bỏ đối số kiểu cuối” của một generic class

Trong một số ngôn ngữ có default generic parameters, có thể viết một kiểu nhiều tham số rồi lược bỏ một vài đối số phía sau. **Java không hỗ trợ quy ước đó.**

Cho:

```java
record Pair<K, V>(K key, V value) {
}
```

Các cách dùng:

```java
Pair<String, Integer> valid =
        new Pair<>("age", 20);
```

Và:

```java
// CỐ Ý KHÔNG HỢP LỆ:
Pair<String> missingOne;
Pair<String, Integer, Boolean> tooMany;
```

Ở tên kiểu `Pair<K,V>`, Java yêu cầu **đúng số lượng type arguments** tương ứng với khai báo nếu chọn dùng dạng parameterized type. Nó không tự bổ sung `V = Object`, `V = String` hoặc một kiểu nào khác để hoàn thành `Pair<String>`.

Nếu không viết type arguments ở kiểu biến:

```java
Pair raw;
```

thì đây là **raw type**, không phải “`Pair<Object,Object>` theo default”. Raw types được giữ để tương thích mã cũ nhưng có các hạn chế an toàn kiểu.

Lưu ý: Java có thể cho phép **suy luận toàn bộ type arguments** tại biểu thức tạo bằng diamond `new Pair<>(...)`. Điều này khác với việc viết thiếu một đối số trong tên kiểu `Pair<String>`.

## 11. `<>` không phải generic arguments tùy chọn ở mọi vị trí

Diamond `<>` là cú pháp dùng ở một số **class instance creation expressions** để suy luận type arguments. Nó không phải ký hiệu chung cho “bỏ các type arguments trong mọi nơi”.

So sánh:

```java
java.util.List<String> values = new java.util.ArrayList<>();
```

Dòng hợp lệ vì `<>` xuất hiện ở **vế phải tạo object**.

Nhưng:

```java
// CỐ Ý KHÔNG HỢP LỆ:
java.util.List<> values;
```

`List<>` không phải cú pháp khai báo generic type của biến. Khi cần một kiểu có đối số chưa biết, dùng `List<?>`:

```java
java.util.List<?> values = java.util.List.of("A");
```

`List<?>` có ý nghĩa “danh sách với type argument chưa xác định”, không có nghĩa “mặc định là `Object`” và không có nghĩa raw `List`.

### Wildcard cũng không phải default

`List<?>` chỉ mô tả giới hạn điều compiler có thể biết về một danh sách bất kỳ. Bạn có thể đọc phần tử như `Object`, nhưng không được thêm tùy tiện một `String` hoặc `Integer` vào danh sách có wildcard unknown.

`List<Object>` lại là một kiểu tham số hóa có đối số **cụ thể** `Object`; nó không thay thế trực tiếp được cho `List<String>` vì Java Generics invariant.

## 12. Constructor overload không đặt default cho T

Java hỗ trợ overload constructors hoặc tham số **giá trị** mặc định qua việc cung cấp overload, nhưng điều đó **không tương đương default type argument**.

Ví dụ:

```java
class Result<T> {
    private final T value;

    Result() {
        this.value = null;
    }

    Result(T value) {
        this.value = value;
    }

    T get() {
        return value;
    }
}
```

Đây là class hợp lệ. Có hai constructor:

- `Result()` tạo object với field `value = null`.
- `Result(T value)` tạo object với một giá trị được truyền vào.

**Không có constructor nào nói `T` mặc định là `String`.** Nếu caller viết:

```java
Result<String> a = new Result<>();
Result<Integer> b = new Result<>();
```

cả hai dùng được constructor không đối số mà vẫn có type arguments khác nhau.

Một phương thức nhận một **giá trị mặc định** như `Result(T initialValue)` cũng không khai báo “kiểu `T` mặc định”. Type parameter và giá trị chứa trong object là hai cấp khái niệm khác nhau.

## 13. Thiết kế thay thế 1: static factory method trả kiểu cụ thể

Nếu yêu cầu nghiệp vụ là “khi muốn một kho chuỗi, có một cách gọi ngắn dễ hiểu”, hãy tạo factory có **return type cụ thể** thay vì tìm cú pháp `class Box<T = String>`.

Tạo tệp **`NamedFactoryDemo.java`**:

```java
import java.util.ArrayList;
import java.util.List;

public class NamedFactoryDemo {

    static final class Box<T> {
        private final List<T> elements = new ArrayList<>();

        void add(T item) {
            elements.add(item);
        }

        List<T> items() {
            return List.copyOf(elements);
        }
    }

    static Box<String> strings() {
        return new Box<>();
    }

    static Box<Integer> integers() {
        return new Box<>();
    }

    static <T> Box<T> empty() {
        return new Box<>();
    }

    public static void main(String[] args) {
        var text = strings();
        text.add("A");

        var numbers = integers();
        numbers.add(10);

        Box<Long> longs = empty();
        longs.add(20L);

        System.out.println(text.items());
        System.out.println(numbers.items());
        System.out.println(longs.items());
    }
}
```

Chạy:

```bash
javac NamedFactoryDemo.java
java NamedFactoryDemo
```

Stdout:

```text
[A]
[10]
[20]
```

`strings()` có kiểu trả về tường minh `Box<String>`, `integers()` trả `Box<Integer>`. Method `empty()` là generic factory để caller chọn type argument thông qua target type hoặc type witness.

**Ưu điểm:** API dễ hiểu, caller không phải nhắc lại type argument trong những trường hợp phổ biến.

**Giới hạn:** Tên factory được thiết kế cụ thể cho một lựa chọn nghiệp vụ. Nó **không** làm khai báo `Box<T>` nhận một default type argument do ngôn ngữ quản lý.

Nếu dữ liệu `null` được cho phép, `List.copyOf(elements)` sẽ từ chối `null` khi tạo kết quả. Chương trình này chỉ thêm các phần tử không null.

## 14. Thiết kế thay thế 2: factory có một kiểu mặc định theo quy ước API

Đôi khi muốn có một “đường gọi mặc định” và một “đường gọi linh hoạt”. Có thể thiết kế hai method với **tên rõ ràng**, hoặc overload bằng tham số đầu vào khác nhau.

Tạo tệp **`FactoryChoiceDemo.java`**:

```java
import java.util.ArrayList;
import java.util.List;

public class FactoryChoiceDemo {

    static final class Bucket<T> {
        private final List<T> items = new ArrayList<>();

        void add(T value) {
            items.add(value);
        }

        List<T> snapshot() {
            return List.copyOf(items);
        }
    }

    static Bucket<String> defaultTextBucket() {
        return new Bucket<>();
    }

    static <T> Bucket<T> bucketOf(T first) {
        Bucket<T> result = new Bucket<>();
        result.add(first);
        return result;
    }

    static <T> Bucket<T> emptyBucket() {
        return new Bucket<>();
    }

    public static void main(String[] args) {
        var defaultText = defaultTextBucket();
        defaultText.add("hello");

        var numbers = bucketOf(7);
        numbers.add(8);

        var explicit =
                FactoryChoiceDemo.<Double>emptyBucket();
        explicit.add(2.5);

        System.out.println(defaultText.snapshot());
        System.out.println(numbers.snapshot());
        System.out.println(explicit.snapshot());
    }
}
```

Biên dịch/chạy:

```bash
javac FactoryChoiceDemo.java
java FactoryChoiceDemo
```

Stdout:

```text
[hello]
[7, 8]
[2.5]
```

Lưu ý tên `defaultTextBucket()` chỉ diễn đạt **mặc định do tác giả API tự chọn**. Nó không làm `Bucket<T>` hỗ trợ `T = String` trong khai báo.

Không nên đặt tên hai overload chỉ khác generic return type:

```java
// CỐ Ý KHÔNG HỢP LỆ — return type không dùng để overload:
static Bucket<String> create() { return new Bucket<>(); }
static <T> Bucket<T> create() { return new Bucket<>(); }
```

Hai method cùng tên, cùng danh sách tham số rỗng không thể phân biệt chỉ bằng kiểu kết quả hoặc type parameter. Dùng tên khác, hoặc chữ ký tham số thực sự khác khi có ý nghĩa.

## 15. Thiết kế thay thế 3: subtype cố định type argument

Một cách khác là định nghĩa **class con không generic** cố định kiểu của class cha:

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

Đây là các khai báo class hợp lệ, không chứa `main`.

`StringBox` không khai báo `T` của riêng nó; nó **kế thừa** `Box<String>`. Caller có thể viết `new StringBox("hello")` thay vì `new Box<String>("hello")`, và compiler biết `get()` trả `String` khi nhìn từ `StringBox`.

Điều này khác với default generic argument:

- `Box<Integer>` vẫn là một cách dùng hợp lệ của `Box<T>`.
- `StringBox` là **một class khác** với `Box<T>` và có tên/quan hệ kế thừa riêng.
- Java không cho viết `Box` rồi tự động hiểu nó là `StringBox` hoặc `Box<String>`.

### Ví dụ chạy được

Tệp **`SpecializedSubtypeDemo.java`**:

```java
public class SpecializedSubtypeDemo {

    static class Box<T> {
        private final T value;

        Box(T value) {
            this.value = value;
        }

        T get() {
            return value;
        }
    }

    static final class StringBox extends Box<String> {
        StringBox(String value) {
            super(value);
        }
    }

    public static void main(String[] args) {
        StringBox text = new StringBox("ready");
        Box<Integer> number = new Box<>(12);

        String message = text.get();

        System.out.println(message);
        System.out.println(number.get());
    }
}
```

Lệnh:

```bash
javac SpecializedSubtypeDemo.java
java SpecializedSubtypeDemo
```

Stdout:

```text
ready
12
```

Subclass specialization hữu ích nếu `StringBox` thật sự là một khái niệm miền nghiệp vụ hoặc cần API/validation bổ sung. Nếu chỉ cần cách tạo nhanh một object generic, factory method ở phần trước thường ít class hơn.

## 16. Thiết kế thay thế 4: dùng target type ở ranh giới API

Thường không cần tạo factory riêng để “mặc định” một kiểu. Chỉ cần caller ghi rõ **kiểu mà chương trình mong muốn** ở bên trái hoặc ở return type.

Ví dụ:

```java
static <T> java.util.List<T> emptyList() {
    return new java.util.ArrayList<>();
}

static java.util.List<String> names() {
    return emptyList();
}
```

Hai method này cần đặt trong class.

`names()` khai báo kết quả `List<String>`, nên compiler có target type rõ ràng cho lời gọi generic `emptyList()`. Một caller dùng `names()` không cần viết type witness nữa.

Tương tự:

```java
static void consumeStrings(java.util.List<String> values) {
    System.out.println(values.size());
}

static void demo() {
    consumeStrings(new java.util.ArrayList<>());
}
```

Parameter type của `consumeStrings` cung cấp context cho diamond ở biểu thức tạo `ArrayList`.

Đây là **target typing**, không phải việc Java tạo default generic parameter. Nó giữ ý định kiểu tại **ranh giới sử dụng** và giúp code client ngắn khi compiler có đủ ngữ cảnh.

## 17. Kiểu raw không phải sự thay thế cho default type argument

Một anti-pattern là bỏ hẳn `<...>`:

```java
java.util.List values = new java.util.ArrayList();
values.add("text");
values.add(100);
```

Dòng này có thể biên dịch với cảnh báo raw/unchecked. Nhưng `values` là **raw `List`**, không phải một `List<String>` có đối số mặc định.

Rủi ro:

- Compiler không kiểm tra đầy đủ loại phần tử trong thao tác `add`.
- Khi gán dữ liệu raw sang biến `List<String>` qua unchecked conversion, có thể tạo **heap pollution**.
- Lỗi `ClassCastException` có thể xuất hiện sau đó khi phần tử bị đọc như `String`.

`List<Object>` cũng không phải đồng nghĩa với raw `List`:

```java
java.util.List<Object> values =
        new java.util.ArrayList<>();
```

Dòng trên có generic type argument rõ ràng là `Object`. Compiler kiểm tra nhất quán rằng các phần tử dùng như `Object`, dù danh sách có thể chứa cả `String` và `Integer`.

**Khi chưa biết type argument mà chỉ cần đọc**, `List<?>` thường phù hợp hơn raw `List`, vì wildcard vẫn duy trì các ràng buộc kiểu ở compile time.

## 18. Default type argument không giải quyết type erasure

Giả sử Java có default type argument (thực tế **không có**), việc chọn một type argument lúc biên dịch vẫn sẽ không tự biến Generics thành reified types. Với Java thực tế:

```java
var a = new Box<>("A");
var b = new Box<>(10);
```

nếu cả hai object là instance trực tiếp của cùng generic class `Box`, `getClass()` thường trả về cùng class runtime, vì type arguments bị erasure.

Tạo tệp **`GenericRuntimeIdentityDemo.java`**:

```java
public class GenericRuntimeIdentityDemo {

    static class Box<T> {
        private final T value;

        Box(T value) {
            this.value = value;
        }

        T get() {
            return value;
        }
    }

    public static void main(String[] args) {
        Box<String> text = new Box<>("A");
        Box<Integer> number = new Box<>(10);

        System.out.println(
                text.getClass() == number.getClass());
        System.out.println(
                Box.class.getTypeParameters()[0].getName());
    }
}
```

Chạy:

```bash
javac GenericRuntimeIdentityDemo.java
java GenericRuntimeIdentityDemo
```

Stdout:

```text
true
T
```

`getClass()` chỉ cho thấy runtime class của object; `getTypeParameters()` cho thấy `Box` khai báo một type variable tên `T`. Nó không cho thấy mỗi instance đã được tạo với một default type argument nào — bởi Java không có default đó, và thông thường object không tự mang type argument đã dùng trong mã nguồn.

Dùng subclass `StringBox extends Box<String>` có thể khiến reflection đọc được generic superclass được khai báo trên **class con**, nhưng điều đó không biến type argument thành một mặc định tự động trên mọi `Box`.

## 19. Phân biệt các khái niệm “mặc định” dễ bị nhầm

Trong Java, cùng từ “default” có thể chỉ những tính năng khác nhau.

| Khái niệm | Java có hỗ trợ? | Có phải default type argument? |
|---|---|---|
| Giá trị mặc định của field reference là `null` | Có | Không |
| Giá trị mặc định của field primitive như `int` là `0` | Có | Không |
| Constructor không đối số | Có thể có hoặc khai báo tường minh | Không |
| Overload method để cung cấp giá trị mặc định | Có thể tự thiết kế | Không |
| `default` method trong interface | Có (Java 8+) | Không |
| Diamond `new Box<>()` | Có | Không; là type inference |
| `var x = expression` | Có (local inference) | Không |
| Upper bound `<T extends Number>` | Có | Không |
| Raw type `Box` | Có để tương thích | Không |
| Wildcard `Box<?>` | Có | Không |
| Khai báo `class Box<T = String>` | **Không** | Đây chính là cú pháp không hỗ trợ |

### `default` method trong interface không liên quan đến type arguments

Ví dụ:

```java
interface Printer<T> {
    void print(T value);

    default void printTwice(T value) {
        print(value);
        print(value);
    }
}
```

Đây là interface hợp lệ. Từ khóa `default` ở đây khai báo **triển khai mặc định của một instance method trong interface**, không cung cấp mặc định `T = String`.

Một interface có default methods vẫn phải được dùng như `Printer<String>`, `Printer<Integer>`, `Printer<?>` hoặc trong ngữ cảnh raw type theo quy tắc bình thường.

## 20. Type inference không phải lúc nào cũng chọn `Object`

Một kết luận quá rộng là:

> “Không có default type argument nên cứ thiếu `<T>` thì compiler tự chọn `Object`.”

Sai vì compiler có thể suy luận từ nhiều nguồn:

1. **Target type:** `Box<String> x = new Box<>()` gợi `String`.
2. **Constructor arguments:** `var x = new Box<>(123)` gợi `Integer`.
3. **Method arguments:** `identity("A")` gợi `String`.
4. **Return context:** `List<String> names() { return Collections.emptyList(); }` cung cấp kiểu đích trong biểu thức return.
5. **Bounds:** `<T extends Number>` giới hạn tập kiểu ứng viên.
6. **Explicit type witness:** `Factory.<String>empty()` đặt `String` tại lời gọi.

Suy luận kiểu Java là một hệ thống ràng buộc, không phải một câu lệnh “nếu vắng thì gán Object”. Chỉ trong những trường hợp cụ thể **không có ràng buộc hữu ích khác**, chẳng hạn `var x = new Box<>()` với type parameter không bound và constructor không đối số, `Object` mới là một kết quả điển hình.

Ngược lại, nếu viết **raw type** `Box x`, compiler không suy luận `T = Object` theo cùng cách như `Box<Object> x`; đây là một kiểu thô có các quy tắc riêng.

## 21. Những lỗi phổ biến và cách sửa

**Lỗi 1 — Viết `class Box<T = String>`.** Java không hỗ trợ. Sửa thành `Box<T>`, rồi tạo `static Box<String> strings()` nếu muốn lựa chọn nghiệp vụ cụ thể.

**Lỗi 2 — Dùng `<T extends String>` để mô phỏng default `String`.** `extends` là **bound**, không phải default. Ngoài ra `String` là final, nên việc chọn bound này có nghĩa hẹp về tập subtype; nó không tạo cú pháp bỏ type argument để mặc định `String`.

**Lỗi 3 — Nghĩ `new Box<>()` luôn chọn `String` nếu chương trình thường làm việc với chuỗi.** Compiler không suy luận từ sở thích nghiệp vụ. Phải có target type, constructor arguments, type witness hoặc factory trả kiểu cụ thể.

**Lỗi 4 — Viết `Box<> x;`.** Diamond không dùng làm tên kiểu của biến. Viết `Box<String> x;`, `Box<?> x;`, hoặc khai báo `var x = new Box<>()` nếu hiểu rõ kiểu được suy luận.

**Lỗi 5 — Dùng `Box` raw như “default”.** Raw type bỏ thông tin generic và có thể dẫn đến unchecked operations; nó không tương đương `Box<Object>` hoặc `Box<String>`.

**Lỗi 6 — Dùng `Box<?>` như `Box<Object>`.** Wildcard chưa biết cho phép đọc theo bound an toàn nhưng hạn chế việc truyền tùy ý một `T` mới vào các phương thức nhận `T`.

**Lỗi 7 — Cho rằng `var` suy ra kiểu dựa trên lệnh `set` gọi sau.** Type của biến được xác định ở initializer; các lệnh sau không làm nó thay đổi.

**Lỗi 8 — Bỏ một đối số của `Pair<K,V>` và kỳ vọng mặc định.** `Pair<String>` không hợp lệ nếu `Pair` khai báo hai type parameters. Ghi đủ arguments hoặc dùng diamond tại class instance creation expression.

**Lỗi 9 — Nghĩ explicit type witness là cast.** `<String>` trong `Factory.<String>empty()` tham gia kiểm tra kiểu; nó không ép một object tùy ý thành `String`.

**Lỗi 10 — Dùng return type khác nhau để overload hai factory cùng tên, không tham số.** Java không dùng return type để phân biệt overload. Hãy đặt tên khác hoặc thay đổi hợp đồng tham số có ý nghĩa.

**Lỗi 11 — Tạo constructor không đối số rồi nghĩ `T` đã mặc định.** Constructor chỉ chọn cách khởi tạo **giá trị**, không cung cấp type argument mặc định.

**Lỗi 12 — Nhầm default interface method với default generic parameter.** Cùng từ “default” nhưng hoàn toàn khác vai trò cú pháp.

**Lỗi 13 — Dùng unchecked cast để giả vờ có generic default.** Cast không được kiểm tra đầy đủ có thể che lỗi compile time và chuyển thành `ClassCastException` muộn hơn; giải pháp tốt hơn là đặt type ở API boundary.

**Lỗi 14 — Cho rằng runtime `getClass()` sẽ báo `Box<String>` được “mặc định”.** Type erasure khiến nhiều instantiations generic dùng cùng runtime class; thông tin đó thường không thể suy từ object một cách tổng quát.

## 22. Chọn thiết kế API theo nhu cầu

| Nhu cầu | Cách viết hợp lý | Lý do |
|---|---|---|
| Mỗi caller biết type argument | `Box<String> box = new Box<>();` | Diamond dùng target type |
| Kiểu có thể suy ra từ giá trị | `var box = new Box<>(42);` | Constructor argument gợi `Integer` |
| Muốn biến có type argument rõ với `var` | `var box = new Box<String>();` | Initializer ghi tường minh `String` |
| Muốn một cách tạo “mặc định dùng String” | `static Box<String> strings()` | Quy ước ở API, không phụ thuộc cú pháp generic không hỗ trợ |
| Muốn generic factory trả kiểu do caller chọn | `static <T> Box<T> empty()` | Target typing hoặc type witness |
| Cần class với hợp đồng cố định String | `class StringBox extends Box<String>` | Subtype chuyên biệt |
| Chỉ đọc từ một Box không biết T | `Box<?>` | Bảo toàn giới hạn generic an toàn |
| Cần ràng buộc T phải là số | `Box<T extends Number>` | Bound, không phải default |
| Muốn nhiều type parameters | `Pair<K,V>` với đủ args | Không có partial type argument default |
| Muốn chọn một giá trị khởi tạo | Constructor/factory nhận giá trị | Giá trị mặc định khác type argument |
| Muốn hỗ trợ mã legacy | Chỉ dùng raw type ở ranh giới bất khả kháng | Khoanh vùng unchecked warning |

Không nên cố mô phỏng default generic parameter bằng nhiều tầng unchecked casts, raw types hoặc overload chỉ khác return type. API có tên factory rõ ràng thường dễ đọc và dễ kiểm thử hơn.

## 23. Bài thực hành có đáp án

### Bài 1 — Phân biệt loại cú pháp

Trong các dòng dưới đây, dòng nào không hợp lệ?

```java
class Box<T> { }                // A
class Other<T = String> { }     // B
class Numeric<T extends Number> { } // C
```

**Đáp án:** B không hợp lệ vì Java không có default type argument. A và C hợp lệ; C dùng upper bound.

### Bài 2 — Suy luận qua vế trái

```java
Box<String> a = new Box<>();
```

`T` được xác định thế nào, giả sử `Box` có constructor không đối số?

**Đáp án:** Compiler suy luận `String` từ target type `Box<String>`. Không cần default type argument.

### Bài 3 — `var` và constructor rỗng

```java
var b = new Box<>();
```

Nếu `Box<T>` không có bound và constructor không nhận dữ liệu để suy luận, kiểu tĩnh của `b` là gì?

**Đáp án:** `Box<Object>` trong trường hợp được mô tả. Đó là kết quả type inference, không phải raw `Box`.

### Bài 4 — Constructor có đối số

```java
var c = new Box<>(10);
```

Nếu `Box` có constructor `Box(T value)`, `T` thường là gì?

**Đáp án:** `Integer` sau boxing `10` và inference từ constructor argument.

### Bài 5 — Generic method rỗng

```java
static <T> java.util.List<T> empty() {
    return new java.util.ArrayList<>();
}
```

Làm sao có `List<String>` mà không thay khai báo method?

**Đáp án:** Dùng `List<String> list = empty();` hoặc `var list = Factory.<String>empty();` nếu method static thuộc `Factory`.

### Bài 6 — Raw type có phải default không?

```java
Box raw = new Box();
```

Có đồng nghĩa với `Box<String>` không?

**Đáp án:** Không. Đây là raw type; compile-time generic checking bị suy giảm.

### Bài 7 — Multiple type parameters

```java
record Pair<K, V>(K key, V value) { }
```

`Pair<String>` có hợp lệ không?

**Đáp án:** Không. Đã sử dụng dạng parameterized type thì cần đúng hai arguments. Có thể dùng `Pair<String,Integer>` hoặc diamond ở biểu thức tạo nếu compiler suy luận được.

### Bài 8 — Bounds

`<T extends Number>` có nghĩa “`T` mặc định là `Number`” không?

**Đáp án:** Không. Nó quy định giới hạn trên; type inference có thể dựa vào bound nhưng không có default type argument.

### Bài 9 — Factory dùng kiểu phổ biến

Nếu hệ thống thường tạo `Box<String>`, nên làm thế nào để caller không phải lặp `String`?

**Đáp án:** Cung cấp `static Box<String> strings()` hoặc subtype `StringBox` khi có lý do nghiệp vụ; vẫn giữ `Box<T>` generic bình thường.

### Bài 10 — Wildcard

`Box<?>` có phải cách ghi “đối số T mặc định là Object” không?

**Đáp án:** Không. `?` biểu diễn một type argument chưa biết và hạn chế các thao tác ghi phụ thuộc `T`.

### Bài 11 — Return-type-only overload

Hai method `Box<String> create()` và `<T> Box<T> create()` cùng tên, cùng không tham số có thể tồn tại bên cạnh nhau không?

**Đáp án:** Không. Return type và type parameter không đủ phân biệt hai method signatures trùng nhau.

### Bài 12 — Runtime

`new Box<String>("a").getClass()` và `new Box<Integer>(1).getClass()` có thể cùng trả class nào?

**Đáp án:** Cùng class runtime `Box`, do type erasure. Đối số kiểu trong mã nguồn không tạo runtime class riêng cho mỗi instantiation.

## 24. Quy trình kiểm tra khi gặp yêu cầu “mặc định T là String”

1. **Xác định yêu cầu là kiểu hay giá trị.** “T mặc định là String” khác “giá trị ban đầu là chuỗi rỗng”.
2. **Xem caller có target type không.** Nếu có, dùng diamond/generic factory inference.
3. **Xem constructor hoặc method có đối số mang thông tin kiểu không.** Có thể suy luận từ input.
4. **Nếu không có target type và không có input**, việc suy luận có thể ra `Object` hoặc kiểu chịu bounds; cần ghi rõ type argument nếu muốn loại cụ thể.
5. **Nếu nhiều callers cùng cần `String`**, tạo factory có return type `Box<String>` để gom ý đồ vào một nơi.
6. **Nếu subtype riêng có ý nghĩa**, tạo `StringBox extends Box<String>` thay vì cố biến `Box` raw thành String.
7. **Nếu API có nhiều type parameters**, không kỳ vọng Java cho lược bớt tham số phía cuối như cú pháp defaults của một số ngôn ngữ khác.
8. **Tránh raw type và unchecked cast** để mô phỏng mặc định.
9. **Thử compiler với trường hợp sai:** một `Box<String>` phải từ chối giá trị khác String ở method nhận `T`.
10. **Kiểm tra phiên bản Java thực tế:** diamond và local-variable inference đã có từ các phiên bản khác nhau; các ví dụ dùng Java 17+.

### Câu hỏi tự kiểm tra nhanh

Nếu thấy một dòng:

```java
var items = new java.util.ArrayList<>();
```

hãy tự hỏi: “Compiler đã có bằng chứng nào để suy luận `String` chưa?” Trong ví dụ này chưa có, nên kết quả thông thường là `ArrayList<Object>`. Nếu ý định là danh sách chuỗi, hãy viết rõ type ở một vị trí hợp lệ thay vì kỳ vọng default generic parameter không tồn tại.

## 25. Tổng kết và nguồn tài liệu

**Java Generics không hỗ trợ giá trị mặc định cho tham số kiểu trong khai báo.** Không thể viết `class Box<T = String>` hoặc `<T = String>` cho generic method. `extends` chỉ đặt bounds; raw type bỏ type arguments, wildcard diễn đạt kiểu chưa biết, còn diamond, `var` và generic method inference suy luận kiểu tại **điểm sử dụng**.

Khi muốn caller không phải lặp một type argument, lựa chọn thiết kế phù hợp thường là:

- **Diamond + target typing:** `Box<String> x = new Box<>()`.
- **Đối số kiểu rõ trong initializer:** `var x = new Box<String>()`.
- **Suy luận từ dữ liệu:** `var x = new Box<>("text")`.
- **Generic static factory:** `List<String> x = empty()` hoặc `Factory.<String>empty()`.
- **Named factory cho kiểu phổ biến:** `Box<String> strings()`.
- **Subtype chuyên biệt khi cần:** `StringBox extends Box<String>`.

Các giải pháp này giữ kiểm tra kiểu ở compile time mà không dựa vào raw types hoặc casts không an toàn. Không giải pháp nào biến default generic argument thành một tính năng mà Java chưa hỗ trợ.

Nguồn tham khảo:

- [Java Language Specification §4.4 — Type Variables](https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html#jls-4.4): khai báo type variables và bounds.
- [Java Language Specification §4.5 — Parameterized Types](https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html#jls-4.5): type arguments và kiểu tham số hóa.
- [Java Language Specification §8.1.2 — Generic Classes and Type Parameters](https://docs.oracle.com/javase/specs/jls/se17/html/jls-8.html#jls-8.1.2): quy tắc type parameters của class.
- [Java Language Specification §8.4.4 — Generic Methods](https://docs.oracle.com/javase/specs/jls/se17/html/jls-8.html#jls-8.4.4): generic methods.
- [Java Language Specification §15.9 — Class Instance Creation Expressions](https://docs.oracle.com/javase/specs/jls/se17/html/jls-15.html#jls-15.9): constructor invocation và diamond.
- [Java Language Specification §18 — Type Inference](https://docs.oracle.com/javase/specs/jls/se17/html/jls-18.html#jls-18): suy luận kiểu bằng ràng buộc.
- [Java Language Specification §14.4 — Local Variable Declarations](https://docs.oracle.com/javase/specs/jls/se17/html/jls-14.html#jls-14.4): `var` và local-variable type inference.
- [Java Language Specification §4.8 — Raw Types](https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html#jls-4.8): raw type không đồng nghĩa parameterized type có default.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="generics-language-limits-008">Các giới hạn overload, instanceof và tạo mảng liên quan xóa kiểu</a>

<details>
<summary>Xem chi tiết</summary>

# Các giới hạn overload, instanceof và tạo mảng liên quan xóa kiểu trong Java Generics

## Mục tiêu học tập

Java Generics giúp kiểm tra kiểu ở thời điểm biên dịch, nhưng phần lớn thông tin về *type argument* (ví dụ `String` trong `List<String>`) không tồn tại như một loại đối tượng riêng ở runtime. Cơ chế này gọi là **type erasure** (xóa kiểu). Nó mang lại khả năng tương thích với thư viện Java trước generics, đồng thời tạo ra những giới hạn quan trọng trong ngôn ngữ.

Ba nhóm thường gây bối rối nhất là:

1. **Overload:** không thể định nghĩa hai overload chỉ khác nhau ở `List<String>` và `List<Integer>` vì chữ ký của chúng trùng nhau sau erasure.
2. **`instanceof`:** không thể nhìn một đối tượng kiểu `Object` rồi hỏi bằng `instanceof List<String>` xem nó có “thật sự” là danh sách chỉ chứa String; runtime chỉ xác minh được kiểu container ở cấp phù hợp.
3. **Tạo mảng:** không thể viết `new List<String>[n]` hay `new T[n]` trong generic method, bởi mảng cần component type thực mà JVM có thể kiểm tra.

Bài này đi từ quy tắc ngôn ngữ tới ví dụ **Java 17+ có thể biên dịch/chạy**, các trường hợp lỗi compiler đã được kiểm chứng, cách thiết kế API thay thế, rồi tới bài tập tự kiểm tra.

## 1. Nền tảng: type erasure xóa những gì?

Xét:

```java
List<String> names = new ArrayList<>();
List<Integer> numbers = new ArrayList<>();
```

Ở mã nguồn, hai tham chiếu có kiểu tham số hóa khác nhau:

- `names.add("An")` hợp lệ, còn `names.add(123)` bị compiler từ chối.
- `numbers.add(123)` hợp lệ, còn `numbers.add("An")` bị compiler từ chối.
- `names.get(0)` được compiler nhìn nhận là `String`.
- `numbers.get(0)` được compiler nhìn nhận là `Integer`.

Nhưng runtime không có một class `ArrayList<String>` khác với `ArrayList<Integer>`. Các instance thông thường cùng dùng lớp `java.util.ArrayList` và giao diện `java.util.List`; `getClass()` không biểu diễn type argument của từng instance.

Erasure có quy tắc cơ bản:

| Khai báo kiểu nguồn | Erasure | Ghi chú |
| --- | --- | --- |
| `List<String>` | `List` | Bỏ thông tin đối số String. |
| `List<Integer>` | `List` | Trùng erasure với List String. |
| `Map<String, Integer>` | `Map` | Cả hai type argument không nằm trong runtime class. |
| `T` không có bound tường minh | `Object` | Bound ngầm là Object. |
| `T extends Number` | `Number` | Erasure của type variable lấy từ bound đầu tiên. |
| `T extends Number & Comparable<T>` | `Number` | Dùng erasure của leftmost bound. |
| `String` | `String` | Không chứa type argument cần xóa. |
| `String[]` | `String[]` | Component type String tồn tại tại runtime. |

Đây là sự đơn giản hóa hữu ích để hiểu cơ chế; class file vẫn có thể chứa metadata generic trong thuộc tính `Signature`, phục vụ reflection và công cụ phân tích. Metadata này **không biến** một List String thành một class runtime riêng có thể được `instanceof` hay array store check xác minh đầy đủ.

Một ví dụ trực giác:

```java
// Mã đặt trong một phương thức có import List, ArrayList:
boolean same = new ArrayList<String>().getClass()
        == new ArrayList<Integer>().getClass();
// same == true
```

Vì hai đối tượng có cùng lớp runtime, các thao tác phụ thuộc trực tiếp vào class descriptor không thể tự phân biệt type argument String hay Integer.

## 2. Giới hạn thứ nhất: không overload chỉ bằng type argument generic

### 2.1. Tình huống bị compiler từ chối

Giả sử bạn muốn cùng tên phương thức xử lý hai loại danh sách:

```java
import java.util.List;

class OverloadErasureClash {
    static void process(List<String> data) {}
    static void process(List<Integer> data) {}
}
```

**Tệp `OverloadErasureClash.java` cố ý không biên dịch.** Kiểm tra bằng:

```bash
javac --release 17 OverloadErasureClash.java
```

Thông báo chính đã kiểm chứng:

```text
name clash: process(List<Integer>) and process(List<String>)
have the same erasure
```

Ở mã nguồn, hai kiểu `List<String>` và `List<Integer>` là khác nhau. Nhưng chữ ký tham số sau erasure của cả hai phương thức đều là `process(List)`. Java không cho phép duy trì chúng như hai overload chỉ khác generic argument.

Để hiểu chính xác, hãy phân biệt:

- **Chữ ký phương thức Java:** tên, danh sách tham số hình thức và các yếu tố liên quan đến tham số kiểu theo quy tắc JLS.
- **Erasure của chữ ký:** thay các kiểu generic trong tham số bằng erasure.
- **JVM method descriptor:** bao gồm cả kiểu trả về ở bytecode, nhưng điều này không có nghĩa Java được overload chỉ bằng kiểu trả về.

Không nên suy ra “generic bị xóa” nghĩa là mọi phần trong phương thức bị xóa: tên và những kiểu tham số không phụ thuộc type argument vẫn phân biệt được các phương thức.

### 2.2. Đổi tên theo mục đích khi hợp đồng thực sự khác

Một giải pháp rõ ràng:

```java
static void logStrings(List<String> values) {
    System.out.println("strings=" + values);
}

static void logIntegers(List<Integer> values) {
    System.out.println("integers=" + values);
}
```

Đây là hai phương thức minh họa đặt trong lớp đã import `List`, cũng xuất hiện trong chương trình hoàn chỉnh ở phần 6.

Ưu điểm: tên method cho biết nghiệp vụ nào áp dụng. Chỉ đổi một vài ký tự để “lách” erasure không phải lúc nào cũng tốt; dùng tên theo ý nghĩa thực sự nếu hai tác vụ khác nhau.

### 2.3. Nếu cùng một thuật toán, thường dùng một generic method

Nếu hai kiểu danh sách cần cùng hành vi, ví dụ chỉ đếm số phần tử, không nên tạo hai overload:

```java
static <E> int count(List<E> values) {
    return values.size();
}
```

Cả `count(List<String>)` và `count(List<Integer>)` ở vị trí gọi đều hợp lệ. Trong method, phần thuật toán không cần biết E là String hay Integer.

Nếu method chỉ quan sát List mà không sử dụng E ở vị trí khác, có thể cân nhắc chữ ký đơn giản hơn:

```java
static int countUnknown(List<?> values) {
    return values.size();
}
```

Chỉ dùng type variable có tên khi cần diễn đạt quan hệ kiểu giữa tham số, kết quả hoặc nhiều vị trí; dùng wildcard khi kiểu phần tử cụ thể không quan trọng đối với contract.

### 2.4. Một tham số khác có thể tạo overload thật

Những overload sau khác nhau ở tham số thực sự không bị xóa giống nhau:

```java
static void process(List<String> data, int mode) {}
static void process(List<Integer> data, String description) {}
```

Erasure tương ứng là `process(List, int)` và `process(List, String)`, nên chúng có thể cùng tồn tại nếu đặt trong một lớp hợp lệ. Tuy nhiên, tham số phụ phải có ý nghĩa thiết kế. Tránh thêm một giá trị giả chỉ để ép overload resolution chạy.

Có thể dùng tên method theo loại nghiệp vụ thay vì tạo các cặp tham số gây khó hiểu cho người gọi.

### 2.5. Type variable có bound cũng có thể clash

Không phải mọi erasure của T đều là Object. Với `T extends Number`, erasure của T là Number:

**Tệp `OverloadBoundClash.java` — không biên dịch:**

```java
class OverloadBoundClash {
    <T extends Number> void take(T n) {}
    void take(Number n) {}
}
```

Kiểm tra:

```bash
javac --release 17 OverloadBoundClash.java
```

Compiler báo dạng:

```text
name clash: take(Number) and <T>take(T) have the same erasure
```

Giải pháp không phải cast T về Object rồi hy vọng hai method khác nhau. Cần một contract khác tên, kiểu tham số khác ở runtime, hoặc gộp logic vào một method phù hợp.

### 2.6. Đừng nhầm overload với override

Quan hệ **override** ở chuỗi kế thừa có quy tắc bổ sung về **subsignature** nhằm bảo toàn khả năng tương thích khi một method trong thư viện cũ được generic hóa. Vì thế có trường hợp method của lớp con dùng chữ ký đã xóa mà vẫn override được method generic của lớp cha.

Điều này **không** cho phép hai overload `process(List<String>)` và `process(List<Integer>)` đứng cùng một lớp. Khi sửa `name clash`, hãy xác định bạn đang xem **hai method được overload** hay **hai method trong quan hệ kế thừa**.

## 3. Giới hạn thứ hai: `instanceof` không kiểm tra sâu type argument

### 3.1. Kiểm tra container là List

Giả sử một giá trị đến từ API không biết trước kiểu:

```java
Object input = List.of("A", 42);
boolean listLike = input instanceof List<?>;
System.out.println(listLike);
```

Đầu ra:

```text
true
```

`instanceof List<?>` chỉ yêu cầu giá trị là một List với kiểu phần tử chưa biết. Runtime có thể xác định điều đó qua class/interface của đối tượng. Nó không cần và **không cố** chứng minh toàn bộ phần tử là String.

Kết quả vẫn là true khi List chứa Integer, vì `List<?>` không cam kết kiểu phần tử cụ thể.

### 3.2. Vì sao `Object instanceof List<String>` bị từ chối?

**Tệp `InstanceofParameterized.java` — cố ý không biên dịch:**

```java
import java.util.List;

class InstanceofParameterized {
    static boolean check(Object value) {
        return value instanceof List<String>;
    }
}
```

Thử:

```bash
javac --release 17 InstanceofParameterized.java
```

Trong môi trường đã kiểm chứng, compiler báo:

```text
Object cannot be safely cast to List<String>
```

Lý do: toán hạng có kiểu tĩnh Object, nhưng `List<String>` không phải kiểu reifiable, nên runtime không thể thực hiện phép thử phân biệt những List có type argument String.

**Cẩn thận với phát biểu tuyệt đối:** trong Java 17, có một số phép `instanceof` dùng kiểu tham số hóa như `List<String>` được compiler cho phép **nếu kiểu tĩnh của toán hạng đã đủ cụ thể để phép cast tương ứng hợp lệ**. Ví dụ sau đã được biên dịch với `--release 17`:

```java
static boolean redundantCheck(List<String> value) {
    return value instanceof List<String>;
}
```

Đây không phải khả năng kiểm tra runtime mới cho từng phần tử: kiểu tĩnh đã biết là `List<String>`, phép thử không phát hiện List bị heap pollution. Vì vậy quy tắc thực hành là **đừng dùng `instanceof List<String>` để kiểm tra dữ liệu không đáng tin**; thay bằng `List<?>` và xác thực từng phần tử. Một lời khẳng định “`instanceof List<String>` luôn luôn không biên dịch trong mọi ngữ cảnh” là không chính xác với Java 17.

### 3.3. `instanceof T` khi T là type variable

**Tệp `InstanceofTypeVariable.java` — cố ý không biên dịch:**

```java
class InstanceofTypeVariable {
    static <T> boolean check(Object value) {
        return value instanceof T;
    }
}
```

Với `javac --release 17`, kết quả có dạng:

```text
Object cannot be safely cast to T
```

T là biến kiểu ở compile time, không tự cung cấp một class token runtime tương ứng với từng lời gọi. Không dùng `T.class` hoặc `instanceof T` để cố truy cập lớp thực.

Nếu cần kiểm tra runtime một kiểu class cụ thể, truyền token từ caller:

```java
static <T> boolean isType(Object value, Class<T> token) {
    return token.isInstance(value);
}
```

Lời gọi `isType("A", String.class)` trả true; `isType(1, String.class)` trả false. Nhưng `List.class` chỉ kiểm tra loại container List, không thể thay thế token `List<String>.class` vì class literal đó không tồn tại.

### 3.4. Cách kiểm tra một List thực sự chứa String

Một phương án:

```java
static boolean allStrings(Object value) {
    if (!(value instanceof List<?> list)) {
        return false;
    }
    for (Object element : list) {
        if (!(element instanceof String)) {
            return false;
        }
    }
    return true;
}
```

Đặt method trong lớp có import `java.util.List`, như chương trình hoàn chỉnh ở phần 6. Method trước hết kiểm tra container và sau đó duyệt mọi phần tử.

Hợp đồng trên chủ động coi `null` là không hợp lệ (vì `null instanceof String` là false). Với list rỗng, kết quả true theo nghĩa “không có phần tử nào vi phạm”. Nếu nghiệp vụ cần ít nhất một String, thêm điều kiện `!list.isEmpty()`.

Lưu ý về **thời điểm kiểm tra**: nếu caller khác có thể sửa danh sách sau khi kiểm tra, kết quả `allStrings` không bảo đảm danh sách sẽ mãi đúng. Để dựng một ranh giới tin cậy, có thể kiểm tra và copy sang collection mới, tránh chia sẻ alias mutable.

### 3.5. Reflection có thể biết khai báo nhưng không chứng minh dữ liệu

Java reflection có `Field.getGenericType()`, `Method.getGenericReturnType()`, `ParameterizedType` và những API tương tự có thể xem metadata kiểu generic từ class file. Nhưng metadata `List<String>` trên một field chỉ mô tả **khai báo kiểu**, không khiến mọi giá trị được đưa vào List tự động vượt qua kiểm tra String ở runtime.

Nếu một List đã bị raw alias chèn Integer, đọc metadata khai báo vẫn cho List<String>; dữ liệu thực vẫn sai. Khi xử lý dữ liệu không tin cậy, phải xác minh phần tử hoặc dùng quá trình deserialize/convert có kiểm tra theo hợp đồng.

## 4. Giới hạn thứ ba: tạo mảng generic

### 4.1. Vì sao mảng cần kiểu phần tử runtime?

Mảng Java là *reified*: đối tượng array giữ component type thực tại runtime. Với:

```java
String[] strings = new String[2];
Object[] alias = strings;
alias[0] = "OK"; // Hợp lệ.
// alias[1] = 42; // ArrayStoreException khi chạy.
```

JVM biết mảng thực là `String[]` nên có thể chặn ghi Integer, dù biến `alias` có kiểu tĩnh `Object[]`. Đây là một bảo vệ runtime đặc trưng của mảng tham chiếu.

Nhưng `List<String>` không có class runtime riêng của List Integer, nên không có component type reified “List of String” để mảng kiểm tra sâu.

### 4.2. Hai cách tạo mảng bị cấm

**Tệp `GenericArrayCreation.java` — cố ý không biên dịch:**

```java
import java.util.List;

class GenericArrayCreation {
    List<String>[] items = new List<String>[2];

    static <T> T[] create(int n) {
        return new T[n];
    }
}
```

Biên dịch:

```bash
javac --release 17 GenericArrayCreation.java
```

Compiler đã được kiểm chứng từ chối cả hai dòng với:

```text
error: generic array creation
```

Ở đây:

- `new List<String>[2]` bị cấm vì `List<String>` là kiểu non-reifiable.
- `new T[n]` bị cấm vì T là type variable không cung cấp class runtime của loại phần tử cần tạo.

Không giải quyết bằng `@SuppressWarnings("unchecked")`: đây là lỗi biên dịch ở biểu thức tạo mảng, không chỉ là warning.

### 4.3. Những mảng có thể tạo

| Biểu thức | Hợp lệ? | Lý do |
| --- | --- | --- |
| `new String[3]` | Có | Component type String là reifiable. |
| `new int[3]` | Có | Mảng nguyên thủy có component runtime int. |
| `new List[3]` | Có | Raw List là reifiable, nhưng làm mất ràng buộc generic khi sử dụng. |
| `new List<?>[3]` | Có | Wildcard không giới hạn; List<?> là reifiable. |
| `new List<String>[3]` | Không | Generic argument String không được reify trong component List. |
| `new List<? extends Number>[3]` | Không | Kiểu parameterized này không phải loại reifiable với mọi argument wildcard không giới hạn. |
| `new T[3]` trong method generic | Không | Type variable không có class token cụ thể. |
| `new String[2][]` | Có | Mảng chiều đầu có hai slot, mỗi slot ban đầu null và mang kiểu String[]. |

Dù `List<?>[]` hợp lệ, mỗi phần tử của array có thể là `List<String>` hoặc `List<Integer>`. Runtime chỉ kiểm tra rằng object được ghi là một List, không kiểm tra loại phần tử của mỗi List.

### 4.4. Nguy cơ nếu cố lách bằng cast không an toàn

Một mẫu rủi ro:

```java
// KHÔNG NÊN DÙNG:
@SuppressWarnings("unchecked")
static <T> T[] fake(int n) {
    return (T[]) new Object[n];
}
```

Mảng thực là `Object[]`. Cast bên trong generic method không biến nó thành `String[]` khi caller yêu cầu String. Đến vị trí gán:

```java
// Khi gọi phương thức fake ở trên:
String[] values = fake(3);
```

runtime có thể ném `ClassCastException` vì Object[] không phải String[]. Xóa kiểu không có nghĩa mảng mất component type runtime.

Một lối lách khác tạo `new List<?>[n]` rồi ép kiểu sang `List<String>[]`. Cast này không xác minh được String trong từng List, nên cho phép heap pollution nếu có đường alias `Object[]` và code ghi `List<Integer>` vào slot. Không nên tạo API dựa vào những cast như vậy.

### 4.5. Tạo mảng bằng factory hoặc Class token

**Dùng `Class<T>`:**

```java
@SuppressWarnings("unchecked")
static <T> T[] newReferenceArray(Class<T> component, int length) {
    if (component.isPrimitive()) {
        throw new IllegalArgumentException("reference type required");
    }
    return (T[]) java.lang.reflect.Array.newInstance(component, length);
}
```

Caller truyền `String.class`, `Integer.class`, v.v. `Array.newInstance` thực sự tạo mảng có component runtime tương ứng. Helper này chủ động từ chối token primitive vì `int[]` không tương đương `Integer[]`.

Cast từ Object về T[] nằm ở một chỗ đã có lập luận an toàn: component token đầu vào được dùng trực tiếp để tạo array thực. Không áp dụng suy luận này cho cast một Object[] tùy ý sang T[].

**Dùng array factory:**

```java
static <T> T[] copyArray(
        Collection<? extends T> source,
        java.util.function.IntFunction<T[]> arrayFactory) {
    return source.toArray(arrayFactory.apply(0));
}
```

Với `String[]::new`, factory cung cấp runtime component type String:

```java
String[] words = copyArray(List.of("C", "D"), String[]::new);
```

Đoạn helper này nằm trong một lớp có import `Collection`, `List` như chương trình hoàn chỉnh. Với dữ liệu đã nằm trong Collection, cách ngắn hơn là gọi thẳng `list.toArray(String[]::new)`.

### 4.6. Khi không cần mảng, hãy dùng List

Nếu mục tiêu chỉ là lưu, thêm, xóa hoặc chuyển phần tử, `List<T>` thường phù hợp hơn `T[]`. Mảng có kích thước cố định, phải tạo component type runtime và có covariance dẫn tới array store check; List có hợp đồng generic compile-time thuận tiện hơn trong ứng dụng thông thường.

Không dùng mảng như một “bản List nhanh hơn” nếu chưa có yêu cầu API hay số liệu hiệu năng phù hợp.

## 5. Phân biệt `List<?>`, raw `List` và `List<Object>`

Ba kiểu này hay bị gộp nhầm khi sửa lỗi erasure:

| Kiểu | Ý nghĩa | An toàn thao tác ghi? |
| --- | --- | --- |
| `List<?>` | Một danh sách có kiểu phần tử chưa biết. | Không thêm được phần tử non-null tùy ý; chỉ đọc thành Object. |
| `List` | Raw type: bỏ tham số generic. | Compiler có thể cho lời gọi ghi với warning unchecked; dễ làm sai hợp đồng một tham chiếu typed khác. |
| `List<Object>` | Danh sách chủ ý chứa Object. | Có thể thêm các Object, nhưng `List<String>` không tự là subtype của nó. |
| `List<? extends Number>` | Phần tử là một subtype chưa biết của Number. | Đọc thành Number; không tự thêm Number tùy ý. |
| `List<? super Integer>` | Container có thể nhận Integer. | Thêm Integer được; đọc an toàn nói chung dưới Object. |

`List<?>` hữu ích cho `instanceof` khi chỉ cần hỏi một đối tượng có phải List không. Nó cũng được xem là reifiable trong phân loại của Java, nên `new List<?>[2]` hợp lệ. Tuy nhiên, ký hiệu wildcard **không tạo lớp List<?> riêng** và không tự xác thực phần tử trong mỗi List.

## 6. Chương trình Java 17 hoàn chỉnh: ba giới hạn và cách áp dụng

**Tệp `ErasureLimitsDemo.java`:**

```java
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.IntFunction;

public class ErasureLimitsDemo {
    static void logStrings(List<String> values) {
        System.out.println("strings=" + values);
    }

    static void logIntegers(List<Integer> values) {
        System.out.println("integers=" + values);
    }

    static <E> int count(List<E> values) {
        return values.size();
    }

    static boolean allStrings(Object value) {
        if (!(value instanceof List<?> list)) {
            return false;
        }
        for (Object element : list) {
            if (!(element instanceof String)) {
                return false;
            }
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    static <T> T[] newReferenceArray(Class<T> component, int length) {
        if (component.isPrimitive()) {
            throw new IllegalArgumentException("reference type required");
        }
        return (T[]) Array.newInstance(component, length);
    }

    static <T> T[] copyArray(
            Collection<? extends T> source,
            IntFunction<T[]> arrayFactory) {
        return source.toArray(arrayFactory.apply(0));
    }

    static <T> T checkedCast(Class<T> type, Object value) {
        return type.cast(value);
    }

    public static void main(String[] args) {
        List<String> words = List.of("A", "B");
        List<Integer> numbers = List.of(10, 20);
        logStrings(words);
        logIntegers(numbers);
        System.out.println("count=" + count(words));
        System.out.println("sameRuntimeClass="
                + (new ArrayList<String>().getClass()
                == new ArrayList<Integer>().getClass()));

        Object mixed = List.of("A", 42);
        System.out.println("instanceofWildcard=" + (mixed instanceof List<?>));
        System.out.println("mixedAllStrings=" + allStrings(mixed));
        System.out.println("wordsAllStrings=" + allStrings(words));

        List<?>[] groups = new List<?>[2];
        groups[0] = words;
        groups[1] = numbers;
        System.out.println("groupSizes=" + groups[0].size()
                + "," + groups[1].size());

        String[] allocated = newReferenceArray(String.class, 2);
        allocated[0] = "X";
        System.out.println("allocated=" + Arrays.toString(allocated));
        try {
            Object[] alias = allocated;
            alias[1] = 42;
        } catch (ArrayStoreException error) {
            System.out.println("arrayStore=" + error.getClass().getSimpleName());
        }

        String[] copied = copyArray(List.of("C", "D"), String[]::new);
        System.out.println("copied=" + Arrays.toString(copied));
        System.out.println("cast=" + checkedCast(String.class, "OK"));
    }
}
```

**Biên dịch và chạy:**

```bash
javac --release 17 -Xlint:all ErasureLimitsDemo.java
java ErasureLimitsDemo
```

**Stdout đã kiểm chứng bằng chương trình này:**

```text
strings=[A, B]
integers=[10, 20]
count=2
sameRuntimeClass=true
instanceofWildcard=true
mixedAllStrings=false
wordsAllStrings=true
groupSizes=2,2
allocated=[X, null]
arrayStore=ArrayStoreException
copied=[C, D]
cast=OK
```

Trong phép thử đã thực hiện, `javac --release 17 -Xlint:all` không sinh cảnh báo cho chương trình trên.

### 6.1. Hai method khác tên thay cho overload bị trùng erasure

`logStrings` và `logIntegers` có thể cùng tồn tại vì **khác tên**. Phương thức `count(List<E>)` minh họa một thuật toán áp dụng chung khi không cần phân biệt kiểu phần tử.

### 6.2. Hai ArrayList có cùng runtime class

Biểu thức so sánh `getClass()` in true; generic argument String/Integer không tạo hai class ArrayList khác nhau ở runtime. Điều này giải thích nguyên nhân chung phía sau các giới hạn của bài.

### 6.3. Kiểm tra List container và từng phần tử là hai bước

`mixed instanceof List<?>` trả true vì `mixed` là List. Nhưng `allStrings(mixed)` trả false khi gặp Integer, còn `allStrings(words)` trả true.

Không dùng `instanceof List<String>` để thay phép duyệt kiểu thực trong tình huống nhận Object từ bên ngoài.

### 6.4. Mảng wildcard hợp lệ nhưng không ép các nhóm cùng kiểu

`new List<?>[2]` cho phép đặt `List<String>` ở slot đầu và `List<Integer>` ở slot thứ hai. Tất cả đều đúng hợp đồng List<?>. Dòng `groupSizes=2,2` chỉ kiểm tra kích thước, không hề đọc phần tử String/Integer.

### 6.5. Mảng String thật vẫn có array store check

`Array.newInstance(String.class, 2)` tạo String[]. Khi alias `Object[]` cố ghi Integer, JVM ném `ArrayStoreException`. Đó là cơ chế runtime của mảng, không mâu thuẫn với việc type argument của generic List bị erasure.

### 6.6. Array factory giữ component type chính xác

`String[]::new` cho `copyArray` biết cần String[] thật. Sau khi copy, chương trình in `copied=[C, D]`. Không có phép `(String[]) new Object[n]` sai bản chất.

## 7. Tóm tắt các ví dụ cố ý không biên dịch

Các tệp dưới đây phải lưu/biên dịch **riêng**, không ghép chúng với chương trình hợp lệ ở phần 6.

| Tệp | Thao tác không hợp lệ | Thông báo trọng tâm của javac |
| --- | --- | --- |
| `OverloadErasureClash.java` | `process(List<String>)` cùng `process(List<Integer>)` | `name clash ... same erasure`. |
| `OverloadBoundClash.java` | `take(Number)` cùng `<T extends Number> take(T)` | `same erasure`. |
| `InstanceofParameterized.java` | `Object value instanceof List<String>` | `Object cannot be safely cast to List<String>`. |
| `InstanceofTypeVariable.java` | `Object value instanceof T` | `Object cannot be safely cast to T`. |
| `GenericArrayCreation.java` | `new List<String>[2]`, `new T[n]` | `generic array creation`. |

Đây là những trường hợp lỗi compiler được tái hiện với `javac --release 17`. Thông báo chi tiết và vị trí dấu mũ có thể thay đổi theo phiên bản trình biên dịch, nhưng loại vi phạm nêu ở bảng là điểm cần hiểu.

## 8. Những cách sửa sai thường gặp

### Sai lầm 1: ép toàn bộ tham số List về raw type để overload

Bạn không thể tạo hai overload cùng tên có tham số raw `List` mà vẫn phân biệt được String/Integer theo nội dung; sau erasure chúng vẫn cùng chữ ký. Bỏ generic chỉ khiến code mất kiểm tra phần tử mà không giải quyết thiết kế API.

### Sai lầm 2: dùng cast `(List<String>) value` như phép xác thực

Một cast generic không thể kiểm tra đầy đủ phần tử List ở runtime. Thường JVM chỉ xác minh container là List, còn phần tử có thể sai kiểu. Nếu dữ liệu không tin cậy, duyệt bằng `List<?>` rồi xác thực từng phần tử; có thể tạo bản sao sau khi xác thực để tránh alias bị sửa về sau.

### Sai lầm 3: coi `instanceof List<?>` nghĩa là “chứa String”

Nó chỉ xác nhận đối tượng thuộc loại List. Nó trả true cả với danh sách Integer hoặc danh sách hỗn hợp.

### Sai lầm 4: cho rằng `T.class` hoặc `new T[n]` có thể dùng tự do

Type variable không tự cung cấp runtime class token. Nhận `Class<T>` hoặc factory từ caller nếu thật sự cần kiểm tra/tạo phần tử kiểu cụ thể.

### Sai lầm 5: dùng `(T[]) new Object[n]`

Cast không thay đổi runtime component type Object của mảng. Caller mong String[] có thể gặp ClassCastException khi nhận kết quả.

### Sai lầm 6: tạo `List<?>[]` rồi cast thành `List<String>[]`

Mảng chỉ kiểm tra component List; cast non-reifiable không bổ sung quy tắc String cho phần tử bên trong. Nếu bị ghi List<Integer> qua Object[] alias, có thể gây heap pollution và CCE muộn.

### Sai lầm 7: thêm `@SuppressWarnings("unchecked")` để “sửa lỗi”

Annotation chỉ giảm cảnh báo, không tạo runtime proof hay thay đổi dữ liệu. Nó cũng không thể biến lỗi compiler như `generic array creation` thành mã hợp lệ.

### Sai lầm 8: khẳng định Java 17 cấm mọi biểu thức `instanceof List<String>`

Cần xét kiểu tĩnh của toán hạng và quy tắc cast conversion: một vài trường hợp như `List<String> x; x instanceof List<String>` có thể biên dịch. Nhưng nó không phải phép kiểm tra sâu dữ liệu; tránh dùng nó để xác thực giá trị `Object` chưa rõ nguồn.

## 9. Cách lựa chọn giải pháp theo nhu cầu

| Nhu cầu thực tế | Cách tiếp cận |
| --- | --- |
| Hai hành vi xử lý List String/Integer khác nhau | Đặt tên method khác hoặc thiết kế tham số có khác biệt thật sau erasure. |
| Cùng thuật toán cho mọi List | `<E> method(List<E>)` hoặc `method(List<?>)` khi không cần liên kết E. |
| Kiểm tra một Object có phải List | `value instanceof List<?>` hoặc `List.class.isInstance(value)`. |
| Kiểm tra List chỉ chứa String | Duyệt `List<?>` và `instanceof String` từng phần tử; kiểm soát alias nếu cần. |
| Kiểm tra một Object là String | `value instanceof String`, hoặc `String.class.isInstance(value)`. |
| Method generic cần runtime Class<T> | Caller truyền `Class<T>`; dùng `type.cast(value)` khi phù hợp. |
| Cần `T[]` với component type runtime thật | Caller truyền `Class<T>` và dùng `Array.newInstance`, hoặc truyền `IntFunction<T[]>`. |
| Đổi Collection sang typed array | `collection.toArray(String[]::new)` hoặc overload tương đương. |
| Cần nhiều danh sách generic lồng nhau | Cân nhắc `List<List<T>>` thay vì mảng non-reifiable. |
| Chỉ cần quan sát nhiều List khác kiểu | `List<?>[]` hoặc `List<List<?>>` tùy quyền sở hữu và mutability. |

Đừng chọn mẹo cast chỉ để compiler hết báo lỗi. Cần làm rõ **giới hạn nằm ở overload resolution, ở phép thử runtime, hay ở yêu cầu component type khi tạo mảng**, từ đó thay đổi contract đúng chỗ.

## 10. Quy trình chẩn đoán nhanh

Khi gặp lỗi liên quan erasure:

1. **Đọc đúng thông điệp:** `same erasure`, `cannot be safely cast`, hay `generic array creation`?
2. **Xác định vị trí compile-time/runtime:** đây là lỗi javac hay exception từ JVM khi chạy?
3. **Viết erasure của các tham số:** ví dụ `List<String> → List`, `T extends Number → Number`.
4. **Với overload:** kiểm tra hai danh sách tham số còn khác nhau sau erasure không; nếu không, đổi thiết kế.
5. **Với instanceof:** hỏi JVM có đủ thông tin về kiểu được thử không. Với List<String> từ Object, kiểm tra List<?> rồi duyệt phần tử.
6. **Với array:** hỏi mảng cần component type reifiable nào ở runtime. Dùng factory/token hoặc List.
7. **Đánh giá dữ liệu không đáng tin:** raw type, unchecked cast, reflection và dữ liệu legacy có thể vượt kiểm tra compile-time.
8. **Viết test đúng ranh giới:** compile các ví dụ cấm để thấy lỗi; chạy trường hợp hợp lệ để quan sát output và array store check.
9. **Không triệt tiêu cảnh báo mà chưa hiểu:** warning có thể báo vị trí gây heap pollution và ClassCastException muộn.
10. **Giữ API đơn giản:** người gọi thường nên nhìn thấy hợp đồng rõ ràng thay vì những cast không thể kiểm chứng.

## 11. Bài tập tự kiểm tra

### Câu 1

Có thể viết cả `void save(List<String> x)` và `void save(List<Integer> x)` trong cùng lớp không?

**Đáp án:** Không. Cả hai thành `save(List)` sau erasure, compiler báo name clash.

### Câu 2

Vì sao `void save(List<String> x, int kind)` và `void save(List<Integer> x, String category)` có thể cùng tồn tại?

**Đáp án:** Erasure cho hai danh sách tham số là `(List, int)` và `(List, String)`, khác nhau ở tham số thứ hai. Tuy nhiên cần cân nhắc sự rõ nghĩa khi thiết kế API.

### Câu 3

`<T extends Number> void read(T x)` có thể overload với `void read(Number x)` không?

**Đáp án:** Không trong cùng ngữ cảnh overload ấy; T erases to Number nên cùng erasure.

### Câu 4

`Object obj = List.of(1, 2); obj instanceof List<?>` cho kết quả gì?

**Đáp án:** True, vì obj là List; wildcard không yêu cầu mọi phần tử thuộc String hay kiểu cụ thể khác.

### Câu 5

Có thể dùng `obj instanceof List<String>` để đảm bảo mỗi phần tử là String khi obj có kiểu tĩnh Object không?

**Đáp án:** Không. Phép thử ấy bị compiler từ chối trong ví dụ Object, và runtime không reify type argument String. Kiểm tra List<?> rồi duyệt phần tử.

### Câu 6

Tại sao `List<String> list; list instanceof List<String>` có thể biên dịch trên Java 17?

**Đáp án:** Vì kiểu tĩnh đã đủ cụ thể để xét phép kiểm tra, không phải do JVM có khả năng kiểm tra sâu từng phần tử. Nó không thích hợp cho xác thực dữ liệu không tin cậy.

### Câu 7

Vì sao `new List<String>[5]` bị cấm nhưng `new List<?>[5]` hợp lệ?

**Đáp án:** List<String> không reifiable còn List<?> reifiable khi tất cả đối số là wildcard không giới hạn. Runtime component của List<?>[] vẫn chỉ là List.

### Câu 8

Làm sao viết method generic cấp phát mảng String[] thực?

**Đáp án:** Truyền `String.class` cho helper dùng `Array.newInstance` hoặc truyền `String[]::new` cho helper nhận `IntFunction<T[]>`; không dùng `new T[n]`.

### Câu 9

Một List đã bị raw alias thêm Integer có thể khiến `String s = list.get(0)` báo gì?

**Đáp án:** Có thể ném ClassCastException tại cast do compiler chèn khi caller yêu cầu String. Đây là runtime failure, khác với lỗi generic array creation ở compile time.

### Câu 10

Nên xử lý thế nào nếu hai overload chỉ khác kiểu generic nhưng cùng một thuật toán?

**Đáp án:** Gộp thành một generic method như `<E> int count(List<E>)` hoặc nhận `List<?>` nếu chỉ quan sát. Nếu hai hành vi thật sự khác nhau, dùng tên phương thức phân biệt nghiệp vụ.

## 12. Ghi nhớ

- **Erasure** loại bỏ nhiều type argument của generic khỏi kiểu runtime; `List<String>` và `List<Integer>` có cùng erasure là `List`.
- **Overload** không được chỉ khác nhau ở những type argument đã bị xóa, vì sẽ dẫn đến `name clash`.
- **`instanceof`** không phải công cụ chứng minh mọi phần tử của `List<String>` từ một `Object` là String; `List<?>` là phép thử container, kiểm tra nội dung cần thêm bước.
- **Mảng** phải có component type reifiable để JVM kiểm tra ở runtime; `new T[n]` và `new List<String>[n]` bị cấm.
- **Class token/factory** cung cấp runtime component type cần thiết khi thực sự phải tạo `T[]`.
- **Raw type/unchecked cast** có thể vượt an toàn generic và gây lỗi muộn, không phải cách sửa những giới hạn trên.

Để thiết kế Java API an toàn và dễ hiểu, hãy xác định phần nào của kiểu tồn tại ở compile time, phần nào có thể kiểm tra ở runtime và **đặt hợp đồng tại đúng ranh giới** thay vì cố ép JVM nhận biết type argument mà erasure không giữ lại.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="generics-language-limits-009">Phân biệt giới hạn ngôn ngữ với giới hạn của một API cụ thể</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt giới hạn ngôn ngữ với giới hạn của một API cụ thể trong Java Generics

Khi một đoạn Java liên quan đến generics không biên dịch hoặc chạy đến một lời gọi rồi ném exception, câu hỏi đầu tiên không nên là “Generics của Java có hạn chế này sao?” mà là: **giới hạn đến từ bản thân ngôn ngữ Java, từ chữ ký/kiểu mà tác giả API lựa chọn, hay từ hợp đồng thực thi của một API/implementation cụ thể?**

Ba tầng này liên quan nhưng không đồng nhất:

1. **Quy tắc ngôn ngữ (language rules):** Java Language Specification (JLS) xác định mã nào hợp lệ, chẳng hạn generics bất biến, không dùng primitive type arguments trong generic thông thường, không tạo `new T[]`, và không dùng type parameter của lớp trong static context. Viết sai sẽ bị compiler từ chối bất kể bạn dùng thư viện nào.
2. **Ràng buộc ở chữ ký API (API type contract):** tác giả thư viện chọn `List<? extends Number>`, `<T extends Number>`, `Consumer<? super T>` hay một overload cụ thể. Compiler áp dụng những ràng buộc này theo quy tắc của Java, nhưng **việc chọn chữ ký** là lựa chọn thiết kế của API, không phải luật mọi generic API bắt buộc phải nhận riêng `Number`.
3. **Hợp đồng runtime của API/implementation:** `List.of` không cho phép phần tử `null` và không hỗ trợ sửa đổi; `ArrayList` có thể thêm phần tử, kể cả `null`; `Arrays.asList` cho phép `set` nhưng không cho thay đổi kích thước. Những khác biệt này thường chỉ lộ ra khi thực thi, dù biến đều có kiểu `List<String>`.

Nắm được ranh giới trên giúp chọn phương pháp khắc phục đúng: **không cố dùng cast để lách một lỗi ngôn ngữ**, cũng không cho rằng mọi lỗi `UnsupportedOperationException` hoặc `NullPointerException` đều do generic type erasure.

## 1. Bản đồ phân loại: lỗi nằm ở tầng nào?

| Tình huống | Tầng chính | Biểu hiện đặc trưng | Hướng xử lý |
| --- | --- | --- | --- |
| `List<int>` | Ngôn ngữ | Lỗi biên dịch: type argument phải là kiểu tham chiếu | Dùng `List<Integer>`, hoặc mảng `int[]` nếu phù hợp |
| `List<String>` gán vào `List<Object>` | Ngôn ngữ | Lỗi biên dịch do invariance | Chọn wildcard/API khác đúng hướng đọc-ghi |
| `new T[10]` với `T` generic | Ngôn ngữ | Lỗi `generic array creation` | Dùng `List<T>`, token hoặc array factory |
| `value instanceof List<String>` với `value` kiểu `Object` | Ngôn ngữ và erasure | Không thể kiểm tra type argument như mong muốn | `instanceof List<?>`, rồi xác thực phần tử |
| `static T cache` trong `class Box<T>` | Ngôn ngữ | Lỗi do `T` lớp không được dùng trong static context | Instance field hoặc static method type parameter riêng |
| Truyền `String` vào hàm khai báo `<T extends Number>` | Chữ ký API | Lỗi biên dịch do bound mà API đặt | Cung cấp `Number` hoặc dùng API có hợp đồng khác |
| `new ArrayList<String>().add(null)` | Hợp đồng API | Được phép đối với `ArrayList` | Quy định riêng null policy nếu cần |
| `List.of("A", (String) null)` | Hợp đồng API | `NullPointerException` | Không truyền null hoặc chọn API phù hợp |
| `List.of("A").add("B")` | Hợp đồng API | `UnsupportedOperationException` | Tạo collection mutable nếu cần thêm |
| `Arrays.asList("A","B").set(0,"X")` | Hợp đồng API | Thành công | Ghi đè được phần tử nhưng không tăng kích thước |
| `Arrays.asList("A","B").add("C")` | Hợp đồng API | `UnsupportedOperationException` | Dùng `new ArrayList<>(...)` |
| Ghi giá trị sai kiểu qua `Collections.checkedList` bằng raw alias | Runtime guard do API cung cấp | `ClassCastException` tại thao tác ghi | Tránh raw alias, duy trì kiểu đúng |
| `List<String> names = raw;` | Ngôn ngữ/generic conversion | Có thể biên dịch với `unchecked warning` | Xác minh dữ liệu và hạn chế raw type |

**Không suy luận chỉ từ tên exception:** một `NullPointerException` cũng có thể do lỗi lập trình thông thường, không phải API cấm null; `ClassCastException` có thể đến từ cast thông thường, heap pollution hoặc wrapper kiểm tra kiểu. Luôn nhìn cả **điều kiện hợp đồng** và **vị trí phát sinh**.

## 2. Quy tắc ngôn ngữ áp dụng bất kể API bên ngoài

**Giới hạn ngôn ngữ** là ràng buộc của Java compiler/JLS, không thay đổi khi bạn đổi từ `ArrayList` sang `LinkedList` hay thư viện của công ty. Hãy dùng **dấu hiệu compile-time** để xác định:

| Trường hợp | Vì sao là quy tắc ngôn ngữ? | Hướng thay thế |
|---|---|---|
| `List<int>` | Generic type argument thông thường phải là reference type | `List<Integer>`, `int[]`, `IntStream` |
| `List<String>` → `List<Object>` | Generic type arguments invariant | Dùng `List<?>` để quan sát, wildcard PECS khi cần đọc/ghi |
| `new T[10]` | Java cấm tạo generic array có component non-reifiable | `List<T>`, `IntFunction<T[]>`, token/factory thích hợp |
| `obj instanceof List<String>` với `obj: Object` | Runtime không xác minh type argument đã erasure | `instanceof List<?>`, kiểm tra phần tử |
| `static T last;` trong `class Box<T>` | Type variable lớp không dùng trực tiếp ở static context | Instance field hoặc static `<U>` method |

Ví dụ nhỏ (đặt trong method với imports phù hợp):

```java
List<String> names = new ArrayList<>();
List<?> view = names;
Object first = view.isEmpty() ? null : view.get(0);
```

`List<?>` **không phải** `List<Object>`: nó cho phép xem danh sách chưa biết type argument mà không cho thêm non-null tùy ý. Tương tự, `String.class` có class token thật nhưng `List<String>.class` không tồn tại; một class token `List.class` không xác thực sâu element type.

Điểm quan trọng để phân loại sự cố: nếu *bất kỳ implementation* `List` nào cũng không giúp đoạn `List<int>` biên dịch, bạn đang gặp luật ngôn ngữ. Trái lại, cùng một `List<String>` có thể `add` thành công, ném `UnsupportedOperationException` hoặc từ chối `null` tùy API. **Đừng nhầm lỗi do type checker với hành vi runtime do implementation/factory quy định.**

## 3. Ràng buộc ở chữ ký API: compiler kiểm tra, nhưng tác giả API chọn hợp đồng

Đây là nhóm dễ bị nhầm thành “Java generic cấm kiểu X”. Xét hai phương thức do người viết API tự tạo:

```java
// Hai phương thức độc lập, có thể cùng tồn tại trong một class.
static <T> T first(java.util.List<T> items) {
    return items.get(0);
}

static <T extends Number> double twice(T number) {
    return number.doubleValue() * 2;
}
```

`first` nhận `List<T>` với `T` là kiểu tham chiếu bất kỳ phù hợp. `twice` giới hạn `T extends Number`; compiler chỉ chấp nhận giá trị có kiểu suy luận phù hợp bound.

```java
record Ticket(int id) {}

Ticket ticket = first(java.util.List.of(new Ticket(7))); // Hợp lệ
double value = twice(3); // Hợp lệ, 3 boxing thành Integer
// twice("3"); // KHÔNG BIÊN DỊCH
```

Điều quan trọng:

- **Luật ngôn ngữ** cho phép định nghĩa generic method với hoặc không có upper bound.
- **Tác giả API** chọn bound `Number` vì muốn gọi `doubleValue()`; compiler sau đó thực thi bound đó theo các quy tắc Java.
- Lỗi `twice("3")` **không** có nghĩa Java cấm dùng `String` với tất cả generic methods. `first(List<String>)` vẫn hợp lệ.

Trong thư viện Java, các chữ ký như `Collections.max(Collection<? extends T>)`, `Comparator<? super T>`, `Function<? super T, ? extends R>` hoặc `Stream<T>` phản ánh những **lựa chọn hợp đồng và quan hệ kiểu cần thiết cho thao tác** của API cụ thể. Chúng vẫn tuân thủ JLS nhưng không biến thành luật rằng mọi generic API phải mang chính xác những wildcard/bounds ấy.

### Chữ ký API và hành vi runtime là hai lớp khác nhau

Một phương thức nhận `List<String>` chưa chắc **được phép sửa** list đó. Chữ ký `List<String>` cho biết **kiểu phần tử dùng ở compile time**, không cho biết list có mutable hay không, có được chứa null hay không, có giới hạn dung lượng hay không.

Tương tự, `List<? extends Number>` có thể cho phép đọc giá trị như `Number`, nhưng sẽ không cho gọi `add(Integer)` thông thường do kiểu wildcard capture chưa xác định. Đây là hạn chế compile-time của kiểu tham số hóa **do API đã chọn**; nếu runtime list đồng thời không sửa được, còn có thêm một hạn chế runtime độc lập.

## 4. Cùng kiểu `List<String>`, ba implementation có hành vi khác nhau

Đây là phản ví dụ rõ nhất cho nhầm lẫn “một kiểu generic luôn quy định cùng khả năng sửa đổi”:

```java
java.util.List<String> mutable = new java.util.ArrayList<>();
java.util.List<String> fixed = java.util.Arrays.asList("A", "B");
java.util.List<String> immutable = java.util.List.of("A", "B");
```

Cả ba biến đều có kiểu tĩnh `List<String>`, do đó compiler biết phần tử có kiểu `String`, có các phương thức `get`, `set`, `add` trong interface `List`. Nhưng implementation/hợp đồng không giống nhau:

| Hành vi | `ArrayList` | `Arrays.asList` | `List.of` |
| --- | --- | --- | --- |
| `get(index)` | Có | Có | Có |
| `set(index, value)` | Có | Có | Không hỗ trợ |
| `add(value)` thay đổi kích thước | Có | Không hỗ trợ | Không hỗ trợ |
| `remove(...)` thay đổi kích thước | Có | Không hỗ trợ | Không hỗ trợ |
| Có thể chứa `null` | Có | Có nếu mảng/phần tử nguồn có null | Không |
| Có runtime exception khi gọi thao tác không hỗ trợ? | Có thể cho thao tác sai điều kiện như chỉ số | `UnsupportedOperationException` khi thêm/xóa | `UnsupportedOperationException` khi sửa |
| Có bản sao riêng với array đầu vào? | Tùy constructor | Là view kích thước cố định được backing bởi array đã cung cấp | Factory tạo unmodifiable list theo hợp đồng |

`Arrays.asList` không trả một `java.util.ArrayList` thông thường: danh sách kích thước cố định gắn với một array. Vì vậy `set` được hỗ trợ nhưng thêm/xóa cấu trúc thì không.

`List.of` là factory tạo **unmodifiable list** không chứa null. Không suy ra “unmodifiable” nghĩa mọi object phần tử đều immutable: nếu phần tử tự nó là object mutable, trạng thái nội bộ của object đó vẫn có thể thay đổi từ nơi khác.

### Tại sao gọi `add` được compiler chấp nhận nhưng runtime từ chối?

Vì chữ ký của `List<E>` có method `boolean add(E e)`; compiler chỉ kiểm tra kiểu tham số `E = String` và tính hợp lệ của lời gọi ở mức ngôn ngữ. Nó không biết chỉ từ biến `List<String>` rằng **đối tượng thực** đang được dùng là implementation unmodifiable.

```java
java.util.List<String> list = java.util.List.of("An");

// Compiler cho phép viết lời gọi vì List<String> có add(String):
// list.add("Binh"); // Runtime: UnsupportedOperationException
```

Dòng có chú thích không cần đưa vào chương trình chạy an toàn; ở demo sẽ bắt exception để minh họa chính xác.

## 5. `null` có phải bị Java Generics cấm không?

**Không.** `List<String>` không có nghĩa “danh sách chỉ nhận các `String` non-null”. Kiểu `String` là reference type; `null` là giá trị có thể gán cho các kiểu tham chiếu theo quy tắc ngôn ngữ, trừ khi API hoặc công cụ phân tích bổ sung đặt chính sách chặt hơn.

Hai đoạn sau có cùng type argument `String`, nhưng hành vi khác nhau:

```java
java.util.List<String> a = new java.util.ArrayList<>();
a.add(null); // Hợp lệ và được ArrayList hỗ trợ

// java.util.List<String> b = java.util.List.of((String) null);
// Runtime: NullPointerException
```

`List.of` thiết kế để từ chối các phần tử null; `ArrayList` chấp nhận chúng. Câu trả lời đúng không phải “generics cấm null”, mà là **API factory `List.of` đặt một hợp đồng null policy cụ thể**.

So sánh thêm:

- `List.copyOf(collection)` tạo unmodifiable list theo hợp đồng và **từ chối phần tử null**.
- `Arrays.asList` cho phép một phần tử null vì list backing của nó có thể chứa null.
- `Objects.requireNonNull(value)` có thể được dùng trong bất kỳ API tự viết nào để áp dụng chính sách non-null, không cần thay đổi generic type argument.

Nếu codebase dùng nullness annotations hoặc các trình phân tích ngoài javac, chúng có thể bổ sung cảnh báo/kiểm tra static ở mức dự án. Đó là **bộ quy tắc bổ sung**, không có nghĩa bản thân `List<String>` của Java tiêu chuẩn đương nhiên cấm null.

## 6. Unmodifiable view khác bản sao: giới hạn cũng là hợp đồng API

Cùng một kiểu tĩnh `List<String>`:

```java
java.util.List<String> source =
        new java.util.ArrayList<>(java.util.List.of("one"));

java.util.List<String> view =
        java.util.Collections.unmodifiableList(source);

java.util.List<String> snapshot =
        java.util.List.copyOf(source);

source.add("two");
System.out.println(view);     // [one, two]
System.out.println(snapshot); // [one]
```

**`Collections.unmodifiableList(source)`** tạo một *view không cho sửa qua view*. Caller không thể gọi `view.add("x")`, nhưng vì view vẫn nhìn vào source, thay đổi trên `source` có thể xuất hiện khi đọc `view`.

**`List.copyOf(source)`** cung cấp kết quả unmodifiable và, với mutable source trong ví dụ, là một snapshot độc lập về **cấu trúc** tại thời điểm sao chép. Sau khi `source.add("two")`, `snapshot` không có phần tử mới.

Cần giữ hai sắc thái:

1. **`List.copyOf` không cam kết mọi lần gọi đều tạo object mới**: nếu nguồn đã là unmodifiable list phù hợp, implementation có thể tái sử dụng object. Đừng dùng `a != b` làm định nghĩa của việc copy.
2. Cả view và snapshot đều **shallow** theo nghĩa không tự clone trạng thái bên trong từng object phần tử mutable. Unmodifiable list không đồng nghĩa immutable object graph.

Đây là khác biệt ở **hợp đồng và tính sở hữu dữ liệu**, không phải sự khác nhau giữa `List<String>` và một dạng generics khác.

## 7. `Collections.checkedList`: API bổ sung runtime type check

Java Generics chủ yếu bảo đảm kiểu ở compile time. Nếu phải tương tác với raw types hoặc mã cũ, `Collections.checkedList` có thể tạo một wrapper kiểm tra class của phần tử **khi phần tử được thêm qua wrapper**:

```java
java.util.List<String> checked =
        java.util.Collections.checkedList(
                new java.util.ArrayList<>(), String.class);

checked.add("ok");

java.util.List raw = checked;
// raw.add(42); // Có unchecked warning; wrapper ném ClassCastException
```

Sự khác nhau:

- **Luật ngôn ngữ:** `List<String>.add(Integer)` bị compiler từ chối ở code typed thông thường; raw alias có thể bỏ qua một phần kiểm tra với warning.
- **Hợp đồng `checkedList`:** wrapper dùng `String.class` để kiểm tra phần tử khi code thao tác **qua wrapper đó**, kể cả lời gọi qua raw alias, và từ chối `Integer`.
- **Giới hạn API:** wrapper không tự duyệt xác minh toàn bộ dữ liệu đã có trước khi bọc và không ngăn một alias khác sửa trực tiếp backing list nếu alias đó vẫn còn.

`checkedList` cũng không biến `List<String>` thành reified generic type trong JVM. Nó thực thi **một runtime guard do thư viện chọn cài đặt** tại các điểm thao tác mà wrapper kiểm soát.

Nếu hệ thống tiếp nhận dữ liệu `Object` không đáng tin, cần kiểm tra từng giá trị trước khi coi nó là `String` — không nên chỉ ép `(List<String>) input` rồi tin vào đối số generic đã bị xóa kiểu.

## 8. Ví dụ Java 17+ chạy được: nhìn cả ba tầng giới hạn

Lưu mã vào **`LanguageVsApiDemo.java`**:

```java
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class LanguageVsApiDemo {
    record Ticket(int id) {}

    static <T> T first(List<T> list) {
        return list.get(0);
    }

    // Bound Number là một lựa chọn chữ ký API này.
    static <T extends Number> double twice(T value) {
        return value.doubleValue() * 2;
    }

    public static void main(String[] args) {
        List<String> mutable = new ArrayList<>();
        mutable.add("An");
        mutable.add(null);
        System.out.println("ArrayList allows null: " + mutable);

        List<String> fixed = Arrays.asList("A", "B");
        fixed.set(0, "X");
        System.out.println("Arrays.asList allows set: " + fixed);
        try {
            fixed.add("C");
        } catch (UnsupportedOperationException ex) {
            System.out.println(
                    "Arrays.asList add: UnsupportedOperationException");
        }

        List<String> immutable = List.of("A", "B");
        try {
            immutable.set(0, "X");
        } catch (UnsupportedOperationException ex) {
            System.out.println(
                    "List.of set: UnsupportedOperationException");
        }
        try {
            List.of("A", (String) null);
        } catch (NullPointerException ex) {
            System.out.println("List.of null: NullPointerException");
        }

        List<String> source = new ArrayList<>(List.of("one"));
        List<String> view = Collections.unmodifiableList(source);
        List<String> copy = List.copyOf(source);
        source.add("two");

        System.out.println("Unmodifiable view: " + view);
        System.out.println("List.copyOf snapshot: " + copy);

        List<?> unknown = source;
        System.out.println("Wildcard can read: " + unknown.get(0));

        List<String> checked = Collections.checkedList(
                new ArrayList<>(), String.class);
        checked.add("safe");

        List raw = checked;
        try {
            raw.add(42); // Cố ý minh họa unchecked call
        } catch (ClassCastException ex) {
            System.out.println(
                    "CheckedList raw add: ClassCastException");
        }

        System.out.println("Generic first accepts Ticket: "
                + first(List.of(new Ticket(7))).id());
        System.out.println("Bounded method accepts Integer: "
                + twice(3));
    }
}
```

Biên dịch và chạy với JDK 17+:

```bash
javac -encoding UTF-8 -Xlint:rawtypes -Xlint:unchecked LanguageVsApiDemo.java
java LanguageVsApiDemo
```

Đầu ra chuẩn (không gồm cảnh báo compiler):

```text
ArrayList allows null: [An, null]
Arrays.asList allows set: [X, B]
Arrays.asList add: UnsupportedOperationException
List.of set: UnsupportedOperationException
List.of null: NullPointerException
Unmodifiable view: [one, two]
List.copyOf snapshot: [one]
Wildcard can read: one
CheckedList raw add: ClassCastException
Generic first accepts Ticket: 7
Bounded method accepts Integer: 6.0
```

Diễn giải theo tầng:

1. `mutable.add(null)` được **ngôn ngữ** chấp nhận và **ArrayList API** hỗ trợ.
2. `fixed.set(0, "X")` thành công vì `Arrays.asList` hỗ trợ thay giá trị phần tử; `fixed.add` bị từ chối vì danh sách có kích thước cố định theo **hợp đồng API**.
3. `List.of` từ chối cả `set` và giá trị `null`, nhưng không có lỗi generic syntax nào khi viết các lời gọi đó.
4. `view` theo dõi nội dung source, còn `copy` tạo kết quả không nhìn thấy phần tử thêm sau đó từ mutable source.
5. `List<?> unknown` được compiler chấp nhận để đọc mọi phần tử như `Object`, minh họa quy tắc generic wildcard **ở ngôn ngữ**.
6. Raw alias `List raw` có thể gọi `add(42)` với **unchecked warning**; `checkedList` dùng runtime guard của API để ném `ClassCastException`.
7. `first` chấp nhận record `Ticket` vì tác giả phương thức không ràng buộc `T`; `twice` yêu cầu `Number` vì tác giả đã chọn bound trong chữ ký.

Chương trình độc lập này cố ý tạo một raw alias để so sánh cơ chế runtime của `checkedList`; không dùng raw type như mẫu triển khai trong mã nghiệp vụ.

## 9. Một kiểu `List<String>` không mô tả toàn bộ hành vi

Có thể phân loại thông tin kiểu và hành vi của một `List<String>`:

**Những điều kiểu tĩnh cho biết:** phần tử được coi là `String` trong các thao tác typed bình thường; compiler từ chối `add(Integer)`; `get(i)` có kiểu `String`; có thể dùng các method được interface `List` khai báo theo điều kiện ngôn ngữ.

**Những điều kiểu tĩnh không tự cho biết:** implementation có mutable không; `add` có được hỗ trợ không; null có được chấp nhận không; list có thread-safe không; có giới hạn dung lượng không; view có phản ánh thay đổi của nguồn không; có snapshot không; phương thức có chi phí thuật toán nào.

Vì vậy, chữ ký `List<String>` không đủ để đảm bảo một phương thức sau đây luôn có thể thêm phần tử:

```java
static void appendName(List<String> names) {
    names.add("new");
}
```

Method này **biên dịch được**, nhưng nếu caller đưa `List.of("A")` hoặc `Arrays.asList("A")`, runtime có thể ném `UnsupportedOperationException`.

Tác giả API phải **tài liệu hóa yêu cầu** (ví dụ “tham số phải là mutable list hỗ trợ structural modification”), hoặc thiết kế để không cần sửa tham số:

```java
static List<String> appendedCopy(List<String> names, String next) {
    List<String> result = new java.util.ArrayList<>(names);
    result.add(next);
    return result;
}
```

Cách thứ hai trả danh sách mới và không đòi input là modifiable; nhưng vẫn cần xét null policy, size, nguồn thay đổi đồng thời và các yêu cầu nghiệp vụ khác. Nó là một **lựa chọn hợp đồng API**, không phải hệ quả tự động từ generics.

## 10. Khi nào chữ ký API thất bại lúc biên dịch?

Không phải lỗi compile-time nào cũng chỉ thuần là “hạn chế ngôn ngữ bất biến áp dụng với mọi API”. Nếu phương thức tự khai báo bound:

```java
static <T extends Comparable<? super T>> T greatest(
        java.util.List<T> items) {
    T best = items.get(0);
    for (T next : items) {
        if (next.compareTo(best) > 0) {
            best = next;
        }
    }
    return best;
}
```

Ở đây API lựa chọn yêu cầu `T` có thể so sánh tương thích `Comparable<? super T>`. Nếu truyền một list của type không đáp ứng bound, compiler báo lỗi. Nhưng bạn hoàn toàn có thể viết một method khác không cần so sánh:

```java
static <T> int count(java.util.List<T> items) {
    return items.size();
}
```

`count` nhận nhiều kiểu `T` hơn vì thuật toán không cần quan hệ thứ tự. **Tác giả API chọn yêu cầu thao tác**, còn compiler thực thi yêu cầu ấy bằng luật generic type checking của ngôn ngữ.

Tương tự, `List.of`, `Arrays.asList` và `Collections.unmodifiableList` có chữ ký tổng quát cho nhiều kiểu phần tử nhưng lại có **hợp đồng mutation khác nhau**. Chữ ký tổng quát không đồng nghĩa khả năng thực thi mọi method thuộc interface.

## 11. Kiểu trả về interface và nguyên tắc thiết kế theo hợp đồng

Khi một method trả `List<T>`, nó có thể trả `ArrayList<T>`, `List.of(...)`, một view hoặc một implementation khác, miễn phù hợp các ràng buộc của chữ ký và hợp đồng API đã công bố.

Ví dụ:

```java
static List<String> names() {
    return List.of("An", "Binh");
}
```

Caller có thể đọc, lặp, tìm kiếm, lấy kích thước; nhưng không được mặc định rằng `names().add("C")` luôn chạy thành công.

Lựa chọn của tác giả API:

- **Nếu muốn trả trạng thái không sửa được:** có thể trả `List.copyOf(source)` cùng tài liệu null policy, shallow copy, và xử lý concurrency.
- **Nếu muốn caller sửa danh sách độc lập:** có thể trả `new ArrayList<>(source)` và ghi rõ ownership.
- **Nếu muốn một live view:** có thể trả `Collections.unmodifiableList(source)`, nhưng phải nói rõ thay đổi ở source sẽ được nhìn thấy qua view.
- **Nếu muốn API chỉ đọc:** nhận `Collection<? extends T>` hoặc `Iterable<T>` tùy thao tác cần thiết, thay vì yêu cầu caller có một mutable `List`.

Các lựa chọn này không làm thay đổi hệ thống type erasure hoặc invariance của Java. Chúng nói lên **semantic contract** mà người dùng phải đọc trong Javadoc và kiểm thử.

## 12. Chẩn đoán đúng khi gặp lỗi

### Trường hợp A — Javac báo không tìm được kiểu hoặc không tương thích type argument

Ví dụ:

```java
// List<int> values;              // Sai type argument
// List<Object> o = List.<String>of("A"); // Bất biến generic
```

Đây là nơi cần xem JLS, quy tắc generic, wildcard và kiểu tĩnh. Không có API mutable nào làm cho `List<int>` trở thành cú pháp hợp lệ.

### Trường hợp B — Javac báo bound không thỏa

Ví dụ:

```java
static <T extends Number> T same(T value) {
    return value;
}

// same("Java"); // Lỗi: không thể suy luận T phù hợp Number
```

Đọc chữ ký **API đã chọn** và đánh giá có đang gọi đúng phương thức hay không. Một generic API khác không có bound ấy có thể chấp nhận `String`.

### Trường hợp C — Biên dịch được nhưng `UnsupportedOperationException`

Ví dụ:

```java
List<String> names = List.of("A");
// names.add("B"); // Lỗi runtime dù type đúng
```

Xem implementation và hợp đồng mutation. Chuyển thành `new ArrayList<>(names)` nếu thực sự cần danh sách mutable; đừng ép kiểu `(ArrayList<String>) names` để mong object đổi loại.

### Trường hợp D — Biên dịch được nhưng `NullPointerException` tại factory

Ví dụ:

```java
// List.of("A", (String) null); // Runtime NPE
```

Đọc null policy của API; không quy kết generics cấm null. Nếu null hợp lệ trong dữ liệu nghiệp vụ, chọn collection/factory thích hợp hoặc lọc dữ liệu trước khi gọi.

### Trường hợp E — Unchecked warning và `ClassCastException`

Ví dụ raw alias vượt qua compile-time generic kiểm tra. Có thể phát sinh heap pollution và lỗi runtime khi đọc, hoặc bị `Collections.checkedList` chặn ngay khi thêm.

Hướng xử lý: giới hạn raw types, sửa caller bằng API generic, kiểm tra dữ liệu khi nhận từ nguồn không đáng tin và không dùng `@SuppressWarnings` để tuyên bố dữ liệu an toàn mà không có bằng chứng.

## 13. Bài tập tự kiểm tra

**Câu 1.** Vì sao `List<int>` không thể biên dịch, còn `List<Integer>` có thể?

Vì generic type arguments thông thường của Java yêu cầu kiểu tham chiếu; `int` là primitive, `Integer` là wrapper reference type. Đây là quy tắc ngôn ngữ.

**Câu 2.** `List<String> x = List.of("A"); x.add("B");` lỗi ở đâu?

Lời gọi hợp lệ về kiểu khi biên dịch; khi chạy, implementation tạo bởi `List.of` từ chối thao tác sửa cấu trúc, thường ném `UnsupportedOperationException`. Đây là hợp đồng API.

**Câu 3.** Vì sao `new ArrayList<String>().add(null)` không bị hệ thống generics cấm?

Vì type argument `String` không có nghĩa “non-null”; `ArrayList` cho phép phần tử null. API khác như `List.of` có thể đặt chính sách chặt hơn.

**Câu 4.** `static <T extends Number> void f(T x)` từ chối `f("abc")`. Đó có phải Java cấm `String` trong mọi generic method?

Không. Bound `Number` là lựa chọn chữ ký của chính `f`. Java chỉ thực thi bound ấy khi kiểm tra lời gọi.

**Câu 5.** Hai biến đều có kiểu `List<String>`; một biến đến từ `Arrays.asList`, một biến đến từ `new ArrayList<>()`. Vì sao `add` khác nhau?

Vì khả năng thay đổi kích thước là một phần hợp đồng của implementation, không được đảm bảo bởi type argument `String` hay riêng kiểu interface `List`.

**Câu 6.** `Collections.unmodifiableList(source)` có tạo snapshot hoàn toàn độc lập không?

Không. Nó tạo unmodifiable view, còn sửa đổi qua backing source có thể phản ánh ở view. Với mutable source, `List.copyOf(source)` là lựa chọn thích hợp hơn nếu muốn snapshot cấu trúc.

**Câu 7.** `Collections.checkedList` giúp gì khi gặp raw alias?

Nó kiểm tra runtime class của phần tử khi thao tác qua wrapper và có thể ném `ClassCastException` tại điểm ghi. Nó không thay thế tất cả luật generic compile time hoặc tự xác thực dữ liệu đã có từ trước.

**Câu 8.** Nếu một API chỉ chấp nhận `List<? extends Number>`, nên làm gì khi bạn có `List<String>`?

Xem mục tiêu và chữ ký API: nếu thao tác cần `Number`, truyền `List<String>` là sai hợp đồng. Không cast unchecked để lách; chọn dữ liệu phù hợp hoặc API khác có tính năng bạn cần.

**Câu 9.** Nếu compiler cho phép `list.add("x")` qua biến `List<String>`, có thể kết luận luôn không có runtime exception không?

Không. API có thể từ chối sửa đổi, từ chối null (khi đối số là null), từ chối do trạng thái, hoặc có preconditions khác được tài liệu hóa.

## 14. Checklist khi thiết kế và sử dụng generic API

Trước khi sửa code, hãy trả lời từng câu:

1. **Javac có từ chối mã không?** Nếu có, xem quy tắc type checking/JLS và chữ ký chính xác của method; lỗi compiler có thể phát sinh từ bound do API lựa chọn.
2. **Có runtime exception không?** Xác định method nào ném, implementation cụ thể là gì và precondition nào bị vi phạm.
3. **Kiểu tĩnh cho biết gì?** `List<String>` nói về phần tử ở compile time, không mặc nhiên nói về mutability, nullness hay thread safety.
4. **API có ràng buộc gì ngoài kiểu?** Fixed-size, unmodifiable, view, snapshot, null policy, ordering, comparator, capacity, ownership?
5. **Có unchecked/raw type không?** Nếu có, đánh giá heap pollution và đường truyền dữ liệu sai kiểu.
6. **Giải pháp đúng nằm ở đâu?** Đổi kiểu/thiết kế generic khi luật ngôn ngữ yêu cầu; đổi method/API khi bound không đúng; đổi collection/cách khởi tạo khi hợp đồng runtime không phù hợp.
7. **Có thể giữ API tổng quát hơn không?** Nếu chỉ đọc dữ liệu, cân nhắc `List<?>`, `Collection<? extends T>`, `Iterable<?>` thay vì buộc input phải là mutable list.
8. **Có cần defensive copy không?** Phân biệt unmodifiable view với snapshot và trách nhiệm quản lý alias.
9. **Đã kiểm tra trường hợp rỗng, null và sửa đổi chưa?** Các trường hợp này thường bộc lộ khác biệt API rõ hơn so với việc chọn `T`.
10. **Đang dựa vào tài liệu hay chỉ vào tên kiểu?** Hãy đọc Javadoc và hợp đồng method: compiler không thể chứng minh mọi quy tắc runtime của một implementation.

## 15. Kết luận

**Giới hạn ngôn ngữ Java** quyết định mã generic nào được viết và kiểm tra kiểu như thế nào — ví dụ invariance, type erasure, không dùng `List<int>`, `new T[]`, hoặc `T` của lớp trong static context. **Giới hạn do chữ ký API** quyết định loại đối số, bound và wildcard mà một operation cụ thể chấp nhận. **Giới hạn/hành vi runtime của API** quyết định collection có thêm/xóa được không, có nhận null không, là live view hay snapshot, và có kiểm tra phần tử lúc ghi hay không.

Vì vậy, hai giá trị cùng có kiểu `List<String>` **không nhất thiết có cùng khả năng sửa đổi hoặc chính sách null**. Khi gặp lỗi, hãy phân loại đúng tầng trước: **ngôn ngữ → chữ ký API → hợp đồng runtime**. Cách phân loại này giúp sửa đúng nguyên nhân, giữ an toàn kiểu và tránh những cast/raw workarounds gây lỗi muộn.

</details>

- [Quay lại đầu trang](#back-to-top)

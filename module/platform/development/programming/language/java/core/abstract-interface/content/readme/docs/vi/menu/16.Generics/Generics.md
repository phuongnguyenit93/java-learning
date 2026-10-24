<a id="back-to-top"></a>

# Kiểu tổng quát

## Menu
- [Khai báo generic interface và generic abstract class](#generics-001)
- [Triển khai interface với tham số kiểu cụ thể hoặc giữ tham số kiểu](#generics-002)
- [Phương thức generic và khác biệt với kiểu chứa generic](#generics-003)
- [Giới hạn kiểu bằng abstract class và interface](#generics-004)
- [Nhiều giới hạn kiểu và intersection type](#generics-005)
- [Tính bất biến của generic và wildcard tại ranh giới API](#generics-006)
- [Tự tham chiếu kiểu với Comparable&lt;T&gt; và kiểu fluent](#generics-007)
- [Type erasure, bridge method và quan hệ ghi đè](#generics-008)
- [Name clash do cùng erasure và kế thừa nhiều tham số kiểu](#generics-009)
- [Raw type, unchecked warning và giới hạn nhận biết tham số kiểu lúc chạy](#generics-010)

## <a id="generics-001">Khai báo generic interface và generic abstract class</a>

<details>
<summary>Xem chi tiết</summary>

# Khai báo generic interface và generic abstract class trong Java

## 1. Generics giải quyết vấn đề gì?

Generics cho phép một interface hoặc class biểu diễn mối quan hệ kiểu mà không phải viết nhiều phiên bản chỉ khác kiểu dữ liệu. Tham số kiểu như **T**, **S**, **K** và **V** được khai báo tại một kiểu; khi sử dụng, người gọi cung cấp đối số kiểu như **String**, **Integer** hoặc **User**.

~~~java
interface Converter<S, T> {
    T convert(S source);
}

abstract class Repository<T, ID> {
    public abstract T findById(ID id);
}
~~~

Trong **Converter&lt;S, T&gt;**, S là kiểu đầu vào và T là kiểu kết quả. Trong **Repository&lt;T, ID&gt;**, T là kiểu đối tượng được quản lý còn ID là kiểu khóa. Các kiểu này được giữ xuyên suốt chữ ký, nên trình biên dịch có thể phát hiện lời gọi không phù hợp trước khi chạy.

## 2. Khai báo generic interface

Một tham số kiểu:

~~~java
interface Box<T> {
    T get();
}
~~~

Nhiều tham số kiểu:

~~~java
interface Pair<K, V> {
    K key();
    V value();
}
~~~

Tên T, K và V chỉ là quy ước, không phải từ khóa. Có thể dùng tên mô tả như Source, Result hoặc ID nếu điều đó giúp hợp đồng dễ đọc hơn.

Khi một lớp triển khai `Box<String>`, hợp đồng `get()` có kiểu trả về `String`; nếu lớp giữ `T` và triển khai `Box<T>`, hợp đồng vẫn tổng quát. Cách lựa chọn và triển khai đầy đủ được phân tích ở bài tiếp theo.

Một interface cũng có thể kế thừa interface generic và truyền tiếp hoặc cố định tham số:

~~~java
interface Source<T> {
    T read();
}

interface NamedSource extends Source<String> {
    String name();
}

interface TransformSource<S, T> extends Source<T> {
    S original();
}
~~~

**NamedSource** kế thừa hợp đồng đã chọn T = String, nên read() trả về String. **TransformSource&lt;S, T&gt;** giữ cả hai tham số và chuyển T sang Source&lt;T&gt;. Interface có thể kế thừa nhiều interface; các chữ ký kế thừa vẫn phải tương thích với quy tắc Java thông thường.

## 3. Khai báo generic abstract class

Generic abstract class có thể dùng tham số kiểu trong field, constructor, phương thức thường và phương thức abstract:

~~~java
abstract class Holder<T> {
    private final T value;

    protected Holder(T value) {
        this.value = value;
    }

    public T getValue() {
        return value;
    }

    public abstract String describe();
}
~~~

Với **Holder&lt;String&gt;**, getValue() có kiểu trả về String. Constructor nhận T để khởi tạo trạng thái, còn describe() để lớp cụ thể cung cấp hành vi riêng. Không thể khởi tạo trực tiếp một abstract class bằng new.

Một class có thể khai báo nhiều tham số kiểu:

~~~java
abstract class Repository<T, ID> {
    public abstract T findById(ID id);
    public abstract void save(T entity);
}
~~~

**Repository&lt;User, Long&gt;** vì vậy nhận khóa Long và trả về User. Trong ví dụ `Holder<T>` phía trên, constructor `Holder(T value)` sử dụng T đã khai báo ở cấp class, chứ không khai báo type parameter mới. Java cũng cho phép generic constructor có type parameter riêng, độc lập với T của class.

## 4. Nối interface với abstract class

Abstract class có thể thực hiện interface và chia sẻ phần cài đặt chung cho các lớp con:

~~~java
interface Identifiable<ID> {
    ID id();
}

abstract class Entity<ID> implements Identifiable<ID> {
    private final ID id;

    protected Entity(ID id) {
        this.id = id;
    }

    @Override
    public final ID id() {
        return id;
    }
}

final class User extends Entity<Long> {
    private final String name;

    User(Long id, String name) {
        super(id);
        this.name = name;
    }

    String name() {
        return name;
    }
}
~~~

**Entity&lt;ID&gt;** chuyển tiếp ID sang interface và cung cấp cách lưu ID dùng chung. **User** cố định ID thành Long. Một abstract class có thể triển khai một phần hợp đồng và để phần còn lại cho lớp con; lớp cụ thể phải hoàn thành các phương thức abstract chưa được cài đặt. Java cho phép một class extends một class cha và implements nhiều interface.

## 5. Ví dụ chạy được

Lưu chương trình sau vào **GenericDeclarationDemo.java**:

~~~java
interface Mapper<S, T> {
    T map(S source);
}

interface Identifiable<ID> {
    ID id();
}

abstract class Entity<ID> implements Identifiable<ID> {
    private final ID id;

    protected Entity(ID id) {
        this.id = id;
    }

    @Override
    public final ID id() {
        return id;
    }
}

final class User extends Entity<Long> {
    private final String name;

    User(Long id, String name) {
        super(id);
        this.name = name;
    }

    String name() {
        return name;
    }
}

abstract class Repository<T, ID> {
    public abstract T findById(ID id);
}

final class UserRepository extends Repository<User, Long> {
    @Override
    public User findById(Long id) {
        return new User(id, "Lan");
    }
}

public class GenericDeclarationDemo {
    public static void main(String[] args) {
        Mapper<String, Integer> length = String::length;
        System.out.println("length=" + length.map("Java"));

        Repository<User, Long> repository = new UserRepository();
        User user = repository.findById(7L);
        System.out.println("user=" + user.name() + ", id=" + user.id());
    }
}
~~~

Biên dịch và chạy với JDK 17 trở lên:

~~~bash
javac GenericDeclarationDemo.java
java GenericDeclarationDemo
~~~

Kết quả:

~~~text
length=4
user=Lan, id=7
~~~

Trong ví dụ này, Mapper liên kết String đầu vào với Integer đầu ra; Entity giữ ID có kiểu; Repository liên kết kiểu thực thể với kiểu khóa. Các quan hệ đó thể hiện ngay trong khai báo và không cần cast ở nơi gọi.

## 6. Những lỗi khai báo cần tránh

- **Dùng primitive làm type argument:** List<int> không hợp lệ; dùng List&lt;Integer&gt;.
- **Dùng type parameter ngoài phạm vi:** T chỉ hợp lệ nếu được khai báo trên class, interface hoặc phương thức hiện tại.
- **Dùng T của instance class trong static member:** static member không gắn với một đối tượng có T cụ thể. Một static generic method phải khai báo tham số kiểu riêng nếu cần dùng kiểu tổng quát.
- **Nhầm extends với implements:** class dùng extends để kế thừa một class và implements để thực hiện interface. Trong giới hạn type parameter, extends được dùng cho cả class lẫn interface.
- **Dùng raw type không cần thiết:** khai báo Box thay vì Box&lt;String&gt; làm mất một phần kiểm tra kiểu.

Các bài tiếp theo đi sâu vào việc cố định hay giữ tham số kiểu khi implements, phạm vi của generic method, type bounds, wildcard và type erasure. Ở bài khai báo này, hãy tập trung xác định tham số kiểu nào thuộc về kiểu nào và cách nó được truyền qua chữ ký.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="generics-002">Triển khai interface với tham số kiểu cụ thể hoặc giữ tham số kiểu</a>

<details>
<summary>Xem chi tiết</summary>

# Triển khai interface với tham số kiểu cụ thể hoặc giữ tham số kiểu trong Java

## 1. Hai lựa chọn khi implements

Giả sử một interface khai báo hợp đồng tổng quát:

~~~java
interface Processor<T> {
    T process(T value);
}
~~~

Khi triển khai interface, class thường chọn một trong hai hướng:

- **Cố định kiểu:** dùng một type argument cụ thể, chẳng hạn implements Processor&lt;String&gt;. Lớp cam kết hỗ trợ hợp đồng cho String.
- **Giữ tính tổng quát:** khai báo tham số kiểu trên chính lớp, chẳng hạn class IdentityProcessor&lt;T&gt; implements Processor&lt;T&gt;. Lớp giữ quan hệ kiểu và có thể được sử dụng với nhiều type argument.

Chọn cách nào phụ thuộc vào trách nhiệm của implementation. Một bộ chuẩn hóa chuỗi có thể chuyên biệt cho String; một bộ chuyển tiếp giá trị có thể giữ nguyên T.

## 2. Cố định tham số kiểu

~~~java
interface Processor<T> {
    T process(T value);
}

final class TrimProcessor implements Processor<String> {
    @Override
    public String process(String value) {
        return value.trim();
    }
}
~~~

Trong mệnh đề **implements Processor&lt;String&gt;**, T được thay bằng String. Vì vậy, chữ ký phải nhận String và trả String. **TrimProcessor** không cần khai báo tham số kiểu riêng, vì kiểu hỗ trợ đã được xác định.

~~~java
Processor<String> processor = new TrimProcessor();
String result = processor.process("  Java  ");
~~~

Trình biên dịch chấp nhận lời gọi với String và từ chối giá trị không tương thích như Integer. Cách này phù hợp với adapter chuyên biệt, bộ chuẩn hóa chuỗi, hoặc repository chỉ quản lý một kiểu thực thể.

## 3. Giữ tham số kiểu của interface

~~~java
interface Processor<T> {
    T process(T value);
}

final class IdentityProcessor<T> implements Processor<T> {
    @Override
    public T process(T value) {
        return value;
    }
}
~~~

**IdentityProcessor&lt;T&gt;** khai báo T ở cấp class rồi chuyển chính tham số đó vào **Processor&lt;T&gt;**. T không bị cố định tại khai báo lớp:

~~~java
Processor<String> textProcessor = new IdentityProcessor<>();
Processor<Integer> numberProcessor = new IdentityProcessor<>();

String text = textProcessor.process("hello");
Integer number = numberProcessor.process(42);
~~~

Toán tử kim cương <> cho phép compiler suy luận type argument trong ngữ cảnh phù hợp. Với tham chiếu Processor&lt;String&gt;, chữ ký nhìn thấy ở nơi gọi nhận String và trả String; với Processor&lt;Integer&gt;, nó nhận Integer và trả Integer. Một implementation tổng quát vẫn chỉ đúng trong những giới hạn mà T cho phép. Nếu thuật toán cần khả năng đặc biệt như so sánh hoặc gọi doubleValue(), hãy khai báo type bound tương ứng thay vì giả định mọi T đều có hành vi đó.

## 4. Ánh xạ nhiều tham số kiểu

Khi interface có nhiều tham số, implementation phải cung cấp một type argument tương ứng cho từng vị trí:

~~~java
interface Converter<S, T> {
    T convert(S source);
}

final class StringLengthConverter implements Converter<String, Integer> {
    @Override
    public Integer convert(String source) {
        return source.length();
    }
}

final class IdentityConverter<U> implements Converter<U, U> {
    @Override
    public U convert(U source) {
        return source;
    }
}
~~~

**StringLengthConverter** cố định kiểu nguồn là String và kiểu đích là Integer. **IdentityConverter&lt;U&gt;** giữ một tham số U cho cả nguồn lẫn đích. Tên tham số của lớp triển khai không cần giống tên trong interface; điều quan trọng là vị trí và quan hệ kiểu phải đúng.

Type argument có thể là kiểu đã tham số hóa, ví dụ **Holder<List&lt;String&gt;>**, miễn là kiểu đó hợp lệ. Java không cho dùng primitive làm type argument; hãy dùng wrapper như Integer và dựa vào boxing/unboxing khi phù hợp.

## 5. Giữ kiểu qua kế thừa

Một lớp abstract có thể giữ tham số kiểu rồi để lớp con cố định nó:

~~~java
interface Repository<T> {
    T findById(long id);
}

abstract class BaseRepository<T> implements Repository<T> {
    // Lớp abstract được phép trì hoãn findById.
}

final class User {
    private final long id;

    User(long id) {
        this.id = id;
    }

    long id() {
        return id;
    }
}

final class UserRepository extends BaseRepository<User> {
    @Override
    public User findById(long id) {
        return new User(id);
    }
}
~~~

**BaseRepository&lt;T&gt;** chuyển tiếp T sang interface và có thể trì hoãn việc triển khai vì nó là abstract. **UserRepository** cố định T thành User, nên phương thức được triển khai phải trả User. Nếu lớp con chưa triển khai phương thức abstract và cũng không kế thừa implementation phù hợp, lớp đó phải tiếp tục là abstract.

## 6. Các lỗi biên dịch điển hình

### Dùng T nhưng không khai báo T

~~~java
interface Processor<T> {
    T process(T value);
}

// Không hợp lệ: T không thuộc phạm vi BrokenProcessor.
class BrokenProcessor implements Processor<T> {
}
~~~

Nếu muốn lớp tổng quát, khai báo **BrokenProcessor&lt;T&gt; implements Processor&lt;T&gt;**. Nếu lớp chỉ xử lý chuỗi, dùng **Processor&lt;String&gt;** và khai báo chữ ký nhận/trả String.

### Giữ T nhưng triển khai chữ ký chuyên biệt

~~~java
interface Processor<T> {
    T process(T value);
}

// Không hợp lệ: lớp tuyên bố hỗ trợ mọi T nhưng chỉ nhận String.
class BadProcessor<T> implements Processor<T> {
    @Override
    public String process(String value) {
        return value.trim();
    }
}
~~~

**BadProcessor&lt;T&gt;** hứa đáp ứng Processor cho bất kỳ T nào, nhưng process(String) không thể xử lý Integer hoặc kiểu T khác. Hãy cố định T thành String nếu implementation chỉ hỗ trợ chuỗi, hoặc triển khai process(T) nếu class thực sự tổng quát.

### Cố định T nhưng dùng chữ ký sai

~~~java
interface Processor<T> {
    T process(T value);
}

class WrongProcessor implements Processor<String> {
    // Không override được process(String).
    public Object process(Object value) {
        return value;
    }
}
~~~

Sau khi thay T bằng String, hợp đồng yêu cầu process(String) trả String. process(Object) là một phương thức khác; annotation @Override sẽ giúp compiler phát hiện lỗi khi đặt trên chữ ký không phù hợp.

## 7. Chọn thiết kế theo trách nhiệm

| Nhu cầu | Khai báo gợi ý | Lý do |
| --- | --- | --- |
| Chỉ xử lý chuỗi | implements Processor&lt;String&gt; | Hợp đồng cụ thể, dễ dùng |
| Cùng thuật toán cho nhiều kiểu | class ProcessorImpl&lt;T&gt; implements Processor&lt;T&gt; | Giữ kiểu qua đầu vào và kết quả |
| Chuyển đổi hai kiểu khác nhau | implements Converter&lt;String, Integer&gt; | Nguồn và đích rõ ràng |
| Lớp cơ sở trì hoãn implementation | abstract class BaseRepository&lt;T&gt; implements Repository&lt;T&gt; | Lớp con quyết định kiểu sau |
| Cần khả năng riêng của một nhóm kiểu | Dùng type bound phù hợp | Compiler kiểm tra điều kiện trước khi gọi |

Không thêm tham số kiểu chỉ để làm class trông tổng quát hơn. Ngược lại, đừng cố định kiểu quá sớm nếu implementation thực sự hỗ trợ nhiều kiểu.

**Phạm vi của bài này:** tính bất biến và wildcard ảnh hưởng đến các kiểu generic ở ranh giới API; type erasure và bridge method giải thích chữ ký ở runtime. Hai chủ đề đó có bài riêng trong chương. Khi implements, điều cần kiểm tra trước tiên là mỗi type parameter đã được cố định hoặc chuyển tiếp nhất quán qua mọi chữ ký chưa.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="generics-003">Phương thức generic và khác biệt với kiểu chứa generic</a>

<details>
<summary>Xem chi tiết</summary>

# Phương thức generic và khác biệt với kiểu chứa generic trong Java

## 1. Hai nơi khai báo tham số kiểu

Một **generic type** khai báo tham số kiểu sau tên class hoặc interface. Một **generic method** khai báo tham số kiểu riêng ngay trong chữ ký phương thức, trước kiểu trả về.

~~~java
class Box<T> {
    private final T value;

    Box(T value) {
        this.value = value;
    }

    T get() {
        return value;
    }

    <U> U echo(U input) {
        return input;
    }
}

class Utility {
    static <T> T identity(T input) {
        return input;
    }
}
~~~

Trong **`Box<T>`**, T thuộc phạm vi của class; get() sử dụng T nhưng bản thân get() không phải generic method. Phương thức **echo** khai báo U riêng, vì vậy cùng một `Box<String>` vẫn có thể gọi echo(42) và nhận Integer. **Utility.identity** cho thấy generic method không yêu cầu class chứa phải là generic.

## 2. Cú pháp và suy luận kiểu

Dạng tổng quát:

~~~java
static <T> T first(List<T> values) {
    return values.get(0);
}
~~~

Phần **`<T>`** khai báo type parameter của phương thức; T tiếp theo là kiểu trả về; `List<T>` liên kết kiểu phần tử đầu vào với kiểu kết quả. Khi gọi first(List.of("red", "blue")), compiler suy luận T là String, nên kết quả có kiểu String mà không cần cast.

Có thể chỉ rõ type argument bằng type witness khi muốn làm ý định rõ hơn:

~~~java
String inferred = first(List.of("red", "blue"));
String explicit = Utility.<String>identity("Java");
~~~

Thông thường compiler tự suy luận từ đối số và ngữ cảnh. Type witness là tùy chọn khi suy luận đã đủ rõ; nó không làm cho những kiểu không tương thích trở nên hợp lệ.

## 3. Ví dụ hoàn chỉnh: nhiều generic method trong một class thường

Lưu mã vào **GenericMethodDemo.java**:

~~~java
import java.util.List;

final class TextTools {
    private TextTools() {
    }

    static <T> T first(List<T> values) {
        if (values.isEmpty()) {
            throw new IllegalArgumentException("values must not be empty");
        }
        return values.get(0);
    }

    static <T> void showPair(T left, T right) {
        System.out.println(left + " | " + right);
    }

    static <T extends Number> double sum(List<T> values) {
        double total = 0.0;
        for (T value : values) {
            total += value.doubleValue();
        }
        return total;
    }
}

public class GenericMethodDemo {
    public static void main(String[] args) {
        TextTools.showPair("Java", "17");
        TextTools.showPair(10, 20);

        String first = TextTools.first(List.of("alpha", "beta"));
        System.out.println(first);
        System.out.println(TextTools.sum(List.of(1, 2, 3)));
    }
}
~~~

Biên dịch và chạy với JDK 17 trở lên:

~~~bash
javac --release 17 GenericMethodDemo.java
java GenericMethodDemo
~~~

Kết quả:

~~~text
Java | 17
10 | 20
alpha
6.0
~~~

Lần gọi showPair đầu tiên suy luận T là String; lần gọi thứ hai suy luận T là Integer. Mỗi lời gọi có thể suy luận một kiểu khác. Với sum, giới hạn T extends Number cho phép gọi doubleValue() an toàn vì compiler biết mọi T hợp lệ đều là Number hoặc kiểu con của Number.

## 4. Khi generic class và generic method cùng tồn tại

~~~java
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

    <U> U echo(U input) {
        return input;
    }

    static <U> U identity(U input) {
        return input;
    }
}
~~~

**T** thuộc về `Box<T>`; get() và set() dùng kiểu của hộp. **U** thuộc về echo(), độc lập với T của đối tượng. Vì vậy, `Box<String>` có thể lưu String nhưng vẫn gọi echo(42). Phương thức static identity cũng khai báo U riêng, nên không phụ thuộc vào một `Box<T>` cụ thể.

Ngược lại, một static member không thể dùng trực tiếp T của class như thể đã có một instance xác định T. Nếu cần một static method tổng quát, hãy khai báo type parameter riêng, như **`static <U> U identity(U input)`**.

## 5. Nhiều type parameter và giới hạn

Một phương thức có thể liên kết nhiều tham số kiểu để bảo toàn quan hệ giữa đầu vào và đầu ra:

~~~java
static <S, T> T convert(S source, java.util.function.Function<S, T> converter) {
    return converter.apply(source);
}
~~~

Ở đây S là kiểu nguồn, T là kiểu kết quả. Khi truyền String và hàm chuyển thành Integer, compiler có thể giữ quan hệ đó ở chữ ký. Nếu phương thức chỉ cần một kiểu đã biết từ class, không cần khai báo lại một type parameter riêng.

Có thể thêm giới hạn cho phương thức:

~~~java
static <T extends Number> double asDouble(T value) {
    return value.doubleValue();
}
~~~

Không có giới hạn, compiler không thể cho phép gọi doubleValue() trên một T tùy ý. Nếu có nhiều giới hạn, cú pháp và thứ tự class/interface được giải thích ở bài về type bounds và intersection type.

## 6. Generic method khác wildcard như thế nào?

~~~java
static <T> T first(List<T> values) {
    return values.get(0);
}

static void printAll(List<?> values) {
    for (Object value : values) {
        System.out.println(value);
    }
}
~~~

first dùng T có tên để liên kết kiểu phần tử với kiểu trả về: truyền `List<String>` thì kết quả là String. printAll chỉ cần duyệt danh sách, không cần biết kiểu phần tử, nên List<?> diễn đạt ý định đơn giản hơn. Wildcard có giới hạn như ? extends Number thường hữu ích khi đọc nhiều kiểu số; ? super Integer phù hợp khi cần ghi Integer vào danh sách.

Hãy dùng type parameter có tên khi nhiều vị trí phải chia sẻ cùng một kiểu hoặc kiểu trả về cần giữ mối quan hệ với đầu vào. Dùng wildcard khi kiểu phần tử cụ thể không quan trọng đối với thao tác cần thực hiện.

## 7. Lỗi thường gặp

- **Quên khai báo type parameter:** trong class không generic, T chỉ hợp lệ nếu phương thức khai báo `<T>` hoặc một phạm vi khác đã khai báo T.
- **Nhầm method dùng T của class với generic method:** T get() trong `Box<T>` không có type parameter riêng.
- **Dùng T của class trong static context:** static method cần khai báo type parameter riêng nếu muốn tổng quát.
- **Cho rằng mọi đối số trong cùng lời gọi có thể tùy ý khác kiểu:** các vị trí cùng dùng T phải thỏa cùng ràng buộc suy luận.
- **Thêm generic mà không tạo được quan hệ kiểu hữu ích:** nếu type parameter không giúp liên kết tham số, kết quả hoặc giới hạn hành vi, chữ ký đơn giản hơn có thể rõ ràng hơn.

Type erasure cũng ảnh hưởng đến những overload chỉ khác type argument; nội dung đó thuộc bài về name clash. Bài này tập trung vào phạm vi type parameter, suy luận kiểu và cách giữ quan hệ giữa đầu vào và kết quả.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="generics-004">Giới hạn kiểu bằng abstract class và interface</a>

<details>
<summary>Xem chi tiết</summary>

# Giới hạn kiểu tổng quát bằng abstract class và interface

## 1. Vì sao cần type bound?

Không có giới hạn, một type parameter T có thể đại diện cho nhiều kiểu tham chiếu khác nhau. Compiler chỉ cho phép gọi những phương thức được bảo đảm bởi kiểu đã biết. Nếu thuật toán cần lấy ID, một T tùy ý không đủ thông tin:

~~~java
static <T> String idOf(T value) {
    // value.id() không biên dịch: T chưa đảm bảo có id().
    return value.toString();
}
~~~

Khai báo giới hạn cho compiler biết kiểu nào được chấp nhận và những thành viên nào có thể gọi an toàn. Java dùng từ khóa **extends** cho cả giới hạn class lẫn interface.

## 2. Giới hạn bằng abstract class

Dùng abstract class làm bound khi thuật toán thật sự cần trạng thái hoặc hành vi được chia sẻ trong một họ kế thừa:

~~~java
abstract class NamedEntity {
    private final String name;

    protected NamedEntity(String name) {
        this.name = name;
    }

    public String name() {
        return name;
    }

    public abstract String category();
}

static <T extends NamedEntity> String labelOf(T value) {
    return value.name() + " (" + value.category() + ")";
}
~~~

Với **T extends NamedEntity**, mọi đối số kiểu hợp lệ phải là NamedEntity hoặc lớp con hợp lệ của nó. Vì vậy, labelOf có thể gọi name() và category() mà không cast. Không thể khởi tạo trực tiếp NamedEntity vì nó là abstract; lớp cụ thể phải cung cấp category() trước khi tạo đối tượng.

Bound bằng class phù hợp khi lớp nền thể hiện một quan hệ kế thừa thật sự. Đổi lại, Java chỉ cho một class cha trực tiếp, nên giới hạn bằng class có thể loại trừ các kiểu không thuộc họ đó.

## 3. Giới hạn bằng interface

Nếu thuật toán chỉ cần một khả năng, interface thường là bound linh hoạt hơn:

~~~java
interface Identifiable {
    String id();
}

static <T extends Identifiable> String idOf(T value) {
    return value.id();
}
~~~

Bất kỳ lớp nào triển khai Identifiable đều có thể truyền vào idOf, kể cả khi các lớp đó kế thừa những class khác nhau. Bound mô tả khả năng mà thuật toán cần thay vì buộc tất cả đối tượng dùng chung một class cha.

**Cú pháp cần phân biệt:** `class Account extends NamedEntity implements Identifiable` nêu class cha và interface mà `Account` cam kết thực hiện; lớp cụ thể còn phải có constructor hợp lệ và cài đặt các phương thức abstract cần thiết. Trong `<T extends Identifiable>` của generic method, `extends` đặt bound cho T, dù `Identifiable` là interface. Viết **&lt;T implements Identifiable&gt;** là sai cú pháp. Ví dụ chạy được ở phần 5 cho thấy cả hai vị trí.

## 4. Kết hợp class bound và interface bound

Một type parameter có thể yêu cầu kiểu vừa thuộc một họ class vừa thực hiện interface:

~~~java
abstract class NamedEntity {
    public abstract String name();
}

interface Identifiable {
    String id();
}

static <T extends NamedEntity & Identifiable> String describe(T value) {
    return value.name() + ", id=" + value.id();
}
~~~

Nếu có class bound, nó phải đứng đầu; các interface đứng sau và phân cách bằng dấu &. Chỉ được có tối đa một class bound. Chi tiết về nhiều interface, intersection type và intersection cast thuộc bài tiếp theo; ở đây cần hiểu rằng T phải đáp ứng mọi giới hạn đã nêu.

## 5. Ví dụ hoàn chỉnh

Lưu mã vào **TypeBoundsDemo.java**:

~~~java
abstract class NamedEntity {
    private final String name;

    protected NamedEntity(String name) {
        this.name = name;
    }

    public final String name() {
        return name;
    }
}

interface Identifiable {
    String id();
}

final class Account extends NamedEntity implements Identifiable {
    private final String id;

    Account(String name, String id) {
        super(name);
        this.id = id;
    }

    @Override
    public String id() {
        return id;
    }
}

public class TypeBoundsDemo {
    static <T extends NamedEntity & Identifiable> String describe(T value) {
        return value.name() + ", id=" + value.id();
    }

    static <T extends Identifiable> String idOf(T value) {
        return value.id();
    }

    public static void main(String[] args) {
        Account account = new Account("Lan", "U-17");
        System.out.println(describe(account));
        System.out.println(idOf(account));
    }
}
~~~

Biên dịch và chạy bằng JDK 17 trở lên:

~~~bash
javac TypeBoundsDemo.java
java TypeBoundsDemo
~~~

Kết quả:

~~~text
Lan, id=U-17
U-17
~~~

Account kế thừa NamedEntity và triển khai Identifiable, nên đáp ứng cả hai bound của describe. idOf chỉ cần Identifiable, vì vậy nó có thể nhận những kiểu không kế thừa NamedEntity. Hãy đặt bound theo đúng các thao tác phương thức cần gọi.

## 6. Type bound khác wildcard bound

~~~java
static <T extends Identifiable> T requireId(T value) {
    return value;
}

static void printIds(java.util.List<? extends Identifiable> values) {
    for (Identifiable value : values) {
        System.out.println(value.id());
    }
}
~~~

**&lt;T extends Identifiable&gt;** đặt tên T để liên kết nhiều vị trí trong chữ ký, chẳng hạn đầu vào và kiểu trả về. **? extends Identifiable** mô tả một kiểu phần tử chưa biết nhưng có thể đọc như Identifiable. Wildcard phù hợp với API đọc dữ liệu; type parameter có tên hữu ích khi cần giữ quan hệ kiểu giữa các vị trí. Bài về invariance và wildcard trình bày lựa chọn này chi tiết hơn.

## 7. Lỗi thường gặp

- Dùng **implements** trong bound thay vì **extends**.
- Đặt interface trước class bound, ví dụ **&lt;T extends Identifiable & NamedEntity&gt;** khi NamedEntity là class.
- Khai báo hai class bound cho cùng một T.
- Gọi phương thức riêng của một lớp triển khai khi bound không bảo đảm phương thức đó tồn tại.
- Đặt bound quá hẹp và loại bỏ các kiểu có thể dùng được, hoặc quá rộng khiến compiler không cho gọi thao tác cần thiết.

Type erasure dùng bound đầu tiên để xác định kiểu xóa của type variable; điều đó không làm mất các ràng buộc còn lại ở thời điểm biên dịch. Bài về type erasure giải thích hệ quả ở bytecode/runtime.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="generics-005">Nhiều giới hạn kiểu và intersection type</a>

<details>
<summary>Xem chi tiết</summary>

# Nhiều giới hạn kiểu và intersection type trong Java Generics

## 1. Multiple bounds biểu đạt điều kiện “tất cả”

Một thuật toán có thể cần một đối tượng đáp ứng nhiều hợp đồng cùng lúc: có ID, có tên và có giá trị đo được. Java cho phép khai báo nhiều giới hạn cho cùng một type parameter bằng dấu **&**:

~~~java
<T extends Number>
<T extends Comparable<T>>
<T extends Number & Comparable<T> & java.io.Serializable>
~~~

Một kiểu hợp lệ phải đáp ứng mọi bound. Nếu có class bound, nó phải đứng đầu và chỉ có tối đa một class bound; các bound sau là interface. Khi không có class bound, có thể liệt kê nhiều interface.

## 2. Ví dụ hoàn chỉnh

~~~java
interface Named {
    String name();
}

interface Measurable {
    double measure();
}

abstract class RecordBase {
    private final String id;

    protected RecordBase(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }
}

final class MetricRecord extends RecordBase
        implements Named, Measurable, Comparable<MetricRecord> {
    private final String name;
    private final double score;

    MetricRecord(String id, String name, double score) {
        super(id);
        this.name = name;
        this.score = score;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public double measure() {
        return score;
    }

    @Override
    public int compareTo(MetricRecord other) {
        return Double.compare(score, other.score);
    }
}

public class IntersectionTypeDemo {
    static <T extends RecordBase & Named & Measurable>
    String describe(T item) {
        return item.id() + ": " + item.name()
                + " (" + item.measure() + ")";
    }

    static <T extends RecordBase & Named & Measurable & Comparable<T>>
    T chooseLarger(T left, T right) {
        return left.compareTo(right) >= 0 ? left : right;
    }

    public static void main(String[] args) {
        MetricRecord alpha = new MetricRecord("alpha", "Alpha", 7.0);
        MetricRecord beta = new MetricRecord("beta", "Beta", 12.0);

        System.out.println(describe(alpha));
        System.out.println("winner=" + chooseLarger(alpha, beta).name());

        Object unknown = alpha;
        System.out.println("intersection="
                + ((Named & Measurable) unknown).name()
                + "/" + ((Named & Measurable) unknown).measure());
    }
}
~~~

Biên dịch và chạy bằng JDK 17 trở lên:

~~~bash
javac IntersectionTypeDemo.java
java IntersectionTypeDemo
~~~

Kết quả:

~~~text
alpha: Alpha (7.0)
winner=Beta
intersection=Alpha/7.0
~~~

**describe** cần lớp nền RecordBase cùng hai interface; compiler cho phép gọi id(), name() và measure() trực tiếp. **chooseLarger** bổ sung Comparable\<T>, nên có thể so sánh hai đối tượng cùng kiểu và trả về T. MetricRecord đáp ứng toàn bộ bound.

## 3. Bound và intersection cast khác nhau

Bound như **T extends RecordBase & Named & Measurable** ràng buộc kiểu đầu vào ở thời điểm biên dịch. Nếu kiểu không đáp ứng mọi bound, lời gọi bị từ chối.

Intersection cast như **(Named & Measurable) unknown** kiểm tra đối tượng thực tế khi chạy. Nếu object không thực hiện cả hai interface, cast có thể ném ClassCastException. Dùng cast khi cần kiểm tra một giá trị có kiểu tĩnh rộng; nếu API luôn cần nhiều khả năng, hãy thể hiện chúng trong bound để phát hiện lỗi sớm hơn.

## 4. Lỗi thường gặp

- Đặt interface trước class bound, ví dụ **\<T extends Named & RecordBase>** khi RecordBase là class.
- Khai báo hai class bound cho một type parameter.
- Dùng dấu phẩy thay dấu &: dấu phẩy ngăn cách các type parameter khác nhau, còn & kết hợp nhiều bound của cùng một parameter.
- Cho rằng chỉ cần đáp ứng một bound: **T extends Named & Measurable** yêu cầu cả hai.
- Thêm bound không phục vụ thao tác thật sự của thuật toán, khiến API loại bỏ những kiểu có thể dùng được.

Hãy xác định các phương thức thuật toán cần gọi, ánh xạ từng phương thức tới class/interface cung cấp hợp đồng, rồi khai báo ít bound nhất nhưng vẫn đủ.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="generics-006">Tính bất biến của generic và wildcard tại ranh giới API</a>

<details>
<summary>Xem chi tiết</summary>

# Tính bất biến của generic và wildcard tại ranh giới API trong Java

## 1. Vì sao generic là bất biến?

Nếu Integer là subtype của Number, một biến Number có thể tham chiếu đến Integer. Nhưng quan hệ đó không mở rộng thành List\<Integer> là subtype của List\<Number>:

~~~java
Number number = Integer.valueOf(10);
List<Integer> integers = new ArrayList<>();

// Không biên dịch:
// List<Number> numbers = integers;
~~~

Nếu phép gán trên hợp lệ, người nhận có thể thêm Double vào danh sách vốn chỉ dành cho Integer. Java từ chối phép gán để bảo toàn type safety ở thời điểm biên dịch.

## 2. Chọn wildcard theo thao tác

| Kiểu tham số | Đọc an toàn thành | Thêm giá trị khác null | Dùng khi |
| --- | --- | --- | --- |
| List\<T> | T | Có thể thêm T | Cần đọc và ghi cùng kiểu đã biết |
| List<?> | Object | Không thêm giá trị tùy ý | Chỉ duyệt/in phần tử |
| List<? extends Number> | Number | Không thêm Number tùy ý | Đọc các kiểu số |
| List<? super Integer> | Object | Có thể thêm Integer | Ghi Integer vào danh sách kiểu đó hoặc kiểu cha |

### Upper bound: Producer Extends

~~~java
static double sum(List<? extends Number> values) {
    double total = 0.0;
    for (Number value : values) {
        total += value.doubleValue();
    }
    return total;
}
~~~

Có thể truyền List\<Integer>, List\<Double> hoặc List\<Number>. Khi đọc, mỗi phần tử đều dùng được như Number. Không thể thêm Integer tùy ý vì danh sách thực tế có thể là List\<Double>.

### Lower bound: Consumer Super

~~~java
static void addDefaults(List<? super Integer> destination) {
    destination.add(10);
    destination.add(20);
}
~~~

Có thể truyền List\<Integer>, List\<Number> hoặc List\<Object>. Thêm Integer là an toàn; khi đọc, kiểu chung an toàn nhất là Object vì danh sách thực tế có thể chứa những kiểu khác.

**PECS** là mẹo ghi nhớ: Producer Extends cho nguồn cung cấp dữ liệu; Consumer Super cho đích nhận dữ liệu. Đây là hướng dẫn thiết kế, không phải luật bắt buộc cho mọi chữ ký.

## 3. Ví dụ hoàn chỉnh

~~~java
import java.util.ArrayList;
import java.util.List;

public class GenericVarianceDemo {
    static double sum(List<? extends Number> values) {
        double total = 0.0;
        for (Number value : values) {
            total += value.doubleValue();
        }
        return total;
    }

    static void addIntegers(List<? super Integer> destination, int... values) {
        for (int value : values) {
            destination.add(value);
        }
    }

    static <T> void copyAll(
            List<? super T> destination,
            List<? extends T> source) {
        for (T value : source) {
            destination.add(value);
        }
    }

    static void printAll(List<?> values) {
        for (Object value : values) {
            System.out.println(value);
        }
    }

    static <T> void swap(List<T> values, int left, int right) {
        T temporary = values.get(left);
        values.set(left, values.get(right));
        values.set(right, temporary);
    }

    public static void main(String[] args) {
        List<Integer> integers = new ArrayList<>(List.of(1, 2, 3));
        System.out.println(sum(integers));

        List<Number> numbers = new ArrayList<>();
        numbers.add(1.5);
        addIntegers(numbers, 7, 8);
        System.out.println(numbers);

        copyAll(numbers, integers);
        System.out.println(numbers);

        swap(integers, 0, 2);
        printAll(integers);
    }
}
~~~

Biên dịch và chạy với JDK 17 trở lên:

~~~bash
javac GenericVarianceDemo.java
java GenericVarianceDemo
~~~

Kết quả:

~~~text
6.0
[1.5, 7, 8]
[1.5, 7, 8, 1, 2, 3]
3
2
1
~~~

**copyAll** dùng T để liên kết kiểu nguồn với kiểu đích, đồng thời dùng wildcard để cho phép nguồn sản xuất T và đích tiêu thụ T. **swap** dùng List\<T> vì nó phải đọc và ghi lại cùng kiểu phần tử.

## 4. Khi nào dùng T thay vì wildcard?

~~~java
static <T> T first(List<T> values) {
    if (values.isEmpty()) {
        throw new IllegalArgumentException("values must not be empty");
    }
    return values.get(0);
}

static Object firstUnknown(List<?> values) {
    if (values.isEmpty()) {
        throw new IllegalArgumentException("values must not be empty");
    }
    return values.get(0);
}
~~~

Với List\<String>, phương thức đầu trả về String; phương thức thứ hai chỉ trả về Object. Dùng T có tên khi cần giữ quan hệ kiểu giữa tham số, nhiều vị trí hoặc kết quả. Dùng List<?> khi không cần biết kiểu phần tử.

Wildcard không thay đổi quan hệ subtype giữa các kiểu generic cụ thể. Nó chỉ cho phép biến hoặc tham số có wildcard nhận những type argument phù hợp. Type erasure, raw type và unchecked cast là các chủ đề liên quan nhưng có quy tắc riêng.

## 5. Lỗi thường gặp

- Gán List\<Child> cho List\<Parent> chỉ vì Child extends Parent.
- Dùng ? extends T rồi cố thêm một giá trị T tùy ý.
- Dùng ? super T rồi giả định phần tử đọc ra là T thay vì Object.
- Thêm wildcard vào mọi chữ ký dù List\<T> đã biểu đạt đúng quan hệ đọc/ghi.
- Quên rằng wildcard không bảo đảm collection có thể thay đổi; danh sách bất biến vẫn có thể ném UnsupportedOperationException.

Thiết kế chữ ký theo thao tác thực tế. Với nguồn đọc, cân nhắc ? extends T; với đích ghi, cân nhắc ? super T; khi cần đọc và ghi cùng kiểu đã biết, List\<T> thường rõ ràng hơn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="generics-007">Tự tham chiếu kiểu với Comparable&lt;T&gt; và kiểu fluent</a>

<details>
<summary>Xem chi tiết</summary>

# Tự tham chiếu kiểu với Comparable&lt;T&gt; và kiểu fluent

## 1. F-bounded polymorphism

Một tham số kiểu có thể tự xuất hiện trong bound của chính nó, ví dụ **T extends Comparable&lt;T&gt;** hoặc **T extends Builder&lt;T&gt;**. Mẫu này thường gọi là F-bounded polymorphism. Nó giúp compiler giữ thông tin về kiểu cụ thể trong các API so sánh hoặc chuỗi lời gọi fluent.

Đây là ràng buộc kiểu ở thời điểm biên dịch, không phải self type riêng của Java và không tự bảo đảm rằng mọi lớp con đều thiết kế đúng.

## 2. Comparable&lt;T&gt; giữ kiểu đối tượng được so sánh

~~~java
record Version(int major, int minor) implements Comparable<Version> {
    @Override
    public int compareTo(Version other) {
        int order = Integer.compare(major, other.major);
        if (order != 0) {
            return order;
        }
        return Integer.compare(minor, other.minor);
    }
}
~~~

Khi Version triển khai Comparable&lt;Version&gt;, phương thức compareTo nhận Version. Giá trị âm nghĩa là đối tượng hiện tại đứng trước đối số, 0 nghĩa là bằng nhau theo thứ tự tự nhiên, còn giá trị dương nghĩa là đứng sau. Không cần trả đúng -1 hoặc 1.

Dùng Integer.compare/Long.compare thay vì phép trừ trực tiếp, vì phép trừ có thể tràn số. Thứ tự compareTo nên có tính đối xứng về dấu, bắc cầu và nhất quán; Java khuyến nghị thứ tự tự nhiên nhất quán với equals, đặc biệt khi dùng TreeSet hoặc TreeMap.

Một API tổng quát thường dùng bound **T extends Comparable<? super T>** để chấp nhận kiểu có thể so sánh với T hoặc một kiểu cha phù hợp của T.

## 3. Giữ kiểu lớp con trong API fluent

Nếu phương thức ở lớp cha trả về chính kiểu cha, chuỗi lời gọi có thể mất các phương thức chỉ có ở lớp con:

~~~java
class BaseBuilder {
    BaseBuilder name(String value) {
        return this;
    }
}
~~~

Nếu UserBuilder bổ sung email(String), lời gọi new UserBuilder().name("Ada").email("ada@example.com") không biên dịch được sau name(), vì biểu thức có kiểu BaseBuilder.

Mẫu tự tham chiếu giải quyết vấn đề:

~~~java
abstract class Builder<T extends Builder<T>> {
    public T name(String value) {
        return self();
    }

    protected abstract T self();
}
~~~

Lớp con điền kiểu của chính nó vào T. Phương thức self() giúp lớp cơ sở trả về giá trị có kiểu cụ thể mà không cần unchecked cast như (T) this.

## 4. Ví dụ chạy được

Lưu mã vào **FluentBuilderDemo.java**:

~~~java
public class FluentBuilderDemo {
    abstract static class SelfBuilder<T extends SelfBuilder<T>> {
        private String name;

        public T name(String name) {
            this.name = name;
            return self();
        }

        protected abstract T self();

        protected String nameValue() {
            return name;
        }
    }

    static final class UserBuilder extends SelfBuilder<UserBuilder> {
        private String email;

        public UserBuilder email(String email) {
            this.email = email;
            return self();
        }

        @Override
        protected UserBuilder self() {
            return this;
        }

        User build() {
            return new User(nameValue(), email);
        }
    }

    record User(String name, String email) {}

    public static void main(String[] args) {
        User user = new UserBuilder()
                .name("Ada")
                .email("ada@example.com")
                .build();
        System.out.println(user);
    }
}
~~~

Biên dịch và chạy bằng JDK 17 trở lên:

~~~bash
javac FluentBuilderDemo.java
java FluentBuilderDemo
~~~

Kết quả:

~~~text
User[name=Ada, email=ada@example.com]
~~~

Sau name("Ada"), kiểu biểu thức vẫn là UserBuilder nên có thể gọi email() rồi build(). Không cần cast vì self() trả về UserBuilder.

## 5. Giới hạn và lỗi thường gặp

- **F-bound không phải self type tuyệt đối:** một lớp con vẫn có thể khai báo quan hệ không phù hợp nếu thiết kế cho phép; hãy giữ type argument nhất quán và giới hạn kế thừa khi cần.
- **Không dùng unchecked cast tùy tiện:** (T) this có thể che lỗi ở cây kế thừa. Mẫu self() abstract buộc lớp con cung cấp kiểu trả về rõ ràng.
- **Không nhầm compareTo với equals:** hai hợp đồng khác nhau; quyết định rõ các trường tham gia thứ tự.
- **Không so sánh số bằng phép trừ:** dùng các hàm compare chuẩn để tránh overflow.
- **Không thêm F-bound chỉ vì API có nhiều lớp:** dùng nó khi thật sự cần giữ kiểu cụ thể trong kết quả hoặc hợp đồng so sánh.

F-bounded polymorphism phù hợp với Comparable, builder và API fluent có nhiều tầng kế thừa. Với class/interface bound thông thường, xem bài về type bounds; với các bound kết hợp bằng &, xem bài về intersection type.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="generics-008">Type erasure, bridge method và quan hệ ghi đè</a>

<details>
<summary>Xem chi tiết</summary>

# Type erasure, bridge method và quan hệ ghi đè trong Java

## 1. Ba khái niệm liên quan

Generics giúp compiler kiểm tra kiểu ở mã nguồn. Java triển khai generic chủ yếu bằng **type erasure**: type parameter được xóa hoặc thay bằng kiểu xóa của bound khi tạo bytecode. Kế thừa generic có thể khiến chữ ký ở mã nguồn và descriptor JVM khác nhau; compiler sinh **bridge method** khi cần để lời gọi đa hình vẫn đến đúng implementation.

- **Type erasure** quy định kiểu của type variable và generic type sau khi biên dịch.
- **Overriding** được xác định theo quy tắc ngôn ngữ Java và quan hệ kế thừa generic.
- **Bridge method** là phương thức tổng hợp do compiler sinh để giữ tương thích giữa chữ ký đã xóa và implementation cụ thể.

Bridge method thường không có trong mã nguồn do lập trình viên viết. Không tự khai báo bridge để sửa một chữ ký generic sai.

## 2. Type erasure hoạt động thế nào?

| Khai báo nguồn | Kiểu xóa minh họa |
| --- | --- |
| List&lt;String&gt; | List |
| Map&lt;String, Integer&gt; | Map |
| T với &lt;T&gt; không bound | Object |
| T với &lt;T extends Number&gt; | Number |
| T với &lt;T extends Number & Comparable&lt;T&gt;&gt; | Number, là bound đầu tiên |

Ví dụ:

~~~java
class Box<T> {
    private T value;

    public void set(T value) {
        this.value = value;
    }

    public T get() {
        return value;
    }
}
~~~

Với T không có bound, chữ ký đã xóa tương đương về mặt khái niệm với set(Object) và get() trả Object. Khi mã nguồn dùng Box&lt;String&gt;, compiler vẫn kiểm tra kiểu và chèn cast cần thiết ở nơi đọc. Điều này không có nghĩa compiler tạo một class runtime riêng cho Box&lt;String&gt;.

Class file có thể giữ metadata generic signature để Reflection và công cụ đọc khai báo tổng quát. Metadata đó không biến Box&lt;String&gt; và Box&lt;Integer&gt; thành hai lớp runtime khác nhau.

## 3. Vì sao override generic cần bridge method?

~~~java
class Node<T> {
    public void setData(T data) {
        // Lưu dữ liệu.
    }

    public T getData() {
        return null;
    }
}

final class IntegerNode extends Node<Integer> {
    @Override
    public void setData(Integer data) {
        System.out.println("integer=" + data);
    }

    @Override
    public Integer getData() {
        return 42;
    }
}
~~~

Ở mã nguồn, IntegerNode cố định T thành Integer và cung cấp setData(Integer), getData() trả Integer. Các override này hợp lệ sau khi áp dụng type argument của Node&lt;Integer&gt;.

Sau erasure, phương thức của Node có chữ ký dùng Object, trong khi implementation của IntegerNode nhận hoặc trả Integer. Compiler cần tạo bridge tương đương với các điểm vào setData(Object) và getData() trả Object. Bridge chuyển tiếp đến implementation cụ thể, đồng thời có thể cast đối số từ Object sang Integer.

Nếu lời gọi dùng kiểu generic hợp lệ, compiler bảo đảm đối số phù hợp. Nếu raw type được dùng để truyền giá trị sai kiểu, cast bên trong bridge có thể ném ClassCastException.

## 4. Ví dụ chạy được và quan sát bằng Reflection

Lưu toàn bộ mã vào **TypeErasureBridgeDemo.java**:

~~~java
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Comparator;
import java.util.stream.Collectors;

class Node<T> {
    private T data;

    public void setData(T data) {
        this.data = data;
    }

    public T getData() {
        return data;
    }
}

final class IntegerNode extends Node<Integer> {
    @Override
    public void setData(Integer data) {
        System.out.println("set Integer: " + data);
        super.setData(data);
    }

    @Override
    public Integer getData() {
        return super.getData();
    }
}

public class TypeErasureBridgeDemo {
    public static void main(String[] args) {
        Node<Integer> node = new IntegerNode();
        node.setData(42);
        System.out.println("value=" + node.getData());

        Arrays.stream(IntegerNode.class.getDeclaredMethods())
                .filter(Method::isBridge)
                .sorted(Comparator.comparing(Method::getName))
                .forEach(method -> {
                    String parameters = Arrays.stream(method.getParameterTypes())
                            .map(Class::getSimpleName)
                            .collect(Collectors.joining(", "));
                    System.out.println("bridge=" + method.getName()
                            + "(" + parameters + ") -> "
                            + method.getReturnType().getSimpleName());
                });
    }
}
~~~

Biên dịch và chạy bằng JDK 17 trở lên:

~~~bash
javac TypeErasureBridgeDemo.java
java TypeErasureBridgeDemo
~~~

Kết quả stdout:

~~~text
set Integer: 42
value=42
bridge=getData() -> Object
bridge=setData(Object) -> void
~~~

Biến node được khai báo là Node&lt;Integer&gt; nhưng trỏ đến IntegerNode. Lời gọi setData(42) đi qua đường dispatch đã xóa rồi tới implementation nhận Integer. Reflection lọc isBridge() và sắp xếp theo tên để kết quả ổn định; không nên dựa vào thứ tự mà getDeclaredMethods() trả về.

## 5. Bridge và kiểu trả về hiệp biến

Bridge cũng có thể xuất hiện khi kiểu trả về của override là subtype cụ thể hơn:

~~~java
class Animal {
    public Object create() {
        return new Object();
    }
}

final class Cat extends Animal {
    @Override
    public String create() {
        return "cat";
    }
}
~~~

Java cho phép Cat.create() override Animal.create() vì String là subtype của Object. Kiểu trả về nằm trong descriptor JVM, nên compiler có thể sinh bridge trả Object để lời gọi qua Animal vẫn đến implementation của Cat. Đây không phải overload: Java không cho khai báo hai phương thức trong cùng class chỉ khác kiểu trả về.

Bridge thường mang các cờ bytecode ACC_BRIDGE và ACC_SYNTHETIC. Reflection có thể kiểm tra isBridge() và isSynthetic(); không phải mọi synthetic method đều là bridge, và không phải mọi generic override đều cần bridge.

## 6. Những hệ quả cần nhớ

- Không thể overload hai phương thức chỉ khác type argument nếu sau erasure chữ ký trùng nhau. Bài về name clash phân tích các trường hợp và cách thiết kế lại.
- Không thể kiểm tra List&lt;String&gt; bằng instanceof như thể runtime biết type argument cụ thể; dùng List&lt;?&gt; nếu chỉ cần xác nhận đó là một List.
- Không thể tạo trực tiếp new T() hoặc new T[n] cho type variable chưa biết cụ thể.
- Static member không dùng trực tiếp type parameter của instance class; static generic method có thể khai báo tham số kiểu riêng.
- Raw type và unchecked cast có thể bỏ qua một phần kiểm tra compile time; chúng không làm object runtime tự kiểm tra type argument.

## 7. Cách điều tra khi thấy method lạ

1. Xác nhận quan hệ extends/implements và chữ ký override ở mã nguồn.
2. Tính erasure của type variable: T không bound thành Object; T extends X thường xóa thành X.
3. Dùng @Override để compiler xác minh quan hệ override dự kiến.
4. Nếu Reflection liệt kê method bất ngờ, kiểm tra isBridge() và isSynthetic().
5. Có thể dùng javap -p -c -v IntegerNode để xem descriptor, cờ ACC_BRIDGE/ACC_SYNTHETIC và lệnh chuyển tiếp.

Type erasure là cơ chế triển khai; bridge method là chi tiết compiler tạo ra để giữ đa hình. Hãy thiết kế và kiểm tra hợp đồng ở mức Java source, không phụ thuộc vào việc tự viết bridge.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="generics-009">Name clash do cùng erasure và kế thừa nhiều tham số kiểu</a>

<details>
<summary>Xem chi tiết</summary>

# Name clash do cùng erasure và kế thừa nhiều tham số kiểu trong Java

## 1. Name clash là gì?

**Name clash** xuất hiện khi hai khai báo phương thức có cùng erasure nhưng không tạo thành một quan hệ override hợp lệ. Type argument giúp compiler kiểm tra kiểu ở mã nguồn, nhưng thường không tạo ra descriptor JVM riêng.

Ví dụ cố ý không biên dịch:

~~~java
import java.util.List;

class InvalidOverload {
    void handle(List<String> values) {}
    void handle(List<Integer> values) {}
}
~~~

Cả hai chữ ký đều xóa thành handle(List). Java không thể phân biệt chúng ở bytecode chỉ dựa vào String hay Integer bên trong List. Đây là lỗi thiết kế chữ ký; đổi tên biến tham số, đổi kiểu trả về hoặc thêm @SuppressWarnings không sửa được.

Overload thông thường với kiểu tham số thực sự khác nhau vẫn hợp lệ, ví dụ handle(String) và handle(Integer). Vấn đề là type argument bên trong cùng một generic type bị xóa.

## 2. Hai interface độc lập có thể xung đột

~~~java
interface TextReceiver<T> {
    void accept(T value);
}

interface NumberReceiver<T> {
    void accept(T value);
}

// Cố ý không biên dịch:
class ConflictingReceiver
        implements TextReceiver<String>, NumberReceiver<Integer> {
    public void accept(String value) {}
    public void accept(Integer value) {}
}
~~~

Sau khi áp dụng type argument, một hợp đồng yêu cầu accept(String), hợp đồng còn lại yêu cầu accept(Integer). Nhưng cả hai phương thức khai báo trong interface đều có erasure accept(Object). Compiler không thể tạo hai bridge accept(Object) khác nhau để đoán mỗi lời gọi cần đi vào implementation nào.

Nếu hai interface có cùng hợp đồng sau khi thay type argument, một implementation duy nhất có thể đáp ứng cả hai:

~~~java
interface First<T> {
    void accept(T value);
}

interface Second<T> {
    void accept(T value);
}

final class SharedReceiver implements First<String>, Second<String> {
    @Override
    public void accept(String value) {
        System.out.println("accepted=" + value);
    }
}
~~~

Ở đây cả hai interface đều yêu cầu accept(String), nên SharedReceiver chỉ cần một phương thức. Bridge nếu cần sẽ được compiler tạo theo quy tắc thông thường; xem bài về type erasure và bridge method để biết cách quan sát chúng.

## 3. Không thể kế thừa cùng interface generic với type argument khác nhau

Đây là lỗi riêng với cấu trúc kế thừa:

~~~java
interface Receiver<T> {
    void receive(T value);
}

// Cố ý không biên dịch:
class InvalidReceiver implements Receiver<String>, Receiver<Integer> {
}
~~~

Java không cho một kiểu kế thừa cùng generic interface với các type argument khác nhau. Đừng nhầm trường hợp này với hai interface độc lập ở phần trước: chúng có nguyên nhân liên quan đến erasure, nhưng quy tắc vi phạm khác nhau.

## 4. Generic method cũng có thể cùng erasure

~~~java
class MethodClash {
    <T> void store(T value) {}
    void store(Object value) {}
}
~~~

Đoạn này không biên dịch vì erasure của **&lt;T&gt; void store(T)** là store(Object), trùng với phương thức bình thường. Hãy giữ một chữ ký duy nhất nếu mục đích là nhận mọi đối tượng; nếu thao tác khác nhau về ý nghĩa, chọn tên hoặc tham số thực sự khác nhau.

Tương tự, không thể tạo hai overload chỉ bằng cách đổi type argument của List, Map hoặc một generic type khác. Wildcard cũng không tạo descriptor khác sau erasure.

## 5. Phân biệt name clash với override hợp lệ

Không phải mọi phương thức cùng erasure đều lỗi. Khi phương thức lớp con thật sự override hợp lệ phương thức generic được kế thừa, compiler có thể sinh bridge để giữ dispatch:

~~~java
class Box<T> {
    public T echo(T value) {
        return value;
    }
}

final class StringBox extends Box<String> {
    @Override
    public String echo(String value) {
        return "echo=" + value;
    }
}
~~~

StringBox.echo(String) override Box&lt;String&gt;.echo(T) sau khi T được thay bằng String. Bridge nhận Object có thể chuyển tiếp tới echo(String). Đây khác với hai overload độc lập handle(List&lt;String&gt;) và handle(List&lt;Integer&gt;), vì cặp overload đó không override nhau.

## 6. Bảng phân biệt

| Trường hợp | Kết quả | Lý do |
| --- | --- | --- |
| f(String) và f(Integer) | Thường hợp lệ | Descriptor tham số khác nhau |
| f(List&lt;String&gt;) và f(List&lt;Integer&gt;) | Không hợp lệ | Cùng erasure f(List) |
| Hai interface cùng yêu cầu accept(String) | Có thể hợp lệ | Một implementation đáp ứng cả hai |
| Hai interface yêu cầu accept(String) và accept(Integer), cả hai erasure accept(Object) | Không thể triển khai bằng hai overload đó | Không có một dispatch Object duy nhất phù hợp với cả hai |
| Kế thừa Receiver&lt;String&gt; và Receiver&lt;Integer&gt; | Không hợp lệ | Cùng interface generic bị kế thừa với type argument khác nhau |
| Override generic hợp lệ ở lớp con | Hợp lệ | Compiler có thể sinh bridge để giữ dispatch |

## 7. Cách xử lý khi gặp lỗi

1. Tìm tất cả phương thức cùng tên trong class, superclass và interface được kế thừa.
2. Tính erasure từng chữ ký: List&lt;String&gt; và List&lt;Integer&gt; đều thành List; T không bound thành Object; T extends Number thành Number.
3. Kiểm tra phương thức có thật sự override hay chỉ là overload. Dùng @Override để compiler xác nhận ý định override.
4. Kiểm tra type argument của các interface cha và xem có một implementation duy nhất đáp ứng được các hợp đồng không.
5. Chọn cách sửa theo ý nghĩa API: đổi tên phương thức nếu thao tác khác nhau; dùng một generic method nếu logic chung; thống nhất type argument khi các hợp đồng thực sự cùng kiểu; hoặc tách interface nếu chúng mô tả các hành vi không tương thích.

Không dùng raw type, unchecked cast hoặc @SuppressWarnings để né name clash. Các cách đó không tạo ra chữ ký JVM khác và có thể làm mất kiểm tra kiểu ở những vị trí khác. Khi lỗi liên quan đến bridge, hãy xem bài type erasure; khi lỗi liên quan đến raw type hoặc unchecked warning, hãy xem bài tương ứng trong chương.

## 8. Tự kiểm tra

1. Vì sao handle(List&lt;String&gt;) và handle(List&lt;Integer&gt;) không phải hai overload hợp lệ? Vì cả hai có erasure handle(List).
2. Vì sao hai interface có thể cùng được triển khai nếu đều yêu cầu accept(String)? Vì một phương thức duy nhất đáp ứng cả hai.
3. Vì sao không thể kế thừa Receiver&lt;String&gt; và Receiver&lt;Integer&gt;? Vì cùng generic interface không được kế thừa với type argument khác nhau.
4. Bridge method có phải cách để tự sửa overload trùng erasure không? Không; compiler chỉ sinh bridge khi cần duy trì một quan hệ override hợp lệ.
5. Nếu hai thao tác khác nhau, nên đổi tên hay ép kiểu? Hãy thiết kế chữ ký thể hiện đúng hợp đồng, không dùng cast để che xung đột.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="generics-010">Raw type, unchecked warning và giới hạn nhận biết tham số kiểu lúc chạy</a>

<details>
<summary>Xem chi tiết</summary>

# Raw type, unchecked warning và giới hạn nhận biết tham số kiểu lúc chạy

## 1. Ba khái niệm cần phân biệt

- **Raw type** là generic type được dùng mà không ghi type argument, ví dụ List thay vì List&lt;String&gt;.
- **Unchecked warning** báo rằng compiler không thể xác minh đầy đủ tính an toàn kiểu của một thao tác.
- **Type erasure** là cơ chế triển khai generics: type argument giúp kiểm tra mã nguồn, nhưng không tạo một runtime class riêng cho từng đối số kiểu.

Ví dụ:

~~~java
List<String> names = new ArrayList<>();
List raw = names;
raw.add(42);
String name = names.get(1); // Có thể ném ClassCastException.
~~~

raw trỏ đến cùng object với names. Dòng raw.add(42) thường biên dịch kèm cảnh báo; khi đọc phần tử 42 qua List&lt;String&gt;, compiler chèn cast về String và cast thất bại. Lỗi vì vậy có thể xuất hiện tại nơi đọc dữ liệu sai kiểu, xa nơi dữ liệu được thêm vào.

## 2. Raw type khác List<?> và List&lt;Object&gt;

| Khai báo | Ý nghĩa | Có thể thêm String? | An toàn kiểu |
| --- | --- | --- | --- |
| List raw | Bỏ qua type argument | Có, thường có warning | Thấp |
| List<?> | Kiểu phần tử chưa biết | Không thêm giá trị tùy ý, ngoại trừ null | Compiler vẫn bảo vệ kiểu |
| List&lt;Object&gt; | Danh sách có phần tử kiểu Object | Có | Kiểm tra kiểu ở compile time |

Dùng List<?> khi chỉ cần đọc/in các phần tử bất kể kiểu. List&lt;String&gt; không phải subtype của List&lt;Object&gt; vì generic là bất biến. Raw type chủ yếu tồn tại để tương thích với thư viện Java cũ; trong code mới, ưu tiên type argument đầy đủ hoặc wildcard phù hợp.

## 3. Ví dụ chạy được: warning và lỗi lúc đọc

~~~java
import java.util.ArrayList;
import java.util.List;

public class RawTypeDemo {
    public static void main(String[] args) {
        List<String> names = new ArrayList<>();
        names.add("An");

        List raw = names;
        raw.add(42);

        System.out.println("first=" + names.get(0));
        try {
            String second = names.get(1);
            System.out.println("second=" + second);
        } catch (ClassCastException ex) {
            System.out.println("read failed=" + ex.getClass().getSimpleName());
        }

        List<Integer> numbers = new ArrayList<>();
        System.out.println("same runtime class="
                + (names.getClass() == numbers.getClass()));
        System.out.println("instanceof List<?>=" + (names instanceof List<?>));
    }
}
~~~

Biên dịch và chạy với JDK 17 trở lên:

~~~bash
javac --release 17 -Xlint:rawtypes,unchecked RawTypeDemo.java
java RawTypeDemo
~~~

Kết quả stdout:

~~~text
first=An
read failed=ClassCastException
same runtime class=true
instanceof List<?>=true
~~~

Khi biên dịch, javac đưa ra warning về raw type và unchecked invocation tại các dòng khai báo/gọi qua raw. Số dòng cụ thể phụ thuộc vị trí mã nguồn. Hai ArrayList có type argument khác nhau vẫn có cùng runtime class; instanceof List<?> chỉ xác nhận object là List, không chứng minh danh sách chứa String.

## 4. Type erasure và reifiable types

Với type parameter không bound, erasure thường là Object; với T extends Number, erasure là Number. List&lt;String&gt; và List&lt;Integer&gt; đều có runtime class là List/ArrayList, không phải hai class riêng.

Vì runtime không giữ type argument như một kiểu riêng có thể kiểm tra trực tiếp, các biểu thức sau không hợp lệ:

~~~java
// Không hợp lệ:
if (value instanceof List<String>) { }
Class<?> type = List<String>.class;
List<String>[] values = new List<String>[10];
~~~

Có thể kiểm tra **instanceof List<?>** rồi xác thực từng phần tử nếu ứng dụng cần chắc chắn dữ liệu là String. Reflection có thể đọc metadata generic của khai báo field/method nếu class file lưu metadata đó; điều này không có nghĩa từng object ArrayList giữ type argument thực tế của nó.

Nếu ứng dụng cần thông tin kiểu lúc chạy, hãy truyền Class&lt;T&gt;, schema hoặc validator rõ ràng. Đừng giả định generic type argument luôn tồn tại trên từng object.

## 5. Cách xử lý warning

1. Đổi raw type sang kiểu tham số hóa như List&lt;String&gt; nếu biết kiểu phần tử.
2. Dùng List<?> nếu chỉ cần đọc mà không phụ thuộc kiểu phần tử.
3. Kiểm tra nguồn dữ liệu trước unchecked conversion/cast; nếu cần, xác thực từng phần tử.
4. Nếu buộc phải tích hợp API legacy, cô lập raw type tại ranh giới tương thích và chuyển dữ liệu sang kiểu an toàn càng sớm càng tốt.
5. Chỉ dùng @SuppressWarnings("unchecked") khi có bằng chứng cho giả định kiểu, và giới hạn annotation ở phạm vi nhỏ nhất. Annotation chỉ ẩn warning, không thêm kiểm tra runtime.

Raw type và unchecked cast có thể phá vỡ các bảo đảm compile time, còn type erasure giải thích vì sao lỗi đôi khi chỉ xuất hiện lúc đọc. Không dùng suppression để che một giả định kiểu chưa được kiểm chứng.

## 6. Tự kiểm tra

- Raw type khác List&lt;Object&gt; thế nào? Raw type bỏ qua type argument; List&lt;Object&gt; vẫn được compiler kiểm tra.
- Vì sao List&lt;String&gt; không thể xác nhận bằng instanceof? Runtime không kiểm tra type argument cụ thể.
- Warning unchecked có nghĩa chắc chắn chương trình hỏng không? Không; nó có nghĩa compiler không thể bảo đảm an toàn kiểu.
- Vì sao lỗi có thể xuất hiện ở get() thay vì add()? Cast về kiểu khai báo được chèn ở nơi đọc có thể thất bại.
- Khi cần xác minh dữ liệu runtime, nên làm gì? Kiểm tra từng phần tử hoặc dùng schema/type token tường minh.

Tài liệu tham khảo chính thức: JLS Java SE 17 §4.6 Type Erasure, §4.7 Reifiable Types, §4.8 Raw Types, §5.1.9 Unchecked Conversion và §5.5.2 Checked and Unchecked Casts.

</details>

- [Quay lại đầu trang](#back-to-top)

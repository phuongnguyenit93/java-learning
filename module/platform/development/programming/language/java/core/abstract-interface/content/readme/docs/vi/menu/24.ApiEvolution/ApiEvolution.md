<a id="back-to-top"></a>

# Tiến hóa API

## Menu
- [Mốc Java 8 cho default, static method và interface hàm](#api-evolution-001)
- [Mốc Java 9 cho private method trong interface](#api-evolution-002)
- [Mốc Java 17 cho sealed class và sealed interface](#api-evolution-003)
- [Phân biệt tương thích mã nguồn, nhị phân và hành vi](#api-evolution-004)
- [Thêm abstract method và tác động tới các lớp triển khai cũ](#api-evolution-005)
- [Thêm default method và nguy cơ xung đột kế thừa](#api-evolution-006)
- [Đổi chữ ký, kiểu trả về hoặc throws và ảnh hưởng tới bên sử dụng](#api-evolution-007)
- [Thay đổi hằng interface và rủi ro giá trị bị nhúng vào mã đã biên dịch](#api-evolution-008)
- [Lộ trình chuyển API cũ bằng interface con, adapter và deprecation](#api-evolution-009)

## <a id="api-evolution-001">Mốc Java 8 cho default, static method và interface hàm</a>

<details>
<summary>Xem chi tiết</summary>

# Mốc Java 8 cho default, static method và interface hàm

Java 8 mở rộng khả năng của interface theo ba hướng quan trọng: **default method** cho phép interface cung cấp hành vi mặc định, **static method** đặt các hàm tiện ích gắn với chính interface, và **functional interface** tạo kiểu đích cho lambda expression cùng method reference. Những thay đổi này giúp thư viện Java phát triển API mà vẫn hỗ trợ nhiều lớp triển khai cũ.

Interface vẫn mô tả một hợp đồng. Default method bổ sung cách thực hiện có thể dùng chung; static method là thao tác gọi qua tên interface; functional interface là interface có một phương thức trừu tượng phù hợp để biểu diễn một hành động.

## 1. Vì sao Java 8 cần mở rộng interface?

Trước Java 8, interface chủ yếu khai báo các phương thức trừu tượng và hằng số. Khi một thư viện muốn thêm phương thức vào interface đã được nhiều lớp triển khai, các lớp đó có thể phải bổ sung phần triển khai mới. Việc thay đổi một hợp đồng dùng rộng rãi vì vậy rất tốn kém.

Java 8 bổ sung default method để interface có thể cung cấp phần thân cho một phương thức mới. Lớp triển khai có thể dùng hành vi mặc định ngay hoặc ghi đè khi cần. Java 8 cũng bổ sung static method để gom các hàm tiện ích vào đúng nơi sở hữu API, đồng thời giới thiệu lambda expression và Stream API, vốn dựa nhiều vào functional interface.

Ví dụ về ba loại phương thức trong cùng một interface:

~~~java
interface TextRule {
    String apply(String text); // phương thức trừu tượng

    default String bracket(String text) {
        return "[" + apply(text) + "]";
    }

    static TextRule trim() {
        return String::trim;
    }
}
~~~

`apply` xác định hành động mà mỗi implementation phải cung cấp. `bracket` là hành vi dùng chung dựa trên `apply`. `trim` là hàm tiện ích tĩnh tạo ra một `TextRule` có nhiệm vụ loại bỏ khoảng trắng ở đầu và cuối chuỗi.

Interface có thể chứa cả phương thức trừu tượng, default và static. Việc có thêm default hoặc static method **không tự động làm interface mất tính functional**.

## 2. Default method: hành vi mặc định trong hợp đồng

Default method là phương thức interface có từ khóa `default` và có phần thân. Lớp triển khai không bắt buộc ghi đè phương thức này. Nếu không ghi đè, lời gọi có thể sử dụng phần triển khai mặc định.

~~~java
interface Printable {
    void print(String text);

    default void printLine(String text) {
        print(text);
        System.out.println();
    }
}
~~~

Lớp triển khai chỉ cần cung cấp `print`:

~~~java
class ConsolePrinter implements Printable {
    @Override
    public void print(String text) {
        System.out.print(text);
    }
}
~~~

Khi gọi `new ConsolePrinter().printLine("Hello")`, phương thức mặc định gọi `print` trên cùng đối tượng, nên implementation `ConsolePrinter.print` được sử dụng. Default method có thể gọi các phương thức khác của interface và có thể bị lớp triển khai ghi đè.

Một default method không làm interface có trạng thái instance riêng. Interface không có các trường instance thông thường; các trường được khai báo trong interface là hằng số `public static final`. Nếu hành vi cần trạng thái riêng cho từng đối tượng, một lớp cụ thể hoặc abstract class thường phù hợp hơn.

### Quy tắc chọn phương thức khi có nhiều implementation

Khi lớp kế thừa hành vi từ lớp cha và interface, Java áp dụng quy tắc phân giải:

1. **Phương thức cụ thể của lớp thắng default method từ interface.** Nếu lớp cha đã có một phương thức instance tương thích, phương thức lớp được ưu tiên.
2. **Interface cụ thể hơn thắng interface cha.** Nếu `Child` kế thừa `Parent` và cả hai định nghĩa default cùng chữ ký, default của `Child` được ưu tiên.
3. **Hai interface không có quan hệ kế thừa cùng định nghĩa default trùng chữ ký gây xung đột.** Lớp triển khai phải ghi đè phương thức đó để quyết định hành vi.
4. Trong phần ghi đè, có thể gọi rõ phần triển khai mặc định bằng `InterfaceName.super.methodName()` khi phù hợp.

Ví dụ về xung đột:

~~~java
interface HasCode {
    default String code() {
        return "A";
    }
}

interface HasName {
    default String code() {
        return "B";
    }
}

class Item implements HasCode, HasName {
    @Override
    public String code() {
        return HasCode.super.code() + "-" + HasName.super.code();
    }
}
~~~

Nếu `Item` không ghi đè `code`, trình biên dịch báo lỗi vì không thể tự chọn giữa hai default method. Bản triển khai trên chủ động kết hợp cả hai hành vi, nên `new Item().code()` trả về `"A-B"`.

Default method cũng có thể được một interface con khai báo lại dưới dạng abstract để yêu cầu implementation cụ thể cung cấp hành vi. Vì vậy, khi thiết kế API, cần xem xét cả quan hệ kế thừa interface và các phương thức mà lớp triển khai đã có.

## 3. Static method trong interface

Static method được khai báo bằng từ khóa `static` và có phần thân. Nó thuộc về interface, nên được gọi bằng tên interface:

~~~java
interface NumberTools {
    static boolean isEven(int number) {
        return number % 2 == 0;
    }
}

boolean result = NumberTools.isEven(8);
~~~

Static method hữu ích để đặt hàm tạo đối tượng, hàm kiểm tra hoặc tiện ích liên quan ngay trong API của interface. Người dùng không phải tìm một lớp tiện ích riêng nếu hành vi đó thuộc về interface.

Khác với default method, static method của interface **không được kế thừa như một phương thức instance** bởi lớp triển khai hoặc interface con, và không tham gia dispatch đa hình. Hãy gọi `NumberTools.isEven(8)`, không gọi `instance.isEven(8)`. Một static method có cùng tên ở lớp triển khai là một phương thức tĩnh riêng, không ghi đè static method của interface.

Các static method trong interface cũng không được gọi bằng `InterfaceName.super`. Cú pháp `InterfaceName.super.method()` dành cho việc chọn một triển khai instance mặc định.

## 4. Functional interface: một phương thức trừu tượng, nhiều khả năng

Functional interface (interface hàm) là interface có đúng một phương thức trừu tượng phù hợp làm mục tiêu của lambda expression hoặc method reference. Annotation `@FunctionalInterface` không bắt buộc để interface trở thành functional interface, nhưng nên dùng để trình biên dịch kiểm tra ý định thiết kế.

~~~java
@FunctionalInterface
interface TextRule {
    String apply(String text);

    default String bracket(String text) {
        return "[" + apply(text) + "]";
    }

    static TextRule trim() {
        return String::trim;
    }
}
~~~

`TextRule` vẫn là functional interface dù có thêm một default method và một static method, vì chỉ `apply` là phương thức trừu tượng cần implementation. Lambda như `text -> text.trim()` có thể tạo một đối tượng phù hợp với kiểu đích `TextRule`.

Các functional interface chuẩn trong `java.util.function` thường gặp:

| Interface | Phương thức trừu tượng | Công dụng |
|---|---|---|
| `Predicate<T>` | `boolean test(T value)` | Kiểm tra điều kiện, trả về đúng/sai. |
| `Function<T, R>` | `R apply(T value)` | Chuyển đổi một giá trị thành giá trị khác. |
| `Consumer<T>` | `void accept(T value)` | Nhận một giá trị và thực hiện tác động. |
| `Supplier<T>` | `T get()` | Cung cấp một giá trị mà không nhận tham số. |
| `UnaryOperator<T>` | `T apply(T value)` | Biến đổi giá trị và trả về cùng kiểu. |
| `BinaryOperator<T>` | `T apply(T left, T right)` | Kết hợp hai giá trị cùng kiểu để tạo một giá trị cùng kiểu. |

Java 8 cung cấp thêm các biến thể cho kiểu nguyên thủy, chẳng hạn `IntPredicate`, `IntFunction<R>` và `ToIntFunction<T>`, giúp giảm boxing/unboxing trong một số trường hợp.

Một interface vẫn có thể là functional interface khi khai báo default và static method. Các phương thức trừu tượng tương thích được kế thừa từ nhiều interface cha có thể đại diện cho cùng một chữ ký logic; tiêu chí quan trọng là hợp đồng trừu tượng cuối cùng có một phương thức chức năng, chứ không đơn giản là đếm số dòng khai báo trong toàn bộ cây kế thừa. `@FunctionalInterface` giúp phát hiện khi một thay đổi vô tình phá vỡ điều kiện này.

## 5. Ví dụ Java 17+ chạy được

Lưu đoạn mã dưới đây vào `Java8InterfaceEvolution.java`. Mã sử dụng cú pháp Java 8 trở lên và chạy được trên JDK 17.

~~~java
@FunctionalInterface
interface TextRule {
    String apply(String text);

    default String bracket(String text) {
        return "[" + apply(text) + "]";
    }

    static TextRule trim() {
        return String::trim;
    }
}

interface HasCode {
    default String code() {
        return "A";
    }
}

interface HasName {
    default String code() {
        return "B";
    }
}

class Item implements HasCode, HasName {
    @Override
    public String code() {
        return HasCode.super.code() + "-" + HasName.super.code();
    }
}

public class Java8InterfaceEvolution {
    public static void main(String[] args) {
        TextRule trim = TextRule.trim();
        TextRule upper = text -> text.toUpperCase(java.util.Locale.ROOT);

        System.out.println("trim=" + trim.apply("  Java  "));
        System.out.println("bracket=" + trim.bracket("  Java  "));
        System.out.println("upper=" + upper.apply("java 8"));
        System.out.println("default=" + upper.bracket("java"));
        System.out.println("conflict=" + new Item().code());
    }
}
~~~

Biên dịch và chạy:

~~~text
javac Java8InterfaceEvolution.java
java Java8InterfaceEvolution
~~~

Kết quả chuẩn:

~~~text
trim=Java
bracket=[Java]
upper=JAVA 8
default=[JAVA]
conflict=A-B
~~~

Trong ví dụ này, `TextRule.trim()` là static method được gọi qua tên interface. Giá trị trả về là một functional interface, nên có thể gán vào biến `TextRule`. `String::trim` là method reference tương thích với phương thức `apply(String)`. Phương thức `bracket` là default method và gọi `apply` trên implementation hiện tại; với `upper`, kết quả vì thế được chuyển thành chữ hoa trước khi đặt trong dấu ngoặc. `Item.code()` giải quyết xung đột bằng cách gọi tường minh default method của từng interface cha.

## 6. Java 8 đã áp dụng những ý tưởng này trong thư viện chuẩn

Các API Java 8 minh họa rõ vai trò của default và static method:

- `Comparator.comparing(...)` là static method tạo comparator dựa trên một hàm trích xuất khóa.
- `Predicate.and(...)`, `Predicate.or(...)` và `Predicate.negate()` là default method kết hợp hoặc đảo điều kiện.
- `Function.andThen(...)` và `Function.compose(...)` là default method ghép các phép biến đổi.
- `Collection.removeIf(...)` là default method cho phép xóa phần tử thỏa predicate.
- `Map.computeIfAbsent(...)` là default method hỗ trợ tính toán và lưu giá trị khi khóa chưa có giá trị ánh xạ phù hợp.

Ví dụ sử dụng API chuẩn:

~~~java
import java.util.List;
import java.util.function.Predicate;

List<String> names = List.of("An", "Binh", "Chi");
Predicate<String> startsWithB = name -> name.startsWith("B");
Predicate<String> longEnough = name -> name.length() >= 3;

List<String> selected = names.stream()
        .filter(startsWithB.and(longEnough))
        .toList();
~~~

Với JDK 17, `selected` chứa `"Binh"`. Ví dụ này cho thấy functional interface giúp biểu diễn điều kiện bằng lambda, còn default method `and` cho phép kết hợp hai điều kiện mà không cần tạo lớp kết hợp riêng.

Các API này còn giúp phát triển thư viện theo hướng tương thích: có thể thêm thao tác tiện dụng vào interface và cung cấp cách triển khai mặc định. Tuy nhiên, việc thêm default method vẫn cần đánh giá tác động đến các lớp triển khai và các interface khác trong cây kế thừa.

## 7. Khả năng tương thích và giới hạn

Default method giúp giảm nhu cầu buộc mọi lớp triển khai phải bổ sung mã khi interface được mở rộng, nhưng không đảm bảo mọi thay đổi đều an toàn:

- **Thêm phương thức abstract:** các lớp triển khai có thể cần bổ sung phần thân; mã nguồn cũ có thể không còn biên dịch.
- **Thêm default method:** thường dễ tương thích hơn, nhưng có thể tạo xung đột với default từ interface khác hoặc làm thay đổi hành vi mà lớp trước đó vốn kế thừa.
- **Thêm static method:** không yêu cầu lớp triển khai thêm phần thân, nhưng cần cân nhắc tên và cách sử dụng để tránh gây nhầm lẫn với các API hiện hữu.
- **Thay đổi hợp đồng hành vi:** dù mã vẫn biên dịch, default method mới có thể làm thay đổi kết quả khi được gọi trên implementation cũ.

Default method không phải cơ chế để thay thế hoàn toàn abstract class. Nếu cần trạng thái instance, constructor, hoặc chia sẻ trạng thái giữa các phương thức, abstract class có thể phù hợp hơn. Interface phù hợp để mô tả khả năng mà nhiều lớp không liên quan có thể cùng triển khai; default method cung cấp hành vi chung khi hành vi đó thực sự có ý nghĩa với hợp đồng.

## 8. Lỗi thường gặp

- **Cho rằng functional interface chỉ được khai báo một phương thức tổng cộng:** không đúng. Interface có thể có nhiều default và static method, nhưng chỉ có một phương thức trừu tượng chức năng.
- **Quên `@Override` khi giải quyết default conflict:** lớp triển khai phải cung cấp phương thức instance tương thích để loại bỏ xung đột; dùng annotation giúp trình biên dịch kiểm tra chữ ký.
- **Gọi static method qua biến instance:** static method thuộc về tên interface, ví dụ `NumberTools.isEven(8)`.
- **Cho rằng static method của interface được kế thừa hoặc ghi đè:** static method của interface không có dispatch đa hình qua lớp triển khai.
- **Dùng `InterfaceName.super.method()` cho static method:** cú pháp này chỉ chọn default method instance.
- **Thêm default method mà không rà soát các interface khác:** lớp triển khai nhiều interface có thể gặp xung đột cùng chữ ký.
- **Cho rằng `@FunctionalInterface` là điều kiện bắt buộc để dùng lambda:** annotation chỉ xác nhận ý định và yêu cầu kiểm tra của compiler; điều quyết định là kiểu đích có thỏa quy tắc functional interface hay không.

## 9. Checklist khi thiết kế interface

1. Phương thức mới có thực sự là hành vi mặc định hợp lý cho mọi implementation không? Nếu không, hãy cân nhắc abstract method hoặc một API riêng.
2. Có interface nào khác mà lớp triển khai thường dùng cùng lúc định nghĩa default cùng chữ ký không?
3. Static method có thực sự thuộc về API của interface, chẳng hạn hàm factory hoặc helper liên quan, không?
4. Nếu interface được dùng làm kiểu đích cho lambda, nó có giữ đúng một phương thức trừu tượng chức năng không?
5. Có thể viết ví dụ kiểm thử cho lớp triển khai cũ và cho tình huống kế thừa nhiều interface để phát hiện thay đổi hành vi không?

Khi dùng đúng mục đích, ba khả năng của Java 8 bổ sung cho nhau: default method giúp mở rộng hợp đồng với hành vi chung, static method gom tiện ích vào đúng API, và functional interface cung cấp hình dạng kiểu cho lambda, method reference cùng các pipeline xử lý dữ liệu.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="api-evolution-002">Mốc Java 9 cho private method trong interface</a>

<details>
<summary>Xem chi tiết</summary>

# Mốc Java 9 cho private method trong interface

Java 9 bổ sung **private method trong interface** để tác giả interface có thể chia sẻ logic nội bộ giữa nhiều phương thức mà không làm lộ các hàm trợ giúp đó thành một phần API công khai. Tính năng này đặc biệt hữu ích khi interface có nhiều phương thức default hoặc static cần thực hiện cùng một bước chuẩn hóa, kiểm tra hay định dạng.

Điểm cần nhớ: **private method trong interface là chi tiết triển khai của chính interface**. Lớp triển khai không thể gọi nó trực tiếp, không kế thừa nó và không ghi đè nó theo cơ chế đa hình.

## 1. Vì sao Java cần private method trong interface?

Java 8 cho phép interface khai báo phương thức **default** có phần thân và phương thức **static** có phần thân. Nhờ vậy, một interface có thể bổ sung hành vi dùng chung mà không buộc mọi lớp triển khai phải viết lại phương thức đó.

Tuy nhiên, khi nhiều phương thức default phải dùng chung một đoạn xử lý, trước Java 9 tác giả thường phải chọn giữa việc lặp lại mã hoặc đưa helper thành một phương thức public trong interface. Phương thức public có thể trở thành một phần hợp đồng mà người dùng interface nhìn thấy và có thể gọi, dù nó chỉ được tạo ra để phục vụ nội bộ.

Java 9 cho phép khai báo phương thức private bên trong interface để giải quyết việc này:

- Phương thức private instance có thể được gọi từ các phương thức instance khác trong cùng interface, chẳng hạn các phương thức default.
- Phương thức private static có thể được gọi từ các phương thức static hoặc những đoạn mã khác bên trong cùng interface phù hợp với quy tắc static.
- Helper được giữ ở phạm vi nội bộ, nên mã gọi interface không thể dựa vào helper như một API công khai.

## 2. Mốc phiên bản và các dạng phương thức

| Tính năng | Phiên bản Java |
|---|---|
| Phương thức abstract trong interface | Có từ các phiên bản Java đầu tiên |
| Phương thức default có phần thân | Java 8 |
| Phương thức static trong interface | Java 8 |
| Phương thức private trong interface | Java 9 |
| Phương thức private static trong interface | Java 9 |

Một interface Java 9+ có thể khai báo các dạng phương thức sau:

- **Abstract method:** không có phần thân; lớp cụ thể triển khai hợp đồng này.
- **Default method:** có phần thân và được kế thừa bởi lớp triển khai nếu không có quy tắc ghi đè khác áp dụng.
- **Public static method:** thuộc interface, được gọi qua tên interface; không phải phương thức instance của đối tượng.
- **Private instance method:** helper có phần thân, dùng bên trong interface bởi các phương thức instance thích hợp.
- **Private static method:** helper static có phần thân, dùng bên trong interface bởi các phương thức nội bộ thích hợp.

Trong interface, phương thức không ghi rõ access modifier thường là public. Phương thức default và static thông thường của interface cũng là public ngầm định. Từ Java 9, từ khóa private cho phép giới hạn helper trong interface.

## 3. Ví dụ chạy được với Java 9 trở lên

Ví dụ dưới đây có hai phương thức default dùng chung các helper instance private để chuẩn hóa tên và bọc thông điệp. Phương thức static công khai dùng một helper private static để chuẩn hóa mã.

Lưu mã sau vào tệp **PrivateInterfaceMethodsDemo.java**:

~~~java
import java.util.Locale;

interface MessageFormatter {
    default String greeting(String name) {
        return decorate("Hello, " + normalizeName(name) + "!");
    }

    default String farewell(String name) {
        return decorate("Goodbye, " + normalizeName(name) + "!");
    }

    static String formatCode(String code) {
        return "CODE-" + normalizeCode(code);
    }

    private String normalizeName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return "guest";
        }
        return name.trim();
    }

    private String decorate(String message) {
        return "[" + message + "]";
    }

    private static String normalizeCode(String code) {
        if (code == null) {
            throw new IllegalArgumentException("code must not be null");
        }
        return code.trim().toUpperCase(Locale.ROOT);
    }
}

public class PrivateInterfaceMethodsDemo implements MessageFormatter {
    public static void main(String[] args) {
        PrivateInterfaceMethodsDemo demo = new PrivateInterfaceMethodsDemo();

        System.out.println(demo.greeting("Linh"));
        System.out.println(demo.farewell("Linh"));
        System.out.println(demo.greeting("  "));
        System.out.println(MessageFormatter.formatCode(" ab-7 "));
    }
}
~~~

Biên dịch và chạy bằng JDK 9 trở lên:

~~~bash
javac PrivateInterfaceMethodsDemo.java
java PrivateInterfaceMethodsDemo
~~~

Stdout chính xác:

~~~text
[Hello, Linh!]
[Goodbye, Linh!]
[Hello, guest!]
CODE-AB-7
~~~

Cả hai phương thức default gọi normalizeName và decorate, nhưng các helper đó không xuất hiện như phương thức công khai của MessageFormatter. Phương thức static formatCode gọi normalizeCode để dùng chung logic chuẩn hóa. Lớp PrivateInterfaceMethodsDemo chỉ sử dụng các phương thức public mà interface cung cấp.

Ví dụ dùng trim và isEmpty để có thể chạy từ Java 9. Nếu dự án đặt mức biên dịch thấp hơn Java 9, trình biên dịch sẽ từ chối cú pháp private method trong interface. JDK 17 cũng có thể biên dịch và chạy ví dụ này.

## 4. Quy tắc truy cập cần nắm

**Phương thức private phải có phần thân.** Một phương thức private không thể đồng thời là phương thức abstract, vì abstract yêu cầu lớp triển khai cung cấp phần thân còn private giữ helper ở nội bộ interface.

**Phương thức private không phải default method.** Không khai báo một phương thức vừa private vừa default. Default method là hành vi instance được công bố cho lớp triển khai và người gọi; private instance method chỉ là helper nội bộ.

**Lớp triển khai không gọi được helper private.** Mã bên ngoài không thể gọi helper qua đối tượng hoặc qua tên interface. Chỉ các phương thức thích hợp bên trong interface mới có thể sử dụng chúng.

**Phương thức private không được kế thừa hay ghi đè.** Nếu một lớp triển khai khai báo một phương thức cùng tên với helper private của interface, phương thức đó là thành viên riêng của lớp triển khai; nó không ghi đè helper private của interface.

**Phân biệt instance và static.** Helper instance có thể làm việc với lời gọi instance trong interface. Helper static không có ngữ cảnh đối tượng và không thể truy cập trực tiếp thành viên instance nếu không có một đối tượng phù hợp. Giữ helper static cho logic chỉ phụ thuộc vào tham số và dữ liệu static.

**Private static cũng chỉ dùng nội bộ.** Dù helper static không cần đối tượng, nó vẫn không trở thành một API gọi được từ lớp triển khai. Từ khóa static không làm mất giới hạn truy cập của private.

## 5. Những lỗi thường gặp

- **Dùng Java 8 để biên dịch mã có private method trong interface.** Tính năng cần Java 9 trở lên; hãy kiểm tra JDK và tùy chọn source/release của dự án.
- **Gọi helper private từ lớp triển khai.** Helper này chỉ dùng trong interface. Nếu hành vi thực sự cần được gọi từ bên ngoài, hãy thiết kế một phương thức public phù hợp với hợp đồng thay vì cố truy cập helper.
- **Đưa mọi logic vào phương thức public chỉ để tái sử dụng.** Việc đó làm API lớn hơn và khiến người dùng phụ thuộc vào chi tiết vốn chỉ nhằm hỗ trợ triển khai. Dùng private method khi logic chỉ cần dùng bên trong interface.
- **Lặp lại cùng một logic trong nhiều default method.** Nếu các phương thức cần cùng một bước xử lý, hãy cân nhắc tách helper private để tránh sai khác khi sửa đổi.
- **Gọi thành viên instance từ helper static như thể có sẵn đối tượng.** Phương thức static không có this. Hãy truyền dữ liệu cần thiết qua tham số hoặc dùng helper instance khi logic phụ thuộc vào ngữ cảnh instance.
- **Nhầm private method với phương thức mặc định.** Default method tạo hành vi công khai mà lớp triển khai có thể thừa hưởng; private method là cơ chế tổ chức mã bên trong interface.

## 6. Khi nào nên sử dụng?

Dùng private method trong interface khi một hoặc nhiều phương thức default cần chia sẻ quy trình nhỏ như chuẩn hóa dữ liệu đầu vào, tạo thông điệp, kiểm tra điều kiện hoặc ghép các bước xử lý. Dùng private static khi helper không cần trạng thái instance và được gọi từ các phương thức static hoặc logic nội bộ phù hợp.

Nếu helper cần được gọi từ lớp triển khai, từ một service độc lập hoặc từ nhiều kiểu không liên quan, cân nhắc chuyển nó thành phương thức của một lớp trợ giúp hay thành một API public được thiết kế có chủ đích. Không nên dùng private method trong interface chỉ vì có thể; nó phù hợp nhất khi hành vi thực sự thuộc về phần triển khai của interface.

Tính năng Java 9 này giúp interface duy trì API gọn hơn trong khi vẫn cho phép tác giả tổ chức mã dùng chung rõ ràng. Khi đọc code, hãy phân biệt phương thức public/default mà người dùng có thể gọi với helper private chỉ phục vụ bên trong interface.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="api-evolution-003">Mốc Java 17 cho sealed class và sealed interface</a>

<details>
<summary>Xem chi tiết</summary>

# Mốc Java 17 cho sealed class và sealed interface

Java 17 đánh dấu thời điểm **sealed classes và sealed interfaces trở thành tính năng chính thức của ngôn ngữ Java**. Chúng cho phép tác giả API kiểm soát những kiểu nào được kế thừa hoặc triển khai trực tiếp một kiểu cha. Điều này hữu ích khi API muốn công bố một tập hợp biến thể có chủ đích, nhưng vẫn cần cho phép một số nhánh mở rộng.

Sealed types liên quan trực tiếp đến tiến hóa API: chúng làm rõ phần nào của hệ phân cấp là do thư viện kiểm soát, phần nào được phép mở rộng, và những thay đổi nào cần được xem xét khi phát hành phiên bản mới.

## 1. Mốc lịch sử và cú pháp ổn định

Sealed classes được thử nghiệm dưới dạng preview trong JDK 15, tiếp tục ở JDK 16, rồi được hoàn thiện trong JDK 17 theo JEP 409. Từ Java 17, có thể dùng `sealed`, `permits` và `non-sealed` như cú pháp chính thức mà không bật preview features.

Ví dụ khai báo một interface chỉ cho phép hai kiểu trực tiếp:

~~~java
sealed interface ApiResult permits ApiSuccess, ApiFailure {
}
~~~

`ApiSuccess` và `ApiFailure` phải là các kiểu con trực tiếp của `ApiResult`. Mỗi kiểu con trực tiếp phải xác định rõ nhánh của nó sẽ đóng lại, tiếp tục được kiểm soát hay mở ra cho các kiểu khác.

~~~java
record ApiSuccess(String value) implements ApiResult {
}

record ApiFailure(int code, String message) implements ApiResult {
}
~~~

Hai record này là final ngầm định theo quy tắc của Java, vì vậy đáp ứng yêu cầu của sealed interface. Chúng không cần ghi thêm từ khóa `final`.

## 2. Ba lựa chọn cho kiểu con trực tiếp

Một class hoặc interface được liệt kê trong `permits` phải khai báo cách nhánh của nó tiếp tục tồn tại:

| Khai báo | Ý nghĩa trong hệ phân cấp |
|---|---|
| `final` | Đóng nhánh tại class này; không cho class khác kế thừa nó. |
| `sealed` | Tiếp tục giới hạn kiểu con trực tiếp bằng một danh sách `permits` riêng. |
| `non-sealed` | Mở nhánh này để các kiểu khác có thể kế thừa hoặc triển khai nó mà không cần được liệt kê trong `permits` của sealed type gốc. |

Record luôn là `final` ngầm định. Một enum không có hằng nào khai báo thân lớp riêng là `final` ngầm định; enum có ít nhất một hằng với thân lớp riêng là `sealed` ngầm định. Interface không thể khai báo `final`; nếu interface là kiểu con trực tiếp của sealed type thì nó phải là `sealed` hoặc `non-sealed`.

Ví dụ về một tầng được kiểm soát:

~~~java
sealed interface ApiResult permits ApiSuccess, ApiFailure, ApiDeferred {
}

sealed class ApiFailure implements ApiResult
        permits NotFoundFailure, ValidationFailure {
}

final class NotFoundFailure extends ApiFailure {
}

final class ValidationFailure extends ApiFailure {
}
~~~

Đây là đoạn minh họa quan hệ kiểu, không phải chương trình hoàn chỉnh. `ApiFailure` có danh sách `permits` riêng vì nó là một sealed class. Hai kiểu con của nó đóng nhánh bằng `final`.

Một nhánh mở có thể được khai báo như sau:

~~~java
non-sealed interface ApiDeferred extends ApiResult {
    String retryAfter();
}

final class RetryLater implements ApiDeferred {
    @Override
    public String retryAfter() {
        return "30 seconds";
    }
}
~~~

`ApiDeferred` vẫn phải xuất hiện trong danh sách `permits` của `ApiResult`, vì nó là kiểu con trực tiếp. Nhưng `RetryLater` chỉ triển khai `ApiDeferred`; nó không cần xuất hiện trong danh sách của `ApiResult`, vì quan hệ đó là gián tiếp.

## 3. Quy tắc về package, module và danh sách permits

Java áp dụng một số ràng buộc để sealed hierarchy được kiểm soát:

- Các kiểu được nêu trong `permits` phải thực sự là kiểu con trực tiếp của sealed class hoặc sealed interface đó.
- Trong named module, sealed type và các kiểu con trực tiếp được cho phép phải thuộc cùng module.
- Trong unnamed module, sealed type và các kiểu con trực tiếp được cho phép phải thuộc cùng package.
- Mỗi kiểu con trực tiếp phải là `final`, `sealed` hoặc `non-sealed`, trừ các kiểu như record vốn final ngầm định.
- Nếu bỏ mệnh đề `permits`, Java suy ra các kiểu con được cho phép từ những khai báo phù hợp trong cùng compilation unit.

Những giới hạn này có ý nghĩa với API công khai. Một sealed interface trong thư viện không thể được triển khai tùy ý bởi một class ở module bên ngoài. Nếu cần hỗ trợ phần mở rộng của bên thứ ba, API có thể cung cấp một nhánh `non-sealed` làm điểm mở rộng, hoặc chọn một interface không sealed ngay từ đầu.

## 4. Ví dụ hoàn chỉnh chạy trên Java 17

Lưu chương trình sau thành file `SealedApiDemo.java`. Ví dụ thể hiện một API kết quả có hai biến thể được đóng và một nhánh trì hoãn có thể mở rộng. Chương trình dùng pattern matching với `instanceof`, đã là tính năng chính thức trước Java 17; không dùng preview switch patterns.

~~~java
public class SealedApiDemo {
    public static void main(String[] args) {
        ApiResult[] results = {
            new ApiSuccess("created"),
            new ApiFailure(404, "Not found"),
            new RetryLater("30 seconds")
        };

        for (ApiResult result : results) {
            if (result instanceof ApiSuccess success) {
                System.out.println("SUCCESS: " + success.value());
            } else if (result instanceof ApiFailure failure) {
                System.out.println(
                        "ERROR " + failure.code() + ": " + failure.message());
            } else if (result instanceof ApiDeferred deferred) {
                System.out.println("DEFERRED: retry after " + deferred.retryAfter());
            }
        }
    }
}

sealed interface ApiResult
        permits ApiSuccess, ApiFailure, ApiDeferred {
}

record ApiSuccess(String value) implements ApiResult {
}

record ApiFailure(int code, String message) implements ApiResult {
}

non-sealed interface ApiDeferred extends ApiResult {
    String retryAfter();
}

final class RetryLater implements ApiDeferred {
    private final String delay;

    RetryLater(String delay) {
        this.delay = delay;
    }

    @Override
    public String retryAfter() {
        return delay;
    }
}
~~~

Biên dịch và chạy bằng JDK 17 trở lên:

~~~shell
javac --release 17 SealedApiDemo.java
java SealedApiDemo
~~~

Stdout chính xác:

~~~text
SUCCESS: created
ERROR 404: Not found
DEFERRED: retry after 30 seconds
~~~

Các kiểu phụ trợ không khai báo `public` nên có thể cùng nằm trong file với lớp `public` `SealedApiDemo`. Nếu chuyển ví dụ sang thư viện thật, cần thiết kế package/module và mức truy cập phù hợp với hợp đồng API.

## 5. Sealed types giúp định hình tiến hóa API ra sao?

Giả sử phiên bản đầu của thư viện công bố:

~~~java
public sealed interface ApiResult
        permits ApiSuccess, ApiFailure {
}
~~~

Hợp đồng cho biết mọi kiểu triển khai trực tiếp đều thuộc danh sách được kiểm soát. Người dùng API có thể dựa vào tập hợp biến thể đã công bố khi viết xử lý nghiệp vụ và kiểm thử.

Nếu phiên bản sau cần bổ sung kết quả trì hoãn, thư viện có thể mở rộng danh sách một cách tường minh:

~~~java
public sealed interface ApiResult
        permits ApiSuccess, ApiFailure, ApiDeferred {
}
~~~

Sau đó thư viện khai báo `ApiDeferred` là `non-sealed` nếu muốn cho phép các kiểu khác tiếp tục triển khai nhánh này. Nếu nhánh mới cũng cần được kiểm soát chặt chẽ, có thể dùng một interface hoặc class `sealed` với danh sách `permits` riêng.

Việc thay đổi danh sách cho phép là một thay đổi hợp đồng API cần được xem xét khi phát hành. Trước khi công bố phiên bản mới, hãy biên dịch lại các module phụ thuộc và kiểm tra các trường hợp xử lý mọi biến thể. Mức độ ảnh hưởng phụ thuộc vào cách client sử dụng API, phiên bản Java và cách mã client được biên dịch.

Sealed type tạo ra sự cân bằng giữa tính ổn định và khả năng mở rộng:

- **Tập biến thể đóng:** phù hợp với các kết quả, trạng thái hoặc kiểu dữ liệu mà thư viện muốn kiểm soát.
- **Nhánh mở có chủ đích:** phù hợp khi cần một điểm mở rộng dành cho các implementation khác.
- **Interface mở:** phù hợp khi mục tiêu chính là cho phép client bên ngoài tự triển khai nhiều kiểu mà thư viện không thể liệt kê trước.

Không nên seal một interface công khai chỉ vì muốn dùng cú pháp mới. Hãy chọn nó khi giới hạn kiểu con trực tiếp là một phần có chủ đích của thiết kế API.

## 6. Tác động tới switch và xử lý đầy đủ các nhánh

Sealed hierarchy cung cấp thông tin về các kiểu con trực tiếp mà trình biên dịch có thể dùng để kiểm tra tính đầy đủ trong những cấu trúc ngôn ngữ hỗ trợ việc đó. Điều này đặc biệt hữu ích với pattern matching for `switch` trong Java hiện đại.

Cần phân biệt mốc ngôn ngữ: pattern matching for `switch` vẫn là preview trong Java 17 và chỉ trở thành tính năng chính thức trong Java 21. Vì vậy, ví dụ chạy bằng `--release 17` ở trên sử dụng `instanceof` thay vì pattern switch.

Khi một thư viện thêm biến thể mới, client dùng switch expression exhaustive có thể cần cập nhật các nhánh khi biên dịch lại. Ngay cả khi client hiện tại dùng `if/else`, logic cũng có thể cần được xem xét để không bỏ qua một biến thể mới. Sealed giúp trình biên dịch phát hiện một số trường hợp thiếu nhánh, nhưng không tự bảo đảm mọi logic nghiệp vụ đã xử lý đúng.

Với nhánh `non-sealed`, cần đặc biệt cẩn thận: có thể tồn tại nhiều kiểu con gián tiếp hơn danh sách ở sealed type gốc. Mã xử lý nên xem xét abstraction mở đó như một nhóm, thay vì giả định rằng chỉ có một implementation cụ thể.

## 7. Lựa chọn sealed, non-sealed hay interface mở

Dùng `sealed` khi tập biến thể trực tiếp là một phần của mô hình nghiệp vụ và thư viện cần kiểm soát các kiểu được phép xuất hiện. Ví dụ gồm kết quả phân tích cú pháp, trạng thái giao dịch hoặc loại sự kiện nội bộ.

Dùng `non-sealed` cho một nhánh khi thư viện muốn kiểm soát loại khái niệm ở tầng trên, nhưng vẫn muốn cho phép ứng dụng mở rộng phía dưới. Ví dụ, `ApiResult` có thể giới hạn các nhóm kết quả, trong khi `ApiDeferred` cho phép từng ứng dụng tạo các loại kết quả trì hoãn riêng.

Dùng interface mở khi client cần quyền triển khai trực tiếp mà không cần được thư viện cho phép trước. Đây thường là lựa chọn phù hợp cho SPI hoặc plugin API được thiết kế để mở rộng qua module độc lập.

Hãy đánh giá ranh giới module, mức truy cập, vòng đời API và khả năng xử lý các biến thể mới. Những quyết định đó quan trọng hơn việc chỉ thêm từ khóa `sealed`.

## 8. Lỗi thường gặp khi áp dụng vào API

**Quên modifier ở kiểu con trực tiếp.** Một class thông thường kế thừa sealed class mà không khai báo `final`, `sealed` hoặc `non-sealed` sẽ không biên dịch. Hãy xác định rõ nhánh cần đóng hay mở.

**Đưa kiểu con gián tiếp vào `permits`.** Mệnh đề chỉ liệt kê kiểu con trực tiếp. Một kiểu ở sâu hơn phải được kết nối thông qua nhánh trung gian.

**Dùng `sealed` cho plugin API vốn cần mở rộng tự do.** Client bên ngoài module/package không thể tùy ý triển khai trực tiếp sealed interface. Hãy cân nhắc interface mở hoặc một nhánh `non-sealed` công khai.

**Nghĩ rằng Java 17 đã có pattern switch chính thức.** Java 17 có sealed types chính thức, nhưng pattern matching for `switch` ở phiên bản này vẫn là preview. Không bật preview khi mục tiêu là mã nguồn Java 17 thông thường.

**Thêm biến thể mà không kiểm tra client.** Khi danh sách `permits` thay đổi, hãy rà soát code xử lý kết quả, kiểm thử tích hợp và quy trình biên dịch các module phụ thuộc.

**Dùng `non-sealed` nhưng giả định cây kiểu đã đóng.** `non-sealed` cố ý mở nhánh đó. Mọi logic dựa trên danh sách implementation cố định cần tính đến các kiểu con có thể được thêm về sau.

**Nhầm giới hạn kiểu với xác thực dữ liệu.** Sealed types kiểm soát quan hệ kế thừa/triển khai trong hệ phân cấp. Chúng không thay thế kiểm tra đầu vào, phân quyền, validation hoặc các quy tắc nghiệp vụ.

## 9. Checklist trước khi công bố API

- Xác định tập kiểu con trực tiếp có thực sự cần được kiểm soát hay không.
- Khai báo rõ danh sách `permits`, hoặc để Java suy ra khi các kiểu được cho phép nằm trong cùng compilation unit.
- Chọn `final`, `sealed` hoặc `non-sealed` cho từng kiểu con trực tiếp theo chính sách mở rộng.
- Kiểm tra các quy tắc cùng module/package.
- Nếu muốn cho phép client mở rộng, cung cấp nhánh `non-sealed` thích hợp hoặc giữ interface mở.
- Khi thay đổi danh sách `permits`, kiểm tra mã client, các nhánh xử lý và kiểm thử tương thích trước khi phát hành.
- Biên dịch ví dụ và thư viện với phiên bản Java mục tiêu. Với Java 17, dùng `javac --release 17` để xác nhận cú pháp ổn định.
- Ghi rõ trong tài liệu API liệu tập biến thể là đóng hay có điểm mở rộng được hỗ trợ.

Sealed classes và sealed interfaces trong Java 17 cung cấp công cụ để công bố ranh giới mở rộng rõ ràng. Chúng phù hợp nhất khi chính sách về các kiểu con trực tiếp là một phần quan trọng của hợp đồng API và được duy trì có chủ đích qua các phiên bản thư viện.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="api-evolution-004">Phân biệt tương thích mã nguồn, nhị phân và hành vi</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt tương thích mã nguồn, nhị phân và hành vi

Khi một thư viện Java được nâng cấp, câu hỏi “API mới có tương thích không?” có ít nhất ba câu trả lời khác nhau. Một thay đổi có thể vẫn cho phép mã nguồn cũ biên dịch, vẫn cho phép bytecode cũ liên kết với thư viện mới, nhưng lại làm chương trình chạy khác trước. Vì vậy, hãy đánh giá riêng **tương thích mã nguồn (source compatibility)**, **tương thích nhị phân (binary compatibility)** và **tương thích hành vi (behavioral compatibility)**.

## 1. Ba loại tương thích

### Tương thích mã nguồn

Một thay đổi tương thích mã nguồn khi mã nguồn client đang hợp lệ với phiên bản API cũ vẫn có thể biên dịch với phiên bản API mới, trong cùng điều kiện compiler và dependency phù hợp.

Đây là phép thử có biên dịch lại client. Kết quả có thể thay đổi vì compiler phân giải overload, generic type hoặc kiểu trả về dựa trên API mới. Do đó, “vẫn biên dịch được” cũng chưa chắc có nghĩa là chương trình sẽ gọi đúng overload như trước.

### Tương thích nhị phân

Một thay đổi tương thích nhị phân khi các class file đã biên dịch với API cũ vẫn liên kết được với phiên bản thư viện mới mà không cần biên dịch lại client. JVM tìm phương thức và trường bằng các thông tin như tên lớp, tên thành viên và descriptor nhị phân.

Tương thích nhị phân hữu ích khi thư viện được nâng cấp độc lập với ứng dụng. Tuy nhiên, nó không bảo đảm mọi đường chạy đều thành công: một lời gọi mới có thể gặp `AbstractMethodError`, một thành viên đã bị xóa có thể gây `NoSuchMethodError` hoặc `NoSuchFieldError`, và thay đổi hành vi vẫn có thể làm hỏng logic của ứng dụng.

### Tương thích hành vi

Một thay đổi tương thích hành vi khi chương trình tiếp tục đáp ứng hợp đồng mà client được quyền dựa vào. Hợp đồng có thể bao gồm giá trị trả về, ngoại lệ, tác dụng phụ, thứ tự phần tử, quy tắc xử lý `null`, tính bất biến của kết quả, tính an toàn luồng hoặc thời điểm thực hiện thao tác.

Không phải mọi chi tiết triển khai đều là hợp đồng. Nếu API không cam kết thứ tự phần tử, client không nên dựa vào thứ tự tình cờ quan sát được. Nhưng khi tài liệu đã cam kết thứ tự, thay đổi thứ tự có thể là phá vỡ tương thích hành vi dù chữ ký phương thức không đổi.

| Loại | Câu hỏi kiểm tra | Cách kiểm tra chính |
|---|---|---|
| Mã nguồn | Source cũ có biên dịch với API mới không? | Biên dịch lại các client đại diện |
| Nhị phân | Class file cũ có chạy với thư viện mới không? | Chạy client đã biên dịch mà không biên dịch lại |
| Hành vi | Kết quả và hợp đồng quan sát được có còn đúng không? | Kiểm thử hợp đồng, hồi quy và tích hợp |

## 2. Vì sao một thay đổi có thể ảnh hưởng khác nhau?

Xét một thư viện có lớp `Printer`. Phiên bản 1 chỉ có phương thức `print(Object)`. Một client gọi phương thức bằng `null`:

~~~java
new Printer().print(null);
~~~

Ở phiên bản 2, thư viện thêm overload `print(String)`. Mã nguồn client cũ vẫn có thể biên dịch, nhưng khi biên dịch lại, compiler chọn overload `print(String)` vì `String` cụ thể hơn `Object`. Trong khi đó, bytecode client đã biên dịch với phiên bản 1 vẫn chứa lời gọi đến descriptor của `print(Object)`.

Đây là ví dụ quan trọng: **thêm overload thường giữ lại liên kết nhị phân cũ, nhưng có thể thay đổi overload được chọn khi biên dịch lại source**. Nếu hai overload có hành vi khác nhau, kết quả còn có thể thay đổi theo việc client đã được biên dịch lại hay chưa.

### Thí nghiệm có thể chạy

Tạo cấu trúc tệp sau:

~~~text
v1/Printer.java
v2/Printer.java
Client.java
~~~

`v1/Printer.java`:

~~~java
public class Printer {
    public void print(Object value) {
        System.out.println("Object overload");
    }
}
~~~

`v2/Printer.java` giữ nguyên phương thức cũ và thêm overload:

~~~java
public class Printer {
    public void print(Object value) {
        System.out.println("Object overload");
    }

    public void print(String value) {
        System.out.println("String overload");
    }
}
~~~

`Client.java`:

~~~java
public class Client {
    public static void main(String[] args) {
        new Printer().print(null);
    }
}
~~~

Biên dịch client với phiên bản 1, sau đó thay class của thư viện bằng phiên bản 2 nhưng không biên dịch lại client:

~~~text
javac -d out v1/Printer.java Client.java
javac -d out v2/Printer.java
java -cp out Client
~~~

Kết quả:

~~~text
Object overload
~~~

Bây giờ biên dịch lại client với API phiên bản 2:

~~~text
javac -cp out -d out Client.java
java -cp out Client
~~~

Kết quả:

~~~text
String overload
~~~

Thí nghiệm dùng cú pháp Java thông thường, chạy được trên JDK 17 trở lên. Hai kết quả khác nhau không phải do JVM chọn overload lúc chạy: **overload được chọn lúc biên dịch**, rồi tên và descriptor của lời gọi được ghi vào bytecode.

## 3. Tiến hóa interface: thêm phương thức abstract và default

Thay đổi interface cần được xem xét cẩn thận vì nhiều lớp bên ngoài thư viện có thể đang triển khai interface đó.

### Thêm phương thức abstract

Giả sử phiên bản đầu có:

~~~java
public interface TextCodec {
    String encode(String input);
}
~~~

Một client đã cung cấp lớp `LegacyCodec implements TextCodec`. Nếu phiên bản mới thêm phương thức abstract `String id()`, mã nguồn `LegacyCodec` khi biên dịch lại sẽ lỗi cho đến khi nó triển khai `id()` hoặc kế thừa một triển khai hợp lệ.

Các class file cũ có thể vẫn được nạp và liên kết ở những đường chạy không gọi phương thức mới. Nhưng nếu client mới gọi `id()` trên một đối tượng `LegacyCodec` cũ không có triển khai tương ứng, JVM có thể ném `AbstractMethodError`. Vì vậy, thêm phương thức abstract có thể duy trì một số khía cạnh liên kết nhị phân mà vẫn làm hỏng source cũ và đường chạy mới.

~~~java
public interface TextCodec {
    String encode(String input);

    // Được thêm ở phiên bản API mới:
    String id();
}
~~~

Đoạn mã trên minh họa chữ ký ở phiên bản mới; nó không phải một ví dụ độc lập có thể biên dịch cùng mọi lớp triển khai cũ.

### Thêm phương thức default

Java cho phép interface cung cấp triển khai mặc định:

~~~java
public interface TextCodec {
    String encode(String input);

    default String normalize(String input) {
        return input.trim();
    }
}
~~~

Một lớp đã biên dịch chỉ triển khai `encode` thường có thể tiếp tục hoạt động với phiên bản interface mới và nhận triển khai mặc định của `normalize`. Client mới biên dịch với interface mới có thể gọi phương thức đó trên đối tượng triển khai cũ.

Dù vậy, thêm `default` không phải lúc nào cũng vô hại:

- Nếu một lớp triển khai hai interface có các phương thức default xung đột cùng chữ ký, lớp có thể phải ghi đè phương thức để giải quyết xung đột.
- Nếu lớp đã có phương thức phù hợp với chữ ký mới, phương thức của lớp có thể được dùng thay cho default và tạo ra hành vi khác mà tác giả thư viện không dự định.
- Nếu kiểu trả về, quyền truy cập hoặc các quan hệ kiểu khiến chữ ký mới xung đột với mã hiện hữu, việc biên dịch lại có thể thất bại.

Khi thiết kế API interface, hãy cân nhắc phương thức default cho chức năng có triển khai mặc định hợp lý; ghi rõ hợp đồng của nó và kiểm thử với các kiểu triển khai tiêu biểu. Đừng coi `default` là bảo đảm tuyệt đối cho mọi client.

## 4. Thay đổi hành vi dù chữ ký không đổi

Giả sử API công khai phương thức:

~~~java
List<String> names();
~~~

Chữ ký này có thể không thay đổi qua nhiều phiên bản, nên source và bytecode client vẫn biên dịch/liên kết. Nhưng phiên bản mới có thể phá vỡ hành vi nếu thay đổi một cam kết trước đó, ví dụ:

- Kết quả từng có thứ tự theo tên nay trả về thứ tự không xác định.
- Danh sách có thể sửa đổi nay trở thành danh sách chỉ đọc.
- Đầu vào `null` từng được hỗ trợ nay gây `NullPointerException`.
- Một trường hợp không tìm thấy trước đây trả về `Optional.empty()` nay ném ngoại lệ.
- Phương thức trước đây không sửa đối tượng đầu vào nay thay đổi nó.
- Một thao tác từng an toàn khi gọi đồng thời nay dùng trạng thái dùng chung không được bảo vệ.
- Phương thức từng thực hiện nhanh và đồng bộ nay thực hiện I/O hoặc gọi callback bất ngờ.

Một số ví dụ chỉ là phá vỡ tương thích nếu hợp đồng cũ đã cam kết hành vi đó. Khi hành vi chưa được đặc tả, hãy phân biệt rõ giữa lỗi thực sự của thư viện và giả định riêng mà client xây dựng từ chi tiết triển khai không được bảo đảm.

Khi thay đổi hành vi là cần thiết, hãy cập nhật tài liệu, release notes và kiểm thử. Nếu thay đổi có khả năng ảnh hưởng client, cân nhắc phương thức mới hoặc cơ chế opt-in để người dùng chuyển đổi có chủ đích.

## 5. Bảng tham khảo khi tiến hóa API

| Thay đổi | Mã nguồn | Nhị phân | Rủi ro hành vi / lưu ý |
|---|---|---|---|
| Thêm phương thức tiện ích mới với tên khác | Thường tương thích | Thường tương thích | Kiểm tra overload, trạng thái dùng chung và hợp đồng |
| Thêm overload vào phương thức đang có | Có thể đổi cách phân giải hoặc gây mơ hồ | Thường giữ được lời gọi cũ nếu thành viên cũ còn nguyên | Client biên dịch lại có thể gọi overload khác |
| Thêm phương thức abstract vào interface | Có thể làm lớp triển khai cũ không biên dịch | Class file cũ có thể liên kết nhưng lời gọi phương thức mới có thể lỗi | Nguy cơ `AbstractMethodError` |
| Thêm phương thức default vào interface | Thường tương thích, nhưng có thể phát sinh xung đột | Thường tương thích | Triển khai kế thừa hoặc ghi đè có thể làm đổi kết quả |
| Xóa hoặc đổi tên phương thức công khai | Không tương thích nếu source dùng phương thức đó | Không tương thích nếu bytecode còn tham chiếu | Có thể gặp `NoSuchMethodError` |
| Đổi kiểu tham số của phương thức | Thường không tương thích | Descriptor cũ không còn khớp | Kiểm tra mọi client gọi phương thức |
| Đổi kiểu trả về của phương thức công khai | Có thể làm source cũ lỗi | Thường phá vỡ descriptor nhị phân | Không nhầm với bridge method do compiler tạo cho override covariant |
| Chỉ đổi cách thực hiện, giữ nguyên chữ ký | Thường tương thích về biên dịch | Thường tương thích về liên kết | Có thể phá vỡ hợp đồng hành vi |
| Siết điều kiện đầu vào hoặc đổi ngoại lệ | Có thể khiến client phải sửa mã | Thường không đổi descriptor | Client hợp lệ trước đây có thể thất bại lúc chạy |

Bảng này là hướng dẫn đánh giá ban đầu, không thay thế việc kiểm tra chi tiết chữ ký, kế thừa, generic type, module và các đường chạy thực tế. “Thường tương thích” không có nghĩa là được miễn kiểm thử.

## 6. Cách đánh giá một thay đổi trước khi phát hành

1. **Xác định hợp đồng công khai.** Liệt kê chữ ký, kiểu trả về, ngoại lệ, quy tắc `null`, khả năng sửa đổi kết quả, thứ tự và các cam kết khác mà client được phép dựa vào.
2. **Kiểm tra source compatibility.** Biên dịch lại một tập client đại diện với thư viện mới. Bao gồm cả các trường hợp overload, lambda, method reference, generic type và lớp tự triển khai interface.
3. **Kiểm tra binary compatibility.** Chạy các class file đã biên dịch với thư viện mới mà không biên dịch lại client. Kiểm tra các đường chạy có gọi thành viên thay đổi, nhất là phương thức mới trên đối tượng triển khai cũ.
4. **Kiểm tra behavioral compatibility.** Dùng kiểm thử hợp đồng và hồi quy để so sánh đầu ra, ngoại lệ, tác dụng phụ, thứ tự, khả năng sửa đổi và các thuộc tính được tài liệu cam kết.
5. **Đọc kỹ các lỗi ở thời điểm chạy.** `NoSuchMethodError` và `NoSuchFieldError` thường cho thấy bytecode đang tìm một thành viên không còn tồn tại với descriptor mong đợi. `AbstractMethodError` có thể xuất hiện khi lời gọi không tìm được triển khai cụ thể. Các lỗi này khác với lỗi biên dịch source.
6. **Thông báo rõ thay đổi.** Release notes nên nêu hành vi thay đổi, client bị ảnh hưởng, cách chuyển đổi và phiên bản cần dùng. Nếu cần, cung cấp giai đoạn chuyển tiếp hoặc phương thức mới trước khi loại bỏ API cũ.

Công cụ phân tích tương thích nhị phân có thể phát hiện nhiều thay đổi về chữ ký và cấu trúc, nhưng không chứng minh được tương thích hành vi. Kiểm thử tích hợp cũng không thay thế biên dịch client cũ; mỗi loại kiểm tra trả lời một câu hỏi khác nhau.

## 7. Lỗi đánh giá thường gặp

- **Chỉ kiểm tra biên dịch:** source có thể biên dịch nhưng gọi overload khác hoặc nhận hành vi mới.
- **Chỉ chạy test sau khi build lại tất cả:** cách này bỏ qua rủi ro của class file cũ còn được triển khai ngoài thực tế.
- **Cho rằng API có chữ ký giống nhau là tương thích:** hành vi, ngoại lệ và điều kiện đầu vào cũng là một phần của hợp đồng.
- **Cho rằng thêm default method luôn an toàn:** xung đột giữa interface và phương thức có sẵn vẫn có thể xuất hiện.
- **Cho rằng một lỗi của client chứng minh thư viện không tương thích:** trước tiên hãy xác định client có dựa vào hành vi được tài liệu cam kết hay vào chi tiết triển khai không được bảo đảm.
- **Sửa nhiều thay đổi cùng lúc:** khi một bản phát hành đổi chữ ký và đổi hành vi đồng thời, rất khó xác định nguyên nhân hồi quy. Chia nhỏ thay đổi và ghi lại hợp đồng trước/sau sẽ giúp đánh giá dễ hơn.

Khi xem xét một bản nâng cấp Java, hãy ghi riêng kết luận cho ba trục: mã nguồn, nhị phân và hành vi. Chỉ kết luận “tương thích” khi đã nêu rõ tương thích với client nào, trong điều kiện nào, và đã kiểm tra bằng phương pháp nào.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="api-evolution-005">Thêm abstract method và tác động tới các lớp triển khai cũ</a>

<details>
<summary>Xem chi tiết</summary>

# Thêm abstract method và tác động tới các lớp triển khai cũ

## Mục tiêu

Khi thư viện thêm phương thức trừu tượng (`abstract method`) vào interface đã công bố, lớp triển khai cũ có thể không biên dịch lại được nếu không có implementation tương ứng. Bài học này tập trung vào cách tái hiện lỗi, điều kiện khiến bytecode cũ tiếp tục chạy hoặc phát sinh `AbstractMethodError`, và cách chọn phương án mở rộng hợp đồng phù hợp. Cần thử riêng khả năng biên dịch lại source, liên kết bytecode cũ và hành vi thực tế.

## 1. Ví dụ về thay đổi gây lỗi biên dịch

Giả sử phiên bản đầu của thư viện cung cấp interface sau:

```java
public interface ReportSink {
    void write(String text);
}
```

Một ứng dụng triển khai interface:

```java
public final class FileReportSink implements ReportSink {
    @Override
    public void write(String text) {
        System.out.println(text);
    }
}
```

Cả hai đoạn mã đều biên dịch. Sau đó, tác giả thư viện quyết định thêm thao tác `flush()` vào `ReportSink`:

```java
public interface ReportSink {
    void write(String text);
    void flush();
}
```

Khi biên dịch lại `FileReportSink` với interface mới, trình biên dịch báo lỗi vì lớp cụ thể chưa triển khai `flush()`. Cách sửa trực tiếp là bổ sung phương thức:

```java
@Override
public void flush() {
    // Đẩy dữ liệu đang đệm xuống đích lưu trữ.
}
```

Phần thân trên chỉ minh họa hình dạng của phương thức; trong một triển khai thực tế, nội dung phải thực hiện đúng hợp đồng lưu trữ. Nếu lớp không thể cung cấp hành vi đó, việc thêm một phương thức trừu tượng bắt buộc có thể là dấu hiệu API cần một thiết kế khác.

Một lớp trừu tượng triển khai `ReportSink` có thể chưa cài đặt `flush()` và vẫn biên dịch, vì lớp đó không hứa sẽ tạo được đối tượng trực tiếp. Tuy nhiên, các lớp cụ thể ở dưới nó vẫn cần có một triển khai phù hợp trước khi tạo đối tượng.

## 2. Source compatibility và binary compatibility

Việc thêm phương thức trừu tượng vào interface có hai tác động cần phân biệt.

| Tình huống | Kết quả thường gặp |
|---|---|
| Biên dịch lại một lớp cụ thể cũ với interface mới | Lỗi biên dịch nếu lớp không có phương thức mới và không kế thừa được một triển khai thích hợp |
| Chạy bytecode cũ, không gọi phương thức mới | Thường vẫn chạy; phương thức mới chưa được sử dụng |
| Gọi phương thức mới trên đối tượng của lớp cũ không có triển khai | Có thể phát sinh `AbstractMethodError` lúc chạy |
| Một lớp con đã có phương thức công khai tương thích với chữ ký mới | Phương thức đó có thể đáp ứng hợp đồng theo quy tắc của Java; vẫn cần kiểm tra ngữ nghĩa |
| Dùng một triển khai mặc định (`default method`) mới | Thường giữ khả năng tương thích nguồn và nhị phân tốt hơn, nhưng phải xét xung đột và thay đổi hành vi |

Theo quy tắc tương thích nhị phân của Java, thêm một phương thức trừu tượng vào interface không nhất thiết khiến mọi bytecode đã biên dịch trước đó ngừng chạy ngay lập tức. Một chương trình cũ có thể tiếp tục chạy nếu đường thực thi không cần phương thức mới. Nhưng nếu mã mới gọi phương thức ấy trên một lớp triển khai cũ không có phần cài đặt phù hợp, JVM có thể ném `AbstractMethodError`.

Vì vậy, **“bytecode cũ vẫn khởi chạy được” không có nghĩa “API thay đổi an toàn trong mọi trường hợp”**. Một thư viện có nhiều bên thứ ba triển khai interface nên xem việc thêm phương thức trừu tượng là thay đổi có rủi ro tương thích nguồn và rủi ro lỗi lúc chạy.

## 3. Thêm default method để có hành vi mặc định

Từ Java 8, interface có thể khai báo phương thức mặc định bằng từ khóa `default`. Phương thức này có phần thân, vì vậy lớp triển khai cũ có thể kế thừa hành vi đó mà không bắt buộc phải viết lại ngay.

Ví dụ hoàn chỉnh dưới đây mô phỏng API đã phát triển thêm một khả năng tùy chọn. Phiên bản cũ của `LegacyNotifier` chỉ cài đặt `send()`; interface mới cung cấp hành vi mặc định cho `supportsScheduling()`.

Lưu nội dung vào tệp `InterfaceEvolutionDemo.java`:

```java
public class InterfaceEvolutionDemo {
    public static void main(String[] args) {
        Notifier notifier = new LegacyNotifier();

        notifier.send("deploy complete");
        System.out.println("supports scheduling: "
                + notifier.supportsScheduling());
    }
}

interface Notifier {
    void send(String message);

    default boolean supportsScheduling() {
        return false;
    }
}

final class LegacyNotifier implements Notifier {
    @Override
    public void send(String message) {
        System.out.println("sent: " + message);
    }
}
```

Biên dịch và chạy bằng Java 17 trở lên:

```text
javac -encoding UTF-8 InterfaceEvolutionDemo.java
java InterfaceEvolutionDemo
```

Kết quả chuẩn:

```text
sent: deploy complete
supports scheduling: false
```

Lớp `LegacyNotifier` không khai báo `supportsScheduling()`, nhưng vẫn là một triển khai cụ thể hợp lệ vì interface đã cung cấp phần thân mặc định. Khi một loại notifier thực sự hỗ trợ lập lịch, lớp đó có thể ghi đè phương thức và trả về `true`.

Chỉ nên thêm `default` khi thư viện xác định được hành vi chung hợp lý cho mọi triển khai chưa ghi đè. Không nên tạo một phần thân giả chỉ để mã biên dịch, chẳng hạn luôn trả về thành công khi thao tác thực tế không được hỗ trợ. Một mặc định sai có thể giữ source compatibility nhưng phá behavioral compatibility.

## 4. Rủi ro khi thay abstract bằng default

Một default method có phần thân vẫn có thể xung đột với phương thức cùng chữ ký từ interface độc lập. Chẳng hạn, nếu `Printable` và `Exportable` cùng cung cấp `default String format()`, lớp `Document implements Printable, Exportable` phải ghi đè `format()` để xác định hành vi; compiler không chọn theo thứ tự liệt kê interface. Phương thức instance tương thích đã có ở lớp hoặc lớp cha có thể được ưu tiên, nhưng ý nghĩa của nó chưa chắc trùng hợp đồng mới.

Hãy kiểm tra những interface và lớp mà client thường kết hợp, kể cả implementation ở ngoài repository. Khi không có hành vi mặc định đúng cho mọi implementation, một interface năng lực riêng sẽ trung thực hơn việc cung cấp phần thân giả. Các quy tắc phân giải chi tiết và thí nghiệm về xung đột được triển khai trong bài chuyên về default method.

## 5. Khi nào nên chọn từng cách tiến hóa API?

| Nhu cầu | Lựa chọn thường phù hợp | Lý do |
|---|---|---|
| Mọi triển khai bắt buộc phải cung cấp một khả năng cốt lõi mới | Thêm abstract method, kèm kế hoạch chuyển đổi rõ ràng | Hợp đồng mới thật sự yêu cầu mọi triển khai hỗ trợ hành vi đó |
| Có hành vi mặc định đúng cho hầu hết triển khai | Thêm default method | Các lớp cũ có thể tiếp tục hoạt động mà không phải lập tức ghi đè |
| Chỉ một số loại triển khai có khả năng mới | Tạo interface năng lực riêng, ví dụ `SchedulableNotifier` | Không ép tất cả triển khai cũ giả vờ có khả năng mới |
| Muốn cung cấp một tiện ích không cần trạng thái đa hình mới | Cân nhắc phương thức `static` hoặc lớp tiện ích | Không bắt mọi lớp triển khai phải thay đổi |
| Có một hợp đồng mới khác đáng kể với hợp đồng cũ | Tạo abstraction mới hoặc adapter chuyển đổi | Làm rõ ranh giới tương thích và giúp khách hàng di chuyển có chủ đích |

Ví dụ, thay vì bắt tất cả notifier hỗ trợ lịch chạy, có thể khai báo:

```java
interface SchedulableNotifier extends Notifier {
    void schedule(String message, java.time.Instant when);
}
```

Chỉ những notifier thực sự hỗ trợ lịch chạy mới triển khai `SchedulableNotifier`. Khách hàng có thể kiểm tra năng lực đó bằng kiểu interface hoặc yêu cầu trực tiếp loại dịch vụ phù hợp. Cách này giữ interface cơ bản ổn định khi tính năng mới chỉ có ý nghĩa với một phần triển khai.

Phương thức `static` của interface thuộc về chính interface và không phải phương thức instance mà lớp triển khai cần cài đặt. Phương thức `private` của interface (có từ Java 9) hỗ trợ chia sẻ logic bên trong các default method; nó không tạo ra một yêu cầu mới cho lớp triển khai.

## 6. Quy trình thay đổi interface an toàn hơn

1. **Xác định hợp đồng mới:** ghi rõ phương thức dùng để làm gì, đầu vào hợp lệ, kết quả, lỗi và ảnh hưởng lên trạng thái.
2. **Kiểm kê bên triển khai:** tìm tất cả lớp trong cùng repository, module, plugin và các triển khai bên ngoài đã biết.
3. **Chọn loại phương thức:** dùng abstract method khi mọi triển khai bắt buộc phải đáp ứng; dùng default khi có hành vi mặc định đúng; dùng interface năng lực riêng khi chỉ một số triển khai hỗ trợ.
4. **Đánh giá ba loại tương thích:** thử biên dịch lại mã nguồn cũ, thử chạy bytecode đã biên dịch trước đó và kiểm tra xem hành vi cũ có bị đổi hay không.
5. **Kiểm thử các trường hợp thiếu triển khai:** đặc biệt nếu khách hàng có thể nâng thư viện độc lập với việc nâng ứng dụng.
6. **Ghi hướng dẫn di chuyển:** nếu không thể tránh việc bắt buộc cài đặt phương thức mới, công bố ví dụ triển khai, thời điểm thay đổi và lỗi có thể gặp.
7. **Kiểm tra xung đột default method:** thử các lớp kết hợp nhiều interface và xác nhận hành vi được chọn đúng như hợp đồng.

Không nên chỉ chạy test trong repository của thư viện rồi kết luận thay đổi an toàn. Bộ test nội bộ có thể đã cập nhật tất cả lớp triển khai cùng lúc, trong khi người dùng thư viện vẫn có những lớp cũ ở bên ngoài.

## Ghi nhớ

- Thêm abstract method vào interface thường làm mã nguồn của lớp cụ thể cũ không biên dịch khi biên dịch lại cho đến khi có triển khai phù hợp.
- Bytecode cũ có thể vẫn chạy nếu không gọi phương thức mới; lời gọi phương thức chưa có phần triển khai có thể dẫn đến `AbstractMethodError`.
- Default method là công cụ hữu ích khi có hành vi mặc định đúng, nhưng cần kiểm tra xung đột chữ ký và thay đổi hành vi.
- Nếu khả năng mới chỉ áp dụng cho một số triển khai, interface năng lực riêng thường biểu đạt hợp đồng tốt hơn việc bắt mọi lớp triển khai một phương thức trừu tượng mới.
- Đánh giá source, binary và behavioral compatibility riêng biệt trước khi phát hành phiên bản API mới.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="api-evolution-006">Thêm default method và nguy cơ xung đột kế thừa</a>

<details>
<summary>Xem chi tiết</summary>

# Thêm default method và nguy cơ xung đột kế thừa

Từ Java 8, interface có thể khai báo **default method**: một phương thức instance có phần thân triển khai mặc định. Tính năng này giúp mở rộng hợp đồng interface bằng hành vi dùng chung mà không bắt buộc mọi lớp triển khai phải tự viết phương thức mới ngay lập tức.

Tuy nhiên, một lớp có thể triển khai nhiều interface và một interface có thể kế thừa từ nhiều interface khác. Khi các nguồn đó cung cấp những default method có chữ ký tương đương, Java cần xác định triển khai nào được sử dụng. Nếu không có quy tắc ưu tiên rõ ràng, chương trình có thể gặp xung đột ở thời điểm biên dịch; việc thêm default method vào một API đã được sử dụng cũng có thể làm lộ ra xung đột trước đó chưa tồn tại.

## 1. Default method là gì?

Một default method là phương thức instance được khai báo bằng từ khóa `default` trong interface. Phương thức này có thân và mặc định là `public`. Nó khác với phương thức `static` của interface: static method được gọi qua tên interface và không được lớp triển khai kế thừa như một phương thức instance.

```java
interface Identifiable {
    String id();

    default boolean hasId() {
        return id() != null && !id().isBlank();
    }
}
```

Lớp triển khai `Identifiable` chỉ cần cung cấp `id()`; nó có thể dùng ngay `hasId()` hoặc ghi đè phương thức này nếu hợp đồng cho phép và có lý do hợp lý.

Default method hữu ích khi:

- Một hành vi nhỏ có ý nghĩa chung với hầu hết hoặc tất cả triển khai của interface.
- API cần được mở rộng mà không buộc mọi lớp triển khai cũ phải bổ sung một phương thức abstract mới.
- Hành vi mặc định có thể được mô tả chính xác từ các thao tác đã có trong hợp đồng.

Không nên thêm default method chỉ vì muốn tránh viết mã. Hành vi mặc định phải đúng về mặt ngữ nghĩa đối với các triển khai mà interface đại diện.

## 2. Java chọn default method nào?

Khi nhiều kiểu cung cấp phương thức cùng chữ ký, hãy xét các quy tắc chính sau theo thứ tự.

### 2.1. Phương thức instance từ lớp thắng default method của interface

Nếu lớp hoặc lớp cha cung cấp phương thức instance phù hợp, phương thức đó được ưu tiên hơn default method. Điều này bảo toàn mô hình kế thừa lớp của Java.

```java
interface Greeting {
    default String greet() {
        return "Greeting";
    }
}

class BaseGreeter {
    public String greet() {
        return "BaseGreeter";
    }
}

class ClassWinsGreeter extends BaseGreeter implements Greeting {
}
```

Gọi `new ClassWinsGreeter().greet()` trả về `"BaseGreeter"`. Lớp không cần ghi đè phương thức chỉ để giải quyết default method của `Greeting`.

Quy tắc này không có nghĩa là mọi phương thức cùng tên đều thay thế được nhau: chữ ký, kiểu trả về và khả năng truy cập vẫn phải tương thích theo quy tắc Java. Phương thức static cũng không tham gia cơ chế ghi đè instance này.

### 2.2. Interface cụ thể hơn thắng interface cha

Nếu một subinterface ghi đè default method của superinterface, triển khai từ subinterface cụ thể hơn được ưu tiên.

```java
interface GeneralGreeting {
    default String greet() {
        return "General";
    }
}

interface FormalGreeting extends GeneralGreeting {
    @Override
    default String greet() {
        return "Formal";
    }
}

class FormalGreeter implements FormalGreeting {
}
```

`FormalGreeter` dùng `FormalGreeting.greet()` và nhận `"Formal"`. Đây không phải xung đột giữa hai default độc lập: quan hệ kế thừa cho biết rõ phương thức nào cụ thể hơn.

### 2.3. Hai interface không liên quan cùng khai báo default sẽ gây xung đột

Nếu hai interface không có quan hệ kế thừa cung cấp default method cùng chữ ký, Java không tự chọn interface được viết trước, được liệt kê trước hay có tên nào đó. Lớp cụ thể cần ghi đè phương thức và xác định hành vi mong muốn.

```java
interface LeftGreeting {
    default String greet() {
        return "Left";
    }
}

interface RightGreeting {
    default String greet() {
        return "Right";
    }
}

// Không biên dịch nếu bỏ phần ghi đè greet():
// class BrokenGreeter implements LeftGreeting, RightGreeting { }

class ResolvedGreeter implements LeftGreeting, RightGreeting {
    @Override
    public String greet() {
        return LeftGreeting.super.greet()
                + " + "
                + RightGreeting.super.greet();
    }
}
```

Cú pháp `LeftGreeting.super.greet()` gọi trực tiếp triển khai default của một superinterface trực tiếp. Nó hữu ích khi hai cách triển khai đều có ý nghĩa và lớp muốn kết hợp chúng. Nếu hai hành vi không thể kết hợp hợp lý, hãy viết một triển khai riêng thay vì gọi cả hai chỉ để làm trình biên dịch hết báo lỗi.

Lớp hoặc interface kết hợp các interface xung đột phải xử lý xung đột một cách tường minh. Đừng dựa vào thứ tự liệt kê tên interface để dự đoán kết quả.

## 3. Ví dụ hoàn chỉnh chạy được

Lưu đoạn mã sau trong tệp `DefaultMethodConflictDemo.java`. Ví dụ minh họa cả ba quy tắc: subinterface cụ thể hơn, phương thức lớp thắng default và lớp chủ động giải quyết hai default không liên quan.

```java
interface Greeting {
    default String greet() { return "Greeting"; }
}

interface FormalGreeting extends Greeting {
    @Override
    default String greet() { return "FormalGreeting"; }
}

class SpecificGreeting implements FormalGreeting { }

class BaseGreeter {
    public String greet() { return "BaseGreeter"; }
}

class ClassWinsGreeter extends BaseGreeter implements Greeting { }

interface LeftGreeting {
    default String greet() { return "Left"; }
}

interface RightGreeting {
    default String greet() { return "Right"; }
}

class ResolvedGreeter implements LeftGreeting, RightGreeting {
    @Override
    public String greet() {
        return LeftGreeting.super.greet() + " + " + RightGreeting.super.greet();
    }
}

public class DefaultMethodConflictDemo {
    public static void main(String[] args) {
        System.out.println(new SpecificGreeting().greet());
        System.out.println(new ClassWinsGreeter().greet());
        System.out.println(new ResolvedGreeter().greet());
    }
}
```

Biên dịch và chạy với JDK 17 trở lên:

```text
javac -encoding UTF-8 DefaultMethodConflictDemo.java
java DefaultMethodConflictDemo
```

Kết quả chuẩn:

```text
FormalGreeting
BaseGreeter
Left + Right
```

Kết quả cho thấy Java không chọn ngẫu nhiên một default method: nó ưu tiên khai báo cụ thể hơn trong cây interface, ưu tiên phương thức instance của lớp, và yêu cầu giải quyết tường minh khi hai default không liên quan xung đột.

## 4. Vì sao thêm default method vẫn có thể phá vỡ API?

Giả sử phiên bản đầu của thư viện có interface `Auditable` và `Displayable`, trong đó chưa interface nào khai báo `label()`. Một lớp khách hàng có thể triển khai cả hai mà không gặp vấn đề.

Ở phiên bản sau, thư viện thêm default `label()` vào `Auditable`. Nếu một phiên bản khác của thư viện hoặc interface mà lớp khách hàng sử dụng cũng cung cấp default `label()` trong `Displayable`, lớp có thể rơi vào xung đột giữa hai default không liên quan. Khi biên dịch lại, mã có thể không biên dịch cho đến khi lớp ghi đè phương thức để chọn hành vi.

Điều này có nghĩa là:

- Thêm default method thường ít gây gánh nặng cho lớp triển khai hơn so với thêm phương thức abstract mới.
- “Có phần thân mặc định” không đồng nghĩa với “luôn tương thích trong mọi hệ thống phân cấp”.
- Một phương thức mới có thể va chạm với default method từ interface khác hoặc với phương thức đã tồn tại trong các lớp khách hàng.
- Thay đổi thư viện cần được kiểm tra với những tổ hợp interface thực tế, không chỉ với một lớp triển khai đơn giản.

Khi phát hành thư viện dùng chung, cần xem xét cả tương thích mã nguồn lẫn tương thích nhị phân. Những tổ hợp đã biên dịch trước đó không nên được mặc nhiên coi là an toàn trong mọi trường hợp sau khi interface thay đổi; hãy kiểm thử các cấu hình triển khai được hỗ trợ và xác minh hành vi ở runtime khi có nhiều phiên bản thư viện liên quan.

## 5. Quy trình xử lý xung đột

Khi compiler báo một lớp kế thừa các default không liên quan cùng chữ ký, hãy thực hiện theo trình tự:

1. **Xác định chính xác chữ ký xung đột.** Kiểm tra tên phương thức, tham số, kiểu trả về và các interface mà lớp triển khai trực tiếp hoặc kế thừa.
2. **Tìm quan hệ kế thừa giữa các interface.** Nếu một khai báo đến từ subinterface cụ thể hơn, đó có thể là trường hợp đã có quy tắc ưu tiên.
3. **Kiểm tra lớp cha.** Xem có phương thức instance tương thích nào trong lớp hoặc lớp cha hay không; phương thức lớp thường được ưu tiên hơn default.
4. **Quyết định hợp đồng mong muốn.** Chọn một default, kết hợp các triển khai nếu có ý nghĩa, hoặc viết hành vi riêng.
5. **Ghi đè rõ ràng tại nơi phù hợp.** Với lớp cụ thể, dùng phương thức public có chữ ký tương thích. Nếu thiết kế một subinterface hợp nhất, hãy định nghĩa lại default ở interface đó để mọi lớp con nhận được một quyết định thống nhất.
6. **Biên dịch và kiểm thử.** Kiểm tra các lớp triển khai trực tiếp, các lớp dùng lớp cơ sở và những tổ hợp interface khác nhau.

Ví dụ khi chỉ muốn chọn một phía:

```java
class LeftOnlyGreeter implements LeftGreeting, RightGreeting {
    @Override
    public String greet() {
        return LeftGreeting.super.greet();
    }
}
```

Không bắt buộc phải gọi `InterfaceName.super`. Một triển khai mới có thể trả về giá trị khác, miễn là vẫn đáp ứng hợp đồng công khai của phương thức.

## 6. Thiết kế default method an toàn hơn

### Giữ phương thức nhỏ và dựa trên hợp đồng đã có

Một default method thường nên phối hợp các phương thức công khai hiện hữu, cung cấp truy vấn tiện ích hoặc thực hiện một hành vi đơn giản. Tránh tạo mặc định mà ý nghĩa chỉ đúng với một vài lớp triển khai.

### Cẩn thận với tên và chữ ký phổ biến

Những tên chung như `close()`, `name()`, `id()` hoặc `toString()` có thể xuất hiện trong nhiều hệ thống phân cấp. Tên phương thức rõ nghĩa và hợp đồng nhất quán giúp giảm nguy cơ va chạm, dù không thể loại bỏ hoàn toàn xung đột chữ ký.

### Không dùng default method thay cho trạng thái đối tượng

Interface không có trường instance cho từng đối tượng. Nếu hành vi cần trạng thái riêng, constructor, invariant hoặc một thuật toán nền phức tạp, hãy cân nhắc abstract class hoặc một đối tượng cộng tác. Default method nên được giữ ở mức phù hợp với hợp đồng interface.

### Chú ý đến phương thức có sẵn của Object

Default method không thể cung cấp triển khai thay thế cho các phương thức instance công khai của `java.lang.Object` như `toString()`, `equals(Object)` hay `hashCode()` theo cách ghi đè chúng từ interface. Nếu API cần hợp đồng cho các hành vi đó, hãy thiết kế và kiểm thử ở cấp lớp triển khai.

### Không biến ghi đè thành thao tác hình thức

Khi giải quyết xung đột, không nên chỉ gọi một default tùy ý vì nó giúp biên dịch thành công. Hãy xác định hành vi mà người gọi có thể dựa vào và kiểm tra rằng kết quả mới vẫn đúng với hợp đồng của interface.

## 7. Default method và tương thích API

Trước khi thêm một default method vào interface đã công bố, hãy xem xét các câu hỏi sau:

- Phương thức có ý nghĩa đối với mọi triển khai hay chỉ một nhóm triển khai?
- Có phương thức cùng chữ ký ở interface cha, interface độc lập hoặc lớp khách hàng không?
- Nếu hai default xung đột, nơi nào nên chịu trách nhiệm hợp nhất hành vi: interface chuyên biệt hơn hay lớp cụ thể?
- Có thể triển khai hành vi mặc định bằng các phương thức hiện hữu mà không làm thay đổi ngữ nghĩa của chúng không?
- Bộ kiểm thử có bao phủ nhiều interface cùng được triển khai, lớp cha có phương thức trùng chữ ký và các cấu hình thư viện được hỗ trợ không?

Nếu mọi triển khai không có một hành vi mặc định hợp lý, một phương thức abstract mới hoặc một interface mở rộng riêng có thể thể hiện hợp đồng tốt hơn. Nếu default method thực sự là hành vi chung, hãy tài liệu hóa điều kiện, kết quả và xung đột có thể xảy ra.

## 8. Những nhầm lẫn thường gặp

- **Cho rằng interface được liệt kê trước sẽ thắng:** thứ tự khai báo trong mệnh đề `implements` không giải quyết xung đột.
- **Cho rằng mọi default đều thắng phương thức lớp:** phương thức instance tương thích trong lớp hoặc lớp cha được ưu tiên.
- **Nhầm default method với static method:** static method được gọi qua tên interface và không được kế thừa như default instance method.
- **Cho rằng thêm default method luôn an toàn:** có thể phát sinh xung đột với interface khác hoặc với cấu trúc lớp của khách hàng.
- **Gọi `InterfaceName.super` từ bất kỳ interface nào:** cú pháp này chỉ dùng với superinterface trực tiếp phù hợp; không phải cơ chế tra cứu tùy ý mọi interface trong cây kế thừa.
- **Chỉ kiểm tra một lớp triển khai:** xung đột thường chỉ xuất hiện khi nhiều interface được kết hợp trong cùng một kiểu.

## 9. Tóm tắt

Default method giúp tiến hóa interface bằng cách bổ sung hành vi có sẵn, nhưng việc chọn triển khai vẫn tuân theo quy tắc kế thừa của Java. Phương thức instance của lớp được ưu tiên hơn default; khai báo ở subinterface cụ thể hơn được ưu tiên hơn khai báo ở interface cha; hai default không liên quan có cùng chữ ký cần được giải quyết tường minh.

Khi phát triển API, hãy xem default method là một thay đổi hợp đồng cần được đánh giá, không phải một cách tự động bảo đảm tương thích. Xác định rõ hành vi mặc định, kiểm tra các hệ thống phân cấp interface có thể kết hợp, viết ghi đè có chủ đích và chạy kiểm thử trên những cấu hình triển khai quan trọng trước khi phát hành.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="api-evolution-007">Đổi chữ ký, kiểu trả về hoặc throws và ảnh hưởng tới bên sử dụng</a>

<details>
<summary>Xem chi tiết</summary>

# Thay đổi chữ ký, kiểu trả về và `throws`: ảnh hưởng đến bên sử dụng API Java

Khi API Java thay đổi, mã nguồn thư viện có thể biên dịch thành công nhưng các ứng dụng sử dụng thư viện vẫn có thể không biên dịch, không liên kết được với bytecode mới hoặc chạy với hành vi khác. Cần đánh giá ba khía cạnh: **source compatibility** (tương thích mã nguồn), **binary compatibility** (tương thích nhị phân) và **behavioral compatibility** (tương thích hành vi).

Bài này tập trung vào việc đổi tham số, kiểu trả về và mệnh đề `throws`, đặc biệt đối với phương thức public/protected và interface được dùng giữa các module.

## 1. Kiểm tra tương thích ở ba thời điểm

Đổi API cần ba phép thử riêng: **source compatibility** khi biên dịch lại client, **binary compatibility** khi chạy bytecode client cũ với thư viện mới, và **behavioral compatibility** khi đối chiếu kết quả/ngoại lệ với hợp đồng đã công bố. Ở bài này, khác biệt giữa chữ ký Java, descriptor JVM và mệnh đề `throws` là nguyên nhân khiến ba phép thử có thể cho kết luận khác nhau.

## 2. Chữ ký phương thức gồm những gì?

Trong Java, chữ ký dùng để phân biệt các phương thức dựa trên **tên phương thức, tham số kiểu (nếu có) và kiểu các tham số hình thức**. Tên biến tham số không thuộc chữ ký; kiểu trả về và mệnh đề `throws` không dùng để phân biệt overload.

```java
String find(long id)
String find(String id) // overload hợp lệ: tham số khác kiểu
```

Hai khai báo cùng tên và cùng tham số nhưng khác kiểu trả về không thể cùng tồn tại:

```java
String find(long id)
Product find(long id) // Không thể overload chỉ bằng kiểu trả về
```

Trong bytecode, descriptor của phương thức JVM có chứa kiểu tham số và kiểu trả về, nhưng không chứa mệnh đề `throws`. Điều này giải thích vì sao đổi kiểu trả về có thể khiến bytecode cũ không tìm thấy phương thức, trong khi đổi `throws` thường không làm thay đổi liên kết nhị phân.

## 3. Đổi kiểu tham số

Giả sử API v1 khai báo:

```java
public User findUser(long id)
```

Nếu v2 chỉ còn `findUser(String id)`, client gọi `findUser(42L)` sẽ không biên dịch với API mới. Bytecode cũ cũng yêu cầu descriptor có tham số `long`; nếu descriptor đó biến mất, lời gọi có thể gặp `NoSuchMethodError` khi chạy.

Một lộ trình ít gián đoạn hơn là giữ phương thức cũ làm cầu nối và thêm overload:

```java
public User findUser(long id) {
    return findUser(Long.toString(id));
}

public User findUser(String id) {
    return lookupByTextId(id);
}
```

Đây là ví dụ cấu trúc; `User` và `lookupByTextId` cần được định nghĩa theo ứng dụng. Giữ phương thức cũ cho client hiện tại thời gian di trú, sau đó có thể đánh dấu nó bằng `@Deprecated` và chỉ loại bỏ trong phiên bản có chủ đích.

Thêm overload cũng có thể làm mã nguồn cũ trở nên mơ hồ. Chẳng hạn, thêm overload nhận `Integer` cạnh overload nhận `String` có thể khiến lời gọi `findUser(null)` không còn chọn được duy nhất một phương thức. Khi thay đổi overload, kiểm tra lời gọi có `null`, lambda, method reference và boxing/unboxing.

## 4. Đổi kiểu trả về

Giả sử v1 cung cấp:

```java
public String findName(String id)
```

Nếu đổi trực tiếp thành `public Product findName(String id)`, client đang gán kết quả vào `String` có thể không biên dịch. Java không cho phép giữ hai phương thức cùng tên và cùng tham số chỉ khác kiểu trả về.

Thay đổi này cũng có thể phá tương thích nhị phân: kiểu trả về thuộc descriptor JVM. Bytecode cũ gọi descriptor trả về `String`; descriptor mới trả về `Product` không tự thay thế descriptor cũ. Nếu phương thức cũ không còn, client có thể gặp `NoSuchMethodError`.

Cách migration an toàn hơn là thêm phương thức mới với tên rõ nghĩa, giữ phương thức cũ làm cầu nối và đánh dấu nó deprecated:

```java
@Deprecated(since = "2.0", forRemoval = false)
public String findName(String id) {
    Product product = findProduct(id);
    return product == null ? null : product.name();
}

public Product findProduct(String id) {
    return lookupProduct(id);
}
```

Đây là ví dụ minh họa; `Product` giả định có accessor `name()` (ví dụ một record), còn `lookupProduct` phải được triển khai thực tế. Hãy xác định rõ hành vi khi không tìm thấy dữ liệu: `null`, `Optional` hay ngoại lệ. Không nên âm thầm thay đổi quy tắc đó trong một bản nâng cấp.

Java cho phép kiểu trả về hiệp biến khi ghi đè (return type mới là kiểu con phù hợp của kiểu cũ); trình biên dịch có thể tạo bridge method để hỗ trợ liên kết. Đây là trường hợp đặc biệt, không có nghĩa mọi thay đổi kiểu trả về đều an toàn. Với generic, thay đổi tham số kiểu nhưng giữ nguyên erasure có thể vẫn liên kết ở bytecode nhưng gây lỗi biên dịch hoặc lỗi ép kiểu lúc chạy.

## 5. Đổi mệnh đề `throws`

Mệnh đề `throws` khai báo các checked exception mà phương thức có thể ném. Nó không thuộc chữ ký dùng để overload và không nằm trong descriptor JVM, nhưng ảnh hưởng đến việc biên dịch mã nguồn.

### Thêm checked exception

Nếu API đổi từ:

```java
public String load(String id)
```

sang:

```java
public String load(String id) throws IOException
```

caller biên dịch lại có thể phải bắt `IOException` hoặc khai báo tiếp nó bằng `throws`. Các lớp triển khai và phương thức override cũng phải được rà soát; override không được khai báo checked exception rộng hơn mức hợp đồng của phương thức cha cho phép.

Vì descriptor không đổi, việc chỉ thêm `throws IOException` thường không phá liên kết nhị phân. Tuy nhiên, nếu phiên bản mới thật sự ném `IOException`, bytecode client cũ chưa xử lý ngoại lệ có thể để nó thoát ra lúc chạy. JVM không thực thi quy tắc checked exception thay cho trình biên dịch.

### Bỏ checked exception hoặc thêm unchecked exception

Bỏ checked exception thường không đổi descriptor, nhưng khi biên dịch lại client, khối `catch` chỉ bắt ngoại lệ đó có thể không còn hợp lệ nếu không còn thao tác nào trong `try` có thể ném nó. Kiểm tra cả caller lẫn các lớp override.

Các ngoại lệ như `IllegalArgumentException` và `IllegalStateException` không cần khai báo trong `throws`. Bắt đầu ném chúng thường không buộc caller sửa mã để biên dịch, nhưng vẫn có thể phá tương thích hành vi nếu client dựa vào kết quả bình thường hoặc loại lỗi khác.

## 6. Ví dụ Java 17+: thêm API mới và giữ API cũ

Ví dụ sau giữ phương thức cũ trả về tên `String`, đồng thời thêm phương thức mặc định trả về `Product` và khai báo `IOException`. Vì phương thức mới là `default`, lớp triển khai cũ không phải lập tức bổ sung một phương thức abstract mới.

Lưu vào `ApiEvolutionDemo.java`:

```java
import java.io.IOException;

public class ApiEvolutionDemo {
    @SuppressWarnings("deprecation")
    public static void main(String[] args) throws IOException {
        CatalogApi api = new InMemoryCatalogApi();

        System.out.println(api.findName("42"));
        System.out.println(api.findProduct("42"));

        try {
            api.findProduct("missing");
        } catch (IOException ex) {
            System.out.println("Lookup failed: " + ex.getMessage());
        }
    }
}

interface CatalogApi {
    @Deprecated(since = "2.0", forRemoval = false)
    String findName(String id);

    @SuppressWarnings("deprecation")
    default Product findProduct(String id) throws IOException {
        String name = findName(id);
        if (name == null) {
            throw new IOException("No product for id " + id);
        }
        return new Product(id, name);
    }
}

record Product(String id, String name) {}

final class InMemoryCatalogApi implements CatalogApi {
    @Override
    public String findName(String id) {
        return "42".equals(id) ? "Keyboard" : null;
    }
}
```

Biên dịch và chạy với JDK 17 trở lên:

```bash
javac --release 17 ApiEvolutionDemo.java
java ApiEvolutionDemo
```

Đầu ra của chương trình:

```text
Keyboard
Product[id=42, name=Keyboard]
Lookup failed: No product for id missing
```

Client cũ vẫn có thể gọi `findName`; client mới có thể chuyển sang `findProduct` và xử lý `IOException`. Đây là chiến lược chuyển tiếp minh họa. Trong hệ thống thực tế, nếu dữ liệu cũ không đủ để tạo `Product`, phương thức mặc định không phù hợp; hãy triển khai chiến lược migration dựa trên ngữ nghĩa thật của API.

## 7. Phân biệt sửa chữ ký với thêm phương thức mới

Thay đổi kiểu tham số hoặc kiểu trả về của phương thức đã phát hành có thể làm biến mất descriptor mà bytecode cũ tham chiếu. Ngược lại, thêm abstract hoặc default method vẫn giữ descriptor của các phương thức cũ, nhưng có rủi ro riêng: lớp triển khai có thể thiếu implementation, hoặc cây kế thừa phát sinh xung đột. Vì vậy, khi cần tính năng mới hãy đánh giá phương thức bổ sung hoặc interface con trước khi sửa trực tiếp chữ ký cũ.

## 8. Bảng tổng hợp tác động

| Thay đổi | Mã nguồn | Bytecode cũ | Cách giảm rủi ro |
|---|---|---|---|
| Đổi kiểu tham số | Lời gọi cũ có thể không biên dịch | Descriptor cũ có thể biến mất; có thể gặp `NoSuchMethodError` | Giữ phương thức cũ, thêm overload chuyển tiếp |
| Đổi kiểu trả về | Client dùng kiểu cũ có thể không biên dịch | Descriptor gồm kiểu trả về; có thể gặp `NoSuchMethodError` | Thêm phương thức mới có tên rõ nghĩa; deprecate phương thức cũ |
| Thêm checked exception vào `throws` | Caller có thể phải bắt hoặc khai báo ngoại lệ | Descriptor không đổi; thường vẫn liên kết | Cân nhắc API mới, cập nhật hướng dẫn và kiểm thử runtime |
| Bỏ checked exception | Một số `catch` có thể không còn hợp lệ khi biên dịch lại | Descriptor không đổi | Rà soát và cập nhật caller |
| Bắt đầu ném unchecked exception | Thường vẫn biên dịch | Thường vẫn liên kết | Coi là thay đổi hành vi, cập nhật hợp đồng |
| Thêm abstract method vào interface | Implementation có thể phải sửa | Gọi phương thức mới trên implementation cũ có thể lỗi | Dùng default method khi ngữ nghĩa phù hợp hoặc tạo interface mở rộng |
| Thêm overload | Một số lời gọi có thể trở nên mơ hồ | Thường giữ descriptor cũ | Kiểm tra `null`, lambda, method reference và overload resolution |

Đây là hướng dẫn chung. Kết quả cụ thể còn phụ thuộc descriptor trước/sau, erasure của generic, quan hệ kế thừa và bridge method mà trình biên dịch tạo.

## 9. Quy trình kiểm tra trước khi phát hành

1. **Liệt kê hợp đồng bị thay đổi:** phương thức public/protected, interface, tham số, kiểu trả về, checked exception và generic.
2. **Biên dịch lại client mẫu:** bao gồm caller xử lý ngoại lệ, lời gọi overload và các lớp tự triển khai interface.
3. **Kiểm tra nhị phân:** nếu ứng dụng không được biên dịch lại cùng thư viện, chạy smoke test với bytecode từ phiên bản trước và kiểm tra descriptor còn cần thiết.
4. **Kiểm tra hành vi:** xác minh kết quả, trường hợp không tìm thấy, loại ngoại lệ và tình huống biên.
5. **Tạo giai đoạn chuyển tiếp:** giữ phương thức cũ khi có thể, thêm overload hoặc phương thức mới, đánh dấu `@Deprecated`, công bố kế hoạch loại bỏ và hướng dẫn di trú.
6. **Loại bỏ có chủ đích:** trước khi xóa API cũ, tìm cách sử dụng trong repository, xem xét phụ thuộc đã phát hành và ghi rõ thay đổi trong release notes.

## 10. Checklist

- [ ] Thay đổi tham số có làm mất descriptor cũ không?
- [ ] Kiểu trả về có đổi trực tiếp hoặc đổi generic/erasure không?
- [ ] Checked exception mới có làm caller hoặc override phải sửa không?
- [ ] Overload mới có làm lời gọi `null`, lambda hoặc method reference mơ hồ không?
- [ ] Interface có thêm abstract method mà nhiều implementation phải hỗ trợ không?
- [ ] Đã thử cả biên dịch client mới và chạy bytecode cũ chưa?
- [ ] Có phương thức chuyển tiếp, `@Deprecated` và hướng dẫn migration khi cần không?

## Tài liệu tham khảo

- [Java Language Specification 17 — Method Signatures](https://docs.oracle.com/javase/specs/jls/se17/html/jls-8.html#jls-8.4.2)
- [Java Language Specification 17 — Binary Compatibility](https://docs.oracle.com/javase/specs/jls/se17/html/jls-13.html)
- [Java Virtual Machine Specification 17 — Descriptors](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-4.html#jvms-4.3)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="api-evolution-008">Thay đổi hằng interface và rủi ro giá trị bị nhúng vào mã đã biên dịch</a>

<details>
<summary>Xem chi tiết</summary>

# Thay đổi hằng interface và rủi ro giá trị bị nhúng vào mã đã biên dịch

Trong Java, một trường được khai báo trong interface luôn là `public static final`. Nếu trường đó là một **hằng số thời điểm biên dịch** (compile-time constant), compiler có thể chép trực tiếp giá trị của nó vào bytecode của lớp sử dụng. Vì thế, thay đổi giá trị hằng trong thư viện rồi chỉ phát hành lại thư viện có thể khiến chương trình đã biên dịch vẫn dùng giá trị cũ.

Đây là một vấn đề quan trọng khi tiến hóa API: mã nguồn có vẻ đang đọc một trường của thư viện, nhưng bytecode của client có thể đã chứa sẵn giá trị. Cần hiểu điều kiện nào khiến Java inline hằng số, cách tái hiện lỗi và cách thiết kế API khi giá trị có thể thay đổi theo phiên bản.

## 1. Trường trong interface là hằng công khai

Trong interface, khai báo:

~~~java
interface RetryPolicy {
    int MAX_RETRIES = 3;
}
~~~

tương đương về các modifier với:

~~~java
interface RetryPolicy {
    public static final int MAX_RETRIES = 3;
}
~~~

Các trường của interface phải có giá trị khởi tạo và ngầm định là:

- `public`: mã bên ngoài có thể truy cập nếu quyền truy cập của interface cho phép.
- `static`: trường thuộc về interface, không phải từng đối tượng.
- `final`: không thể gán lại trường sau khi khởi tạo.

Đây là trường hợp khác với trường instance trong một lớp. Interface không có các trường instance thông thường để mỗi đối tượng triển khai giữ một giá trị riêng. Vì trường interface là public, việc công bố nó cũng là một phần của API mà các client có thể phụ thuộc vào.

## 2. Khi nào compiler có thể inline hằng số?

Theo quy tắc Java, một *constant variable* là biến `final` có kiểu nguyên thủy hoặc `String`, được khởi tạo bằng một biểu thức hằng (constant expression). Trường interface có kiểu nguyên thủy hoặc `String` và được khởi tạo bằng biểu thức hằng thường đáp ứng điều kiện này.

Ví dụ:

~~~java
interface Protocol {
    int VERSION = 2;
    String HEADER = "X-Protocol-Version";
    long TIMEOUT_MILLIS = 1_000L * 5;
}
~~~

Các giá trị trên là biểu thức hằng hợp lệ. Khi mã khác tham chiếu trực tiếp đến `Protocol.VERSION`, compiler có thể đưa giá trị `2` vào bytecode của mã gọi.

Không phải mọi trường `static final` đều là compile-time constant:

~~~java
interface RuntimeValues {
    Integer RETRIES = 3;
    int PARSED = Integer.parseInt("3");
    int RANDOM_EXAMPLE = (int) System.currentTimeMillis();
}
~~~

- `Integer RETRIES` là kiểu tham chiếu, nên không phải constant variable theo định nghĩa Java, dù tham chiếu đó là `final`.
- `PARSED` dùng lời gọi phương thức, không phải biểu thức hằng.
- `RANDOM_EXAMPLE` phụ thuộc vào thời điểm chạy.

Các trường không phải compile-time constant cần được đọc như trường lúc chạy; chúng không được thay bằng giá trị hằng theo cùng cơ chế inline. `final` có nghĩa là không thể gán lại biến sau khi khởi tạo, còn “compile-time constant” là một khái niệm hẹp hơn.

## 3. Rủi ro khi đổi giá trị hằng trong một API

Giả sử phiên bản thư viện đầu tiên công bố:

~~~java
public interface ApiSettings {
    int MAX_RETRIES = 3;
    static int currentMaxRetries() {
        return MAX_RETRIES;
    }
}
~~~

Client được biên dịch với phiên bản này:

~~~java
public class Client {
    public static void main(String[] args) {
        System.out.println("constant=" + ApiSettings.MAX_RETRIES);
        System.out.println("method=" + ApiSettings.currentMaxRetries());
    }
}
~~~

Sau đó, thư viện đổi `MAX_RETRIES` từ `3` thành `5`. Nếu chỉ thay tệp lớp của thư viện mà không biên dịch lại `Client.class`, hai dòng có thể cho ra hai giá trị khác nhau:

- Truy cập trực tiếp `ApiSettings.MAX_RETRIES` có thể đã được compiler nhúng thành giá trị `3` trong bytecode của client.
- Lời gọi `ApiSettings.currentMaxRetries()` vẫn là một lời gọi phương thức. Nếu phiên bản mới của interface được nạp lúc chạy, phần thân phương thức mới trả về `5`.

Do đó, việc phát hành lại thư viện không tự động cập nhật các hằng đã được nhúng vào những client đã biên dịch trước đó. Đây thường là lỗi tương thích hành vi: chương trình vẫn chạy, nhưng một phần mã sử dụng giá trị cũ.

## 4. Tái hiện bằng hai phiên bản thư viện

Ví dụ này dùng Java 17 trở lên. Ta biên dịch client với phiên bản 1, sau đó chạy chính bytecode client ấy cùng phiên bản 2 của thư viện. Không biên dịch lại client ở bước đầu tiên.

Tạo cấu trúc thư mục:

~~~text
api-v1/
  ApiSettings.java
api-v2/
  ApiSettings.java
client-src/
  Client.java
~~~

Nội dung `api-v1/ApiSettings.java`:

~~~java
public interface ApiSettings {
    int MAX_RETRIES = 3;

    static int currentMaxRetries() {
        return MAX_RETRIES;
    }
}
~~~

Nội dung `api-v2/ApiSettings.java`:

~~~java
public interface ApiSettings {
    int MAX_RETRIES = 5;

    static int currentMaxRetries() {
        return MAX_RETRIES;
    }
}
~~~

Nội dung `client-src/Client.java`:

~~~java
public class Client {
    public static void main(String[] args) {
        System.out.println("constant=" + ApiSettings.MAX_RETRIES);
        System.out.println("method=" + ApiSettings.currentMaxRetries());
    }
}
~~~

Trên macOS/Linux hoặc shell POSIX, chạy từ thư mục chứa ba thư mục trên:

~~~sh
mkdir -p out-v1 out-v2 out-client
javac -d out-v1 api-v1/ApiSettings.java
javac -cp out-v1 -d out-client client-src/Client.java
javac -d out-v2 api-v2/ApiSettings.java
java -cp out-client:out-v2 Client
~~~

Trên Windows PowerShell, dùng dấu chấm phẩy làm dấu phân cách classpath:

~~~powershell
New-Item -ItemType Directory -Force out-v1, out-v2, out-client | Out-Null
javac -d out-v1 api-v1/ApiSettings.java
javac -cp out-v1 -d out-client client-src/Client.java
javac -d out-v2 api-v2/ApiSettings.java
java -cp "out-client;out-v2" Client
~~~

Kết quả của client đã biên dịch với phiên bản 1 nhưng đang chạy với phiên bản 2:

~~~text
constant=3
method=5
~~~

Dòng đầu phản ánh hằng số đã được inline khi biên dịch `Client.java`. Dòng thứ hai gọi phương thức từ phiên bản 2 của `ApiSettings`, vì vậy trả về giá trị mới.

Để kiểm tra rằng biên dịch lại client sẽ cập nhật giá trị trực tiếp, biên dịch client với phiên bản 2:

~~~sh
mkdir -p out-client-v2
javac -cp out-v2 -d out-client-v2 client-src/Client.java
java -cp out-client-v2:out-v2 Client
~~~

Trên Windows PowerShell, lệnh chạy cuối là:

~~~powershell
New-Item -ItemType Directory -Force out-client-v2 | Out-Null
javac -cp out-v2 -d out-client-v2 client-src/Client.java
java -cp "out-client-v2;out-v2" Client
~~~

Kết quả sau khi biên dịch lại:

~~~text
constant=5
method=5
~~~

Ví dụ này cho thấy khác biệt giữa giá trị được chép vào bytecode của client và giá trị được trả về bởi một phương thức đang có trong thư viện lúc chạy. Các lệnh tạo thư mục có thể được bỏ qua nếu thư mục tương ứng đã tồn tại; để tái hiện sạch từ đầu, hãy dùng các thư mục đầu ra trống.

## 5. Vì sao đây là rủi ro khi tiến hóa API?

Giả sử một thư viện được dùng bởi nhiều ứng dụng hoặc nhiều module:

1. Phiên bản 1 được phát hành với `MAX_RETRIES = 3`.
2. Một module client được biên dịch và phát hành. Bytecode có thể đã chứa giá trị `3`.
3. Thư viện phát hành phiên bản 2 với `MAX_RETRIES = 5`.
4. Ứng dụng chỉ cập nhật thư viện mà giữ nguyên bytecode client.
5. Một số nơi vẫn dùng `3`, trong khi những nơi gọi API theo cách khác có thể thấy `5`.

Điều này gây khó khăn khi điều tra vì mã nguồn đang hiển thị `ApiSettings.MAX_RETRIES`, khiến người đọc nghĩ rằng giá trị sẽ được lấy từ thư viện hiện tại. Trên thực tế, giá trị có thể đã được thay bằng một literal trong bytecode.

Việc thay đổi giá trị một compile-time constant thường không gây lỗi liên kết kiểu `NoSuchFieldError` ở các chỗ tham chiếu trực tiếp đã được inline, bởi bytecode có thể không còn đọc trường đó lúc chạy. Tuy nhiên, đây vẫn là rủi ro tương thích hành vi. Các client có thể cần được biên dịch lại để đồng bộ giá trị mới.

Không nên nhầm điều này với mọi thay đổi API đều an toàn về nhị phân. Xóa hoặc đổi tên trường, thay đổi chữ ký phương thức, hoặc thay đổi cấu trúc kiểu có thể gây các lỗi liên kết khác. Mỗi loại thay đổi cần được đánh giá theo hợp đồng và cách client đã biên dịch sử dụng API.

## 6. Lựa chọn thiết kế khi giá trị có thể thay đổi

Nếu giá trị dự kiến ổn định lâu dài và là một phần rõ ràng của hợp đồng, hằng số công khai có thể phù hợp. Ví dụ, một giá trị mã giao thức đã được chuẩn hóa hoặc một chuỗi định danh không thay đổi có thể là hằng số hợp lý. Dù vậy, nếu client cần cập nhật giá trị khi thư viện đổi phiên bản, cần có quy trình phát hành bao gồm biên dịch lại các client phụ thuộc.

Nếu giá trị có thể thay đổi theo phiên bản, môi trường triển khai, cấu hình người dùng hoặc chính sách nghiệp vụ, hãy ưu tiên một API đọc giá trị lúc chạy:

~~~java
public final class RetryPolicy {
    private RetryPolicy() {
    }

    public static int maxRetries() {
        return 5;
    }
}
~~~

Client gọi `RetryPolicy.maxRetries()`. Khi thư viện được thay bằng phiên bản mới có phần thân phương thức trả về giá trị khác, client đã biên dịch trước đó vẫn gọi phương thức khi chạy và có thể nhận giá trị mới. Điều này chỉ đúng khi lớp thư viện mới thực sự được triển khai và nạp; nếu toàn bộ ứng dụng vẫn chạy với phiên bản cũ, kết quả đương nhiên vẫn cũ.

Với cấu hình theo từng ứng dụng hoặc từng phiên chạy, một đối tượng cấu hình thường rõ ràng hơn một trường tĩnh:

~~~java
public record RetryConfig(int maxRetries) {
    public RetryConfig {
        if (maxRetries < 0) {
            throw new IllegalArgumentException("maxRetries must be >= 0");
        }
    }
}
~~~

Đối tượng cấu hình giúp truyền chính sách một cách tường minh, kiểm tra điều kiện hợp lệ và dùng các giá trị khác nhau cho những thành phần khác nhau. Record trên cần được lưu trong tệp `RetryConfig.java` nếu khai báo là kiểu public ở cấp cao nhất. Đây là một ví dụ độc lập về cách biểu diễn cấu hình, không phải phần bắt buộc của ví dụ hai phiên bản.

Chuyển trường hằng từ interface sang một lớp tiện ích **không tự nó loại bỏ inline**. Một trường `public static final` có giá trị compile-time constant trong lớp cũng có thể được inline vào client. Muốn giá trị được đọc từ phiên bản thư viện lúc chạy, hãy cung cấp một phương thức hoặc một đối tượng cấu hình có hợp đồng phù hợp, thay vì chỉ chuyển vị trí khai báo hằng số.

## 7. Hằng số trong interface và nguyên tắc thiết kế

Dùng interface chỉ để gom các hằng số thường tạo ra “constant interface”: các lớp triển khai interface nhận những tên hằng trong phạm vi sử dụng của mình dù không thực sự cần thể hiện một hành vi hay quan hệ kiểu. Cách này làm rối API và khiến việc tiến hóa các tên công khai khó hơn.

Một số nguyên tắc thực tế:

- Đặt hằng số cạnh khái niệm mà nó mô tả; tránh buộc lớp phải triển khai một interface chỉ để dùng hằng.
- Dùng hằng compile-time cho giá trị thực sự ổn định, không phải cho giá trị cấu hình hoặc chính sách có thể thay đổi.
- Nếu client cần nhìn thấy giá trị mới khi chỉ cập nhật thư viện lúc chạy, dùng phương thức truy cập hoặc đối tượng cấu hình.
- Khi thay đổi giá trị hằng compile-time trong API đã phát hành, đánh giá việc biên dịch lại toàn bộ module client phụ thuộc.
- Viết kiểm thử tương thích với đúng mô hình triển khai: kiểm thử source mới được biên dịch cùng thư viện mới không đủ để phát hiện bytecode client cũ còn chứa hằng cũ.

## 8. Phân biệt ba khái niệm dễ nhầm

| Khái niệm | Ý nghĩa | Hệ quả |
|---|---|---|
| `final` field | Không thể gán lại biến sau khi khởi tạo | Không đồng nghĩa với inline |
| Compile-time constant | Biến `final` kiểu nguyên thủy hoặc `String`, khởi tạo bằng biểu thức hằng | Compiler có thể chép giá trị vào bytecode của client |
| Runtime accessor | Phương thức hoặc cấu hình được đọc khi chương trình chạy | Có thể cung cấp giá trị mới nếu implementation mới được nạp |

Khi điều tra một giá trị cũ sau khi cập nhật thư viện, hãy kiểm tra xem mã nguồn tham chiếu trực tiếp một compile-time constant hay gọi phương thức. Tiếp theo, xác minh client có được biên dịch lại không, classpath lúc chạy thực sự nạp phiên bản thư viện nào, và có module nào khác vẫn mang bytecode cũ hay không.

Điểm cốt lõi khi tiến hóa API là phân biệt **giá trị cố định tại thời điểm biên dịch** với **giá trị được truy vấn lúc chạy**. Hằng interface tiện dụng, nhưng một giá trị đã inline sẽ không tự thay đổi chỉ vì thư viện được nâng cấp.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="api-evolution-009">Lộ trình chuyển API cũ bằng interface con, adapter và deprecation</a>

<details>
<summary>Xem chi tiết</summary>

# Tiến hóa API Java: interface con, adapter và deprecation

## 1. Vì sao cần một lộ trình chuyển đổi?

API công khai thường được nhiều ứng dụng, thư viện và nhóm phát triển sử dụng. Sau khi phát hành, thay đổi chữ ký phương thức hoặc thêm phương thức bắt buộc vào interface có thể khiến mã nguồn người dùng không biên dịch được, hoặc khiến chương trình đã biên dịch gặp lỗi khi chạy. Vì vậy, tiến hóa API cần có các bước chuyển tiếp và quy tắc tương thích rõ ràng.

Ba kỹ thuật thường được kết hợp:

- **Interface con:** tạo một hợp đồng mới mở rộng hợp đồng cũ, để khả năng mới có kiểu riêng mà không bắt buộc mọi implementation cũ thay đổi ngay.
- **Adapter:** bọc implementation cũ hoặc mới và chuyển đổi lời gọi giữa hai hợp đồng khi phép chuyển đổi có thể được xác định rõ.
- **Deprecation:** đánh dấu API cũ là không còn được khuyến nghị, chỉ ra phương án thay thế và cho người dùng thời gian di chuyển trước khi cân nhắc loại bỏ.

Mỗi kỹ thuật giải quyết một phần khác nhau. Interface con thể hiện quan hệ kiểu; adapter chuyển tiếp hoặc chuyển đổi hành vi; deprecation thông báo vòng đời API. Chúng không tự động bảo đảm mọi thay đổi đều tương thích. Trước khi phát hành, cần xác định mục tiêu là tương thích mã nguồn, tương thích binary, tương thích hành vi hay cả ba.

## 2. Mục tiêu tương thích khi di chuyển

Trước khi tạo cầu nối, xác định client cũ cần **biên dịch lại được** với API mới (source), **dùng bytecode cũ** với thư viện mới (binary), hay **giữ nguyên hành vi đã cam kết** (behavioral). Interface con giúp tránh sửa hợp đồng cũ; adapter giúp chuyển lời gọi giữa hai hợp đồng; deprecation báo cách và thời điểm chuyển đổi. Một adapter có thể giải quyết lỗi kiểu nhưng vẫn làm mất dữ liệu hoặc thay đổi ngoại lệ, nên kiểm thử cả ba mục tiêu.

## 3. Lộ trình tiến hóa API

1. **Khảo sát hợp đồng hiện tại.** Liệt kê kiểu và phương thức công khai, các implementation bên ngoài, client đang dùng API và hành vi được mong đợi.
2. **Thiết kế hợp đồng mới.** Xác định khả năng bổ sung, phần đổi nghĩa và phần không thể chuyển đổi tự động. Giữ chữ ký đã phát hành nếu có thể.
3. **Thêm API mới.** Tạo interface con hoặc kiểu mới cho khả năng mới; giữ API cũ hoạt động trong giai đoạn chuyển tiếp.
4. **Cung cấp adapter.** Chuyển tiếp các thao tác tương đương và xử lý rõ những trường hợp không thể ánh xạ.
5. **Đánh dấu deprecation.** Nêu lý do, API thay thế, khác biệt hành vi và thời điểm dự kiến loại bỏ nếu đã có kế hoạch.
6. **Kiểm thử và theo dõi.** Kiểm tra client cũ/mới, implementation cũ/mới, adapter và các trường hợp không hỗ trợ.
7. **Loại bỏ theo chính sách phát hành.** Chỉ bỏ API cũ sau thời gian chuyển tiếp phù hợp với cam kết tương thích và mức độ phụ thuộc của người dùng.

Với thư viện có người dùng bên ngoài, thường nên để API cũ và mới cùng tồn tại trong ít nhất một giai đoạn deprecation. Lộ trình cụ thể phụ thuộc vào chính sách phiên bản và mức độ ảnh hưởng của thay đổi.

## 4. Dùng interface con để mở rộng hợp đồng

Giả sử API cũ chỉ nhận tên, còn API mới cần thêm locale để tạo lời chào theo ngôn ngữ. Ta có thể tạo interface mới kế thừa interface cũ, bổ sung phương thức mới và cung cấp default method cho chữ ký cũ:

~~~java
@Deprecated(since = "2.0", forRemoval = false)
interface LegacyGreeting {
    String greet(String name);
}

record GreetingRequest(String name, String locale) {
}

interface GreetingService extends LegacyGreeting {
    String greet(GreetingRequest request);

    @Override
    default String greet(String name) {
        return greet(new GreetingRequest(name, "en"));
    }
}
~~~

`GreetingService` là interface con của `LegacyGreeting`, vì vậy đối tượng triển khai GreetingService cũng có thể được dùng ở nơi yêu cầu LegacyGreeting. Default method chuyển lời gọi cũ `greet(String)` sang phương thức mới với locale mặc định là `en`.

Tuy nhiên, implementation cũ chỉ triển khai LegacyGreeting **không tự động trở thành** GreetingService. Quan hệ kế thừa không biến một đối tượng đã tồn tại thành instance của interface con. Adapter có thể làm cầu nối cho trường hợp này.

Default method phải có ngữ nghĩa hợp lý. Trong ví dụ, locale `en` là quyết định có chủ đích. Nếu không có giá trị mặc định phù hợp, hãy yêu cầu client gọi API mới hoặc trả lỗi rõ ràng thay vì tự đặt một giá trị tùy tiện.

## 5. Dùng adapter để bọc implementation cũ

Adapter triển khai interface mới nhưng nhận một đối tượng interface cũ ở constructor. Khi client gọi phương thức mới, adapter chuyển yêu cầu sang thao tác cũ nếu có thể:

~~~java
final class LegacyGreetingAdapter implements GreetingService {
    private final LegacyGreeting legacy;

    LegacyGreetingAdapter(LegacyGreeting legacy) {
        this.legacy = legacy;
    }

    @Override
    public String greet(GreetingRequest request) {
        if (!"en".equals(request.locale())) {
            throw new IllegalArgumentException(
                    "Legacy API supports only en");
        }
        return legacy.greet(request.name());
    }
}
~~~

Adapter này chỉ hỗ trợ locale `en`, vì API cũ không nhận thông tin locale. Với locale khác, nó ném lỗi rõ ràng thay vì giả vờ đã hỗ trợ bản địa hóa. Nếu API cũ biểu diễn đủ dữ liệu, adapter có thể chuyển đổi tự động; nếu không, tài liệu phải nêu rõ giới hạn.

Có hai chiều chuyển tiếp thường gặp:

- **Cũ sang mới:** bọc implementation cũ để client đang cần interface mới có thể sử dụng nó.
- **Mới sang cũ:** cung cấp cách nhìn tương thích cho client cũ khi implementation nội bộ đã chuyển sang API mới. Default method trên interface con có thể đủ cho chiều này; nếu hai mô hình khác nhau nhiều, có thể cần adapter riêng.

Không phải lúc nào cũng cần hai adapter. Chỉ tạo cầu nối cần thiết và tránh nhân đôi logic nếu default method hoặc một facade đã đáp ứng hợp đồng.

## 6. Ví dụ Java 17 hoàn chỉnh

Chương trình sau minh họa cả hai chiều. ModernGreetingService triển khai hợp đồng mới nhưng vẫn được client cũ dùng qua LegacyGreeting. Ngược lại, LegacyGreetingAdapter bọc implementation cũ để client mới gọi qua GreetingService.

~~~java
@Deprecated(since = "2.0", forRemoval = false)
interface LegacyGreeting {
    String greet(String name);
}

record GreetingRequest(String name, String locale) {
}

interface GreetingService extends LegacyGreeting {
    String greet(GreetingRequest request);

    @Override
    default String greet(String name) {
        return greet(new GreetingRequest(name, "en"));
    }
}

final class SimpleLegacyGreeting implements LegacyGreeting {
    @Override
    public String greet(String name) {
        return "Legacy hello, " + name;
    }
}

final class LegacyGreetingAdapter implements GreetingService {
    private final LegacyGreeting legacy;

    LegacyGreetingAdapter(LegacyGreeting legacy) {
        this.legacy = legacy;
    }

    @Override
    public String greet(GreetingRequest request) {
        if (!"en".equals(request.locale())) {
            throw new IllegalArgumentException(
                    "Legacy API supports only en");
        }
        return legacy.greet(request.name());
    }
}

final class ModernGreetingService implements GreetingService {
    @Override
    public String greet(GreetingRequest request) {
        return switch (request.locale()) {
            case "en" -> "Hello, " + request.name();
            case "vi" -> "Xin chào, " + request.name();
            default -> throw new IllegalArgumentException(
                    "Unsupported locale: " + request.locale());
        };
    }
}

public class Main {
    @SuppressWarnings("deprecation")
    public static void main(String[] args) {
        GreetingService modern = new ModernGreetingService();

        // Client cũ vẫn dùng implementation mới qua kiểu cũ.
        LegacyGreeting oldView = modern;
        System.out.println("Old client -> " + oldView.greet("Linh"));

        // Client mới dùng implementation cũ qua adapter.
        GreetingService adapted =
                new LegacyGreetingAdapter(new SimpleLegacyGreeting());
        System.out.println("New client -> "
                + adapted.greet(new GreetingRequest("An", "en")));

        // Default method chuyển lời gọi cũ sang API mới.
        System.out.println("Default bridge -> " + adapted.greet("Mai"));

        try {
            adapted.greet(new GreetingRequest("An", "vi"));
        } catch (IllegalArgumentException ex) {
            System.out.println("Unsupported feature -> " + ex.getMessage());
        }
    }
}
~~~

Lưu toàn bộ chương trình vào `Main.java`, rồi biên dịch và chạy bằng JDK 17 trở lên:

~~~text
javac Main.java
java Main
~~~

Kết quả stdout chính xác:

~~~text
Old client -> Hello, Linh
New client -> Legacy hello, An
Default bridge -> Legacy hello, Mai
Unsupported feature -> Legacy API supports only en
~~~

Dòng đầu cho thấy implementation mới vẫn được dùng qua kiểu cũ nhờ default method. Dòng thứ hai cho thấy adapter bọc implementation cũ để đáp ứng API mới. Dòng thứ ba minh họa bridge mặc định; dòng cuối thể hiện adapter từ chối yêu cầu mà API cũ không thể biểu diễn.

Switch expression trong ví dụ là cú pháp chuẩn từ Java 14, nên không cần bật preview trên Java 17. Các kiểu không khai báo public có thể cùng nằm trong tệp với public class Main.

## 7. Deprecation có chủ đích

Dùng `@Deprecated` để báo rằng API không còn được khuyến nghị. Từ Java 9, annotation hỗ trợ hai thuộc tính quan trọng:

- `since`: phiên bản bắt đầu deprecate API, ví dụ `since = "2.0"`.
- `forRemoval`: cho biết API có dự kiến bị loại bỏ hay chưa. `false` phù hợp khi mới khuyến cáo chuyển đổi; `true` biểu thị ý định loại bỏ rõ ràng hơn.

Annotation không tự di chuyển người dùng, không tự tạo adapter và cũng không xóa phương thức. Hãy bổ sung JavaDoc bằng `@deprecated` để nêu lý do và API thay thế. Ví dụ minh họa riêng sau đây dùng lại tên kiểu trong chương trình chính, nên không cần dán đoạn này vào cùng tệp `Main.java`:

~~~java
/**
 * @deprecated Use {@link GreetingService} instead.
 *     This legacy API does not support locale selection.
 */
@Deprecated(since = "2.0", forRemoval = false)
interface LegacyGreeting {
    String greet(String name);
}
~~~

Giữ hướng dẫn nhất quán giữa annotation, JavaDoc, tài liệu phát hành và ví dụ. Chỉ đặt `forRemoval = true` khi có ý định loại bỏ thực tế và lộ trình phù hợp với chính sách tương thích của thư viện.

## 8. Công bố và duy trì giai đoạn chuyển tiếp

Khi giới thiệu API mới, tiếp tục phát hành các chữ ký cũ theo thời hạn tương thích đã cam kết; công bố adapter, JavaDoc deprecation và hướng dẫn thay thế cùng một phiên bản. Theo dõi nơi còn dùng API cũ, thử chạy bytecode client từ bản trước và đo những trường hợp adapter không biểu diễn được. Chỉ chuyển `forRemoval` sang `true` khi kế hoạch loại bỏ đã rõ, và tránh xóa API cũ ngay sau khi phát hành kiểu mới.

## 9. Những giới hạn cần xử lý trong adapter

Một adapter tốt thực hiện phép ánh xạ có thể giải thích và công bố phần không tương thích. Cần chú ý:

- **Mất thông tin:** API cũ không có trường dữ liệu mới. Quy định giá trị mặc định, từ chối yêu cầu hoặc yêu cầu một chính sách chuyển đổi rõ ràng.
- **Khác biệt lỗi:** API mới có thể dùng kiểu kết quả hoặc exception khác. Adapter phải chuyển đổi có chủ đích và giữ nguyên nguyên nhân cần thiết.
- **Khác biệt vòng đời:** API mới có thể yêu cầu đóng tài nguyên, hủy tác vụ hoặc quản lý trạng thái mà API cũ không hỗ trợ.
- **Vòng gọi đệ quy:** nếu default method gọi API mới nhưng adapter lại gọi ngược phương thức cũ, hãy chắc chắn có điểm triển khai thực sự kết thúc lời gọi. Hai bridge gọi qua lại có thể gây đệ quy vô hạn.
- **Trạng thái và luồng:** nếu API mới bổ sung thread-safety, tính bất biến hoặc đồng bộ, adapter chỉ nên cam kết điều mà nó thực sự bảo đảm.

Ghi giới hạn trong JavaDoc và kiểm thử các trường hợp không thể ánh xạ. Adapter minh bạch có thể từ chối thao tác không được hỗ trợ; âm thầm trả về giá trị giả thường gây lỗi khó phát hiện hơn.

## 10. Kiểm thử lộ trình di chuyển

1. **Implementation cũ:** xác nhận class chỉ triển khai interface cũ vẫn hoạt động với phiên bản thư viện được hỗ trợ.
2. **Implementation mới:** xác nhận lớp mới triển khai interface con và hoạt động qua cả kiểu mới lẫn kiểu cũ.
3. **Adapter:** kiểm tra dữ liệu, kết quả, exception và các trường hợp không được hỗ trợ.
4. **Client cũ và mới:** biên dịch client cũ với API cũ và kiểm tra nó trong môi trường nâng cấp được hỗ trợ; biên dịch client mới với API mới.
5. **Kiểm thử tương thích:** nếu cam kết binary compatibility, dùng công cụ kiểm tra API/binary hoặc bộ kiểm thử tương thích.
6. **Tài liệu:** kiểm tra hướng dẫn migration, ví dụ, JavaDoc deprecation và thông báo phát hành.

Chỉ chạy test sau khi biên dịch lại toàn bộ mã nguồn không đủ để chứng minh binary compatibility. Khi thư viện có cam kết này, giữ artifact đã biên dịch từ phiên bản trước trong bộ kiểm thử.

## 11. Chọn kỹ thuật theo tình huống

| Tình huống | Hướng xử lý |
|---|---|
| Muốn bổ sung khả năng nhưng giữ kiểu cũ làm hợp đồng cơ sở | Tạo interface con và đặt phương thức mới ở đó. |
| Client cũ cần dùng implementation mới | Cho kiểu mới tương thích với kiểu cũ qua interface con và default method có ngữ nghĩa hợp lệ. |
| Client mới cần dùng implementation chỉ triển khai API cũ | Viết adapter từ interface cũ sang interface mới. |
| Có hành vi mặc định đúng cho mọi implementation cũ | Cân nhắc default method sau khi kiểm tra xung đột và ngữ nghĩa. |
| Hai API khác nhau đáng kể về dữ liệu hoặc hành vi | Thiết kế phép chuyển đổi tường minh; có thể cần API mới độc lập. |
| API cũ đã có lựa chọn thay thế ổn định | Đánh dấu deprecated, hướng dẫn migration và lên kế hoạch loại bỏ theo chính sách phiên bản. |

Không có một mẫu duy nhất cho mọi thay đổi. Quyết định dựa trên số lượng implementation bên ngoài, mức tương thích đã cam kết, khác biệt ngữ nghĩa và chi phí hỗ trợ hai hợp đồng trong giai đoạn chuyển tiếp.

## 12. Lỗi thường gặp

- **Thêm phương thức abstract trực tiếp vào interface công khai mà không đánh giá tác động.** Implementation bên ngoài có thể không biên dịch hoặc không đáp ứng lời gọi mới.
- **Cho rằng interface con tự biến implementation cũ thành implementation mới.** Cần adapter hoặc thay đổi rõ ràng ở class triển khai.
- **Dùng default method với giá trị tùy tiện.** Chỉ dùng khi mặc định đúng và được tài liệu hóa.
- **Đánh dấu deprecated nhưng không nêu phương án thay thế.** Người dùng cần biết kiểu/phương thức nào nên dùng và khác biệt gì.
- **Bật `forRemoval = true` khi chưa có lộ trình.** Annotation phải phản ánh ý định phát hành thực tế.
- **Bỏ qua dữ liệu bị mất trong adapter.** Không âm thầm bỏ locale, trạng thái, lỗi hoặc dữ liệu mà API mới cần.
- **Chỉ kiểm thử sau khi biên dịch lại toàn bộ dự án.** Cách đó không chứng minh binary cũ còn tương thích.
- **Xóa API cũ ngay khi tạo API mới.** Giữ giai đoạn chuyển tiếp phù hợp với cam kết của thư viện.

## 13. Ghi nhớ

Tiến hóa API an toàn cần hợp đồng mới rõ ràng, cầu nối có hành vi xác định và thông báo deprecation đủ sớm. Interface con mở rộng khả năng mà vẫn giữ kiểu cũ làm hợp đồng cơ sở; adapter giúp dùng implementation cũ trong API mới hoặc ngược lại; `@Deprecated` báo cho người dùng biết đường di chuyển. Hãy phân biệt source, binary và behavioral compatibility, kiểm tra giới hạn ánh xạ, thử nghiệm với implementation/client cũ và chỉ loại bỏ API sau giai đoạn chuyển tiếp theo chính sách phiên bản.

</details>

- [Quay lại đầu trang](#back-to-top)
